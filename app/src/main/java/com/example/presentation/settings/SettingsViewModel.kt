package com.example.presentation.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.preferences.AppPreferences
import com.example.data.local.preferences.ThemePreferences
import com.example.domain.repository.ISessionRepository
import com.example.domain.repository.ITestResultRepository
import com.example.domain.usecase.ExportResultsUseCase
import com.example.presentation.common.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val appPreferences: AppPreferences,
    private val themePreferences: ThemePreferences,
    private val sessionRepository: ISessionRepository,
    private val testResultRepository: ITestResultRepository,
    private val exportResultsUseCase: ExportResultsUseCase
) : BaseViewModel() {

    sealed class UiEvent {
        data class ShowMessage(val resId: Int) : UiEvent()
        data class ShowText(val text: String) : UiEvent()
        data class ShareExportFile(val file: File, val message: String) : UiEvent()
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    val pageLoadDelay = appPreferences.pageLoadDelay
    val cardTestDelay = appPreferences.cardTestDelay
    val screenshotDelay = appPreferences.screenshotDelay
    val successAction = appPreferences.successAction
    val vibrateOnSuccess = appPreferences.vibrateOnSuccess
    val soundOnSuccess = appPreferences.soundOnSuccess
    val enablePreload = appPreferences.enablePreload
    val threadCount = appPreferences.threadCount
    val themeMode = themePreferences.themeMode
    val primaryColor = themePreferences.primaryColor
    val appLanguage = appPreferences.appLanguage

    fun setPageLoadDelay(delay: Long) {
        viewModelScope.launch {
            appPreferences.setPageLoadDelay(delay)
        }
    }

    fun setCardTestDelay(delay: Long) {
        viewModelScope.launch {
            appPreferences.setCardTestDelay(delay)
        }
    }

    fun setScreenshotDelay(delay: Long) {
        viewModelScope.launch {
            appPreferences.setScreenshotDelay(delay)
        }
    }

    fun setSuccessAction(action: String) {
        viewModelScope.launch {
            appPreferences.setSuccessAction(action)
        }
    }

    fun setVibrateOnSuccess(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setVibrateOnSuccess(enabled)
        }
    }

    fun setSoundOnSuccess(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setSoundOnSuccess(enabled)
        }
    }

    fun setEnablePreload(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setEnablePreload(enabled)
        }
    }

    fun setThreadCount(count: String) {
        viewModelScope.launch {
            appPreferences.setThreadCount(count)
        }
    }
    
    fun resetDelaysToDefault() {
        viewModelScope.launch {
            appPreferences.setPageLoadDelay(2000L)
            appPreferences.setCardTestDelay(3000L)
            appPreferences.setScreenshotDelay(2000L)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
            val nightMode = when (mode) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            appPreferences.setAppLanguage(lang)
        }
    }

    fun setPrimaryColor(colorKey: String) {
        viewModelScope.launch {
            themePreferences.setPrimaryColor(colorKey)
        }
    }

    fun confirmClearHistory() {
        if (com.example.service.TestService.isRunning.value) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowMessage(R.string.cannot_clear_history_active))
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            testResultRepository.deleteAll()
            sessionRepository.deleteAll()
            _uiEvent.emit(UiEvent.ShowMessage(R.string.history_cleared))
        }
    }

    fun exportAllResults(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val exportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports")
            val fileName = "all_sessions_results_${System.currentTimeMillis()}.json"
            val result = exportResultsUseCase(outputDir = exportDir, fileName = fileName, sessionId = null)
            val isEn = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
            when (result) {
                is ExportResultsUseCase.Result.Success -> {
                    val file = File(result.path)
                    val msg = if (isEn) "All records exported to: ${file.name}" else "تم تصدير جميع السجلات بنجاح إلى: ${file.name}"
                    _uiEvent.emit(UiEvent.ShareExportFile(file, msg))
                }
                is ExportResultsUseCase.Result.Error -> {
                    val msg = if (isEn) "Failed to export: ${result.message}" else "فشل تصدير السجلات: ${result.message}"
                    _uiEvent.emit(UiEvent.ShowText(msg))
                }
            }
        }
    }
}
