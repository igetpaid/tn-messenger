package com.sozvezdie.messenger.feature.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние экранов входа и регистрации. */
data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Логика входа и регистрации.
 *
 * Проверки формата делаются до обращения к Firebase, чтобы пользователь
 * сразу видел понятную ошибку, а не ждал ответа сети.
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        val validationError = validateEmail(cleanEmail)
            ?: validatePassword(password)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.signIn(cleanEmail, password)
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun signUp(name: String, email: String, password: String, passwordAgain: String) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()

        val validationError = when {
            cleanName.length < 2 -> "Введите имя (минимум 2 символа)"
            validateEmail(cleanEmail) != null -> validateEmail(cleanEmail)
            validatePassword(password) != null -> validatePassword(password)
            password != passwordAgain -> "Пароли не совпадают"
            else -> null
        }
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.signUp(cleanName, cleanEmail, password)
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Введите email"
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Неверный формат email"
        else -> null
    }

    private fun validatePassword(password: String): String? = when {
        password.isBlank() -> "Введите пароль"
        password.length < 6 -> "Пароль должен быть не короче 6 символов"
        else -> null
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(repository) }
        }
    }
}
