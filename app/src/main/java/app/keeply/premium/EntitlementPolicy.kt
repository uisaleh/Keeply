package app.keeply.premium

import app.keeply.core.model.ItemType

/** Free-plan allowances. Generous on purpose: the free tier must genuinely solve the problem. */
/** Free plan: 5 of each type, 30 in total (so the per-type numbers always add up to the total). */
object FreePlanLimits {
    const val PER_TYPE = 5
    const val TOTAL_ITEMS = 30
    val perType: Map<ItemType, Int> = ItemType.available.associateWith { PER_TYPE }
}

sealed interface AddDecision {
    data object Allowed : AddDecision
    data class TypeLimitReached(val type: ItemType, val limit: Int, val stillAvailable: Map<ItemType, Int> = emptyMap()) : AddDecision
    data class TotalLimitReached(val limit: Int) : AddDecision
}

object EntitlementPolicy {

    /**
     * Demo items never count against limits. Editing an existing item is always allowed;
     * this is only consulted when creating a new one.
     */
    fun canAdd(tier: EntitlementTier, type: ItemType, countsByType: Map<ItemType, Int>): AddDecision {
        if (tier.isPremium) return AddDecision.Allowed
        val typeLimit = FreePlanLimits.perType[type] ?: return AddDecision.Allowed
        val typeCount = countsByType[type] ?: 0
        if (typeCount >= typeLimit) return AddDecision.TypeLimitReached(type, typeLimit, remaining(countsByType))
        val total = countsByType.values.sum()
        if (total >= FreePlanLimits.TOTAL_ITEMS) return AddDecision.TotalLimitReached(FreePlanLimits.TOTAL_ITEMS)
        return AddDecision.Allowed
    }

    /** Types that still have free space, with how many are left of each. */
    fun remaining(countsByType: Map<ItemType, Int>): Map<ItemType, Int> {
        val totalLeft = FreePlanLimits.TOTAL_ITEMS - countsByType.values.sum()
        if (totalLeft <= 0) return emptyMap()
        return FreePlanLimits.perType
            .mapValues { (t, limit) -> minOf(limit - (countsByType[t] ?: 0), totalLeft) }
            .filterValues { it > 0 }
    }

    fun hasFeature(tier: EntitlementTier, feature: PremiumFeature): Boolean = tier.isPremium
}
