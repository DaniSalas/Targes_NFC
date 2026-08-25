package com.danielsalas.targes_nfc.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nfc_cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true)
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
