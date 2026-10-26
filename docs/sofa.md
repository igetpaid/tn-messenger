# Архитектура: 5 человек, один репозиторий, 5 отдельных проектов потом

> Документ отвечает на три вопроса:
> 1. Как пятерым работать в одном GitHub-репозитории и не мешать друг другу.
> 2. Как выглядит проект на уровне папок и файлов.
> 3. Как потом каждый забирает **только свою часть** и делает из неё индивидуальный проект.

---

## 1. Три требования → три решения

| Требование | Решение |
|---|---|
| Пятеро работают параллельно, без конфликтов | **Один человек = один Gradle-модуль = одна папка.** Разные папки не конфликтуют в Git |
| Потом каждый делает свой индивидуальный проект | Модули **не зависят друг от друга**. Удалил чужую папку — ничего не сломалось |
| Фичи вроде анимаций и голосовых должны встраиваться в общий чат | **Слоты** (`ChatSlots`) — точки расширения, которые чат отдаёт наружу |

Главный принцип: **фича не знает о существовании других фич.** Она знает только про `core` и про слоты.

---

## 2. Дерево проекта

Стандартная структура Android-проекта: `app/`, `core/`, `feature/`, `gradle/`, `docs/`.

```
sozvezdie/
│
├── .github/
│   ├── CODEOWNERS                      ← кто владеет какой папкой
│   ├── pull_request_template.md
│   └── workflows/
│       └── ci.yml                      ← сборка на каждый push
│
├── app/                                ← ОБОЛОЧКА. Владелец: тимлид
│   ├── build.gradle.kts
│   ├── google-services.json            ← ключ Firebase (в репозитории)
│   ├── google-services.json.example
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/sozvezdie/app/
│       │   ├── MainActivity.kt         ← 20 строк: setContent { AppNavHost() }
│       │   ├── navigation/
│       │   │   ├── Routes.kt           ← имена экранов ("auth", "chats", "chat/{chatId}")
│       │   │   ├── AppNavigator.kt     ← toChats(), toChat(id), back(), signOut()
│       │   │   └── AppNavHost.kt       ← ЕДИНСТВЕННОЕ место, где фичи встречаются
│       │   └── plugins/
│       │       └── PluginRegistry.kt   ← список подключённых личных плагинов
│       └── res/
│           ├── values/strings.xml
│           └── values/themes.xml
│
├── core/                               ← ФУНДАМЕНТ. Владелец: тимлид
│   ├── model/                          ← чистые данные, без UI и без Firebase
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/core/model/
│   │       ├── Models.kt               ← UserProfile, Chat, Message
│   │       ├── AppResult.kt            ← Success / Failure
│   │       ├── FirebasePaths.kt        ← строки путей БД и Storage
│   │       └── repository/
│   │           ├── AuthRepository.kt
│   │           ├── ChatsRepository.kt
│   │           ├── MessagesRepository.kt
│   │           └── ProfileRepository.kt
│   │
│   ├── ui/                             ← тема и общие компоненты
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/core/ui/
│   │       ├── theme/
│   │       │   ├── Color.kt
│   │       │   ├── Type.kt
│   │       │   └── Theme.kt
│   │       ├── component/
│   │       │   ├── Avatar.kt
│   │       │   └── StateViews.kt       ← Empty / Error / Loading
│   │       └── slots/
│   │           └── ChatSlots.kt        ← ТОЧКИ РАСШИРЕНИЯ (см. п. 6)
│   │
│   └── media/                          ← картинки: загрузка и показ
│       ├── build.gradle.kts
│       └── src/main/java/com/sozvezdie/core/media/
│           ├── ImageUploader.kt        ← байты → Firebase Storage → URL
│           └── RemoteImage.kt          ← Compose-обёртка над Coil
│
├── feature/                            ← ПО ОДНОМУ МОДУЛЮ НА ЧЕЛОВЕКА
│   │
│   ├── auth/                           ← Участник 1
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/feature/auth/
│   │       ├── AuthScreen.kt           ← публичный API модуля
│   │       ├── AuthViewModel.kt
│   │       ├── AuthUiState.kt
│   │       └── data/
│   │           └── FirebaseAuthRepository.kt
│   │
│   ├── chats/                          ← Участник 2
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/feature/chats/
│   │       ├── ChatsScreen.kt
│   │       ├── ChatsViewModel.kt
│   │       ├── NewChatDialog.kt
│   │       └── data/
│   │           └── FirebaseChatsRepository.kt
│   │
│   ├── chat/                           ← Участник 3
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/feature/chat/
│   │       ├── ChatScreen.kt           ← принимает ChatSlots
│   │       ├── ChatViewModel.kt
│   │       ├── MessageBubble.kt
│   │       └── data/
│   │           ├── FirebaseMessagesRepository.kt
│   │           └── local/
│   │               ├── ChatDatabase.kt
│   │               ├── MessageDao.kt
│   │               └── MessageEntity.kt
│   │
│   ├── attachments/                    ← Участник 4
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/sozvezdie/feature/attachments/
│   │       └── AttachmentsSlot.kt      ← кнопка «Фото» + выбор файла + загрузка
│   │                                     (реализует интерфейс ChatAttachments)
│   │
│   └── profile/                        ← Участник 5
│       ├── build.gradle.kts
│       └── src/main/java/com/sozvezdie/feature/profile/
│           ├── ProfileScreen.kt
│           ├── ProfileViewModel.kt
│           └── data/
│               └── FirebaseProfileRepository.kt
│
├── plugin/                             ← ЛИЧНЫЕ МОДУЛИ (для индивидуальных проектов)
│   └── motion/                         ← пример: анимации
│       ├── build.gradle.kts
│       └── src/main/java/com/sozvezdie/plugin/motion/
│           └── MotionWrapper.kt
│
├── docs/
│   ├── sofa.md                         ← этот файл
│   ├── DATA_CONTRACT.md                ← схема Firebase
│   ├── WORKFLOW.md                     ← git, PR, ревью
│   ├── firebase-rules.json
│   └── firebase-storage-rules.txt
│
├── gradle/
│   ├── libs.versions.toml              ← все версии библиотек в одном месте
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── build.gradle.kts                    ← только объявление плагинов
├── settings.gradle.kts                 ← автоподключение модулей
├── gradle.properties
├── gradlew / gradlew.bat
├── ARCHITECTURE.md
├── TEAM.md
└── README.md
```

**Правило чтения дерева:** если файл лежит в `feature/chat/`, его трогает только участник 3. Если в `core/` — только тимлид и только через RFC.

---

## 3. Карта зависимостей

```
                          ┌──────────┐
                          │   :app   │   знает всех, но не содержит логики
                          └────┬─────┘
        ┌──────────┬───────────┼───────────┬──────────────┐
        ▼          ▼           ▼           ▼              ▼
   :feature:  :feature:   :feature:   :feature:      :feature:
     auth       chats       chat     attachments      profile
        │          │           │           │              │
        └──────────┴───────────┴───────────┴──────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
   :core:model            :core:ui              :core:media
   модели + контракты     тема, компоненты,     загрузка и показ
                          слоты                 картинок

                    :plugin:motion  ──►  :core:ui, :core:model
                    (личный модуль)       (ничего из :feature/*)
```

Разрешённые зависимости:

| Модуль | Может зависеть от |
|---|---|
| `:app` | `:core:*`, `:feature:*`, `:plugin:*` |
| `:feature:X` | `:core:model`, `:core:ui`, `:core:media` |
| `:plugin:X` | `:core:model`, `:core:ui` |
| `:core:*` | только от других `:core:*` |

**Запрещено:** `:feature:*` → `:feature:*` и `:plugin:*` → `:feature:*`.

Именно это правило и даёт «удалил чужое — ничего не сломалось». Проверяется оно не на словах: в `:app/build.gradle.kts` зависимости на фичи добавляются **только если папка модуля существует**:

```kotlin
// app/build.gradle.kts
val featureModules = listOf("auth", "chats", "chat", "attachments", "profile")
val pluginModules = listOf("motion")

featureModules.filter { findProject(":feature:$it") != null }
    .forEach { implementation(project(":feature:$it")) }

pluginModules.filter { findProject(":plugin:$it") != null }
    .forEach { implementation(project(":plugin:$it")) }
```

А `settings.gradle.kts` подхватывает модули сам — добавлять `include(...)` руками не нужно:

```kotlin
// settings.gradle.kts — модуль появляется вместе с папкой и исчезает вместе с ней
listOf("core", "feature", "plugin").forEach { group ->
    file(group).listFiles()
        ?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
        ?.sortedBy { it.name }
        ?.forEach { include(":$group:${it.name}") }
}
```

---

## 4. Что внутри модуля (на примере `auth`)

Каждый feature-модуль устроен одинаково — это упрощает жизнь:

```
feature/auth/
├── build.gradle.kts                      ← зависимости только на core
└── src/main/java/com/sozvezdie/feature/auth/
    ├── AuthScreen.kt                     ← ПУБЛИЧНЫЙ API, одна @Composable
    ├── AuthViewModel.kt                  ← internal, наружу не выходит
    ├── AuthUiState.kt                    ← internal
    └── data/
        └── FirebaseAuthRepository.kt     ← internal, единственное место с Firebase
```

`AuthScreen.kt` — всё, что модуль отдаёт наружу:

```kotlin
package com.sozvezdie.feature.auth

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,   // не «перейди на экран чатов», а «пользователь вошёл»
) {
    val viewModel = featureViewModel { AuthViewModel(FirebaseAuthRepository()) }
    AuthContent(state = viewModel.state, ...)
}
```

Внутри модуля — MVVM, как в любом стандартном Android-проекте:

```
Screen (Compose)  →  ViewModel (состояние)  →  Repository (Firebase / Room)
     ↑                                            │
     └──────────── Flow / State ──────────────────┘
```

`Screen` не знает слов `Firebase`, `DataSnapshot`, `Room`. `Repository` не знает про Compose. Это позволяет тестировать логику без Android и менять бэкенд в одном файле.

---

## 5. Пять человек и их папки

| № | Зона в общем проекте | Папка | Что делает | Индивидуальный проект (пример) |
|---|---|---|---|---|
| 1 | Аутентификация | `feature/auth/` | вход, регистрация, сессия, создание `users/{uid}` | «Безопасная авторизация в мессенджере»: + биометрия, + восстановление пароля |
| 2 | Список чатов | `feature/chats/` | список диалогов, создание чата | «Архитектура списков на Compose»: + поиск, + архив, + пиннинг |
| 3 | Переписка | `feature/chat/` | сообщения, realtime, Room, офлайн | «Realtime + офлайн-кэш»: + пагинация, + статусы прочтения |
| 4 | Вложения | `feature/attachments/` | выбор картинки, загрузка, прогресс | «Медиа в мобильном приложении»: + сжатие, + видео, + документы |
| 5 | Профиль | `feature/profile/` | имя, аватар, поиск людей, выход | «Профиль и социальный граф»: + контакты, + блокировки |
| — | Тимлид | `app/`, `core/` | навигация, контракты, ревью, релиз | — |

**Личный модуль** (`plugin/*`) каждый заводит себе сам — это его «фишка» для индивидуальной защиты. Именно там появляются анимации, голосовые, расшифровка, темы оформления. Пример такого модуля — `plugin/motion/`, он уже есть в проекте.

Личные модули **не обязательны для работы приложения**: удалили — приложение работает как раньше, просто без фишки. Это ключевое свойство для индивидуальных проектов.

---

## 6. Точки расширения: как анимации и голосовые попадают в чат

Проблема: экран чата принадлежит участнику 3. Участник с анимациями хочет менять вид пузырей, участник с голосовыми — добавить кнопку микрофона. Если они полезут в `ChatScreen.kt` — конфликт и поломка.

Решение: **слоты**. Чат объявляет «дырки», в которые можно вставить что угодно, и не знает, кто их заполнит.

`core/ui/slots/ChatSlots.kt`:

```kotlin
package com.sozvezdie.core.ui.slots

/** Что можно подключить к экрану чата, не меняя сам экран чата. */
data class ChatSlots(
    val attachments: ChatAttachments? = null,       // кнопка «прикрепить»
    val messageWrapper: MessageWrapper? = null,     // обёртка пузыря (анимации)
    val inputTrailing: (@Composable RowScope.() -> Unit)? = null, // кнопки справа в поле ввода
)

/** Кнопка прикрепления файла. Реализует участник 4. */
interface ChatAttachments {
    @Composable
    fun AttachButton(chatId: String, onUploaded: (url: String) -> Unit)
}

/** Обёртка вокруг пузыря сообщения. Реализует тот, кто делает анимации. */
interface MessageWrapper {
    @Composable
    fun Wrap(message: Message, content: @Composable () -> Unit)
}
```

Чат их просто вызывает:

```kotlin
// feature/chat/ChatScreen.kt
@Composable
fun ChatScreen(
    chatId: String,
    title: String,
    onBack: () -> Unit,
    slots: ChatSlots = ChatSlots(),   // по умолчанию пусто — чат работает и без плагинов
)

// внутри:
slots.messageWrapper?.Wrap(message) {
    MessageBubble(message, isMine)
} ?: MessageBubble(message, isMine)

// в поле ввода:
slots.attachments?.AttachButton(chatId, onUploaded = viewModel::sendImage)
```

Собирает всё `:app` — единственное место, которое знает про всех:

```kotlin
// app/plugins/PluginRegistry.kt
val slots = ChatSlots(
    attachments = AttachmentsSlot(),        // ← участник 4
    messageWrapper = MotionWrapper(),      // ← plugin/motion
)
```

Удалить `plugin/motion` → убрать одну строку `messageWrapper = ...` → чат работает без анимаций.
Удалить `feature/attachments` → убрать одну строку `attachments = ...` → чат работает без кнопки «прикрепить».

**Никаких правок в чужом коде.** Это и есть механизм «добавлять свои функции, не ломая чужие».

---

## 7. Как добавить новую функцию (полный рецепт)

Пример: голосовые сообщения с расшифровкой → `plugin/voice/`.

### Шаг 1. Ветка

```bash
git checkout main && git pull
git checkout -b feature/voice-messages
```

### Шаг 2. Скопировать шаблон модуля

```bash
cp -r plugin/motion plugin/voice
```

### Шаг 3. Переименовать пакет и namespace

`plugin/voice/build.gradle.kts`:

```kotlin
android {
    namespace = "com.sozvezdie.plugin.voice"
}
```

Папка с исходниками:

```
plugin/voice/src/main/java/com/sozvezdie/plugin/voice/
├── VoiceRecorder.kt
├── VoiceWrapper.kt          ← реализует MessageWrapper или поставляет кнопку
└── data/VoiceRecorderImpl.kt
```

### Шаг 4. Реализовать точку расширения

```kotlin
package com.sozvezdie.plugin.voice

/** Встраивается в поле ввода чата через ChatSlots.inputTrailing */
@Composable
fun VoiceButton(onRecorded: (url: String) -> Unit) { ... }
```

### Шаг 5. Подключить в одном месте — `PluginRegistry.kt`

```kotlin
inputTrailing = { VoiceButton(onRecorded = viewModel::sendVoice) }
```

### Шаг 6. Если нужны новые данные — согласовать

Новое поле в `Message` (`voiceUrl`, `durationMs`, `transcript`) добавляется **со значениями по умолчанию** и описывается в `docs/DATA_CONTRACT.md`. Переименовывать существующие поля нельзя.

### Шаг 7. PR

Один PR = один модуль + одна строка в `PluginRegistry.kt`. Такой PR ревьюится за 5 минут и не конфликтует ни с кем.

---

## 8. Как собрать индивидуальный проект (10 минут)

Задача: остался только мой экран, остальное удалено, всё собирается.

### Вариант А — «моя фича как есть» (проще всего)

```bash
# 1. отдельная ветка или форк
git checkout -b solo/chat

# 2. удалить чужие фичи
rm -rf feature/auth feature/chats feature/attachments feature/profile
rm -rf plugin/motion

# 3. поправить стартовый экран
#    app/src/main/java/com/sozvezdie/app/navigation/AppNavHost.kt
#    удалить из NavHost чужие блоки composable { ... }
```

В `AppNavHost.kt` остаётся один экран. Например, для чата:

```kotlin
val navController = rememberNavController()

NavHost(navController = navController, startDestination = "chat/demo") {
    composable("chat/demo") {
        ChatScreen(
            chatId = "demo",
            title = "Демо-чат",
            onBack = {},
        )
    }
}
```

```bash
# 4. собрать
./gradlew assembleDebug
```

`settings.gradle.kts` и `app/build.gradle.kts` правятся сами — они подхватывают только существующие папки.

### Вариант Б — «моя фича + демо-данные без Firebase»

Если для защиты не нужен живой Firebase, в свой репозиторий добавляется реализация с тестовыми данными:

```
feature/chat/data/FakeMessagesRepository.kt   ← возвращает список сообщений из памяти
```

и в `ChatViewModel` подставляется она вместо Firebase-версии. Одна строка — весь бэкенд заменён. Это возможно только потому, что экран зависит от `MessagesRepository` (интерфейс в `core:model`), а не от Firebase напрямую.

### Что остаётся в индивидуальном проекте

```
app/                    ← оболочка (можно урезать AppNavHost)
core/model/             ← обязательно
core/ui/                ← обязательно (тема)
core/media/             ← если используешь картинки
feature/моя/            ← твоя часть
docs/                   ← README + описание
```

Всё это — 4–5 папок. Именно поэтому `core` делается маленьким: он должен целиком влезть в любой индивидуальный проект.

---

## 9. Как пять ветвей сливаются без конфликтов

| Файл | Кто меняет | Почему не конфликтует |
|---|---|---|
| `feature/*`, `plugin/*` | каждый свою | разные папки — Git не сравнивает их между собой |
| `settings.gradle.kts` | **никто** | модули подключаются автоматически по папкам |
| `app/build.gradle.kts` | тимлид | список фич меняется только при добавлении/удалении модуля |
| `app/.../AppNavHost.kt` | каждый +1 блок | разные участки файла; Git сливает построчно |
| `app/.../PluginRegistry.kt` | каждый +1 строку | разные строки списка |
| `gradle/libs.versions.toml` | все | новая библиотека — в конец секции |
| `core/**` | тимлид | защищено CODEOWNERS |
| `docs/DATA_CONTRACT.md` | все | новая строка в таблицу полей |

Реальный риск конфликта — **только `libs.versions.toml`**, если двое одновременно добавят разные библиотеки. Лечится за 10 секунд: оставить обе строки.

### Порядок слияния

```
main  ←  feature/auth   (PR #1, ревью: тимлид)
      ←  feature/chats  (PR #2, ревью: тимлид)
      ←  feature/chat   (PR #3, ревью: участник 2 + тимлид)
      ...
```

- Ветка живёт ≤ 2 дней.
- PR содержит один модуль и одну строку подключения.
- Перед PR: `git merge main` + `./gradlew assembleDebug`.
- Красный CI → не мержим.

---

## 10. Проверка «архитектура не нарушена»

Три быстрые проверки перед каждым PR:

**1. Нет ли импортов из чужой фичи**

```bash
grep -rn "com.sozvezdie.feature" feature/chat/src | grep -v "feature.chat"
grep -rn "com.sozvezdie.plugin"  feature/
```

Пусто → хорошо. Есть вывод → нарушение.

**2. Нет ли Firebase в UI-слое**

```bash
grep -rn "com.google.firebase" feature/*/src --include="*Screen.kt"
```

Пусто → хорошо.

**3. Собирается ли проект**

```bash
./gradlew assembleDebug
```

CI делает это автоматически на каждый push.

---

## 11. Чего делать нельзя

| Нельзя | Почему |
|---|---|
| Импортировать `feature:chat` из `feature:chats` | удалят чат — сломается список чатов |
| Править `ChatScreen.kt`, чтобы «добавить кнопку» | конфликт с владельцем; для этого есть `ChatSlots` |
| Класть общий код в `feature/` | общий код живёт только в `core/` |
| Добавлять поле в `Message` без дефолта | Firebase упадёт на чтении старых записей |
| Создавать `object AppState` с изменяемым состоянием | глобальный стейт = скрытая связь между модулями |
| Коммитить `build/`, `.idea/`, `local.properties` | мусор в репозитории, конфликты |
| Делать ветку на 2 недели | мерж превратится в разбор конфликтов |

---

## 12. Чек-лист «мой модуль готов»

- [ ] Модуль лежит в `feature/<имя>/` или `plugin/<имя>/`.
- [ ] Наружу торчит одна `@Composable`-функция с колбэками.
- [ ] Ни одного `import` из другой фичи.
- [ ] Firestore/Firebase — только в `data/`.
- [ ] Экран работает, если рядом нет других фич.
- [ ] Есть состояние загрузки, пустое состояние и текст ошибки.
- [ ] `./gradlew assembleDebug` проходит.
- [ ] PR меняет только свою папку + одну строку подключения.
- [ ] После удаления папки остальные модули собираются (проверить один раз перед защитой).

Последний пункт — самый важный. Он и есть ответ на вопрос «а что если я останусь один».
