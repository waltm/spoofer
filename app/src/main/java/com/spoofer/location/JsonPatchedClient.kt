package com.spoofer.location

import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonPatchedClient
    @Inject
    constructor() {
        private val latFormatter = DecimalFormat("0.000000")
        private val lngFormatter = DecimalFormat("0.000000")

        fun generatePositionJson(
            latitude: Double,
            longitude: Double,
        ): String {
            return buildString {
                append('{')
                append("\"Action\": \"SendPosition\",")
                append("\"data\": {")
                append("\"Lat\": \"${latFormatter.format(latitude)}\",")
                append("\"Lng\": \"${lngFormatter.format(longitude)}\",")
                append("\"Type\": \"mhn\"")
                append("}")
                append("}")
            }
        }
    }
