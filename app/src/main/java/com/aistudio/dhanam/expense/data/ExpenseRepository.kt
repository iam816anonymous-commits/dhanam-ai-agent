package com.aistudio.dhanam.expense.data

import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun observeAll(): Flow<List<ExpenseEntity>>
    suspend fun findById(id: Long): ExpenseEntity?
    suspend fun add(expense: ExpenseEntity): Long
    suspend fun update(expense: ExpenseEntity)
    suspend fun deleteById(id: Long)
}

class RoomExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {
    override fun observeAll(): Flow<List<ExpenseEntity>> = dao.observeAll()
    override suspend fun findById(id: Long): ExpenseEntity? = dao.findById(id)
    override suspend fun add(expense: ExpenseEntity): Long = dao.insert(expense)

    override suspend fun update(expense: ExpenseEntity) {
        check(dao.update(expense) == 1) {
            "This expense no longer exists. Refresh the list and try again."
        }
    }

    override suspend fun deleteById(id: Long) {
        val expense = dao.findById(id) ?: return
        check(dao.delete(expense) == 1) {
            "This expense changed before it could be deleted. Refresh and try again."
        }
    }
}
