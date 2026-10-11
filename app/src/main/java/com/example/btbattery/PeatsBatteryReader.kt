package com.example.btbattery

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Experimental SoundPEATS battery reader.
 *
 * Protocol is confirmed on SoundPEATS H3, NOT yet confirmed on PearlClip Pro.
 * It uses RFCOMM channels 11/12 and the documented 03 06 / 03 07 / 03 23 reads.
 */
data class PeatsBatteryState(
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null
) {
    val hasAnyValue: Boolean
        get() = left != null || right != null || case != null
}

class PeatsBatteryReader(private val device: BluetoothDevice) {

    companion object {
        private const val TAG = "PeatsBatteryReader"
        private const val SPP_CHANNEL_PRIMARY = 11
        private const val SPP_CHANNEL_FALLBACK = 12
        private const val CONNECT_SETTLE_MS = 650L
        private const val RESPONSE_TIMEOUT_MS = 1_100L
        private const val RETRIES = 3
    }

    fun read(callback: (PeatsBatteryState?, String?) -> Unit) {
        Thread {
            var lastError = "No reply from SoundPEATS battery protocol"

            for (channel in listOf(SPP_CHANNEL_PRIMARY, SPP_CHANNEL_FALLBACK)) {
                var socket: BluetoothSocket? = null
                try {
                    socket = createChannelSocket(channel)
                    socket.connect()
                    Thread.sleep(CONNECT_SETTLE_MS)

                    val left = queryBattery(socket, 0x06)
                    val right = queryBattery(socket, 0x07)
                    val case = queryBattery(socket, 0x23)

                    val state = PeatsBatteryState(left, right, case)
                    if (state.hasAnyValue) {
                        callback(state, null)
                        return@Thread
                    }
                    lastError = "Connected on RFCOMM channel $channel, but no battery replies"
                } catch (t: Throwable) {
                    lastError = "RFCOMM channel $channel: ${t.javaClass.simpleName}: ${t.message ?: "no details"}"
                    Log.w(TAG, lastError)
                } finally {
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                }
            }

            callback(null, lastError)
        }.apply {
            name = "PeatsBatteryReader"
            isDaemon = true
            start()
        }
    }

    @Suppress("DiscouragedPrivateApi", "MissingPermission")
    private fun createChannelSocket(channel: Int): BluetoothSocket {
        // Android's public SPP UUID discovery is not reliable for every SoundPEATS model.
        // OpenPeats observed RFCOMM channels 11 and 12 on its tested H3.
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, channel) as BluetoothSocket
    }

    private fun queryBattery(socket: BluetoothSocket, command: Int): Int? {
        val request = byteArrayOf(
            0xFF.toByte(), 0x04, 0x00, 0x00,
            0x00, 0x0A, 0x03, command.toByte()
        )

        repeat(RETRIES) { attempt ->
            try {
                socket.outputStream.write(request)
                socket.outputStream.flush()

                val reply = readMatchingReply(socket, command)
                if (reply != null) return reply

                Log.d(TAG, "No reply for command 03 ${command.toString(16)} (attempt ${attempt + 1})")
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "Timeout for command 03 ${command.toString(16)}")
            } catch (e: IOException) {
                Log.w(TAG, "I/O error for command 03 ${command.toString(16)}", e)
                return null
            }
        }
        return null
    }

    private fun readMatchingReply(socket: BluetoothSocket, command: Int): Int? {
        val input = socket.inputStream
        val bytes = ArrayList<Int>()
        val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            try {
                // BluetoothSocket input streams may block indefinitely when no
                // reply arrives. Poll available() so the timeout remains effective.
                if (input.available() <= 0) {
                    Thread.sleep(20)
                    continue
                }

                val next = input.read()
                if (next < 0) return null
                bytes.add(next and 0xFF)

                // Expected response:
                // FF 03 00 02 00 0A 83 <command> 00 <battery 0..100>
                if (bytes.size >= 10) {
                    val start = bytes.size - 10
                    if (
                        bytes[start] == 0xFF &&
                        bytes[start + 1] == 0x03 &&
                        bytes[start + 3] == 0x02 &&
                        bytes[start + 4] == 0x00 &&
                        bytes[start + 5] == 0x0A &&
                        bytes[start + 6] == 0x83 &&
                        bytes[start + 7] == command &&
                        bytes[start + 8] == 0x00
                    ) {
                        val value = bytes[start + 9]
                        if (value in 0..100) return value
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            } catch (_: IOException) {
                return null
            }
        }
        return null
    }
}
