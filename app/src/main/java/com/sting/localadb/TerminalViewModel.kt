package com.sting.localadb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class TerminalState(
    val outputLines: List<String> = listOf(
        "LocalADB v1.0",
        "Android 本机 ADB Shell",
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
        "首次使用：请先在"配对设置"中添加无线调试配对信息",
        ""
    ),
    val isConnected: Boolean = false,
    val showPairingDialog: Boolean = false
)

class TerminalViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(TerminalState())
    val state: TerminalState get() = _state.value
    
    val outputLines: List<String> get() = _state.value.outputLines
    val isConnected: Boolean get() = _state.value.isConnected
    val showPairingDialog: Boolean get() = _state.value.showPairingDialog
    
    private var adbManager: AdbManager? = null
    
    fun init(context: Context) {
        adbManager = AdbManager(context)
        viewModelScope.launch {
            adbManager?.initialize()
        }
    }
    
    fun executeCommand(cmd: String) {
        _state.value = _state.value.copy(
            outputLines = _state.value.outputLines + "$ $cmd"
        )
        viewModelScope.launch {
            val result = adbManager?.executeCommand(cmd) ?: "ADB not initialized"
            _state.value = _state.value.copy(
                outputLines = _state.value.outputLines + result.split("\n")
            )
        }
    }
    
    fun showPairingDialog() {
        _state.value = _state.value.copy(showPairingDialog = true)
    }
    
    fun hidePairingDialog() {
        _state.value = _state.value.copy(showPairingDialog = false)
    }
    
    fun pairAndConnect(address: String, pairingCode: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                outputLines = _state.value.outputLines + "正在配对 $address..."
            )
            val paired = adbManager?.pair(address, pairingCode) ?: false
            if (paired) {
                _state.value = _state.value.copy(
                    outputLines = _state.value.outputLines + "配对成功，正在连接..."
                )
                val connected = adbManager?.connect(address) ?: false
                if (connected) {
                    _state.value = _state.value.copy(
                        isConnected = true,
                        showPairingDialog = false,
                        outputLines = _state.value.outputLines + "✓ 连接成功！可以执行命令了"
                    )
                } else {
                    _state.value = _state.value.copy(
                        outputLines = _state.value.outputLines + "✗ 连接失败"
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    outputLines = _state.value.outputLines + "✗ 配对失败，请检查地址和配对码"
                )
            }
        }
    }
}