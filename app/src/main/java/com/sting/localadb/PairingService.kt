package com.sting.localadb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground service that monitors wireless debugging status.
 * Shows notification with status and pairing code input.
 */
class PairingService : Service() {

    companion object {
        const val CHANNEL_ID = "localadb_pairing"
        const val NOTIFICATION_ID = 1001

        // Intent actions for notification buttons
        const val ACTION_PAIR = "com.sting.localadb.ACTION_PAIR"
        const val ACTION_CONNECT = "com.sting.localadb.ACTION_CONNECT"
        const val EXTRA_CODE = "pairing_code"

        // Settings keys
        const val KEY_WIRELESS_DEBUG = "adb_port"
        const val KEY_ADB_ENABLED = "adb_enabled"
    }

    inner class LocalBinder : Binder() {
        fun getService(): PairingService = this@PairingService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var notificationManager: NotificationManager
    private lateinit var wifiManager: WifiManager
    private lateinit var powerManager: PowerManager

    private var wakeLock: PowerManager.WakeLock? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    data class UiState(
        val wirelessDebugEnabled: Boolean = false,
        val adbPort: Int = -1,
        val localIp: String = "",
        val pairingActive: Boolean = false,
        val connectionState: ConnectionState = ConnectionState.DISCONNECTED
    )

    enum class ConnectionState {
        DISCONNECTED,
        WAITING_PAIRING,
        PAIRING,
        CONNECTED,
        ERROR
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        createNotificationChannel()
        acquireWakeLock()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        // Start monitoring loop
        scope.launch {
            while (true) {
                checkWirelessDebugStatus()
                delay(1000) // Poll every second
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAIR -> {
                val code = intent.getStringExtra(EXTRA_CODE)
                if (!code.isNullOrBlank()) {
                    scope.launch { executePairing(code) }
                }
            }
            ACTION_CONNECT -> {
                scope.launch { connectAdb() }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        wakeLock?.release()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LocalADB",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "无线 ADB 配对服务"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): android.app.Notification {
        val state = _uiState.value

        val contentText = when {
            !state.wirelessDebugEnabled -> "无线调试已关闭"
            state.connectionState == ConnectionState.CONNECTED -> "已连接 ${state.localIp}:${state.adbPort}"
            state.connectionState == ConnectionState.PAIRING -> "配对中..."
            state.adbPort > 0 -> "无线调试已开启，等待配对 (${state.localIp}:${state.adbPort})"
            else -> "等待开启无线调试..."
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocalADB")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_terminal)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Add pairing action if waiting
        if (state.wirelessDebugEnabled && state.adbPort > 0 &&
            state.connectionState != ConnectionState.CONNECTED &&
            state.connectionState != ConnectionState.PAIRING) {

            val pairIntent = Intent(this, PairingService::class.java).apply {
                action = ACTION_PAIR
                putExtra(EXTRA_CODE, "------") // Placeholder - user will enter
            }
            val pairPendingIntent = PendingIntent.getService(
                this, 1, pairIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder.addAction(
                android.R.drawable.ic_menu_preferences,
                "输入配对码",
                pairPendingIntent
            )
        }

        return builder.build()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun acquireWakeLock() {
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LocalADB::PairingWakeLock"
        ).apply {
            acquire(30 * 60 * 1000L) // 30 min max
        }
    }

    private fun checkWirelessDebugStatus() {
        try {
            val port = try {
                Settings.Global.getInt(contentResolver, KEY_WIRELESS_DEBUG)
            } catch (e: Settings.SettingNotFoundException) {
                -1
            }

            val ip = getLocalIpAddress()

            val wasEnabled = _uiState.value.wirelessDebugEnabled
            val wasPort = _uiState.value.adbPort

            _uiState.value = _uiState.value.copy(
                wirelessDebugEnabled = port > 0,
                adbPort = port,
                localIp = if (ip.isNullOrBlank()) "127.0.0.1" else ip
            )

            // Auto transition states
            if (port > 0 && wasPort <= 0 && !wasEnabled) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.WAITING_PAIRING
                )
            }

            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo != null && wifiInfo.ipAddress != 0) {
                val ip = wifiInfo.ipAddress
                return String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    private suspend fun executePairing(code: String) {
        val state = _uiState.value
        if (state.adbPort <= 0) return

        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.PAIRING)
        updateNotification()

        try {
            val result = AdbPairing.pair(state.localIp, state.adbPort, code)
            if (result) {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTED)
            } else {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.ERROR)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.ERROR)
        }

        updateNotification()
    }

    private suspend fun connectAdb() {
        val state = _uiState.value
        if (state.adbPort <= 0) return

        try {
            AdbPairing.connect(state.localIp, state.adbPort)
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTED)
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.ERROR)
        }

        updateNotification()
    }
}
