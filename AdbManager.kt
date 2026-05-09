package com.sting.localadb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class AdbManager(private val context: Context) {
    
    private var adbBinary: File? = null
    var isConnected = false
        private set
    
    private val adbDir: File by lazy {
        File(context.filesDir, "adb").also { it.mkdirs() }
    }
    
    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Extract bundled ADB binary
        extractAssets("adb", File(adbDir, "adb").also { it.setExecutable(true) })
    }
    
    private fun extractAssets(assetName: String, destFile: File) {
        try {
            context.assets.open(assetName).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.setExecutable(true)
        } catch (e: Exception) {
            // Asset not found, ignore
        }
    }
    
    fun getAdbPath(): String = File(adbDir, "adb").absolutePath
    
    suspend fun pair(address: String, pairingCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = runAdbCommand("pair", address, pairingCode)
            isConnected = result.contains("Successfully") || result.contains("already")
            isConnected
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = runAdbCommand("connect", address)
            isConnected = result.contains("connected") || result.contains("already")
            isConnected
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            runAdbCommand("shell", command)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    private fun runAdbCommand(vararg args: String): String {
        val pb = ProcessBuilder(getAdbPath(), *args)
        pb.environment()["LD_LIBRARY_PATH"] = adbDir.absolutePath
        val process = pb.start()
        return process.inputStream.bufferedReader().readText() + 
               process.errorStream.bufferedReader().readText()
    }
}