package app.keeply.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ItemEntity::class, ItemFts::class, ReceiptEntity::class, ReceiptLineEntity::class,
        WarrantyEntity::class, SubscriptionEntity::class, SavedLinkEntity::class, DocumentEntity::class, ReminderNoteEntity::class, PaymentEntity::class, NotificationLogEntity::class,
        TagEntity::class, ItemTagCrossRef::class, ReminderEntity::class, AttachmentEntity::class, ActivityEventEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class KeeplyDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        fun build(context: Context): KeeplyDatabase =
            Room.databaseBuilder(context, KeeplyDatabase::class.java, "keeply.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                // Documents/screenshots/attachments arrive as explicit migrations in later
                // phases. No destructive fallback: user data must never be dropped silently.
                .build()
    }
}

/** v2: photo and logo attachments. SQL copied from schemas/2.json so Room's validation matches. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `items` ADD COLUMN `logoId` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `uploaded` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_itemId` ON `attachments` (`itemId`)")
    }
}

/** v3: Activity history. Existing items get an "Added" entry so the history isn't empty after updating. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activity_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `at` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_events_at` ON `activity_events` (`at`)")
        db.execSQL(
            "INSERT INTO `activity_events` (`itemId`, `type`, `title`, `kind`, `at`) " +
                "SELECT `id`, `type`, `title`, 'ADDED', `createdAt` FROM `items` WHERE `isDemo` = 0"
        )
    }
}

/**
 * v4: history syncs to the account. Rebuilds the table to add a cross-device `eventId`
 * (random for existing rows) and an `uploaded` flag (0, so existing history gets uploaded).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activity_events_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` TEXT NOT NULL, " +
                "`itemId` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `at` INTEGER NOT NULL, `uploaded` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `activity_events_new` (`id`, `eventId`, `itemId`, `type`, `title`, `kind`, `at`, `uploaded`) " +
                "SELECT `id`, lower(hex(randomblob(16))), `itemId`, `type`, `title`, `kind`, `at`, 0 FROM `activity_events`"
        )
        db.execSQL("DROP TABLE `activity_events`")
        db.execSQL("ALTER TABLE `activity_events_new` RENAME TO `activity_events`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_events_at` ON `activity_events` (`at`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_events_eventId` ON `activity_events` (`eventId`)")
    }
}

/** v5: documents (ID cards, passports, licences…) with an optional expiry date. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `documents` (`itemId` TEXT NOT NULL, `kind` TEXT NOT NULL, `number` TEXT NOT NULL, " +
                "`holder` TEXT NOT NULL, `issuer` TEXT NOT NULL, `issueEpochDay` INTEGER, `expiryEpochDay` INTEGER, " +
                "`reminderOffsets` TEXT NOT NULL, PRIMARY KEY(`itemId`), " +
                "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_expiryEpochDay` ON `documents` (`expiryEpochDay`)")
    }
}

/** v6: timed reminders (exams, birthdays, interviews…). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminder_notes` (`itemId` TEXT NOT NULL, `category` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, " +
                "`minuteOfDay` INTEGER NOT NULL, `repeat` TEXT NOT NULL, `alerts` TEXT NOT NULL, PRIMARY KEY(`itemId`), " +
                "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_notes_dateEpochDay` ON `reminder_notes` (`dateEpochDay`)")
    }
}

/** v7: optional alert time on receipts and links. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `items` ADD COLUMN `remindAtLocalMinute` INTEGER")
    }
}

/** v8: optional link on any item. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `items` ADD COLUMN `pageUrl` TEXT")
    }
}

/** v9: receipt and link alerts can repeat. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `items` ADD COLUMN `remindRepeat` TEXT")
    }
}

/** v10: marking items done/paid (with undo) and the subscription payment log. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `items` ADD COLUMN `markedAt` INTEGER")
        db.execSQL("ALTER TABLE `items` ADD COLUMN `markUndo` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `payments` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, `paidEpochDay` INTEGER NOT NULL, `forEpochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_itemId` ON `payments` (`itemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_paidEpochDay` ON `payments` (`paidEpochDay`)")
    }
}

/** v11: the Notifications page (reminders shown on this phone, read/unread). */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notification_log` (`notificationId` INTEGER NOT NULL, `itemId` TEXT, `category` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `details` TEXT, `occurrenceMinute` INTEGER, `postedAt` INTEGER NOT NULL, " +
                "`readAt` INTEGER, `outcome` TEXT, `snoozedUntil` INTEGER, PRIMARY KEY(`notificationId`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_log_postedAt` ON `notification_log` (`postedAt`)")
    }
}
