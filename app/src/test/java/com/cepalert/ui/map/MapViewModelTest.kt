package com.cepalert.ui.map

import app.cash.turbine.test
import com.cepalert.data.model.ForestZone
import com.cepalert.data.model.WeatherData
import com.cepalert.data.repository.ForestRepository
import com.cepalert.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MapViewModelTest {

    private val forestRepo = mockk<ForestRepository>()
    private val weatherRepo = mockk<WeatherRepository>()

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun zone(id: String) = ForestZone(
        id = id, centroidLat = 42.4, centroidLon = 1.5,
        bosqueCompatible = true, bosqueTipo = "pino",
        altitud = 1200, orientacion = "N", geometryJson = "{}"
    )

    private val goodWeather = WeatherData(
        humidity7dAvg = 80.0, rain10dTotal = 55.0, rain7dTotal = 30.0,
        rain14dTotal = 70.0, temp7dAvg = 15.0, temp7dMax = 22.0, temp7dMin = 8.0,
        daysSinceSignificantRain = 3, source = "test", updatedAtEpochMs = 0L
    )

    @Test fun loads_zones_and_emits_scored_state() = runTest {
        coEvery { forestRepo.loadZones() } returns listOf(zone("z1"))
        coEvery { weatherRepo.getWeather(any(), any()) } returns goodWeather
        val vm = MapViewModel(forestRepo, weatherRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(1, state.scoredZones.size)
            assertEquals(100, state.scoredZones.first().score!!.score)
            assertEquals(goodWeather, state.weather)
        }
    }

    @Test fun zone_with_failed_weather_has_null_score() = runTest {
        coEvery { forestRepo.loadZones() } returns listOf(zone("z1"))
        coEvery { weatherRepo.getWeather(any(), any()) } throws RuntimeException("net")
        val vm = MapViewModel(forestRepo, weatherRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertNull(state.scoredZones.first().score)
        }
    }
}
