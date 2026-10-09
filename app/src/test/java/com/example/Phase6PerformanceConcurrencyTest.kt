package com.example

import com.example.data.local.entity.TestResultEntity
import com.example.data.mapper.TestResultMapper.toLogEntries
import com.example.data.mapper.TestResultMapper.toStatistics
import com.example.domain.model.LogLevel
import com.example.service.ServiceState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * PHASE 6 — Performance & Concurrency Unit Test Suite.
 *
 * Verifies:
 * 1. Counter Concurrency: Multi-worker atomic increments without lost updates.
 * 2. Stop-on-Success & Collision Guard: Atomic compareAndSet prevents duplicate success claims.
 * 3. Bounded Queue & Backpressure: FIFO delivery, cancellation, and memory bounding.
 * 4. Worker Cancellation: Structured concurrency lifecycle shutdown without leaks or hangs.
 * 5. Pause / Resume Synchrony: Coroutine worker pause boundary contract.
 * 6. Session Persistence Throttling: Write amplification reduction while ensuring 100% final accuracy.
 * 7. Memory & Allocation Optimization: TestResultMapper subList-based log extraction & single-pass statistics.
 */
class Phase6PerformanceConcurrencyTest {

    // =========================================================================
    // 1. Counter Concurrency & Lost Update Prevention
    // =========================================================================

    @Test
    fun testCounterConcurrencyNoLostUpdates() = runBlocking {
        val totalCards = 500
        val workerCount = 5
        val progressCounter = AtomicInteger(0)
        val successCounter = AtomicInteger(0)
        val failureCounter = AtomicInteger(0)
        val stateMutex = Mutex()
        val serviceState = MutableStateFlow(ServiceState(total = totalCards))

        val channel = Channel<Int>(capacity = 64)
        val feederJob = launch {
            for (i in 1..totalCards) {
                channel.send(i)
            }
            channel.close()
        }

        val workers = (1..workerCount).map {
            launch(Dispatchers.Default) {
                for (item in channel) {
                    val p = progressCounter.incrementAndGet()
                    val isSuccess = (item % 5 == 0) // 20% success rate

                    if (isSuccess) {
                        val s = successCounter.incrementAndGet()
                        stateMutex.withLock {
                            serviceState.update { it.copy(progress = p, successCount = s) }
                        }
                    } else {
                        val f = failureCounter.incrementAndGet()
                        stateMutex.withLock {
                            serviceState.update { it.copy(progress = p, failureCount = f) }
                        }
                    }
                }
            }
        }

        workers.forEach { it.join() }
        feederJob.join()

        assertEquals(totalCards, progressCounter.get())
        assertEquals(100, successCounter.get())
        assertEquals(400, failureCounter.get())
        assertEquals(totalCards, successCounter.get() + failureCounter.get())
    }

    // =========================================================================
    // 2. Success Collision Guard (Atomic compareAndSet)
    // =========================================================================

    @Test
    fun testSuccessCollisionGuardPreventsDuplicateSuccess() = runBlocking {
        val isBlockedBySuccess = AtomicBoolean(false)
        val claimedSuccesses = AtomicInteger(0)
        val discardedCollisions = AtomicInteger(0)
        val concurrentWorkers = 10

        val jobs = (1..concurrentWorkers).map {
            launch(Dispatchers.Default) {
                // All workers simultaneously attempt to report success
                val wonRace = isBlockedBySuccess.compareAndSet(false, true)
                if (wonRace) {
                    claimedSuccesses.incrementAndGet()
                } else {
                    discardedCollisions.incrementAndGet()
                }
            }
        }

        jobs.forEach { it.join() }

        assertEquals("Exactly one worker must claim success", 1, claimedSuccesses.get())
        assertEquals("All other workers must discard collision", concurrentWorkers - 1, discardedCollisions.get())
        assertTrue("Guard must remain locked until explicitly cleared", isBlockedBySuccess.get())

        // Clear lock (e.g. after logout completes)
        isBlockedBySuccess.set(false)
        val secondRoundWinner = isBlockedBySuccess.compareAndSet(false, true)
        assertTrue("Subsequent card can claim success once lock is released", secondRoundWinner)
    }

    // =========================================================================
    // 3. Bounded Queue & Backpressure Contract
    // =========================================================================

    @Test
    fun testBoundedQueueFifoOrderAndBackpressure() = runBlocking {
        val capacity = 16
        val itemCount = 100
        val channel = Channel<String>(capacity = capacity)
        val sentList = (1..itemCount).map { "CARD-$it" }
        val receivedList = ConcurrentLinkedQueue<String>()

        val producer = launch(Dispatchers.Default) {
            for (card in sentList) {
                channel.send(card)
            }
            channel.close()
        }

        val consumer = launch(Dispatchers.Default) {
            for (card in channel) {
                receivedList.add(card)
                delay(1) // simulate slight processing delay to test backpressure
            }
        }

        producer.join()
        consumer.join()

        assertEquals(itemCount, receivedList.size)
        assertEquals("FIFO ordering must be preserved across bounded channel", sentList, receivedList.toList())
    }

    // =========================================================================
    // 4. Worker Cancellation & Structured Concurrency Lifecycle
    // =========================================================================

    @Test
    fun testWorkerCancellationPropagatesCleanly() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val channel = Channel<String>(capacity = 10)
        val processedCount = AtomicInteger(0)

        val feederJob = scope.launch {
            var counter = 0
            while (isActive) {
                channel.send("CARD-${++counter}")
            }
        }

        val workers = (1..3).map {
            scope.launch {
                for (card in channel) {
                    processedCount.incrementAndGet()
                    delay(50)
                }
            }
        }

        // Allow some cards to be consumed
        delay(120)

        // Cancel scope (simulating TestService cancellation / stopSelf)
        scope.cancel()

        assertTrue("Feeder job must be cancelled", feederJob.isCancelled)
        workers.forEach { assertTrue("Worker job must be cancelled", it.isCancelled) }
        assertTrue("At least one card processed before cancel", processedCount.get() > 0)
    }

    // =========================================================================
    // 5. Pause / Resume Synchrony
    // =========================================================================

    @Test
    fun testPauseResumeBoundary() = runBlocking {
        val isPaused = AtomicBoolean(true)
        val channel = Channel<Int>(capacity = 10)
        val processedAfterResume = AtomicInteger(0)

        val job = launch(Dispatchers.Default) {
            for (item in channel) {
                while (isPaused.get()) {
                    delay(20)
                }
                processedAfterResume.incrementAndGet()
            }
        }

        channel.send(1)
        channel.send(2)
        delay(100)
        // While paused, items should not be processed
        assertEquals("No items should be processed while paused", 0, processedAfterResume.get())

        // Resume
        isPaused.set(false)
        channel.close()
        job.join()

        assertEquals("Both items should be processed after resume", 2, processedAfterResume.get())
    }

    // =========================================================================
    // 6. Session Persistence Throttling (Database Write Amplification Reduction)
    // =========================================================================

    @Test
    fun testSessionPersistenceThrottlingLogic() {
        val totalCards = 100
        var dbWriteCount = 0

        fun checkShouldSyncSession(currentProgress: Int, isTrueSuccess: Boolean, total: Int): Boolean {
            return isTrueSuccess || (currentProgress % 10 == 0) || (currentProgress >= total)
        }

        // Scenario A: 100 failed cards
        for (progress in 1..totalCards) {
            if (checkShouldSyncSession(progress, isTrueSuccess = false, total = totalCards)) {
                dbWriteCount++
            }
        }
        // Exactly 10 syncs (at 10, 20, 30, ... 100) instead of 100 syncs!
        assertEquals("DB write count should be reduced by 90% for failed cards", 10, dbWriteCount)

        // Scenario B: Success on card 15
        assertTrue("Must immediately sync DB on true success", checkShouldSyncSession(15, isTrueSuccess = true, total = totalCards))
    }

    // =========================================================================
    // 7. Memory & Allocation Optimization (TestResultMapper)
    // =========================================================================

    @Test
    fun testTestResultMapperSubListMemoryOptimization() {
        val entityList = (1..250).map { i ->
            TestResultEntity(
                id = i.toLong(),
                sessionId = 1L,
                cardCode = "CARD-$i",
                routerId = 1L,
                routerName = "TestRouter",
                state = if (i % 2 == 0) "Success" else "Failure",
                message = "Test message $i",
                durationMs = 120L,
                testedAt = 1000L + i
            )
        }

        // Default limit = 100
        val logEntriesDefault = entityList.toLogEntries()
        assertEquals(100, logEntriesDefault.size)
        assertEquals("CARD-250: Test message 250", logEntriesDefault.last().message)
        assertEquals(LogLevel.SUCCESS, logEntriesDefault.last().level)

        // Small list below limit uses full list without subList slicing
        val smallList = entityList.take(50)
        val logEntriesSmall = smallList.toLogEntries(limit = 100)
        assertEquals(50, logEntriesSmall.size)
        assertEquals("CARD-50: Test message 50", logEntriesSmall.last().message)

        // Single-pass statistics calculation
        val stats = entityList.toStatistics()
        assertEquals(250, stats.total)
        assertEquals(125, stats.success)
        assertEquals(125, stats.failure)
        assertEquals(50.0f, stats.successRate, 0.001f)
    }
}
