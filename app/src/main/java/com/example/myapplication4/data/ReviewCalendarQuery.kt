package com.example.myapplication4.data

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.myapplication4.domain.ReviewCalendarPeriod

/** One dueAt range scan and one grouping query for a month, including overdue in Today. */
fun reviewCalendarQuery(period: ReviewCalendarPeriod): SimpleSQLiteQuery {
    val args = mutableListOf<Any>()
    val buckets = period.dates.map { date ->
        args += date.plusDays(1).atStartOfDay(period.zone).toInstant().toEpochMilli()
        args += date.toEpochDay()
        "WHEN rs.dueAt < ? THEN ?"
    }
    // A past month has no upcoming dates and returns no rows.
    val dayExpression = if (buckets.isEmpty()) "0" else "CASE ${buckets.joinToString(" ")} END"
    args += period.today.atStartOfDay(period.zone).toInstant().toEpochMilli()
    args += period.startMillis
    args += period.endMillis
    return SimpleSQLiteQuery(
        "SELECT $dayExpression AS epochDay, COUNT(*) AS total, " +
            "SUM(CASE WHEN c.type='qa' THEN 1 ELSE 0 END) AS qa, " +
            "SUM(CASE WHEN c.type='cloze' THEN 1 ELSE 0 END) AS cloze, " +
            "SUM(CASE WHEN rs.reps=0 THEN 1 ELSE 0 END) AS unseen, " +
            "SUM(CASE WHEN rs.dueAt < ? THEN 1 ELSE 0 END) AS overdue " +
            "FROM ReviewStateEntity rs JOIN CardEntity c ON c.id=rs.cardId " +
            "JOIN LessonEntity l ON l.id=c.lessonId JOIN SubjectEntity s ON s.id=l.subjectId " +
            "WHERE c.suspended=0 AND l.archived=0 AND s.archived=0 AND rs.dueAt>=? AND rs.dueAt<? " +
            "GROUP BY epochDay ORDER BY epochDay", args.toTypedArray(),
    )
}
