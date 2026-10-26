// Слой данных: Firebase (авторизация, база, файлы) и Room (история на телефоне).
// Экраны не работают с Firebase напрямую — только через репозитории этого модуля.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sozvezdie.messenger.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:model"))

    // Firebase: вход, переписка в реальном времени, хранение файлов
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)

    // await() для задач Firebase — чтобы писать обычные suspend-функции
    implementation(libs.kotlinx.coroutines.play.services)

    // Room: локальная копия чатов и сообщений
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
