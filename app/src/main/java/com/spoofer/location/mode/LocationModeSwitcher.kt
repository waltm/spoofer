package com.spoofer.location.mode

import android.content.Context
import com.spoofer.location.JsonPatchedClient
import com.spoofer.location.MockLocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationModeSwitcher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val mockLocationProvider: MockLocationProvider,
        private val jsonPatchedClient: JsonPatchedClient,
    ) {
        private var currentMode: LocationMode? = null

        fun setMode(mode: LocationMode) {
            currentMode = mode
        }

        fun isDebugMode(): Boolean = currentMode is LocationMode.DebugMode

        fun isPatchedMode(): Boolean = currentMode is LocationMode.PatchedMode

        fun setMockLocation(
            latitude: Double,
            longitude: Double,
            accuracy: Float = 10f,
            altitude: Double = 0.0,
            bearing: Float = 0f,
            speed: Float = 0f,
        ) {
            currentMode?.let { mode ->
                when (mode) {
                    is LocationMode.DebugMode -> {
                        mockLocationProvider.setMockLocation(
                            latitude,
                            longitude,
                            accuracy,
                            altitude,
                            bearing,
                            speed,
                        )
                    }
                    is LocationMode.PatchedMode -> {
                        val json = jsonPatchedClient.generatePositionJson(latitude, longitude)
                        android.util.Log.d("LocationModeSwitcher", "Patched JSON: $json")
                    }
                }
            }
        }

        fun setSpeed(speedKmh: Double) {
            currentMode?.let { mode ->
                when (mode) {
                    is LocationMode.DebugMode -> {}
                    is LocationMode.PatchedMode -> {
                        android.util.Log.d("LocationModeSwitcher", "Patched speed: $speedKmh km/h")
                    }
                }
            }
        }

        fun setAltitude(meters: Double) {
            currentMode?.let { mode ->
                when (mode) {
                    is LocationMode.DebugMode -> {}
                    is LocationMode.PatchedMode -> {
                        android.util.Log.d("LocationModeSwitcher", "Patched altitude: $meters m")
                    }
                }
            }
        }

        fun setBearing(degrees: Double) {
            currentMode?.let { mode ->
                when (mode) {
                    is LocationMode.DebugMode -> {}
                    is LocationMode.PatchedMode -> {
                        android.util.Log.d("LocationModeSwitcher", "Patched bearing: ${degrees}°")
                    }
                }
            }
        }

        fun currentModeString(): String {
            return currentMode?.javaClass?.simpleName ?: "none"
        }

        fun reset() {
            currentMode = null
        }
    }
