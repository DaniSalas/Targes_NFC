package com.danielsalas.targes_nfc.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppDatabase private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val cardsFlow = MutableStateFlow<List<CardEntity>>(emptyList())

    init {
        reloadCards()
    }

    companion object {
        private const val DATABASE_NAME = "targes_nfc_database.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_CARDS = "nfc_cards"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CARDS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                uid TEXT NOT NULL,
                cardType TEXT NOT NULL,
                technologies TEXT NOT NULL,
                compatibilityStatus TEXT NOT NULL,
                compatibilitySummary TEXT NOT NULL,
                compatibilityDetails TEXT NOT NULL,
                contentDump TEXT,
                createdAt INTEGER NOT NULL
            )
            """
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CARDS")
        onCreate(db)
    }

    fun getAllCards(): Flow<List<CardEntity>> = cardsFlow.asStateFlow()

    private fun reloadCards() {
        val list = mutableListOf<CardEntity>()
        val db = readableDatabase
        val cursor = db.query(TABLE_CARDS, null, null, null, null, null, "createdAt DESC")
        cursor.use {
            val idIdx = it.getColumnIndex("id")
            val nameIdx = it.getColumnIndex("name")
            val uidIdx = it.getColumnIndex("uid")
            val cardTypeIdx = it.getColumnIndex("cardType")
            val techsIdx = it.getColumnIndex("technologies")
            val statusIdx = it.getColumnIndex("compatibilityStatus")
            val summaryIdx = it.getColumnIndex("compatibilitySummary")
            val detailsIdx = it.getColumnIndex("compatibilityDetails")
            val dumpIdx = it.getColumnIndex("contentDump")
            val createdIdx = it.getColumnIndex("createdAt")

            while (it.moveToNext()) {
                list.add(
                    CardEntity(
                        id = if (idIdx >= 0) it.getLong(idIdx) else 0L,
                        name = if (nameIdx >= 0) it.getString(nameIdx) else "",
                        uid = if (uidIdx >= 0) it.getString(uidIdx) else "",
                        cardType = if (cardTypeIdx >= 0) it.getString(cardTypeIdx) else "",
                        technologies = if (techsIdx >= 0) it.getString(techsIdx) else "",
                        compatibilityStatus = if (statusIdx >= 0) it.getString(statusIdx) else "",
                        compatibilitySummary = if (summaryIdx >= 0) it.getString(summaryIdx) else "",
                        compatibilityDetails = if (detailsIdx >= 0) it.getString(detailsIdx) else "",
                        contentDump = if (dumpIdx >= 0) it.getString(dumpIdx) else null,
                        createdAt = if (createdIdx >= 0) it.getLong(createdIdx) else System.currentTimeMillis()
                    )
                )
            }
        }
        cardsFlow.value = list
    }

    suspend fun insertCard(card: CardEntity): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("name", card.name)
            put("uid", card.uid)
            put("cardType", card.cardType)
            put("technologies", card.technologies)
            put("compatibilityStatus", card.compatibilityStatus)
            put("compatibilitySummary", card.compatibilitySummary)
            put("compatibilityDetails", card.compatibilityDetails)
            put("contentDump", card.contentDump)
            put("createdAt", card.createdAt)
        }
        val id = db.insert(TABLE_CARDS, null, values)
        reloadCards()
        return id
    }

    suspend fun deleteCard(card: CardEntity) {
        val db = writableDatabase
        db.delete(TABLE_CARDS, "id = ?", arrayOf(card.id.toString()))
        reloadCards()
    }

    suspend fun getCardById(cardId: Long): CardEntity? {
        val db = readableDatabase
        val cursor = db.query(TABLE_CARDS, null, "id = ?", arrayOf(cardId.toString()), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                val idIdx = it.getColumnIndex("id")
                val nameIdx = it.getColumnIndex("name")
                val uidIdx = it.getColumnIndex("uid")
                val cardTypeIdx = it.getColumnIndex("cardType")
                val techsIdx = it.getColumnIndex("technologies")
                val statusIdx = it.getColumnIndex("compatibilityStatus")
                val summaryIdx = it.getColumnIndex("compatibilitySummary")
                val detailsIdx = it.getColumnIndex("compatibilityDetails")
                val dumpIdx = it.getColumnIndex("contentDump")
                val createdIdx = it.getColumnIndex("createdAt")
                return CardEntity(
                    id = if (idIdx >= 0) it.getLong(idIdx) else 0L,
                    name = if (nameIdx >= 0) it.getString(nameIdx) else "",
                    uid = if (uidIdx >= 0) it.getString(uidIdx) else "",
                    cardType = if (cardTypeIdx >= 0) it.getString(cardTypeIdx) else "",
                    technologies = if (techsIdx >= 0) it.getString(techsIdx) else "",
                    compatibilityStatus = if (statusIdx >= 0) it.getString(statusIdx) else "",
                    compatibilitySummary = if (summaryIdx >= 0) it.getString(summaryIdx) else "",
                    compatibilityDetails = if (detailsIdx >= 0) it.getString(detailsIdx) else "",
                    contentDump = if (dumpIdx >= 0) it.getString(dumpIdx) else null,
                    createdAt = if (createdIdx >= 0) it.getLong(createdIdx) else System.currentTimeMillis()
                )
            }
        }
        return null
    }

    fun cardDao(): AppDatabase = this
}
