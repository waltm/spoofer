package com.spoofer.location.mode

sealed class LocationMode {

    data class DebugMode(
        val provider: String = "gps",
        val isMockEnabled: Boolean = true,
    ) : LocationMode()

    data class PatchedMode(
        val outputFormat: OutputFormat = OutputFormat.JSON,
    ) : LocationMode()

    /** Inject through both the standard mock-location provider and the patched client. */
    data object Both : LocationMode()

    sealed class OutputFormat {
        data object JSON : OutputFormat()
        data object Raw : OutputFormat()
    }

    companion object {
        const val DEBUG = "debug"
        const val PATCHED = "patched"
        const val BOTH = "both"

        fun fromString(name: String): LocationMode? =
            when (name.lowercase()) {
                DEBUG -> DebugMode()
                PATCHED -> PatchedMode()
                BOTH -> Both
                else -> null
            }
    }
}