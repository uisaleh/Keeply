package app.keeply.premium

/**
 * What the user is entitled to. The UI never checks a raw "isPremium" boolean;
 * it asks [EntitlementPolicy] whether a specific action is allowed.
 *
 * Future tiers (PREMIUM_TRIAL) slot in here. Usage-metered server/AI features are
 * modelled separately as [UsageAllowance] so they never ride on the flat Premium tier.
 */
enum class EntitlementTier { FREE, PREMIUM_YEARLY, PREMIUM_LIFETIME }

val EntitlementTier.isPremium: Boolean get() = this != EntitlementTier.FREE

/** Placeholder for metered features (e.g. AI credits). Always zero until a backend grants them. */
data class UsageAllowance(val aiCreditsRemaining: Int = 0)

/** Features gated behind Premium. Only add an entry once the feature exists. */
enum class PremiumFeature {
    UNLIMITED_ITEMS,
}
