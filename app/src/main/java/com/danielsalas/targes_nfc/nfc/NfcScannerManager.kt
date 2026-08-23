package com.danielsalas.targes_nfc.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA

data class ScannedCardData(
    val uid: String,
    val cardType: String,
    val technologies: String,
    val compatibilityStatus: String,
    val compatibilityDetails: String
)

object NfcScannerManager {
    fun parseTag(tag: Tag): ScannedCardData {
        val idBytes = tag.id
        val uidHex = idBytes?.joinToString("") { "%02X".format(it) } ?: "DESCONOCIDO"

        val techList = tag.techList
        val compatibility = CardCompatibilityEvaluator.evaluate(techList, uidHex)

        val readableTechs = techList.map { it.substringAfterLast('.') }.joinToString(", ")

        val cardType = when {
            techList.any { it.contains("MifareClassic") } -> "Mifare Classic"
            techList.any { it.contains("MifareUltralight") } -> "Mifare Ultralight"
            techList.any { it.contains("IsoDep") } -> "SmartCard ISO-DEP"
            techList.any { it.contains("NfcA") } -> "NFC-A Standard"
            else -> "Tarjeta NFC Genérica"
        }

        return ScannedCardData(
            uid = uidHex,
            cardType = cardType,
            technologies = readableTechs,
            compatibilityStatus = compatibility.status,
            compatibilityDetails = compatibility.details
        )
    }
}
