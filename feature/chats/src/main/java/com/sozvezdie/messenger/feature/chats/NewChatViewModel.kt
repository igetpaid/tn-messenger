package com.sozvezdie.messenger.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.data.repository.UserRepository
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние экрана выбора собеседника. */
data class NewChatUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Выбор пользователя для нового диалога.
 *
 * Список берётся из узла users в Firebase, то есть это реальные
 * зарегистрированные аккаунты. Себя из списка убираем.
 */
class NewChatViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    userRepository: UserRepository,
) : ViewModel() {

    private val myUid = authRepository.currentUserId.orEmpty()
    private val myName = authRepository.currentUserName

    private val _state = MutableStateFlow(NewChatUiState())
    val state: StateFlow<NewChatUiState> = _state.asStateFlow()

    // Поиск по имени или email — простой фильтр поверх потока пользователей.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val allUsers: StateFlow<List<User>> = userRepository.observeUsers()
        .map { users -> users.filter { it.uid != myUid } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val users: StateFlow<List<User>> = kotlinx.coroutines.flow.combine(
        allUsers,
        _query,
    ) { users, query ->
        val text = query.trim().lowercase()
        if (text.isEmpty()) {
            users
        } else {
            users.filter {
                it.name.lowercase().contains(text) || it.email.lowercase().contains(text)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Открывает чат с выбранным пользователем.
     * onOpened получает id чата — по нему приложение перейдёт к переписке.
     */
    fun openChat(other: User, onOpened: (String) -> Unit) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            chatRepository.openChat(myUid, myName, other)
                .onSuccess { chatId ->
                    _state.update { it.copy(loading = false) }
                    onOpened(chatId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message ?: "Не удалось открыть чат")
                    }
                }
        }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NewChatViewModel(authRepository, chatRepository, userRepository)
            }
        }
    }
}
