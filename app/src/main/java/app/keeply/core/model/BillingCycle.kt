package app.keeply.core.model

enum class BillingCycle(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
    /** Every [Subscription.customIntervalDays] days. */
    CUSTOM("Custom"),
}

enum class SubscriptionStatus { ACTIVE, CANCELLED }
