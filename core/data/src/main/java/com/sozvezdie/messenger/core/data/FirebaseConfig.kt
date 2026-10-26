package com.sozvezdie.messenger.core.data

/**
 * Настройки Firebase, которые нельзя получить из google-services.json.
 *
 * URL базы нужно один раз скопировать из консоли Firebase:
 * Realtime Database → вкладка «Data» → адрес в начале страницы.
 *
 * У этого проекта база создана в регионе europe-west1, поэтому адрес
 * отличается от привычного ...firebaseio.com. Если вы создадите свою базу
 * в другом регионе, замените строку ниже на адрес из консоли.
 */
object FirebaseConfig {
    const val DATABASE_URL = "https://tn-messenger-e1585-default-rtdb.europe-west1.firebasedatabase.app"
}
