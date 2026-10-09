package com.example.presentation.history

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.TestResultEntity
import com.example.data.local.entity.TestSessionEntity
import com.example.data.mapper.TestResultMapper.getSubReason
import com.example.domain.model.ResultSubReason
import com.example.domain.repository.ISessionRepository
import com.example.domain.repository.ITestResultRepository
import com.example.domain.usecase.ExportResultsUseCase
import com.example.presentation.common.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val sessionRepository: ISessionRepository,
    private val testResultRepository: ITestResultRepository,
    private val exportResultsUseCase: ExportResultsUseCase
) : BaseViewModel() {

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    private val _currentFilter = MutableStateFlow("all") // "all"/"success"/"failure"
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    val sessions: StateFlow<List<TestSessionEntity>> = sessionRepository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredResults: StateFlow<List<TestResultEntity>> = combine(
        _selectedSessionId.flatMapLatest { id ->
            if (id != null) testResultRepository.getResultsBySession(id)
            else flowOf(emptyList())
        },
        _currentFilter
    ) { results, filter ->
        when (filter) {
            "success" -> results.filter { it.state == "Success" || it.category == "SUCCESS" }
            "two_devices" -> results.filter { 
                it.getSubReason() == ResultSubReason.TWO_DEVICES_SUCCESS ||
                it.successReason == "TWO_DEVICES_ACTIVE_CARD" ||
                it.subReason == ResultSubReason.TWO_DEVICES_SUCCESS.name
            }
            "failure" -> results.filter { it.category == "FAILURE" || (it.state != "Success" && it.state != "Timeout" && it.state != "Network_Error" && it.state != "Engine_Error") }
            "timeout" -> results.filter { it.state == "Timeout" || it.category == "TIMEOUT" || it.state == "Network_Error" || it.category == "NETWORK_ERROR" }
            else -> results
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    sealed class ExportEvent {
        data class Success(val file: File, val message: String) : ExportEvent()
        data class Error(val message: String) : ExportEvent()
    }

    private val _exportEvent = MutableSharedFlow<ExportEvent>()
    val exportEvent: SharedFlow<ExportEvent> = _exportEvent.asSharedFlow()

    private val _exportStatus = MutableSharedFlow<String>()
    val exportStatus: SharedFlow<String> = _exportStatus.asSharedFlow()

    fun selectSession(id: Long) {
        _selectedSessionId.value = id
    }

    fun clearSessionSelection() {
        _selectedSessionId.value = null
    }

    fun setFilter(filter: String) {
        _currentFilter.value = filter
    }

    fun exportToFile(context: Context, fileName: String) {
        val sessionId = _selectedSessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            val dir = context.getExternalFilesDir("exports") ?: context.filesDir
            val result = exportResultsUseCase(dir, fileName, sessionId)
            val isEn = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
            when (result) {
                is ExportResultsUseCase.Result.Success -> {
                    val file = File(result.path)
                    val message = if (isEn) "Results exported successfully to: ${file.name}" else "تم تصدير النتائج بنجاح إلى: ${file.name}"
                    _exportStatus.emit(message)
                    _exportEvent.emit(ExportEvent.Success(file, message))
                }
                is ExportResultsUseCase.Result.Error -> {
                    val message = if (isEn) "Export failed: ${result.message}" else "فشل عملية التصدير: ${result.message}"
                    _exportStatus.emit(message)
                    _exportEvent.emit(ExportEvent.Error(message))
                }
            }
        }
    }
}
