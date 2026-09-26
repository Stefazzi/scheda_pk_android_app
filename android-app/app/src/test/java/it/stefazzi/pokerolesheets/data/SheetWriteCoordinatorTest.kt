package it.stefazzi.pokerolesheets.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetWriteCoordinatorTest {
    private val trainer = SheetWriteKey(isPokemon = false, recordId = "trainer-1")
    private val pokemon = SheetWriteKey(isPokemon = true, recordId = "pokemon-1")

    @Test
    fun sameTargetIsSingleFlightAndDoesNotInvokeTheSecondOperation() = runBlocking {
        val coordinator = SheetWriteCoordinator()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()

        val first = async {
            coordinator.execute(trainer) {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                "saved"
            }
        }
        started.await()

        val duplicate = coordinator.execute(trainer) {
            calls.incrementAndGet()
            "duplicate"
        }

        assertSame(SheetWriteResult.AlreadyInFlight, duplicate)
        assertEquals(1, calls.get())
        release.complete(Unit)
        assertEquals(SheetWriteResult.Success("saved"), first.await())
    }

    @Test
    fun differentTargetsMaySaveIndependently() = runBlocking {
        val coordinator = SheetWriteCoordinator()
        val trainerStarted = CompletableDeferred<Unit>()
        val releaseTrainer = CompletableDeferred<Unit>()

        val first = async {
            coordinator.execute(trainer) {
                trainerStarted.complete(Unit)
                releaseTrainer.await()
                "trainer"
            }
        }
        trainerStarted.await()

        assertEquals(
            SheetWriteResult.Success("pokemon"),
            coordinator.execute(pokemon) { "pokemon" },
        )
        releaseTrainer.complete(Unit)
        assertEquals(SheetWriteResult.Success("trainer"), first.await())
    }

    @Test
    fun conflictIsTerminalUntilReloadAndIsNeverRetried() = runBlocking {
        val coordinator = SheetWriteCoordinator()
        val calls = AtomicInteger()

        val conflict = coordinator.execute(trainer) {
            calls.incrementAndGet()
            throw SheetSaveConflictException(IllegalStateException("server detail"))
        }

        assertEquals(
            SheetWriteResult.Failure(SheetWriteFailure.CONFLICT),
            conflict,
        )
        assertSame(
            SheetWriteResult.ReloadRequired,
            coordinator.execute(trainer) {
                calls.incrementAndGet()
                "must not run"
            },
        )
        assertEquals(1, calls.get())

        coordinator.clearReloadRequirement(trainer)
        assertEquals(SheetWriteResult.Success("saved"), coordinator.execute(trainer) { "saved" })
    }

    @Test
    fun timeoutRunsOnceAndRequiresReloadForAnExistingSheet() = runBlocking {
        val coordinator = SheetWriteCoordinator(writeTimeout = 20.milliseconds)
        val calls = AtomicInteger()

        val result = coordinator.execute(trainer) {
            calls.incrementAndGet()
            delay(500)
            "late"
        }

        assertEquals(SheetWriteResult.Failure(SheetWriteFailure.TIMEOUT), result)
        assertEquals(1, calls.get())
        assertSame(SheetWriteResult.ReloadRequired, coordinator.execute(trainer) { "must not run" })
    }

    @Test
    fun ambiguousNetworkFailureRunsOnceAndRequiresReload() = runBlocking {
        val coordinator = SheetWriteCoordinator()
        val calls = AtomicInteger()

        val result = coordinator.execute(trainer) {
            calls.incrementAndGet()
            throw IOException("sensitive transport detail")
        }

        assertEquals(SheetWriteResult.Failure(SheetWriteFailure.NETWORK), result)
        assertEquals(1, calls.get())
        assertSame(SheetWriteResult.ReloadRequired, coordinator.execute(trainer) { "must not run" })
    }

    @Test
    fun gatewayFailureRunsOnceAndRequiresReload() = runBlocking {
        val coordinator = SheetWriteCoordinator()
        val calls = AtomicInteger()

        val result = coordinator.execute(trainer) {
            calls.incrementAndGet()
            throw SheetSaveAmbiguousException(IllegalStateException("gateway detail"))
        }

        assertEquals(SheetWriteResult.Failure(SheetWriteFailure.AMBIGUOUS_SERVER), result)
        assertEquals(1, calls.get())
        assertSame(SheetWriteResult.ReloadRequired, coordinator.execute(trainer) { "must not run" })
    }

    @Test
    fun cancellationIsRethrownAndAlwaysReleasesSingleFlight() = runBlocking {
        val coordinator = SheetWriteCoordinator()
        val started = CompletableDeferred<Unit>()

        val cancelled = async {
            coordinator.execute(trainer) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        cancelled.cancelAndJoin()

        assertEquals(SheetWriteResult.Success("saved"), coordinator.execute(trainer) { "saved" })
    }

    @Test
    fun genericFailureReleasesSingleFlightWithoutResubmitting() = runBlocking {
        val coordinator = SheetWriteCoordinator()
        val calls = AtomicInteger()

        val failed = coordinator.execute(trainer) {
            calls.incrementAndGet()
            error("unexpected detail")
        }

        assertEquals(SheetWriteResult.Failure(SheetWriteFailure.OTHER), failed)
        assertEquals(1, calls.get())
        assertTrue(coordinator.execute(trainer) { "saved" } is SheetWriteResult.Success)
    }
}
