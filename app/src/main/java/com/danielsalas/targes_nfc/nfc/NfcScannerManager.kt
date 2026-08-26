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

    fun writeUidToTag(tag: Tag, targetUidHex: String): Result<String> {
        val mifare = MifareClassic.get(tag) ?: return Result.failure(Exception("La pegatina debe ser Mifare Classic"))
        
        try {
            mifare.connect()
            // 1. Convertir UID hex a bytes (debe ser de 4 bytes / 8 caracteres hex)
            val uidBytes = targetUidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (uidBytes.size != 4) return Result.failure(Exception("Solo se soportan UIDs de 4 bytes para clonado estándar."))

            // 2. Calcular BCC (Checkbyte): XOR de los 4 bytes del UID
            var bcc: Byte = 0
            for (b in uidBytes) bcc = (bcc.toInt() xor b.toInt()).toByte()

            // 3. Preparar Bloque 0 (16 bytes)
            // Formato: [UID (4b)] [BCC (1b)] [SAK (1b)] [ATQA (2b)] [Manufacturer (8b)]
            // Usaremos valores genéricos compatibles para SAK/ATQA si no los tenemos
            val block0 = ByteArray(16)
            System.arraycopy(uidBytes, 0, block0, 0, 4)
            block0[4] = bcc
            block0[5] = 0x08.toByte() // SAK genérico para Mifare 1K
            block0[6] = 0x04.toByte() // ATQA byte 1
            block0[7] = 0x00.toByte() // ATQA byte 2
            // Rellenar el resto con datos de fabricante genéricos
            for (i in 8..15) block0[i] = 0x00.toByte()

            // 4. Intentar escribir en el Sector 0, Bloque 0 (Requiere pegatina "Magic" Gen2/CUID)
            // Autenticar primero (llave por defecto FFFFFFFFFFFF)
            val key = MifareClassic.KEY_DEFAULT
            if (mifare.authenticateSectorWithKeyA(0, key)) {
                mifare.writeBlock(0, block0)
                mifare.close()
                return Result.success("¡UID escrito con éxito en la pegatina!")
            } else {
                mifare.close()
                return Result.failure(Exception("Error de autenticación. ¿Es una pegatina CUID/Magic Gen2?"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
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
