package com.sozvezdie.messenger.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.model.Chat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Список чатов текущего пользователя.
 *
 * Репозиторий уже отдаёт готовый поток из Room, а ViewModel только
 * превращает его в состояние для Compose и хранит имя пользователя.
 */
class ChatListViewModel(
    private val authRepository: AuthRepository,
    chatRepository: ChatRepository,
) : ViewModel() {

    val myUid: String = authRepository.currentUserId.orEmpty()
    val myName: String = authRepository.currentUserName

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    val chats: StateFlow<List<Chat>> = chatRepository.observeChats(myUid)
        .onStart { _loading.value = true }
        .onEach { _loading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatListViewModel(authRepository, chatRepository) }
        }
    }
}
