package com.sting.localadb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket
import java.net.InetAddress

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
            socket.connect(InetAddress.getByName(ip), port, CONN_TIMEOUT)
            
            // Send ADB CNXN packet
            val connectPayload = "host::\u0000".toByteArray()
            val packet = createAdbPacket(0x00000001, connectPayload.size.toUInt(), connectPayload)
            socket.outputStream.write(packet)
            socket.outputStream.flush()

            // Read response
            val response = ByteArray(1024)
            val len = socket.inputStream.read(response)
            socket.close()

            len > 0 && (response[4].toInt() and 0xFF) == 0x00000001 // CNXN OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Pair with ADB daemon using pairing code
     * Flow:
     * 1. Connect to device
     * 2. Send 'adb pair' command with code
     * 3. Verify success
     * 4. Reconnect with auth
     */
    suspend fun pair(ip: String, port: Int, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetAddress.getByName(ip), port, CONN_TIMEOUT)
            socket.soTimeout = 10000

            // Step 1: Initial CNXN
            val connectPayload = "host::\u0000".toByteArray()
            val cnxnPacket = createAdbPacket(0x00000001, connectPayload.size.toUInt(), connectPayload)
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
            val pairCmd = "pair:${code}\u0000".toByteArray()
            val pairPacket = createAdbPacket(0x00000002, pairCmd.size.toUInt(), pairCmd)
            socket.outputStream.write(pairPacket)
            socket.outputStream.flush()

            // Read pair response
            val pairResponse = ByteArray(1024)
            val pairLen = socket.inputStream.read(pairResponse)
            socket.close()

            if (pairLen <= 0) return@withContext false

            // Check for OK response (0x00000001 for CNXN, 0x00000002 for CLSE)
            val cmd = bytesToUint(pairResponse.copyOfRange(4, 8))
            cmd == 0x00000002.toUInt() // Success response
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
        
        // Magic (4 bytes)
        header[0] = 0x41 // 'A'
        header[1] = 0x53 // 'S'
        header[2] = 0x59 // 'Y'
        header[3] = 0x4e // 'N'

        // Command (4 bytes)
        val cmdBytes = uintToBytes(command)
        System.arraycopy(cmdBytes, 0, header, 4, 4)

        // Arg0 (4 bytes)
        val arg0Bytes = uintToBytes(arg0)
        System.arraycopy(arg0Bytes, 0, header, 8, 4)

        // Arg1 (4 bytes)
        val arg1Bytes = uintToBytes(arg1)
        System.arraycopy(arg1Bytes, 0, header, 12, 4)

        // Payload length (4 bytes)
        val payloadLen = uintToBytes(payload.size.toUInt())
        System.arraycopy(payloadLen, 0, header, 16, 4)

        // Checksum (4 bytes) - simple sum
        var checksum: UInt = 0u
        for (b in payload) {
            checksum += b.toUInt()
        }
        val checksumBytes = uintToBytes(checksum)
        System.arraycopy(checksumBytes, 0, header, 20, 4)

        // Combine header + payload
        val packet = ByteArray(24 + payload.size)
        System.arraycopy(header, 0, packet, 0, 24)
        System.arraycopy(payload, 0, packet, 24, payload.size)

        return packet
    }

    private fun createAdbPacket(command: UInt, payloadSize: UInt, payload: ByteArray): ByteArray {
        return createAdbPacket(command, 0u, 0u, payload)
    }

    private fun uintToBytes(value: UInt): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            (value.toInt() shr 8 and 0xFF).toByte(),
            (value.toInt() shr 16 and 0xFF).toByte(),
            (value.toInt() shr 24 and 0xFF).toByte()
        )
    }

    private fun bytesToUint(bytes: ByteArray): UInt {
        if (bytes.size < 4) return 0u
        return ((bytes[0].toInt() and 0xFF) or
                (bytes[1].toInt() and 0xFF shl 8) or
                (bytes[2].toInt() and 0xFF shl 16) or
                (bytes[3].toInt() and 0xFF shl 24)).toUInt()
    }
}
