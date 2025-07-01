package me.nukrs.root.envcheck.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Lock
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
    )
)