package com.sting.localadb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Pure Kotlin ADB pairing implementation.
 * No native libraries needed - uses adb protocol directly via TCP socket.
 */
object AdbPairing {

    private const val ADB_PORT = 5555
    private const val CONN_TIMEOUT = 5000

    /**
     * Connect to ADB daemon (no pairing needed if already paired)
     */
    suspend fun connect(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), CONN_TIMEOUT)

            // Send ADB CNXN packet
            val connectPayload = "host::\u0000".toByteArray()
            val packet = createAdbPacket(0x00000001u, connectPayload.size.toUInt(), connectPayload)
            socket.outputStream.write(packet)
            socket.outputStream.flush()

            // Read response
            val response = ByteArray(1024)
            val len = socket.inputStream.read(response)
            socket.close()

            len > 0 && (response[4].toInt() and 0xFF) == 0x01 // CNXN OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Pair with ADB daemon using pairing code
     */
    suspend fun pair(ip: String, port: Int, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), CONN_TIMEOUT)
            socket.soTimeout = 10000

            // Step 1: Initial CNXN
            val connectPayload = "host::\u0000".toByteArray()
            val cnxnPacket = createAdbPacket(0x00000001u, connectPayload.size.toUInt(), connectPayload)
            socket.outputStream.write(cnxnPacket)
            socket.outputStream.flush()

            // Read CNXN response
            val banner = ByteArray(1024)
            val bannerLen = socket.inputStream.read(banner)
            if (bannerLen <= 0) {
                socket.close()
                return@withContext false
            }

            // Step 2: Send pair request
            val pairCmd = "pair:$code\u0000".toByteArray()
            val pairPacket = createAdbPacket(0x00000002u, pairCmd.size.toUInt(), pairCmd)
            socket.outputStream.write(pairPacket)
            socket.outputStream.flush()

            // Read pair response
            val pairResponse = ByteArray(1024)
            val pairLen = socket.inputStream.read(pairResponse)
            socket.close()

            if (pairLen <= 0) return@withContext false

            // Check for OK response
            val cmd = bytesToUint(pairResponse.copyOfRange(4, 8))
            cmd == 0x00000002u // Success response
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Create ADB packet with header + payload
     */
    private fun createAdbPacket(command: UInt, arg0: UInt, arg1: UInt, payload: ByteArray): ByteArray {
        val header = ByteArray(24)

        // Magic (4 bytes) - "ASYNC"
        header[0] = 0x41 // 'A'
        header[1] = 0x53 // 'S'
        header[2] = 0x59 // 'Y'
        header[3] = 0x4E // 'N'

        // Command (4 bytes)
        header[4] = (command.toInt() and 0xFF).toByte()
        header[5] = (command.toInt() shr 8 and 0xFF).toByte()
        header[6] = (command.toInt() shr 16 and 0xFF).toByte()
        header[7] = (command.toInt() shr 24 and 0xFF).toByte()

        // Arg0 (4 bytes)
        header[8] = (arg0.toInt() and 0xFF).toByte()
        header[9] = (arg0.toInt() shr 8 and 0xFF).toByte()
        header[10] = (arg0.toInt() shr 16 and 0xFF).toByte()
        header[11] = (arg0.toInt() shr 24 and 0xFF).toByte()

        // Arg1 (4 bytes)
        header[12] = (arg1.toInt() and 0xFF).toByte()
        header[13] = (arg1.toInt() shr 8 and 0xFF).toByte()
        header[14] = (arg1.toInt() shr 16 and 0xFF).toByte()
        header[15] = (arg1.toInt() shr 24 and 0xFF).toByte()

        // Payload length (4 bytes)
        header[16] = (payload.size and 0xFF).toByte()
        header[17] = (payload.size shr 8 and 0xFF).toByte()
        header[18] = (payload.size shr 16 and 0xFF).toByte()
        header[19] = (payload.size shr 24 and 0xFF).toByte()

        // Checksum (4 bytes) - simple sum
        var checksum = 0
        for (b in payload) {
            checksum += b.toInt() and 0xFF
        }
        header[20] = (checksum and 0xFF).toByte()
        header[21] = (checksum shr 8 and 0xFF).toByte()
        header[22] = (checksum shr 16 and 0xFF).toByte()
        header[23] = (checksum shr 24 and 0xFF).toByte()

        // Combine header + payload
        val packet = ByteArray(24 + payload.size)
        System.arraycopy(header, 0, packet, 0, 24)
        System.arraycopy(payload, 0, packet, 24, payload.size)

        return packet
    }

    private fun createAdbPacket(command: UInt, payloadSize: UInt, payload: ByteArray): ByteArray {
        return createAdbPacket(command, 0u, 0u, payload)
    }

    private fun bytesToUint(bytes: ByteArray): UInt {
        if (bytes.size < 4) return 0u
        return ((bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)).toUInt()
    }
}