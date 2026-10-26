package com.sozvezdie.messenger.core.data.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.sozvezdie.messenger.core.data.FirebaseConfig
import com.sozvezdie.messenger.core.data.local.AppDatabase
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.data.repository.MediaRepository
import com.sozvezdie.messenger.core.data.repository.MessageRepository
import com.sozvezdie.messenger.core.data.repository.PresenceManager
import com.sozvezdie.messenger.core.data.repository.UserRepository

/**
 * Ручное внедрение зависимостей.
 *
 * Все Firebase-объекты создаются один раз здесь и передаются экранам
 * через конструкторы ViewModel. Библиотека Hilt для учебного проекта
 * избыточна: зависимостей немного, и так проще понять, что откуда берётся.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val auth = FirebaseAuth.getInstance()

    // setPersistenceEnabled включает кеш Firebase: данные читаются и без сети.
    private val database = FirebaseDatabase.getInstance(FirebaseConfig.DATABASE_URL).apply {
        setPersistenceEnabled(true)
    }

    private val localDatabase = AppDatabase.build(appContext)

    val authRepository = AuthRepository(
        auth = auth,
        database = database,
        clearLocalHistory = {
            localDatabase.messageDao().clear()
            localDatabase.chatDao().clear()
        },
    )

    val userRepository = UserRepository(database)
    val chatRepository = ChatRepository(database, localDatabase.chatDao())
    val messageRepository = MessageRepository(database, localDatabase.messageDao())
    // Медиа хранится в Realtime Database: Firebase Storage требует платный тариф.
    val mediaRepository = MediaRepository(appContext, database)

    init {
        PresenceManager(auth, database).start()
    }
}
