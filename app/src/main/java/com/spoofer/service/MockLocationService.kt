package com.spoofer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.google.android.gms.maps.model.LatLng
import com.spoofer.data.PreferencesDataStore
import com.spoofer.data.repository.HistoryRepository
import com.spoofer.location.MockLocationProvider
import com.spoofer.model.SpoofMode
import com.spoofer.network.PcReceiverServer
import com.spoofer.usecase.SpeedSimulationUseCase
import com.spoofer.usecase.StaticSpoofUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * A location received over the PC receiver socket, alongside the monotonic clock
 * reading at which it arrived. The timestamp is what makes the readout useful:
 * a PC that has stopped sending looks identical to one resending the same
 * coordinate, unless you can see how long ago the last message landed.
 */
data class PcPosition(
    val lat: Double,
    val lng: Double,
    val receivedAtElapsedMs: Long,
)

@AndroidEntryPoint
class MockLocationService : Service() {
    @Inject lateinit var mockLocationProvider: MockLocationProvider

    @Inject lateinit var staticSpoofUseCase: StaticSpoofUseCase

    @Inject lateinit var speedSimulationUseCase: SpeedSimulationUseCase

    @Inject lateinit var historyRepo: HistoryRepository

    @Inject lateinit var spoofLocationSource: com.spoofer.location.SpoofLocationSource

    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    @Inject lateinit var locationModeSwitcher: com.spoofer.location.mode.LocationModeSwitcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    @Volatile private var tickIntervalMs: Long = DEFAULT_TICK_INTERVAL_MS

    @Volatile private var jitterEnabledSetting: Boolean = true

    @Volatile private var jitterIntensitySetting: Float = 2f

    @Volatile private var isPaused: Boolean = false

    @Volatile private var elevationEnabledSetting: Boolean = false

    @Volatile private var emittedAltitude: Double = 0.0

    @Volatile private var altitudeInitialized = false

    private var spoofMode: SpoofMode = SpoofMode.STATIC
    private var staticLat = 0.0
    private var staticLng = 0.0
    private var joyAngle = 0f
    private var joyMagnitude = 0f
    private var joySpeed = 0f
    private var speedMps = 0f
    private var destLat = 0.0
    private var destLng = 0.0
    private var historySessionId: Long = -1
    private var lastNotifText = ""
    private var pcReceiverServer: PcReceiverServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mockLocationProvider.addTestProvider()

        scope.launch {
            preferencesDataStore.gpsUpdateInterval.collect { tickIntervalMs = it.coerceAtLeast(MIN_TICK_INTERVAL_MS) }
        }
        scope.launch {
            preferencesDataStore.jitterEnabled.collect { jitterEnabledSetting = it }
        }
        scope.launch {
            preferencesDataStore.jitterIntensity.collect { jitterIntensitySetting = it }
        }
        scope.launch {
            preferencesDataStore.elevationEnabled.collect { elevationEnabledSetting = it }
        }
        scope.launch {
            preferencesDataStore.locationMode.collect { name ->
                com.spoofer.location.mode.LocationMode.fromString(name)?.let {
                    locationModeSwitcher.setMode(it)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_SET_STATIC -> {
                spoofMode = SpoofMode.STATIC
                staticLat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                staticLng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                startSpoofing()
            }
            ACTION_START_JOYSTICK -> {
                spoofMode = SpoofMode.JOYSTICK
                staticLat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                staticLng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                joySpeed = intent.getFloatExtra(EXTRA_SPEED, 5f)
                joyAngle = 0f
                joyMagnitude = 0f
                startSpoofing()
            }
            ACTION_UPDATE_JOYSTICK -> {
                joyAngle = intent.getFloatExtra(EXTRA_ANGLE, joyAngle)
                joyMagnitude = intent.getFloatExtra(EXTRA_MAGNITUDE, joyMagnitude)
                if (intent.hasExtra(EXTRA_SPEED)) {
                    joySpeed = intent.getFloatExtra(EXTRA_SPEED, joySpeed)
                }
            }
            ACTION_START_MOVEMENT -> {
                spoofMode = SpoofMode.DIRECTIONS
                val waypoints =
                    androidx.core.content.IntentCompat.getParcelableArrayListExtra(
                        intent, EXTRA_WAYPOINTS, LatLng::class.java,
                    ) ?: arrayListOf()
                speedMps = intent.getFloatExtra(EXTRA_SPEED, 4.17f)
                if (waypoints.size >= 2) {
                    staticLat = waypoints.first().latitude
                    staticLng = waypoints.first().longitude
                    destLat = waypoints.last().latitude
                    destLng = waypoints.last().longitude
                    // Bug 5 fix: initialize the route BEFORE starting the ticker so
                    // the first tick has valid polyline data and doesn't freeze/teleport.
                    scope.launch(Dispatchers.IO) {
                        speedSimulationUseCase.initialize(waypoints, fetchElevation = elevationEnabledSetting)
                        _remainingDistance.value = speedSimulationUseCase.remainingDistance
                        startSpoofing()
                    }
                }
                return START_STICKY
            }
            ACTION_START_MOVEMENT_ROUTE -> {
                spoofMode = SpoofMode.DIRECTIONS
                val points =
                    androidx.core.content.IntentCompat.getParcelableArrayListExtra(
                        intent, EXTRA_ROUTE_POINTS, LatLng::class.java,
                    ) ?: arrayListOf()
                val elevations =
                    intent.getDoubleArrayExtra(EXTRA_ROUTE_ELEVATIONS)?.map { if (it.isNaN()) null else it }
                speedMps = intent.getFloatExtra(EXTRA_SPEED, 4.17f)
                if (points.size >= 2) {
                    staticLat = points.first().latitude
                    staticLng = points.first().longitude
                    destLat = points.last().latitude
                    destLng = points.last().longitude
                    // Same ordering as Bug 5's fix above: initialize the route before
                    // starting the ticker so the first tick has valid polyline data.
                    scope.launch(Dispatchers.IO) {
                        speedSimulationUseCase.initializeWithPolyline(
                            points, elevations, fetchElevationIfMissing = elevationEnabledSetting,
                        )
                        _remainingDistance.value = speedSimulationUseCase.remainingDistance
                        startSpoofing()
                    }
                }
                return START_STICKY
            }
            ACTION_START_PC_RECEIVER -> {
                spoofMode = SpoofMode.PC_RECEIVER
                staticLat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                staticLng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                startPcReceiver()
                startSpoofing()
            }
            ACTION_PAUSE -> {
                isPaused = true
                _isPaused.value = true
                forceNotificationRefresh()
            }
            ACTION_RESUME -> {
                isPaused = false
                _isPaused.value = false
                forceNotificationRefresh()
            }
            ACTION_STOP -> stopSpoofing()
        }

        return START_STICKY
    }

    private fun startPcReceiver() {
        pcReceiverServer?.stop()
        pcReceiverServer =
            PcReceiverServer(
                onLocation = { lat, lng ->
                    staticLat = lat
                    staticLng = lng
                    _pcPosition.value =
                        PcPosition(lat, lng, android.os.SystemClock.elapsedRealtime())
                },
                onConnectionStateChanged = { connected ->
                    _pcReceiverConnected.value = connected
                    // A dropped link means the last position is no longer live;
                    // clearing it keeps the readout from showing stale data that
                    // would look identical to fresh data on screen.
                    if (!connected) _pcPosition.value = null
                },
                onPinChanged = { pin -> _pcReceiverPin.value = pin },
            ).also { it.start() }
    }

    private fun startSpoofing() {
        // Bug 6 fix: always cancel the previous ticker job before resetting state
        // to prevent race conditions when SpeedSimulationUseCase (a singleton) is
        // re-initialized for a new route while the old ticker is still running.
        tickerJob?.cancel()
        tickerJob = null

        _isActive.value = true
        _currentMode.value = spoofMode
        _elapsedSeconds.value = 0L
        _totalDistanceTraveled.value = 0.0
        _remainingDistance.value = 0.0
        _currentHeading.value = 0f
        _currentRoadSpeedLimitKmh.value = null
        isPaused = false
        _isPaused.value = false
        altitudeInitialized = false

        spoofLocationSource.enterSpoofMode()

        val notification = buildNotification(staticLat, staticLng)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        tickerJob?.cancel()
        tickerJob =
            scope.launch {
                logHistoryStart()
                val startRealTime = android.os.SystemClock.elapsedRealtime()
                while (true) {
                    _elapsedSeconds.value = (android.os.SystemClock.elapsedRealtime() - startRealTime) / 1000L

                    if (isPaused) {
                        // Movement is frozen, but keep emitting from the same tick loop so
                        // stationary GPS jitter still applies — a perfectly static coordinate
                        // is itself a signal real GPS never produces, even at rest.
                        val jittered =
                            staticSpoofUseCase.getJitteredLocation(
                                LatLng(staticLat, staticLng),
                                jitterEnabled = jitterEnabledSetting,
                                intensityMeters = jitterIntensitySetting,
                            )
                        locationModeSwitcher.setMockLocation(
                            jittered.latitude, jittered.longitude,
                            altitude = emittedAltitude,
                            speed = 0f,
                        )
                        spoofLocationSource.pushSpoofedLocation(jittered.latitude, jittered.longitude, 0f, 0f)
                        _currentLocation.value = jittered
                        updateNotification(staticLat, staticLng)
                        delay(tickIntervalMs)
                        continue
                    }

                    when (spoofMode) {
                        SpoofMode.STATIC -> {
                            val jittered =
                                staticSpoofUseCase.getJitteredLocation(
                                    LatLng(staticLat, staticLng),
                                    jitterEnabled = jitterEnabledSetting,
                                    intensityMeters = jitterIntensitySetting,
                                )
                            locationModeSwitcher.setMockLocation(jittered.latitude, jittered.longitude)
                            spoofLocationSource.pushSpoofedLocation(jittered.latitude, jittered.longitude)
                            _currentLocation.value = jittered
                        }
                        SpoofMode.JOYSTICK -> {
                            val radians = Math.toRadians(joyAngle.toDouble())
                            val joyMetersPerTick = (joySpeed * (tickIntervalMs / 1000f)).toDouble()
                            val deltaLat = joyMagnitude * joyMetersPerTick * Math.cos(radians) * METERS_PER_DEGREE_LAT
                            val deltaLng =
                                joyMagnitude * joyMetersPerTick * Math.sin(radians) *
                                        Math.cos(
                                            Math.toRadians(staticLat),
                                        ) * METERS_PER_DEGREE_LAT
                            staticLat += deltaLat
                            staticLng += deltaLng
                            val distanceThisTick = joyMagnitude * joyMetersPerTick
                            _totalDistanceTraveled.value += distanceThisTick
                            _currentHeading.value = joyAngle
                            val jittered =
                                staticSpoofUseCase.getJitteredLocation(
                                    LatLng(staticLat, staticLng),
                                    jitterEnabled = jitterEnabledSetting,
                                    intensityMeters = jitterIntensitySetting,
                                )
                            locationModeSwitcher.setMockLocation(
                                jittered.latitude, jittered.longitude,
                                bearing = joyAngle,
                                speed = joySpeed,
                            )
                            spoofLocationSource.pushSpoofedLocation(jittered.latitude, jittered.longitude, joyAngle, joySpeed)
                            _currentLocation.value = jittered
                        }
                        SpoofMode.PC_RECEIVER -> {
                            // staticLat/staticLng are updated asynchronously by
                            // PcReceiverServer as messages arrive; this tick just
                            // re-emits the latest value with the same jitter treatment
                            // STATIC mode uses.
                            val jittered =
                                staticSpoofUseCase.getJitteredLocation(
                                    LatLng(staticLat, staticLng),
                                    jitterEnabled = jitterEnabledSetting,
                                    intensityMeters = jitterIntensitySetting,
                                )
                            locationModeSwitcher.setMockLocation(jittered.latitude, jittered.longitude)
                            spoofLocationSource.pushSpoofedLocation(jittered.latitude, jittered.longitude)
                            _currentLocation.value = jittered
                        }
                        SpoofMode.DIRECTIONS -> {
                            // Cap to the current road segment's OSRM-assigned speed (a free,
                            // keyless proxy for its real-world speed limit) before applying
                            // jitter, so the spoofed speed never exceeds what's plausible for
                            // that stretch of road, regardless of the user's slider setting.
                            val roadSpeedLimitMps = speedSimulationUseCase.currentSegmentSpeedLimitMps
                            _currentRoadSpeedLimitKmh.value = roadSpeedLimitMps?.let { (it * 3.6).toFloat() }
                            val cappedSpeedMps =
                                if (roadSpeedLimitMps != null) minOf(speedMps, roadSpeedLimitMps.toFloat()) else speedMps
                            val speedVariation = cappedSpeedMps * (1f + (Random.nextFloat() - 0.5f) * 0.1f)
                            val metersPerTick = speedVariation * (tickIntervalMs / 1000f)
                            val result = speedSimulationUseCase.tick(metersPerTick)
                            if (result != null) {
                                val jittered =
                                    staticSpoofUseCase.getJitteredLocation(
                                        result.position,
                                        jitterEnabled = jitterEnabledSetting,
                                        intensityMeters = jitterIntensitySetting,
                                    )
                                if (!altitudeInitialized) {
                                    // Snap to the real value on the first tick instead of
                                    // ramping up from zero, which would otherwise take
                                    // minutes for a route with real elevation change.
                                    emittedAltitude = result.altitude
                                    altitudeInitialized = true
                                } else {
                                    val maxStep = MAX_VERTICAL_RATE_MPS * (tickIntervalMs / 1000.0)
                                    val delta = (result.altitude - emittedAltitude).coerceIn(-maxStep, maxStep)
                                    emittedAltitude += delta
                                }
                                locationModeSwitcher.setMockLocation(
                                    jittered.latitude, jittered.longitude,
                                    altitude = emittedAltitude,
                                    bearing = result.bearing,
                                    speed = speedVariation,
                                )
                                spoofLocationSource.pushSpoofedLocation(jittered.latitude, jittered.longitude, result.bearing, speedVariation)
                                _currentLocation.value = jittered
                                // Bug 8 fix: keep staticLat/Lng tracking the un-jittered route
                                // position so the notification and state don't drift off-route.
                                staticLat = result.position.latitude
                                staticLng = result.position.longitude
                                _totalDistanceTraveled.value = result.totalDistance
                                _remainingDistance.value = speedSimulationUseCase.remainingDistance
                                if (result.arrived) {
                                    stopSpoofing()
                                }
                            }
                        }
                    }

                    updateNotification(staticLat, staticLng)
                    delay(tickIntervalMs)
                }
            }
    }

    private fun stopSpoofing() {
        logHistoryEnd()
        tickerJob?.cancel()
        tickerJob = null
        pcReceiverServer?.stop()
        pcReceiverServer = null
        _pcReceiverConnected.value = false
        _pcPosition.value = null
        _pcReceiverPin.value = null
        _isActive.value = false
        _currentMode.value = null
        _currentLocation.value = null
        _elapsedSeconds.value = 0L
        _totalDistanceTraveled.value = 0.0
        _remainingDistance.value = 0.0
        _currentHeading.value = 0f
        _currentRoadSpeedLimitKmh.value = null
        isPaused = false
        _isPaused.value = false
        altitudeInitialized = false
        spoofLocationSource.exitSpoofMode()
        mockLocationProvider.removeTestProvider()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        logHistoryEnd()
        scope.cancel()
        pcReceiverServer?.stop()
        pcReceiverServer = null
        _pcPosition.value = null
        _pcReceiverPin.value = null
        mockLocationProvider.removeTestProvider()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Location Spoofing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when location spoofing is active"
                setSound(null, null)
                enableVibration(false)
            }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        lat: Double,
        lng: Double,
    ): Notification {
        val stopIntent =
            Intent(this, MockLocationService::class.java).apply {
                action = ACTION_STOP
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val pauseResumeIntent =
            Intent(this, MockLocationService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            }
        val pauseResumePendingIntent =
            PendingIntent.getService(
                this,
                1,
                pauseResumeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val pauseResumeIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val pauseResumeLabel = if (isPaused) "Resume" else "Pause"

        val coordinateText = formatCoordinate(lat, lng)
        val modeText =
            when (spoofMode) {
                SpoofMode.STATIC -> "Static"
                SpoofMode.DIRECTIONS -> "Directions"
                SpoofMode.JOYSTICK -> "Joystick"
                SpoofMode.PC_RECEIVER -> "PC Receiver"
            } + if (isPaused) " (Paused)" else ""

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Spoofing Active")
                .setContentText("$modeText • $coordinateText")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(pauseResumeIcon, pauseResumeLabel, pauseResumePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Location Spoofing Active")
                .setContentText("$modeText • $coordinateText")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(pauseResumeIcon, pauseResumeLabel, pauseResumePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
                .build()
        }
    }

    private fun updateNotification(
        lat: Double,
        lng: Double,
    ) {
        val coord = formatCoordinate(lat, lng)
        if (coord == lastNotifText) return
        lastNotifText = coord
        val notification = buildNotification(lat, lng)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun forceNotificationRefresh() {
        lastNotifText = ""
        updateNotification(staticLat, staticLng)
    }

    private fun formatCoordinate(
        lat: Double,
        lng: Double,
    ): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lngDir = if (lng >= 0) "E" else "W"
        return String.format("%.4f°%s, %.4f°%s", Math.abs(lat), latDir, Math.abs(lng), lngDir)
    }

    private fun logHistoryStart() {
        scope.launch {
            historySessionId =
                historyRepo.startSession(
                    mode = spoofMode.name,
                    lat = staticLat,
                    lng = staticLng,
                )
        }
    }

    private fun logHistoryEnd() {
        if (historySessionId <= 0) return
        val id = historySessionId
        historySessionId = -1
        val distance =
            if (spoofMode == SpoofMode.JOYSTICK || spoofMode == SpoofMode.DIRECTIONS) {
                _totalDistanceTraveled.value.toFloat()
            } else {
                null
            }
        scope.launch {
            historyRepo.endSession(id, distance)
        }
    }

    companion object {
        private const val CHANNEL_ID = "spoofing_channel"
        private const val NOTIFICATION_ID = 1001
        private const val DEFAULT_TICK_INTERVAL_MS = 1000L
        private const val MIN_TICK_INTERVAL_MS = 100L
        private const val MAX_VERTICAL_RATE_MPS = 0.5
        private const val METERS_PER_DEGREE_LAT = 1.0 / 111_320.0

        const val ACTION_SET_STATIC = "com.spoofer.action.SET_STATIC"
        const val ACTION_START_JOYSTICK = "com.spoofer.action.START_JOYSTICK"
        const val ACTION_UPDATE_JOYSTICK = "com.spoofer.action.UPDATE_JOYSTICK"
        const val ACTION_START_MOVEMENT = "com.spoofer.action.START_MOVEMENT"
        const val ACTION_START_MOVEMENT_ROUTE = "com.spoofer.action.START_MOVEMENT_ROUTE"
        const val ACTION_START_PC_RECEIVER = "com.spoofer.action.START_PC_RECEIVER"
        const val ACTION_PAUSE = "com.spoofer.action.PAUSE"
        const val ACTION_RESUME = "com.spoofer.action.RESUME"
        const val ACTION_STOP = "com.spoofer.action.STOP"

        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_DEST_LATITUDE = "dest_latitude"
        const val EXTRA_DEST_LONGITUDE = "dest_longitude"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_ANGLE = "angle"
        const val EXTRA_MAGNITUDE = "magnitude"
        const val EXTRA_WAYPOINTS = "waypoints"
        const val EXTRA_ROUTE_POINTS = "route_points"
        const val EXTRA_ROUTE_ELEVATIONS = "route_elevations"

        val isActive: StateFlow<Boolean>
            get() = _isActive.asStateFlow()
        private val _isActive = MutableStateFlow(false)

        val isPaused: StateFlow<Boolean>
            get() = _isPaused.asStateFlow()
        private val _isPaused = MutableStateFlow(false)

        val currentLocation: StateFlow<LatLng?>
            get() = _currentLocation.asStateFlow()
        private val _currentLocation = MutableStateFlow<LatLng?>(null)

        val currentMode: StateFlow<SpoofMode?>
            get() = _currentMode.asStateFlow()
        private val _currentMode = MutableStateFlow<SpoofMode?>(null)

        val elapsedSeconds: StateFlow<Long>
            get() = _elapsedSeconds.asStateFlow()
        private val _elapsedSeconds = MutableStateFlow(0L)

        val totalDistanceTraveled: StateFlow<Double>
            get() = _totalDistanceTraveled.asStateFlow()
        private val _totalDistanceTraveled = MutableStateFlow(0.0)

        val remainingDistance: StateFlow<Double>
            get() = _remainingDistance.asStateFlow()
        private val _remainingDistance = MutableStateFlow(0.0)

        val currentHeading: StateFlow<Float>
            get() = _currentHeading.asStateFlow()
        private val _currentHeading = MutableStateFlow(0f)

        val currentRoadSpeedLimitKmh: StateFlow<Float?>
            get() = _currentRoadSpeedLimitKmh.asStateFlow()
        private val _currentRoadSpeedLimitKmh = MutableStateFlow<Float?>(null)

        val pcReceiverConnected: StateFlow<Boolean>
            get() = _pcReceiverConnected.asStateFlow()
        private val _pcReceiverConnected = MutableStateFlow(false)

        val pcPosition: StateFlow<PcPosition?>
            get() = _pcPosition.asStateFlow()
        private val _pcPosition = MutableStateFlow<PcPosition?>(null)

        val pcReceiverPin: StateFlow<String?>
            get() = _pcReceiverPin.asStateFlow()
        private val _pcReceiverPin = MutableStateFlow<String?>(null)
    }
}