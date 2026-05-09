package com.sting.localadb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalADBTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TerminalScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val viewModel = remember { TerminalViewModel() }
    var command by remember { mutableStateOf("") }
    var showPairing by remember { mutableStateOf(false) }
    var pairAddress by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    
    LaunchedEffect(Unit) {
        viewModel.init(context)
    }
    
    LaunchedEffect(viewModel.outputLines.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
    ) {
        // Connection Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (viewModel.isConnected) Color(0xFF2E7D32) else Color(0xFFB71C1C)
            )
        ) {
            Text(
                text = if (viewModel.isConnected) "✓ 已连接" else "✗ 未连接",
                modifier = Modifier.padding(12.dp),
                color = Color.White,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Terminal Output
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            viewModel.outputLines.forEach { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFF00FF00),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Command Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                label = { Text("ADB 命令", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00FF00),
                    unfocusedBorderColor = Color.Gray
                )
            )
            Button(
                onClick = {
                    if (command.isNotBlank()) {
                        viewModel.executeCommand(command)
                        command = ""
                    }
                },
                enabled = viewModel.isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF00))
            ) {
                Text("执行", color = Color.Black)
            }
        }
        
        // Settings Button
        TextButton(onClick = { showPairing = true }) {
            Text("配对设置", color = Color(0xFF00BFFF))
        }
    }
    
    // Pairing Dialog
    if (showPairing) {
        AlertDialog(
            onDismissRequest = { showPairing = false },
            title = { Text("配对无线调试", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pairAddress,
                        onValueChange = { pairAddress = it },
                        label = { Text("地址 (IP:Port)", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pairCode,
                        onValueChange = { pairCode = it },
                        label = { Text("配对码", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.pairAndConnect(pairAddress, pairCode)
                    showPairing = false
                }) {
                    Text("配对")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairing = false }) {
                    Text("取消", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF2D2D2D)
        )
    }
}
