package com.sting.localadb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.net.InetAddress

/**
 * ADB command execution via TCP socket to local ADB daemon
 */
object AdbManager {

    private const val DEFAULT_IP = "127.0.0.1"
    private const val DEFAULT_PORT = 5555

    private var currentIp = DEFAULT_IP
    private var currentPort = DEFAULT_PORT

    /**
     * Execute an ADB shell command via TCP socket
     */
    suspend fun execute(command: String, ip: String? = null, port: Int? = null): String = withContext(Dispatchers.IO) {
        val targetIp = ip ?: currentIp
        val targetPort = port ?: currentPort

        val result = StringBuilder()

        try {
            // For shell commands, we need to:
            // 1. Connect to ADB daemon
            // 2. Send shell:command payload
            // 3. Read response

            val socket = Socket()
            socket.connect(InetAddress.getByName(targetIp), targetPort, 5000)
            socket.soTimeout = 15000

            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // Build shell command payload
            val shellPayload = "shell:${command}\u0000"
            val payloadBytes = shellPayload.toByteArray()

            // Calculate payload length
            val payloadLen = payloadBytes.size

            // Build ADB packet
            // Command: 0x00000006 (EXEC)
            val packet = buildAdbPacket(0x00000006u, payloadLen, payloadBytes)

            output.write(packet)
            output.flush()

            // Read response in chunks
            val buffer = ByteArray(4096)
            val stringBuilder = StringBuilder()

            // Read until socket closes or timeout
            try {
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead <= 0) break
                    stringBuilder.append(String(buffer, 0, bytesRead))
                }
            } catch (e: java.net.SocketTimeoutException) {
                // Expected on some commands
            }

            socket.close()

            stringBuilder.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }

    /**
     * Connect to a specific IP and port
     */
    suspend fun connect(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            currentIp = ip
            currentPort = port

            val socket = Socket()
            socket.connect(InetAddress.getByName(ip), port, 5000)
            socket.soTimeout = 5000

            // Send CNXN
            val cnxnPayload = "host::\u0000".toByteArray()
            val packet = buildAdbPacket(0x00000001u, cnxnPayload.size, cnxnPayload)

            socket.outputStream.write(packet)
            socket.outputStream.flush()

            // Read response
            val response = ByteArray(1024)
            val len = socket.inputStream.read(response)
            socket.close()

            len > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Build ADB packet with header
     */
    private fun buildAdbPacket(command: UInt, payloadLen: Int, payload: ByteArray): ByteArray {
        val header = ByteArray(24)

        // Magic: "ASAY"
        header[0] = 0x41
        header[1] = 0x53
        header[2] = 0x59
        header[3] = 0x4e

        // Command
        header[4] = (command.toInt() and 0xFF).toByte()
        header[5] = (command.toInt() shr 8 and 0xFF).toByte()
        header[6] = (command.toInt() shr 16 and 0xFF).toByte()
        header[7] = (command.toInt() shr 24 and 0xFF).toByte()

        // Arg0, Arg1 (zero for most commands)
        // [8-11], [12-15] remain 0

        // Payload length
        header[16] = (payloadLen and 0xFF).toByte()
        header[17] = (payloadLen shr 8 and 0xFF).toByte()
        header[18] = (payloadLen shr 16 and 0xFF).toByte()
        header[19] = (payloadLen shr 24 and 0xFF).toByte()

        // Checksum (sum of payload bytes)
        var checksum = 0
        for (b in payload) {
            checksum += b.toInt() and 0xFF
        }
        header[20] = (checksum and 0xFF).toByte()
        header[21] = (checksum shr 8 and 0xFF).toByte()
        header[22] = (checksum shr 16 and 0xFF).toByte()
        header[23] = (checksum shr 24 and 0xFF).toByte()

        // Combine header + payload
        val packet = ByteArray(24 + payloadLen)
        System.arraycopy(header, 0, packet, 0, 24)
        System.arraycopy(payload, 0, packet, 24, payloadLen)

        return packet
    }
}
