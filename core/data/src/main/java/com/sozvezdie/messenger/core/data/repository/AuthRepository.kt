package com.sozvezdie.messenger.core.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Вход, регистрация и выход.
 *
 * Firebase сам хранит сессию на телефоне, поэтому после перезапуска
 * приложение сразу открывает список чатов, если пользователь уже вошёл.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val clearLocalHistory: suspend () -> Unit,
) {

    val currentUserId: String? get() = auth.currentUser?.uid

    // Поток id пользователя для главного экрана: сразу знает текущее
    // состояние и меняется после входа или выхода.
    private val _currentUserIdFlow = MutableStateFlow(auth.currentUser?.uid)
    val currentUserIdFlow: StateFlow<String?> = _currentUserIdFlow.asStateFlow()

    init {
        auth.addAuthStateListener { _currentUserIdFlow.value = it.currentUser?.uid }
    }

    val currentUserName: String
        get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Пользователь"

    // Поток состояния входа: экраны сами решают, показать вход или чаты.
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        // Если профиль когда-то не записался, создаём его при входе.
        ensureProfile()
    }.mapError()

    suspend fun signUp(name: String, email: String, password: String): Result<Unit> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Firebase не вернул пользователя")

        val profile = UserProfileChangeRequest.Builder()
            .setDisplayName(name.trim())
            .build()
        user.updateProfile(profile).await()

        val profileData = mapOf(
            "uid" to user.uid,
            "email" to email.trim(),
            "name" to name.trim(),
            "createdAt" to ServerValue.TIMESTAMP,
        )
        database.getReference("users").child(user.uid).setValue(profileData).await()
        Unit
    }.mapError()

    suspend fun signOut() {
        auth.signOut()
        // История на телефоне не должна оставаться после выхода.
        clearLocalHistory()
    }

    private suspend fun ensureProfile() {
        val user = auth.currentUser ?: return
        val profileRef = database.getReference("users").child(user.uid)
        val snapshot = profileRef.get().await()
        if (!snapshot.exists()) {
            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Пользователь"
            profileRef.setValue(
                mapOf(
                    "uid" to user.uid,
                    "email" to user.email.orEmpty(),
                    "name" to name,
                    "createdAt" to ServerValue.TIMESTAMP,
                ),
            ).await()
        }
    }

    /**
     * Firebase отдаёт ошибки на английском и кодами.
     * Переводим самые частые в понятные фразы, остальные — в общий текст.
     */
    private fun Result<Unit>.mapError(): Result<Unit> {
        val error = exceptionOrNull() ?: return this
        val message = when (error) {
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Неверный формат email"
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Неверный email или пароль"
                "ERROR_EMAIL_ALREADY_IN_USE" -> "Этот email уже зарегистрирован"
                "ERROR_WEAK_PASSWORD" -> "Пароль слишком простой: нужно минимум 6 символов"
                "ERROR_USER_NOT_FOUND" -> "Пользователь с таким email не найден"
                "ERROR_NETWORK_REQUEST_FAILED" -> "Нет подключения к интернету"
                "ERROR_TOO_MANY_REQUESTS" -> "Слишком много попыток. Подождите немного"
                else -> "Ошибка Firebase: ${error.errorCode}"
            }
            else -> error.message ?: "Не удалось выполнить действие"
        }
        return Result.failure(Exception(message))
    }
}
