package com.danielsalas.targes_nfc.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.util.Log

data class ScannedCardData(
    val uid: String,
    val cardType: String,
    val technologies: String,
    val compatibilityStatus: String,
    val compatibilitySummary: String,
    val compatibilityDetails: String,
    val contentDump: String? = null
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

        val dump = attemptDeepScan(tag)

        return ScannedCardData(
            uid = uidHex,
            cardType = cardType,
            technologies = readableTechs,
            compatibilityStatus = compatibility.status,
            compatibilitySummary = compatibility.summary,
            compatibilityDetails = compatibility.details,
            contentDump = dump
        )
    }

    private fun attemptDeepScan(tag: Tag): String {
        val sb = StringBuilder()
        
        // --- MIFARE CLASSIC ---
        val mifareClassic = MifareClassic.get(tag)
        if (mifareClassic != null) {
            try {
                mifareClassic.connect()
                sb.append("--- MIFARE CLASSIC DUMP ---\n")
                val sectorCount = mifareClassic.sectorCount
                for (i in 0 until sectorCount) {
                    // Intentar con llave por defecto (Transport Key)
                    val auth = mifareClassic.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT) ||
                               mifareClassic.authenticateSectorWithKeyA(i, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)) ||
                               mifareClassic.authenticateSectorWithKeyA(i, byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()))
                    
                    if (auth) {
                        val blockCount = mifareClassic.getBlockCountInSector(i)
                        val firstBlock = mifareClassic.sectorToBlock(i)
                        sb.append("Sector $i:\n")
                        for (j in 0 until blockCount) {
                            val blockData = mifareClassic.readBlock(firstBlock + j)
                            sb.append("  B${j}: ${blockData.joinToString("") { "%02X".format(it) }}\n")
                        }
                    } else {
                        sb.append("Sector $i: [BLOQUEADO / LLAVE DESCONOCIDA]\n")
                    }
                }
                mifareClassic.close()
            } catch (e: Exception) {
                sb.append("Error leyendo Mifare Classic: ${e.message}")
            }
            return sb.toString()
        }

        // --- MIFARE ULTRALIGHT ---
        val mifareUltralight = MifareUltralight.get(tag)
        if (mifareUltralight != null) {
            try {
                mifareUltralight.connect()
                sb.append("--- MIFARE ULTRALIGHT DUMP ---\n")
                // Leer las primeras 16 páginas (común en Ultralight/NTAG)
                for (i in 0 until 16 step 4) {
                    val pages = mifareUltralight.readPages(i)
                    for (j in 0 until 4) {
                        val start = j * 4
                        val pageData = pages.sliceArray(start until start + 4)
                        sb.append("Page ${i + j}: ${pageData.joinToString("") { "%02X".format(it) }}\n")
                    }
                }
                mifareUltralight.close()
            } catch (e: Exception) {
                sb.append("Error leyendo Mifare Ultralight: ${e.message}")
            }
            return sb.toString()
        }

        return "La tarjeta no permite lectura de memoria directa o requiere llaves propietarias."
    }
}
