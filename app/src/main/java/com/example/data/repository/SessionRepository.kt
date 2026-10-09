package com.example.data.repository

import com.example.data.local.database.SessionDao
import com.example.data.local.entity.TestSessionEntity
import com.example.data.mapper.SessionMapper.toDomainList
import com.example.domain.model.TestSession
import com.example.domain.repository.ISessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(private val sessionDao: SessionDao) : ISessionRepository {
    override val allSessions: Flow<List<TestSessionEntity>> = sessionDao.getAllSessions()
    override val sessions: Flow<List<TestSession>> = allSessions.map { it.toDomainList() }

    override suspend fun insert(session: TestSessionEntity): Long {
        return sessionDao.insert(session)
    }

    override suspend fun update(session: TestSessionEntity) {
        sessionDao.update(session)
    }

    override suspend fun getById(id: Long): TestSessionEntity? {
        return sessionDao.getById(id)
    }

    override suspend fun getRunningSession(): TestSessionEntity? {
        return sessionDao.getRunningSession()
    }
    
    override suspend fun cleanUpOrphanedSessions() {
        sessionDao.cleanUpOrphanedSessions()
    }

    override suspend fun updateCounts(sessionId: Long, successCount: Int, failureCount: Int) {
        sessionDao.updateCounts(sessionId, successCount, failureCount)
    }

    override suspend fun markFinished(sessionId: Long, successCount: Int, failureCount: Int) {
        sessionDao.markFinished(sessionId, successCount, failureCount, System.currentTimeMillis())
    }

    override suspend fun createSession(routerId: Long, routerName: String): Long {
        val session = TestSessionEntity(
            routerId = routerId,
            routerName = routerName,
            isRunning = true,
            startedAt = System.currentTimeMillis()
        )
        return sessionDao.insert(session)
    }

    override suspend fun finishSession(id: Long) {
        val session = sessionDao.getById(id) ?: return
        sessionDao.update(session.copy(isRunning = false, finishedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteAll() {
        sessionDao.deleteAll()
    }
}
