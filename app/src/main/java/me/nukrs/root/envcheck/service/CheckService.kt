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
     * PM包名检测
     * 检测可疑应用包名和模拟器环境
     */
    suspend fun performPmPackageCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1200)
            
            val pmResults = mutableListOf<Boolean>()
            
            // 检测可疑包名
            pmResults.add(!checkSuspiciousPackages())
            
            // 检测模拟器环境
            pmResults.add(!checkEmulatorEnvironment())
            
            // 检测Root应用（重复检查以加强检测）
            pmResults.add(!checkRootApps())
            
            val passedChecks = pmResults.count { it }
            val totalChecks = pmResults.size
            val checkNames = listOf("可疑包名检测", "模拟器环境检测", "Root应用检测")
            
            val details = CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> pmResults.getOrNull(index) == true },
                failedChecks = checkNames.filterIndexed { index, _ -> pmResults.getOrNull(index) == false },
                warningChecks = emptyList(),
                score = "${(passedChecks.toFloat() / totalChecks * 100).toInt()}%",
                recommendation = when {
                    passedChecks == totalChecks -> "PM包名检测全部通过，未发现可疑应用或模拟器环境。"
                    passedChecks >= totalChecks * 0.7 -> "大部分检测通过，建议检查并卸载可疑应用。"
                    else -> "检测到多个安全风险，强烈建议清理设备环境。"
                }
            )
            
            when {
                passedChecks == totalChecks -> emit(Pair(CheckStatus.PASSED, details))
                passedChecks >= totalChecks * 0.7 -> emit(Pair(CheckStatus.WARNING, details))
                else -> emit(Pair(CheckStatus.FAILED, details))
            }
            
        } catch (e: Exception) {
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("PM包名检测异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "PM包名检测过程中发生异常，请检查设备兼容性"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    /**
     * SELinux检测
     * 检测SELinux状态和策略完整性
     */
    suspend fun performSelinuxCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1000)
            
            val selinuxResults = mutableListOf<Boolean>()
            
            // SELinux状态检查
            selinuxResults.add(checkSelinuxStatus())
            
            // SELinux策略检查
            selinuxResults.add(checkSelinuxPolicy())
            
            val passedChecks = selinuxResults.count { it }
            val totalChecks = selinuxResults.size
            val checkNames = listOf("SELinux状态检查", "SELinux策略检查")
            
            val details = CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> selinuxResults.getOrNull(index) == true },
                failedChecks = checkNames.filterIndexed { index, _ -> selinuxResults.getOrNull(index) == false },
                warningChecks = emptyList(),
                score = "${(passedChecks.toFloat() / totalChecks * 100).toInt()}%",
                recommendation = when {
                    passedChecks == totalChecks -> "SELinux检测全部通过，系统安全策略完整有效。"
                    passedChecks > 0 -> "部分SELinux检测通过，建议检查系统安全策略配置。"
                    else -> "SELinux检测失败，系统安全策略可能被篡改或禁用。"
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
                failedChecks = listOf("SELinux检测异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "SELinux检测过程中发生异常，请检查设备兼容性"
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
        val rootApps = arrayOf(
            //magisk
            "com.topjohnwu.magisk",
            //Sukisu Ultra
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
    
    // PM包名检测方法
    private fun checkSuspiciousPackages(): Boolean {
        val suspiciousPackages = arrayOf(
            // Root管理应用
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "com.koushikdutta.superuser",
            "eu.chainfire.supersu",
            "me.phh.superuser",
            
            // Xposed框架
            "de.robv.android.xposed.installer",
            "org.meowcat.edxposed.manager",
            "org.lsposed.manager",
            "top.canyie.dreamland.manager",
            "me.weishu.exp",
            
            // 模拟器检测
            "com.android.vendinf",
            "com.bluestacks",
            "com.bignox.app",
            "com.microvirt.guide",
            "com.mumu.launcher",
            
            // 调试和Hook工具
            "com.android.development",
            "com.android.development_settings",
            "jackpal.androidterm",
            "com.aide.ui",
            "com.termux",
            
            // 存储重定向
            "moe.shizuku.redirectstorage",
            "rikka.appops",
            "com.catchingnow.icebox"
        )
        
        return suspiciousPackages.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
    
    private fun checkEmulatorEnvironment(): Boolean {
        val emulatorIndicators = mutableListOf<Boolean>()
        
        // 检查设备型号
        val deviceModel = Build.MODEL.lowercase()
        val deviceManufacturer = Build.MANUFACTURER.lowercase()
        val deviceProduct = Build.PRODUCT.lowercase()
        
        emulatorIndicators.add(
            deviceModel.contains("emulator") ||
            deviceModel.contains("simulator") ||
            deviceManufacturer.contains("genymotion") ||
            deviceProduct.contains("sdk") ||
            deviceProduct.contains("emulator")
        )
        
        // 检查CPU架构异常
        val cpuAbi = Build.SUPPORTED_ABIS.joinToString(",")
        emulatorIndicators.add(
            cpuAbi.contains("x86") && !cpuAbi.contains("arm")
        )
        
        // 检查传感器数量（模拟器通常传感器较少）
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensorCount = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL).size
        emulatorIndicators.add(sensorCount < 10)
        
        return emulatorIndicators.any { it }
    }

    // SELinux检测方法
    private fun checkSelinuxStatus(): Boolean {
        return try {
            //通过getenforce命令检测（最准确）
            val getenforceResult = executeCommand("getenforce")
            if (getenforceResult != null) {
                val isEnforcingByCommand = getenforceResult.equals("Enforcing", true)
                if (isEnforcingByCommand) return true
            }

            //原有的系统属性和文件检测
            val selinuxStatus = System.getProperty("ro.boot.selinux")
            val selinuxEnforce = File("/sys/fs/selinux/enforce")

            var isEnforcing = false
            if (selinuxEnforce.exists() && selinuxEnforce.canRead()) {
                val enforceContent = selinuxEnforce.readText().trim()
                isEnforcing = enforceContent == "1"
            } else {
                // 如果无法读取enforce文件，尝试通过命令获取
                val enforceByCommand = executeCommand("cat /sys/fs/selinux/enforce")
                if (enforceByCommand != null) {
                    isEnforcing = enforceByCommand.trim() == "1"
                }
            }

            // 检查SELinux策略
            val policyFile = File("/sepolicy")
            val hasPolicyFile = policyFile.exists()

            // 如果系统属性检测失败，尝试其他方法
            val selinuxEnabled = if (selinuxStatus != null) {
                selinuxStatus != "disabled"
            } else {
                // 备用检测方法
                checkSelinuxAlternative()
            }

            selinuxEnabled && isEnforcing && hasPolicyFile
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSelinuxPolicy(): Boolean {
        return try {
            //原有的ls -Z检测
            val process = Runtime.getRuntime().exec("ls -Z /system")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()

            // 正常的SELinux上下文应该包含类型信息
            val hasValidContext = output.contains("u:object_r:") && output.contains(":s0")

            if (hasValidContext) {
                return true
            }

            //检查当前进程的SELinux上下文
            val currentContext = executeCommand("cat /proc/self/attr/current")
            if (currentContext != null && currentContext.contains(":")) {
                return true
            }

            //检查SELinux文件系统是否挂载
            val mountOutput = executeCommand("mount | grep selinuxfs")
            if (mountOutput != null && mountOutput.contains("selinuxfs")) {
                return true
            }

            //备用检测
            File("/sys/fs/selinux/policy").exists()
        } catch (e: Exception) {
            // 如果无法执行命令，检查基本的SELinux文件
            File("/sys/fs/selinux/policy").exists()
        }
    }

    // 执行命令
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

    // 备用SELinux检测方法
    private fun checkSelinuxAlternative(): Boolean {
        return try {
            // 检查SELinux相关的系统属性
            val selinuxProps = listOf(
                "ro.boot.selinux",
                "ro.build.selinux",
                "selinux.reload_policy"
            )

            for (prop in selinuxProps) {
                val value = System.getProperty(prop)
                if (value != null && value != "disabled" && value != "0") {
                    return true
                }
            }

            // 检查SELinux文件系统是否存在
            val selinuxFiles = listOf(
                "/sys/fs/selinux",
                "/selinux",
                "/sys/fs/selinux/class",
                "/sys/fs/selinux/policy"
            )

            for (file in selinuxFiles) {
                if (File(file).exists()) {
                    return true
                }
            }

            false
        } catch (e: Exception) {
            false
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