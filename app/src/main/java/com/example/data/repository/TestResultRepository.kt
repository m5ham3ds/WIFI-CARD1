package com.example.data.repository

import com.example.data.local.database.TestResultDao
import com.example.data.local.entity.TestResultEntity
import com.example.data.mapper.TestResultMapper.toDomainList
import com.example.domain.model.TestResult
import com.example.domain.repository.ITestResultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TestResultRepository(private val testResultDao: TestResultDao) : ITestResultRepository {
    override val allResults: Flow<List<TestResultEntity>> = testResultDao.getAllResults()
    override val results: Flow<List<TestResult>> = allResults.map { it.toDomainList() }

    override fun getResultsBySession(sessionId: Long): Flow<List<TestResultEntity>> {
        return testResultDao.getResultsBySession(sessionId)
    }

    override fun getSessionResults(sessionId: Long): Flow<List<TestResult>> {
        return testResultDao.getResultsBySession(sessionId).map { it.toDomainList() }
    }

    override suspend fun insertResult(result: TestResultEntity) {
        testResultDao.insertResult(result)
    }

    override suspend fun insertResults(results: List<TestResultEntity>) {
        testResultDao.insertResults(results)
    }

    override suspend fun deleteBySession(sessionId: Long) {
        testResultDao.deleteBySession(sessionId)
    }

    override suspend fun deleteAll() {
        testResultDao.deleteAll()
    }
}
