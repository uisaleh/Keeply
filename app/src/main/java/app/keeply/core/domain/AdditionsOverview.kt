package app.keeply.core.domain

import app.keeply.core.model.ActivityEvent
import app.keeply.core.model.ActivityKind
import app.keeply.core.model.ItemType
import java.time.LocalDate
import java.time.ZoneId

/** Items added in one bucket (a day or a month). */
data class AddBucket(val start: LocalDate, val count: Int)

data class AdditionsSummary(
    val from: LocalDate,
    val to: LocalDate,
    val granularity: Granularity,
    val buckets: List<AddBucket>,
    /** How many of each type were added in the range, most first. */
    val byType: List<Pair<ItemType, Int>>,
    /** Per type, the count in each bucket (same order as [buckets]), for one line per type. */
    val series: Map<ItemType, List<Int>> = emptyMap(),
    val asOf: LocalDate = to,
) {
    val total get() = buckets.sumOf { it.count }
}

object AdditionsOverview {
    /** Counts "Added" history entries between [from] and [to], so deleted items still count. */
    fun compute(events: List<ActivityEvent>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): AdditionsSummary {
        val g = SpendingOverview.granularityFor(from, to)
        fun key(d: LocalDate) = if (g == Granularity.DAY) d else d.withDayOfMonth(1)
        val keys = generateSequence(key(from)) { if (g == Granularity.DAY) it.plusDays(1) else it.plusMonths(1) }
            .takeWhile { !it.isAfter(to) }.toList()
        val added = events.filter { it.kind == ActivityKind.ADDED }
            .map { it to it.at.atZone(zone).toLocalDate() }
            .filter { (_, d) -> !d.isBefore(from) && !d.isAfter(to) }
        val perBucket = added.groupingBy { (_, d) -> key(d) }.eachCount()
        val byType = added.groupingBy { (e, _) -> e.type }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
        val perTypeBucket = added.groupingBy { (e, d) -> e.type to key(d) }.eachCount()
        val series = byType.associate { (type, _) -> type to keys.map { perTypeBucket[type to it] ?: 0 } }
        return AdditionsSummary(from, to, g, keys.map { AddBucket(it, perBucket[it] ?: 0) }, byType, series, minOf(LocalDate.now(zone), to))
    }
}
