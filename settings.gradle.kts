// Корневой файл настроек: какие модули входят в проект.
// Каждая крупная часть мессенджера — отдельный Gradle-модуль,
// поэтому разные люди могут работать в разных папках и не мешать друг другу.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Messenger"
include(":app")

// Общие модули
include(":core:model")
include(":core:data")
include(":core:ui")

// Функции приложения
include(":feature:auth")
include(":feature:chats")
include(":feature:chat")
include(":feature:media")
include(":feature:motion")
