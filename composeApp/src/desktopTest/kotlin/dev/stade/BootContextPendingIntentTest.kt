package dev.stade

import dev.stade.db.DriverFactory
import dev.stade.security.FileVault
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun bootOnFreshVault(): BootContext {
    val root = File(
        System.getProperty("java.io.tmpdir"),
        "stade-boot-${Random.nextLong()}"
    ).apply { mkdirs() }
    val vault = FileVault(root)
    vault.setup("123456")
    return BootContext(vault, DriverFactory(), { emptyList() })
}

class BootContextPendingIntentTest {

    @Test
    fun anInviteSurvivesTheSessionLockingAndUnlocking() {
        val boot = bootOnFreshVault()
        val locked = boot.buildContainer()
        locked.pendingInvite.value = "STADE2-EXAMPLE-INVITE"
        runBlocking { locked.close() }

        val reopened = boot.buildContainer()
        assertNotSame(locked, reopened)
        assertEquals("STADE2-EXAMPLE-INVITE", reopened.pendingInvite.value)
        runBlocking { reopened.close() }
    }

    @Test
    fun aTappedNotificationSurvivesTheSessionLockingAndUnlocking() {
        val boot = bootOnFreshVault()
        val locked = boot.buildContainer()
        locked.pendingOpenChat.value = "contact-1"
        locked.pendingOpenStadium.value = "stadium-1"
        locked.pendingOpenGroup.value = "group-1"
        locked.pendingGoHome.value = true
        runBlocking { locked.close() }

        val reopened = boot.buildContainer()
        assertEquals("contact-1", reopened.pendingOpenChat.value)
        assertEquals("stadium-1", reopened.pendingOpenStadium.value)
        assertEquals("group-1", reopened.pendingOpenGroup.value)
        assertTrue(reopened.pendingGoHome.value)
        runBlocking { reopened.close() }
    }

    @Test
    fun nothingIsCarriedWhenThereWasNothingPending() {
        val boot = bootOnFreshVault()
        val first = boot.buildContainer()
        runBlocking { first.close() }

        val second = boot.buildContainer()
        assertNull(second.pendingInvite.value)
        assertNull(second.pendingOpenChat.value)
        assertFalse(second.pendingGoHome.value)
        runBlocking { second.close() }
    }

    @Test
    fun aLiveContainerIsReusedRatherThanRebuilt() {
        val boot = bootOnFreshVault()
        val first = boot.buildContainer()
        first.pendingInvite.value = "STADE2-STILL-OPEN"
        val second = boot.buildContainer()
        assertSame(first, second)
        assertEquals("STADE2-STILL-OPEN", second.pendingInvite.value)
        runBlocking { first.close() }
    }

    @Test
    fun closingMarksTheContainerClosedAndIsSafeToRepeat() {
        val boot = bootOnFreshVault()
        val container = boot.buildContainer()
        assertFalse(container.isClosed)
        runBlocking {
            container.close()
            container.close()
        }
        assertTrue(container.isClosed)
        assertNull(boot.activeContainer())
    }

    @Test
    fun closingWaitsForBackgroundQueriesInsteadOfCrashingThem() {
        val boot = bootOnFreshVault()
        val container = boot.buildContainer()
        val crash = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val rounds = java.util.concurrent.atomic.AtomicInteger(0)

        val probe = container.appScope.launch {
            try {
                while (isActive) {
                    container.db.stadeDbQueries.getKv("probe").executeAsOneOrNull()
                    rounds.incrementAndGet()
                    yield()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                crash.set(t)
            }
        }

        runBlocking {
            while (rounds.get() < 5) delay(10)
            container.close()
        }

        assertTrue(probe.isCompleted, "background work must be finished once close returns")
        assertNull(crash.get(), "a background query saw the database after it was closed: ${crash.get()}")
    }
}
