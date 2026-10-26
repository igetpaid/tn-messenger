package com.sozvezdie.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sozvezdie.messenger.core.ui.theme.MessengerTheme

/**
 * Единственная Activity. Весь интерфейс написан на Compose,
 * поэтому отдельные XML-экраны не нужны.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MessengerApp).container

        setContent {
            MessengerTheme {
                MessengerAppRoot(container)
            }
        }
    }
}
