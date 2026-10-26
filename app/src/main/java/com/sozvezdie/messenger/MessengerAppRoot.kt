package com.sozvezdie.messenger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sozvezdie.messenger.core.data.di.AppContainer
import com.sozvezdie.messenger.feature.auth.AuthViewModel
import com.sozvezdie.messenger.feature.auth.LoginScreen
import com.sozvezdie.messenger.feature.auth.RegisterScreen
import com.sozvezdie.messenger.feature.chat.ChatScreen
import com.sozvezdie.messenger.feature.chat.ChatViewModel
import com.sozvezdie.messenger.feature.chats.ChatListScreen
import com.sozvezdie.messenger.feature.chats.ChatListViewModel
import com.sozvezdie.messenger.feature.chats.NewChatScreen
import com.sozvezdie.messenger.feature.chats.NewChatViewModel

/**
 * Корень приложения.
 *
 * Пока пользователь не вошёл, показываем экраны входа и регистрации.
 * После входа открывается навигация с чатами. Переключение происходит
 * автоматически: Firebase сообщает об изменении состояния авторизации.
 */
@Composable
fun MessengerAppRoot(container: AppContainer) {
    val userId by container.authRepository.currentUserIdFlow.collectAsStateWithLifecycle()

    if (userId == null) {
        AuthFlow(container)
    } else {
        MainFlow(container)
    }
}

@Composable
private fun AuthFlow(container: AppContainer) {
    var showRegister by rememberSaveable { mutableStateOf(false) }
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(container.authRepository),
    )

    if (showRegister) {
        RegisterScreen(
            viewModel = viewModel,
            onBackClick = { showRegister = false },
        )
    } else {
        LoginScreen(
            viewModel = viewModel,
            onRegisterClick = { showRegister = true },
        )
    }
}

@Composable
private fun MainFlow(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "chats",
    ) {
        composable("chats") {
            val viewModel: ChatListViewModel = viewModel(
                factory = ChatListViewModel.factory(
                    container.authRepository,
                    container.chatRepository,
                ),
            )
            ChatListScreen(
                viewModel = viewModel,
                onChatClick = { chatId -> navController.navigate("chat/$chatId") },
                onNewChatClick = { navController.navigate("new_chat") },
            )
        }

        composable("new_chat") {
            val viewModel: NewChatViewModel = viewModel(
                factory = NewChatViewModel.factory(
                    container.authRepository,
                    container.chatRepository,
                    container.userRepository,
                ),
            )
            NewChatScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    // Экран выбора собеседника убираем из истории:
                    // после переписки кнопка «назад» вернёт к списку чатов.
                    navController.navigate("chat/$chatId") {
                        popUpTo("chats")
                    }
                },
            )
        }

        composable("chat/{chatId}") { entry ->
            val chatId = entry.arguments?.getString("chatId")
            if (chatId != null) {
                val viewModel: ChatViewModel = viewModel(
                    key = chatId,
                    factory = ChatViewModel.factory(
                        chatId = chatId,
                        authRepository = container.authRepository,
                        chatRepository = container.chatRepository,
                        messageRepository = container.messageRepository,
                        mediaRepository = container.mediaRepository,
                        userRepository = container.userRepository,
                    ),
                )
                ChatScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
