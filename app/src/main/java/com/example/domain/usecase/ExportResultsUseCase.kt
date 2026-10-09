package com.example.domain.usecase

import com.example.data.mapper.TestResultMapper.toDomainList
import com.example.domain.repository.ITestResultRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ExportResultsUseCase(private val testResultRepository: ITestResultRepository) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend operator fun invoke(
        outputDir: File,
        fileName: String = "results_export.json",
        sessionId: Long? = null
    ): Result {
        return try {
            val entities = if (sessionId != null) {
                testResultRepository.getResultsBySession(sessionId).first()
            } else {
                testResultRepository.allResults.first()
            }
            val results = entities.toDomainList()
            val jsonString = json.encodeToString(results)
            if (!outputDir.exists()) outputDir.mkdirs()
            val file = File(outputDir, fileName)
            file.writeText(jsonString)
            Result.Success(file.absolutePath)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Export failed")
        }
    }

    sealed class Result {
        data class Success(val path: String) : Result()
        data class Error(val message: String) : Result()
    }
}
