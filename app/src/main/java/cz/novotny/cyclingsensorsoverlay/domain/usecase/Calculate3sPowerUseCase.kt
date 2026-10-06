package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.domain.model.PowerData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * Computes a sliding time-window 3-second average power output from live power sample streams.
 *
 * Uses Flow.[scan] to accumulate incoming power samples and prunes entries older than
 * 3,000 milliseconds relative to the latest sample's timestamp. Returns null if no valid
 * power samples exist within the current window.
 */
class Calculate3sPowerUseCase {
    /**
     * Transforms a stream of [PowerData] into a stream emitting the calculated 3-second average power in Watts.
     *
     * @param powerStream Hot stream emitting instantaneous power telemetry or null values.
     * @return Flow emitting rounded integer average power in Watts, or null if window is empty.
     */
    operator fun invoke(powerStream: Flow<PowerData?>): Flow<Int?> {
        return powerStream
            .scan(emptyList<PowerData>()) { accumulator, newSample ->
                if (newSample == null) return@scan accumulator
                val now = newSample.timestamp
                val windowStart = now - 3000L
                (accumulator + newSample).filter { it.timestamp >= windowStart }
            }
            .map { samples ->
                if (samples.isEmpty()) null
                else samples.map { it.instantaneousPower }.average().toInt()
            }
    }
}
