package it.stefazzi.pokerolesheets.data

import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class SheetWriteKey(
    val isPokemon: Boolean,
    val recordId: String,
) {
    val isPersisted: Boolean get() = recordId != NEW_RECORD

    companion object {
        private const val NEW_RECORD = "<new>"

        fun from(sheet: EditableSheet): SheetWriteKey = SheetWriteKey(
            isPokemon = sheet.isPokemon,
            recordId = sheet.recordId.ifBlank { NEW_RECORD },
        )
    }
}

internal enum class SheetWriteFailure {
    CONFLICT,
    TIMEOUT,
    NETWORK,
    AMBIGUOUS_SERVER,
    OTHER,
}

internal sealed interface SheetWriteResult<out T> {
    data class Success<T>(val value: T) : SheetWriteResult<T>
    data object AlreadyInFlight : SheetWriteResult<Nothing>
    data object ReloadRequired : SheetWriteResult<Nothing>
    data class Failure(val reason: SheetWriteFailure) : SheetWriteResult<Nothing>
}

internal class SheetWriteCoordinator(
    private val writeTimeout: Duration = 30.seconds,
) {
    private val inFlight = ConcurrentHashMap.newKeySet<SheetWriteKey>()
    private val reloadRequired = ConcurrentHashMap.newKeySet<SheetWriteKey>()

    suspend fun <T> execute(
        key: SheetWriteKey,
        onStarted: () -> Unit = {},
        operation: suspend () -> T,
    ): SheetWriteResult<T> {
        if (key in reloadRequired) return SheetWriteResult.ReloadRequired
        if (!inFlight.add(key)) return SheetWriteResult.AlreadyInFlight

        return try {
            onStarted()
            val value = withTimeout(writeTimeout) { operation() }
            reloadRequired.remove(key)
            SheetWriteResult.Success(value)
        } catch (error: SheetSaveConflictException) {
            reloadRequired.add(key)
            SheetWriteResult.Failure(SheetWriteFailure.CONFLICT)
        } catch (error: SheetSaveAmbiguousException) {
            requireReloadAfterAmbiguousFailure(key)
            SheetWriteResult.Failure(SheetWriteFailure.AMBIGUOUS_SERVER)
        } catch (error: TimeoutCancellationException) {
            requireReloadAfterAmbiguousFailure(key)
            SheetWriteResult.Failure(SheetWriteFailure.TIMEOUT)
        } catch (error: HttpRequestTimeoutException) {
            requireReloadAfterAmbiguousFailure(key)
            SheetWriteResult.Failure(SheetWriteFailure.TIMEOUT)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpRequestException) {
            requireReloadAfterAmbiguousFailure(key)
            SheetWriteResult.Failure(SheetWriteFailure.NETWORK)
        } catch (error: IOException) {
            requireReloadAfterAmbiguousFailure(key)
            SheetWriteResult.Failure(SheetWriteFailure.NETWORK)
        } catch (error: Exception) {
            SheetWriteResult.Failure(SheetWriteFailure.OTHER)
        } finally {
            inFlight.remove(key)
        }
    }

    fun clearReloadRequirement(key: SheetWriteKey) {
        reloadRequired.remove(key)
    }

    private fun requireReloadAfterAmbiguousFailure(key: SheetWriteKey) {
        if (key.isPersisted) reloadRequired.add(key)
    }
}
