package com.sozvezdie.messenger.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.Message
import com.sozvezdie.messenger.core.model.MessageType
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.core.ui.components.LoadingScreen
import com.sozvezdie.messenger.core.ui.theme.BubbleMine
import com.sozvezdie.messenger.core.ui.util.formatLastSeen
import com.sozvezdie.messenger.core.ui.util.formatMessageTime
import com.sozvezdie.messenger.feature.media.FileMessageCard
import com.sozvezdie.messenger.feature.media.ImagePreviewDialog
import com.sozvezdie.messenger.feature.media.MessageImage
import com.sozvezdie.messenger.feature.media.openCachedFile
import com.sozvezdie.messenger.feature.media.rememberFilePicker
import com.sozvezdie.messenger.feature.media.rememberPhotoPicker
import com.sozvezdie.messenger.feature.motion.AnimatedMessage
import com.sozvezdie.messenger.feature.motion.TypingDots

/**
 * Экран переписки: сообщения, статус собеседника и поле ввода.
 *
 * Все данные приходят из ChatViewModel. Экран только показывает их
 * и вызывает нужные методы ViewModel.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatContent(
        state = state,
        myUid = viewModel.currentUserId,
        onBack = onBack,
        onSendText = viewModel::sendText,
        onTextChanged = viewModel::onTextChanged,
        onSendImage = viewModel::sendImage,
        onSendFile = viewModel::sendFile,
        loadMedia = viewModel::loadMediaBytes,
        onFileClick = viewModel::openFile,
        onFileOpened = viewModel::onFileOpened,
        onClearError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    state: ChatUiState,
    myUid: String,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onSendImage: (android.net.Uri) -> Unit,
    onSendFile: (android.net.Uri) -> Unit,
    loadMedia: suspend (String) -> ByteArray?,
    onFileClick: (String, String) -> Unit,
    onFileOpened: () -> Unit,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    val chat = state.chat
    var text by remember { mutableStateOf("") }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var previewMediaId by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val pickPhoto = rememberPhotoPicker { uri -> onSendImage(uri) }
    val pickFile = rememberFilePicker { uri -> onSendFile(uri) }

    // Новое сообщение — прокручиваем список к концу.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHost.showSnackbar(message)
            onClearError()
        }
    }

    // Файл скачался из базы — открываем его системным приложением.
    LaunchedEffect(state.fileToOpen) {
        state.fileToOpen?.let { file ->
            openCachedFile(context, file)
            onFileOpened()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = chat?.otherUserName ?: "?", size = 38.dp)
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = chat?.otherUserName ?: "Чат",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ChatSubtitle(state)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                text = text,
                uploading = state.uploading,
                attachMenuOpen = attachMenuOpen,
                onTextChange = {
                    text = it
                    onTextChanged(it)
                },
                onAttachMenuChange = { attachMenuOpen = it },
                onPickPhoto = pickPhoto,
                onPickFile = pickFile,
                onSend = {
                    if (text.isNotBlank()) {
                        onSendText(text)
                        text = ""
                        onTextChanged("")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.messages.isEmpty() -> LoadingScreen()
                state.messages.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Сообщений пока нет",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Напишите первое сообщение или отправьте фотографию.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        AnimatedMessage {
                            MessageBubble(
                                message = message,
                                chat = chat,
                                isMine = message.senderId == myUid,
                                loadMedia = loadMedia,
                                onImageClick = { previewMediaId = message.mediaId },
                                onFileClick = onFileClick,
                            )
                        }
                    }
                }
            }
        }
    }

    previewMediaId?.let { mediaId ->
        ImagePreviewDialog(
            mediaId = mediaId,
            load = loadMedia,
            onDismiss = { previewMediaId = null },
        )
    }
}

/** Вторая строка заголовка: «печатает», «в сети» или время последнего входа. */
@Composable
private fun ChatSubtitle(state: ChatUiState) {
    val typing = state.typingName
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    when {
        typing != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$typing печатает",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(6.dp))
            TypingDots(color = MaterialTheme.colorScheme.primary)
        }

        state.presence.online -> Text(
            text = "в сети",
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
        )

        else -> Text(
            text = formatLastSeen(state.presence.lastSeen),
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
        )
    }
}

/** Поле ввода и кнопки вложений. */
@Composable
private fun InputBar(
    text: String,
    uploading: Boolean,
    attachMenuOpen: Boolean,
    onTextChange: (String) -> Unit,
    onAttachMenuChange: (Boolean) -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.imePadding()) {
            if (uploading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Загрузка файла...",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box {
                    IconButton(onClick = { onAttachMenuChange(true) }) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Прикрепить",
                        )
                    }
                    DropdownMenu(
                        expanded = attachMenuOpen,
                        onDismissRequest = { onAttachMenuChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Фотография") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                onAttachMenuChange(false)
                                onPickPhoto()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Файл") },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null)
                            },
                            onClick = {
                                onAttachMenuChange(false)
                                onPickFile()
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Сообщение") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )

                FilledIconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !uploading,
                    modifier = Modifier
                        .padding(start = 4.dp, bottom = 4.dp)
                        .size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                    )
                }
            }
        }
    }
}

/**
 * Одно сообщение.
 *
 * Свои сообщения справа и цветные, чужие слева и светлые.
 * Для своих показываем галочки прочтения, для фото — картинку,
 * для файлов — карточку с именем.
 */
@Composable
private fun MessageBubble(
    message: Message,
    chat: Chat?,
    isMine: Boolean,
    loadMedia: suspend (String) -> ByteArray?,
    onImageClick: () -> Unit,
    onFileClick: (String, String) -> Unit,
) {
    val bubbleColor = if (isMine) BubbleMine else MaterialTheme.colorScheme.surface
    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val shape = if (isMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                when (message.type) {
                    MessageType.IMAGE -> MessageImage(
                        mediaId = message.mediaId,
                        load = loadMedia,
                        onClick = onImageClick,
                    )

                    MessageType.FILE -> FileMessageCard(
                        fileName = message.fileName.ifBlank { "Файл" },
                        isMine = isMine,
                        onClick = {
                            onFileClick(message.mediaId, message.fileName.ifBlank { "Файл" })
                        },
                    )

                    MessageType.TEXT -> Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatMessageTime(message.createdAt),
                        color = if (isMine) {
                            Color.White.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    // Галочки: сообщение прочитано, если собеседник отметил более позднее время.
                    if (isMine && chat != null && chat.otherReadAt >= message.createdAt) {
                        Text(
                            text = " ✓✓",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
