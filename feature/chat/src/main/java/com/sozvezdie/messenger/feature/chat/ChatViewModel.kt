package com.sozvezdie.messenger.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.data.repository.MediaRepository
import com.sozvezdie.messenger.core.data.repository.MessageRepository
import com.sozvezdie.messenger.core.data.repository.UserRepository
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.Message
import com.sozvezdie.messenger.core.model.Presence
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Состояние экрана переписки. */
data class ChatUiState(
    val chat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val typingName: String? = null,
    val presence: Presence = Presence(),
    val loading: Boolean = true,
    val uploading: Boolean = false,
    // Файл, который пользователь попросил открыть. Экран покажет его и очистит.
    val fileToOpen: File? = null,
    val error: String? = null,
)

/**
 * Переписка одного чата.
 *
 * ViewModel слушает четыре потока Firebase: сообщения, данные чата,
 * «печатает» и присутствие собеседника. Всё складывается в одно
 * состояние, а экран просто рисует его.
 */
class ChatViewModel(
    private val chatId: String,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val mediaRepository: MediaRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    // id текущего пользователя нужен экрану, чтобы отличать свои сообщения.
    val currentUserId: String = authRepository.currentUserId.orEmpty()
    private val myUid = currentUserId
    private val myName = authRepository.currentUserName
    private val me = User(
        uid = myUid,
        email = "",
        name = myName,
    )

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    // Время последней отметки «прочитано», чтобы не писать в базу на каждое сообщение.
    private var lastMarkedAt = 0L
    private var typingJob: Job? = null
    private var typingSent = false
    private var presenceUid: String? = null

    init {
        observeMessages()
        observeChat()
        observeTyping()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.observeMessages(chatId).collect { messages ->
                _state.update { it.copy(messages = messages, loading = false) }
                markReadIfNeeded(messages)
            }
        }
    }

    private fun observeChat() {
        viewModelScope.launch {
            chatRepository.observeChat(chatId, myUid).collect { chat ->
                _state.update { it.copy(chat = chat) }
                chat ?: return@collect

                // Присутствие слушаем только когда знаем собеседника.
                if (chat.otherUserId != presenceUid) {
                    presenceUid = chat.otherUserId
                    observePresence(chat.otherUserId)
                }
            }
        }
    }

    private fun observePresence(uid: String) {
        viewModelScope.launch {
            userRepository.observePresence(uid).collect { presence ->
                _state.update { it.copy(presence = presence) }
            }
        }
    }

    private fun observeTyping() {
        viewModelScope.launch {
            chatRepository.observeTyping(chatId, myUid).collect { name ->
                _state.update { it.copy(typingName = name) }
            }
        }
    }

    /**
     * Отмечает чат прочитанным, когда пришло новое чужое сообщение.
     * Время записи сравниваем, чтобы не отправлять одинаковые запросы.
     */
    private fun markReadIfNeeded(messages: List<Message>) {
        val latest = messages.lastOrNull() ?: return
        if (latest.senderId == myUid) return
        if (latest.createdAt <= lastMarkedAt) return

        lastMarkedAt = latest.createdAt
        viewModelScope.launch {
            chatRepository.markRead(chatId, myUid)
        }
    }

    fun sendText(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return

        viewModelScope.launch {
            clearTyping()
            messageRepository.sendText(chatId, me, body)
                .onFailure { error -> showError(error.message) }
        }
    }

    fun sendImage(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            mediaRepository.uploadImage(chatId, uri)
                .onSuccess { uploaded ->
                    messageRepository.sendImage(chatId, me, uploaded.mediaId)
                        .onFailure { error -> showError(error.message) }
                }
                .onFailure { error -> showError(error.message) }
            _state.update { it.copy(uploading = false) }
        }
    }

    fun sendFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            mediaRepository.uploadFile(chatId, uri)
                .onSuccess { uploaded ->
                    messageRepository.sendFile(
                        chatId = chatId,
                        sender = me,
                        mediaId = uploaded.mediaId,
                        fileName = uploaded.fileName,
                    ).onFailure { error -> showError(error.message) }
                }
                .onFailure { error -> showError(error.message) }
            _state.update { it.copy(uploading = false) }
        }
    }

    // Фото и файлы лежат в базе, поэтому экран просит их через ViewModel.
    suspend fun loadMediaBytes(mediaId: String): ByteArray? =
        mediaRepository.loadBytes(chatId, mediaId)

    /** Готовит файл к открытию: сохраняет во временную папку приложения. */
    fun openFile(mediaId: String, fileName: String) {
        viewModelScope.launch {
            val file = mediaRepository.saveToCache(chatId, mediaId, fileName)
            if (file == null) {
                showError("Не удалось открыть файл")
            } else {
                _state.update { it.copy(fileToOpen = file) }
            }
        }
    }

    fun onFileOpened() {
        _state.update { it.copy(fileToOpen = null) }
    }

    /**
     * Пользователь печатает: ставим метку в Firebase и снимаем её
     * через две секунды после последнего нажатия клавиши.
     */
    fun onTextChanged(text: String) {
        if (text.isNotEmpty() && !typingSent) {
            typingSent = true
            viewModelScope.launch {
                chatRepository.setTyping(chatId, myUid, myName)
            }
        }

        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(2_000)
            clearTyping()
        }

        if (text.isEmpty()) {
            typingJob?.cancel()
            viewModelScope.launch { clearTyping() }
        }
    }

    private suspend fun clearTyping() {
        if (!typingSent) return
        typingSent = false
        chatRepository.clearTyping(chatId, myUid)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun showError(message: String?) {
        _state.update { it.copy(error = message ?: "Не удалось отправить сообщение") }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope уже отменён, поэтому метку «печатает» снимаем
        // в отдельной короткоживущей корутине.
        if (typingSent) {
            CoroutineScope(Dispatchers.IO).launch { clearTyping() }
        }
    }

    companion object {
        fun factory(
            chatId: String,
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
            messageRepository: MessageRepository,
            mediaRepository: MediaRepository,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    chatId = chatId,
                    authRepository = authRepository,
                    chatRepository = chatRepository,
                    messageRepository = messageRepository,
                    mediaRepository = mediaRepository,
                    userRepository = userRepository,
                )
            }
        }
    }
}
