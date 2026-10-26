package com.sozvezdie.messenger.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.MessageType
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.core.ui.components.EmptyState
import com.sozvezdie.messenger.core.ui.components.LoadingScreen
import com.sozvezdie.messenger.core.ui.util.formatChatTime
import com.sozvezdie.messenger.feature.motion.pressScale

/**
 * Главный экран мессенджера — список диалогов.
 * Показывает только реальные чаты, созданные пользователями.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onChatClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(
                            text = "Чаты",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = viewModel.myName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Avatar(name = viewModel.myName, size = 34.dp)
                    IconButton(onClick = viewModel::signOut) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Выйти из аккаунта",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChatClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Новый чат") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading && chats.isEmpty() -> LoadingScreen()
                chats.isEmpty() -> EmptyState(
                    title = "Пока нет чатов",
                    subtitle = "Нажмите «Новый чат» и выберите собеседника. " +
                        "Для этого должен быть зарегистрирован хотя бы один другой аккаунт.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 4.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(chats, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            myUid = viewModel.myUid,
                            onClick = { onChatClick(chat.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: Chat,
    myUid: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val unread = chat.unreadFor(myUid)

    Surface(
        color = if (unread) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = chat.otherUserName)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = chat.otherUserName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = previewText(chat, myUid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatChatTime(chat.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (unread) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

/** Превью последнего сообщения: для фото и файлов показываем подпись, а не пустоту. */
private fun previewText(chat: Chat, myUid: String): String {
    val body = when (chat.lastMessageType) {
        MessageType.IMAGE -> "Фотография"
        MessageType.FILE -> chat.lastMessageText.ifBlank { "Файл" }
        MessageType.TEXT -> chat.lastMessageText
    }
    if (body.isBlank()) return "Начните переписку"
    return if (chat.lastMessageSenderId == myUid) "Вы: $body" else body
}
