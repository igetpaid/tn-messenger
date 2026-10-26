package com.sozvezdie.messenger.core.data.local

import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.Message
import com.sozvezdie.messenger.core.model.MessageType

// Простые преобразования между таблицами Room и моделями приложения.
// Так экраны не знают про Entity, а база — про удобные классы.

fun ChatEntity.toModel(): Chat = Chat(
    id = id,
    otherUserId = otherUserId,
    otherUserName = otherUserName,
    lastMessageText = lastMessageText,
    lastMessageAt = lastMessageAt,
    lastMessageSenderId = lastMessageSenderId,
    lastMessageType = MessageType.fromKey(lastMessageType),
    myReadAt = myReadAt,
    otherReadAt = otherReadAt,
)

fun MessageEntity.toModel(): Message = Message(
    id = id,
    chatId = chatId,
    senderId = senderId,
    senderName = senderName,
    type = MessageType.fromKey(type),
    text = text,
    mediaId = mediaId,
    fileName = fileName,
    createdAt = createdAt,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    senderId = senderId,
    senderName = senderName,
    type = MessageType.toKey(type),
    text = text,
    mediaId = mediaId,
    fileName = fileName,
    createdAt = createdAt,
)
