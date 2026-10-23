package com.danielsalas.targes_nfc.data

data class CardEntity(
    val id: Long = 0L,
    val name: String,
    val uid: String,
    val cardType: String,
    val technologies: String,
    val compatibilityStatus: String,
    val compatibilitySummary: String,
    val compatibilityDetails: String,
    val contentDump: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
