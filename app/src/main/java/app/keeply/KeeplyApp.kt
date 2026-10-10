package app.keeply

import android.app.Application
import app.keeply.notifications.Notifications
import app.keeply.notifications.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import app.keeply.quickadd.FloatingAddService

class KeeplyApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        Notifications.createChannels(this)
        ReminderWorker.schedule(this)
        // Starts cloud sync for the signed-in account (and seeds demo data in debug builds).
        container.session.start()
        // Older test builds seeded sample items; clear any that are still on the device.
        appScope.launch { container.vault.deleteDemoData() }
        // Bring the floating + back after a restart if it was left on.
        appScope.launch {
            if (container.settings.settings.first().floatingButton) FloatingAddService.start(this@KeeplyApp)
        }
        // Keep the home-screen widget in step with subscriptions, the default currency and app lock.
        appScope.launch {
            kotlinx.coroutines.flow.combine(container.vault.observeSubscriptions(), container.settings.settings) { subs, s -> Triple(subs, s.defaultCurrency, s.appLock) }
                .distinctUntilChanged()
                .collectLatest {
                    kotlinx.coroutines.delay(500) // let a burst of changes (e.g. a sync) settle first
                    app.keeply.widget.SpendWidget.refresh(this@KeeplyApp)
                }
        }
        // Keep alarms in step with timed reminders: edits, deletions, sign-out and synced items.
        appScope.launch {
            kotlinx.coroutines.flow.combine(container.vault.observeReminderNotes(), container.vault.observeItemAlerts()) { n, i -> n to i }
                .collect { (notes, items) -> container.reminderAlarms.reconcile(notes, items) }
        }
    }
}
