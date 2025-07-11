package me.nukrs.root.envcheck.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import me.nukrs.root.envcheck.model.CheckStatus
import me.nukrs.root.envcheck.model.CheckDetails
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Android系统完整性检测器
 * 负责检测系统文件和权限完整性
 */
class AndroidSystemIntegrityChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "SystemIntegrityChecker"
        
        // 普通应用可访问的系统文件列表
        private val ACCESSIBLE_SYSTEM_FILES = arrayOf(
            "/system/build.prop",
            "/system/etc/hosts",
            "/proc/version",
            "/proc/cpuinfo",
            "/proc/meminfo"
        )
        
        // 普通应用可检查的系统目录列表
        private val ACCESSIBLE_SYSTEM_DIRS = arrayOf(
            "/system",
            "/system/bin",
            "/system/lib",
            "/system/lib64",
            "/system/etc",
            "/system/framework",
            "/system/app",
            "/system/priv-app",
            "/data",
            "/vendor",
            "/product",
            "/proc",
            "/sys",
            "/cache"
        )
        
        // 系统完整性关键文件路径
        private val INTEGRITY_CHECK_FILES = arrayOf(
            "/system/framework/framework.jar",
            "/system/framework/services.jar",
            "/system/framework/android.jar",
            "/system/lib/libc.so",
            "/system/lib64/libc.so",
            "/system/lib/libm.so",
            "/system/lib/libdl.so",
            "/system/lib/liblog.so",
            "/system/bin/app_process",
            "/system/bin/app_process32",
            "/system/bin/app_process64",
            "/system/bin/sh",
            "/system/bin/toolbox",
            "/system/etc/security/cacerts",
            "/system/recovery-resource.dat"
        )
        
        // 系统属性检查列表
        private val SYSTEM_PROPERTIES = arrayOf(
            "ro.debuggable",
            "ro.secure",
            "ro.build.type",
            "ro.build.tags",
            "service.adb.root",
            "ro.build.version.security_patch",
            "ro.boot.verifiedbootstate",
            "ro.boot.flash.locked",
            "ro.build.selinux",
            "ro.boot.selinux",
            "ro.build.fingerprint",
            "ro.system.build.fingerprint",
            "ro.vendor.build.fingerprint",
            "ro.build.version.release",
            "ro.build.version.sdk"
        )
    }
    
    /**
     * 执行系统完整性检测
     * @return Flow<Pair<CheckStatus, CheckDetails?>> 检测状态和详情
     */
    suspend fun performSystemIntegrityCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            val integrityResults = mutableListOf<Boolean>()
            val checkNames = mutableListOf<String>()
            val warningChecks = mutableListOf<String>()
            val detailedInfo = mutableListOf<String>()
            
            // 阶段1: 系统文件可访问性检查
            emit(Pair(CheckStatus.RUNNING, CheckDetails(
                passedChecks = emptyList(),
                failedChecks = emptyList(),
                warningChecks = listOf("正在检查系统文件可访问性..."),
                score = "0%",
                recommendation = "检测进行中"
            )))
            delay(300)
            
            val fileAccessResult = checkSystemFileAccessibility()
            integrityResults.add(fileAccessResult.passed)
            checkNames.add("系统文件可访问性")
            if (fileAccessResult.warnings.isNotEmpty()) {
                warningChecks.addAll(fileAccessResult.warnings)
            }
            detailedInfo.addAll(fileAccessResult.details)
            
            // 阶段2: 系统属性检查
            emit(Pair(CheckStatus.RUNNING, CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> integrityResults.getOrNull(index) == true },
                failedChecks = emptyList(),
                warningChecks = listOf("正在检查系统属性配置..."),
                score = "${(integrityResults.count { it }.toFloat() / 1 * 25).toInt()}%",
                recommendation = "检测进行中"
            )))
            delay(300)
            
            val propertyResult = checkSystemProperties()
            integrityResults.add(propertyResult.passed)
            checkNames.add("系统属性检查")
            if (propertyResult.warnings.isNotEmpty()) {
                warningChecks.addAll(propertyResult.warnings)
            }
            detailedInfo.addAll(propertyResult.details)
            
            // 阶段3: 系统完整性文件检测
            emit(Pair(CheckStatus.RUNNING, CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> integrityResults.getOrNull(index) == true },
                failedChecks = emptyList(),
                warningChecks = listOf("正在检测系统完整性文件..."),
                score = "${(integrityResults.count { it }.toFloat() / 2 * 50).toInt()}%",
                recommendation = "检测进行中"
            )))
            delay(300)
            
            val integrityFileResult = checkSystemIntegrityFiles()
            integrityResults.add(integrityFileResult.passed)
            checkNames.add("系统完整性文件检测")
            if (integrityFileResult.warnings.isNotEmpty()) {
                warningChecks.addAll(integrityFileResult.warnings)
            }
            detailedInfo.addAll(integrityFileResult.details)
            
            // 阶段4: 系统目录权限检查
            emit(Pair(CheckStatus.RUNNING, CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> integrityResults.getOrNull(index) == true },
                failedChecks = emptyList(),
                warningChecks = listOf("正在检查系统目录权限..."),
                score = "${(integrityResults.count { it }.toFloat() / 3 * 75).toInt()}%",
                recommendation = "检测进行中"
            )))
            delay(300)
            
            val dirPermissionResult = checkSystemDirectoryPermissions()
            integrityResults.add(dirPermissionResult.passed)
            checkNames.add("系统目录权限")
            if (dirPermissionResult.warnings.isNotEmpty()) {
                warningChecks.addAll(dirPermissionResult.warnings)
            }
            detailedInfo.addAll(dirPermissionResult.details)
            
            // 阶段5: 进程和挂载点检查
            emit(Pair(CheckStatus.RUNNING, CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> integrityResults.getOrNull(index) == true },
                failedChecks = emptyList(),
                warningChecks = listOf("正在检查进程和挂载点..."),
                score = "${(integrityResults.count { it }.toFloat() / 4 * 90).toInt()}%",
                recommendation = "检测进行中"
            )))
            delay(300)
            
            val processResult = checkProcessAndMounts()
            integrityResults.add(processResult.passed)
            checkNames.add("进程和挂载点检查")
            if (processResult.warnings.isNotEmpty()) {
                warningChecks.addAll(processResult.warnings)
            }
            detailedInfo.addAll(processResult.details)
            
            val passedChecks = integrityResults.count { it }
            val totalChecks = integrityResults.size
            
            // 构建详细推荐信息
            val recommendationBuilder = StringBuilder()
            when {
                passedChecks == totalChecks && warningChecks.isEmpty() -> {
                    recommendationBuilder.append("✅ 系统完整性检测全部通过，系统环境安全正常。")
                }
                passedChecks >= totalChecks * 0.8 -> {
                    recommendationBuilder.append("⚠️ 大部分完整性检测通过，系统基本安全。")
                    if (warningChecks.isNotEmpty()) {
                        recommendationBuilder.append("\n\n⚠️ 发现的警告：")
                        warningChecks.forEach { warning ->
                            recommendationBuilder.append("\n• $warning")
                        }
                    }
                }
                passedChecks >= totalChecks * 0.6 -> {
                    recommendationBuilder.append("⚠️ 部分完整性检测通过，建议关注系统安全状态。")
                    val failedChecks = checkNames.filterIndexed { index, _ -> 
                        integrityResults.getOrNull(index) == false 
                    }
                    if (failedChecks.isNotEmpty()) {
                        recommendationBuilder.append("\n\n❌ 检测失败的项目：")
                        failedChecks.forEach { failed ->
                            recommendationBuilder.append("\n• $failed")
                        }
                    }
                }
                else -> {
                    recommendationBuilder.append("❌ 系统完整性检测发现多个问题，系统可能存在安全风险。")
                    recommendationBuilder.append("\n\n🔧 建议措施：")
                    recommendationBuilder.append("\n• 检查系统是否被Root或修改")
                    recommendationBuilder.append("\n• 验证系统更新和安全补丁")
                    recommendationBuilder.append("\n• 检查是否安装了可疑应用")
                    recommendationBuilder.append("\n• 考虑恢复出厂设置")
                }
            }
            
            // 添加详细的技术信息
            recommendationBuilder.append("\n\n📊 检测统计：")
            recommendationBuilder.append("\n• 检测项目总数: $totalChecks")
            recommendationBuilder.append("\n• 通过检测项: $passedChecks")
            recommendationBuilder.append("\n• 警告数量: ${warningChecks.size}")
            recommendationBuilder.append("\n• 设备型号: ${Build.MODEL}")
            recommendationBuilder.append("\n• Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            recommendationBuilder.append("\n• 安全补丁: ${Build.VERSION.SECURITY_PATCH}")
            
            if (detailedInfo.isNotEmpty()) {
                recommendationBuilder.append("\n\n🔍 详细检测信息：")
                detailedInfo.forEach { info ->
                    recommendationBuilder.append("\n• $info")
                }
            }
            
            val details = CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> integrityResults.getOrNull(index) == true },
                failedChecks = checkNames.filterIndexed { index, _ -> integrityResults.getOrNull(index) == false },
                warningChecks = warningChecks,
                score = "${(passedChecks.toFloat() / totalChecks * 100).toInt()}%",
                recommendation = recommendationBuilder.toString()
            )
            
            when {
                passedChecks == totalChecks && warningChecks.isEmpty() -> emit(Pair(CheckStatus.PASSED, details))
                passedChecks >= totalChecks * 0.7 || warningChecks.isNotEmpty() -> emit(Pair(CheckStatus.WARNING, details))
                else -> emit(Pair(CheckStatus.FAILED, details))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "系统完整性检测异常", e)
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("系统完整性检测异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "系统完整性检测过程中发生异常，请检查设备兼容性。\n错误详情: ${e.message}"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    /**
     * 检查系统文件可访问性（普通应用权限）
     */
    private fun checkSystemFileAccessibility(): IntegrityCheckResult {
        val warnings = mutableListOf<String>()
        val details = mutableListOf<String>()
        var passed = true
        var accessibleCount = 0
        
        try {
            for (filePath in ACCESSIBLE_SYSTEM_FILES) {
                try {
                    val file = File(filePath)
                    when {
                        file.exists() && file.canRead() -> {
                            accessibleCount++
                            details.add("✅ $filePath - 可访问 (${file.length()} bytes)")
                        }
                        file.exists() && !file.canRead() -> {
                            details.add("⚠️ $filePath - 存在但不可读")
                            warnings.add("系统文件权限异常: $filePath")
                        }
                        else -> {
                            details.add("❌ $filePath - 不存在或不可访问")
                            warnings.add("系统文件不可访问: $filePath")
                        }
                    }
                } catch (e: SecurityException) {
                    details.add("🔒 $filePath - 权限被拒绝")
                    warnings.add("访问权限被拒绝: $filePath")
                } catch (e: Exception) {
                    details.add("❌ $filePath - 检查异常: ${e.message}")
                    warnings.add("文件检查异常: $filePath - ${e.message}")
                }
            }
            
            // 如果可访问的文件数量过少，认为检测失败
            if (accessibleCount < ACCESSIBLE_SYSTEM_FILES.size * 0.6) {
                passed = false
                warnings.add("可访问的系统文件数量过少 ($accessibleCount/${ACCESSIBLE_SYSTEM_FILES.size})")
            }
            
            details.add("📊 系统文件可访问性统计: $accessibleCount/${ACCESSIBLE_SYSTEM_FILES.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "检查系统文件可访问性时出错", e)
            warnings.add("系统文件检查异常: ${e.message}")
            details.add("❌ 检查过程异常: ${e.message}")
            passed = false
        }
        
        return IntegrityCheckResult(passed, warnings, details)
    }
    
    /**
     * 检查系统属性（普通应用权限）
     */
    private fun checkSystemProperties(): IntegrityCheckResult {
        val warnings = mutableListOf<String>()
        val details = mutableListOf<String>()
        var passed = true
        
        try {
            for (property in SYSTEM_PROPERTIES) {
                try {
                    val value = getSystemProperty(property)
                    when (property) {
                        "ro.debuggable" -> {
                            if (value == "1") {
                                warnings.add("系统处于调试模式 (ro.debuggable=1)")
                                details.add("⚠️ $property = $value (调试模式开启)")
                            } else {
                                details.add("✅ $property = $value (正常)")
                            }
                        }
                        "ro.secure" -> {
                            if (value != "1") {
                                warnings.add("系统安全模式未启用 (ro.secure≠1)")
                                details.add("⚠️ $property = $value (安全模式关闭)")
                                passed = false
                            } else {
                                details.add("✅ $property = $value (安全模式开启)")
                            }
                        }
                        "ro.build.type" -> {
                            if (value == "eng" || value == "userdebug") {
                                warnings.add("检测到开发版本构建 (ro.build.type=$value)")
                                details.add("⚠️ $property = $value (开发版本)")
                            } else {
                                details.add("✅ $property = $value (用户版本)")
                            }
                        }
                        "ro.build.tags" -> {
                             if (value.contains("test-keys")) {
                                 warnings.add("检测到测试签名 (ro.build.tags包含test-keys)")
                                 details.add("⚠️ $property = $value (测试签名)")
                             } else {
                                 details.add("✅ $property = $value (正式签名)")
                             }
                         }
                         "service.adb.root" -> {
                             if (value == "1") {
                                 warnings.add("ADB调试模式已启用")
                                 details.add("⚠️ $property = $value (ADB调试开启)")
                             } else {
                                 details.add("✅ $property = $value (ADB调试关闭)")
                             }
                         }
                        "ro.build.version.security_patch" -> {
                             if (value.isNotEmpty()) {
                                 details.add("✅ $property = $value (安全补丁日期)")
                                 
                                 // 检查安全补丁是否过旧（超过6个月）
                                 try {
                                     val patchDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(value)
                                     val currentDate = java.util.Date()
                                     val diffInDays = (currentDate.time - (patchDate?.time ?: 0)) / (1000 * 60 * 60 * 24)
                                 
                                 when {
                                     diffInDays > 365 -> {
                                         warnings.add("安全补丁严重过旧 (${diffInDays}天前)")
                                         details.add("❌ 安全补丁距今${diffInDays}天 (严重过旧)")
                                         passed = false
                                     }
                                     diffInDays > 180 -> {
                                         warnings.add("安全补丁过旧 (${diffInDays}天前)")
                                         details.add("⚠️ 安全补丁距今${diffInDays}天")
                                     }
                                     else -> {
                                         details.add("✅ 安全补丁较新 (${diffInDays}天前)")
                                     }
                                 }
                                 } catch (e: Exception) {
                                     details.add("⚠️ 无法解析安全补丁日期格式")
                                 }
                             } else {
                                 warnings.add("安全补丁信息缺失")
                                 details.add("❌ $property - 信息缺失")
                             }
                         }
                         "ro.boot.verifiedbootstate" -> {
                             when (value) {
                                 "green" -> details.add("✅ $property = $value (验证启动正常)")
                                 "yellow" -> {
                                     warnings.add("验证启动状态异常 (yellow)")
                                     details.add("⚠️ $property = $value (验证启动警告)")
                                 }
                                 "orange", "red" -> {
                                     warnings.add("验证启动状态异常 ($value)")
                                     details.add("❌ $property = $value (验证启动失败)")
                                     passed = false
                                 }
                                 else -> details.add("ℹ️ $property = $value (未知状态)")
                             }
                         }
                         "ro.boot.flash.locked" -> {
                             if (value == "1") {
                                 details.add("✅ $property = $value (Bootloader已锁定)")
                             } else {
                                 warnings.add("Bootloader未锁定")
                                 details.add("⚠️ $property = $value (Bootloader未锁定)")
                             }
                         }
                         "ro.build.selinux" -> {
                             if (value == "1") {
                                 details.add("✅ $property = $value (SELinux构建支持启用)")
                             } else {
                                 warnings.add("SELinux构建支持未启用")
                                 details.add("⚠️ $property = $value (SELinux构建支持关闭)")
                             }
                         }
                         "ro.boot.selinux" -> {
                             when (value.lowercase()) {
                                 "enforcing" -> details.add("✅ $property = $value (SELinux强制模式)")
                                 "permissive" -> {
                                     warnings.add("SELinux处于宽松模式")
                                     details.add("⚠️ $property = $value (SELinux宽松模式)")
                                 }
                                 "disabled" -> {
                                     warnings.add("SELinux已禁用")
                                     details.add("❌ $property = $value (SELinux已禁用)")
                                     passed = false
                                 }
                                 else -> details.add("ℹ️ $property = $value (SELinux未知状态)")
                             }
                         }
                         "ro.build.fingerprint", "ro.system.build.fingerprint", "ro.vendor.build.fingerprint" -> {
                             if (value.isNotEmpty()) {
                                 // 检查指纹格式是否正常
                                 if (value.contains("/") && value.contains(":")) {
                                     details.add("✅ $property = $value (指纹格式正常)")
                                     
                                     // 检查是否为官方构建
                                     if (value.contains("release-keys")) {
                                         details.add("✅ 官方发布密钥签名")
                                     } else if (value.contains("test-keys")) {
                                         warnings.add("使用测试密钥签名")
                                         details.add("⚠️ 测试密钥签名构建")
                                     } else if (value.contains("dev-keys")) {
                                         warnings.add("使用开发密钥签名")
                                         details.add("⚠️ 开发密钥签名构建")
                                     }
                                 } else {
                                     warnings.add("构建指纹格式异常")
                                     details.add("⚠️ $property = $value (指纹格式异常)")
                                 }
                             } else {
                                 warnings.add("构建指纹为空")
                                 details.add("❌ $property - 指纹缺失")
                             }
                         }
                         "ro.build.version.release", "ro.build.version.sdk" -> {
                             if (value.isNotEmpty()) {
                                 details.add("✅ $property = $value")
                             } else {
                                 warnings.add("版本信息缺失: $property")
                                 details.add("❌ $property - 信息缺失")
                             }
                         }
                         else -> {
                             details.add("ℹ️ $property = $value")
                         }
                    }
                } catch (e: Exception) {
                    details.add("❌ $property - 获取失败: ${e.message}")
                    warnings.add("无法获取系统属性: $property")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "检查系统属性时出错", e)
            warnings.add("系统属性检查异常: ${e.message}")
            details.add("❌ 检查过程异常: ${e.message}")
            passed = false
        }
        
        return IntegrityCheckResult(passed, warnings, details)
    }
    
    /**
     * 检查系统完整性文件（普通应用权限）
     */
    private fun checkSystemIntegrityFiles(): IntegrityCheckResult {
        val warnings = mutableListOf<String>()
        val details = mutableListOf<String>()
        var passed = true
        var integrityFileCount = 0
        
        try {
            // 检查关键系统完整性文件
            for (filePath in INTEGRITY_CHECK_FILES) {
                try {
                    val file = File(filePath)
                    if (file.exists() && file.canRead()) {
                        integrityFileCount++
                        val fileSize = file.length()
                        details.add("✅ $filePath - 完整 (${fileSize} bytes)")
                        
                        // 检查文件权限是否正常
                        if (file.canWrite()) {
                            warnings.add("系统文件具有异常写权限: $filePath")
                            details.add("⚠️ $filePath - 异常的写权限")
                        }
                        
                        // 检查文件大小是否合理
                        if (fileSize == 0L) {
                            warnings.add("系统文件大小异常: $filePath")
                            details.add("⚠️ $filePath - 文件大小为0")
                        }
                    } else if (file.exists() && !file.canRead()) {
                        details.add("🔒 $filePath - 存在但不可读")
                        warnings.add("系统文件权限异常: $filePath")
                    } else {
                        details.add("❌ $filePath - 不存在")
                        warnings.add("关键系统文件缺失: $filePath")
                    }
                } catch (e: SecurityException) {
                    details.add("🔒 $filePath - 权限被拒绝")
                    warnings.add("访问权限被拒绝: $filePath")
                } catch (e: Exception) {
                    details.add("❌ $filePath - 检查异常: ${e.message}")
                    warnings.add("文件检查异常: $filePath - ${e.message}")
                }
            }
            
            // 检查系统库文件的架构一致性
            val lib32Exists = File("/system/lib/libc.so").exists()
            val lib64Exists = File("/system/lib64/libc.so").exists()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val supportedAbis = Build.SUPPORTED_ABIS
                val has64BitAbi = supportedAbis.any { it.contains("64") }
                
                if (has64BitAbi && !lib64Exists) {
                    warnings.add("64位设备缺少64位系统库")
                    details.add("⚠️ 64位架构但缺少lib64目录")
                } else if (!has64BitAbi && lib64Exists) {
                    details.add("ℹ️ 32位设备但存在lib64目录")
                }
                
                details.add("📱 支持的ABI: ${supportedAbis.joinToString(", ")}")
            }
            
            // 检查系统版本一致性
            try {
                val buildProp = File("/system/build.prop")
                if (buildProp.exists() && buildProp.canRead()) {
                    val buildPropContent = buildProp.readText()
                    val versionRelease = extractBuildPropValue(buildPropContent, "ro.build.version.release")
                    val versionSdk = extractBuildPropValue(buildPropContent, "ro.build.version.sdk")
                    
                    if (versionRelease.isNotEmpty() && versionRelease != Build.VERSION.RELEASE) {
                        warnings.add("系统版本不一致: build.prop($versionRelease) vs Runtime(${Build.VERSION.RELEASE})")
                        details.add("⚠️ 版本不一致: $versionRelease vs ${Build.VERSION.RELEASE}")
                    } else if (versionRelease.isNotEmpty()) {
                        details.add("✅ 系统版本一致: $versionRelease")
                    }
                    
                    if (versionSdk.isNotEmpty() && versionSdk.toIntOrNull() != Build.VERSION.SDK_INT) {
                        warnings.add("SDK版本不一致: build.prop($versionSdk) vs Runtime(${Build.VERSION.SDK_INT})")
                        details.add("⚠️ SDK不一致: $versionSdk vs ${Build.VERSION.SDK_INT}")
                    } else if (versionSdk.isNotEmpty()) {
                        details.add("✅ SDK版本一致: $versionSdk")
                    }
                } else {
                    warnings.add("无法读取build.prop文件")
                    details.add("❌ build.prop - 不可访问")
                }
            } catch (e: Exception) {
                details.add("⚠️ 版本一致性检查异常: ${e.message}")
            }
            
            // 如果关键文件缺失过多，认为检测失败
            if (integrityFileCount < INTEGRITY_CHECK_FILES.size * 0.5) {
                passed = false
                warnings.add("关键系统文件缺失过多 ($integrityFileCount/${INTEGRITY_CHECK_FILES.size})")
            }
            
            details.add("📊 系统完整性文件统计: $integrityFileCount/${INTEGRITY_CHECK_FILES.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "检查系统完整性文件时出错", e)
            warnings.add("系统完整性文件检查异常: ${e.message}")
            details.add("❌ 检查过程异常: ${e.message}")
            passed = false
        }
        
        return IntegrityCheckResult(passed, warnings, details)
    }
    
    /**
     * 检查系统目录权限（普通应用权限）
     */
    private fun checkSystemDirectoryPermissions(): IntegrityCheckResult {
        val warnings = mutableListOf<String>()
        val details = mutableListOf<String>()
        var passed = true
        var accessibleDirs = 0
        
        try {
            for (dirPath in ACCESSIBLE_SYSTEM_DIRS) {
                try {
                    val dir = File(dirPath)
                    when {
                        dir.exists() && dir.isDirectory && dir.canRead() -> {
                            accessibleDirs++
                            val fileCount = try {
                                dir.listFiles()?.size ?: 0
                            } catch (e: SecurityException) {
                                -1
                            }
                            
                            if (fileCount >= 0) {
                                details.add("✅ $dirPath - 可访问 ($fileCount 个文件)")
                            } else {
                                details.add("⚠️ $dirPath - 可访问但无法列举内容")
                            }
                            
                            // 检查写权限（不应该有）
                            if (dir.canWrite()) {
                                warnings.add("系统目录具有写权限: $dirPath")
                                details.add("🚨 $dirPath - 异常的写权限")
                                passed = false
                            }
                        }
                        dir.exists() && dir.isDirectory && !dir.canRead() -> {
                            details.add("🔒 $dirPath - 存在但不可读")
                            warnings.add("系统目录权限异常: $dirPath")
                        }
                        dir.exists() && !dir.isDirectory -> {
                            details.add("❌ $dirPath - 不是目录")
                            warnings.add("系统路径不是目录: $dirPath")
                            passed = false
                        }
                        else -> {
                            details.add("❌ $dirPath - 不存在")
                            warnings.add("系统目录不存在: $dirPath")
                        }
                    }
                } catch (e: SecurityException) {
                    details.add("🔒 $dirPath - 权限被拒绝")
                    warnings.add("访问权限被拒绝: $dirPath")
                } catch (e: Exception) {
                    details.add("❌ $dirPath - 检查异常: ${e.message}")
                    warnings.add("目录检查异常: $dirPath - ${e.message}")
                }
            }
            
            details.add("📊 系统目录可访问性统计: $accessibleDirs/${ACCESSIBLE_SYSTEM_DIRS.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "检查系统目录权限时出错", e)
            warnings.add("系统目录检查异常: ${e.message}")
            details.add("❌ 检查过程异常: ${e.message}")
            passed = false
        }
        
        return IntegrityCheckResult(passed, warnings, details)
    }
    
    /**
     * 检查进程和挂载点（普通应用权限）
     */
    private fun checkProcessAndMounts(): IntegrityCheckResult {
        val warnings = mutableListOf<String>()
        val details = mutableListOf<String>()
        var passed = true
        
        try {
            // 检查挂载信息
            val mountsFile = File("/proc/mounts")
            if (mountsFile.exists() && mountsFile.canRead()) {
                val mounts = mountsFile.readText()
                details.add("✅ /proc/mounts - 可读取")
                
                // 分析关键挂载点
                 val lines = mounts.split("\n")
                 var systemMountFound = false
                 var dataMountFound = false
                 var vendorMountFound = false
                 var suspiciousMounts = 0
                 
                 for (line in lines) {
                     val parts = line.split(" ")
                     if (parts.size >= 4) {
                         val device = parts[0]
                         val mountPoint = parts[1]
                         val fsType = parts[2]
                         val options = parts[3]
                         
                         when {
                             mountPoint == "/system" -> {
                                 systemMountFound = true
                                 if (options.contains("ro")) {
                                     details.add("✅ /system - 只读挂载 ($fsType)")
                                 } else if (options.contains("rw")) {
                                     warnings.add("系统分区以读写模式挂载")
                                     details.add("🚨 /system - 读写挂载 ($fsType, 异常)")
                                     passed = false
                                 }
                                 
                                 // 检查文件系统类型
                                 if (fsType == "tmpfs") {
                                     warnings.add("系统分区使用临时文件系统")
                                     details.add("⚠️ /system - tmpfs文件系统 (可疑)")
                                     suspiciousMounts++
                                 }
                             }
                             mountPoint == "/data" -> {
                                 dataMountFound = true
                                 details.add("✅ /data - 挂载点存在 ($fsType)")
                             }
                             mountPoint == "/vendor" -> {
                                 vendorMountFound = true
                                 details.add("✅ /vendor - 挂载点存在 ($fsType)")
                             }
                             fsType == "tmpfs" && mountPoint.startsWith("/dev") -> {
                                 details.add("ℹ️ tmpfs挂载: $mountPoint")
                             }
                             device.contains("loop") && !mountPoint.startsWith("/dev") -> {
                                 details.add("⚠️ 循环设备挂载: $mountPoint ($device)")
                                 suspiciousMounts++
                             }
                         }
                     }
                 }
                
                if (!systemMountFound) {
                     warnings.add("未找到/system挂载点")
                     details.add("❌ /system挂载点未找到")
                     passed = false
                 }
                 
                 if (!dataMountFound) {
                     warnings.add("未找到/data挂载点")
                     details.add("❌ /data挂载点未找到")
                 }
                 
                 if (suspiciousMounts > 2) {
                     warnings.add("发现过多可疑挂载点 (${suspiciousMounts}个)")
                     details.add("⚠️ 可疑挂载点数量: $suspiciousMounts")
                 }
                 
                 details.add("📊 挂载点统计: System($systemMountFound), Data($dataMountFound), Vendor($vendorMountFound)")
                
            } else {
                warnings.add("无法读取挂载信息")
                details.add("❌ /proc/mounts - 不可访问")
                passed = false
            }
            
            // 检查进程信息
            val statFile = File("/proc/stat")
            if (statFile.exists() && statFile.canRead()) {
                details.add("✅ /proc/stat - 可读取")
            } else {
                details.add("❌ /proc/stat - 不可访问")
                warnings.add("无法读取进程统计信息")
            }
            
            // 检查内存信息
            val meminfoFile = File("/proc/meminfo")
            if (meminfoFile.exists() && meminfoFile.canRead()) {
                val meminfo = meminfoFile.readText()
                val totalMemMatch = Regex("MemTotal:\\s+(\\d+)\\s+kB").find(meminfo)
                if (totalMemMatch != null) {
                    val totalMemKB = totalMemMatch.groupValues[1].toLong()
                    val totalMemMB = totalMemKB / 1024
                    details.add("✅ 系统内存: ${totalMemMB}MB")
                } else {
                    details.add("⚠️ 无法解析内存信息")
                }
            } else {
                details.add("❌ /proc/meminfo - 不可访问")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "检查进程和挂载点时出错", e)
            warnings.add("进程和挂载点检查异常: ${e.message}")
            details.add("❌ 检查过程异常: ${e.message}")
            passed = false
        }
        
        return IntegrityCheckResult(passed, warnings, details)
    }
    
    /**
     * 获取系统属性
     */
    private fun getSystemProperty(key: String): String {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine() ?: ""
            process.waitFor()
            reader.close()
            result
        } catch (e: Exception) {
            Log.w(TAG, "获取系统属性失败: $key", e)
            ""
        }
    }
    
    /**
     * 从build.prop内容中提取指定属性值
     */
    private fun extractBuildPropValue(content: String, key: String): String {
        return try {
            val lines = content.split("\n")
            for (line in lines) {
                if (line.startsWith("$key=")) {
                    return line.substringAfter("=").trim()
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 完整性检查结果数据类
     */
    private data class IntegrityCheckResult(
        val passed: Boolean,
        val warnings: List<String>,
        val details: List<String> = emptyList()
    )
}