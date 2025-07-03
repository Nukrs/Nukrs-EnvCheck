package me.nukrs.root.envcheck.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.nukrs.root.envcheck.service.UpdateService
import me.nukrs.root.envcheck.service.UpdateCheckResult

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    
    private val updateService = UpdateService(application)
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()
    
    private val _updateResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateResult: StateFlow<UpdateCheckResult?> = _updateResult.asStateFlow()
    
    init {
        // 应用启动时自动检测更新
        checkForUpdates(isAutoCheck = true)
    }
    
    fun checkForUpdates(isAutoCheck: Boolean = false) {
        if (_updateState.value is UpdateState.Checking) return
        
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            
            try {
                val result = updateService.checkForUpdates()
                _updateResult.value = result
                
                when {
                    result.error != null -> {
                        _updateState.value = UpdateState.Error(result.error)
                        if (!isAutoCheck) {
                            // 手动检测时显示错误信息
                            _showUpdateDialog.value = true
                        }
                    }
                    result.hasUpdate -> {
                        _updateState.value = UpdateState.UpdateAvailable(result.versionInfo!!)
                        _showUpdateDialog.value = true
                    }
                    else -> {
                        _updateState.value = UpdateState.UpToDate
                        if (!isAutoCheck) {
                            // 手动检测时显示已是最新版本
                            _showUpdateDialog.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "检查更新失败")
                if (!isAutoCheck) {
                    _showUpdateDialog.value = true
                }
            }
        }
    }
    
    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }
    
    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
        _updateResult.value = null
    }
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class UpdateAvailable(val versionInfo: me.nukrs.root.envcheck.service.VersionInfo) : UpdateState()
    data class Error(val message: String) : UpdateState()
}