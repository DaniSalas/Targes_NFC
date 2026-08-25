package com.danielsalas.targes_nfc.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class MyHostApduService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return byteArrayOf(0x6F.toByte(), 0x00.toByte())

        val hexCommand = commandApdu.joinToString("") { "%02X".format(it) }
        Log.d("HCE_SERVICE", "Comando recibido: $hexCommand")
        HceLogManager.addLog("📥 $hexCommand")

        // 1. Si el comando es un SELECT AID (empieza por 00A40400)
        if (hexCommand.startsWith("00A40400")) {
            HceLogManager.addLog("✅ Lector intentando seleccionar APP")
            return byteArrayOf(0x90.toByte(), 0x00.toByte()) // Responder OK
        }

        // 2. Si el comando es una petición de lectura (ej. 00B0 o comandos Mifare)
        // Intentaremos responder con un "éxito" genérico para ver si el lector sigue hablando
        return byteArrayOf(0x90.toByte(), 0x00.toByte())
    }

    override fun onDeactivated(reason: Int) {
        Log.d("HCE_SERVICE", "Servicio HCE desactivado. Razón: $reason")
    }
}

