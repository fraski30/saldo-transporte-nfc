package com.fraski.saldotransportenfc.db

import androidx.room.*

@Entity(tableName = "history_records")
data class HistoryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val balance: Int,
    val date: String, // YYYY-MM-DD
    val timestamp: Long
)

@Entity(tableName = "card_aliases")
data class CardAlias(
    @PrimaryKey val uid: String,
    val nickname: String
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<HistoryRecord>

    @Insert
    suspend fun insert(record: HistoryRecord)

    @Query("SELECT COUNT(*) FROM history_records WHERE uid = :uid AND balance = :balance AND date = :date")
    suspend fun countSpecific(uid: String, balance: Int, date: String): Int

    @Query("DELETE FROM history_records")
    suspend fun deleteAll()

    @Delete
    suspend fun deleteRecords(records: List<HistoryRecord>)

    // Alias Queries
    @Query("SELECT nickname FROM card_aliases WHERE uid = :uid")
    suspend fun getNickname(uid: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlias(alias: CardAlias)
    
    @Query("SELECT * FROM history_records")
    suspend fun getAllRaw(): List<HistoryRecord>
}

@Database(entities = [HistoryRecord::class, CardAlias::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
