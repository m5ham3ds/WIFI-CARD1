package com.example.domain.repository

import com.example.data.local.entity.TestResultEntity
import com.example.domain.model.TestResult
import kotlinx.coroutines.flow.Flow

interface ITestResultRepository {
    val allResults: Flow<List<TestResultEntity>>
    val results: Flow<List<TestResult>>
    fun getResultsBySession(sessionId: Long): Flow<List<TestResultEntity>>
    fun getSessionResults(sessionId: Long): Flow<List<TestResult>>
    suspend fun insertResult(result: TestResultEntity)
    suspend fun insertResults(results: List<TestResultEntity>)
    suspend fun deleteBySession(sessionId: Long)
    suspend fun deleteAll()
}
