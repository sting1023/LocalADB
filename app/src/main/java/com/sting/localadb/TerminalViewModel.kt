package com.sting.localadb

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class TerminalState(
    val outputLines: List<String> = listOf(
        "LocalADB v1.0",
        "Local ADB Shell for Android",
        "================================",
        "First time: Tap Pairing Settings to add wireless debugging",
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
                outputLines = _state.value.outputLines + "Pairing $address..."
            )
            val paired = adbManager?.pair(address, pairingCode) ?: false
            if (paired) {
                _state.value = _state.value.copy(
                    outputLines = _state.value.outputLines + "Pairing successful, connecting..."
                )
                val connected = adbManager?.connect(address) ?: false
                if (connected) {
                    _state.value = _state.value.copy(
                        isConnected = true,
                        showPairingDialog = false,
                        outputLines = _state.value.outputLines + "Connected! You can now execute commands"
                    )
                } else {
                    _state.value = _state.value.copy(
                        outputLines = _state.value.outputLines + "Connection failed"
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    outputLines = _state.value.outputLines + "Pairing failed. Check address and pairing code"
                )
            }
        }
    }
}
