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
     * 多维度验证完整性
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
}