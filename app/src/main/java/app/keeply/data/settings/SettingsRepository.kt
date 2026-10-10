package app.keeply.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("Match system"), LIGHT("Light"), DARK("Dark") }

enum class NotificationCategory(val channelId: String, val label: String, val description: String) {
    SUBSCRIPTIONS("renewals", "Upcoming payments", "Before a subscription renews"),
    WARRANTIES("warranties", "Warranty expiry", "Before a warranty runs out"),
    DOCUMENTS("documents", "Document expiry", "Before a passport, licence or visa expires"),
    REMINDERS("timed_reminders", "Reminders", "Exams, birthdays, interviews and other notes"),
}

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultCurrency: String = "USD",
    val enabledNotifications: Set<NotificationCategory> = NotificationCategory.entries.toSet(),
    /** The floating + button over other apps. */
    val floatingButton: Boolean = false,
    /** Suggest saving payments/renewals spotted in other apps' notifications. */
    val smartSuggestions: Boolean = false,
    /** Ask for fingerprint, face or the phone's PIN to open Keeply. */
    val appLock: Boolean = false,
    /** Seconds Keeply can sit in the background before it locks again (0 = immediately). */
    val lockAfterSeconds: Int = 60,
    /** Block screenshots and hide content in the recent-apps view. */
    val privacyScreen: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "keeply_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val floating = booleanPreferencesKey("floating_button")
        val suggestions = booleanPreferencesKey("smart_suggestions")
        val appLock = booleanPreferencesKey("app_lock")
        val lockAfter = androidx.datastore.preferences.core.intPreferencesKey("lock_after_seconds")
        val privacy = booleanPreferencesKey("privacy_screen")
        val theme = stringPreferencesKey("theme_mode")
        val currency = stringPreferencesKey("default_currency")
        fun notif(c: NotificationCategory) = booleanPreferencesKey("notif_${c.channelId}")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        onboardingComplete = this[Keys.onboarding] ?: false,
        themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        defaultCurrency = this[Keys.currency] ?: "USD",
        enabledNotifications = NotificationCategory.entries.filter { this[Keys.notif(it)] ?: true }.toSet(),
        floatingButton = this[Keys.floating] ?: false,
        smartSuggestions = this[Keys.suggestions] ?: false,
        appLock = this[Keys.appLock] ?: false,
        lockAfterSeconds = this[Keys.lockAfter] ?: 60,
        privacyScreen = this[Keys.privacy] ?: true,
    )

    suspend fun completeOnboarding() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[Keys.appLock] = on }
    suspend fun setLockAfterSeconds(s: Int) = context.dataStore.edit { it[Keys.lockAfter] = s }
    suspend fun setPrivacyScreen(on: Boolean) = context.dataStore.edit { it[Keys.privacy] = on }
    suspend fun setSmartSuggestions(on: Boolean) = context.dataStore.edit { it[Keys.suggestions] = on }
    suspend fun setFloatingButton(on: Boolean) = context.dataStore.edit { it[Keys.floating] = on }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDefaultCurrency(code: String) = context.dataStore.edit { it[Keys.currency] = code }
    suspend fun setNotificationEnabled(c: NotificationCategory, enabled: Boolean) =
        context.dataStore.edit { it[Keys.notif(c)] = enabled }
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
