package com.sting.localadb

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LocalAdbApplication

    private val _outputLines = MutableStateFlow<List<String>>(
        listOf(
            "LocalADB v2.0",
            "本机无线 ADB 调试工具",
            "─────────────────────────────",
            "提示：开启无线调试后，等待配对..."
        )
    )
    val outputLines: StateFlow<List<String>> = _outputLines

    private val _connectionState = MutableStateFlow(PairingService.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PairingService.ConnectionState> = _connectionState

    private val _currentCommand = MutableStateFlow("")
    val currentCommand: StateFlow<String> = _currentCommand

    private val _localIp = MutableStateFlow("")
    val localIp: StateFlow<String> = _localIp

    private val _adbPort = MutableStateFlow(-1)
    val adbPort: StateFlow<Int> = _adbPort

    init {
        app.bindPairingService()

        viewModelScope.launch {
            app.pairingService?.uiState?.collect { state ->
                _connectionState.value = state.connectionState
                _localIp.value = state.localIp
                _adbPort.value = state.adbPort

                updateOutputForState(state)
            }
        }
    }

    private fun updateOutputForState(state: PairingService.UiState) {
        val lines = mutableListOf<String>()

        lines.add("LocalADB v2.0")
        lines.add("本机无线 ADB 调试工具")
        lines.add("─────────────────────────────")

        when (state.connectionState) {
            PairingService.ConnectionState.DISCONNECTED -> {
                if (state.wirelessDebugEnabled && state.adbPort > 0) {
                    lines.add("⚠️ 无线调试已开启，请配对")
                    lines.add("  IP: ${state.localIp}")
                    lines.add("  端口: ${state.adbPort}")
                    lines.add("")
                    lines.add("在通知栏输入配对码开始配对")
                } else {
                    lines.add("等待开启无线调试...")
                    lines.add("(开发者选项 → 无线调试)")
                }
            }
            PairingService.ConnectionState.WAITING_PAIRING -> {
                lines.add("✅ 无线调试已开启")
                lines.add("  IP: ${state.localIp}")
                lines.add("  端口: ${state.adbPort}")
                lines.add("")
                lines.add("→ 在通知栏输入配对码")
            }
            PairingService.ConnectionState.PAIRING -> {
                lines.add("⏳ 配对中...")
                lines.add("  IP: ${state.localIp}:${state.adbPort}")
            }
            PairingService.ConnectionState.CONNECTED -> {
                lines.add("✅ 已连接!")
                lines.add("  ${state.localIp}:${state.adbPort}")
                lines.add("")
                lines.add("可以执行 ADB 命令")
                lines.add("")
            }
            PairingService.ConnectionState.ERROR -> {
                lines.add("❌ 配对失败")
                lines.add("请重试或检查设置")
            }
        }

        _outputLines.value = lines
    }

    fun executeCommand(command: String) {
        if (command.isBlank()) return
        if (_connectionState.value != PairingService.ConnectionState.CONNECTED) {
            appendOutput("❌ 未连接，请先配对")
            return
        }

        viewModelScope.launch {
            appendOutput("\$ $command")

            try {
                val result = AdbManager.execute(command)
                if (result.isNotBlank()) {
                    appendOutput(result)
                }
            } catch (e: Exception) {
                appendOutput("错误: ${e.message}")
            }
        }
    }

    fun updateCommand(cmd: String) {
        _currentCommand.value = cmd
    }

    fun clearOutput() {
        _outputLines.value = _outputLines.value.filterIndexed { index, _ ->
            index < 4 // Keep header
        }
    }

    private fun appendOutput(text: String) {
        _outputLines.value = _outputLines.value + text.split("\n")
    }

    fun pairWithCode(code: String) {
        viewModelScope.launch {
            val state = _connectionState.value
            if (state == PairingService.ConnectionState.WAITING_PAIRING) {
                appendOutput("\n→ 配对码: $code")
                val success = AdbPairing.pair(_localIp.value, _adbPort.value, code)
                if (success) {
                    appendOutput("✅ 配对成功!")
                    connect()
                } else {
                    appendOutput("❌ 配对失败")
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            val ip = _localIp.value
            val port = _adbPort.value
            if (ip.isNotBlank() && port > 0) {
                appendOutput("\n→ 连接到 $ip:$port...")
                val success = AdbPairing.connect(ip, port)
                if (success) {
                    appendOutput("✅ 连接成功!")
                } else {
                    appendOutput("❌ 连接失败")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        app.unbindPairingService()
    }
}
