package com.spoofer.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spoofer.location.mode.LocationMode
import com.spoofer.location.mode.LocationModeSwitcher

@Composable
fun LocationModeSelector(
    modeSwitcher: LocationModeSwitcher,
    onModeChanged: (LocationMode) -> Unit = { },
) {
    val currentMode = modeSwitcher.currentModeString()

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Mode: $currentMode",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(onClick = {
            if (modeSwitcher.isDebugMode()) {
                onModeChanged(LocationMode.PatchedMode())
            } else {
                onModeChanged(LocationMode.DebugMode())
            }
        }) {
            Text("Toggle Mode")
        }
    }
}
