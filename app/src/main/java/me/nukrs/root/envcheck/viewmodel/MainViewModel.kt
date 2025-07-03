package me.nukrs.root.envcheck.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import me.nukrs.root.envcheck.model.CheckItem
import me.nukrs.root.envcheck.model.CheckStatus
import me.nukrs.root.envcheck.model.checkItems
import me.nukrs.root.envcheck.service.CheckService

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val checkService = CheckService(application)
    
    private val _checkItems = MutableStateFlow(checkItems)
    val checkItemsList: StateFlow<List<CheckItem>> = _checkItems.asStateFlow()
    
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()
    
    fun startCheck(itemId: String) {
        if (_isChecking.value) return
        
        viewModelScope.launch {
            _isChecking.value = true
            
            val currentItems = _checkItems.value.toMutableList()
            val itemIndex = currentItems.indexOfFirst { it.id == itemId }
            
            if (itemIndex != -1) {
                val flow = when (itemId) {
                    "tee_check" -> checkService.performTeeCheck()
                    "bootloader_check" -> checkService.performBootloaderCheck()
                    "pm_package_check" -> checkService.performPmPackageCheck()
                    "selinux_check" -> checkService.performSelinuxCheck()
                    "system_integrity_check" -> checkService.performSystemIntegrityCheck()
                    "network_security_check" -> checkService.performNetworkSecurityCheck()
                    else -> return@launch
                }
                
                flow.collect { (status, details) ->
                    val updatedItems = _checkItems.value.toMutableList()
                    updatedItems[itemIndex] = updatedItems[itemIndex].copy(status = status, details = details)
                    _checkItems.value = updatedItems.toList()
                    
                    if (status != CheckStatus.RUNNING) {
                        _isChecking.value = false
                    }
                }
            }
        }
    }
    
    fun startAllChecks() {
        if (_isChecking.value) return
        
        viewModelScope.launch {
            _isChecking.value = true
            
            // 重置所有状态
            val resetItems = _checkItems.value.map { it.copy(status = CheckStatus.PENDING) }
            _checkItems.value = resetItems
            
            // 依次执行所有检测
            for (item in resetItems) {
                val currentItems = _checkItems.value.toMutableList()
                val itemIndex = currentItems.indexOfFirst { it.id == item.id }
                
                if (itemIndex != -1) {
                    val flow = when (item.id) {
                        "tee_check" -> checkService.performTeeCheck()
                        "bootloader_check" -> checkService.performBootloaderCheck()
                        "pm_package_check" -> checkService.performPmPackageCheck()
                        "selinux_check" -> checkService.performSelinuxCheck()
                        "system_integrity_check" -> checkService.performSystemIntegrityCheck()
                        "network_security_check" -> checkService.performNetworkSecurityCheck()
                        else -> continue
                    }
                    
                    flow.collect { (status, details) ->
                        val updatedItems = _checkItems.value.toMutableList()
                        updatedItems[itemIndex] = updatedItems[itemIndex].copy(status = status, details = details)
                        _checkItems.value = updatedItems.toList()
                    }
                }
            }
            
            _isChecking.value = false
        }
    }
    
    fun resetAllChecks() {
        if (_isChecking.value) return
        
        val resetItems = _checkItems.value.map { it.copy(status = CheckStatus.PENDING) }
        _checkItems.value = resetItems
    }
    
    fun simulateAllPass() {
        viewModelScope.launch {
            _isChecking.value = true
            
            // 模拟检测过程
            val updatedItems = _checkItems.value.map { item ->
                item.copy(
                    status = CheckStatus.RUNNING,
                    details = item.details?.copy(
                        score = "检测中...",
                        recommendation = "正在进行安全检测，请稍候..."
                    )
                )
            }
            _checkItems.value = updatedItems
            
            // 延迟模拟检测时间
            delay(1500)
            
            // 设置所有项目为通过状态
            val passedItems = _checkItems.value.map { item ->
                item.copy(
                    status = CheckStatus.PASSED,
                    details = item.details?.copy(
                        score = "100%",
                        recommendation = "恭喜！您的设备已通过所有安全检测。",
                        passedChecks = item.details.passedChecks + item.details.failedChecks + item.details.warningChecks,
                        failedChecks = emptyList(),
                        warningChecks = emptyList()
                    )
                )
            }
            _checkItems.value = passedItems
            
            _isChecking.value = false
        }
    }
}