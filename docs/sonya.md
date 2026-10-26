# Модульная архитектура мессенджера для команды из 5 человек

- **Автор:** Sonya
- **Дата:** 26.10.2026
- **Статус:** предложение к обсуждению
- **Проект:** учебный мессенджер для Android (Kotlin, Jetpack Compose, Firebase, Room)

---

## 1. Что должна решить эта архитектура

1. **Пять человек работают одновременно** в одном репозитории, каждый в своей ветке.
2. **Слияние веток проходит без конфликтов** — люди не редактируют одни и те же файлы.
3. **Любую функцию можно удалить**, и проект продолжает собираться.
4. **Каждый участник может вынести свою часть в отдельный проект** и защитить её как индивидуальную работу.
5. Структура остаётся **стандартной для Android-проекта**: Gradle-модули, `src/main`, `src/test`, манифесты, ресурсы.

Главная идея:

> **Одна функция = один Gradle-модуль. `:app` — тонкая сборка. Функции не зависят друг от друга.**

Почему не «всё в одном модуле, но по папкам»: папки не мешают коду случайно импортировать чужой экран. Gradle-модуль мешает: если `:feature:voice` не подключён в его `build.gradle.kts`, импорт просто не скомпилируется. Это и есть техническая гарантия модульности.

---

## 2. Ключевые решения

| № | Решение | Зачем |
|---|---|---|
| 1 | **Multi-module Gradle**: `:app`, `:core:*`, `:feature:*` | физическая изоляция функций |
| 2 | **Функции не зависят друг от друга** | удаление функции не ломает остальные |
| 3 | **`:core:domain`** — модели и интерфейсы | общий язык без привязки к Firebase и Android |
| 4 | **`:app`** — единственное место, где функции соединяются | навигация и DI в одном месте |
| 5 | **Ручной DI** (`AppContainer`), без Hilt | меньше настройки, проще объяснить на защите |
| 6 | **Публичный API функции** — несколько файлов в `api/` | всё остальное можно менять свободно |
| 7 | **Свой манифест и ресурсы у каждого модуля** | разрешения и строки исчезают вместе с функцией |
| 8 | **`:demo:<функция>`** — отдельное приложение для защиты | индивидуальный проект уже готов |
| 9 | **Контракты раньше кода** | пять человек могут писать параллельно |
| 10 | **CI собирает `:app` на каждом PR** | `main` всегда рабочая |

---

## 3. Карта модулей

```
        :app                    :demo:<функция>
          │                            │
          └─────────────┬──────────────┘
                        ▼
                 ┌─────────────┐
                 │ :feature:*  │  auth, chats, conversation, voice
                 └──────┬──────┘
                        ▼
        ┌──────────────────────────────────────────────┐
        │ :core:domain         :core:designsystem      │
        │ :core:common         :core:data              │
        │ :core:testing  ← только тесты и демо         │
        └──────────────────────────────────────────────┘
```

Правила стрелок (проверяются на ревью и в CI):

- `:app` может зависеть от **всех** модулей — он их собирает.
- `:feature:*` может зависеть только от `:core:*`.
- `:feature:*` **не может** зависеть от другого `:feature:*`.
- `:core:*` не зависит от `:feature:*` и от `:app`.
- `:core:domain` — чистый Kotlin: без Android, Firebase и Room. В моделях — `String`, `Long`, `Flow`, а не `Uri`, `Context`, `FirebaseUser`.

Если функции нужно поговорить друг с другом, есть только два легальных способа:

1. **Через интерфейс из `:core:domain`**, который передаёт `:app`.
2. **Через callback навигации** (`onOpenChat`, `onRecordVoice`), который передаёт `:app`.

Прямой импорт `import ...feature.chats...` из `:feature:voice` — ошибка архитектуры.

`:demo:*` зависит от одной `:feature:*` и `:core:*`. Демо не зависит от другого демо и не зависит от `:app`.

---

## 4. Роли и владение модулями

Буквы — роли, а не имена. Имена вписываются на первом занятии.

| Кто | Зона | Модули и файлы | Индивидуальный демо-модуль |
|---|---|---|---|
| **L — Lead / Design & Motion** | каркас, навигация, DI, дизайн-система, анимации, релиз, CI | `:app`, `:core:designsystem`, `:core:common`, `:core:testing`, `:core:data/firebase`, `settings.gradle.kts`, `libs.versions.toml`, `.github/` | `:demo:design` |
| **A — Auth** | регистрация, вход, выход, профиль, сессия | `:feature:auth`, `User.kt`, `AuthRepository.kt`, `SessionStore.kt`, `FirebaseSessionStore.kt` | `:demo:auth` |
| **B — Chats** | список чатов, поиск людей, создание чата | `:feature:chats`, `Chat.kt`, `ChatRepository.kt` | `:demo:chats` |
| **C — Messages & Media** | переписка, сообщения в реальном времени, Room, картинки | `:feature:conversation`, `Message.kt`, `Attachment.kt`, `VoiceNote.kt`, `MessageRepository.kt`, `MediaRepository.kt` | `:demo:conversation` |
| **D — Voice** | запись, воспроизведение и расшифровка голосовых | `:feature:voice`, `Transcription.kt`, `VoiceRepository.kt`, `AudioPlayer.kt`, `core:data/media`, `core:designsystem/voice` | `:demo:voice` |

Общие файлы, которые меняет **только L**:

- `settings.gradle.kts`, корневой `build.gradle.kts`, `gradle/libs.versions.toml`;
- `app/src/main/java/.../navigation/AppNavHost.kt`;
- `app/src/main/java/.../di/AppContainer.kt`;
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`;
- `.github/workflows/ci.yml`.

Если A–D нужна новая библиотека или новый модуль — пишут L, он добавляет одной строкой. Так не бывает пяти одновременных правок в одном Gradle-файле.

---

## 5. Структура репозитория (стандартный Android-проект)

```
messenger/
├── .github/
│   ├── CODEOWNERS
│   ├── PULL_REQUEST_TEMPLATE.md
│   ├── ISSUE_TEMPLATE/task.md
│   └── workflows/ci.yml
│
├── app/                                  [L]
│   ├── build.gradle.kts
│   ├── google-services.json
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/sozvezdie/messenger/
│       │   │   ├── MessengerApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── di/AppContainer.kt
│       │   │   └── navigation/
│       │   │       ├── AppNavHost.kt
│       │   │       └── StartDestination.kt
│       │   └── res/
│       │       ├── values/strings.xml
│       │       ├── values/themes.xml
│       │       └── mipmap-*/…
│       ├── test/java/…                  smoke-тесты сборки
│       └── androidTest/java/…           end-to-end сценарий
│
├── core/
│   ├── common/                           [L]
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/messenger/core/common/
│   │       ├── result/AppResult.kt
│   │       ├── coroutine/DispatcherProvider.kt
│   │       ├── validation/Validators.kt
│   │       └── time/TimeFormatter.kt
│   │
│   ├── domain/                           контракты, чистый Kotlin
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/messenger/core/domain/
│   │       ├── model/
│   │       │   ├── User.kt                 [A]
│   │       │   ├── Chat.kt                 [B]
│   │       │   ├── Message.kt              [C]
│   │       │   ├── Attachment.kt           [C]
│   │       │   ├── VoiceNote.kt            [C]  часть Message
│   │       │   └── Transcription.kt        [D]
│   │       ├── repository/
│   │       │   ├── AuthRepository.kt       [A]
│   │       │   ├── ChatRepository.kt       [B]
│   │       │   ├── MessageRepository.kt    [C]
│   │       │   ├── MediaRepository.kt      [C]
│   │       │   ├── AudioPlayer.kt          [D]
│   │       │   └── VoiceRepository.kt      [D]
│   │       ├── session/SessionStore.kt     [A]
│   │       └── error/AppError.kt           [L]
│   │
│   ├── data/                             инфраструктура [L; session/ — A, media/ — D]
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/messenger/core/data/
│   │       ├── firebase/FirebaseServices.kt   [L]
│   │       ├── session/FirebaseSessionStore.kt [A]
│   │       └── media/AndroidAudioPlayer.kt      [D]
│   │
│   ├── designsystem/                     [L; voice/ — D]
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/messenger/core/designsystem/
│   │       ├── theme/Color.kt
│   │       ├── theme/Type.kt
│   │       ├── theme/Shape.kt
│   │       ├── theme/MessengerTheme.kt
│   │       ├── component/Avatar.kt
│   │       ├── component/PrimaryButton.kt
│   │       ├── component/LoadingBox.kt
│   │       ├── component/ErrorBox.kt
│   │       ├── motion/MotionSpec.kt         [L]
│   │       ├── motion/ScreenTransitions.kt  [L]
│   │       ├── motion/MessageAnimations.kt  [L]
│   │       └── voice/VoiceBubble.kt         [D]
│   │
│   └── testing/                          [L]
│       ├── build.gradle.kts
│       └── src/main/java/com/sozvezdie/messenger/core/testing/
│           ├── FakeAuthRepository.kt
│           ├── FakeChatRepository.kt
│           ├── FakeMessageRepository.kt
│           ├── FakeVoiceRepository.kt
│           ├── FakeSessionStore.kt
│           └── TestData.kt
│
├── feature/
│   ├── auth/                             [A]
│   ├── chats/                            [B]
│   ├── conversation/                     [C]
│   └── voice/                            [D]
│
├── demo/                                 необязательно, но удобно для защиты
│   ├── auth-demo/                        [A]
│   ├── chats-demo/                       [B]
│   ├── conversation-demo/                [C]
│   ├── voice-demo/                       [D]
│   └── design-demo/                      [L]
│
├── gradle/
│   ├── libs.versions.toml                [L]
│   └── wrapper/…
├── build.gradle.kts                      [L]
├── settings.gradle.kts                   [L]
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .editorconfig
├── .gitattributes
├── .gitignore
├── README.md
└── docs/
    ├── sonya.md                          ← этот документ
    ├── 01-pravila-komandy.md
    ├── 02-arhitektura-proekta.md
    ├── 03-roli-i-zadachi.md
    └── …
```

Каждый `:feature:*` — это отдельный Gradle-модуль со своим `build.gradle.kts`, манифестом, ресурсами и тестами. `:demo:*` — отдельные application-модули. Всё это стандартные каталоги Android: `src/main/java`, `src/main/res`, `src/test/java`, `src/androidTest/java`.

В квадратных скобках — владелец файла или пакета. У `:core:data` и `:core:designsystem` владелец модуля — L, но пакеты `session/`, `media/`, `voice/` принадлежат A и D: L не меняет их без договорённости, а A и D не трогают `build.gradle.kts` модуля.

---

## 6. Что лежит внутри каждой функции

### 6.1. Общий шаблон

Любая функция устроена одинаково — по этому шаблону легко добавить шестую:

```
feature/<имя>/
├── build.gradle.kts
├── consumer-rules.pro
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/sozvezdie/messenger/feature/<имя>/
    │   │   ├── api/                  ← ПУБЛИЧНОЕ. Только это видят другие
    │   │   │   ├── <Имя>Routes.kt
    │   │   │   ├── <Имя>Dependencies.kt
    │   │   │   └── <Имя>Navigation.kt
    │   │   ├── ui/                   ← экраны и ViewModel
    │   │   │   ├── <Экран>Screen.kt
    │   │   │   ├── <Экран>ViewModel.kt
    │   │   │   └── <Экран>UiState.kt
    │   │   ├── domain/               ← use case только этой функции
    │   │   └── data/                 ← реализация репозитория
    │   └── res/
    │       └── values/strings.xml    ← строки с префиксом <имя>_
    ├── test/java/…                   ← unit-тесты ViewModel и use case
    └── androidTest/java/…            ← Compose-тесты экрана
```

Три правила:

1. **Всё, что не лежит в `api/`, считается внутренним.** Другие модули не имеют права импортировать `ui/`, `domain/`, `data/`.
2. **Строки ресурсов имеют префикс**: `auth_login_title`, `voice_record_hint`. Иначе ресурсы двух модулей сольются в один `R` и перекроют друг друга.
3. **Разрешения — в манифесте модуля.** Удалили `:feature:voice` — вместе с ним ушло `RECORD_AUDIO`.

### 6.2. `:feature:auth` — A

```
feature/auth/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/sozvezdie/messenger/feature/auth/
    │   │   ├── api/
    │   │   │   ├── AuthRoutes.kt              LOGIN, REGISTER
    │   │   │   ├── AuthDependencies.kt        AuthRepository
    │   │   │   └── AuthNavigation.kt          authGraph(...)
    │   │   ├── ui/login/
    │   │   │   ├── LoginScreen.kt
    │   │   │   ├── LoginViewModel.kt
    │   │   │   └── LoginUiState.kt
    │   │   ├── ui/register/
    │   │   │   ├── RegisterScreen.kt
    │   │   │   ├── RegisterViewModel.kt
    │   │   │   └── RegisterUiState.kt
    │   │   ├── domain/
    │   │   │   ├── LoginUseCase.kt
    │   │   │   ├── RegisterUseCase.kt
    │   │   │   └── ValidateCredentialsUseCase.kt
    │   │   └── data/
    │   │       ├── FirebaseAuthRepository.kt
    │   │       └── UserMapper.kt
    │   └── res/values/strings.xml
    ├── test/java/…/LoginViewModelTest.kt
    └── androidTest/java/…/LoginScreenTest.kt
```

### 6.3. `:feature:chats` — B

```
feature/chats/
└── src/main/java/com/sozvezdie/messenger/feature/chats/
    ├── api/
    │   ├── ChatsRoutes.kt                     LIST, NEW_CHAT
    │   ├── ChatsDependencies.kt               ChatRepository, SessionStore
    │   └── ChatsNavigation.kt                 chatsGraph(..., onOpenChat)
    ├── ui/list/
    │   ├── ChatsScreen.kt
    │   ├── ChatsViewModel.kt
    │   ├── ChatsUiState.kt
    │   └── ChatRow.kt
    ├── ui/newchat/
    │   ├── NewChatScreen.kt
    │   ├── NewChatViewModel.kt
    │   └── UserSearchRow.kt
    ├── domain/
    │   ├── ObserveChatsUseCase.kt
    │   ├── SearchUsersUseCase.kt
    │   └── CreateChatUseCase.kt
    └── data/
        ├── FirebaseChatRepository.kt
        └── ChatMapper.kt
```

### 6.4. `:feature:conversation` — C

```
feature/conversation/
└── src/main/java/com/sozvezdie/messenger/feature/conversation/
    ├── api/
    │   ├── ConversationRoutes.kt              PATTERN = "conversation/{chatId}"
    │   ├── ConversationDependencies.kt        MessageRepository, MediaRepository,
    │   │                                       SessionStore, AudioPlayer
    │   └── ConversationNavigation.kt          conversationGraph(..., onRecordVoice)
    ├── ui/
    │   ├── ConversationScreen.kt
    │   ├── ConversationViewModel.kt
    │   ├── ConversationUiState.kt
    │   ├── MessageList.kt
    │   ├── MessageInput.kt
    │   └── component/
    │       ├── TextMessageBubble.kt
    │       ├── ImageMessageBubble.kt
    │       └── VoiceMessageBubble.kt          использует VoiceBubble из core
    ├── domain/
    │   ├── ObserveMessagesUseCase.kt
    │   ├── SendTextUseCase.kt
    │   ├── SendImageUseCase.kt
    │   └── MarkAsReadUseCase.kt
    └── data/
        ├── FirebaseMessageRepository.kt
        ├── FirebaseMediaRepository.kt
        ├── MessageMapper.kt
        └── local/
            ├── AppDatabase.kt
            ├── MessageDao.kt
            ├── MessageEntity.kt
            └── MessageLocalMapper.kt
```

Room живёт **внутри** `:feature:conversation`: только эта функция работает с локальной историей. При удалении функции удаляется и база.

### 6.5. `:feature:voice` — D

```
feature/voice/
├── src/main/
│   ├── AndroidManifest.xml                    RECORD_AUDIO
│   └── java/com/sozvezdie/messenger/feature/voice/
│       ├── api/
│       │   ├── VoiceRoutes.kt                 RECORD = "voice/{chatId}"
│       │   ├── VoiceDependencies.kt           MessageRepository, VoiceRepository,
│       │   │                                   SessionStore
│       │   └── VoiceNavigation.kt             voiceGraph(..., onSent)
│       ├── ui/
│       │   ├── VoiceRecordScreen.kt
│       │   ├── VoiceViewModel.kt
│       │   ├── VoiceUiState.kt
│       │   ├── Waveform.kt
│       │   └── TranscriptPanel.kt
│       ├── domain/
│       │   ├── RecordVoiceUseCase.kt
│       │   ├── TranscribeVoiceUseCase.kt
│       │   └── SendVoiceUseCase.kt
│       └── data/
│           ├── AndroidAudioRecorder.kt        пишет файл в cacheDir
│           ├── SpeechTranscriber.kt           Android SpeechRecognizer / внешний API
│           └── FirebaseVoiceRepository.kt     Storage + MessageRepository
└── src/test/java/…/VoiceViewModelTest.kt
```

Расшифровка голоса — **внутри** модуля voice. Никакой другой модуль не знает, как она устроена. Наружу торчит только `VoiceRepository` из `:core:domain`.

---

## 7. Публичный контракт функции

Публичный API — это 3–4 файла в `api/`. Всё остальное можно переписывать, не заглядывая к соседям.

### 7.1. Маршруты

```kotlin
// :feature:chats/api/ChatsRoutes.kt
object ChatsRoutes {
    const val LIST = "chats"
    const val NEW_CHAT = "chats/new"
}
```

```kotlin
// :feature:conversation/api/ConversationRoutes.kt
object ConversationRoutes {
    const val ARG_CHAT_ID = "chatId"
    const val PATTERN = "conversation/{chatId}"

    fun chat(chatId: String) = "conversation/$chatId"
}
```

### 7.2. Зависимости

Зависимости — только интерфейсы из `:core:domain`. Никаких `FirebaseAuth` и `FirebaseDatabase` в публичном API: иначе функция перестанет быть заменяемой.

```kotlin
// :feature:auth/api/AuthDependencies.kt
class AuthDependencies(
    val repository: AuthRepository,
)
```

```kotlin
// :feature:voice/api/VoiceDependencies.kt
class VoiceDependencies(
    val messages: MessageRepository,
    val voice: VoiceRepository,
    val session: SessionStore,
)
```

### 7.3. Навигационный граф

Функция сама описывает свои экраны и аргументы. `:app` только подключает граф.

```kotlin
// :feature:chats/api/ChatsNavigation.kt
fun NavGraphBuilder.chatsGraph(
    navController: NavHostController,
    dependencies: ChatsDependencies,
    onOpenChat: (chatId: String) -> Unit,
    onLogout: () -> Unit,
) {
    composable(ChatsRoutes.LIST) {
        ChatsScreen(
            viewModel = chatsViewModel(dependencies),
            onChatClick = onOpenChat,
            onNewChatClick = { navController.navigate(ChatsRoutes.NEW_CHAT) },
            onLogout = onLogout,
        )
    }
    composable(ChatsRoutes.NEW_CHAT) {
        NewChatScreen(
            viewModel = newChatViewModel(dependencies),
            onCreated = { chatId -> onOpenChat(chatId) },
            onBack = { navController.popBackStack() },
        )
    }
}
```

Важно: `chatsGraph` **не знает** маршрут переписки. Он вызывает `onOpenChat(chatId)`. Куда идти — решает `:app`. Поэтому `:feature:chats` не импортирует `:feature:conversation`.

### 7.4. Фабрики по умолчанию

Чтобы `:app` не создавал Firebase-репозитории вручную, функция даёт фабрику. Она лежит в модуле функции и берёт инфраструктуру из `:core:data` **внутри себя**. В подписи фабрики — только доменные интерфейсы, никаких `FirebaseAuth`/`FirebaseServices`.

```kotlin
// :feature:auth/api/AuthDependenciesFactory.kt
fun defaultAuthDependencies(): AuthDependencies =
    AuthDependencies(
        repository = FirebaseAuthRepository(),   // внутри берёт FirebaseServices.default()
    )
```

```kotlin
// :feature:voice/api/VoiceDependenciesFactory.kt
fun defaultVoiceDependencies(
    context: Context,
    messages: MessageRepository,
    session: SessionStore,
): VoiceDependencies = VoiceDependencies(
    messages = messages,
    voice = FirebaseVoiceRepository(context),     // внутри берёт Firebase Storage
    session = session,
)
```

`FirebaseAuthRepository`, `FirebaseVoiceRepository` остаются `internal`. Наружу видна только фабрика. Для тестов и демо `AuthDependencies`/`VoiceDependencies` создаются напрямую с `Fake*`-репозиториями — Firebase вообще не нужен.

---

## 8. `:app` — единственное место сборки

### 8.1. `AppContainer`: кто что создаёт

`AppContainer` — это ручной DI. Он живёт в `:app`, создаётся один раз в `MessengerApp` и передаётся в `MainActivity`.

```kotlin
// app/src/main/java/com/sozvezdie/messenger/di/AppContainer.kt
class AppContainer(context: Context) {

    private val session: SessionStore = FirebaseSessionStore()

    val auth: AuthDependencies =
        defaultAuthDependencies()

    val chats: ChatsDependencies =
        defaultChatsDependencies(session)

    val conversation: ConversationDependencies =
        defaultConversationDependencies(context, session)

    // Голос получает тот же MessageRepository, что и переписка.
    // Именно поэтому отправленное голосовое сразу появляется в чате.
    val voice: VoiceDependencies =
        defaultVoiceDependencies(
            context = context,
            messages = conversation.messages,
            session = session,
        )
}
```

Ключевой момент: `:feature:voice` не импортирует `:feature:conversation`. Он получает `MessageRepository` — интерфейс из `:core:domain`. Связывает их `:app`.

### 8.2. `AppNavHost`: одна строка на функцию

```kotlin
// app/src/main/java/com/sozvezdie/messenger/navigation/AppNavHost.kt
@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = AuthRoutes.LOGIN,
    ) {
        authGraph(
            navController = nav,
            dependencies = container.auth,
            onLoggedIn = {
                nav.navigate(ChatsRoutes.LIST) {
                    popUpTo(AuthRoutes.LOGIN) { inclusive = true }
                }
            },
        )

        chatsGraph(
            navController = nav,
            dependencies = container.chats,
            onOpenChat = { chatId ->
                nav.navigate(ConversationRoutes.chat(chatId))
            },
            onLogout = {
                nav.navigate(AuthRoutes.LOGIN) { popUpTo(0) }
            },
        )

        conversationGraph(
            navController = nav,
            dependencies = container.conversation,
            onBack = { nav.popBackStack() },
            onRecordVoice = { chatId ->
                nav.navigate(VoiceRoutes.record(chatId))
            },
        )

        voiceGraph(
            navController = nav,
            dependencies = container.voice,
            onSent = { nav.popBackStack() },
        )
    }
}
```

Этот файл меняется **только тимлидом** и только когда:

- добавляется или удаляется целая функция;
- меняется порядок экранов;
- добавляется deep link.

Внутри функций навигация своя: A добавляет экраны в `authGraph`, B — в `chatsGraph`. `AppNavHost` при этом не трогается.

### 8.3. `MainActivity` и `MessengerApp`

```kotlin
// MessengerApp.kt
class MessengerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MessengerApp).container
        setContent {
            MessengerTheme {
                AppNavHost(container)
            }
        }
    }
}
```

---

## 9. Gradle: как выглядит модуль

### 9.1. `settings.gradle.kts` (только L)

```kotlin
rootProject.name = "messenger"

include(":app")

include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:testing")

include(":feature:auth")
include(":feature:chats")
include(":feature:conversation")
include(":feature:voice")

// Демо-приложения для индивидуальной защиты.
include(":demo:auth")
include(":demo:chats")
include(":demo:conversation")
include(":demo:voice")
include(":demo:design")
```

### 9.2. `:feature:auth/build.gradle.kts` (владелец A)

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sozvezdie.messenger.feature.auth"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // api — потому что типы из domain входят в публичный API модуля
    api(project(":core:domain"))

    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Почему `api(project(":core:domain"))`, а не `implementation`: в `AuthDependencies` и `authGraph` используются типы `AuthRepository`, `User`. Если объявить `implementation`, приложение не увидит эти типы при компиляции и получит ошибку «cannot access class». Всё, что попадает в публичную подпись, должно быть `api`; всё внутреннее — `implementation`.

### 9.3. `app/build.gradle.kts` (только L)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.sozvezdie.messenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sozvezdie.messenger"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:auth"))
    implementation(project(":feature:chats"))
    implementation(project(":feature:conversation"))
    implementation(project(":feature:voice"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
}
```

`google-services.json` лежит в `app/`. Модули функций не хранят ключи Firebase: инфраструктуру они получают через `:core:data`.

### 9.4. Шаблон `build.gradle.kts` для остальных функций

A–D копируют блок из 9.2 и меняют три вещи:

| Что | На что менять |
|---|---|
| `namespace` | `com.sozvezdie.messenger.feature.<имя>` |
| зависимости Firebase | только те, что реально нужны функции |
| имя модуля в `include(...)` | добавляет L одной строкой в `settings.gradle.kts` |

Версии библиотек — в `gradle/libs.versions.toml`, его меняет только L. Если нужна новая библиотека: сообщение в чат → L добавляет версию → владелец добавляет `implementation(libs.…)` в свой `build.gradle.kts`. Конфликта нет: у каждого модуля свой файл сборки.

### 9.5. Манифесты

`app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".MessengerApp"
        android:theme="@style/Theme.Messenger">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`feature/voice/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
</manifest>
```

Манифест функции **сливается** с манифестом приложения при сборке. Удалили модуль voice из `settings.gradle.kts` и из зависимостей `:app` — разрешение на микрофон исчезло из итогового APK.

---

## 10. Как оставить только свою функцию (индивидуальный проект)

### 10.1. Что копировать

Вместо основного `:app` можно взять готовый `:demo:<роль>` — тогда основной `:app` не нужен. В таблице указан минимальный набор.

| Роль | Обязательные модули | Что удалить |
|---|---|---|
| **L — Design & Motion** | `:demo:design`, `:core:designsystem`, `:core:common` | основной `:app`, все `:feature:*`, `:core:data`, `:core:testing` |
| **A — Auth** | `:app` или `:demo:auth`, `:feature:auth`, `:core:common`, `:core:domain`, `:core:data`, `:core:designsystem`, `:core:testing` | `:feature:chats`, `:feature:conversation`, `:feature:voice` |
| **B — Chats** | `:app` или `:demo:chats`, `:feature:chats`, `:core:common`, `:core:domain`, `:core:data`, `:core:designsystem`, `:core:testing` | `:feature:auth`, `:feature:conversation`, `:feature:voice` |
| **C — Conversation** | `:app` или `:demo:conversation`, `:feature:conversation`, `:core:common`, `:core:domain`, `:core:data`, `:core:designsystem`, `:core:testing` | `:feature:auth`, `:feature:chats`, `:feature:voice` |
| **D — Voice** | `:app` или `:demo:voice`, `:feature:voice`, `:core:common`, `:core:domain`, `:core:data`, `:core:designsystem`, `:core:testing` | `:feature:auth`, `:feature:chats`, `:feature:conversation` |

`app` при выносе заменяется на минимальный: `MainActivity` + `MessengerTheme` + один вызов графа. Для voice это выглядит так:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val voice = VoiceDependencies(
            messages = FakeMessageRepository(),
            voice = FakeVoiceRepository(),
            session = FakeSessionStore(),
        )

        setContent {
            MessengerTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = VoiceRoutes.record("demo")) {
                    voiceGraph(
                        navController = nav,
                        dependencies = voice,
                        onSent = { /* показать результат */ },
                    )
                }
            }
        }
    }
}
```

Никаких чужих экранов, чатов и авторизации. Только voice + его зависимости.

### 10.2. Два способа выноса

**Способ 1 — папка `demo/` (рекомендую).** В основном репозитории уже лежат `:demo:voice`, `:demo:auth` и т. д. Это отдельные application-модули, которые зависят только от своей функции и `:core:*`. На защите достаточно открыть Android Studio, выбрать конфигурацию `voice-demo`, собрать APK и показать. Ничего копировать не нужно.

```
demo/voice-demo/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    └── java/com/sozvezdie/messenger/demo/voice/MainActivity.kt
```

```kotlin
// demo/voice-demo/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sozvezdie.messenger.demo.voice"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sozvezdie.messenger.demo.voice"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":feature:voice"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:testing"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
}
```

Важно: если демо работает на реальном Firebase, у него свой `applicationId`, поэтому нужен отдельный `google-services.json`. Для защиты проще демо на `Fake*`-репозиториях — тогда Firebase и интернет вообще не нужны, и демо не упадёт на сцене. Реальный Firebase показывается в основном `:app`.

**Способ 2 — новый репозиторий.** Скопировать `:feature:voice` и нужные `:core:*` в чистый Android-проект, добавить их в `settings.gradle.kts`, заменить `MainActivity`, положить `google-services.json`. Подходит, если индивидуальный проект обязан быть отдельным репозиторием. Минус — копия разъедется с общей кодовой базой; поэтому основной вариант — `demo/`.

### 10.3. Удаление функции из общего приложения

Порядок (делает L в одном PR):

1. Удалить `include(":feature:voice")` из `settings.gradle.kts`.
2. Удалить `implementation(project(":feature:voice"))` из `app/build.gradle.kts`.
3. Удалить `voiceGraph(...)` из `AppNavHost.kt` и `voice` из `AppContainer.kt`.
4. Удалить папку `feature/voice/`.
5. Удалить из `:core:domain` `VoiceRepository.kt` и `Transcription.kt`, если на них больше никто не ссылается. `VoiceNote.kt` **остаётся**: это часть `Message`, её использует переписка. `AudioPlayer.kt` тоже остаётся — он нужен для прослушивания уже полученных голосовых.
6. Собрать `:app`. Если сборка зелёная — всё, других следов нет.

Ни один другой `:feature:*` не сломается: они не импортировали voice.

---

## 11. Как пять человек работают в Git

### 11.1. Ветки

Одна задача — одна ветка от свежей `main`:

| Ветка | Кто | Что |
|---|---|---|
| `feature/auth-login` | A | экран и логика входа |
| `feature/auth-register` | A | регистрация |
| `feature/chats-list` | B | список чатов |
| `feature/chats-search` | B | поиск людей |
| `feature/conversation-ui` | C | экран переписки |
| `feature/conversation-room` | C | локальная история |
| `feature/voice-record` | D | запись голосового |
| `feature/voice-transcription` | D | расшифровка |
| `feature/design-motion` | L | анимации и переходы |
| `chore/ci` | L | CI |

Правила:

- Ветка живёт **не дольше 2 дней**, PR — **до ~400 строк**. Долгая ветка = большой конфликт.
- Перед PR: `git switch main && git pull`, затем `git merge main` в свою ветку.
- В `main` напрямую не пушим. Мержит L после ревью (или автор после Approve).
- Ревьюер — любой, кроме автора, по кругу: A → B → C → D → A, L → любой.

### 11.2. Почему конфликтов почти не будет

| Что меняет участник | Где это лежит | Пересечение с другими |
|---|---|---|
| экраны, ViewModel, use case, репозиторий | своя папка `feature/<имя>/` | нет |
| `build.gradle.kts` своей функции | свой модуль | нет |
| строки ресурсов | `feature/<имя>/src/main/res` | нет (уникальный префикс) |
| разрешения | манифест своего модуля | нет |
| тесты | `src/test` своего модуля | нет |
| маршруты | `api/<Имя>Routes.kt` своего модуля | нет |

Реально общие файлы — только шесть, и все они у L:

1. `settings.gradle.kts`
2. корневой `build.gradle.kts`
3. `gradle/libs.versions.toml`
4. `app/src/main/java/.../navigation/AppNavHost.kt`
5. `app/src/main/java/.../di/AppContainer.kt`
6. `app/build.gradle.kts`

Плюс **контракты** в `:core:domain`: их меняют владельцы моделей, но каждое изменение — отдельный маленький PR с пометкой `contract` и уведомлением в чат.

### 11.3. Порядок работы на первом занятии

1. L создаёт каркас: все модули, пустые `api/`, заглушки экранов, `AppNavHost`, `AppContainer`. Это один PR `chore/skeleton`.
2. Все делают `git switch main && git pull`.
3. A–D параллельно заменяют заглушки своими экранами. Каждый в своей ветке.
4. L мержит PR по одному, CI проверяет сборку.
5. Каждые 2 дня — короткий созвон: кто что делает, где блокер.

Заглушки важны: пока интерфейсы пустые, никто не ждёт другого. Уже на первом занятии приложение запускается и ходит по экранам.

### 11.4. CI: что проверяется на каждом PR

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - name: Сборка приложения
        run: ./gradlew :app:assembleDebug
      - name: Unit-тесты
        run: ./gradlew test
      - name: Функции не зависят друг от друга
        run: |
          if grep -R "project(\":feature:" feature/*/build.gradle.kts ; then
            echo "Функция не должна зависеть от другой функции"
            exit 1
          fi
```

Если CI красный — PR не мержим. Это и есть техническая защита правила «`main` всегда собирается».

### 11.5. CODEOWNERS

`.github/CODEOWNERS` назначает ревьюеров автоматически:

```
*                                                   @lead

/app/                                               @lead
/settings.gradle.kts                                @lead
/gradle/libs.versions.toml                          @lead
/.github/                                           @lead

/core/designsystem/                                 @lead
/core/common/                                       @lead
/core/data/firebase/                                @lead
/core/designsystem/voice/                           @voice
/core/data/media/                                   @voice
/core/data/session/                                 @auth

/core/domain/model/User.kt                          @auth
/core/domain/model/Chat.kt                          @chats
/core/domain/model/Message.kt                       @messages
/core/domain/model/VoiceNote.kt                     @messages
/core/domain/repository/AuthRepository.kt           @auth
/core/domain/repository/ChatRepository.kt           @chats
/core/domain/repository/MessageRepository.kt        @messages
/core/domain/repository/AudioPlayer.kt              @voice
/core/domain/repository/VoiceRepository.kt          @voice

/feature/auth/                                      @auth
/feature/chats/                                     @chats
/feature/conversation/                              @messages
/feature/voice/                                     @voice
```

Ники заменяются на реальные GitHub-логины. Пока они не заменены, CODEOWNERS не работает — это первое, что делает L. Текущий `.github/CODEOWNERS` описывает старую одномодульную схему; при выборе этого варианта его заменяют целиком.

---

## 12. Поток данных на примере голосового сообщения

Это главный пример того, как две функции работают вместе, не импортируя друг друга.

```
D: VoiceRecordScreen
      │ нажали «записать»
      ▼
D: VoiceViewModel
      │
      ▼
D: RecordVoiceUseCase ──► AndroidAudioRecorder ──► файл в cacheDir
      │
      ▼
D: TranscribeVoiceUseCase ──► SpeechTranscriber ──► текст
      │
      ▼
D: SendVoiceUseCase
      │ 1. загрузить файл в Firebase Storage
      │ 2. получить downloadUrl
      ▼
C: MessageRepository (интерфейс из :core:domain)
      │
      ▼
C: FirebaseMessageRepository
      │ пишет в Realtime Database и Room
      ▼
C: ConversationViewModel
      │ слушает observeMessages(chatId)
      ▼
C: ConversationScreen
      │ показывает VoiceMessageBubble
      ▼
D: VoiceBubble (в :core:designsystem/voice)
      │ играет через AudioPlayer (интерфейс из :core:domain)
      ▼
D: AndroidAudioPlayer (в :core:data/media)
```

Что здесь важно:

- D **не знает** про экран переписки. Он вызывает `MessageRepository`.
- C **не знает** про запись и расшифровку. Он получает готовое `Message` с `Attachment.Voice`.
- `:app` связывает их, передав один и тот же `MessageRepository` в оба модуля.
- `VoiceBubble` лежит в дизайн-системе, потому что это общий UI-компонент; владелец — D, но тема и анимации — L.

Такую же схему можно нарисовать для картинок (C), авторизации (A) и списка чатов (B).

---

## 13. План по занятиям

| Занятие | L (лид + дизайн) | A (Auth) | B (Chats) | C (Messages) | D (Voice) |
|---|---|---|---|---|---|
| **0. До занятия** | создать репозиторий, модули, `settings.gradle.kts`, CI | — | — | — | — |
| **1** | каркас, тема, навигация, заглушки всех экранов, `AppContainer` | заглушка входа/регистрации | заглушка списка чатов | заглушка переписки | заглушка записи |
| **2** | дизайн-система, базовые анимации, ревью | Firebase Auth: вход | список чатов на fake | UI переписки, схема Room | запись и воспроизведение |
| **3** | переходы между экранами, motion-токены | регистрация, выход, ошибки | поиск людей, создание чата | отправка/приём текста, Room | расшифровка голоса |
| **4** | интеграция, подключение реального Firebase | профиль, сессия | правки по ревью | картинки, офлайн-история | отправка голосового в чат |
| **5** | сборка APK, CI, проверка демо | тесты, демо `auth-demo` | тесты, демо `chats-demo` | тесты, демо `conversation-demo` | тесты, демо `voice-demo` |
| **6** | защита: архитектура, Git, дизайн | защита: авторизация | защита: чаты | защита: переписка | защита: голос и расшифровка |

Этапы 2–5 идут параллельно. Именно для этого на занятии 1 создаются заглушки и контракты: никто не ждёт, пока другой закончит свой экран.

---

## 14. Риски и как их снять

| Риск | Почему возникает | Что делаем |
|---|---|---|
| **Много модулей — долгая настройка** | 10–15 `build.gradle.kts` | L делает каркас один раз по шаблону; дальше модули копируются |
| **Долгая сборка** | Gradle собирает все модули | на PR собирается `:app`; отдельный модуль можно собрать `./gradlew :feature:voice:assembleDebug` |
| **Соблазн импортировать чужую функцию** | так быстрее | CI-проверка + ревью; связь только через `:core:domain` или callback |
| **Конфликт ресурсов** | одинаковые имена строк | префикс `<функция>_` у каждого модуля |
| **Разные версии библиотек** | каждый правит свой build-файл | версии только в `libs.versions.toml`, меняет L |
| **Сломанный контракт** | A поменял `AuthRepository`, B не заметил | PR с меткой `contract`, уведомление в чат, сначала мержим контракт, потом код |
| **Демо не собирается** | у демо другой `applicationId` | демо на `Fake*`-репозиториях, без Firebase |
| **«У меня работает, у тебя нет»** | разные версии Android Studio/JDK | зафиксировать JDK 17 и Gradle wrapper в репозитории |
| **Hilt не взлетел** | больше настройки, чем пользы | ручной DI; Hilt можно добавить позже, не меняя структуру |

### Что мы сознательно не делаем

- **Не вводим Hilt** на старте: `AppContainer` проще объяснить и отладить.
- **Не делаем `build-logic`** с convention-плагинами: 10 основных модулей переживут копирование 30 строк. Если модулей станет больше 15 — вернёмся.
- **Не дробим каждый слой в отдельный Gradle-модуль** (`data:auth`, `domain:auth`…): это удвоит число модулей и запутает на защите. Функция — вертикальный срез: UI + use case + repository внутри одного модуля.
- **Не используем динамические фичи** (Dynamic Feature Modules): они нужны для Play Store, а не для учебного проекта.

---

## 15. Чем этот вариант отличается от `docs/02`

| | `docs/02` (один модуль) | Этот документ (multi-module) |
|---|---|---|
| Изоляция функций | папки и договорённости | Gradle-модули, компилятор не даст нарушить |
| Удаление функции | вручную вычищать импорты | удалить модуль и несколько строк в `:app` |
| Индивидуальный проект | копировать папки, чистить код | готовый `:demo:<функция>` |
| Скорость старта | быстрее на 1–2 часа | каркас на 10 модулей |
| Риск конфликтов | средний | низкий |
| Сложность для новичка | ниже | выше, но объяснима |

**Вывод:** если индивидуальная защита каждой части — обязательное условие, выбираем multi-module. Если команда совсем не успевает — можно начать с одного модуля и вынести функции в модули на занятии 3, но это дороже: придётся менять импорты и `build.gradle.kts`. Лучше сделать каркас сразу.

---

## 16. Чек-лист первого занятия

- [ ] Создан репозиторий, все пятеро добавлены в коллабораторы.
- [ ] L заменил ники в `.github/CODEOWNERS`.
- [ ] В `settings.gradle.kts` подключены все `:core:*` и `:feature:*`.
- [ ] В каждом `:feature:*` есть `build.gradle.kts`, манифест, `api/` и заглушка экрана.
- [ ] `:app` запускается и проходит по маршрутам: auth → chats → conversation → voice.
- [ ] `AppContainer` собирает зависимости; на старте экраны работают на заглушках.
- [ ] В `:core:domain` лежат модели и интерфейсы, согласованные командой.
- [ ] CI собирает `:app` и падает, если функция импортирует другую функцию.
- [ ] У каждого участника есть ветка с первой задачей.
- [ ] Договорились о ревью по кругу и ежедневном `git pull`.

---

## 17. Итог

Архитектура держится на трёх вещах:

1. **Gradle-модуль на функцию** — граница, которую видит компилятор.
2. **`:core:domain`** — общий язык: модели и интерфейсы без Firebase.
3. **`:app`** — единственное место, где функции встречаются: навигация и DI.

Пять человек могут работать одновременно, потому что каждый владеет своим модулем. Каждый может уйти со своей частью, потому что его модуль не тянет за собой чужие. А `main` остаётся рабочей, потому что CI не пускает в неё красную сборку.
