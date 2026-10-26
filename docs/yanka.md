# Архитектура мессенджера «Созвездие» для команды из пяти человек

> Документ описывает **целевую** архитектуру проекта: как разделить Android-клиент и сервер на модули,
> как каждому участнику работать в своей ветке, как собрать индивидуальный APK со своей функцией
> и как удалить чужую функцию, не сломав приложение.

Текущий MVP уже работает: Kotlin + Jetpack Compose, Room, Retrofit/OkHttp, WebSocket, Node.js 24 +
Express + `ws` + SQLite. Его не нужно переписывать с нуля. Нужно постепенно выделить из монолитного
`:app` вертикальные модули и зафиксировать границы так, чтобы пять человек могли работать параллельно.

---

## 1. Задача и ограничения

| Ограничение | Как учитываем |
| --- | --- |
| Пять человек, один GitHub-репозиторий | У каждого своя долгоживущая ветка и свой набор модулей |
| Каждый представляет свою часть как отдельный проект | Отдельный product flavor и отдельный APK на каждую роль |
| Функции должны легко удаляться | Фича — отдельный Gradle-модуль; связи только через контракты |
| Нельзя ломать общий проект | `main` защищён, контракты меняются отдельным PR, CI собирает все flavor |
| Стандартная структура Android-проекта | `android/app`, `android/core/*`, `android/feature/*`, `build-logic`, version catalog |
| Текущий серверный протокол уже используется | Не меняем существующие REST/WS-контракты; расширяем обратно совместимо |
| Android 6.0+ (minSdk 23) | Photo Picker с fallback, desugaring, проверка API перед вызовом |
| Firebase рекомендован в ТЗ, но MVP уже на своём сервере | Не переписываем проект; Firebase подключается адаптером, если потребуется |

**Главный принцип:** это не микросервисы и не набор отдельных приложений. Это **модульный монолит**:
один репозиторий, один сервер, одно приложение, но внутри — независимые вертикальные срезы.

---

## 2. Главное решение

```
┌──────────────────────────────── :app ────────────────────────────────┐
│ AppShell, AppNavHost, DI, flavors, выбор стартового экрана            │
└──────┬──────────────┬───────────────┬──────────────┬─────────────────┘
       │              │               │              │
 :feature:auth  :feature:messaging  :feature:media  :feature:voice
       │              │               │              │
       └──────────────┴───────┬───────┴──────────────┘
                              │
                    :feature:motion / :feature:showcase
                              │
        :core:contracts  :core:model  :core:ui  :core:design
        :core:network    :core:database  :core:datastore  :core:navigation
```

Три жёстких правила:

1. **`:app` — только composition root.** Он собирает приложение из фич, хранит общую навигацию,
   тему и flavor-обвязку. В `:app` нет бизнес-логики чатов, авторизации или загрузки файлов.
2. **Фича не зависит от другой фичи.** Только `feature:* → core:*`. Если одной фиче нужна другая,
   создаётся интерфейс в `:core:contracts` (или `:core:ui` для UI-слотов), а реализация подставляется
   в `:app` или через Hilt multibinding.
3. **Одна зона ответственности = один владелец = одна ветка = один flavor = свой набор серверных
   папок.** Это делает границы очевидными и позволяет удалить чужую функцию без археологии.
   У design-зоны две фичи (`motion`, `showcase`) и один flavor `design`.

Почему не динамические feature-модули, не отдельные репозитории и не микросервисы:

- динамические модули усложняют сборку, установку и тестирование, а выигрыш для учебного проекта
  минимален;
- отдельные репозитории заставят дублировать `core` и синхронизировать контракты вручную;
- микросервисы для команды из пяти человек и SQLite — лишняя инфраструктура;
- модульный монолит даёт те же границы, но остаётся простым в запуске и защите.

---

## 3. Роли и зоны ответственности

Имена GitHub-аккаунтов ниже — плейсхолдеры, их нужно заменить на реальные в `CODEOWNERS`.

| # | Роль | Ветка | Android-модули | Сервер | Индивидуальная сборка | Тема защиты |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Auth & Security | `auth` | `:feature:auth`, часть `:core:datastore` | `features/auth`, `features/users` | `authDebug` | Вход, регистрация, JWT, refresh, безопасное хранение сессии |
| 2 | Messaging & Realtime | `messaging` | `:feature:messaging` | `features/chats`, `features/messages`, `core/realtime` | `messagingDebug` | Чаты, сообщения, offline-first, WebSocket, статусы |
| 3 | Media & Files | `media` | `:feature:media` | `features/files` | `mediaDebug` | Выбор, сжатие, загрузка, кэш и просмотр медиа |
| 4 | Voice & Transcription | `voice` | `:feature:voice` | `features/voice` | `voiceDebug` | Запись, плеер, waveform, расшифровка речи |
| 5 | Design System & Motion | `design` | `:core:design`, `:feature:motion`, `:feature:showcase`, `:app` shell | — | `designDebug` | Токены, компоненты, тёмная тема, анимации, доступность |

### 3.1. Auth & Security

**Владеет:** экранами входа и регистрации, сессией, токенами, refresh-логикой, выходом, опциональной
биометрией, серверными `/api/auth/*` и профилем пользователя.

**Не делает:** список чатов, отправку сообщений, загрузку файлов. После успешного входа auth-фича
только сообщает об этом через `SessionProvider` и навигацию.

**Что показывает на защите:** полный цикл «регистрация → вход → перезапуск приложения → refresh →
выход», обработку неверного пароля, истечения токена и отсутствия сети.

### 3.2. Messaging & Realtime

**Владеет:** списком чатов, экраном диалога, текстовыми сообщениями, историей, синхронизацией,
офлайн-очередью `outbox`, WebSocket-соединением, статусами `sent/delivered/read`, индикатором
«печатает…», присутствием, поиском по чатам.

**Не делает:** выбор медиа, запись голоса, тему оформления. Для вложений и голосовых он использует
слоты `ComposerAction` и `MessagePartRenderer`, которые поставляют другие фичи.

**Что показывает:** два клиента, обмен в реальном времени, разрыв сети и автоматическую досылку
сообщения без дубликата, восстановление истории после перезапуска.

### 3.3. Media & Files

**Владеет:** выбором изображения/файла, камерой, сжатием, загрузкой с прогрессом, кэшем файлов,
просмотром изображений, рендером вложений в сообщении, открытием файла во внешнем приложении,
серверной загрузкой и отдачей файлов.

**Не делает:** список чатов и отправку текста. Чтобы отправить вложение, сначала загружает его и
получает `attachmentId`, затем вызывает контракт `MessageSender`, который реализует messaging (или
demo-host в индивидуальной сборке).

**Что показывает:** выбор фото, прогресс загрузки, отмену, повтор, кэш, просмотр на весь экран,
работу с файлами разных MIME-типов.

### 3.4. Voice & Transcription

**Владеет:** записью с микрофона, waveform, плеером, длительностью, отправкой аудио, расшифровкой
речи, отображением транскрипта, повторной попыткой расшифровки.

**Не делает:** текстовый чат и медиа-галерею. Аудио отправляется как обычное вложение
(`kind=file`, `mimeType=audio/*`), а расшифровка хранится отдельно и приходит дополнительным
событием.

**Что показывает:** запись, паузу, отмену, воспроизведение, скорость, waveform, расшифровку и
поведение при отсутствии согласия на обработку речи.

### 3.5. Design System & Motion

**Владеет:** `:core:design` (цвета, типографика, отступы, формы, тени, motion-токены),
`:feature:motion` (переходы, анимации списков, shared element, настройка «уменьшить движение»),
`:feature:showcase` (галерея компонентов и токенов), оболочкой `:app` (`AppShell`, `AppNavHost`).

**Не делает:** бизнес-логику фич. Компоненты принимают состояние и колбэки, но не ходят в сеть.

**Что показывает:** галерею компонентов, светлую/тёмную тему, анимации переходов, доступность,
адаптивность и визуальные регрессионные тесты.

> **Важно:** владелец `:app` — design/motion, но любое изменение `:app`, затрагивающее навигацию или
> контракты, требует ревью владельца затронутой фичи. Это защита от «тихой» поломки чужой сборки.

---

## 4. Структура репозитория

```
.
├─ .github/
│  ├─ CODEOWNERS
│  ├─ pull_request_template.md
│  └─ workflows/
│     ├─ android.yml
│     └─ server.yml
├─ android/
│  ├─ settings.gradle.kts
│  ├─ build.gradle.kts
│  ├─ gradle.properties
│  ├─ gradle/
│  │  ├─ libs.versions.toml          # единый version catalog
│  │  └─ wrapper/
│  ├─ build-logic/                   # convention-плагины, чтобы не дублировать build.gradle.kts
│  │  ├─ settings.gradle.kts
│  │  ├─ build.gradle.kts
│  │  └─ convention/
│  │     ├─ build.gradle.kts
│  │     └─ src/main/kotlin/
│  │        ├─ AndroidApplicationConventionPlugin.kt
│  │        ├─ AndroidLibraryConventionPlugin.kt
│  │        ├─ AndroidFeatureConventionPlugin.kt
│  │        ├─ AndroidComposeConventionPlugin.kt
│  │        ├─ AndroidHiltConventionPlugin.kt
│  │        └─ AndroidRoomConventionPlugin.kt
│  ├─ app/                           # composition root, только сборка и shell
│  │  ├─ build.gradle.kts
│  │  ├─ proguard-rules.pro
│  │  └─ src/
│  │     ├─ main/
│  │     │  ├─ AndroidManifest.xml
│  │     │  ├─ kotlin/com/sozvezdie/messenger/
│  │     │  │  ├─ MessengerApp.kt
│  │     │  │  ├─ MainActivity.kt
│  │     │  │  ├─ shell/AppShell.kt
│  │     │  │  ├─ shell/AppNavHost.kt
│  │     │  │  ├─ shell/StartDestination.kt
│  │     │  │  ├─ shell/DemoPlaceholderScreen.kt
│  │     │  │  ├─ di/AppModule.kt
│  │     │  │  └─ di/NetworkModule.kt
│  │     │  └─ res/
│  │     ├─ full/kotlin/.../di/FullBindings.kt
│  │     ├─ auth/kotlin/.../di/AuthBindings.kt
│  │     ├─ messaging/kotlin/.../di/MessagingBindings.kt
│  │     ├─ media/kotlin/.../di/MediaBindings.kt
│  │     ├─ voice/kotlin/.../di/VoiceBindings.kt
│  │     └─ design/kotlin/.../di/DesignBindings.kt
│  ├─ core/
│  │  ├─ common/                     # Result, Dispatchers, time, UUID, extensions
│  │  ├─ model/                      # общие модели: User, Chat, Message, MessagePart, Attachment,
│  │  │                             # Session, Transcript и ID
│  │  ├─ contracts/                  # порты и их DTO: SessionProvider, MessageSender, ChatHost, Transcriber
│  │  ├─ network/                    # OkHttp, Retrofit, JSON, auth-interceptor, error mapping
│  │  ├─ datastore/                  # DataStore: токены, адрес сервера, настройки
│  │  ├─ database/                   # DatabaseFactory, конвертеры, транзакции; без сущностей фич
│  │  ├─ ui/                         # общие UI-примитивы и слоты: MessagePartRenderer, ComposerAction
│  │  ├─ design/                     # тема, цвета, типографика, формы, motion-токены
│  │  ├─ navigation/                 # FeatureEntry, HomeDestination, маршруты shell
│  │  ├─ testing/                    # fakes, test rules, dispatchers, test data
│  │  └─ demo/                       # demo-session и demo-host для индивидуальных сборок
│  └─ feature/
│     ├─ auth/
│     ├─ messaging/
│     ├─ media/
│     ├─ voice/
│     ├─ motion/
│     └─ showcase/
├─ server/
│  ├─ package.json
│  ├─ .env.example
│  ├─ scripts/seed.js
│  ├─ test/                          # общие helpers и сквозные тесты
│  └─ src/
│     ├─ index.js                    # entrypoint
│     ├─ app.js                      # composition root: подключает только включённые фичи
│     ├─ server.js
│     ├─ config.js
│     ├─ core/
│     │  ├─ db/                      # connection, migrations runner, transaction
│     │  ├─ http/                    # async handler, errors, validation
│     │  ├─ auth/                    # jwt, password, tokens
│     │  ├─ realtime/                # WebSocket hub, connection registry
│     │  ├─ storage/                 # файловое хранилище
│     │  └─ logger.js
│     └─ features/
│        ├─ auth/
│        ├─ users/
│        ├─ chats/
│        ├─ messages/
│        ├─ files/
│        └─ voice/
├─ docs/
│  ├─ yanka.md                       # этот документ
│  ├─ architecture.md                # текущая архитектура MVP
│  ├─ protocol.md                    # единственный источник истины по REST/WS
│  ├─ schema.md                      # схема БД
│  ├─ features/
│  │  ├─ auth.md
│  │  ├─ messaging.md
│  │  ├─ media.md
│  │  ├─ voice.md
│  │  └─ design-motion.md
│  ├─ manual-test-checklist.md
│  ├─ demo-script.md
│  └─ defense.md
├─ scripts/
├─ artifacts/
├─ README.md
└─ LICENSE
```

### 4.1. Что здесь стандартно для Android

- Gradle-корень — `android/`, а не корень репозитория: сервер и Android живут рядом, но собираются
  независимо.
- `:app` — единственный `com.android.application`; все остальные модули — библиотеки.
- `core/*` — переиспользуемая инфраструктура без знания о конкретных экранах.
- `feature/*` — вертикальные срезы: UI, ViewModel, domain, data и DI в одном модуле.
- `build-logic` — convention-плагины, чтобы каждый `build.gradle.kts` был коротким и одинаковым.
- `gradle/libs.versions.toml` — единственное место версий зависимостей.

---

## 5. Android-модули и карта зависимостей

| Модуль | Тип | Владелец | Назначение | Может зависеть от |
| --- | --- | --- | --- | --- |
| `:app` | application | design/motion + ревью команды | Shell, навигация, DI, flavors | все `core:*`, включённые `feature:*` |
| `:core:common` | JVM/Android library | общий | `Result`, диспетчеры, утилиты | ничего |
| `:core:model` | JVM library | общий | Доменные модели и ID | `:core:common` |
| `:core:contracts` | JVM/Android library | общий | Порты между фичами | `:core:model`, `:core:common` |
| `:core:network` | Android library | messaging | HTTP-клиент, JSON, интерсепторы | `:core:common`, `:core:model`, `:core:contracts` |
| `:core:datastore` | Android library | auth | DataStore-обёртки | `:core:common`, `:core:model` |
| `:core:database` | Android library | messaging | `DatabaseFactory`, конвертеры | `:core:common` |
| `:core:ui` | Android library | design/motion | UI-примитивы, слоты рендера | `:core:design`, `:core:model` |
| `:core:design` | Android library | design/motion | Тема и токены | `:core:common` |
| `:core:navigation` | Android library | design/motion | `FeatureEntry`, `HomeDestination` | `:core:model`, `:core:ui` |
| `:core:testing` | Android library | общий | Fakes, test rules | `:core:model`, `:core:contracts` |
| `:core:demo` | Android library | общий | Demo-session и demo-host | `:core:contracts`, `:core:model`, `:core:ui` |
| `:feature:auth` | feature | auth | Вход, регистрация, сессия | `core:*` |
| `:feature:messaging` | feature | messaging | Чаты, сообщения, sync, WS | `core:*` |
| `:feature:media` | feature | media | Медиа, файлы, загрузка | `core:*` |
| `:feature:voice` | feature | voice | Запись, плеер, расшифровка | `core:*` |
| `:feature:motion` | feature | design/motion | Анимации и переходы | `core:*` |
| `:feature:showcase` | feature | design/motion | Галерея дизайн-системы | `core:*` |

**Запрещено:** `:feature:a` → `:feature:b` в любом виде — `implementation`, `api`, `testImplementation`,
`debugImplementation`. Проверяется в CI отдельной задачей (см. раздел 15).

Разрешённые направления:

```
:app  ──────────────►  :feature:*  ──────────────►  :core:*
  │                                                    │
  └────────────────────────────────────────────────────┘
```

`core`-модули тоже не должны образовывать циклы. Например, `:core:ui` может зависеть от
`:core:design`, но `:core:design` не должен зависеть от `:core:ui`.

---

## 6. Шаблон фичи

Каждая фича устроена по одному шаблону. Ниже — пример `:feature:voice`; у остальных меняются имена
и набор core-модулей: auth не подключает Room, motion и showcase — network/database.

```
android/feature/voice/
├─ build.gradle.kts
├─ README.md                         # что умеет, как собрать flavor, скриншоты
├─ src/main/AndroidManifest.xml      # RECORD_AUDIO; исчезает вместе с модулем
├─ src/main/kotlin/com/sozvezdie/messenger/feature/voice/
│  ├─ VoiceFeatureEntry.kt           # вклад в навигацию и home
│  ├─ di/VoiceModule.kt              # Hilt: репозитории, use case, renderer, composer action
│  ├─ data/
│  │  ├─ local/VoiceDatabase.kt
│  │  ├─ local/VoiceDao.kt
│  │  ├─ local/VoiceDraftEntity.kt
│  │  ├─ local/TranscriptEntity.kt
│  │  ├─ recorder/AndroidVoiceRecorder.kt
│  │  ├─ player/AndroidVoicePlayer.kt
│  │  ├─ remote/VoiceApi.kt
│  │  ├─ remote/VoiceDto.kt
│  │  └─ repository/VoiceRepositoryImpl.kt
│  ├─ domain/
│  │  ├─ model/VoiceDraft.kt
│  │  ├─ repository/VoiceRepository.kt
│  │  └─ usecase/
│  │     ├─ StartRecordingUseCase.kt
│  │     ├─ StopRecordingUseCase.kt
│  │     ├─ SendVoiceMessageUseCase.kt
│  │     ├─ TranscribeVoiceUseCase.kt
│  │     └─ ObserveTranscriptUseCase.kt
│  ├─ ui/
│  │  ├─ composer/VoiceComposerAction.kt
│  │  ├─ recorder/VoiceRecordScreen.kt
│  │  ├─ recorder/VoiceRecordViewModel.kt
│  │  ├─ player/VoiceBubble.kt
│  │  ├─ player/VoicePlayerViewModel.kt
│  │  ├─ transcript/TranscriptSheet.kt
│  │  └─ renderer/VoicePartRenderer.kt
│  └─ navigation/
│     ├─ VoiceRoutes.kt
│     └─ VoiceNavigation.kt
├─ src/main/res/
│  ├─ values/strings.xml             # все строки с префиксом voice_
│  ├─ drawable/ic_voice_*.xml
│  └─ raw/                           # звуки/заглушки, если нужны
├─ src/test/kotlin/.../              # unit-тесты use case и мапперов
└─ src/androidTest/kotlin/.../       # Compose-тесты записи/плеера
```

### 6.1. Правила внутри фичи

1. **Публичным является `*FeatureEntry`, реализации портов, которые связывает `:app`
   (`AuthSessionProvider`, `MessagingMessageSender`, `MessagingChatHost`), вклады в
   Hilt-мультибиндинг и явно документированные точки расширения (например, `MediaPicker`).**
   Остальные классы — `internal`, чтобы другой владелец не начал использовать их напрямую.
   Публичная поверхность фичи — это её «API для сборки», а не весь модуль.
2. **Своя БД — свои миграции.** `VoiceDatabase` живёт в `feature:voice`; никто снаружи не знает
   её схему. Связь с сообщением — по `messageId`, без внешних ключей между БД.
3. **Свои строки — свой префикс.** `voice_`, `media_`, `chat_`, `auth_`, `motion_`, `showcase_`.
   Это исключает конфликты `strings.xml` при мерже.
4. **Свои маршруты — свой префикс.** `voice/record/{chatId}`, `media/viewer/{id}`,
   `chat/{chatId}`. Константы маршрутов лежат в фиче, а не в `:app`.
5. **Свои разрешения — свой manifest.** `RECORD_AUDIO` объявляется в `feature:voice`, `CAMERA` —
   в `feature:media`. Если модуль не подключён, разрешение не попадает в итоговый manifest.
6. **Свои тесты — рядом с кодом.** Unit-тесты не требуют Android-эмулятора; Compose-тесты
   запускаются только для нужного flavor.
7. **README фичи.** Владелец описывает, что реализовано, как собрать `voiceDebug`, какие есть
   ограничения и как проверить за 2–3 минуты.

### 6.2. Пример `build.gradle.kts` фичи

```kotlin
plugins {
    id("messenger.android.feature")
    id("messenger.android.compose")
    id("messenger.android.hilt")
    id("messenger.android.room")   // нет у auth, motion и showcase
}

android {
    namespace = "com.sozvezdie.messenger.feature.voice"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:ui"))
    implementation(project(":core:design"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))   // нет у auth, motion и showcase

    testImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:testing"))
}
```

Для auth `room`-плагин и `:core:database` не нужны: сессия хранится в `:core:datastore`, а не в Room.
Для motion и showcase не нужны ни Room, ни сеть. Шаблон задаёт форму, а не обязывает тащить лишние
зависимости.

Здесь нет ни одной строки `project(":feature:...")`. Это и есть главная проверка изоляции.

---

## 7. Пять фич подробно

### 7.1. `:feature:auth` — вход, регистрация, сессия

**Задача:** дать приложению безопасную сессию и экран входа, не зная ничего о чатах.

```
feature/auth/
├─ AuthFeatureEntry.kt
├─ di/AuthModule.kt
├─ data/
│  ├─ local/SessionStore.kt          # DataStore + Keystore-шифрование
│  ├─ session/AuthSessionProvider.kt # реализация SessionProvider для :app
│  ├─ remote/AuthApi.kt
│  ├─ remote/AuthDto.kt
│  └─ repository/AuthRepositoryImpl.kt
├─ domain/
│  ├─ repository/AuthRepository.kt
│  └─ usecase/
│     ├─ LoginUseCase.kt
│     ├─ RegisterUseCase.kt
│     ├─ RefreshSessionUseCase.kt
│     ├─ LogoutUseCase.kt
│     └─ ObserveSessionUseCase.kt
├─ ui/
│  ├─ login/LoginScreen.kt
│  ├─ login/LoginViewModel.kt
│  ├─ register/RegisterScreen.kt
│  ├─ register/RegisterViewModel.kt
│  ├─ success/AuthSuccessScreen.kt
│  ├─ success/AuthSuccessViewModel.kt
│  └─ common/AuthErrorText.kt
└─ navigation/AuthRoutes.kt
```

`Session` — общая модель в `:core:model`, потому что её видят `SessionProvider` и shell. Фича auth
владеет только способом её получить и сохранить.

**Публичный контракт:** реализует `SessionProvider` (см. раздел 9), кладёт `AuthFeatureEntry` в
набор фич и задаёт `startDestination(session)`: без сессии — `auth/login`, с сессией — `auth/success`.
Именно поэтому standalone-сборка после перезапуска не показывает форму входа повторно. Наружу не
отдаёт ни `AuthApi`, ни `SessionStore`.

**Сервер:** `server/src/features/auth` — `POST /api/auth/register`, `/login`, `/refresh`, `/logout`,
`GET/PATCH /api/me`; `features/users` — поиск пользователей и профиль. Пароли — bcrypt, access —
JWT HS256 на 15 минут, refresh — ротируемая случайная строка, в БД только SHA-256.

**Индивидуальная сборка:** `authDebug` → вход → экран успеха. После успешного логина shell реагирует
на смену `SessionProvider.session` и переходит на `chooseStartDestination`: в `auth` это
`auth/success`, в `full` — чаты. Auth-фича не хардкодит чужой экран. Сервер: `FEATURES=auth,users`.

**Границы:**

- не хранит сообщения и файлы;
- не рисует чаты;
- не знает про WebSocket;
- при отсутствии сети показывает понятную ошибку, а не падает.

**Тесты:** неверный пароль, истёкший access, ротация refresh, выход, перезапуск с валидной сессией,
отсутствие токена в логах.

### 7.2. `:feature:messaging` — чаты, сообщения, реальное время

**Задача:** весь текстовый messaging и offline-first синхронизация.

```
feature/messaging/
├─ MessagingFeatureEntry.kt
├─ di/MessagingModule.kt
├─ data/
│  ├─ local/
│  │  ├─ MessengerDatabase.kt        # основная БД чатов
│  │  ├─ dao/ChatDao.kt
│  │  ├─ dao/MessageDao.kt
│  │  ├─ dao/OutboxDao.kt
│  │  ├─ dao/SyncStateDao.kt
│  │  ├─ entity/ChatEntity.kt
│  │  ├─ entity/MessageEntity.kt
│  │  ├─ entity/OutboxEntity.kt
│  │  └─ entity/SyncStateEntity.kt
│  ├─ remote/
│  │  ├─ ChatApi.kt
│  │  ├─ MessageApi.kt
│  │  ├─ SyncApi.kt
│  │  └─ ws/ChatSocket.kt
│  ├─ mapper/MessageMapper.kt
│  ├─ sender/MessagingMessageSender.kt # реализация MessageSender
│  ├─ host/MessagingChatHost.kt        # реализация ChatHost
│  └─ repository/
│     ├─ ChatRepositoryImpl.kt
│     ├─ MessageRepositoryImpl.kt
│     └─ SyncCoordinator.kt
├─ domain/
│  ├─ repository/ChatRepository.kt
│  └─ usecase/
│     ├─ ObserveChatsUseCase.kt
│     ├─ ObserveMessagesUseCase.kt
│     ├─ SendTextMessageUseCase.kt
│     ├─ SyncMessagesUseCase.kt
│     ├─ MarkChatReadUseCase.kt
│     └─ SetTypingUseCase.kt
├─ ui/
│  ├─ chats/ChatListScreen.kt
│  ├─ chats/ChatListViewModel.kt
│  ├─ chat/ChatScreen.kt
│  ├─ chat/ChatViewModel.kt
│  ├─ composer/MessageComposer.kt
│  ├─ renderer/TextPartRenderer.kt
│  └─ common/MessageStatusIcon.kt
└─ navigation/MessagingRoutes.kt
```

`Chat`, `Message` и `MessagePart` живут в `:core:model`, потому что их форму видят рендеры media/voice
и контракты. Фича messaging владеет их загрузкой, хранением и синхронизацией, но не дублирует модель.

**Публичные контракты:**

- `MessageSender` — им пользуются media и voice, чтобы отправить вложение в текущий чат;
- `ChatHost` — даёт текущий `chatId` и минимальную информацию о чате для standalone-фич;
- `MessagePartRenderer` для текста;
- `HomeDestination` для вкладки «Чаты».

**Сервер:** `features/chats` (список, создание, участники, read), `features/messages` (история,
отправка, идемпотентность, sync), `core/realtime` (WebSocket-хаб, presence, typing).

**Ключевые инварианты, которые нельзя терять при рефакторинге:**

- Room — источник истины для UI; экран не читает сеть напрямую;
- `clientMessageId` + уникальный индекс на сервере = отсутствие дублей;
- `outbox` переживает перезапуск;
- `sinceId` — курсор синхронизации;
- WebSocket переподключается с backoff, а REST-история догоняет пропущенное.

**Индивидуальная сборка:** `messagingDebug` + demo-session. Сервер:
`FEATURES=auth,users,chats,messages,files` и `DEMO_MODE=1` (или seed-данные).

**Границы:** не декодирует изображения и не пишет аудио; не меняет тему; не владеет экраном входа.

**Тесты:** отправка при офлайне, повтор без дубля, порядок сообщений, read/delivered, refresh токена
во время WebSocket-сессии, пагинация истории.

### 7.3. `:feature:media` — изображения, файлы, загрузка

**Задача:** всё, что связано с вложениями, кроме голоса.

```
feature/media/
├─ MediaFeatureEntry.kt
├─ di/MediaModule.kt
├─ data/
│  ├─ local/MediaDatabase.kt
│  ├─ local/MediaDao.kt
│  ├─ local/CachedFileEntity.kt
│  ├─ picker/AndroidMediaPicker.kt
│  ├─ camera/CameraLauncher.kt
│  ├─ compress/ImageCompressor.kt
│  ├─ upload/UploadWorker.kt          # WorkManager, ретраи, прогресс
│  ├─ remote/MediaApi.kt
│  ├─ remote/MediaDto.kt
│  └─ repository/MediaRepositoryImpl.kt
├─ domain/
│  ├─ model/PickedMedia.kt
│  ├─ repository/MediaRepository.kt
│  └─ usecase/
│     ├─ PickImageUseCase.kt
│     ├─ CapturePhotoUseCase.kt
│     ├─ UploadAttachmentUseCase.kt
│     ├─ OpenAttachmentUseCase.kt
│     └─ ObserveUploadUseCase.kt
├─ ui/
│  ├─ composer/AttachComposerAction.kt
│  ├─ picker/MediaPickerSheet.kt
│  ├─ gallery/MediaGalleryScreen.kt
│  ├─ viewer/ImageViewerScreen.kt
│  ├─ renderer/ImagePartRenderer.kt
│  ├─ renderer/FilePartRenderer.kt
│  └─ upload/UploadProgress.kt
└─ navigation/MediaRoutes.kt
```

**Контракты и точки расширения:**

- `ComposerAction` — кнопка «прикрепить» в композере чата (слот из `:core:ui`);
- `MessagePartRenderer` — рендер изображения и файла в пузыре (слот из `:core:ui`);
- `AttachmentOpener` — **внутренний** интерфейс media: открыть файл во внешнем приложении. В core
  не выносим, пока нет второго потребителя;
- `MediaPicker` — публичный интерфейс выбора. Пока его использует только сама media; если аватар в
  auth захочет выбирать фото, интерфейс переезжает в `:core:contracts` отдельным PR (contract-first),
  а реализация остаётся в media;
- `MediaFeatureEntry.startDestination(session)` возвращает `media/gallery`, когда сессия есть
  (в standalone-сборке она есть всегда), — чтобы сборка открывалась на галерее, даже если у неё нет
  вкладки в bottom bar.

**Сервер:** `server/src/features/files` — `POST /api/files` (multipart, лимит размера, белый список
MIME, проверка сигнатуры), `GET /api/files/{id}` (проверка прав участника чата). Метаданные — в
таблице `attachments`, бинарники — на диске.

**Индивидуальная сборка:** `mediaDebug` + demo-host. Показывает галерею/выбор, прогресс, отмену,
повтор, кэш, полноэкранный просмотр. Сервер:
`FEATURES=auth,users,chats,messages,files`.

**Границы:** не рисует список чатов и не отправляет текст; не записывает голос; не хранит токены.

**Технические детали, которые важно не потерять:**

- Photo Picker на API 33+, `ACTION_OPEN_DOCUMENT` на старых API;
- runtime-разрешение `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` только там, где нужно;
- сжатие и поворот по EXIF до загрузки, чтобы не гонять 20 МБ;
- загрузка через `WorkManager` с уникальным именем работы по `clientAttachmentId`;
- кэш на диске + индекс в `MediaDatabase`, чтобы не качать повторно;
- отмена и повтор не должны создавать дубликаты на сервере;
- сначала `UploadAttachmentUseCase` загружает файл и получает `attachmentId`, затем
  `MessageSender.sendAttachment` создаёт сообщение; если приложение закрылось между шагами,
  media повторяет отправку по сохранённому статусу;
- на границе с `MessageSender` используется `OutgoingAttachment` из `:core:contracts`.

**Тесты:** выбор/отмена, лимит размера, неверный MIME, обрыв сети во время загрузки, повтор,
открытие файла без приложения-обработчика, поворот изображения.

### 7.4. `:feature:voice` — голосовые сообщения и расшифровка

**Задача:** записать, отправить, воспроизвести голосовое и показать его расшифровку.

```
feature/voice/
├─ VoiceFeatureEntry.kt
├─ di/VoiceModule.kt
├─ data/
│  ├─ local/VoiceDatabase.kt
│  ├─ local/VoiceDao.kt
│  ├─ local/VoiceDraftEntity.kt
│  ├─ local/TranscriptEntity.kt
│  ├─ recorder/AndroidVoiceRecorder.kt
│  ├─ recorder/WaveformSampler.kt
│  ├─ player/AndroidVoicePlayer.kt
│  ├─ remote/VoiceApi.kt
│  ├─ remote/VoiceDto.kt
│  └─ repository/VoiceRepositoryImpl.kt
├─ domain/
│  ├─ model/VoiceDraft.kt
│  ├─ repository/VoiceRepository.kt
│  └─ usecase/
│     ├─ StartRecordingUseCase.kt
│     ├─ StopRecordingUseCase.kt
│     ├─ CancelRecordingUseCase.kt
│     ├─ SendVoiceMessageUseCase.kt
│     ├─ TranscribeVoiceUseCase.kt
│     └─ ObserveTranscriptUseCase.kt
├─ ui/
│  ├─ composer/VoiceComposerAction.kt
│  ├─ recorder/VoiceRecordScreen.kt
│  ├─ recorder/VoiceRecordViewModel.kt
│  ├─ player/VoiceBubble.kt
│  ├─ player/VoicePlayerViewModel.kt
│  ├─ transcript/TranscriptSheet.kt
│  └─ renderer/VoicePartRenderer.kt
└─ navigation/VoiceRoutes.kt
```

`Transcript` и `TranscriptStatus` — общие модели в `:core:model`: их возвращает порт `Transcriber` и
их показывает пузырь сообщения, даже если voice-модуля нет (тогда просто нет данных).

**Как вписывается в текущий протокол без ломки:**

- аудио отправляется как обычное вложение: `kind=file`, `mimeType=audio/mp4` (или `audio/aac`),
  `attachment.id` уже существует;
- аудио сначала загружается тем же `POST /api/files` и получает `attachmentId`, затем
  `MessageSender` создаёт сообщение; voice не дублирует upload-логику media;
- в `message` добавляется **необязательный** объект `voice`:
  ```json
  {
    "id": 123,
    "kind": "file",
    "attachment": { "id": 55, "mimeType": "audio/mp4", "sizeBytes": 48213 },
    "voice": {
      "durationMs": 7300,
      "waveform": [1, 4, 7, 3, 9],
      "transcript": "Привет, я на месте",
      "transcriptStatus": "done"
    }
  }
  ```
  Старые клиенты просто игнорируют `voice`; новые — рисуют плеер и транскрипт.
- расшифровка асинхронная: `POST /api/voice/transcribe` с `messageId` → `202` → WebSocket-событие
  `voice.transcript` с `messageId`, `status`, `text`. Если voice-фича не установлена, событие просто
  не приходит, а аудио остаётся обычным файлом.

**Публичный контракт:** `ComposerAction`, `MessagePartRenderer`, `Transcriber`;
`VoiceFeatureEntry.startDestination(session)` возвращает `voice/record` при наличии сессии —
для standalone-сборки.

**Провайдер расшифровки:** интерфейс `Transcriber` в `:core:contracts`. Реализации:

- `StubTranscriber` — для тестов и демо, возвращает заготовленный текст;
- `ServerTranscriber` — вызывает сервер (например, Whisper или корпоративный API);
- `OnDeviceTranscriber` — Android `SpeechRecognizer`/ML Kit, если нужна приватность.

Выбор — в DI. Фича voice не знает, какая реализация используется.

**Индивидуальная сборка:** `voiceDebug` + demo-host. Запись → отправка в demo-чат → плеер →
расшифровка. Сервер: `FEATURES=auth,users,chats,messages,files,voice`.

**Границы:** не рисует чат и не заменяет media; не хранит токены; не отправляет аудио без
разрешения пользователя.

**Приватность и UX:**

- разрешение `RECORD_AUDIO` запрашивается в момент нажатия, а не при старте приложения;
- перед отправкой показывается длительность и кнопка отмены;
- расшифровка включается только после явного согласия (настройка/первый раз);
- транскрипт можно удалить, не удаляя аудио;
- при ошибке расшифровки остаётся кнопка «повторить», аудио не теряется;
- запись останавливается при уходе приложения в фон, если это не foreground-service сценарий.

**Тесты:** старт/стоп/отмена, ограничение длительности, потеря фокуса аудио, повторная запись,
отправка при офлайне, ошибка транскрипции, повторная транскрипция без дубля.

### 7.5. `:core:design`, `:feature:motion`, `:feature:showcase` — дизайн и анимации

**Задача:** единая визуальная система, движение и отдельная галерея для защиты дизайна.

```
core/design/src/main/kotlin/com/sozvezdie/messenger/core/design/
├─ theme/MessengerTheme.kt
├─ theme/Color.kt
├─ theme/Type.kt
├─ theme/Shape.kt
├─ theme/Dimens.kt
├─ theme/Motion.kt                  # длительности, easing, reduced-motion
└─ component/
   ├─ MessengerButton.kt
   ├─ MessengerTextField.kt
   ├─ Avatar.kt
   ├─ Badge.kt
   ├─ EmptyState.kt
   ├─ ErrorState.kt
   └─ LoadingState.kt

feature/motion/src/main/kotlin/com/sozvezdie/messenger/feature/motion/
├─ MotionFeatureEntry.kt
├─ di/MotionModule.kt
├─ navigation/MotionRoutes.kt
└─ ui/
   ├─ MotionShowcaseScreen.kt
   ├─ transitions/ScreenTransitions.kt
   ├─ list/AnimatedChatList.kt
   └─ settings/ReduceMotionSetting.kt

feature/showcase/src/main/kotlin/com/sozvezdie/messenger/feature/showcase/
├─ ShowcaseFeatureEntry.kt
├─ di/ShowcaseModule.kt
├─ navigation/ShowcaseRoutes.kt
└─ ui/
   ├─ ShowcaseScreen.kt
   ├─ ComponentsGallery.kt
   ├─ TokensScreen.kt
   └─ MotionScreen.kt
```

**Правила дизайн-системы:**

- фичи не используют «сырые» `Color(0xFF...)`, `fontSize = 17.sp`, `padding(13.dp)` — только токены
  из `:core:design`;
- тёмная тема обязательна; dynamic color — опционально на Android 12+;
- минимальный touch target — 48 dp, контраст текста — не ниже WCAG AA;
- у каждого интерактивного элемента есть `contentDescription` или явная декоративность;
- анимации не дольше 200–300 мс для переходов, учитывают `Settings.Global.ANIMATOR_DURATION_SCALE`
  и настройку «уменьшить движение»;
- `feature:showcase` не входит в `full` release (или входит только в debug), чтобы не тащить
  демо-галерею в продуктовую сборку.

**Индивидуальная сборка:** `designDebug` → галерея компонентов, токены, светлая/тёмная тема,
анимации. Сервер не нужен.

**Тесты:** скриншотные тесты ключевых компонентов (Paparazzi/Roborazzi), Compose-тесты на
кликабельность и contentDescription, проверка тёмной темы.

---

## 8. Core-модули: что в них лежит и чего быть не должно

| Модуль | Что лежит | Чего быть не должно |
| --- | --- | --- |
| `:core:common` | `Result`, `DispatcherProvider`, `Clock`, `IdGenerator`, строковые утилиты | UI, Retrofit, Room, знание фич |
| `:core:model` | `User`, `Chat`, `Message`, `MessagePart`, `Attachment`, `Session`, `Transcript`, `TranscriptStatus`, value-классы ID | Android-контекст, DTO с аннотациями сети |
| `:core:contracts` | Порты `SessionProvider`, `MessageSender`, `ChatHost`, `Transcriber` и их DTO (`OutgoingAttachment`) | Реализации, Compose, доступ к БД |
| `:core:network` | OkHttp, Retrofit, JSON, `AuthInterceptor`, `ApiError` | Экраны, бизнес-правила чатов |
| `:core:datastore` | `SessionDataStore`, `SettingsDataStore`, шифрование | Токены в открытом виде в логах |
| `:core:database` | `DatabaseFactory`, общие конвертеры, `suspendTransaction` | Сущности и DAO конкретных фич |
| `:core:ui` | `MessagePartRenderer`, `ComposerAction`, общие пузыри/состояния | Бизнес-логика, прямые вызовы API |
| `:core:design` | Тема, токены, базовые компоненты | Экраны фич, навигация |
| `:core:navigation` | `FeatureEntry`, `HomeDestination`, общие маршруты shell | Конкретные экраны фич |
| `:core:testing` | Fakes, `TestDispatcherRule`, тестовые данные, Hilt test runner | Продакшен-код |
| `:core:demo` | `DemoSessionProvider`, `DemoMessageSender`, demo-чат, demo-навигация | Попадание в `full` release |

**Правило для `:core:contracts`:** интерфейс должен быть достаточным, чтобы фича-потребитель мог
работать, и минимальным, чтобы не превращать core в «божественный объект». Если контракт нужен
только одной фиче — он живёт в её `domain`, а не в core.

---

## 9. Контракты, слоты и DI

### 9.1. Схема связей

```
                 ┌───────────────────────────┐
                 │        :app shell         │
                 │  собирает Set<FeatureEntry>│
                 └─────────────┬─────────────┘
                               │
     ┌─────────────────────────┼─────────────────────────┐
     │                         │                         │
:feature:auth           :feature:messaging        :feature:media
SessionProvider          MessageSender             ComposerAction
                         ChatHost                  MessagePartRenderer
                         TextPartRenderer
     │                         │                         │
     └─────────────────────────┼─────────────────────────┘
                               │
                    :core:contracts / :core:ui
```

Ключевая идея: фичи не видят классы друг друга. Они видят **интерфейсы** и **слоты**. Реализации
подставляет Hilt в `:app` или в flavor-специфичном binding-модуле.

### 9.2. Основные порты

```kotlin
// :core:contracts
data class OutgoingAttachment(
    val attachmentId: AttachmentId, // уже загружен через POST /api/files
    val mimeType: String,
    val name: String,
    val sizeBytes: Long,
)

interface SessionProvider {
    val session: StateFlow<Session?>
    suspend fun requireAccessToken(): String
}

interface MessageSender {
    suspend fun sendText(chatId: ChatId, text: String)
    suspend fun sendAttachment(
        chatId: ChatId,
        attachment: OutgoingAttachment,
        caption: String? = null,
    )
}

interface ChatHost {
    val currentChatId: StateFlow<ChatId?>
    fun openChat(chatId: ChatId)
}

interface Transcriber {
    suspend fun transcribe(messageId: MessageId): Transcript
    suspend fun observeTranscript(messageId: MessageId): Flow<Transcript?>
}
```

`OutgoingAttachment` описывает **уже загруженный** файл. Загрузку выполняет владелец файла
(media или voice), а `MessageSender` только создаёт сообщение со ссылкой на `attachmentId`.
Так messaging не тащит логику загрузки и не зависит от media.

```kotlin
// :core:ui
interface MessagePartRenderer {
    val id: String
    fun canRender(part: MessagePart): Boolean
    @Composable fun Content(part: MessagePart, host: MessagePartHost, modifier: Modifier)
}

interface MessagePartHost {
    /** Только навигация по строковому маршруту; хост не знает, чей это экран. */
    fun navigate(route: String)
}

interface ComposerAction {
    val id: String
    val order: Int

    /** Действие само рисует кнопку и владеет своим сценарием (picker, запись). */
    @Composable fun Content(host: ComposerHost, modifier: Modifier)
}

interface ComposerHost {
    val chatId: ChatId
    fun navigate(route: String)
    fun dismissComposerMenu()
}
```

`ComposerAction` не просит messaging «открыть медиа»: media/voice сами показывают picker или экран
записи, а messaging лишь размещает слот и передаёт `chatId`. Поэтому добавление или удаление
media/voice не меняет композер чата. Аналогично `MessagePartHost` умеет только `navigate(route)`,
а конкретный маршрут выбирает рендер.

```kotlin
// :core:navigation
interface FeatureEntry {
    val id: String

    /**
     * Стартовый экран этой фичи.
     * session == null: только auth возвращает auth/login, остальные — null.
     * session != null: auth → auth/success, media → media/gallery, voice → voice/record.
     * Если в сборке есть HomeDestination, shell выбирает вкладку, а не это значение.
     */
    fun startDestination(session: Session?): String?

    fun registerGraph(builder: NavGraphBuilder)
}

data class HomeDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val order: Int,
)
```

### 9.3. Hilt: мультибиндинги вместо ручного каталога

Каждая фича добавляет свои вклады в общие наборы. `:app` не перечисляет фичи по именам.

```kotlin
// :core:navigation
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationMultibinds {
    @Multibinds abstract fun entries(): Set<FeatureEntry>
    @Multibinds abstract fun homeDestinations(): Set<HomeDestination>
}

// :core:ui
@Module
@InstallIn(SingletonComponent::class)
abstract class UiMultibinds {
    @Multibinds abstract fun renderers(): Set<MessagePartRenderer>
    @Multibinds abstract fun composerActions(): Set<ComposerAction>
}

// :feature:messaging/di
@Module
@InstallIn(SingletonComponent::class)
object MessagingModule {
    @Provides @IntoSet
    fun featureEntry(): FeatureEntry = MessagingFeatureEntry()

    @Provides @IntoSet
    fun homeDestination(): HomeDestination = HomeDestination(
        route = MessagingRoutes.Chats,
        labelRes = R.string.chat_tab_title,
        icon = Icons.Outlined.Chat,
        order = 10,
    )

    @Provides @IntoSet
    fun textRenderer(): MessagePartRenderer = TextPartRenderer()

    // MessageSender и ChatHost биндятся в :app (см. 9.4), чтобы не было двух реализаций.
}
```

`@Multibinds` нужен, чтобы Hilt умел внедрять пустой `Set`, если фича не подключена. Без него
сборка `designDebug` без messaging может упасть на пустом наборе.

Такой подход даёт главное свойство: **удаление фичи не требует правок в `AppNavHost`/`AppShell`**.
Если модуль не подключён, его `@Provides @IntoSet` просто не попадает в граф. Отдельно остаётся
только flavor-binding для портов (см. 9.4), если фича была их владельцем.

### 9.4. Flavor-специфичные binding-модули

Порты `SessionProvider`, `MessageSender`, `ChatHost` должны иметь ровно одну реализацию в каждой
сборке, где они используются. В `auth` достаточно `SessionProvider`; `media`/`voice`/`messaging`
нужны все три. Для этого в `:app` есть маленькие модули в source set флейвора:

```kotlin
// app/src/full/kotlin/.../di/FullBindings.kt
@Module
@InstallIn(SingletonComponent::class)
interface FullBindings {
    @Binds fun session(impl: AuthSessionProvider): SessionProvider
    @Binds fun sender(impl: MessagingMessageSender): MessageSender
    @Binds fun chatHost(impl: MessagingChatHost): ChatHost
}
```

```kotlin
// app/src/media/kotlin/.../di/MediaBindings.kt
@Module
@InstallIn(SingletonComponent::class)
interface MediaBindings {
    @Binds fun session(impl: DemoSessionProvider): SessionProvider
    @Binds fun sender(impl: DemoMessageSender): MessageSender
    @Binds fun chatHost(impl: DemoChatHost): ChatHost
}
```

```kotlin
// app/src/messaging/kotlin/.../di/MessagingBindings.kt
@Module
@InstallIn(SingletonComponent::class)
interface MessagingBindings {
    @Binds fun session(impl: DemoSessionProvider): SessionProvider
    @Binds fun sender(impl: MessagingMessageSender): MessageSender
    @Binds fun chatHost(impl: MessagingChatHost): ChatHost
}
```

```kotlin
// app/src/auth/kotlin/.../di/AuthBindings.kt
@Module
@InstallIn(SingletonComponent::class)
interface AuthBindings {
    @Binds fun session(impl: AuthSessionProvider): SessionProvider
}
```

```kotlin
// app/src/design/kotlin/.../di/DesignBindings.kt
@Module
@InstallIn(SingletonComponent::class)
interface DesignBindings {
    // shell выбирает стартовый экран по сессии, поэтому demo-session нужен и здесь
    @Binds fun session(impl: DemoSessionProvider): SessionProvider
}
```

`VoiceBindings` устроен аналогично `MediaBindings`: demo-session, demo-message-sender, demo-chat-host.
Это единственные файлы, которые знают, «настоящая» реализация или demo. Они крошечные и не
содержат логики. В `full` они связывают реальные фичи, в `auth` — только auth, в `media`/`voice` —
demo-хосты, в `design` — demo-сессию для стартового экрана.

В `app/src/main` биндингов портов нет: `AppModule`/`NetworkModule` отвечают только за shell и сеть.
Иначе flavor-модуль не смог бы заменить реализацию, и удаление фичи снова потребовало бы правок
в общем коде.

> `:core:demo` предоставляет **классы** `DemoSessionProvider`, `DemoMessageSender`, `DemoChatHost`,
> но не биндит их сам. Иначе в flavor, где есть и реальная фича, и demo, Hilt получит две
> реализации одного порта. Биндинг всегда делает конкретный `*Bindings.kt` в `:app`.

То же правило для фич: `AuthModule` не биндит `AuthSessionProvider` на `SessionProvider`,
`MessagingModule` не биндит `MessagingMessageSender` на `MessageSender`. Фича отдаёт класс, а
интерфейс связывает только `*Bindings.kt`. Иначе при удалении фичи или добавлении demo-binding
Hilt получит duplicate binding.

> Альтернатива без source set — `@BindsOptionalOf` + `Optional<SessionProvider>`, но она сложнее
> читается и хуже объясняется на защите. Для команды из пяти человек явные binding-модули проще.

### 9.5. Почему не `ServiceLocator`

Текущий `ServiceLocator` — нормальный старт для MVP, но он:

- создаёт глобальные синглтоны, которые трудно подменять в тестах;
- заставляет `:app` знать все репозитории;
- не умеет собирать пустые наборы для flavor без фичи.

Целевое состояние — Hilt. Миграция постепенная: сначала `ServiceLocator` вызывает Hilt-граф, потом
удаляется. Не нужно переписывать все ViewModel за один PR.

---

## 10. Навигация и shell

### 10.1. Динамическая сборка графа

`AppNavHost` не содержит жёсткого списка экранов. Он получает набор `FeatureEntry` и регистрирует
все графы:

```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController,
    entries: Set<FeatureEntry>,
    startDestination: String,
) {
    NavHost(navController, startDestination = startDestination) {
        entries.forEach { entry -> entry.registerGraph(this) }
        // общий fallback для сборки без фич
        composable(NavRoutes.DemoPlaceholder) { DemoPlaceholderScreen() }
    }
}
```

Стартовый экран выбирается по состоянию сессии:

```kotlin
fun chooseStartDestination(
    session: Session?,
    entries: Set<FeatureEntry>,
    homeDestinations: Set<HomeDestination>,
): String {
    if (session == null) {
        // Контракт: без сессии не-null возвращает только auth.
        return entries.firstNotNullOfOrNull { it.startDestination(null) }
            ?: NavRoutes.DemoPlaceholder
    }
    return homeDestinations.minByOrNull { it.order }?.route
        ?: entries.firstNotNullOfOrNull { it.startDestination(session) }
        ?: NavRoutes.DemoPlaceholder
}
```

Почему так:

- порядок `Set` не гарантирован, поэтому полагаться на «первую фичу» нельзя; правило «без сессии
  стартовый экран отдаёт только auth» делает выбор детерминированным;
- `HomeDestination` важнее личного `startDestination`: в `full` и `messaging` открываются чаты,
  а не служебный экран авторизации;
- в `auth` без сессии открывается `auth/login`, с сохранённой сессией — `auth/success`;
- в `media`/`voice` сессия всегда есть (demo-session), поэтому открываются галерея/запись;
- `chooseStartDestination` используется и при холодном старте, и в `LaunchedEffect(isAuthenticated)`
  в shell: вход ведёт в `full` → чаты, в `auth` → `auth/success`; выход возвращает на login.
  `NavHost` при этом не пересоздаётся, а shell проверяет текущий destination, чтобы не дублировать
  стартовый переход.

### 10.2. Нижняя навигация

Вкладки — это `HomeDestination` из фич. Если messaging не подключён, вкладки «Чаты» нет. Если
подключён только design, показывается `showcase`. Shell не падает на пустом списке — есть
`DemoPlaceholder`, а bottom bar скрывается, если вкладок меньше двух.

### 10.3. Deep links

Маршруты объявляются в фиче:

- `chat/{chatId}` — messaging;
- `media/viewer/{attachmentId}` — media;
- `voice/player/{messageId}` — voice;
- `auth/login`, `auth/register` — auth;
- `showcase/home` — design.

`AndroidManifest.xml` с intent-filter для `messenger://` живёт в `:app`, потому что activity одна.
Фича отдаёт только константу маршрута. Если deep link не нужен — не добавляем, чтобы не тащить
связанность.

### 10.4. Один activity, много экранов

`:app` содержит `MainActivity` и `MessengerApp`. Фичи не объявляют свои activity. Это упрощает
навигацию, тему, back stack и тестирование.

---

## 11. Gradle flavors: пять индивидуальных сборок

### 11.1. `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SozvezdieMessenger"
includeBuild("build-logic")

include(":app")

include(
    ":core:common",
    ":core:model",
    ":core:contracts",
    ":core:network",
    ":core:datastore",
    ":core:database",
    ":core:ui",
    ":core:design",
    ":core:navigation",
    ":core:testing",
    ":core:demo",
)

include(
    ":feature:auth",
    ":feature:messaging",
    ":feature:media",
    ":feature:voice",
    ":feature:motion",
    ":feature:showcase",
)
```

### 11.2. Флейворы в `:app`

```kotlin
android {
    flavorDimensions += "product"

    productFlavors {
        create("full") {
            dimension = "product"
            // полный продукт, без суффикса applicationId
            resValue("string", "app_name", "Созвездие")
        }
        create("auth") {
            dimension = "product"
            applicationIdSuffix = ".auth"
            versionNameSuffix = "-auth"
            resValue("string", "app_name", "Созвездие: Auth")
        }
        create("messaging") {
            dimension = "product"
            applicationIdSuffix = ".messaging"
            versionNameSuffix = "-messaging"
            resValue("string", "app_name", "Созвездие: Messaging")
        }
        create("media") {
            dimension = "product"
            applicationIdSuffix = ".media"
            versionNameSuffix = "-media"
            resValue("string", "app_name", "Созвездие: Media")
        }
        create("voice") {
            dimension = "product"
            applicationIdSuffix = ".voice"
            versionNameSuffix = "-voice"
            resValue("string", "app_name", "Созвездие: Voice")
        }
        create("design") {
            dimension = "product"
            applicationIdSuffix = ".design"
            versionNameSuffix = "-design"
            resValue("string", "app_name", "Созвездие: Design")
        }
    }
}

dependencies {
    // общие core-модули
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:network"))   // нужен NetworkModule в :app
    implementation(project(":core:ui"))
    implementation(project(":core:design"))
    implementation(project(":core:navigation"))

    // full
    "fullImplementation"(project(":feature:auth"))
    "fullImplementation"(project(":feature:messaging"))
    "fullImplementation"(project(":feature:media"))
    "fullImplementation"(project(":feature:voice"))
    "fullImplementation"(project(":feature:motion"))

    // auth
    "authImplementation"(project(":feature:auth"))

    // messaging
    "messagingImplementation"(project(":feature:messaging"))
    "messagingImplementation"(project(":core:demo"))

    // media
    "mediaImplementation"(project(":feature:media"))
    "mediaImplementation"(project(":core:demo"))

    // voice
    "voiceImplementation"(project(":feature:voice"))
    "voiceImplementation"(project(":core:demo"))

    // design
    "designImplementation"(project(":feature:motion"))
    "designImplementation"(project(":feature:showcase"))
    "designImplementation"(project(":core:demo"))
}
```

> В Kotlin DSL имена конфигураций с дефисами/флейворами удобнее писать строкой:
> `"fullImplementation"(project(":feature:auth"))`. Это стандартный приём, если IDE не подсказывает
> сгенерированный аксессор.

> Если в общем `res/values/strings.xml` уже есть `app_name`, не задавайте его одновременно через
> `resValue` — будет duplicate resource. Либо уберите общую строку и оставьте `resValue` во **всех**
> flavor, включая `full` (как в примере выше), либо задавайте имя только через `manifestPlaceholders`.

### 11.3. Таблица сборок

| Flavor | Что входит | Сервер | Стартовый экран | Что демонстрирует |
| --- | --- | --- | --- | --- |
| `full` | auth + messaging + media + voice + motion | все фичи | login → chats | Полный мессенджер |
| `auth` | auth | `FEATURES=auth,users` | login → success | Вход, регистрация, refresh, logout |
| `messaging` | messaging + demo | `FEATURES=auth,users,chats,messages,files`, `DEMO_MODE=1` | chats | Чаты, realtime, offline, статусы |
| `media` | media + demo | `FEATURES=auth,users,chats,messages,files`, `DEMO_MODE=1` | media/gallery | Выбор, загрузка, просмотр |
| `voice` | voice + demo | `FEATURES=auth,users,chats,messages,files,voice`, `DEMO_MODE=1` | voice/record | Запись, плеер, расшифровка |
| `design` | motion + showcase + demo | не нужен | showcase/home | Токены, компоненты, анимации |

### 11.4. Команды сборки

```bash
cd android

# полный APK
./gradlew :app:assembleFullDebug

# индивидуальные APK
./gradlew :app:assembleAuthDebug
./gradlew :app:assembleMessagingDebug
./gradlew :app:assembleMediaDebug
./gradlew :app:assembleVoiceDebug
./gradlew :app:assembleDesignDebug

# все сразу (используется в CI)
./gradlew :app:assembleFullDebug :app:assembleAuthDebug :app:assembleMessagingDebug \
  :app:assembleMediaDebug :app:assembleVoiceDebug :app:assembleDesignDebug
```

Windows:

```powershell
cd android
.\gradlew.bat :app:assembleVoiceDebug
```

APK лежит по пути:

```
android/app/build/outputs/apk/<flavor>/debug/app-<flavor>-debug.apk
```

Например, `android/app/build/outputs/apk/voice/debug/app-voice-debug.apk`.

### 11.5. Почему флейворы, а не отдельные приложения

- один код и один `core` — нет дублирования;
- разные `applicationIdSuffix` позволяют держать все сборки на одном устройстве;
- каждый участник ставит свой APK и показывает только свою функцию;
- CI собирает все flavor и ловит поломки изоляции;
- `full` остаётся главным продуктом для общей защиты.

### 11.6. Release-сборки

Для release:

- `minifyEnabled = true`, `shrinkResources = true`;
- `proguard-rules.pro` общий, правила фич — в их `consumer-rules.pro`;
- demo-модуль не попадает в `fullRelease`;
- подпись — через переменные окружения/keystore, не в репозитории;
- `applicationId` полного продукта не меняется.

---

## 12. Сервер: модульный монолит с включаемыми фичами

### 12.1. Целевая структура

```
server/src/
├─ index.js              # entrypoint: читает .env, стартует server
├─ app.js                # composition root: подключает включённые фичи
├─ server.js
├─ config.js
├─ core/
│  ├─ db/
│  │  ├─ connection.js
│  │  ├─ migrate.js
│  │  └─ transaction.js
│  ├─ http/
│  │  ├─ async-handler.js
│  │  ├─ errors.js
│  │  └─ validate.js
│  ├─ auth/
│  │  ├─ jwt.js
│  │  ├─ password.js
│  │  └─ tokens.js
│  ├─ realtime/
│  │  ├─ hub.js
│  │  └─ connections.js
│  ├─ storage/
│  │  └─ file-storage.js
│  └─ logger.js
└─ features/
   ├─ auth/
   │  ├─ index.js            # id, dependsOn, migrate, registerHttp, registerWs
   │  ├─ auth.routes.js
   │  ├─ auth.service.js
   │  ├─ auth.repository.js
   │  ├─ auth.schema.sql
   │  └─ auth.test.js
   ├─ users/
   ├─ chats/
   ├─ messages/
   ├─ files/
   └─ voice/
```

Каждая фича — плагин с одним и тем же интерфейсом:

```js
// server/src/features/voice/index.js
import { readFileSync } from 'node:fs'
import { createVoiceRouter } from './voice.routes.js'

export const id = 'voice'
export const dependsOn = ['messages', 'files']

const schema = readFileSync(new URL('./voice.schema.sql', import.meta.url), 'utf8')

export async function migrate(db) {
  await db.exec(schema)
}

export function registerHttp(app, deps) {
  app.use('/api/voice', createVoiceRouter(deps))
}

export function registerWs(hub, deps) {
  hub.on('message.created', (event) => deps.voice.enqueue(event.messageId))
}
```

`app.js` не знает URL, DTO и внутренних сервисов — только идентификаторы фич и их `dependsOn`.
Он читает список включённых фич и подключает их:

```js
// server/src/app.js
// db и deps создаются здесь же из core/*; ниже — только подключение фич.
// Упрощённо: в реальном коде после проверки добавьте топологическую сортировку по dependsOn.
const enabled = new Set(config.features)
const features = []

for (const id of enabled) {
  const mod = await import(`./features/${id}/index.js`)
  for (const dep of mod.dependsOn ?? []) {
    if (!enabled.has(dep)) {
      throw new Error(`Feature "${id}" requires "${dep}"`)
    }
  }
  features.push(mod)
}

for (const feature of features) {
  await feature.migrate?.(db)
}
for (const feature of features) {
  feature.registerHttp?.(app, deps)
  feature.registerWs?.(hub, deps)
}
```

`id` из `FEATURES` нужно проверять по allowlist (`/^[a-z]+$/`), чтобы переменная окружения не
превратилась в произвольный путь импорта. Это дешёвая, но важная защита.

Список `enabled` берётся из `FEATURES`:

```bash
# полный сервер
FEATURES=auth,users,chats,messages,files,voice npm start

# только auth
FEATURES=auth,users npm start

# messaging без voice
FEATURES=auth,users,chats,messages,files npm start
```

Windows PowerShell:

```powershell
$env:FEATURES = "auth,users,chats,messages,files"
npm start
```

### 12.2. Зависимости серверных фич

| Фича | Зависит от | HTTP | Таблицы |
| --- | --- | --- | --- |
| `auth` | — | `/api/auth/*`, `/api/me` | `users`, `refresh_tokens` |
| `users` | `auth` | `/api/users` | `users` (чтение/профиль) |
| `chats` | `auth`, `users` | `/api/chats*` | `chats`, `chat_members` |
| `messages` | `chats`; `files` — опционально | `/api/chats/:id/messages`, `/api/sync` | `messages` |
| `files` | `auth`, `chats` | `/api/files*` | `attachments` |
| `voice` | `messages`, `files` | `/api/voice/*` | `voice_transcripts` |

`messages` без `files` работает в текстовом режиме: вложения просто недоступны. Это важно для
удаления media-фичи. А вот `voice` без `files`/`messages` не имеет смысла и не стартует — лучше
явная ошибка, чем «тихая» поломка. Проверка `dependsOn` в `app.js` делает зависимость явной.

`chats` не зависит от `messages`: список чатов отдаёт метаданные, а последнее сообщение клиент берёт
из локальной БД. Иначе получился бы цикл `chats ↔ messages`.

### 12.3. Границы внутри сервера

- **Одна БД, но разные владельцы таблиц.** Каждая фича создаёт только свои таблицы и не пишет в
  чужие напрямую. Если нужны данные из другой фичи — вызывается её сервис через `deps`.
- **Свои миграции.** `auth.schema.sql`, `chats.schema.sql`, `messages.schema.sql`,
  `files.schema.sql`, `voice.schema.sql`. Общий `migrate.js` применяет их по порядку и ведёт
  `schema_migrations`.
- **Свои тесты.** `auth.test.js` лежит рядом с фичей; `server/test/` — только общие helpers и
  сквозные сценарии.
- **Свои ошибки.** Фича возвращает коды из `core/http/errors.js`, не придумывает новый формат.
- **Свой префикс событий.** WebSocket-события фичи имеют префикс: `voice.transcript`,
  `message.new`, `chat.read`. Так получатель понимает, кто владелец события.
- **Общий хаб — в core.** `core/realtime/hub.js` не знает про сообщения; он только хранит подписки
  и рассылает кадры. Обработчики кадров регистрирует messaging.

### 12.4. `DEMO_MODE`

Для индивидуальных сборок media/voice нужен чат, но auth-фича может быть не нужна. `DEMO_MODE=1`:

- создаёт демо-пользователя и демо-чат при старте;
- выдаёт предсказуемый токен только для локального dev-сервера;
- пишет в лог предупреждение «demo mode, не использовать в production»;
- отключается, если `NODE_ENV=production`.

Это позволяет media/voice подключаться к реальному API, не тащя в APK auth-фичу. Альтернатива —
`core:demo` с полностью локальным in-memory хостом; в документе поддерживаются оба варианта.

### 12.5. Совместимость с текущим сервером

Сейчас роуты лежат в `server/src/routes/*`, сервисы — в `server/src/services/*`, хаб — в
`server/src/realtime/hub.js`. Миграция:

1. Создать `core/*` и перенести туда `db`, `auth`, `logger`, `errors`, `http`, `storage`.
2. Создать `features/*`, перенести роуты/сервисы по владельцам.
3. Оставить старые пути как re-export на один релиз, чтобы не ломать тесты.
4. Удалить старые папки после зелёного CI.

Никаких изменений в URL и форматах ответов на этом этапе быть не должно.

---

## 13. Модель данных и протокол

### 13.1. Клиентские БД

| Модуль | БД | Что хранит | Почему отдельно |
| --- | --- | --- | --- |
| `:feature:messaging` | `messenger.db` | пользователи, чаты, участники, сообщения, outbox, sync state | ядро переписки |
| `:feature:media` | `media.db` | кэш файлов, локальные пути, размер, хэш, статус загрузки | не блокирует чат |
| `:feature:voice` | `voice.db` | черновики записи, waveform, транскрипты | голос можно удалить без потери чата |
| `:feature:auth` | нет Room | сессия в DataStore | токены не должны лежать в общей БД |

Связи между БД — только по ID (`messageId`, `attachmentId`, `chatId`). Никаких foreign key между
`messenger.db` и `voice.db`: SQLite не умеет межфайловые FK, а логическая связь достаточна.

### 13.2. Полиморфное содержимое сообщения

В `:core:model`:

```kotlin
sealed interface MessagePart {
    data class Text(val text: String) : MessagePart
    data class Image(
        val attachmentId: AttachmentId,
        val width: Int?,
        val height: Int?,
    ) : MessagePart
    data class File(
        val attachmentId: AttachmentId,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
    ) : MessagePart
    data class Audio(
        val attachmentId: AttachmentId,
        val durationMs: Long,
        val waveform: List<Int>,
        val transcript: String?,
        val transcriptStatus: TranscriptStatus,
    ) : MessagePart
}
```

Messaging хранит `kind` и `attachmentId`, а конкретный рендер выбирает `MessagePartRenderer`.
Если media/voice не подключены, срабатывает fallback из `:core:ui`: «Файл недоступен» или
«Аудио недоступно», но сообщение и чат продолжают работать.

### 13.3. Расширение протокола для voice

Правило: **сначала `docs/protocol.md`, потом код.** Для voice добавляются только необязательные
поля и одно событие:

- `message.voice` (optional): `durationMs`, `waveform`, `transcript`, `transcriptStatus`;
- `POST /api/voice/transcribe` → `202`;
- WS `voice.transcript`: `{ messageId, status, text, language, updatedAt }`.

Старые клиенты не ломаются: неизвестные поля игнорируются, событие можно не подписывать.
Сервер без voice-фичи просто не отдаёт `message.voice` и не отправляет `voice.transcript`.

### 13.4. Идемпотентность и офлайн

- `clientMessageId` генерируется на клиенте и сохраняется в outbox;
- сервер хранит уникальный индекс `(sender_id, client_message_id)`;
- повторная отправка возвращает существующее сообщение, а не создаёт дубль;
- `messages.id` — глобально возрастающий курсор для `/api/sync?sinceId=`;
- Room — источник истины для UI, сеть только дополняет;
- WebSocket-события идемпотентны: повторное `message.new` обновляет запись, а не добавляет новую.

Эти инварианты принадлежат messaging и не должны меняться при добавлении media/voice.

---

## 14. Git-процесс для пяти человек

### 14.1. Ветки

| Ветка | Назначение | Кто работает | Вливается |
| --- | --- | --- | --- |
| `main` | стабильное состояние, зелёный CI | никто напрямую | — |
| `auth` | личная интеграционная ветка auth | владелец auth | не вливается целиком, только PR |
| `messaging` | личная интеграционная ветка messaging | владелец messaging | то же |
| `media` | личная интеграционная ветка media | владелец media | то же |
| `voice` | личная интеграционная ветка voice | владелец voice | то же |
| `design` | личная интеграционная ветка design | владелец design | то же |

Рабочие ветки задач создаются **от личной ветки** и называются `<роль>/<задача>`:

```
auth/login-validation
messaging/offline-outbox
media/upload-progress
voice/transcript-sheet
design/chat-list-animation
```

Личная ветка — это «песочница» и площадка для демо. В `main` попадают только маленькие PR.
Раз в день личная ветка обновляется:

```bash
git fetch origin
git switch auth
git rebase origin/main
```

Если rebase личной ветки уже был запушен, используйте `git push --force-with-lease` **только для
своей** ветки. Для `main` force-push запрещён.

### 14.2. Ежедневный цикл

```bash
# 1. Обновить main и личную ветку
git fetch origin
git switch auth
git rebase origin/main

# 2. Создать задачу
git switch -c auth/login-validation

# 3. Работать маленькими коммитами
git add android/feature/auth
git commit -m "feat(auth): validate login form"

# 4. Перед PR — rebase на main
git fetch origin
git rebase origin/main

# 5. Отправить и открыть PR
git push -u origin auth/login-validation
gh pr create --base main --head auth/login-validation
```

### 14.3. Правила PR

- один PR — одна фича или один контракт;
- PR не трогает чужие `feature/*`, кроме случаев, когда владелец явно попросил;
- PR с изменением `:core:contracts`, `docs/protocol.md`, `settings.gradle.kts`,
  `gradle/libs.versions.toml`, `:app` требует ревью затронутых владельцев;
- PR не больше ~400 строк изменений; большой рефакторинг — отдельно;
- в описании: что сделано, какой flavor собрать, как проверить, есть ли изменение протокола;
- скриншоты/видео — для UI-изменений;
- CI должен быть зелёным до merge;
- merge — squash, чтобы история `main` была читаемой.

### 14.4. Contract-first

Любое изменение границы делается в таком порядке:

1. Issue: «нужен контракт X для фичи Y».
2. Отдельный PR: интерфейс в `:core:contracts` + `docs/protocol.md` + тесты сериализации.
3. Ревью всех, кто реализует или потребляет контракт.
4. Merge в `main`.
5. Все личные ветки делают rebase.
6. Только после этого — реализация в фичах.

Это медленнее, чем «быстро поправить у себя», но избавляет от ситуации, когда пять веток
по-разному понимают `message.voice`.

### 14.5. `CODEOWNERS`

```
# .github/CODEOWNERS
# Замените @... на реальные GitHub-аккаунты.

/android/feature/auth/          @auth-owner
/android/feature/messaging/     @messaging-owner
/android/feature/media/         @media-owner
/android/feature/voice/         @voice-owner
/android/feature/motion/        @design-owner
/android/feature/showcase/      @design-owner
/android/core/design/           @design-owner

/android/core/contracts/        @auth-owner @messaging-owner @media-owner @voice-owner @design-owner
/android/core/model/            @auth-owner @messaging-owner @media-owner @voice-owner @design-owner
/android/core/navigation/       @design-owner @messaging-owner
/android/core/ui/               @design-owner @messaging-owner
/android/app/                   @design-owner @messaging-owner

/server/src/features/auth/      @auth-owner
/server/src/features/users/     @auth-owner
/server/src/features/chats/     @messaging-owner
/server/src/features/messages/  @messaging-owner
/server/src/core/realtime/      @messaging-owner
/server/src/features/files/     @media-owner
/server/src/features/voice/     @voice-owner

/docs/protocol.md               @auth-owner @messaging-owner @media-owner @voice-owner @design-owner
```

GitHub требует хотя бы одного approval от code owner. Для `core` лучше договориться о правиле
«два approval», даже если технически достаточно одного.

### 14.6. Коммиты

Формат: `type(scope): summary`.

```
feat(auth): add refresh token rotation
fix(messaging): avoid duplicate message after reconnect
refactor(media): extract upload worker
test(voice): cover transcript retry
docs(protocol): describe voice.transcript event
chore(ci): build all flavors
```

Scope — имя фичи или `core`. Это упрощает чтение истории и поиск регрессий.

---

## 15. Как не конфликтовать

1. **Не редактируйте чужой модуль.** Нужна правка — issue владельцу или PR с его ревью.
2. **Не добавляйте зависимость `feature → feature`.** Только контракт в core.
3. **Префиксы ресурсов.** `auth_`, `chat_`, `media_`, `voice_`, `motion_`, `showcase_`.
4. **Префиксы маршрутов.** `auth/...`, `chat/...`, `media/...`, `voice/...`, `showcase/...`.
5. **Префиксы миграций.** `auth_001`, `chat_001`, `media_001`, `voice_001`.
6. **Не меняйте общий `strings.xml` без префикса.** Общие строки — только в `:app`/`:core:ui`.
7. **Один владелец version catalog.** Изменения версий — отдельный PR с объяснением.
8. **Не коммитьте сгенерированное.** `build/`, `.gradle/`, `local.properties`, `server/data/`,
   `node_modules/`, `*.apk` (кроме `artifacts/` по договорённости).
9. **Форматирование отдельно от логики.** ktlint/detekt и Prettier/ESLint в CI; не переформатируйте
   чужой файл «заодно».
10. **Протокол — только через `docs/protocol.md`.** Нельзя добавить поле в DTO и «забыть» документ.
11. **Не блокируйте других.** Если ваша фича ещё не готова, она не должна ломать `full`: используйте
    заглушку/feature flag, а не пустой экран.
12. **Интеграционный день раз в неделю.** Все личные ветки rebase на `main`, собирается `full`,
    прогоняются сквозные тесты. Проблемы чинятся сразу, а не в конце семестра.

### 15.1. Автоматическая проверка изоляции

В CI достаточно простого шага:

```bash
# feature-модули не должны зависеть от других feature-модулей
if grep -R --include='build.gradle.kts' -E 'project\(":feature:' android/feature; then
  echo "feature -> feature dependency found"
  exit 1
fi
```

Дополнительно можно добавить Gradle-задачу в `build-logic`, которая падает на любой
`ProjectDependency` с путём `:feature:` внутри `:feature:*`. Но для старта достаточно grep: он
прозрачен и понятен на защите.

---

## 16. Как удалить фичу (главный сценарий)

Пример: удаляем `:feature:voice`, оставляем всё остальное.

### Шаг 1. Убрать модуль из Gradle

`android/settings.gradle.kts`:

```diff
- include(":feature:voice")
```

`android/app/build.gradle.kts`:

```diff
- "fullImplementation"(project(":feature:voice"))
- "voiceImplementation"(project(":feature:voice"))
- "voiceImplementation"(project(":core:demo"))
```

### Шаг 2. Удалить flavor-обвязку

Удалить `app/src/voice/` (там только `VoiceBindings.kt` и, возможно, demo-настройки). Flavor `voice`
тоже удалите из `productFlavors`: без модуля он останется без `SessionProvider` и упадёт на старте
Hilt-графа. Уберите `voiceImplementation` и `assembleVoiceDebug` из CI.

### Шаг 3. Удалить Android-модуль

```bash
rm -rf android/feature/voice
```

Вместе с ним уходят:

- экран записи и плеер;
- `VoiceComposerAction` и `VoicePartRenderer`;
- `RECORD_AUDIO` из merged manifest;
- строки `voice_*`;
- `voice.db` и её миграции;
- тесты voice.

### Шаг 4. Убрать серверную фичу

```diff
# server/.env
- FEATURES=auth,users,chats,messages,files,voice
+ FEATURES=auth,users,chats,messages,files
```

```bash
rm -rf server/src/features/voice
```

Если `voice` был единственным потребителем таблицы `voice_transcripts`, удалите и её миграцию.
Если таблица уже в продакшене — оставьте миграцию, но перестаньте её использовать (данные не
удаляются автоматически).

### Шаг 5. Убрать документацию и CI

- удалить `docs/features/voice.md`;
- убрать `assembleVoiceDebug` из `.github/workflows/android.yml`;
- убрать `voice` из матрицы тестов.

### Шаг 6. Проверить

```bash
cd android
./gradlew :app:assembleFullDebug
./gradlew :app:assembleAuthDebug :app:assembleMessagingDebug :app:assembleMediaDebug :app:assembleDesignDebug

cd ../server
npm test
FEATURES=auth,users,chats,messages,files npm start
```

**Почему это почти не требует правок `:app`:**

- навигация собирается из `Set<FeatureEntry>` через Hilt;
- рендеры и composer actions — тоже мультибиндинги;
- voice не владеет общим портом, поэтому его удаление не затрагивает `SessionProvider`/
  `MessageSender`;
- в `:app` нет ни одной строки `import ...feature.voice`.

Если бы мы убирали auth или messaging **только из `full`**, оставив их standalone-сборки, пришлось
бы поправить один маленький `FullBindings.kt`: подставить demo-реализацию порта и добавить
`fullImplementation(project(":core:demo"))`. Это явная точка сборки, а не скрытая зависимость.
`AppNavHost` и `AppShell` при этом не меняются.

Если фича удаляется **целиком**, вместе с модулем уходят её flavor, source set с `*Bindings.kt` и
серверная папка. Общий shell по-прежнему не меняется; меняется только composition root — это и есть
его работа.

Если после удаления пришлось править `AppNavHost` или `AppShell` — значит, граница была нарушена.
Это хороший тест архитектуры.

---

## 17. Как собрать и показать индивидуальный проект

### 17.1. Общие команды

```bash
git clone <repo-url>
cd <repo>
git switch auth        # или messaging, media, voice, design
cd android
./gradlew :app:assembleAuthDebug
```

Установка:

```bash
adb install -r app/build/outputs/apk/auth/debug/app-auth-debug.apk
```

Сервер:

```bash
cd server
npm ci
cp .env.example .env
npm start
```

Windows: `copy .env.example .env`.

### 17.2. По ролям

| Роль | Команда сборки | Сервер | Что показать за 3 минуты |
| --- | --- | --- | --- |
| Auth | `./gradlew :app:assembleAuthDebug` | `FEATURES=auth,users` | Регистрация → вход → перезапуск → refresh → выход |
| Messaging | `./gradlew :app:assembleMessagingDebug` | `FEATURES=auth,users,chats,messages,files`, `DEMO_MODE=1` | Два клиента, сообщение, статус, офлайн, досылка |
| Media | `./gradlew :app:assembleMediaDebug` | `FEATURES=auth,users,chats,messages,files`, `DEMO_MODE=1` | Выбор фото → прогресс → просмотр → кэш |
| Voice | `./gradlew :app:assembleVoiceDebug` | `FEATURES=auth,users,chats,messages,files,voice`, `DEMO_MODE=1` | Запись → отправка → плеер → расшифровка |
| Design | `./gradlew :app:assembleDesignDebug` | не нужен | Галерея компонентов, темы, анимации |

### 17.3. Что положить в `docs/features/<role>.md`

- цель и границы фичи;
- скриншоты/гифки;
- как собрать flavor;
- какие серверные фичи включить;
- сценарий демо на 2–3 минуты;
- известные ограничения;
- что было бы, если удалить эту фичу (одна фраза).

Этот файл — основа индивидуальной защиты. Он не дублирует `docs/yanka.md`, а показывает
конкретный результат.

---

## 18. Тестирование и CI

### 18.1. Пирамида тестов

| Уровень | Что проверяем | Где |
| --- | --- | --- |
| Unit | use case, мапперы, валидация, парсеры кадров | `feature/*/src/test` |
| Room | DAO, миграции, уникальные индексы | `feature/*/src/androidTest` |
| Compose UI | экран, состояния, доступность | `feature/*/src/androidTest` |
| Contract | DTO ↔ `docs/protocol.md`, сериализация | `core:network`, `server/src/features/*` |
| Server | роуты, права, идемпотентность, WS | `server/src/features/*/*.test.js` |
| E2E | два клиента, сеть, офлайн, файлы | `docs/manual-test-checklist.md` |
| Screenshot | тема, компоненты, регрессии | `core:design`, `feature:showcase` |

### 18.2. Команды

```bash
# Android: unit + lint по всем flavor
cd android
./gradlew testFullDebugUnitTest lintFullDebug
./gradlew :app:assembleFullDebug :app:assembleAuthDebug :app:assembleMessagingDebug
./gradlew :app:assembleMediaDebug :app:assembleVoiceDebug :app:assembleDesignDebug

# Server
cd server
npm ci
npm test
```

Для Compose-тестов нужен эмулятор/устройство; в CI — один API 34 emulator и только ключевые
сценарии, чтобы не ждать час.

### 18.3. CI

`.github/workflows/android.yml`:

- path filter: `android/**`, `.github/workflows/android.yml`;
- JDK 17, Gradle cache;
- `./gradlew check` для затронутых модулей;
- сборка всех flavor (можно параллельно);
- upload APK в artifacts;
- проверка `feature → feature` grep-ом;
- для PR в `main` — обязательный статус.

`.github/workflows/server.yml`:

- path filter: `server/**`;
- Node.js 24;
- `npm ci && npm test`;
- отдельный job: старт с `FEATURES=auth,users` и health-check, чтобы проверить минимальную сборку.

### 18.4. Что считать достаточным покрытием

Не гоняемся за 100%. Обязательны:

- критические инварианты (идемпотентность, токены, права доступа, офлайн-очередь);
- все состояния экранов: loading / empty / error / content;
- миграции БД;
- контракты, которые пересекают границу фич;
- сценарий удаления фичи (собирается `full` без неё).

---

## 19. Безопасность и приватность

| Аспект | Решение |
| --- | --- |
| Пароли | bcrypt, одинаковое время ответа при неверном логине |
| Access-токен | JWT HS256, 15 минут, секрет только в окружении |
| Refresh-токен | случайная строка, в БД SHA-256, ротация, отзыв при выходе |
| Хранение на клиенте | DataStore + шифрование через Android Keystore; не логировать токены |
| Доступ к чату | проверка участия на каждом запросе |
| Доступ к файлу | владелец или участник чата, где файл отправлен |
| Загрузка файлов | лимит размера, белый список MIME, проверка сигнатуры, очистка имени |
| Rate limit | `/api/auth/*` и загрузка файлов |
| Транспорт | TLS в production; cleartext только для локальной разработки |
| Логи | без токенов, паролей, текстов сообщений и путей к файлам |
| Голос | явное согласие на запись и на расшифровку; возможность удалить транскрипт |
| Расшифровка | не отправлять аудио третьей стороне без согласия; хранить срок хранения |
| E2E | не заявляем, пока не реализовано; в MVP защита на уровне транспорта |

**Отдельно про voice:** расшифровка — это биометрия и персональные данные. В UI должно быть
понятно, куда уходит аудио (локально/сервер/внешний провайдер), и должна быть возможность
отказаться. Для учебного проекта достаточно `StubTranscriber` + явная пометка «демо-расшифровка»,
но архитектура должна позволять заменить провайдера без переписывания фичи.

**Отдельно про auth:** если преподаватель требует Firebase Authentication, `AuthRepository`
меняется на `FirebaseAuthGateway`, а `SessionProvider` остаётся тем же. Фичи не знают, откуда
токен. Аналогично Firebase Storage может заменить `features/files`, если сохранить контракты
`MessageSender` и `MessagePartRenderer`.

---

## 20. Миграция текущего MVP

Не нужно делать всё сразу. Порядок такой, чтобы после каждого шага проект собирался.

### Этап 0. Подготовка (1–2 дня)

- создать `main`, защитить ветку, добавить CI;
- добавить `kotlin`-форматтер и `detekt`/`ktlint`;
- зафиксировать `docs/protocol.md` как источник истины;
- создать личные ветки `auth`, `messaging`, `media`, `voice`, `design`;
- добавить `CODEOWNERS` и PR-template.

### Этап 1. `core` без изменения поведения

- вынести `Result`, модели, тему, сетевые обёртки, DataStore, `DatabaseFactory`;
- `:app` пока зависит от всего, но экраны не меняются;
- CI собирает `:app:assembleDebug`;
- цель — не сломать текущий MVP.

### Этап 2. Auth

- вынести `ui/auth`, `AuthRepository`, `SessionStore` в `:feature:auth`;
- добавить `SessionProvider`;
- `:app` зависит от `:feature:auth`;
- текущий экран входа работает как раньше;
- отдельный `authDebug` появится на этапе 7, когда будут добавлены flavors.

### Этап 3. Messaging

- вынести `ui/chats`, `ui/chat`, `ChatRepository`, `MessageRepository`, `SyncCoordinator`,
  `ChatSocket`, Room-сущности в `:feature:messaging`;
- добавить `MessageSender`, `ChatHost`;
- `:app` собирает чаты через `FeatureEntry`;
- `messagingDebug` с demo-session появится на этапе 7.

### Этап 4. Media

- вынести `FileRepository`, `FileBubble`, загрузку/скачивание в `:feature:media`;
- добавить `ComposerAction`, `MessagePartRenderer` и внутренний `AttachmentOpener`;
- `messaging` начинает рисовать вложения через слоты, а не через прямой импорт;
- `mediaDebug` появится на этапе 7.

### Этап 5. Voice

- добавить `:feature:voice` с записью, плеером, `voice.db`;
- добавить серверную `features/voice` и `Transcriber`;
- расширить `docs/protocol.md` и DTO необязательным `voice`;
- `voiceDebug` появится на этапе 7.

### Этап 6. Design/motion

- вынести тему в `:core:design`;
- добавить `:feature:motion` (переходы) и `:feature:showcase` (галерея);
- `HomeDestination` для дизайна добавляет `:feature:showcase` и только в `design` flavor;
  `:feature:motion` в `full` даёт анимации, но не добавляет вкладку;
- `designDebug` появится на этапе 7.

### Этап 7. Флейворы и удаление

- добавить product flavors и flavor-specific зависимости;
- проверить каждую сборку;
- удалить по очереди media, voice, auth, messaging из `full` (в тестовой ветке) и убедиться, что
  оставшиеся собираются; для auth/messaging при проверке подставьте demo-binding и добавьте
  `fullImplementation(project(":core:demo"))`, иначе shell останется без
  `SessionProvider`/`MessageSender`;
- вернуть всё обратно;
- зафиксировать checklist удаления в README.

### 20.1. Карта переноса текущих файлов

| Сейчас | Куда |
| --- | --- |
| `android/app/.../ui/auth/*` | `:feature:auth/ui/*` |
| `android/app/.../data/repo/AuthRepository.kt` | `:feature:auth/data/repository` |
| `android/app/.../data/prefs/SessionStore.kt` | `:feature:auth/data/local` + `:core:datastore` |
| `android/app/.../ui/chats/*` | `:feature:messaging/ui/chats` |
| `android/app/.../ui/chat/*` | `:feature:messaging/ui/chat` |
| `android/app/.../data/repo/ChatRepository.kt` | `:feature:messaging/data/repository` |
| `android/app/.../data/repo/MessageRepository.kt` | `:feature:messaging/data/repository` |
| `android/app/.../data/repo/SyncCoordinator.kt` | `:feature:messaging/data/repository` |
| `android/app/.../data/remote/ws/ChatSocket.kt` | `:feature:messaging/data/remote/ws` |
| `android/app/.../data/local/*` | `:feature:messaging/data/local` (чатовая БД) |
| `android/app/.../data/repo/FileRepository.kt` | `:feature:media/data/repository` |
| `android/app/.../ui/common/FileBubble.kt` | `:feature:media/ui/renderer` + fallback в `:core:ui` |
| `android/app/.../data/remote/*` | `:core:network` (Retrofit/OkHttp/DTO base) |
| `android/app/.../ui/theme/*` | `:core:design/theme` |
| `android/app/.../ui/ViewModelFactory.kt`, `ServiceLocator.kt` | Hilt-модули в `:app/di` и `feature/*/di` |
| `android/app/.../MainActivity.kt`, `MessengerApp.kt` | `:app` shell |
| `server/src/routes/auth.js` | `server/src/features/auth` |
| `server/src/routes/users.js` | `server/src/features/users` |
| `server/src/routes/chats.js` | `server/src/features/chats` |
| `server/src/routes/sync.js` | `server/src/features/messages` |
| `server/src/routes/files.js` | `server/src/features/files` |
| `server/src/services/*` | `server/src/features/*` |
| `server/src/realtime/hub.js` | `server/src/core/realtime` |

**Важно:** на этапе переноса не меняйте URL, форматы JSON и названия кадров. Сначала «переезд без
изменений», потом новые возможности. Иначе тесты и клиенты начнут падать одновременно.

---

## 21. Риски и как их снимать

| Риск | Проявление | Снижение |
| --- | --- | --- |
| Личные ветки расходятся | конфликты в `settings.gradle.kts`, `libs.versions.toml` | ежедневный rebase, маленькие PR, один владелец версий |
| Общий `core` меняется всеми | чужая фича ломается | CODEOWNERS, contract-first, два approval |
| Фича тянет чужую фичу | удаление ломает сборку | CI grep, code review, только `core:*` |
| Hilt сложен для команды | долгая настройка | convention-плагины, один пример, парная работа |
| Flavor-сборки медленные | CI долгий | кэш Gradle, matrix, только нужные модули |
| Разные БД усложняют запросы | нужно собирать данные в память | не дробить чатовую БД; media/voice — только кэш |
| Голосовая расшифровка дорогая/неточная | демо нестабильно | `StubTranscriber`, on-device, кнопка повтора |
| Медиа вызывает OOM | падение на больших фото | сжатие, downsampling, Coil, лимиты |
| Протокол расходится | клиент и сервер не понимают друг друга | `docs/protocol.md`, contract-тесты, один PR на изменение |
| Участник заболел | фича не готова к защите | фичу можно удалить; `full` собирается без неё |
| Дизайн ломает скриншоты | визуальные регрессии | screenshot-тесты, токены, ревью design |
| Firebase требуют внезапно | переписывание | адаптеры за портами, не менять фичи |

---

## 22. Definition of Done

### Для фичи

- [ ] Модуль собирается отдельно в своём flavor.
- [ ] Нет зависимостей `feature → feature` (проверено CI).
- [ ] Нет `import` классов другой фичи.
- [ ] Свои строки с префиксом, свои маршруты, свои миграции.
- [ ] Есть loading / empty / error / content состояния.
- [ ] Есть unit-тесты на критические сценарии.
- [ ] Есть README и сценарий демо на 2–3 минуты.
- [ ] Проверено удаление фичи: `full` без неё собирается (для auth/messaging — с demo-binding).
- [ ] Обновлён `docs/protocol.md`, если менялся контракт.
- [ ] Нет токенов/персональных данных в логах.
- [ ] Доступность: contentDescription, touch target, тёмная тема.

### Для PR

- [ ] Один смысл изменений, один модуль (или один контракт).
- [ ] CI зелёный.
- [ ] Ревью владельца затронутой фичи.
- [ ] Для UI — скриншот/видео.
- [ ] Для протокола — отдельный PR и ревью всех потребителей.
- [ ] Нет случайных файлов (`build/`, `node_modules/`, `local.properties`).

### Для релиза `full`

- [ ] `assembleFullDebug` и `assembleFullRelease` собираются.
- [ ] Все flavor собираются в CI.
- [ ] Серверные тесты зелёные.
- [ ] Ручной чек-лист на двух клиентах пройден.
- [ ] Проверено удаление каждой фичи (хотя бы по одному разу за проект).
- [ ] APK лежит в `artifacts/` (если требуется).

---

## 23. Что говорить на защите

Короткая версия для каждого участника:

- **Архитектура:** модульный монолит, `:app` — composition root, `:core:*` — инфраструктура,
  `:feature:*` — вертикальные срезы. Фичи не зависят друг от друга.
- **Командная работа:** у каждого своя ветка, свой модуль, свой flavor и свой серверный каталог;
  `main` защищён, контракты меняются отдельным PR.
- **Индивидуальный проект:** `./gradlew :app:assembleVoiceDebug` даёт самостоятельный APK, в котором
  есть только voice и demo-host. Не нужно тащить чужие экраны.
- **Удаление фичи:** удалить Gradle-модуль, flavor-зависимость, серверную папку и запись в
  `FEATURES`; навигация и слоты подхватываются через Hilt-мультибиндинги. Если фича владеет общим
  портом (auth/messaging), меняется один явный `*Bindings.kt`, а не `AppShell`.
- **Надёжность:** offline-first, идемпотентность, sync по `sinceId`, тесты на сервере и клиенте.
- **Расширяемость:** media и voice подключаются как слоты (`ComposerAction`, `MessagePartRenderer`),
  а не как жёсткие зависимости чата.
- **Firebase:** если требуется по ТЗ, заменяется адаптером за портами; фичи и их экраны не
  переписываются.

Главный тезис: **нам не нужно выбирать между «одним общим приложением» и «пятью отдельными
проектами». Один репозиторий и один `core` дают общий продукт, а модули и flavor — пять
самостоятельных презентаций.**

