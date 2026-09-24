package com.spoofer.network

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

/**
 * Listens for newline-delimited location updates from a PC companion, reusing the wire
 * format observed from a commercial spoofer's own PC<->device protocol:
 * {"Action":"SendPosition","data":{"Lat":"<decimal string>","Lng":"<decimal string>","Type":"<string>"}}
 *
 * Bound to all interfaces on [port]. Connections arriving via `adb forward` (loopback)
 * are trusted with no handshake, exactly as before. Connections from the LAN must send
 * {"Action":"Auth","data":{"Token":"<pin>"}} as their first line before any SendPosition
 * is accepted; the PIN is regenerated every time the server starts and is surfaced to
 * the UI via [onPinChanged].
 */
class PcReceiverServer(
    private val port: Int = DEFAULT_PORT,
    private val onLocation: (lat: Double, lng: Double) -> Unit,
    private val onConnectionStateChanged: (connected: Boolean) -> Unit,
    private val onPinChanged: (String) -> Unit = {},
) {
    @Volatile private var running = false
    @Volatile private var authPin: String = ""
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        authPin = Random.nextInt(100000, 1000000).toString()
        onPinChanged(authPin)
        thread = Thread(::runServer, "PcReceiverServer").apply { isDaemon = true }
        thread?.start()
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        thread?.interrupt()
        thread = null
        serverSocket = null
        onConnectionStateChanged(false)
    }

    private fun runServer() {
        try {
            val socket = ServerSocket(port)
            serverSocket = socket
            while (running) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                handleClient(client)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server loop ended: ${e.message}")
        }
    }

    private fun handleClient(client: Socket) {
        onConnectionStateChanged(true)
        var authenticated = client.inetAddress.isLoopbackAddress
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            while (running) {
                val line = reader.readLine() ?: break
                if (!authenticated) {
                    if (!tryAuth(line)) {
                        Log.w(TAG, "Auth failed from ${client.inetAddress}")
                        break
                    }
                    authenticated = true
                    continue
                }
                parseAndDispatch(line)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client loop ended: ${e.message}")
        } finally {
            runCatching { client.close() }
            onConnectionStateChanged(false)
        }
    }

    private fun tryAuth(line: String): Boolean = runCatching {
        val json = JSONObject(line.trim())
        json.optString("Action") == "Auth" &&
                json.optJSONObject("data")?.optString("Token") == authPin
    }.getOrDefault(false)

    private fun parseAndDispatch(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        runCatching {
            val json = JSONObject(trimmed)
            if (json.optString("Action") != "SendPosition") return
            val data = json.getJSONObject("data")
            val lat = data.getString("Lat").toDouble()
            val lng = data.getString("Lng").toDouble()
            onLocation(lat, lng)
        }.onFailure { Log.w(TAG, "Bad message: $trimmed", it) }
    }

    companion object {
        const val DEFAULT_PORT = 8765
        private const val TAG = "PcReceiverServer"
    }
}