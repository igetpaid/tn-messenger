package com.sozvezdie.messenger.core.model

/**
 * Пользователь мессенджера.
 *
 * online и lastSeen обновляются отдельным узлом presence в Firebase,
 * поэтому в обычном профиле они могут быть нулевыми.
 */
data class User(
    val uid: String,
    val email: String,
    val name: String,
    val online: Boolean = false,
    val lastSeen: Long = 0L,
) {
    // Первая буква имени для аватара-кружка.
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/** Состояние «в сети» для собеседника. */
data class Presence(
    val online: Boolean = false,
    val lastSeen: Long = 0L,
)

/** Тип сообщения. В базе хранится короткой строкой. */
enum class MessageType {
    TEXT,
    IMAGE,
    FILE;

    companion object {
        fun fromKey(key: String?): MessageType = when (key) {
            "image" -> IMAGE
            "file" -> FILE
            else -> TEXT
        }

        fun toKey(type: MessageType): String = when (type) {
            TEXT -> "text"
            IMAGE -> "image"
            FILE -> "file"
        }
    }
}

/**
 * Чат в списке диалогов.
 *
 * Прототип поддерживает личные переписки, поэтому у чата один собеседник.
 * Поля lastMessage* дублируют последнее сообщение: так список чатов
 * не должен читать всю переписку целиком.
 */
data class Chat(
    val id: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessageText: String = "",
    val lastMessageAt: Long = 0L,
    val lastMessageSenderId: String = "",
    val lastMessageType: MessageType = MessageType.TEXT,
    val myReadAt: Long = 0L,
    val otherReadAt: Long = 0L,
) {
    // Есть ли непрочитанное: последнее сообщение чужое и новее моей отметки.
    fun unreadFor(myUid: String): Boolean =
        lastMessageAt > myReadAt && lastMessageSenderId != myUid
}

/**
 * Сообщение переписки.
 *
 * mediaId указывает на запись в узле media/{chatId}: там лежит сам файл
 * в Base64. Пустая строка означает, что вложение отсутствует.
 */
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val type: MessageType,
    val text: String = "",
    val mediaId: String = "",
    val fileName: String = "",
    val createdAt: Long = 0L,
) {
    val isText: Boolean get() = type == MessageType.TEXT
}
