package com.spoofer.ui.screen

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.spoofer.data.RouteInfo
import com.spoofer.data.gpx.GpxRoute
import com.spoofer.model.SpeedMode
import com.spoofer.model.SpoofMode
import com.spoofer.model.TransportMode
import com.spoofer.service.PcPosition
import com.spoofer.ui.component.LocationInputField
import com.spoofer.ui.component.SpeedSlider
import com.spoofer.viewmodel.ReturnMode
import com.spoofer.viewmodel.WaypointStop
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.util.Locale

@Composable
fun BottomSheetContent(
    selectedMode: SpoofMode,
    onModeSelected: (SpoofMode) -> Unit,
    targetLatLng: LatLng?,
    isSpoofing: Boolean,
    onSaveFavorite: () -> Unit = {},
    originText: String = "",
    destText: String = "",
    onOriginTextChange: (String) -> Unit = {},
    onDestTextChange: (String) -> Unit = {},
    onOriginSelected: (LatLng) -> Unit = {},
    onSwap: () -> Unit = {},
    speedKmh: Float = 15f,
    onSpeedChange: (Float) -> Unit = {},
    speedMode: SpeedMode = SpeedMode.MANUAL,
    onSpeedModeChange: (SpeedMode) -> Unit = {},
    currentSpeedKmh: Float = 0f,
    transportMode: TransportMode = TransportMode.CAR,
    onTransportModeChange: (TransportMode) -> Unit = {},
    routeInfo: RouteInfo? = null,
    remainingDistance: Double? = null,
    isLoadingRoute: Boolean = false,
    routeError: String? = null,
    joySpeedKmh: Float = 5f,
    onJoySpeedChange: (Float) -> Unit = {},
    totalDistanceTraveled: Double = 0.0,
    currentHeading: Float = 0f,
    onSearchPlace: suspend (String) -> List<com.spoofer.data.PlaceSuggestion> = { emptyList() },
    onDestSelected: (LatLng) -> Unit = {},
    importedGpxRoute: GpxRoute? = null,
    onImportGpxClick: () -> Unit = {},
    onExportGpxClick: () -> Unit = {},
    onClearImportedRoute: () -> Unit = {},
    roadSpeedLimitKmh: Float? = null,
    waypointStops: List<WaypointStop> = emptyList(),
    onAddWaypointStop: () -> Unit = {},
    onWaypointStopTextChange: (Int, String) -> Unit = { _, _ -> },
    onWaypointStopSelected: (Int, LatLng) -> Unit = { _, _ -> },
    onRemoveWaypointStop: (Int) -> Unit = {},
    returnMode: ReturnMode = ReturnMode.NONE,
    onReturnModeChange: (ReturnMode) -> Unit = {},
    pcReceiverConnected: Boolean = false,
    pcReceiverModeEnabled: Boolean = false,
    pcPosition: PcPosition? = null,
    pcReceiverPin: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drag Handle
        Box(
            modifier =
                Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.height(16.dp))

        if (!pcReceiverModeEnabled) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode == SpoofMode.STATIC,
                    onClick = { onModeSelected(SpoofMode.STATIC) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(Icons.Default.LocationOn, null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                ) { Text("Static", style = MaterialTheme.typography.labelMedium) }
                SegmentedButton(
                    selected = selectedMode == SpoofMode.DIRECTIONS,
                    onClick = { onModeSelected(SpoofMode.DIRECTIONS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(Icons.Default.DirectionsWalk, null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                ) { Text("Directions", style = MaterialTheme.typography.labelMedium) }
                SegmentedButton(
                    selected = selectedMode == SpoofMode.JOYSTICK,
                    onClick = { onModeSelected(SpoofMode.JOYSTICK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(Icons.Default.Gamepad, null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                ) { Text("Joystick", style = MaterialTheme.typography.labelMedium) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(300)) { it * direction } + fadeIn(tween(200, delayMillis = 60)))
                    .togetherWith(slideOutHorizontally(tween(300)) { -it * direction } + fadeOut(tween(150)))
                    .using(SizeTransform(clip = false))
            },
            label = "mode_content",
        ) { mode ->
            when (mode) {
                SpoofMode.STATIC -> StaticModePanel(targetLatLng, onSaveFavorite)
                SpoofMode.DIRECTIONS ->
                    DirectionsModePanel(
                        originText = originText,
                        destText = destText,
                        onOriginTextChange = onOriginTextChange,
                        onDestTextChange = onDestTextChange,
                        onOriginSelected = onOriginSelected,
                        onDestSelected = onDestSelected,
                        onSearchPlace = onSearchPlace,
                        onSwap = onSwap,
                        speedKmh = speedKmh,
                        onSpeedChange = onSpeedChange,
                        speedMode = speedMode,
                        onSpeedModeChange = onSpeedModeChange,
                        currentSpeedKmh = currentSpeedKmh,
                        transportMode = transportMode,
                        onTransportModeChange = onTransportModeChange,
                        routeInfo = routeInfo,
                        remainingDistance = remainingDistance,
                        isLoadingRoute = isLoadingRoute,
                        routeError = routeError,
                        isSpoofing = isSpoofing,
                        importedGpxRoute = importedGpxRoute,
                        onImportGpxClick = onImportGpxClick,
                        onExportGpxClick = onExportGpxClick,
                        onClearImportedRoute = onClearImportedRoute,
                        roadSpeedLimitKmh = roadSpeedLimitKmh,
                        waypointStops = waypointStops,
                        onAddWaypointStop = onAddWaypointStop,
                        onWaypointStopTextChange = onWaypointStopTextChange,
                        onWaypointStopSelected = onWaypointStopSelected,
                        onRemoveWaypointStop = onRemoveWaypointStop,
                        returnMode = returnMode,
                        onReturnModeChange = onReturnModeChange,
                    )
                SpoofMode.JOYSTICK ->
                    JoystickPanel(
                        joySpeedKmh,
                        onJoySpeedChange,
                        totalDistanceTraveled,
                        currentHeading,
                        isSpoofing,
                    )
                SpoofMode.PC_RECEIVER ->
                    PcReceiverPanel(
                        isSpoofing = isSpoofing,
                        connected = pcReceiverConnected,
                        position = pcPosition,
                        pin = pcReceiverPin,
                    )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun StaticModePanel(
    targetLatLng: LatLng?,
    onSaveFavorite: () -> Unit,
) {
    if (targetLatLng == null) {
        androidx.compose.material3.Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Tap the map to select a target",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        androidx.compose.material3.Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Target Location",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    formatDms(targetLatLng.latitude, targetLatLng.longitude),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "%.6f, %.6f".format(targetLatLng.latitude, targetLatLng.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AssistChip(
                    onClick = onSaveFavorite,
                    label = { Text("Save to Favorites") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.StarBorder,
                            null,
                            Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PcReceiverPanel(
    isSpoofing: Boolean,
    connected: Boolean,
    position: PcPosition?,
    pin: String?,
) {
    androidx.compose.material3.Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Usb,
                null,
                tint =
                    if (connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    when {
                        !isSpoofing -> "Not listening"
                        connected -> "PC connected"
                        else -> "Waiting for PC…"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (connected && position != null) {
                    Spacer(Modifier.height(8.dp))
                    PositionReadout(position)
                }

                if (pin != null) {
                    Spacer(Modifier.height(8.dp))
                    val lanIp = getLanIpv4(LocalContext.current)
                    Text(
                        "WiFi: ${lanIp ?: "no LAN"}:${com.spoofer.network.PcReceiverServer.DEFAULT_PORT}" +
                                " · PIN: $pin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "adb forward tcp:${com.spoofer.network.PcReceiverServer.DEFAULT_PORT} " +
                            "tcp:${com.spoofer.network.PcReceiverServer.DEFAULT_PORT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PositionReadout(position: PcPosition) {
    // A ticker so "Updated X.Xs ago" keeps counting even when the PC has gone
    // quiet. LaunchedEffect restarts on every new position, so on a live link
    // `now` tracks the freshest render and the delay(500) is effectively unused;
    // on a stalled link it fires and the age visibly climbs — which is the whole
    // reason this line exists rather than just the coordinates.
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(position.receivedAtElapsedMs) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val ageSeconds = (now - position.receivedAtElapsedMs).coerceAtLeast(0L) / 1000.0

    Column {
        Text(
            "Lat: %.6f   Lng: %.6f".format(position.lat, position.lng),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Updated %.1fs ago".format(ageSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Best-effort IPv4 address of the phone on the active network, for display
 * next to the PIN. Recomputed on each recomposition (cheap; called at most
 * a few times a second) rather than remembered, so it picks up a WiFi change
 * without needing to be wired to a connectivity callback.
 */
private fun getLanIpv4(context: Context): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
    val network = cm.activeNetwork ?: return null
    val lp = cm.getLinkProperties(network) ?: return null
    return lp.linkAddresses
        .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
        ?.address?.hostAddress
}

@Composable
private fun DirectionsModePanel(
    originText: String,
    destText: String,
    onOriginTextChange: (String) -> Unit,
    onDestTextChange: (String) -> Unit,
    onOriginSelected: (LatLng) -> Unit,
    onDestSelected: (LatLng) -> Unit,
    onSearchPlace: suspend (String) -> List<com.spoofer.data.PlaceSuggestion>,
    onSwap: () -> Unit,
    speedKmh: Float,
    onSpeedChange: (Float) -> Unit,
    speedMode: SpeedMode,
    onSpeedModeChange: (SpeedMode) -> Unit,
    currentSpeedKmh: Float,
    transportMode: TransportMode,
    onTransportModeChange: (TransportMode) -> Unit,
    routeInfo: RouteInfo?,
    remainingDistance: Double?,
    isLoadingRoute: Boolean = false,
    routeError: String? = null,
    isSpoofing: Boolean,
    importedGpxRoute: GpxRoute? = null,
    onImportGpxClick: () -> Unit = {},
    onExportGpxClick: () -> Unit = {},
    onClearImportedRoute: () -> Unit = {},
    roadSpeedLimitKmh: Float? = null,
    waypointStops: List<WaypointStop> = emptyList(),
    onAddWaypointStop: () -> Unit = {},
    onWaypointStopTextChange: (Int, String) -> Unit = { _, _ -> },
    onWaypointStopSelected: (Int, LatLng) -> Unit = { _, _ -> },
    onRemoveWaypointStop: (Int) -> Unit = {},
    returnMode: ReturnMode = ReturnMode.NONE,
    onReturnModeChange: (ReturnMode) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        importedGpxRoute?.let { route ->
            AssistChip(
                onClick = onClearImportedRoute,
                label = {
                    Text(
                        "GPX: ${route.name} (${route.points.size} pts)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Route, null, Modifier.size(AssistChipDefaults.IconSize))
                },
                trailingIcon = {
                    Icon(Icons.Default.Close, "Clear imported route", Modifier.size(AssistChipDefaults.IconSize))
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        androidx.compose.material3.Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            Column {
                LocationInputField(
                    value = originText,
                    onValueChange = onOriginTextChange,
                    placeholder = "From",
                    onLocationSelected = onOriginSelected,
                    onSearch = onSearchPlace,
                    leadingIcon = {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        androidx.compose.material3.TextFieldDefaults.colors(
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                )

                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                waypointStops.forEachIndexed { index, stop ->
                    LocationInputField(
                        value = stop.text,
                        onValueChange = { onWaypointStopTextChange(index, it) },
                        placeholder = "Stop ${index + 1}",
                        onLocationSelected = { onWaypointStopSelected(index, it) },
                        onSearch = onSearchPlace,
                        leadingIcon = {
                            Icon(
                                Icons.Default.LocationOn,
                                null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { onRemoveWaypointStop(index) }) {
                                Icon(Icons.Default.Close, "Remove stop", Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            androidx.compose.material3.TextFieldDefaults.colors(
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                    )
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                LocationInputField(
                    value = destText,
                    onValueChange = onDestTextChange,
                    placeholder = "To",
                    onLocationSelected = onDestSelected,
                    onSearch = onSearchPlace,
                    leadingIcon = {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onSwap) {
                            Icon(Icons.Default.SwapVert, "Swap", Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        androidx.compose.material3.TextFieldDefaults.colors(
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        AssistChip(
            onClick = onAddWaypointStop,
            enabled = importedGpxRoute == null && waypointStops.size < MAX_WAYPOINT_STOPS,
            label = { Text("Add Stop", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(Icons.Default.Add, null, Modifier.size(AssistChipDefaults.IconSize))
            },
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Return to Start",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = returnMode == ReturnMode.NONE,
                onClick = { onReturnModeChange(ReturnMode.NONE) },
                enabled = importedGpxRoute == null,
                label = { Text("None", style = MaterialTheme.typography.labelMedium) },
            )
            FilterChip(
                selected = returnMode == ReturnMode.LOOP,
                onClick = { onReturnModeChange(ReturnMode.LOOP) },
                enabled = importedGpxRoute == null,
                label = { Text("Loop", style = MaterialTheme.typography.labelMedium) },
            )
            FilterChip(
                selected = returnMode == ReturnMode.BACKTRACK,
                onClick = { onReturnModeChange(ReturnMode.BACKTRACK) },
                // With no stops, retracing the route back is identical to looping
                // straight back — only offer it once there's something to retrace.
                enabled = importedGpxRoute == null && waypointStops.isNotEmpty(),
                label = { Text("Backtrack", style = MaterialTheme.typography.labelMedium) },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onImportGpxClick,
                label = { Text("Import GPX", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(Icons.Default.FileUpload, null, Modifier.size(AssistChipDefaults.IconSize))
                },
            )
            AssistChip(
                onClick = onExportGpxClick,
                enabled = routeInfo != null,
                label = { Text("Export GPX", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(AssistChipDefaults.IconSize))
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Transport Mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportMode.entries.forEach { mode ->
                FilterChip(
                    selected = transportMode == mode,
                    onClick = { onTransportModeChange(mode) },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = speedMode == SpeedMode.MANUAL,
                onClick = { onSpeedModeChange(SpeedMode.MANUAL) },
                label = { Text("Manual", style = MaterialTheme.typography.labelMedium) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
            FilterChip(
                selected = speedMode == SpeedMode.CURRENT,
                onClick = { onSpeedModeChange(SpeedMode.CURRENT) },
                label = { Text("Current Speed", style = MaterialTheme.typography.labelMedium) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }

        Spacer(Modifier.height(12.dp))

        when (speedMode) {
            SpeedMode.MANUAL -> SpeedSlider(speedKmh, onSpeedChange)
            SpeedMode.CURRENT -> {
                androidx.compose.material3.Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Your Speed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            String.format(Locale.US, "%.0f km/h", currentSpeedKmh),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (isLoadingRoute) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }

        AnimatedVisibility(
            visible = routeError != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
            exit = fadeOut(tween(150)),
        ) {
            if (routeError != null) {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            routeError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        routeInfo?.let { route ->
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors =
                    androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Route: ${formatDistance(route.distanceMeters.toDouble())}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "ETA: ${formatDuration(route.durationSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (isSpoofing && remainingDistance != null && routeInfo != null) {
            val progress =
                if (routeInfo.distanceMeters > 0) {
                    ((routeInfo.distanceMeters - remainingDistance) / routeInfo.distanceMeters).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${formatDistance(remainingDistance)} remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val etaSeconds =
                    if (speedMode == SpeedMode.CURRENT && currentSpeedKmh > 0) {
                        (remainingDistance / (currentSpeedKmh / 3.6)).toInt()
                    } else if (speedKmh > 0) {
                        (remainingDistance / (speedKmh / 3.6)).toInt()
                    } else {
                        0
                    }
                Text(
                    "ETA: ${formatDuration(etaSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            roadSpeedLimitKmh?.let { limit ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Speed,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Capped to road speed: %.0f km/h".format(limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun JoystickPanel(
    speedKmh: Float,
    onSpeedChange: (Float) -> Unit,
    totalDistanceTraveled: Double,
    currentHeading: Float,
    isSpoofing: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Drag the joystick on the map to move.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        // Bug 15 fix: cap joystick speed at 30 km/h so the slider gives fine-grained
        // control for manual movement (120 km/h default makes low speeds unworkable).
        SpeedSlider(speedKmh, onSpeedChange, maxKmh = 30f)
        if (isSpoofing) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Heading: ${currentHeading.toInt()}°",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Traveled: ${formatDistance(totalDistanceTraveled)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val MAX_WAYPOINT_STOPS = 8

private fun formatDms(
    lat: Double,
    lng: Double,
): String {
    val latDir = if (lat >= 0) "N" else "S"
    val lngDir = if (lng >= 0) "E" else "W"
    return String.format("%.4f\u00B0 %s, %.4f\u00B0 %s", Math.abs(lat), latDir, Math.abs(lng), lngDir)
}

private fun formatDistance(meters: Double): String = if (meters >= 1000) "%.1f km".format(meters / 1000) else "%.0f m".format(meters)

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}min" else "$m min"
}