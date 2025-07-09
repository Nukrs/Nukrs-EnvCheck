package me.nukrs.root.envcheck.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import me.nukrs.root.envcheck.model.CheckStatus
import me.nukrs.root.envcheck.model.CheckDetails
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import me.nukrs.root.envcheck.service.AndroidSELinuxChecker


class CheckService(private val context: Context) {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeystore"
        private const val TEE_KEY_ALIAS = "tee_verification_key"
        private const val MIN_SECURITY_PATCH_LEVEL = "2023-01-01"
    }

    suspend fun performTeeCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(2000)
            
            val teeResults = mutableListOf<Boolean>()
            
            //基础TEE可用性检查
            teeResults.add(checkBasicTeeAvailability())
            
            //硬件安全模块验证
            teeResults.add(checkHardwareSecurityModule())
            
            //密钥认证存储验证
            teeResults.add(checkKeystoreAttestation())
            
            val checkNames = mutableListOf("TEE可用性检查", "硬件安全模块", "密钥认证存储")
            
            // StrongBox安全芯片检查（作为加分项）
            var hasStrongBox = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                hasStrongBox = checkStrongBoxAvailability()
                checkNames.add("StrongBox安全芯片")
                teeResults.add(hasStrongBox)
            }
            
            //生物识别安全等级验证
            teeResults.add(checkBiometricSecurityLevel())
            checkNames.add("生物识别安全")
            
            //标准：所有检查必须通过
            val passedChecks = teeResults.count { it }
            val totalChecks = teeResults.size
             val details = CheckDetails(
                 passedChecks = checkNames.filterIndexed { index, _ -> teeResults.getOrNull(index) == true },
                 failedChecks = checkNames.filterIndexed { index, _ -> teeResults.getOrNull(index) == false },
                 warningChecks = emptyList(),
                 score = "${(passedChecks.toFloat() / totalChecks * 100).toInt()}%",
                 recommendation = when {
                     passedChecks == totalChecks && hasStrongBox -> "设备TEE安全检查全部通过，并配备StrongBox芯片，达到最高安全标准。"
                     passedChecks == totalChecks -> "设备TEE安全检查全部通过，具备金融级安全能力。"
                     else -> "部分TEE安全检查未通过，建议使用支持完整TEE功能的设备。"
                 }
             )
            
            when {
                passedChecks == totalChecks -> emit(Pair(CheckStatus.PASSED, details))
                passedChecks >= totalChecks * 0.8 -> emit(Pair(CheckStatus.WARNING, details))
                else -> emit(Pair(CheckStatus.FAILED, details))
            }
            
        } catch (e: Exception) {
            val details = CheckDetails(
                 passedChecks = emptyList(),
                 failedChecks = listOf("TEE检查异常: ${e.message}"),
                 warningChecks = emptyList(),
                 score = "0%",
                 recommendation = "TEE检查过程中发生异常，请检查设备兼容性"
             )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    /**
     * 安全检测
     * 验证完整性
     */
    suspend fun performBootloaderCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1500)
            
            val bootloaderResults = mutableListOf<Boolean>()
            
            //Bootloader锁定状态检查
            bootloaderResults.add(checkBootloaderLockStatus())
            
            //系统完整性验证
            bootloaderResults.add(checkSystemIntegrity())
            
            //验证启动验证（Verified Boot）
            bootloaderResults.add(checkVerifiedBootStatus())
            
            //安全补丁级别验证
            bootloaderResults.add(checkSecurityPatchLevel())
            
            //反调试和反篡改检测
            bootloaderResults.add(checkAntiTamperProtection())
            
            //Root检测
            bootloaderResults.add(checkAdvancedRootDetection())
            
            //必须全部通过
            val criticalChecks = bootloaderResults.take(4) // 前4项为关键检查
            val allCriticalPassed = criticalChecks.all { it }
            val totalPassed = bootloaderResults.count { it }
            
            val checkNames = listOf("Bootloader锁定", "系统完整性", "验证启动", "安全补丁级别", "反篡改保护", "Root检测")
             val details = CheckDetails(
                 passedChecks = checkNames.filterIndexed { index, _ -> bootloaderResults.getOrNull(index) == true },
                 failedChecks = checkNames.filterIndexed { index, _ -> bootloaderResults.getOrNull(index) == false },
                 warningChecks = emptyList(),
                 score = "${(totalPassed.toFloat() / bootloaderResults.size * 100).toInt()}%",
                 recommendation = when {
                     allCriticalPassed && totalPassed >= 5 -> "Bootloader安全检查全部通过，设备具备金融级安全能力"
                     allCriticalPassed -> "关键安全检查通过，建议加强其他安全措施"
                     else -> "关键安全检查未通过，设备存在安全风险"
                 }
             )
            
            when {
                allCriticalPassed && totalPassed >= 5 -> emit(Pair(CheckStatus.PASSED, details))
                allCriticalPassed && totalPassed >= 3 -> emit(Pair(CheckStatus.WARNING, details))
                else -> emit(Pair(CheckStatus.FAILED, details))
            }
            
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
    suspend fun performSystemIntegrityCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1300)
            
            val integrityResults = mutableListOf<Boolean>()
            
            // 系统文件完整性检查
            integrityResults.add(checkSystemFileIntegrity())
            
            // 系统权限检查
            integrityResults.add(checkSystemPermissions())
            
            val passedChecks = integrityResults.count { it }
            val totalChecks = integrityResults.size
            val checkNames = listOf("系统文件完整性", "系统权限检查")
            
            val details = CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> integrityResults.getOrNull(index) == true },
                failedChecks = checkNames.filterIndexed { index, _ -> integrityResults.getOrNull(index) == false },
                warningChecks = emptyList(),
                score = "${(passedChecks.toFloat() / totalChecks * 100).toInt()}%",
                recommendation = when {
                    passedChecks == totalChecks -> "系统完整性检测全部通过，系统文件和权限正常。"
                    passedChecks > 0 -> "部分完整性检测通过，建议检查系统文件权限。"
                    else -> "系统完整性检测失败，系统可能被篡改或损坏。"
                }
            )
            
            when {
                passedChecks == totalChecks -> emit(Pair(CheckStatus.PASSED, details))
                passedChecks > 0 -> emit(Pair(CheckStatus.WARNING, details))
                else -> emit(Pair(CheckStatus.FAILED, details))
            }
            
        } catch (e: Exception) {
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("系统完整性检测异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "系统完整性检测过程中发生异常，请检查设备兼容性"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    /**
     * 网络安全检测
     * 检测网络配置和证书安全
     */
    suspend fun performNetworkSecurityCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1100)
            
            val networkResults = mutableListOf<Boolean>()
            
            // 网络配置检查
            networkResults.add(checkNetworkConfiguration())
            
            // 证书固定检查
            networkResults.add(checkCertificatePinning())
            
            val passedChecks = networkResults.count { it }
            val totalChecks = networkResults.size
            val checkNames = listOf("网络配置检查", "证书安全检查")
            
            val details = CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> networkResults.getOrNull(index) == true },
                failedChecks = checkNames.filterIndexed { index, _ -> networkResults.getOrNull(index) == false },
                warningChecks = emptyList(),
                score = "${(passedChecks.toFloat() / totalChecks * 100).toInt()}%",
                recommendation = when {
                    passedChecks == totalChecks -> "网络安全检测全部通过，网络配置和证书安全。"
                    passedChecks > 0 -> "部分网络安全检测通过，建议检查网络配置。"
                    else -> "网络安全检测失败，可能存在网络安全风险。"
                }
            )
            
            when {
                passedChecks == totalChecks -> emit(Pair(CheckStatus.PASSED, details))
                passedChecks > 0 -> emit(Pair(CheckStatus.WARNING, details))
                else -> emit(Pair(CheckStatus.FAILED, details))
            }
            
        } catch (e: Exception) {
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("网络安全检测异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "网络安全检测过程中发生异常，请检查设备兼容性"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    //TEE检测方法
    
    private fun checkBasicTeeAvailability(): Boolean {
        return try {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
            context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkHardwareSecurityModule(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            // 检查是否支持硬件支持的密钥生成
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                TEE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            
            // 验证密钥是否在硬件中生成
            val secretKey = keyStore.getKey(TEE_KEY_ALIAS, null) as? SecretKey
            secretKey != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkKeystoreAttestation(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 检查密钥认证功能
                context.packageManager.hasSystemFeature("android.hardware.keystore.app_attest_key")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkStrongBoxAvailability(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkBiometricSecurityLevel(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 检查生物识别安全等级
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE) &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)
            } else {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    //Bootloader检测方法
    
    private fun checkBootloaderLockStatus(): Boolean {
        return try {
            val bootloaderStatus = Build.BOOTLOADER
            val tags = Build.TAGS
            val fingerprint = Build.FINGERPRINT
            
            //验证Bootloader状态
            !bootloaderStatus.contains("unlocked", ignoreCase = true) &&
            !tags.contains("test-keys", ignoreCase = true) &&
            !fingerprint.contains("test-keys", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkSystemIntegrity(): Boolean {
        return try {
            // 检查系统分区完整性
            val systemPaths = arrayOf(
                "/system/recovery-from-boot.p",
                "/system/etc/security/cacerts",
                "/system/framework"
            )
            
            systemPaths.all { path ->
                val file = File(path)
                file.exists() && file.canRead()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkVerifiedBootStatus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 检查dm-verity状态
                val verity = System.getProperty("ro.boot.veritymode")
                val verifiedBootState = System.getProperty("ro.boot.verifiedbootstate")
                
                verity == "enforcing" && verifiedBootState == "green"
            } else {
                true // 旧版本默认通过
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkSecurityPatchLevel(): Boolean {
        return try {
            val securityPatch = Build.VERSION.SECURITY_PATCH
            securityPatch >= MIN_SECURITY_PATCH_LEVEL
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkAntiTamperProtection(): Boolean {
        return try {
            // 检查调试状态
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val isAdbEnabled = android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.ADB_ENABLED,
                0
            ) == 1
            
            // 检查开发者选项
            val isDeveloperOptionsEnabled = android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
            
            !isDebuggable && !isAdbEnabled && !isDeveloperOptionsEnabled
        } catch (e: Exception) {
            true // 无法检测时默认通过
        }
    }
    
    private fun checkAdvancedRootDetection(): Boolean {
        return try {
            //Root检测
            val rootIndicators = mutableListOf<Boolean>()
            
            //检查常见Root文件
            rootIndicators.add(checkRootFiles())
            
            //检查Root应用
            rootIndicators.add(checkRootApps())
            
            //检查系统属性
            rootIndicators.add(checkRootProperties())
            
            //检查可执行文件
            rootIndicators.add(checkRootBinaries())
            
            //检查Magisk隐藏
            rootIndicators.add(checkMagiskHide())
            
            //所有检查都必须通过
            rootIndicators.all { !it }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkRootFiles(): Boolean {
        val rootFiles = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/system/etc/init.d/99SuperSUDaemon",
            "/dev/com.koushikdutta.superuser.daemon/",
            "/system/xbin/daemonsu"
        )
        
        return rootFiles.any { File(it).exists() }
    }
    
    private fun checkRootApps(): Boolean {
        // 使用增强的安全检测器
        val securityDetector = AndroidSecurityDetector()
        val securityResult = securityDetector.performSecurityCheck()
        
        // 如果检测到危险应用，返回true表示存在风险
        if (securityResult.isDangerous) {
            return true
        }
        
        // 保留原有的包管理器检测作为备用
        val rootApps = arrayOf(
            "com.topjohnwu.magisk",
            "com.sukisu.ultra"
        )
        
        return rootApps.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
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
    
    private fun checkRootProperties(): Boolean {
        val dangerousProps = mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0",
            "service.adb.root" to "1"
        )
        
        return dangerousProps.any { (prop, value) ->
            System.getProperty(prop) == value
        }
    }
    
    private fun checkRootBinaries(): Boolean {
        val paths = System.getenv("PATH")?.split(":") ?: return false
        
        return paths.any { path ->
            File(path, "su").exists() || File(path, "busybox").exists()
        }
    }
    
    private fun checkMagiskHide(): Boolean {
        return try {
            // 检查Magisk特征
            val magiskPaths = arrayOf(
                "/sbin/.magisk",
                "/cache/.disable_magisk",
                "/dev/.magisk.unblock",
                "/cache/magisk.log",
                "/data/adb/magisk",
                "/sbin/.core/mirror",
                "/sbin/.core/img"
            )
            
            magiskPaths.any { File(it).exists() }
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
    
    // 系统完整性检测方法
    private fun checkSystemFileIntegrity(): Boolean {
        val criticalFiles = arrayOf(
            "/system/build.prop",
            "/system/etc/hosts",
            "/system/framework/framework.jar",
            "/system/lib/libc.so",
            "/system/bin/app_process"
        )
        
        return criticalFiles.all { path ->
            val file = File(path)
            file.exists() && file.canRead() && !file.canWrite()
        }
    }
    
    private fun checkSystemPermissions(): Boolean {
        return try {
            // 检查关键目录权限
            val systemDir = File("/system")
            val dataDir = File("/data")
            
            // /system应该是只读的
            val systemReadOnly = systemDir.exists() && systemDir.canRead() && !systemDir.canWrite()
            
            // /data应该有适当的权限控制
            val dataPermissions = dataDir.exists() && dataDir.canRead()
            
            systemReadOnly && dataPermissions
        } catch (e: Exception) {
            false
        }
    }
    
    // 网络安全检测方法
    private fun checkNetworkConfiguration(): Boolean {
        return try {
            // 检查代理设置
            val proxyHost = System.getProperty("http.proxyHost")
            val proxyPort = System.getProperty("http.proxyPort")
            val hasProxy = !proxyHost.isNullOrEmpty() || !proxyPort.isNullOrEmpty()
            
            // 检查VPN连接
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val hasVpn = networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
            
            // 正常情况下不应该有代理或VPN（在安全检测场景下）
            !hasProxy && !hasVpn
        } catch (e: Exception) {
            true // 无法检测时默认通过
        }
    }
    
    private fun checkCertificatePinning(): Boolean {
        return try {
            // 检查系统证书存储
            val certDir = File("/system/etc/security/cacerts")
            val userCertDir = File("/data/misc/user/0/cacerts-added")
            
            // 检查是否有用户添加的证书（可能的中间人攻击）
            val hasSystemCerts = certDir.exists() && certDir.listFiles()?.isNotEmpty() == true
            val hasUserCerts = userCertDir.exists() && userCertDir.listFiles()?.isNotEmpty() == true
            
            hasSystemCerts && !hasUserCerts
        } catch (e: Exception) {
            true // 无法检测时默认通过
        }
    }
}