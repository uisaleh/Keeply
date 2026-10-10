package app.keeply.premium

/**
 * Product configuration. Display prices shown to users must come from Google Play
 * ProductDetails at runtime (localised, regional, tax-correct). The list prices here
 * are only a labelled fallback for the plan-comparison copy before Play responds,
 * and are never used to charge anyone.
 */
data class PremiumProduct(
    val productId: String,
    val tier: EntitlementTier,
    val isRecurring: Boolean,
    val periodLabel: String,
    val listPriceUsd: String,
)

object PriceCatalog {
    val annual = PremiumProduct(
        productId = "keeply_premium_annual",
        tier = EntitlementTier.PREMIUM_YEARLY,
        isRecurring = true,
        periodLabel = "per year, renews automatically",
        listPriceUsd = "$19.99",
    )
    val lifetime = PremiumProduct(
        productId = "keeply_premium_lifetime",
        tier = EntitlementTier.PREMIUM_LIFETIME,
        isRecurring = false,
        periodLabel = "one-time purchase",
        listPriceUsd = "$49.99",
    )
    val all = listOf(annual, lifetime)
}
