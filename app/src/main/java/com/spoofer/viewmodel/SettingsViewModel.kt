package com.spoofer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoofer.data.PreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val prefs: PreferencesDataStore,
) : ViewModel() {
    val gpsUpdateInterval: StateFlow<Long> =
        prefs.gpsUpdateInterval
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1000L)

    val jitterEnabled: StateFlow<Boolean> =
        prefs.jitterEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val jitterIntensity: StateFlow<Float> =
        prefs.jitterIntensity
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2f)

    val defaultTransportMode: StateFlow<String> =
        prefs.defaultTransportMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "CYCLE")

    val darkTheme: StateFlow<Boolean> =
        prefs.darkTheme
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val elevationEnabled: StateFlow<Boolean> =
        prefs.elevationEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pcReceiverModeEnabled: StateFlow<Boolean> =
        prefs.pcReceiverModeEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val locationMode: StateFlow<String> =
        prefs.locationMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "debug")

    fun setGpsUpdateInterval(ms: Long) {
        viewModelScope.launch { prefs.setGpsUpdateInterval(ms) }
    }

    fun setJitterEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setJitterEnabled(enabled) }
    }

    fun setJitterIntensity(value: Float) {
        viewModelScope.launch { prefs.setJitterIntensity(value) }
    }

    fun setDefaultTransportMode(mode: String) {
        viewModelScope.launch { prefs.setDefaultTransportMode(mode) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(enabled) }
    }

    fun setElevationEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setElevationEnabled(enabled) }
    }

    fun setPcReceiverModeEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setPcReceiverModeEnabled(enabled) }
    }

    fun setLocationMode(mode: String) {
        viewModelScope.launch { prefs.setLocationMode(mode) }
    }
}