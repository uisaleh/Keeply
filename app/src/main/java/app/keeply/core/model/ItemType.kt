package app.keeply.core.model

/**
 * Every record in the vault is an item of one type. The unified `items` table
 * (see data/db) holds the shared fields; each type has its own detail table.
 *
 * SCREENSHOT is declared so limits, filters and analytics have a stable vocabulary;
 * [isAvailable] keeps it out of capture menus until it ships.
 */
enum class ItemType(val label: String, val pluralLabel: String, val isAvailable: Boolean) {
    RECEIPT("Receipt", "Receipts", true),
    WARRANTY("Warranty", "Warranties", true),
    LINK("Link", "Links", true),
    SUBSCRIPTION("Subscription", "Subscriptions", true),
    DOCUMENT("Document", "Documents", true),
    REMINDER("Reminder", "Reminders", true),
    SCREENSHOT("Screenshot", "Screenshots", false);

    companion object {
        val available: List<ItemType> = entries.filter { it.isAvailable }
    }
}
