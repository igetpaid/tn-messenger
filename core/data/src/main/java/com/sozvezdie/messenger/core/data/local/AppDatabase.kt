package com.sozvezdie.messenger.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Локальная база мессенджера.
 *
 * Здесь лежат чаты и сообщения, которые уже пришли из Firebase.
 * Благодаря этому история открывается мгновенно и видна без интернета.
 */
@Database(
    entities = [ChatEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "messenger.db")
                // Для учебного прототипа проще пересоздать базу при смене схемы,
                // чем писать миграции.
                .fallbackToDestructiveMigration()
                .build()
    }
}
