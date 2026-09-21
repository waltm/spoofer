package com.spoofer.network

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Listens for newline-delimited location updates from a PC companion, reusing the wire
 * format observed from a commercial spoofer's own PC<->device protocol:
 * {"Action":"SendPosition","data":{"Lat":"<decimal string>","Lng":"<decimal string>","Type":"<string>"}}
 *
 * Bound to loopback only — reachable via `adb forward tcp:<port> tcp:<port>` over USB,
 * never exposed on the phone's own network interface. A real WiFi listener would need
 * a shared-token handshake before accepting updates; this prototype has none.
 */
class PcReceiverServer(
    private val port: Int = DEFAULT_PORT,
    private val onLocation: (lat: Double, lng: Double) -> Unit,
    private val onConnectionStateChanged: (connected: Boolean) -> Unit,
) {
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
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
            val socket = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
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
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            while (running) {
                val line = reader.readLine() ?: break
                parseAndDispatch(line)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client loop ended: ${e.message}")
        } finally {
            runCatching { client.close() }
            onConnectionStateChanged(false)
        }
    }

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
