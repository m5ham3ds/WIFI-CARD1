package com.example.presentation.settings

import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.entity.RouterProfileEntity
import com.example.domain.usecase.ManageRoutersUseCase
import com.example.presentation.common.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.local.preferences.AppPreferences
import kotlinx.coroutines.flow.first

class RouterManagerViewModel(
    private val manageRoutersUseCase: ManageRoutersUseCase,
    private val appPreferences: AppPreferences
) : BaseViewModel() {

    sealed class UiEvent {
        data class ShowMessage(val resId: Int) : UiEvent()
        data class NavigateToTest(val routerId: Long) : UiEvent()
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    val routers: StateFlow<List<RouterProfileEntity>> = manageRoutersUseCase.allRouters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRouter(router: RouterProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            manageRoutersUseCase.addRouter(router)
            _uiEvent.emit(UiEvent.ShowMessage(R.string.router_added))
        }
    }

    fun updateRouter(router: RouterProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            manageRoutersUseCase.updateRouter(router)
            _uiEvent.emit(UiEvent.ShowMessage(R.string.router_updated))
        }
    }

    fun deleteRouter(router: RouterProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            manageRoutersUseCase.deleteRouter(router)
            if (router.isDefault) {
                val remaining = manageRoutersUseCase.allRouters.first()
                val nextDef = remaining.firstOrNull { it.id != router.id }
                if (nextDef != null) {
                    manageRoutersUseCase.setDefault(nextDef.id)
                    appPreferences.setDefaultRouterId(nextDef.id)
                }
            }
            _uiEvent.emit(UiEvent.ShowMessage(R.string.router_deleted))
        }
    }

    fun setDefaultRouter(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            manageRoutersUseCase.setDefault(id)
            appPreferences.setDefaultRouterId(id)
        }
    }
}
