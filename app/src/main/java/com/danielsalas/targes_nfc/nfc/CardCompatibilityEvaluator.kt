package com.danielsalas.targes_nfc.nfc

data class CompatibilityResult(
    val status: String, // "COMPATIBLE", "RESTRICTED", "UNKNOWN"
    val summary: String,
    val details: String
)

object CardCompatibilityEvaluator {
    fun evaluate(techList: Array<String>, uidHex: String): CompatibilityResult {
        val techString = techList.joinToString(",") { it.substringAfterLast('.') }
        
        val isMifareClassic = techList.any { it.contains("MifareClassic", ignoreCase = true) }
        val isMifareUltralight = techList.any { it.contains("MifareUltralight", ignoreCase = true) }
        val isIsoDep = techList.any { it.contains("IsoDep", ignoreCase = true) }
        val isNfcA = techList.any { it.contains("NfcA", ignoreCase = true) }

        return when {
            isIsoDep -> CompatibilityResult(
                status = "COMPATIBLE",
                summary = "Alta compatibilidad (ISO-DEP / HCE)",
                details = "La tarjeta soporta ISO-DEP. Esto significa que puede emularse mediante Host Card Emulation (HCE) si el lector del contenedor acepta transacciones APDU estándar o AID virtuales."
            )
            isMifareClassic || isMifareUltralight -> CompatibilityResult(
                status = "RESTRICTED",
                summary = "Restringido por Android (UID Fijo)",
                details = "UID detectado: $uidHex. Esta tarjeta es una tarjeta basada en UID físico fijo o memoria Mifare. Android NO permite alterar el UID de hardware del teléfono por software sin root. El contenedor probablemente rechazará el emulado a menos que reconozca el AID/UID virtual generado por HCE."
            )
            isNfcA -> CompatibilityResult(
                status = "RESTRICTED",
                summary = "Parcialmente compatible (NfcA / UID Fijo)",
                details = "UID detectado: $uidHex. Utiliza protocolo NfcA estándar. Al igual que Mifare, la mayoría de contenedores leen el UID físico de 4 o 7 bytes, el cual no se puede clonar exactamente en el chip NFC del smartphone."
            )
            else -> CompatibilityResult(
                status = "UNKNOWN",
                summary = "Tecnología no clasificada ($techString)",
                details = "UID detectado: $uidHex. Se ha leído correctamente la tarjeta, pero su tecnología requiere validación directa en el contenedor de basura."
            )
        }
    }
}
