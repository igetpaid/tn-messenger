package com.sozvezdie.messenger.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Чат в локальной базе Room.
 *
 * Это не копия узла Firebase, а удобная плоская таблица для списка диалогов:
 * имя собеседника и последнее сообщение уже разложены по полям.
 */
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessageText: String,
    val lastMessageAt: Long,
    val lastMessageSenderId: String,
    val lastMessageType: String,
    val myReadAt: Long,
    val otherReadAt: Long,
)
