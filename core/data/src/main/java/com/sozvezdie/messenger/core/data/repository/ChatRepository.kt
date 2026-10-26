package com.sozvezdie.messenger.core.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.sozvezdie.messenger.core.data.local.ChatDao
import com.sozvezdie.messenger.core.data.local.ChatEntity
import com.sozvezdie.messenger.core.data.local.toModel
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.MessageType
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Чаты: список диалогов, создание переписки, отметка «прочитано» и «печатает».
 *
 * Схема в Realtime Database:
 *   userChats/{uid}/{chatId} = true          — быстрый список чатов пользователя
 *   chats/{chatId}/members                    — участники
 *   chats/{chatId}/names                      — имена для заголовка
 *   chats/{chatId}/lastMessage                — превью для списка
 *   chats/{chatId}/readAt/{uid}               — до какого времени прочитано
 *   typing/{chatId}/{uid}                     — кто сейчас печатает
 */
class ChatRepository(
    private val database: FirebaseDatabase,
    private val chatDao: ChatDao,
) {

    /**
     * Список чатов. Firebase присылает изменения, мы складываем их в Room,
     * а наружу отдаём поток из Room — поэтому история видна и без сети.
     */
    fun observeChats(myUid: String): Flow<List<Chat>> = callbackFlow {
        val listeners = mutableMapOf<String, Pair<DatabaseReference, ValueEventListener>>()

        fun attachChat(chatId: String) {
            val ref = database.reference.child("chats").child(chatId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val entity = snapshot.toChatEntity(myUid) ?: return
                    launch { chatDao.upsert(listOf(entity)) }
                }

                override fun onCancelled(error: DatabaseError) = Unit
            }
            ref.addValueEventListener(listener)
            listeners[chatId] = ref to listener
        }

        fun detachChat(chatId: String) {
            val pair = listeners.remove(chatId) ?: return
            pair.first.removeEventListener(pair.second)
        }

        val indexRef = database.reference.child("userChats").child(myUid)
        val indexListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                (listeners.keys - ids).forEach { detachChat(it) }
                (ids - listeners.keys).forEach { attachChat(it) }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        indexRef.addValueEventListener(indexListener)

        val roomJob = launch {
            chatDao.observeChats().collect { entities ->
                send(entities.map { it.toModel() })
            }
        }

        awaitClose {
            indexRef.removeEventListener(indexListener)
            listeners.keys.toList().forEach { detachChat(it) }
            roomJob.cancel()
        }
    }

    // Один чат в реальном времени: нужен для заголовка и статуса «прочитано».
    fun observeChat(chatId: String, myUid: String): Flow<Chat?> = callbackFlow {
        val ref = database.reference.child("chats").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toChatEntity(myUid)?.toModel())
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Открывает личный чат с пользователем или создаёт его.
     *
     * id чата собирается из двух uid по алфавиту: так у обоих
     * пользователей получится один и тот же чат, а не два разных.
     */
    suspend fun openChat(myUid: String, myName: String, other: User): Result<String> = runCatching {
        val chatId = listOf(myUid, other.uid).sorted().joinToString("__")
        val chatRef = database.reference.child("chats").child(chatId)
        val existing = chatRef.get().await()

        if (!existing.exists()) {
            val chat = mapOf(
                "id" to chatId,
                "createdAt" to ServerValue.TIMESTAMP,
                "members" to mapOf(myUid to true, other.uid to true),
                "names" to mapOf(myUid to myName, other.uid to other.name),
                "readAt" to mapOf(myUid to 0L, other.uid to 0L),
            )
            // Одним обновлением создаём чат и добавляем его обоим участникам.
            database.reference.updateChildren(
                mapOf(
                    "chats/$chatId" to chat,
                    "userChats/$myUid/$chatId" to true,
                    "userChats/${other.uid}/$chatId" to true,
                ),
            ).await()
        } else {
            // Имена могли измениться — обновляем их в чате.
            chatRef.child("names").updateChildren(
                mapOf(myUid to myName, other.uid to other.name),
            ).await()
        }
        chatId
    }.mapError("Не удалось открыть чат")

    // Отметка прочтения: время ставит сервер Firebase, чтобы часы телефонов не влияли.
    suspend fun markRead(chatId: String, uid: String) {
        database.reference
            .child("chats").child(chatId).child("readAt").child(uid)
            .setValue(ServerValue.TIMESTAMP)
            .await()
    }

    suspend fun setTyping(chatId: String, uid: String, name: String) {
        val ref = database.reference.child("typing").child(chatId).child(uid)
        ref.setValue(name).await()
        // Если приложение закроется, надпись «печатает» исчезнет сама.
        ref.onDisconnect().removeValue()
    }

    suspend fun clearTyping(chatId: String, uid: String) {
        database.reference.child("typing").child(chatId).child(uid).removeValue().await()
    }

    fun observeTyping(chatId: String, myUid: String): Flow<String?> = callbackFlow {
        val ref = database.reference.child("typing").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val other = snapshot.children.firstOrNull { it.key != myUid }
                trySend(other?.getValue(String::class.java))
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DataSnapshot.toChatEntity(myUid: String): ChatEntity? {
        val id = child("id").getValue(String::class.java) ?: key ?: return null
        val members = child("members").children.mapNotNull { it.key }
        val otherUid = members.firstOrNull { it != myUid } ?: return null
        val otherName = child("names").child(otherUid).getValue(String::class.java)
            ?: "Пользователь"

        val last = child("lastMessage")
        val lastText = last.child("text").getValue(String::class.java).orEmpty()
        val lastSender = last.child("senderId").getValue(String::class.java).orEmpty()
        val lastAt = last.child("createdAt").getValue(Long::class.java) ?: 0L
        val lastType = MessageType.fromKey(last.child("type").getValue(String::class.java))

        return ChatEntity(
            id = id,
            otherUserId = otherUid,
            otherUserName = otherName,
            lastMessageText = lastText,
            lastMessageAt = lastAt,
            lastMessageSenderId = lastSender,
            lastMessageType = MessageType.toKey(lastType),
            myReadAt = child("readAt").child(myUid).getValue(Long::class.java) ?: 0L,
            otherReadAt = child("readAt").child(otherUid).getValue(Long::class.java) ?: 0L,
        )
    }

    private fun <T> Result<T>.mapError(fallback: String): Result<T> {
        val error = exceptionOrNull() ?: return this
        return Result.failure(Exception(error.message ?: fallback))
    }
}
