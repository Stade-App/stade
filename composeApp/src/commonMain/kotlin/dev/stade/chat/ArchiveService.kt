package dev.stade.chat

import dev.stade.identity.LocalIdentity
import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ArchiveService(
    private val archived: ArchivedChats,
    private val sync: SyncEngine
) {
    fun start(owner: LocalIdentity, scope: CoroutineScope) {
        sync.events.onEach { event ->
            if (!archived.autoUnarchiveOnMessage()) return@onEach
            val key = when (event) {
                is SyncEngine.SyncEvent.MessageReceived -> event.contactId
                is SyncEngine.SyncEvent.GroupMessageReceived -> "grp_${event.groupId}"
                is SyncEngine.SyncEvent.StadiumMessageReceived -> "std_${event.stadiumId}"
                else -> return@onEach
            }
            runCatching { archived.setArchived(owner.id, key, false) }
        }.launchIn(scope)
    }
}
