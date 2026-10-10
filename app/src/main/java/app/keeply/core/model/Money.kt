package app.keeply.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money is stored as minor units (cents) plus an ISO-4217 code. Never as Double.
 * Amounts in different currencies are never summed together.
 */
data class Money(val amountMinor: Long, val currencyCode: String) {

    val currency: Currency get() = Currency.getInstance(currencyCode)

    fun toDecimal(): BigDecimal =
        BigDecimal.valueOf(amountMinor).movePointLeft(fractionDigits(currencyCode))

    fun format(locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getCurrencyInstance(locale)
        nf.currency = currency
        val digits = fractionDigits(currencyCode)
        nf.minimumFractionDigits = digits
        nf.maximumFractionDigits = digits
        return nf.format(toDecimal())
    }

    operator fun plus(other: Money): Money {
        require(other.currencyCode == currencyCode) { "Cannot add different currencies" }
        return Money(amountMinor + other.amountMinor, currencyCode)
    }

    companion object {
        fun fractionDigits(code: String): Int =
            Currency.getInstance(code).defaultFractionDigits.coerceAtLeast(0)

        fun fromDecimal(value: BigDecimal, currencyCode: String): Money =
            Money(
                value.setScale(fractionDigits(currencyCode), RoundingMode.HALF_UP)
                    .movePointRight(fractionDigits(currencyCode)).longValueExact(),
                currencyCode,
            )

        /** Parses user input like "1,299.00" or "22.99". Returns null if not a valid amount. */
        fun parse(input: String, currencyCode: String): Money? {
            val cleaned = input.trim().replace(",", "").replace(" ", "")
            if (cleaned.isEmpty()) return null
            val value = cleaned.toBigDecimalOrNull() ?: return null
            if (value.signum() < 0) return null
            if (value.scale() > fractionDigits(currencyCode)) return null
            return fromDecimal(value, currencyCode)
        }

        fun isValidCurrency(code: String): Boolean =
            runCatching { Currency.getInstance(code) }.isSuccess
    }
}
