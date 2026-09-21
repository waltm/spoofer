package com.spoofer.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.spoofer.data.DirectionsRepository
import com.spoofer.data.RouteInfo
import com.spoofer.data.gpx.GpxParser
import com.spoofer.data.gpx.GpxRoute
import com.spoofer.data.gpx.GpxWriter
import com.spoofer.service.MockLocationService
import com.spoofer.usecase.SpeedSimulationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStreamWriter
import javax.inject.Inject

@HiltViewModel
class SpoofViewModel
    @Inject
    constructor(
        private val application: Application,
        private val dataStore: DataStore<Preferences>,
        private val directionsRepo: DirectionsRepository,
    ) : ViewModel() {
        private val _showSetupDialog = MutableStateFlow(false)
        val showSetupDialog: StateFlow<Boolean> = _showSetupDialog.asStateFlow()

        private val _routeInfo = MutableStateFlow<RouteInfo?>(null)
        val routeInfo: StateFlow<RouteInfo?> = _routeInfo.asStateFlow()

        private val _routePreview = MutableStateFlow<List<LatLng>>(emptyList())
        val routePreview: StateFlow<List<LatLng>> = _routePreview.asStateFlow()

        private val _isLoadingRoute = MutableStateFlow(false)
    val isLoadingRoute: StateFlow<Boolean> = _isLoadingRoute.asStateFlow()

    private val _routeError = MutableStateFlow<String?>(null)
    val routeError: StateFlow<String?> = _routeError.asStateFlow()

    private val _importedGpxRoute = MutableStateFlow<GpxRoute?>(null)
    val importedGpxRoute: StateFlow<GpxRoute?> = _importedGpxRoute.asStateFlow()

    val remainingDistance: StateFlow<Double> = MockLocationService.remainingDistance

        fun startStaticSpoof(target: LatLng) {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_SET_STATIC
                    putExtra(MockLocationService.EXTRA_LATITUDE, target.latitude)
                    putExtra(MockLocationService.EXTRA_LONGITUDE, target.longitude)
                }
            application.startForegroundService(intent)
        }

        /** [waypoints] is an ordered list of 2+ points: origin, any stops, then a destination (which may be the origin again, for a round trip). */
        fun startDirectionsSpoof(
            waypoints: List<LatLng>,
            speedMps: Float,
        ) {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START_MOVEMENT
                    putParcelableArrayListExtra(MockLocationService.EXTRA_WAYPOINTS, ArrayList(waypoints))
                    putExtra(MockLocationService.EXTRA_SPEED, speedMps)
                }
            application.startForegroundService(intent)
        }

        fun startPcReceiver(origin: LatLng) {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START_PC_RECEIVER
                    putExtra(MockLocationService.EXTRA_LATITUDE, origin.latitude)
                    putExtra(MockLocationService.EXTRA_LONGITUDE, origin.longitude)
                }
            application.startForegroundService(intent)
        }

        fun stopSpoofing() {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_STOP
                }
            application.startService(intent)
        }

        fun pauseSpoofing() {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_PAUSE
                }
            application.startService(intent)
        }

        fun resumeSpoofing() {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_RESUME
                }
            application.startService(intent)
        }

        /** [waypoints] is an ordered list of 2+ points: origin, any stops, then a destination (which may be the origin again, for a round trip). */
        fun fetchRoutePreview(waypoints: List<LatLng>) {
            viewModelScope.launch {
                _isLoadingRoute.value = true
                try {
                    val route = directionsRepo.getRoute(waypoints)
                    _routeInfo.value = route
                    _routePreview.value = route.polyline
                    _routeError.value = null
                } catch (_: Exception) {
                    _routeInfo.value = null
                    _routePreview.value = emptyList()
                    _routeError.value = "Could not find a route. Check your locations."
                } finally {
                    _isLoadingRoute.value = false
                }
            }
        }

        fun clearRoutePreview() {
            _routeInfo.value = null
            _routePreview.value = emptyList()
            _importedGpxRoute.value = null
        }

        fun startGpxRouteSpoof(
            points: List<LatLng>,
            speedMps: Float,
            elevations: List<Double?>? = null,
        ) {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START_MOVEMENT_ROUTE
                    putParcelableArrayListExtra(MockLocationService.EXTRA_ROUTE_POINTS, ArrayList(points))
                    putExtra(MockLocationService.EXTRA_SPEED, speedMps)
                    if (elevations != null) {
                        // NaN is the "missing" sentinel — DoubleArray has no null elements.
                        putExtra(
                            MockLocationService.EXTRA_ROUTE_ELEVATIONS,
                            elevations.map { it ?: Double.NaN }.toDoubleArray(),
                        )
                    }
                }
            application.startForegroundService(intent)
        }

        fun importGpx(uri: Uri) {
            viewModelScope.launch {
                try {
                    val route =
                        withContext(Dispatchers.IO) {
                            application.contentResolver.openInputStream(uri)?.use { GpxParser.parse(it) }
                                ?: throw IOException("Could not open the selected file")
                        }
                    val latLngPoints = route.points.map { LatLng(it.latitude, it.longitude) }
                    val distanceMeters =
                        latLngPoints.zipWithNext { a, b -> SpeedSimulationUseCase.distanceBetween(a, b) }.sum()
                    _importedGpxRoute.value = route
                    _routePreview.value = latLngPoints
                    _routeInfo.value =
                        RouteInfo(
                            polyline = latLngPoints,
                            durationSeconds = (distanceMeters / 1.4).toInt(),
                            distanceMeters = distanceMeters.toInt(),
                        )
                    _routeError.value = null
                } catch (e: Exception) {
                    _importedGpxRoute.value = null
                    _routeError.value = "Could not read GPX file: ${e.message}"
                }
            }
        }

        fun exportGpx(uri: Uri) {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val points = _routePreview.value
                        val name = _importedGpxRoute.value?.name ?: "Spoofer route"
                        application.contentResolver.openOutputStream(uri)?.use { out ->
                            OutputStreamWriter(out).use { writer ->
                                GpxWriter.write(writer, name, points)
                            }
                        } ?: throw IOException("Could not open the destination file")
                    }
                    _routeError.value = null
                } catch (e: Exception) {
                    _routeError.value = "Could not export GPX file: ${e.message}"
                }
            }
        }

        fun checkMockLocationProvider() {
            viewModelScope.launch {
                val dismissed = dataStore.data.first()[KEY_SETUP_DISMISSED] ?: false
                if (dismissed) return@launch
                val allowMock =
                    try {
                        Settings.Secure.getInt(application.contentResolver, "mock_location", 0)
                    } catch (_: Exception) {
                        0
                    }
                if (allowMock == 0) {
                    _showSetupDialog.value = true
                }
            }
        }

        fun dismissSetupDialog() {
            _showSetupDialog.value = false
            viewModelScope.launch { dataStore.edit { it[KEY_SETUP_DISMISSED] = true } }
        }

        fun startJoystick(
            origin: LatLng,
            speedMps: Float,
        ) {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START_JOYSTICK
                    putExtra(MockLocationService.EXTRA_LATITUDE, origin.latitude)
                    putExtra(MockLocationService.EXTRA_LONGITUDE, origin.longitude)
                    putExtra(MockLocationService.EXTRA_SPEED, speedMps)
                }
            application.startForegroundService(intent)
        }

        fun updateJoystick(
            angle: Float,
            magnitude: Float,
            speedMps: Float? = null,
        ) {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_UPDATE_JOYSTICK
                    putExtra(MockLocationService.EXTRA_ANGLE, angle)
                    putExtra(MockLocationService.EXTRA_MAGNITUDE, magnitude)
                    if (speedMps != null) putExtra(MockLocationService.EXTRA_SPEED, speedMps)
                }
            application.startService(intent)
        }

        fun updateJoystickSpeed(speedMps: Float) {
            val intent =
                Intent(application, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_UPDATE_JOYSTICK
                    putExtra(MockLocationService.EXTRA_SPEED, speedMps)
                }
            application.startService(intent)
        }

        companion object {
            private val KEY_SETUP_DISMISSED = booleanPreferencesKey("setup_dismissed")
        }
    }
