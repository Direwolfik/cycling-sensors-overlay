package cz.novotny.cyclingsensorsoverlay

import cz.novotny.cyclingsensorsoverlay.domain.model.PowerData
import cz.novotny.cyclingsensorsoverlay.domain.usecase.Calculate3sPowerUseCase
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class Calculate3sPowerUseCaseTest {

    private val useCase = Calculate3sPowerUseCase()

    @Test
    fun calculate3sPower_computesMovingAverageWithin3Seconds() = runTest {
        val baseTime = 1000L
        val samples = listOf(
            PowerData(instantaneousPower = 200, timestamp = baseTime),
            PowerData(instantaneousPower = 250, timestamp = baseTime + 1000),
            PowerData(instantaneousPower = 300, timestamp = baseTime + 2000),
            PowerData(instantaneousPower = 350, timestamp = baseTime + 6000) // 6s later -> >3s window, previous samples dropped
        )

        val results = useCase(samples.asFlow()).toList()

        val expected = listOf(200, 225, 250, 350)
        val filteredResults = results.filterNotNull()
        assertEquals(expected, filteredResults)
    }
}
