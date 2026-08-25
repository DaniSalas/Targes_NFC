package com.danielsalas.targes_nfc.nfc

import androidx.compose.runtime.mutableStateListOf

object HceLogManager {
    val logs = mutableStateListOf<String>()

    fun addLog(message: String) {
        logs.add(0, message) // Insertar al principio
        if (logs.size > 50) {
            logs.removeAt(logs.size - 1)
        }
    }

    fun clear() {
        logs.clear()
    }
}
