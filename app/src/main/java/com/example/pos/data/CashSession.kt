package com.example.pos.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import java.io.Serializable

@Entity(tableName = "cash_sessions")
data class CashSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cashierName: String,
    val openingTime: Long = System.currentTimeMillis(),
    val closingTime: Long? = null,
    val openingCash: Double,
    val closingCash: Double? = null,
    val expectedCash: Double = openingCash,
    val difference: Double? = null,
    val differenceReason: String? = null,
    val status: String = "OPEN" // "OPEN", "CLOSED"
) : Serializable

@Entity(tableName = "cash_transactions")
data class CashTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val type: String, // "CASH_IN", "CASH_OUT"
    val amount: Double,
    val reason: String,
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SessionSummary(
    val openingCash: Double,
    val totalSales: Double,
    val totalCashIn: Double,
    val totalCashOut: Double,
    val expectedCash: Double
)

@Dao
interface CashSessionDao {
    @Insert
    suspend fun insertSession(session: CashSession): Long

    @Update
    suspend fun updateSession(session: CashSession)

    @Query("SELECT * FROM cash_sessions WHERE status = 'OPEN' LIMIT 1")
    suspend fun getOpenSession(): CashSession?

    @Query("SELECT * FROM cash_sessions ORDER BY openingTime DESC")
    suspend fun getAllSessions(): List<CashSession>

    @Query("SELECT * FROM cash_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): CashSession?

    @Insert
    suspend fun insertTransaction(transaction: CashTransaction)

    @Query("SELECT * FROM cash_transactions WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getTransactionsForSession(sessionId: Int): List<CashTransaction>

    @Query("SELECT SUM(totalAmount) FROM sales WHERE sessionId = :sessionId AND isVoided = 0")
    suspend fun getSessionTotalSales(sessionId: Int): Double?

    @Query("SELECT SUM(amount) FROM cash_transactions WHERE sessionId = :sessionId AND type = 'CASH_IN'")
    suspend fun getSessionTotalCashIn(sessionId: Int): Double?

    @Query("SELECT SUM(amount) FROM cash_transactions WHERE sessionId = :sessionId AND type = 'CASH_OUT'")
    suspend fun getSessionTotalCashOut(sessionId: Int): Double?

    @Transaction
    suspend fun getSessionSummary(sessionId: Int): SessionSummary {
        val session = getSessionById(sessionId) ?: throw Exception("Session not found")
        val sales = getSessionTotalSales(sessionId) ?: 0.0
        val cashIn = getSessionTotalCashIn(sessionId) ?: 0.0
        val cashOut = getSessionTotalCashOut(sessionId) ?: 0.0
        val expected = session.openingCash + sales + cashIn - cashOut
        return SessionSummary(session.openingCash, sales, cashIn, cashOut, expected)
    }

    @Transaction
    suspend fun closeSession(sessionId: Int, closingCash: Double, expectedCash: Double, difference: Double, reason: String?) {
        val session = getSessionById(sessionId) ?: return
        updateSession(session.copy(
            closingTime = System.currentTimeMillis(),
            closingCash = closingCash,
            expectedCash = expectedCash,
            difference = difference,
            differenceReason = reason,
            status = "CLOSED"
        ))
    }
}
