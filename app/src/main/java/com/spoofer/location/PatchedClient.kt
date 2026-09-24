package com.spoofer.location

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects to the mhn app running on the same device (127.0.0.1:9898) and
 * forwards position updates in mhn's wire format: a 4-byte frame header
 * (0x01 followed by a 3-byte big-endian length) wrapping a UTF-8 JSON body.
 *
 * mhn acknowledges each message with the same framing. We read those acks on
 * a background coroutine and log them — the send path itself never blocks.
 *
 * Fire-and-forget on the send side, like PcReceiverServer: a failed send is
 * logged, not thrown. Reconnect happens lazily on the next send.
 */
@Singleton
class PatchedClient
@Inject
constructor(
    private val jsonPatchedClient: JsonPatchedClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var connected = false
    private var readerJob: Job? = null

    @Volatile var host: String = DEFAULT_HOST
    @Volatile var port: Int = DEFAULT_PORT

    fun sendPosition(
        latitude: Double,
        longitude: Double,
    ) {
        if (!connected && !tryConnect()) return

        val json = jsonPatchedClient.generatePositionJson(latitude, longitude)
        val payload = json.toByteArray(Charsets.UTF_8)
        val frame = buildFrame(payload)

        runCatching {
            output?.write(frame)
            output?.flush()
        }.onFailure { e ->
            Log.w(TAG, "Send failed: ${e.message}")
            disconnect()
        }
    }

    fun disconnect() {
        connected = false
        readerJob?.cancel()
        readerJob = null
        runCatching { output?.close() }
        runCatching { socket?.close() }
        output = null
        socket = null
    }

    private fun tryConnect(): Boolean {
        return runCatching {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket = s
            output = s.getOutputStream()
            connected = true
            startReader(s)
            Log.d(TAG, "Connected to $host:$port")
            true
        }.getOrElse { e ->
            Log.w(TAG, "Connect to $host:$port failed: ${e.message}")
            false
        }
    }

    private fun startReader(s: Socket) {
        readerJob?.cancel()
        readerJob =
            scope.launch {
                val input = BufferedReader(InputStreamReader(s.getInputStream()))
                while (connected) {
                    val line = input.readLine() ?: break
                    Log.d(TAG, "Ack: $line")
                }
            }
    }

    private fun buildFrame(payload: ByteArray): ByteArray {
        val len = payload.size
        val header =
            byteArrayOf(
                MSG_TYPE_JSON,
                (len ushr 16).toByte(),
                (len ushr 8).toByte(),
                len.toByte(),
            )
        return header + payload
    }

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 9898
        private const val MSG_TYPE_JSON: Byte = 0x01
        private const val CONNECT_TIMEOUT_MS = 500
        private const val TAG = "PatchedClient"
    }
}