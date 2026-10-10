package app.keeply.premium

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of truth for the user's tier.
 *
 * Phase 4 replaces [LocalEntitlementRepository] with a Play Billing implementation that
 * (1) queries purchases via BillingClient, (2) sends the purchase token to the Keeply
 * backend, which verifies it with the Google Play Developer API, and (3) only then
 * grants the tier. A client-side purchase result is never trusted on its own.
 *
 * Until that ships there is no way to become Premium in this build, so nothing here
 * pretends otherwise.
 */
interface EntitlementRepository {
    val tier: StateFlow<EntitlementTier>
    val usage: Flow<UsageAllowance>
    val purchasesAvailable: Boolean
}

class LocalEntitlementRepository : EntitlementRepository {
    override val tier: StateFlow<EntitlementTier> = MutableStateFlow(EntitlementTier.FREE)
    override val usage: Flow<UsageAllowance> = MutableStateFlow(UsageAllowance())
    override val purchasesAvailable: Boolean = false
}
