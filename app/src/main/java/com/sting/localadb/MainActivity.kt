package com.sting.localadb

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sting.localadb.ui.theme.LocalADBTheme

class MainActivity : ComponentActivity() {

    private lateinit var notificationManager: NotificationManager
    private var pairingReceiver: BroadcastReceiver? = null

    companion object {
        const val CHANNEL_ID = "localadb_pairing"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAIR_CODE = "com.sting.localadb.PAIR_CODE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        registerPairingReceiver()
        showPairingNotification()

        setContent {
            LocalADBTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: TerminalViewModel = viewModel()
                    val uiState by viewModel.connectionState.collectAsState()
                    val outputLines by viewModel.outputLines.collectAsState()
                    val currentCommand by viewModel.currentCommand.collectAsState()
                    val localIp by viewModel.localIp.collectAsState()
                    val adbPort by viewModel.adbPort.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E))
                            .padding(16.dp)
                    ) {
                        // Title
                        Text(
                            text = "LocalADB",
                            color = Color(0xFF00FF00),
                            fontSize = 24.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Status bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "IP: $localIp:$adbPort",
                                color = Color(0xFF888888),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            val (statusText, statusColor) = when (uiState) {
                                PairingService.ConnectionState.CONNECTED -> "✅ 已连接" to Color(0xFF00FF00)
                                PairingService.ConnectionState.PAIRING -> "⏳ 配对中" to Color(0xFFFFFF00)
                                PairingService.ConnectionState.WAITING_PAIRING -> "⚠️ 等待配对" to Color(0xFFFFFF00)
                                PairingService.ConnectionState.ERROR -> "❌ 错误" to Color(0xFFFF0000)
                                else -> "○ 未连接" to Color(0xFF888888)
                            }
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Output area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                outputLines.forEach { line ->
                                    val lineColor = when {
                                        line.startsWith("✅") -> Color(0xFF00FF00)
                                        line.startsWith("❌") -> Color(0xFFFF4444)
                                        line.startsWith("⚠️") -> Color(0xFFFFFF00)
                                        line.startsWith("⏳") -> Color(0xFFFFFF00)
                                        line.startsWith("→") -> Color(0xFF00CCFF)
                                        line.startsWith("$") -> Color(0xFFFFFFFF)
                                        line.startsWith("─") -> Color(0xFF444444)
                                        else -> Color(0xFFCCCCCC)
                                    }
                                    Text(
                                        text = line,
                                        color = lineColor,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Command input
                        val focusManager = LocalFocusManager.current

                        OutlinedTextField(
                            value = currentCommand,
                            onValueChange = { viewModel.updateCommand(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "输入命令...",
                                    color = Color(0xFF666666)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFFFFFFF),
                                unfocusedTextColor = Color(0xFFFFFFFF),
                                focusedBorderColor = Color(0xFF00FF00),
                                unfocusedBorderColor = Color(0xFF444444),
                                cursorColor = Color(0xFF00FF00),
                                focusedContainerColor = Color(0xFF1A1A1A),
                                unfocusedContainerColor = Color(0xFF1A1A1A)
                            ),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    viewModel.executeCommand(currentCommand)
                                    viewModel.updateCommand("")
                                    focusManager.clearFocus()
                                }
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.clearOutput() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF333333)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("清空", color = Color.White)
                            }

                            val buttonColor = if (uiState == PairingService.ConnectionState.WAITING_PAIRING)
                                Color(0xFF0066FF) else Color(0xFF00AA00)
                            val buttonText = when (uiState) {
                                PairingService.ConnectionState.WAITING_PAIRING -> "配对"
                                PairingService.ConnectionState.CONNECTED -> "已连接"
                                else -> "连接"
                            }

                            Button(
                                onClick = {
                                    if (uiState == PairingService.ConnectionState.WAITING_PAIRING) {
                                        showPairingNotification()
                                    } else {
                                        viewModel.connect()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = buttonText, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "LocalADB 配对",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "无线 ADB 配对服务"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerPairingReceiver() {
        pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_PAIR_CODE) {
                    val code = intent.getStringExtra("code") ?: ""
                    if (code.length == 6) {
                        val serviceIntent = Intent(this@MainActivity, PairingService::class.java).apply {
                            action = PairingService.ACTION_PAIR
                            putExtra(PairingService.EXTRA_CODE, code)
                        }
                        startService(serviceIntent)
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_PAIR_CODE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pairingReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pairingReceiver, filter)
        }
    }

    private fun showPairingNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LocalADB")
            .setContentText("点击启动配对")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        pairingReceiver?.let { unregisterReceiver(it) }
    }
}

class PairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == MainActivity.ACTION_PAIR_CODE) {
            val code = intent.getStringExtra("code") ?: ""
            if (code.length == 6) {
                val serviceIntent = Intent(context, PairingService::class.java).apply {
                    action = PairingService.ACTION_PAIR
                    putExtra(PairingService.EXTRA_CODE, code)
                }
                context?.startService(serviceIntent)
            }
        }
    }
}