package com.roadtrip.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FEED_TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm")

/** "Jul 18, 14:32"-style local timestamp for feed rows. */
fun formatFeedTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    FEED_TIME.withZone(zone).format(instant)

/** Circular avatar rendering the profile's avatar string (emoji) or a fallback initial. */
@Composable
fun Avatar(avatar: String?, name: String?, size: Int = 40) {
    val label = avatar?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }?.take(1)
        ?: "?"
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = (size / 2).sp)
    }
}

/** Online/offline indicator driven by /api/health reachability (AND-006). */
@Composable
fun OnlineBadge(online: Boolean) {
    Icon(
        imageVector = if (online) Icons.Filled.Wifi else Icons.Filled.WifiOff,
        contentDescription = if (online) "Online" else "Offline",
        tint = if (online) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

/** Banner explaining reduced functionality while offline (AND-005, ANDGAME-008). */
@Composable
fun OfflineBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
