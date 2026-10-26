package com.sozvezdie.messenger.core.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.sozvezdie.messenger.core.data.local.MessageDao
import com.sozvezdie.messenger.core.data.local.MessageEntity
import com.sozvezdie.messenger.core.data.local.toModel
import com.sozvezdie.messenger.core.model.Message
import com.sozvezdie.messenger.core.model.MessageType
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Сообщения конкретного чата.
 *
 * Новые сообщения приходят из Firebase и сразу сохраняются в Room.
 * Наружу отдаётся поток Room, поэтому переписка не пропадает
 * при перезапуске приложения и открывается без интернета.
 */
class MessageRepository(
    private val database: FirebaseDatabase,
    private val messageDao: MessageDao,
) {

    fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val ref = database.reference.child("messages").child(chatId)
            .orderByChild("createdAt")
            .limitToLast(300)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { it.toEntity(chatId) }
                if (messages.isEmpty()) return
                launch { messageDao.upsert(messages) }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)

        val roomJob = launch {
            messageDao.observeMessages(chatId).collect { entities ->
                send(entities.map { it.toModel() })
            }
        }

        awaitClose {
            ref.removeEventListener(listener)
            roomJob.cancel()
        }
    }

    suspend fun sendText(chatId: String, sender: User, text: String): Result<Unit> {
        val message = mapOf(
            "senderId" to sender.uid,
            "senderName" to sender.name,
            "type" to MessageType.toKey(MessageType.TEXT),
            "text" to text,
            "createdAt" to ServerValue.TIMESTAMP,
        )
        return send(chatId, message, previewText = text, type = MessageType.TEXT)
    }

    suspend fun sendImage(chatId: String, sender: User, mediaId: String): Result<Unit> {
        val message = mapOf(
            "senderId" to sender.uid,
            "senderName" to sender.name,
            "type" to MessageType.toKey(MessageType.IMAGE),
            "text" to "",
            "mediaId" to mediaId,
            "createdAt" to ServerValue.TIMESTAMP,
        )
        return send(chatId, message, previewText = "Фотография", type = MessageType.IMAGE)
    }

    suspend fun sendFile(
        chatId: String,
        sender: User,
        mediaId: String,
        fileName: String,
    ): Result<Unit> {
        val message = mapOf(
            "senderId" to sender.uid,
            "senderName" to sender.name,
            "type" to MessageType.toKey(MessageType.FILE),
            "text" to "",
            "mediaId" to mediaId,
            "fileName" to fileName,
            "createdAt" to ServerValue.TIMESTAMP,
        )
        return send(chatId, message, previewText = fileName, type = MessageType.FILE)
    }

    private suspend fun send(
        chatId: String,
        message: Map<String, Any>,
        previewText: String,
        type: MessageType,
    ): Result<Unit> = runCatching {
        val newMessageRef = database.reference.child("messages").child(chatId).push()
        val messageId = newMessageRef.key ?: error("Не удалось создать сообщение")

        val withId = message + mapOf("id" to messageId)
        val preview = mapOf(
            "text" to previewText,
            "senderId" to message.getValue("senderId"),
            "type" to MessageType.toKey(type),
            "createdAt" to ServerValue.TIMESTAMP,
        )

        // Сообщение и превью в списке чатов обновляем одной операцией.
        database.reference.updateChildren(
            mapOf(
                "messages/$chatId/$messageId" to withId,
                "chats/$chatId/lastMessage" to preview,
                "chats/$chatId/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
        Unit
    }.mapError("Не удалось отправить сообщение")

    private fun DataSnapshot.toEntity(chatId: String): MessageEntity? {
        val id = child("id").getValue(String::class.java) ?: key ?: return null
        val type = MessageType.fromKey(child("type").getValue(String::class.java))
        return MessageEntity(
            id = id,
            chatId = chatId,
            senderId = child("senderId").getValue(String::class.java).orEmpty(),
            senderName = child("senderName").getValue(String::class.java).orEmpty(),
            type = MessageType.toKey(type),
            text = child("text").getValue(String::class.java).orEmpty(),
            mediaId = child("mediaId").getValue(String::class.java).orEmpty(),
            fileName = child("fileName").getValue(String::class.java).orEmpty(),
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
        )
    }

    private fun Result<Unit>.mapError(fallback: String): Result<Unit> {
        val error = exceptionOrNull() ?: return this
        return Result.failure(Exception(error.message ?: fallback))
    }
}
