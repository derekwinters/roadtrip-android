package com.roadtrip.app.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.Avatar
import com.roadtrip.app.ui.common.OfflineBanner
import com.roadtrip.app.ui.common.formatFeedTime
import com.roadtrip.core.api.Profile
import com.roadtrip.core.journal.DeepLinkRouter
import com.roadtrip.core.journal.JournalComposer
import com.roadtrip.core.journal.JournalDisplay
import com.roadtrip.core.journal.JournalFeedItem
import com.roadtrip.core.journal.JournalFeedReducer
import com.roadtrip.core.journal.NavTarget
import com.roadtrip.core.sync.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The home screen: shared family feed (docs/spec/04-journal.md). All entry kinds render
 * distinctly (ANDJRNL-001); the composer queues offline posts that appear immediately
 * marked "syncing" (ANDJRNL-002); rows deep-link per kind (ANDJRNL-004); a refresh action
 * syncs forward and "load older" pages backward with the before cursor (ANDJRNL-005).
 */
@Composable
fun JournalScreen(
    container: AppContainer,
    profile: Profile,
    onNavigate: (NavTarget) -> Unit,
) {
    val tick by container.refreshTick.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()
    val scope = rememberCoroutineScope()
    var loadingOlder by remember { mutableStateOf(false) }

    val feed: List<JournalFeedItem> = remember(tick) {
        JournalFeedReducer.reduce(
            serverEntries = container.journalCache.read()?.value.orEmpty(),
            pendingOutbox = container.outboxStore.pending(),
            selfProfile = profile,
        )
    }

    // First entry: seed the cache from the server if we have nothing yet.
    LaunchedEffect(Unit) {
        if (container.journalCache.read() == null) {
            withContext(Dispatchers.IO) {
                runCatching { container.mergeJournalPage(container.api.getJournal(limit = 50)) }
            }
            container.bumpTick()
        }
    }

    fun loadOlder() {
        val oldest = container.journalCache.read()?.value?.minOfOrNull { it.seq } ?: return
        loadingOlder = true
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { container.mergeJournalPage(container.api.getJournal(before = oldest, limit = 50)) }
            }
            loadingOlder = false
            container.bumpTick()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!online) OfflineBanner("Offline — showing the saved journal; new posts will sync later")

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Family journal",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { container.requestSync(SyncTrigger.FOREGROUND) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(feed, key = { it.key }) { item ->
                JournalRow(item = item, onNavigate = onNavigate)
            }
            item {
                TextButton(
                    onClick = { loadOlder() },
                    enabled = online && !loadingOlder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loadingOlder) "Loading…" else "Load older entries")
                }
            }
        }

        ComposerBar(container = container)
    }
}

@Composable
private fun JournalRow(item: JournalFeedItem, onNavigate: (NavTarget) -> Unit) {
    val target = item.link?.let(DeepLinkRouter::route)
    Card(
        onClick = { target?.let(onNavigate) },
        enabled = target != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            when (val display = item.display) {
                is JournalDisplay.ManualPost -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(display.authorAvatar, display.authorName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            display.authorName ?: "Someone",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(display.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is JournalDisplay.StateCrossing -> IconRow(Icons.Filled.Flag, display.text)
                is JournalDisplay.Stop -> IconRow(Icons.Filled.Place, display.text)
                is JournalDisplay.GameResult -> IconRow(Icons.Filled.EmojiEvents, display.text)
                is JournalDisplay.LegArrival -> IconRow(Icons.Filled.SportsEsports, display.text, useIcon = false)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    formatFeedTime(item.clientTs),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                if (item.syncing) {
                    Text(
                        "syncing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconRow(icon: ImageVector, text: String, useIcon: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (useIcon) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Text("🏁", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Free-text composer, identical for every role (ANDJRNL-006). */
@Composable
private fun ComposerBar(container: AppContainer) {
    var text by remember { mutableStateOf("") }
    val valid = text.length in JournalComposer.MIN_CHARS..JournalComposer.MAX_CHARS

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Write something…") },
            supportingText = { Text("${text.length}/${JournalComposer.MAX_CHARS}") },
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                if (valid) {
                    container.postJournal(text)
                    text = ""
                }
            },
            enabled = valid,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post")
        }
    }
}
