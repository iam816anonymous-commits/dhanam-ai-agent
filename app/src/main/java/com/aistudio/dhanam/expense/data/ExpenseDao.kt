package com.aistudio.dhanam.expense.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY occurred_on_epoch_day DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ExpenseEntity?

    @Insert suspend fun insert(expense: ExpenseEntity): Long
    @Update suspend fun update(expense: ExpenseEntity): Int
    @Delete suspend fun delete(expense: ExpenseEntity): Int

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int
}
