package com.sozvezdie.messenger

import android.app.Application
import com.sozvezdie.messenger.core.data.di.AppContainer

/**
 * Точка входа приложения.
 *
 * AppContainer создаётся один раз на всё приложение: Firebase, Room
 * и репозитории должны быть в единственном экземпляре.
 */
class MessengerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
