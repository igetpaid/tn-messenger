package com.sozvezdie.messenger.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сообщение в локальной базе. История хранится даже без интернета. */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["chatId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val type: String,
    val text: String,
    val mediaId: String,
    val fileName: String,
    val createdAt: Long,
)
