package com.example.myapplication4.data

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.myapplication4.domain.ActivityPeriod
import java.time.Instant

/**
 * One indexed range scan, aggregated in SQLite. ZoneRules supplies the offset at every
 * DST/zone transition in this range, rather than applying today's offset to old reviews
 * or depending on SQLite's device-localtime implementation.
 */
fun studyActivityQuery(period: ActivityPeriod): SimpleSQLiteQuery {
    val args = mutableListOf<Any>()
    val rules = period.zone.rules
    var cursor = Instant.ofEpochMilli(period.startMillis)
    var offsetMillis = rules.getOffset(cursor).totalSeconds.toLong() * 1000
    val clauses = mutableListOf<String>()
    while (true) {
        val transition = rules.nextTransition(cursor) ?: break
        if (transition.instant.toEpochMilli() >= period.endMillis) break
        clauses += "WHEN reviewedAt < ? THEN ?"
        args += transition.instant.toEpochMilli()
        args += offsetMillis
        offsetMillis = transition.offsetAfter.totalSeconds.toLong() * 1000
        cursor = transition.instant
    }
    val offsetSql = if (clauses.isEmpty()) "?" else "CASE ${clauses.joinToString(" ")} ELSE ? END"
    args += offsetMillis
    args += period.startMillis
    args += period.endMillis
    return SimpleSQLiteQuery(
        "SELECT (reviewedAt + $offsetSql) / 86400000 AS epochDay, COUNT(*) AS reviewCount " +
            "FROM ReviewLogEntity WHERE reviewedAt >= ? AND reviewedAt < ? " +
            "GROUP BY epochDay ORDER BY epochDay",
        args.toTypedArray(),
    )
}
