package me.nukrs.root.envcheck.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import me.nukrs.root.envcheck.model.CheckStatus
import me.nukrs.root.envcheck.model.CheckDetails
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import me.nukrs.root.envcheck.service.AndroidSELinuxChecker
import me.nukrs.root.envcheck.service.AndroidBootloaderChecker
import me.nukrs.root.envcheck.service.AndroidTeeChecker
import me.nukrs.root.envcheck.service.AndroidSystemIntegrityChecker
import me.nukrs.root.envcheck.service.AndroidNetworkSecurityChecker


class CheckService(private val context: Context) {
    
    

    /**
     * TEE安全检测
     * 验证TEE可用性、硬件安全模块和密钥认证存储
     */
    suspend fun performTeeCheck(): Flow<Pair<CheckStatus, CheckDetails?>> {
        val teeChecker = AndroidTeeChecker(context)
        return teeChecker.performTeeCheck()
    }
    
    /**
     * Bootloader安全检测
     * 验证Bootloader锁定状态和系统完整性
     */
    suspend fun performBootloaderCheck(): Flow<Pair<CheckStatus, CheckDetails?>> {
        val bootloaderChecker = AndroidBootloaderChecker(context)
        return bootloaderChecker.performBootloaderCheck()
    }
    

    
    /**
     * SELinux检测
     * 检测SELinux状态和策略完整性
     */
    /**
     * 危险应用检测
     * 检测系统中是否存在破解工具、Hook框架等危险应用
     */
    suspend fun performDangerousAppsCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1200)
            
            val securityDetector = AndroidSecurityDetector()
            val securityResult = securityDetector.performSecurityCheck()
            
            val checkNames = listOf("危险应用扫描", "Hook框架检测", "破解工具检测")
            val results = mutableListOf<Boolean>()
            
            // 主要检测：是否存在危险应用
            val noDangerousApps = !securityResult.isDangerous
            results.add(noDangerousApps)
            
            // 补充检测：传统Root应用检测
            val noTraditionalRootApps = !checkTraditionalRootApps()
            results.add(noTraditionalRootApps)
            
            // 系统完整性检测
            val systemIntegrity = checkBasicSystemIntegrity()
            results.add(systemIntegrity)
            
            val passedChecks = results.count { it }
            val totalChecks = results.size
            
            // 构建详细的危险应用列表
            val detectedAppsList = if (securityResult.isDangerous) {
                securityResult.detectedApps.map { packageName ->
                    // 将包名转换为更友好的显示格式
                    when {
                        packageName.contains("magisk") -> "$packageName (Magisk工具)"
                        packageName.contains("xposed") || packageName.contains("lsposed") -> "$packageName (Hook框架)"
                        packageName.contains("hook") || packageName.contains("vip") -> "$packageName (破解工具)"
                        packageName.contains("termux") || packageName.contains("adb") -> "$packageName (系统工具)"
                        packageName.contains("fake") || packageName.contains("emulator") -> "$packageName (隐私绕过)"
                        else -> packageName
                    }
                }
            } else emptyList()
            
            val details = CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> results.getOrNull(index) == true },
                failedChecks = checkNames.filterIndexed { index, _ -> results.getOrNull(index) == false } + detectedAppsList,
                warningChecks = if (detectedAppsList.isNotEmpty()) listOf("检测到${securityResult.detectedAppsCount}个危险应用") else emptyList(),
                score = "${(passedChecks.toFloat() / totalChecks * 100).toInt()}%",
                recommendation = when {
                    passedChecks == totalChecks -> "未检测到危险应用，系统环境安全。\n\n感谢 lshwjgpt (https://github.com/lshwjgpt25) 提供的危险应用检测方法。"
                    securityResult.isDangerous -> "检测到${securityResult.detectedAppsCount}个危险应用，建议卸载相关应用以确保安全。\n\n感谢 lshwjgpt (https://github.com/lshwjgpt25) 提供的危险应用检测方法。"
                    else -> "部分安全检测未通过，建议进一步检查系统环境。\n\n感谢 lshwjgpt (https://github.com/lshwjgpt25) 提供的危险应用检测方法。"
                }
            )
            
            when {
                passedChecks == totalChecks -> emit(Pair(CheckStatus.PASSED, details))
                securityResult.isDangerous -> emit(Pair(CheckStatus.FAILED, details))
                else -> emit(Pair(CheckStatus.WARNING, details))
            }
            
        } catch (e: Exception) {
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("危险应用检测异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "危险应用检测过程中发生异常，请检查系统兼容性"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    suspend fun performSelinuxCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1000)
            
            val selinuxChecker = AndroidSELinuxChecker(context)
            val checkResult = selinuxChecker.runComprehensiveCheck()
            
            val selinuxResults = mutableListOf<Boolean>()
            val checkNames = mutableListOf<String>()
            val warningChecks = mutableListOf<String>()
            
            // SELinux启用状态检查
            selinuxResults.add(checkResult.systemInfo.enabled)
            checkNames.add("SELinux启用状态")
            
            // SELinux强制模式检查
            selinuxResults.add(checkResult.systemInfo.enforcing)
            checkNames.add("SELinux强制模式")
            
            // 策略版本检查
            val hasPolicyVersion = checkResult.systemInfo.policyVersion != "Unknown"
            selinuxResults.add(hasPolicyVersion)
            checkNames.add("SELinux策略版本")
            
            // 进程上下文检查
            selinuxResults.add(checkResult.processContext.isValid)
            checkNames.add("进程SELinux上下文")
            
            // 策略文件检查
            selinuxResults.add(checkResult.policyInfo.policyExists)
            checkNames.add("SELinux策略文件")
            
            // 文件上下文检查（如果有文件上下文检查失败，添加到警告）
            val fileContextIssues = checkResult.fileContexts.filter { !it.readable || !it.exists }
            if (fileContextIssues.isNotEmpty()) {
                warningChecks.add("部分文件上下文检查异常: ${fileContextIssues.size}个文件")
            }
            
            val passedChecks = selinuxResults.count { it }
            val totalChecks = selinuxResults.size
            
            // 构建详细的推荐信息
            val recommendationBuilder = StringBuilder()
            if (passedChecks == totalChecks) {
                recommendationBuilder.append("SELinux检测全部通过，系统安全策略完整有效。")
            } else {
                recommendationBuilder.append("SELinux检测发现问题：")
                if (!checkResult.systemInfo.enabled) {
                    recommendationBuilder.append("\n• SELinux未启用，系统安全性降低")
                }
                if (!checkResult.systemInfo.enforcing) {
                    recommendationBuilder.append("\n• SELinux处于宽松模式，建议启用强制模式")
                }
                if (!hasPolicyVersion) {
                    recommendationBuilder.append("\n• 无法获取SELinux策略版本信息")
                }
                if (!checkResult.processContext.isValid) {
                    recommendationBuilder.append("\n• 进程SELinux上下文异常")
                }
                if (!checkResult.policyInfo.policyExists) {
                    recommendationBuilder.append("\n• SELinux策略文件缺失")
                }
            }
            
            // 添加系统信息到推荐中
            recommendationBuilder.append("\n\n系统信息:")
            recommendationBuilder.append("\n• API级别: ${checkResult.systemInfo.apiLevel}")
            recommendationBuilder.append("\n• 安全补丁: ${checkResult.systemInfo.securityPatch}")
            recommendationBuilder.append("\n• 当前上下文: ${checkResult.systemInfo.currentContext}")
            
            if (checkResult.policyInfo.customPolicies.isNotEmpty()) {
                recommendationBuilder.append("\n• 自定义策略: ${checkResult.policyInfo.customPolicies.size}个")
            }
            
            val details = CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> selinuxResults.getOrNull(index) == true },
                failedChecks = checkNames.filterIndexed { index, _ -> selinuxResults.getOrNull(index) == false },
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
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("SELinux检测异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "SELinux检测过程中发生异常，请检查设备兼容性。\n错误详情: ${e.message}"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    /**
     * 系统完整性检测
     * 检测系统文件和权限完整性
     */
    suspend fun performSystemIntegrityCheck(): Flow<Pair<CheckStatus, CheckDetails?>> {
        val systemIntegrityChecker = AndroidSystemIntegrityChecker(context)
        return systemIntegrityChecker.performSystemIntegrityCheck()
    }
    
    /**
     * 网络安全检测
     * 检测网络配置和证书安全
     */
    suspend fun performNetworkSecurityCheck(): Flow<Pair<CheckStatus, CheckDetails?>> {
        val networkSecurityChecker = AndroidNetworkSecurityChecker(context)
        return networkSecurityChecker.performNetworkSecurityCheck()
    }
    

    
    /**
     * 获取检测到的危险应用详细信息
     * @return 危险应用检测结果
     */
    fun getDangerousAppsDetails(): SecurityCheckResult {
        val securityDetector = AndroidSecurityDetector()
        return securityDetector.performSecurityCheck()
    }
    
    /**
     * 传统Root应用检测（通过包管理器）
     */
    private fun checkTraditionalRootApps(): Boolean {
        val traditionalRootApps = arrayOf(
            "com.topjohnwu.magisk",
            "com.sukisu.ultra",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su"
        )
        
        return traditionalRootApps.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
    
    /**
     * 基础系统完整性检测
     */
    private fun checkBasicSystemIntegrity(): Boolean {
        return try {
            // 检查关键系统文件是否存在且不可写
            val criticalFiles = arrayOf(
                "/system/build.prop",
                "/system/framework/framework.jar",
                "/system/lib/libc.so"
            )
            
            val filesIntact = criticalFiles.all { path ->
                val file = File(path)
                file.exists() && file.canRead() && !file.canWrite()
            }
            
            // 检查系统分区是否为只读
            val systemDir = File("/system")
            val systemReadOnly = systemDir.exists() && systemDir.canRead() && !systemDir.canWrite()
            
            filesIntact && systemReadOnly
        } catch (e: Exception) {
            false
        }
    }

    // 执行命令（保留给其他检测使用）
    private fun executeCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText().trim()
            reader.close()

            // 等待命令执行完成
            val exitCode = process.waitFor()
            if (exitCode == 0 && result.isNotEmpty()) {
                result
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    


}