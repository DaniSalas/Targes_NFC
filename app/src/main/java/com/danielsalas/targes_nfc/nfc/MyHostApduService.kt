package com.danielsalas.targes_nfc.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.util.Arrays

class MyHostApduService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        // Respuesta simulada de selección APDU para emulación HCE
        // Respuesta OK (9000)
        return byteArrayOf(0x90.toByte(), 0x00.toByte())
    }

    override fun onDeactivated(reason: Int) {
        // Desactivación del servicio
    }
}
