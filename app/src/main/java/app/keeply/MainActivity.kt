package app.keeply

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.keeply.security.IntentToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.keeply.ui.app.KeeplyRoot
import app.keeply.notifications.ReminderActionReceiver
import app.keeply.ui.app.LaunchEvent
import kotlinx.coroutines.flow.MutableStateFlow

/** FragmentActivity (not just ComponentActivity) because the system unlock prompt needs it. */
class MainActivity : FragmentActivity() {

    private val launchEvents = MutableStateFlow<LaunchEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as KeeplyApp).container
        // Privacy screen: no screenshots, and a blank card in recent apps.
        lifecycleScope.launch {
            container.settings.settings.collect { s ->
                if (s.privacyScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        setContent {
            val event by launchEvents.collectAsStateWithLifecycle()
            KeeplyRoot(launchEvent = event, onLaunchEventConsumed = { launchEvents.value = null })
        }
    }

    override fun onStart() {
        super.onStart()
        app.keeply.quickadd.FloatingAddService.setKeeplyInFront(true)
        val container = (application as KeeplyApp).container
        lifecycleScope.launch {
            container.appLock.onForeground(container.settings.settings.first().lockAfterSeconds)
        }
    }

    override fun onStop() {
        app.keeply.quickadd.FloatingAddService.setKeeplyInFront(false)
        (application as KeeplyApp).container.appLock.onBackground()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        // Reminder and item extras only count when they came from Keeply's own notifications.
        val trusted = IntentToken.isFromKeeply(this, intent)
        val alert = if (trusted) ReminderActionReceiver.alertOrNull(intent) else null
        when {
            intent.action == ACTION_QUICK_ADD -> launchEvents.value = LaunchEvent.QuickAdd
            alert != null -> launchEvents.value = LaunchEvent.Reminder(alert)
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(4_000)
                if (!text.isNullOrBlank()) launchEvents.value = LaunchEvent.SharedText(text)
            }
            trusted && intent.hasExtra(EXTRA_OPEN_ITEM_ID) -> {
                intent.getStringExtra(EXTRA_OPEN_ITEM_ID)?.let { launchEvents.value = LaunchEvent.OpenItem(it) }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ITEM_ID = "app.keeply.extra.OPEN_ITEM_ID"
        /** From the floating + button or the home-screen shortcut: open the Add menu. */
        const val ACTION_QUICK_ADD = "app.keeply.action.QUICK_ADD"
    }
}
