package app.keeply

import android.content.Context
import app.keeply.attachments.AttachmentFiles
import app.keeply.auth.AccountSession
import app.keeply.auth.AuthRepository
import app.keeply.data.db.KeeplyDatabase
import app.keeply.data.repo.RoomVaultRepository
import app.keeply.data.settings.SettingsRepository
import app.keeply.links.LinkMetadataFetcher
import app.keeply.notifications.ReminderAlarms
import app.keeply.security.AppLock
import app.keeply.premium.EntitlementRepository
import app.keeply.premium.LocalEntitlementRepository
import app.keeply.sync.CloudSync
import app.keeply.sync.SyncOutbox
import kotlinx.coroutines.CoroutineScope

/**
 * Manual dependency container. Small enough that a DI framework would add more
 * build complexity than it removes; swap for Hilt if the module graph grows.
 */
class AppContainer(context: Context, appScope: CoroutineScope) {
    private val appContext = context.applicationContext
    val database: KeeplyDatabase by lazy { KeeplyDatabase.build(appContext) }
    val attachmentFiles: AttachmentFiles by lazy { AttachmentFiles(appContext) }
    val vault: RoomVaultRepository by lazy { RoomVaultRepository(database, attachmentFiles) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val entitlements: EntitlementRepository by lazy { LocalEntitlementRepository() }
    val linkMetadata: LinkMetadataFetcher by lazy { LinkMetadataFetcher() }
    val reminderAlarms: ReminderAlarms by lazy { ReminderAlarms(appContext) }
    val appLock: AppLock = AppLock()
    val auth: AuthRepository by lazy { AuthRepository() }
    val cloudSync: CloudSync by lazy { CloudSync(vault, appScope, SyncOutbox(appContext), attachmentFiles) }
    val session: AccountSession by lazy { AccountSession(auth, vault, cloudSync, appScope) }
}
