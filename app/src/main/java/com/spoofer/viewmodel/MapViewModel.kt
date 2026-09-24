package com.spoofer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.spoofer.data.PreferencesDataStore
import com.spoofer.location.RealLocationProvider
import com.spoofer.model.SpeedMode
import com.spoofer.model.SpoofMode
import com.spoofer.model.TransportMode
import com.spoofer.service.MockLocationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        private val realLocationProvider: RealLocationProvider,
        val spoofLocationSource: com.spoofer.location.SpoofLocationSource,
        private val geocodingRepo: com.spoofer.data.GeocodingRepository,
        preferencesDataStore: PreferencesDataStore,
    ) : ViewModel() {
        val pcReceiverModeEnabled: StateFlow<Boolean> =
            preferencesDataStore.pcReceiverModeEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        suspend fun searchPlaces(query: String): List<com.spoofer.data.PlaceSuggestion> {
            val bias = _cameraPosition.value ?: _originLatLng.value
            return geocodingRepo.search(
                query = query,
                biasLat = bias?.latitude,
                biasLon = bias?.longitude,
            )
        }

        private val _targetLatLng = MutableStateFlow<LatLng?>(null)
        val targetLatLng: StateFlow<LatLng?> = _targetLatLng.asStateFlow()

        private val _originLatLng = MutableStateFlow<LatLng?>(null)
        val originLatLng: StateFlow<LatLng?> = _originLatLng.asStateFlow()

        private val _selectedMode = MutableStateFlow(SpoofMode.STATIC)
        val selectedMode: StateFlow<SpoofMode> = _selectedMode.asStateFlow()

        private val _cameraPosition = MutableStateFlow<LatLng?>(null)
        val cameraPosition: StateFlow<LatLng?> = _cameraPosition.asStateFlow()

        private val _speedKmh = MutableStateFlow(15f)
        val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()

        private val _transportMode = MutableStateFlow(TransportMode.CAR)
        val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

        private val _speedMode = MutableStateFlow(SpeedMode.MANUAL)
        val speedMode: StateFlow<SpeedMode> = _speedMode.asStateFlow()

        private val _joySpeedKmh = MutableStateFlow(5f)
        val joySpeedKmh: StateFlow<Float> = _joySpeedKmh.asStateFlow()

        private val _waypointStops = MutableStateFlow<List<WaypointStop>>(emptyList())
        val waypointStops: StateFlow<List<WaypointStop>> = _waypointStops.asStateFlow()

        private val _returnMode = MutableStateFlow(ReturnMode.NONE)
        val returnMode: StateFlow<ReturnMode> = _returnMode.asStateFlow()

        val currentSpeedKmh: StateFlow<Float> = spoofLocationSource.currentSpeedKmh

        val isSpoofing = MockLocationService.isActive
        val isPaused = MockLocationService.isPaused
        val currentSpoofedLocation = MockLocationService.currentLocation
        val spoofMode = MockLocationService.currentMode
        val elapsedSeconds = MockLocationService.elapsedSeconds
        val totalDistanceTraveled = MockLocationService.totalDistanceTraveled
        val currentHeading = MockLocationService.currentHeading
        val currentRoadSpeedLimitKmh = MockLocationService.currentRoadSpeedLimitKmh
        val pcReceiverConnected = MockLocationService.pcReceiverConnected
        val pcPosition = MockLocationService.pcPosition
        val pcReceiverPin = MockLocationService.pcReceiverPin

        fun setTarget(latLng: LatLng) {
            _targetLatLng.value = latLng
        }

        fun setOrigin(latLng: LatLng) {
            _originLatLng.value = latLng
        }

        fun swapOriginAndDestination() {
            val orig = _originLatLng.value
            val dest = _targetLatLng.value
            _originLatLng.value = dest
            _targetLatLng.value = orig
        }

        fun setMode(mode: SpoofMode) {
            _selectedMode.value = mode
        }

        fun setSpeedKmh(kmh: Float) {
            _speedKmh.value = kmh
        }

        fun setTransportMode(mode: TransportMode) {
            _transportMode.value = mode
            _speedKmh.value = mode.defaultSpeedKmh
        }

        fun setSpeedMode(mode: SpeedMode) {
            _speedMode.value = mode
        }

        fun setJoySpeedKmh(kmh: Float) {
            _joySpeedKmh.value = kmh
        }

        fun addWaypointStop() {
            _waypointStops.value = _waypointStops.value + WaypointStop()
        }

        fun updateWaypointStopText(
            index: Int,
            text: String,
        ) {
            _waypointStops.value =
                _waypointStops.value.toMutableList().also {
                    if (index in it.indices) it[index] = it[index].copy(text = text)
                }
        }

        fun setWaypointStopLocation(
            index: Int,
            latLng: LatLng,
        ) {
            _waypointStops.value =
                _waypointStops.value.toMutableList().also {
                    if (index in it.indices) it[index] = it[index].copy(latLng = latLng)
                }
        }

        fun removeWaypointStop(index: Int) {
            _waypointStops.value =
                _waypointStops.value.toMutableList().also {
                    if (index in it.indices) it.removeAt(index)
                }
            // Backtrack with no stops is identical to Loop — fall back to it explicitly
            // so the selected chip always matches what will actually happen.
            if (_waypointStops.value.isEmpty() && _returnMode.value == ReturnMode.BACKTRACK) {
                _returnMode.value = ReturnMode.LOOP
            }
        }

        fun setReturnMode(mode: ReturnMode) {
            _returnMode.value = mode
        }

        fun loadInitialLocation() {
            viewModelScope.launch {
                val location = realLocationProvider.getLastLocation()
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    _cameraPosition.value = latLng
                    _originLatLng.value = latLng
                    if (_targetLatLng.value == null) {
                        _targetLatLng.value = latLng
                    }
                } else {
                    val default = LatLng(DEFAULT_LAT, DEFAULT_LNG)
                    _cameraPosition.value = default
                    _originLatLng.value = default
                    _targetLatLng.value = default
                }
            }
        }

        init {
            // Keep the selected mode consistent with the Settings toggle: switching
            // into PC Receiver mode hides the on-device modes and vice versa, so the
            // current selection must always belong to whichever set is visible. Declared
            // last so every property it reads (_selectedMode) is already initialized —
            // StateFlow.collect emits synchronously on subscribe, so an init block placed
            // earlier in the class body would run before later properties exist.
            viewModelScope.launch {
                pcReceiverModeEnabled.collect { enabled ->
                    val onPcReceiver = _selectedMode.value == SpoofMode.PC_RECEIVER
                    if (enabled && !onPcReceiver) {
                        _selectedMode.value = SpoofMode.PC_RECEIVER
                    } else if (!enabled && onPcReceiver) {
                        _selectedMode.value = SpoofMode.STATIC
                    }
                }
            }
        }

        companion object {
            private const val DEFAULT_LAT = 28.6139
            private const val DEFAULT_LNG = 77.2090
        }
    }

/** An intermediate Directions stop between origin and destination. [latLng] is null until the typed/selected text resolves to a location. */
data class WaypointStop(
    val text: String = "",
    val latLng: LatLng? = null,
)

/** How a Directions route returns to its origin after reaching the destination, if at all. */
enum class ReturnMode {
    /** Ends at the destination — no return leg. */
    NONE,

    /** A new direct leg straight from the destination back to the origin. */
    LOOP,

    /** Retraces the same stops in reverse order back to the origin, instead of a new direct leg. */
    BACKTRACK,
}

/** Origin, resolved stops in order, destination, and — per [returnMode] — a path back to the origin. */
fun buildRouteWaypoints(
    origin: LatLng,
    stops: List<LatLng>,
    destination: LatLng,
    returnMode: ReturnMode,
): List<LatLng> {
    val points = mutableListOf(origin)
    points.addAll(stops)
    points.add(destination)
    when (returnMode) {
        ReturnMode.NONE -> {}
        ReturnMode.LOOP -> points.add(origin)
        ReturnMode.BACKTRACK -> {
            points.addAll(stops.asReversed())
            points.add(origin)
        }
    }
    return points
}
