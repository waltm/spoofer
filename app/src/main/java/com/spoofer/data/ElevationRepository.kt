package com.spoofer.data

import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch-fetches ground elevation for a set of points from Open-Elevation (open source,
 * self-hostable). Used only when a route has no elevation data of its own (an imported
 * GPX file's `<ele>` tags are preferred and never trigger a network call).
 */
@Singleton
class ElevationRepository
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        private val baseUrl = "https://api.open-elevation.com/api/v1/lookup"
        private val gson = Gson()

        suspend fun getElevations(points: List<LatLng>): List<Double>? {
            if (points.isEmpty()) return emptyList()

            return withContext(Dispatchers.IO) {
                try {
                    val locations =
                        points.joinToString(",", prefix = "[", postfix = "]") { point ->
                            "{\"latitude\":${point.latitude},\"longitude\":${point.longitude}}"
                        }
                    val body =
                        "{\"locations\":$locations}"
                            .toRequestBody("application/json".toMediaType())

                    val request = Request.Builder().url(baseUrl).post(body).build()
                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string() ?: throw IOException("Empty elevation response")

                    if (!response.isSuccessful) {
                        throw IOException("Elevation lookup error ${response.code}")
                    }

                    val json = gson.fromJson(responseBody, JsonObject::class.java)
                    val results = json.getAsJsonArray("results") ?: throw IOException("Malformed elevation response")

                    results.map { it.asJsonObject.get("elevation").asDouble }
                } catch (_: Exception) {
                    // Elevation is a cosmetic enhancement — never fail the spoof session over it.
                    null
                }
            }
        }
    }
