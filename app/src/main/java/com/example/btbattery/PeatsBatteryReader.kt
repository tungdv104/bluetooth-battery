package com.example.btbattery

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

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

        private const val CHANNEL_PRIMARY = 11
        private const val CHANNEL_FALLBACK = 12

        private const val CONNECT_SETTLE_MS = 650L
        private const val RESPONSE_TIMEOUT_MS = 1500L
        private const val RETRIES = 3

        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Suppress("MissingPermission")
    fun read(callback: (PeatsBatteryState?, String?) -> Unit) {
        Thread {
            var lastError = "No battery response"
            var success = false

            val attempts: List<Pair<String, () -> BluetoothSocket>> = listOf(
                "SPP UUID" to {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                },
                "RFCOMM channel $CHANNEL_PRIMARY" to {
                    createChannelSocket(CHANNEL_PRIMARY)
                },
                "RFCOMM channel $CHANNEL_FALLBACK" to {
                    createChannelSocket(CHANNEL_FALLBACK)
                }
            )

            for ((label, createSocket) in attempts) {
                var socket: BluetoothSocket? = null

                try {
                    Log.i(TAG, "Trying $label")

                    socket = createSocket()
                    socket.connect()

                    Log.i(TAG, "Connected using $label")
                    Thread.sleep(CONNECT_SETTLE_MS)

                    val left = queryBattery(socket, 0x06)
                    val right = queryBattery(socket, 0x07)
                    val case = queryBattery(socket, 0x23)

                    val state = PeatsBatteryState(
                        left = left,
                        right = right,
                        case = case
                    )

                    Log.i(
                        TAG,
                        "Battery result via $label: " +
                            "left=$left, right=$right, case=$case"
                    )

                    if (state.hasAnyValue) {
                        success = true
                        callback(state, null)
                        return@Thread
                    }

                    lastError = "$label connected, but no valid battery reply"
                    Log.w(TAG, lastError)

                } catch (e: SecurityException) {
                    lastError = "Bluetooth permission denied: ${e.message}"
                    Log.e(TAG, lastError, e)
                    break

                } catch (e: Exception) {
                    lastError =
                        "$label: ${e.javaClass.simpleName}: ${e.message}"
                    Log.w(TAG, lastError, e)

                } finally {
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                }
            }

            if (!success) {
                Log.e(TAG, "FINAL RESULT: $lastError")
                callback(null, lastError)
            }
        }.apply {
            name = "PeatsBatteryReader"
            isDaemon = true
            start()
        }
    }

    @Suppress("DiscouragedPrivateApi", "MissingPermission")
    private fun createChannelSocket(channel: Int): BluetoothSocket {
        val method = device.javaClass.getMethod(
            "createRfcommSocket",
            Int::class.javaPrimitiveType
        )
        return method.invoke(device, channel) as BluetoothSocket
    }

    private fun queryBattery(
        socket: BluetoothSocket,
        command: Int
    ): Int? {
        val request = byteArrayOf(
            0xFF.toByte(),
            0x04,
            0x00,
            0x00,
            0x00,
            0x0A,
            0x03,
            command.toByte()
        )

        repeat(RETRIES) { attempt ->
            try {
                Log.d(
                    TAG,
                    "TX command=03 ${command.toString(16)} " +
                        "attempt=${attempt + 1}"
                )

                socket.outputStream.write(request)
                socket.outputStream.flush()

                val value = readMatchingReply(socket, command)

                if (value != null) {
                    Log.i(
                        TAG,
                        "RX battery command=03 " +
                            "${command.toString(16)} value=$value"
                    )
                    return value
                }

                Log.w(
                    TAG,
                    "No valid reply for 03 ${command.toString(16)}"
                )

            } catch (e: IOException) {
                Log.w(
                    TAG,
                    "I/O failed for command 03 " +
                        command.toString(16),
                    e
                )
                return null
            }
        }

        return null
    }

    
private fun readMatchingReply(
    socket: BluetoothSocket,
    command: Int
): Int? {
    val input = socket.inputStream
    val bytes = ArrayList<Int>()
    val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS

    while (System.currentTimeMillis() < deadline) {
        try {
            if (!socket.isConnected) {
                Log.w(TAG, "Socket disconnected while waiting for reply")
                logRaw(bytes)
                return null
            }

            if (input.available() <= 0) {
                Thread.sleep(20)
                continue
            }

            val next = input.read()
            if (next < 0) {
                Log.w(TAG, "EOF waiting for command ${command.toString(16)}")
                logRaw(bytes)
                return null
            }

            bytes.add(next and 0xFF)

            if (bytes.size >= 10) {
                val start = bytes.size - 10

                val version = bytes[start + 1]
                val matches =
                    bytes[start] == 0xFF &&
                    (version == 0x03 || version == 0x04) &&
                    bytes[start + 2] == 0x00 &&
                    bytes[start + 3] == 0x02 &&
                    bytes[start + 4] == 0x00 &&
                    bytes[start + 5] == 0x0A &&
                    bytes[start + 6] == 0x83 &&
                    bytes[start + 7] == command &&
                    bytes[start + 8] == 0x00

                if (matches) {
                    val rawValue = bytes[start + 9]
val value = rawValue and 0x7F

if (value in 0..100) {
    Log.i(
        TAG,
        "Battery command=$command raw=0x${rawValue.toString(16)} decoded=$value"
    )
    return value
}

                    Log.w(
                        TAG,
                        "Invalid battery value: 0x${value.toString(16).uppercase()} " +
                            "($value), command=${command.toString(16)}"
                    )
                    return null
                }
            }

            if (bytes.size > 128) {
                while (bytes.size > 32) {
                    bytes.removeAt(0)
                }
            }

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logRaw(bytes)
            return null
        } catch (e: IOException) {
            Log.w(TAG, "Read failed", e)
            logRaw(bytes)
            return null
        }
    }

    Log.w(TAG, "Response timeout for command ${command.toString(16)}")
    logRaw(bytes)
    return null
}


    private fun logRaw(bytes: List<Int>) {
        val raw = if (bytes.isEmpty()) {
            "<empty>"
        } else {
            bytes.joinToString(" ") {
                "%02X".format(it)
            }
        }

        Log.d(TAG, "RAW RX [$raw]")
    }
}
