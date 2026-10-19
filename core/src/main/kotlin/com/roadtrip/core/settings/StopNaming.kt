package com.roadtrip.core.settings

import com.roadtrip.core.common.Role
import com.roadtrip.core.sync.OutboxEntry
import com.roadtrip.core.sync.OutboxQueue

/** An open "Name this stop" dialog: which stop (by `stop_id`) and its pre-filled text. */
data class StopNameDraft(val stopId: String, val initialText: String)

sealed interface StopNameSave {
    data class Saved(val entry: OutboxEntry) : StopNameSave

    /** Blank after trimming: a name can be changed but never cleared (ANDSET-015). */
    data object Blank : StopNameSave
}

/**
 * Naming a checklist stop (docs/spec/07-settings.md, ANDSET-014/015): parent-only, the
 * stop addressed solely by its backend `stop_id`, saved through the offline outbox.
 */
object StopNaming {
    /** Long-press: a parent gets a draft pre-filled with label, else place; anyone else nothing. */
    fun open(row: StopRow, role: Role?): StopNameDraft? =
        if (role == Role.PARENT) StopNameDraft(row.stopId, row.label ?: row.place.orEmpty()) else null

    fun canSave(text: String): Boolean = text.isNotBlank()

    fun save(queue: OutboxQueue, stopId: String, text: String): StopNameSave =
        if (!canSave(text)) StopNameSave.Blank else StopNameSave.Saved(queue.enqueueStopLabel(stopId, text))
}
