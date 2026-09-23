package com.spoofer.location.mode

sealed class LocationMode {

    data class DebugMode(
        val provider: String = "gps",
        val isMockEnabled: Boolean = true,
    ) : LocationMode()

    data class PatchedMode(
        val outputFormat: OutputFormat = OutputFormat.JSON,
    ) : LocationMode()

    sealed class OutputFormat {
        data object JSON : OutputFormat()
        data object Raw : OutputFormat()
    }

    companion object {
        const val DEBUG = "debug"
        const val PATCHED = "patched"

        fun fromString(name: String): LocationMode? =
            when (name.lowercase()) {
                "debug" -> DebugMode()
                "patched" -> PatchedMode()
                else -> null
            }
    }
}


