package com.kazisasa.app.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * v4 design spec §5.2: "timestamp becomes relative and monospaced."
 * Mirrors kazi-sasa-feed/scripts/site/app.js's timeAgo() exactly, same
 * consistency principle already applied to jobTypeValue()/remoteValue() in
 * FeedFilters.kt - "3d ago" on the app should mean the same thresholds as
 * "3d ago" on the shareable web site, not two independently-tuned formats.
 *
 * Falls back to an absolute date past 30 days, same as the web version -
 * "47d ago" stops being a useful at-a-glance unit past about a month.
 */
fun formatRelativeTime(postedAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
    if (postedAtMillis == null) return null
    val diffMillis = nowMillis - postedAtMillis
    if (diffMillis < 0) return null // future-dated - don't show something nonsensical, same guard as the web version

    val hours = diffMillis / (60 * 60 * 1000)
    return when {
        hours < 1 -> "Just posted"
        hours < 24 -> "${hours}h ago"
        else -> {
            val days = hours / 24
            if (days < 30) {
                "${days}d ago"
            } else {
                val instant = Instant.ofEpochMilli(postedAtMillis)
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(instant)
            }
        }
    }
}
