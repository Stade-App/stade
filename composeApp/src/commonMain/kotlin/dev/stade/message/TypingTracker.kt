package dev.stade.message

import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val TYPING_REFRESH_MS = 4000L
const val TYPING_IDLE_MS = 3000L
const val TYPING_EXPIRY_MS = 10000L

class TypingTracker {
    private val _typingContacts = MutableStateFlow<Set<String>>(emptySet())
    val typingContacts: StateFlow<Set<String>> = _typingContacts.asStateFlow()

    private val expiryJobs = mutableMapOf<String, Job>()
    private val lock = Mutex()

    fun start(sync: SyncEngine, scope: CoroutineScope) {
        sync.events.onEach { event ->
            when (event) {
                is SyncEngine.SyncEvent.TypingChanged -> apply(event.contactId, event.typing, scope)
                is SyncEngine.SyncEvent.ContactDisconnected -> apply(event.contactId, false, scope)
                is SyncEngine.SyncEvent.MessageReceived -> apply(event.contactId, false, scope)
                else -> {}
            }
        }.launchIn(scope)
    }

    private suspend fun apply(contactId: String, typing: Boolean, scope: CoroutineScope) {
        lock.withLock {
            expiryJobs.remove(contactId)?.cancel()
            if (typing) {
                _typingContacts.value = _typingContacts.value + contactId
                expiryJobs[contactId] = scope.launch {
                    delay(TYPING_EXPIRY_MS)
                    lock.withLock {
                        expiryJobs.remove(contactId)
                        _typingContacts.value = _typingContacts.value - contactId
                    }
                }
            } else {
                _typingContacts.value = _typingContacts.value - contactId
            }
        }
    }
}
