package app.keeply.quickadd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.keeply.KeeplyApp
import app.keeply.MainActivity
import app.keeply.R
import app.keeply.notifications.Notifications
import app.keeply.share.ShareSuggest
import app.keeply.share.ShareSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Smart suggestions: looks at new notifications on the phone and, when one is clearly a payment,
 * order or renewal, offers to save it. Text is analysed in memory and never stored or uploaded;
 * only a suggestion the user taps is passed into the Save to Keeply sheet.
 */
class SuggestionListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY) != 0) return
        val e = n.extras ?: return
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val text = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n").take(1_000)
        if (text.length < 8) return
        val suggestion = ShareSuggest.analyse(text)
        if (!suggestion.worthSuggesting) return

        scope.launch {
            val app = applicationContext as KeeplyApp
            if (!app.container.settings.settings.first().smartSuggestions) return@launch
            if (app.container.session.account.value == null) return@launch
            // One suggestion per message, and a quiet minute between suggestions.
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val key = text.hashCode()
            val now = System.currentTimeMillis()
            if (prefs.getInt("last_hash", 0) == key) return@launch
            if (now - prefs.getLong("last_at", 0) < 60_000) return@launch
            prefs.edit().putInt("last_hash", key).putLong("last_at", now).apply()
            suggest(this@SuggestionListener, suggestion, appLabel(sbn.packageName))
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("an app")

    companion object {
        private const val CHANNEL = "suggestions"
        private const val PREFS = "keeply_suggestions"

        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /**
         * Android 13+ locks notification access ("restricted settings") for apps that weren't
         * installed from an app store, until the person allows it from App info.
         */
        fun needsRestrictedUnlock(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 33) return false
            val installer = runCatching {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            }.getOrNull()
            return installer != "com.android.vending"
        }

        fun openAccessSettings(context: Context) {
            val intent = if (Build.VERSION.SDK_INT >= 30) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, SuggestionListener::class.java).flattenToString())
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        /** "Save this payment?" — tapping opens the Save to Keeply sheet with the text pre-filled. */
        fun suggest(context: Context, s: ShareSuggestion, from: String) {
            if (!Notifications.canPost(context)) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Save suggestions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Payments, orders and renewals Keeply can save for you"
                })
            }
            val type = s.types.first()
            val what = when (type) {
                app.keeply.core.model.ItemType.SUBSCRIPTION -> "subscription"
                app.keeply.core.model.ItemType.RECEIPT -> "payment"
                else -> type.label.lowercase()
            }
            val amount = s.amount?.let { a -> listOfNotNull(s.currency, a).joinToString(" ") }
            val open = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(context, s.text.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_keeply)
                .setContentTitle("Save this $what?")
                .setContentText(listOfNotNull(s.title.takeIf { it.isNotBlank() }, amount, "from $from").joinToString(" · "))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pi)
                .addAction(0, "Save to Keeply", pi)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(s.text.hashCode(), n)
            } catch (ignored: SecurityException) {
                // Permission revoked between check and post; nothing to do.
            }
        }
    }
}
