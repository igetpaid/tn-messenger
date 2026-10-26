package com.sozvezdie.messenger.core.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sozvezdie.messenger.core.model.Presence
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Профили пользователей и присутствие «в сети».
 */
class UserRepository(private val database: FirebaseDatabase) {

    // Список всех зарегистрированных пользователей — для нового диалога.
    fun observeUsers(): Flow<List<User>> = callbackFlow {
        val ref = database.reference.child("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { it.toUser() }
                    .sortedBy { it.name.lowercase() }
                trySend(users)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeProfile(uid: String): Flow<User?> = callbackFlow {
        val ref = database.reference.child("users").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toUser())
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observePresence(uid: String): Flow<Presence> = callbackFlow {
        val ref = database.reference.child("presence").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(
                    Presence(
                        online = snapshot.child("online").getValue(Boolean::class.java) ?: false,
                        lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L,
                    ),
                )
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DataSnapshot.toUser(): User? {
        val uid = child("uid").getValue(String::class.java) ?: key ?: return null
        val name = child("name").getValue(String::class.java)
            ?: child("email").getValue(String::class.java)?.substringBefore("@")
            ?: "Пользователь"
        return User(
            uid = uid,
            email = child("email").getValue(String::class.java).orEmpty(),
            name = name,
        )
    }
}

/**
 * Отмечает пользователя «в сети» и ставит обработчик onDisconnect.
 *
 * onDisconnect — специальная операция Firebase: сервер сам запишет
 * offline, если телефон потеряет связь или приложение закроется.
 */
class PresenceManager(
    private val auth: com.google.firebase.auth.FirebaseAuth,
    private val database: FirebaseDatabase,
) {
    private var lastUid: String? = null

    fun start() {
        auth.addAuthStateListener {
            val uid = auth.currentUser?.uid
            if (uid != lastUid) {
                // Предыдущий пользователь вышел — сразу помечаем его offline.
                lastUid?.let { oldUid ->
                    database.reference.child("presence").child(oldUid)
                        .child("online").setValue(false)
                }
                lastUid = uid
            }
        }

        // .info/connected — встроенный узел Firebase. Он приходит заново
        // после каждого восстановления интернета.
        val connectedRef = database.reference.child(".info").child("connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                val uid = auth.currentUser?.uid ?: return
                setOnline(uid)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    private fun setOnline(uid: String) {
        val presenceRef: DatabaseReference = database.reference.child("presence").child(uid)
        presenceRef.onDisconnect().setValue(
            mapOf(
                "online" to false,
                "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP,
            ),
        )
        presenceRef.child("online").setValue(true)
        presenceRef.child("lastSeen").setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
    }
}
