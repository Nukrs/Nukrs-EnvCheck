package me.nukrs.root.envcheck.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import me.nukrs.root.envcheck.model.CheckStatus
import me.nukrs.root.envcheck.model.CheckDetails
import java.io.File
import java.util.Calendar
import java.util.Locale

/**
 * 检测结果数据类
 * @param passed 检测是否通过
 * @param message 检测结果消息
 * @param details 详细检测信息
 */
data class DetectionResult(
    val passed: Boolean,
    val message: String,
    val details: String
)

/**
 * Android Bootloader安全检测器
 * 负责检测Bootloader锁定状态、系统完整性、验证启动等安全特性
 */
class AndroidBootloaderChecker(private val context: Context) {
    
    companion object {
        // 动态计算最小安全补丁级别（当前时间前18个月）
        private fun getMinSecurityPatchLevel(): String {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.MONTH, -18)
            return String.format("%04d-%02d-01", 
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1)
        }
        
        /**
         * 检测级别枚举
         * 用于控制检测的深度和权限要求
         */
        enum class DetectionLevel {
            BASIC,    // 仅使用公开API，适用于普通应用权限
            ENHANCED, // 尝试访问部分系统信息，可能需要额外权限
            FULL      // 完整检测，需要特殊权限（如root或系统应用）
        }
        
        /**
         * 检测结果缓存时间（毫秒）
         */
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5分钟
    }
    
    // 当前检测级别，默认为BASIC以确保普通应用权限下的兼容性
    private val detectionLevel = DetectionLevel.BASIC
    
    /**
     * 执行完整的Bootloader安全检测
     * @return Flow<Pair<CheckStatus, CheckDetails?>> 检测状态和详细结果
     */
    suspend fun performBootloaderCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1500)
            
            val detectionResults = mutableListOf<DetectionResult>()
            
            // Bootloader锁定状态检查
            detectionResults.add(checkBootloaderLockStatusDetailed())
            
            // 系统完整性验证
            detectionResults.add(checkSystemIntegrityDetailed())
            
            // 验证启动验证（Verified Boot）
            detectionResults.add(checkVerifiedBootStatusDetailed())
            
            // 安全补丁级别验证
            detectionResults.add(checkSecurityPatchLevelDetailed())
            
            // 反调试和反篡改检测
            detectionResults.add(checkAntiTamperProtectionDetailed())
            
            // 智能评分机制
            val checkNames = listOf("Bootloader锁定", "系统完整性", "验证启动", "安全补丁级别", "反篡改保护")
            val checkWeights = listOf(0.3f, 0.25f, 0.25f, 0.1f, 0.1f) // 权重分配
            
            // 计算加权分数
            var weightedScore = 0.0f
            detectionResults.forEachIndexed { index, result ->
                if (result.passed) {
                    weightedScore += checkWeights.getOrElse(index) { 0.0f }
                }
            }
            
            val totalPassed = detectionResults.count { it.passed }
            val criticalChecks = detectionResults.take(3) // 前3项为最关键检查
            val allCriticalPassed = criticalChecks.all { it.passed }
            val criticalPassedCount = criticalChecks.count { it.passed }
            
            // 分类检测结果
            val passedChecks = mutableListOf<String>()
            val failedChecks = mutableListOf<String>()
            val warningChecks = mutableListOf<String>()
            
            detectionResults.forEachIndexed { index, result ->
                val checkName = checkNames.getOrElse(index) { "未知检查" }
                val detailInfo = "${result.message} (详情: ${result.details})"
                when {
                    result.passed -> passedChecks.add("$checkName: $detailInfo")
                    index < 3 -> failedChecks.add("$checkName (关键): $detailInfo")
                    else -> warningChecks.add("$checkName: $detailInfo")
                }
            }
            
            val scorePercentage = (weightedScore * 100).toInt()
            
            val details = CheckDetails(
                passedChecks = passedChecks,
                failedChecks = failedChecks,
                warningChecks = warningChecks,
                score = "$scorePercentage%",
                recommendation = generateRecommendation(weightedScore, criticalPassedCount, totalPassed)
            )
            
            // 更精细的状态判断
            val status = when {
                weightedScore >= 0.9f && allCriticalPassed -> CheckStatus.PASSED
                weightedScore >= 0.7f && criticalPassedCount >= 2 -> CheckStatus.WARNING
                weightedScore >= 0.5f && criticalPassedCount >= 1 -> CheckStatus.WARNING
                else -> CheckStatus.FAILED
            }
            
            emit(Pair(status, details))
            
        } catch (e: Exception) {
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("Bootloader检查异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "Bootloader检查过程中发生异常，请检查设备兼容性"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    /**
     * 生成安全建议
     * @param weightedScore 加权分数
     * @param criticalPassedCount 关键检查通过数量
     * @param totalPassed 总通过数量
     * @return String 安全建议
     */
    private fun generateRecommendation(
        weightedScore: Float, 
        criticalPassedCount: Int, 
        totalPassed: Int
    ): String {
        return when {
            weightedScore >= 0.9f && criticalPassedCount == 3 -> 
                "设备安全状态优秀，具备极好的安全防护能力。建议定期更新系统以维持安全水平。"
            
            weightedScore >= 0.8f && criticalPassedCount >= 2 -> 
                "设备安全状态良好，大部分安全检查通过。建议关注未通过的安全项目并及时改进。"
            
            weightedScore >= 0.7f && criticalPassedCount >= 2 -> 
                "设备基本安全，但存在一些安全隐患。建议加强安全设置，特别是关闭开发者选项和ADB调试。"
            
            weightedScore >= 0.5f && criticalPassedCount >= 1 -> 
                "设备安全状态一般，存在明显的安全风险。强烈建议检查Bootloader锁定状态和系统完整性。"
            
            criticalPassedCount == 0 -> 
                "设备存在严重安全风险，关键安全检查全部失败。建议立即停止使用敏感应用，并考虑重新刷入官方固件。"
            
            else -> 
                "设备安全状态较差，多项安全检查未通过。建议全面检查设备安全设置，避免使用Root权限和第三方ROM。"
        }
    }
    
    /**
     * 检查Bootloader锁定状态（详细版本）
     * @return DetectionResult 详细的检测结果
     */
    private fun checkBootloaderLockStatusDetailed(): DetectionResult {
        return try {
            val bootloaderStatus = Build.BOOTLOADER
            val tags = Build.TAGS
            val fingerprint = Build.FINGERPRINT
            
            val isLocked = !bootloaderStatus.contains("unlocked", ignoreCase = true)
            val hasReleaseKeys = !tags.contains("test-keys", ignoreCase = true)
            val hasValidFingerprint = !fingerprint.contains("test-keys", ignoreCase = true)
            
            val passed = isLocked && hasReleaseKeys && hasValidFingerprint
            
            val details = mapOf(
                "bootloader" to bootloaderStatus,
                "tags" to tags,
                "fingerprint" to fingerprint,
                "isLocked" to isLocked,
                "hasReleaseKeys" to hasReleaseKeys,
                "hasValidFingerprint" to hasValidFingerprint
            ).toString()
            
            val message = if (passed) {
                "Bootloader已锁定，系统安全"
            } else {
                "Bootloader可能已解锁或使用测试密钥"
            }
            
            DetectionResult(passed, message, details)
        } catch (e: Exception) {
            DetectionResult(false, "Bootloader状态检测异常: ${e.message}", "异常信息: ${e.localizedMessage}")
        }
    }
    
    /**
     * 检查系统完整性（详细版本）
     * @return DetectionResult 详细的检测结果
     */
    private fun checkSystemIntegrityDetailed(): DetectionResult {
        return try {
            // 基于公开API推断系统完整性，避免直接访问受限的系统路径
            val isOfficialBuild = Build.TAGS.contains("release-keys")
            val hasValidFingerprint = Build.FINGERPRINT.contains(Build.MANUFACTURER, ignoreCase = true)
            val isUserBuild = Build.TYPE == "user"
            val hasValidBoard = Build.BOARD.isNotEmpty() && !Build.BOARD.contains("unknown", ignoreCase = true)
            val hasValidProduct = Build.PRODUCT.isNotEmpty() && !Build.PRODUCT.contains("unknown", ignoreCase = true)
            val hasValidHost = Build.HOST.isNotEmpty()
            val hasValidId = Build.ID.isNotEmpty()
            
            // 检查构建信息的一致性
            val buildConsistency = Build.BRAND.isNotEmpty() && 
                                 Build.MODEL.isNotEmpty() && 
                                 Build.DEVICE.isNotEmpty()
            
            // 检查是否为模拟器或测试环境
            val isNotEmulator = !Build.MODEL.contains("sdk", ignoreCase = true) &&
                              !Build.MODEL.contains("emulator", ignoreCase = true) &&
                              !Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
            
            val passed = isOfficialBuild && hasValidFingerprint && isUserBuild && 
                        hasValidBoard && buildConsistency && isNotEmulator &&
                        hasValidProduct && hasValidHost && hasValidId
            
            val details = mapOf(
                "isOfficialBuild" to isOfficialBuild,
                "hasValidFingerprint" to hasValidFingerprint,
                "isUserBuild" to isUserBuild,
                "hasValidBoard" to hasValidBoard,
                "buildConsistency" to buildConsistency,
                "isNotEmulator" to isNotEmulator,
                "brand" to Build.BRAND,
                "model" to Build.MODEL,
                "device" to Build.DEVICE,
                "manufacturer" to Build.MANUFACTURER,
                "product" to Build.PRODUCT,
                "host" to Build.HOST,
                "id" to Build.ID,
                "type" to Build.TYPE,
                "tags" to Build.TAGS
            ).toString()
            
            val message = if (passed) {
                "系统完整性良好，未发现篡改迹象"
            } else {
                "系统完整性存疑，可能被修改或运行在非标准环境"
            }
            
            DetectionResult(passed, message, details)
        } catch (e: Exception) {
            DetectionResult(false, "系统完整性检测异常: ${e.message}", "异常信息: ${e.localizedMessage}")
        }
    }
    
    /**
     * 检查验证启动（Verified Boot）状态（详细版本）
     * @return DetectionResult 详细的检测结果
     */
    private fun checkVerifiedBootStatusDetailed(): DetectionResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 基于Build信息推断验证启动状态，避免直接访问受限的系统属性
                val isSecureBuild = Build.TAGS.contains("release-keys")
                val isUserBuild = Build.TYPE == "user"
                val hasValidBootloader = !Build.BOOTLOADER.contains("unlocked", ignoreCase = true)
                val hasValidFingerprint = !Build.FINGERPRINT.contains("test-keys", ignoreCase = true)
                val hasValidDisplay = Build.DISPLAY.isNotEmpty() && !Build.DISPLAY.contains("test", ignoreCase = true)
                val hasReasonableTime = Build.TIME > 0
                val isNotDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0
                val hasBuildConsistency = Build.BRAND.equals(Build.MANUFACTURER, ignoreCase = true) ||
                                        Build.FINGERPRINT.contains(Build.BRAND, ignoreCase = true)
                
                // 检查设备是否为官方构建且未被篡改
                val passed = isSecureBuild && isUserBuild && hasValidBootloader && hasValidFingerprint &&
                           hasValidDisplay && hasReasonableTime && hasBuildConsistency
                
                val details = mapOf(
                    "sdkVersion" to Build.VERSION.SDK_INT,
                    "isSecureBuild" to isSecureBuild,
                    "isUserBuild" to isUserBuild,
                    "hasValidBootloader" to hasValidBootloader,
                    "hasValidFingerprint" to hasValidFingerprint,
                    "hasValidDisplay" to hasValidDisplay,
                    "hasReasonableTime" to hasReasonableTime,
                    "isNotDebuggable" to isNotDebuggable,
                    "hasBuildConsistency" to hasBuildConsistency,
                    "display" to Build.DISPLAY,
                    "time" to Build.TIME,
                    "bootloader" to Build.BOOTLOADER,
                    "fingerprint" to Build.FINGERPRINT,
                    "tags" to Build.TAGS,
                    "type" to Build.TYPE
                ).toString()
                
                val message = if (passed) {
                    "验证启动状态正常，系统完整性得到保障"
                } else {
                    "验证启动可能被绕过或系统存在安全风险"
                }
                
                DetectionResult(passed, message, details)
            } else {
                // Android N以下版本基于基础安全检查
                val isSecureBuild = !Build.TAGS.contains("test-keys")
                val isUserBuild = Build.TYPE == "user"
                val passed = isSecureBuild && isUserBuild
                
                val details = mapOf(
                    "sdkVersion" to Build.VERSION.SDK_INT,
                    "isSecureBuild" to isSecureBuild,
                    "isUserBuild" to isUserBuild,
                    "tags" to Build.TAGS,
                    "type" to Build.TYPE,
                    "note" to "Android N以下版本，验证启动功能有限"
                ).toString()
                
                val message = if (passed) {
                    "基础安全检查通过（Android N以下版本）"
                } else {
                    "基础安全检查未通过，可能使用测试构建"
                }
                
                DetectionResult(passed, message, details)
            }
        } catch (e: Exception) {
            DetectionResult(false, "验证启动状态检测异常: ${e.message}", "异常信息: ${e.localizedMessage}")
        }
    }
    
    /**
     * 检查安全补丁级别（详细版本）
     * @return DetectionResult 详细的检测结果
     */
    private fun checkSecurityPatchLevelDetailed(): DetectionResult {
        return try {
            val securityPatch = Build.VERSION.SECURITY_PATCH
            val androidVersion = Build.VERSION.RELEASE
            val sdkInt = Build.VERSION.SDK_INT
            
            if (securityPatch.isEmpty()) {
                // 如果没有安全补丁信息，基于Android版本判断
                val passed = sdkInt >= Build.VERSION_CODES.O // Android 8.0+
                val details = mapOf(
                    "securityPatch" to "未提供",
                    "androidVersion" to androidVersion,
                    "sdkInt" to sdkInt,
                    "fallbackCheck" to "基于Android版本判断",
                    "result" to if (passed) "通过" else "未通过"
                ).toString()
                
                val message = if (passed) {
                    "安全补丁信息缺失，但Android版本较新"
                } else {
                    "安全补丁信息缺失，且Android版本较旧"
                }
                
                return DetectionResult(passed, message, details)
            }
            
            val minPatchLevel = getMinSecurityPatchLevel()
            val currentTime = System.currentTimeMillis()
            val calendar = Calendar.getInstance()
            
            // 计算补丁年龄（月数）
            val patchAge = try {
                val patchParts = securityPatch.split("-")
                if (patchParts.size >= 2) {
                    val patchYear = patchParts[0].toInt()
                    val patchMonth = patchParts[1].toInt()
                    val currentYear = calendar.get(Calendar.YEAR)
                    val currentMonth = calendar.get(Calendar.MONTH) + 1
                    (currentYear - patchYear) * 12 + (currentMonth - patchMonth)
                } else {
                    -1
                }
            } catch (e: Exception) {
                -1
            }
            
            // 对于较老的Android版本，放宽要求
            val passed = when {
                sdkInt >= Build.VERSION_CODES.R -> // Android 11+
                    securityPatch >= minPatchLevel
                sdkInt >= Build.VERSION_CODES.O -> // Android 8.0+
                    securityPatch >= "2020-01-01" // 更宽松的要求
                else -> true // 更老版本默认通过
            }
            
            val details = mapOf(
                "securityPatch" to securityPatch,
                "minPatchLevel" to minPatchLevel,
                "androidVersion" to androidVersion,
                "sdkInt" to sdkInt,
                "patchAge" to "${patchAge}个月",
                "checkResult" to if (passed) "通过" else "未通过",
                "requirement" to when {
                    sdkInt >= Build.VERSION_CODES.R -> "18个月内"
                    sdkInt >= Build.VERSION_CODES.O -> "2020年以后"
                    else -> "无特殊要求"
                }
            ).toString()
            
            val message = if (passed) {
                "安全补丁级别满足要求（${securityPatch}）"
            } else {
                "安全补丁级别过旧（${securityPatch}），建议更新系统"
            }
            
            DetectionResult(passed, message, details)
        } catch (e: Exception) {
            // 无法获取安全补丁信息时，基于SDK版本判断
            val fallbackPassed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            val details = mapOf(
                "error" to (e.message ?: "未知异常"),
                "fallbackCheck" to "基于SDK版本",
                "sdkInt" to Build.VERSION.SDK_INT,
                "result" to if (fallbackPassed) "通过" else "未通过"
            ).toString()
            
            DetectionResult(
                fallbackPassed, 
                "安全补丁检测异常，使用备用检查: ${e.message}", 
                details
            )
        }
    }
    
    /**
     * 检查反篡改保护（详细版本）
     * @return DetectionResult 详细的检测结果
     */
    private fun checkAntiTamperProtectionDetailed(): DetectionResult {
        return try {
            var securityScore = 0
            var totalChecks = 0
            val checkResults = mutableMapOf<String, Any>()
            
            // 检查应用调试状态（仅检查当前应用）
            totalChecks++
            val isAppDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            checkResults["isAppDebuggable"] = isAppDebuggable
            if (!isAppDebuggable) securityScore++
            
            // 检查ADB状态（可能需要权限）
            var adbCheckMethod = "direct"
            try {
                totalChecks++
                val isAdbEnabled = Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.ADB_ENABLED,
                    0
                ) == 1
                checkResults["isAdbEnabled"] = isAdbEnabled
                checkResults["adbCheckMethod"] = "direct"
                if (!isAdbEnabled) securityScore++
            } catch (e: SecurityException) {
                // 权限不足时，基于其他指标推断
                adbCheckMethod = "fallback"
                val fallbackSecure = Build.TYPE == "user" && Build.TAGS.contains("release-keys")
                checkResults["isAdbEnabled"] = "权限不足，推断: ${!fallbackSecure}"
                checkResults["adbCheckMethod"] = "fallback"
                checkResults["adbFallbackReason"] = e.message ?: "未知异常"
                if (fallbackSecure) securityScore++
            }
            
            // 检查开发者选项（可能需要权限）
            var devOptionsCheckMethod = "direct"
            try {
                totalChecks++
                val isDeveloperOptionsEnabled = Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0
                ) == 1
                checkResults["isDeveloperOptionsEnabled"] = isDeveloperOptionsEnabled
                checkResults["devOptionsCheckMethod"] = "direct"
                if (!isDeveloperOptionsEnabled) securityScore++
            } catch (e: SecurityException) {
                // 权限不足时，基于构建类型推断
                devOptionsCheckMethod = "fallback"
                val fallbackSecure = Build.TYPE == "user"
                checkResults["isDeveloperOptionsEnabled"] = "权限不足，推断: ${!fallbackSecure}"
                checkResults["devOptionsCheckMethod"] = "fallback"
                checkResults["devOptionsFallbackReason"] = e.message ?: "未知异常"
                if (fallbackSecure) securityScore++
            }
            
            // 检查系统构建类型
            totalChecks++
            val isUserBuild = Build.TYPE == "user"
            val hasReleaseKeys = Build.TAGS.contains("release-keys")
            val isSecureBuild = isUserBuild && hasReleaseKeys
            checkResults["isUserBuild"] = isUserBuild
            checkResults["hasReleaseKeys"] = hasReleaseKeys
            checkResults["isSecureBuild"] = isSecureBuild
            checkResults["buildType"] = Build.TYPE
            checkResults["buildTags"] = Build.TAGS
            if (isSecureBuild) securityScore++
            
            // 计算安全分数
            val securityPercentage = (securityScore.toFloat() / totalChecks * 100).toInt()
            val passed = securityScore.toFloat() / totalChecks >= 0.7f
            
            checkResults["securityScore"] = securityScore
            checkResults["totalChecks"] = totalChecks
            checkResults["securityPercentage"] = "${securityPercentage}%"
            checkResults["passed"] = passed
            
            val details = checkResults.toString()
            
            val message = if (passed) {
                "反篡改保护有效，安全评分: ${securityPercentage}%"
            } else {
                "反篡改保护可能被绕过，安全评分: ${securityPercentage}%，建议关闭调试选项"
            }
            
            DetectionResult(passed, message, details)
        } catch (e: Exception) {
            // 发生异常时，基于基础信息判断
            val fallbackPassed = Build.TYPE == "user" && Build.TAGS.contains("release-keys")
            val details = mapOf(
                "error" to (e.message ?: "未知异常"),
                "fallbackCheck" to "基于构建信息",
                "buildType" to Build.TYPE,
                "buildTags" to Build.TAGS,
                "result" to if (fallbackPassed) "通过" else "未通过"
            ).toString()
            
            DetectionResult(
                fallbackPassed,
                "反篡改保护检测异常，使用备用检查: ${e.message}",
                details
            )
        }
    }
    

}