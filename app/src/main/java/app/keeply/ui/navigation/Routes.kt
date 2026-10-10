package app.keeply.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data class VaultRoute(val filter: String? = null)
@Serializable data object ActivityRoute
@Serializable data object SettingsRoute
@Serializable data object ProfileRoute
@Serializable data object SearchRoute
@Serializable data object PremiumRoute
@Serializable data object NotificationsRoute

@Serializable data class ReceiptFormRoute(val id: String? = null, val sharedText: String? = null)
@Serializable data class WarrantyFormRoute(val id: String? = null, val fromReceiptId: String? = null)
@Serializable data class SubscriptionFormRoute(val id: String? = null, val sharedText: String? = null)
@Serializable data class DocumentFormRoute(val id: String? = null)
@Serializable data class ReminderFormRoute(val id: String? = null, val sharedText: String? = null)
@Serializable data class LinkFormRoute(val id: String? = null, val sharedText: String? = null)

/** Read-only preview of any item; Edit and Delete live here. */
@Serializable data class ItemDetailRoute(val id: String)
