package com.nastytech.eden2.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(historyItem: HistoryItem)

    @Query("SELECT * FROM history_table ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Query("DELETE FROM history_table WHERE id = :itemId")
    suspend fun deleteById(itemId: Int)

    @Query("DELETE FROM history_table")
    suspend fun deleteAll()

    @Query("SELECT * FROM history_table WHERE url = :url LIMIT 1")
    suspend fun getHistoryItemByUrl(url: String): HistoryItem?
}