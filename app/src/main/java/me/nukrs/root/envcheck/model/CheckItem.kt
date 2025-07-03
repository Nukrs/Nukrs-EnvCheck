package me.nukrs.root.envcheck.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.ui.graphics.vector.ImageVector

data class CheckItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val status: CheckStatus = CheckStatus.PENDING,
    val details: CheckDetails? = null
)

data class CheckDetails(
    val passedChecks: List<String> = emptyList(),
    val failedChecks: List<String> = emptyList(),
    val warningChecks: List<String> = emptyList(),
    val score: String = "",
    val recommendation: String = ""
)

enum class CheckStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    WARNING
}

val checkItems = listOf(
    CheckItem(
        id = "tee_check",
        title = "TEE 检测",
        description = "检测可信执行环境状态",
        icon = Icons.Default.Security
    ),
    CheckItem(
        id = "bootloader_check",
        title = "Bootloader 检测",
        description = "检测引导加载程序解锁状态",
        icon = Icons.Default.Lock
    ),
    CheckItem(
        id = "pm_package_check",
        title = "PM 包名检测",
        description = "检测系统中的敏感包名和应用",
        icon = Icons.Default.Apps
    ),
    CheckItem(
        id = "selinux_check",
        title = "SELinux 检测",
        description = "检测SELinux安全策略状态",
        icon = Icons.Default.AdminPanelSettings
    ),
    CheckItem(
        id = "system_integrity_check",
        title = "系统完整性检测",
        description = "检测系统文件和权限完整性",
        icon = Icons.Default.Verified
    ),
    CheckItem(
        id = "network_security_check",
        title = "网络安全检测",
        description = "检测网络配置和证书安全",
        icon = Icons.Default.NetworkCheck
    )
)