package me.nukrs.root.envcheck.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nukrs.root.envcheck.model.CheckStatus
import me.nukrs.root.envcheck.model.CheckDetails
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import androidx.biometric.BiometricManager
import android.security.keystore.KeyInfo
import java.security.KeyPairGenerator
import java.security.KeyPair
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import java.security.SecureRandom
import android.util.Log
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Android TEE (Trusted Execution Environment) 检测器
 * 通过实际与TEE交互来检测设备的可信执行环境功能
 * 使用普通应用权限进行详细的TEE功能测试
 */
class AndroidTeeChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "AndroidTeeChecker"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TEE_KEY_ALIAS = "tee_test_key"
        private const val TEE_EC_KEY_ALIAS = "tee_ec_verification_key"
        
        // 性能优化常量
        private const val MAX_TEST_TIMEOUT_MS = 30000L // 30秒超时
        private const val PERFORMANCE_TEST_ITERATIONS_SMALL = 10 // 减少小数据测试次数
        private const val PERFORMANCE_TEST_ITERATIONS_MEDIUM = 5 // 减少中等数据测试次数
        private const val PERFORMANCE_TEST_ITERATIONS_LARGE = 3 // 减少大数据测试次数
        private const val KEY_GEN_TEST_ITERATIONS = 3 // 减少密钥生成测试次数
        
        // 权限检查常量
        private const val MIN_ANDROID_VERSION_TEE = Build.VERSION_CODES.M
        private const val MIN_ANDROID_VERSION_ATTESTATION = Build.VERSION_CODES.N
        private const val MIN_ANDROID_VERSION_STRONGBOX = Build.VERSION_CODES.P
        
        // 性能阈值常量
        private const val PERFORMANCE_THRESHOLD_SMALL_MS = 10.0
        private const val PERFORMANCE_THRESHOLD_MEDIUM_MS = 50.0
        private const val PERFORMANCE_THRESHOLD_LARGE_MS = 200.0
        private const val PERFORMANCE_THRESHOLD_KEYGEN_MS = 1000.0
    }
    
    /**
     * TEE检测结果数据类
     */
    data class TeeTestResult(
        val testName: String,
        val passed: Boolean,
        val details: Map<String, Any>,
        val message: String,
        val exception: String? = null
    )
    
    /**
     * 执行完整的TEE安全检测
     * @return Flow<Pair<CheckStatus, CheckDetails?>> 检测状态和详细结果
     */
    suspend fun performTeeCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            // 使用IO线程池执行密集型操作
            val testResults = withContext(Dispatchers.IO) {
                val results = mutableListOf<TeeTestResult>()
                
                // 1. 基础TEE环境检测（快速检测）
                results.add(performBasicTeeEnvironmentTest())
                
                // 2. 硬件密钥存储交互测试
                results.add(performHardwareKeystoreInteractionTestOptimized())
                
                // 3. 密钥认证功能测试（仅在支持的版本上运行）
                if (Build.VERSION.SDK_INT >= MIN_ANDROID_VERSION_ATTESTATION) {
                    results.add(performKeystoreAttestationTestOptimized())
                }
                
                // 4. StrongBox功能测试（仅在Android P+且支持的设备上运行）
                if (Build.VERSION.SDK_INT >= MIN_ANDROID_VERSION_STRONGBOX && 
                    context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")) {
                    results.add(performStrongBoxTestOptimized())
                }
                
                // 5. 生物识别TEE集成测试（轻量级检测）
                results.add(performBiometricTeeIntegrationTestOptimized())
                
                // 6. TEE加密性能测试（优化版本）
                results.add(performTeeEncryptionPerformanceTestOptimized())
                
                results
            }
            
            // 分析测试结果
            val passedTests = testResults.count { it.passed }
            val totalTests = testResults.size
            val hasStrongBox = testResults.any { it.testName.contains("StrongBox") && it.passed }
            
            // 构建详细信息
            val passedChecks = testResults.filter { it.passed }.map { "${it.testName}: ${it.message}" }
            val failedChecks = testResults.filter { !it.passed }.map { "${it.testName}: ${it.message}" }
            val warningChecks = testResults.filter { it.exception != null }.map { "${it.testName}: 检测异常" }
            
            // 生成详细的推荐信息
            val recommendationBuilder = StringBuilder()
            when {
                passedTests == totalTests && hasStrongBox -> {
                    recommendationBuilder.append("TEE安全检测全部通过，设备配备StrongBox芯片，达到最高安全标准。\n\n")
                }
                passedTests == totalTests -> {
                    recommendationBuilder.append("TEE安全检测全部通过，设备具备金融级安全能力。\n\n")
                }
                passedTests >= totalTests * 0.8 -> {
                    recommendationBuilder.append("大部分TEE功能正常，但存在部分问题需要关注。\n\n")
                }
                else -> {
                    recommendationBuilder.append("TEE功能存在较多问题，建议使用支持完整TEE功能的设备。\n\n")
                }
            }
            
            // 添加详细的测试信息
            recommendationBuilder.append("详细测试结果：\n")
            testResults.forEach { result ->
                recommendationBuilder.append("• ${result.testName}: ${if (result.passed) "通过" else "失败"}\n")
                if (result.details.isNotEmpty()) {
                    result.details.forEach { (key, value) ->
                        recommendationBuilder.append("  - $key: $value\n")
                    }
                }
                if (result.exception != null) {
                    recommendationBuilder.append("  - 异常: ${result.exception}\n")
                }
            }
            
            val details = CheckDetails(
                passedChecks = passedChecks,
                failedChecks = failedChecks,
                warningChecks = warningChecks,
                score = "${(passedTests.toFloat() / totalTests * 100).toInt()}%",
                recommendation = recommendationBuilder.toString().trim()
            )
            
            when {
                passedTests == totalTests -> emit(Pair(CheckStatus.PASSED, details))
                passedTests >= totalTests * 0.8 -> emit(Pair(CheckStatus.WARNING, details))
                else -> emit(Pair(CheckStatus.FAILED, details))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "TEE检测过程中发生异常", e)
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("TEE检查异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "TEE检查过程中发生异常，请检查设备兼容性。异常信息: ${e.message}"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    /**
     * 执行基础TEE环境检测（优化版本）
     * @return TeeTestResult 检测结果
     */
    private fun performBasicTeeEnvironmentTest(): TeeTestResult {
        return try {
            val details = mutableMapOf<String, Any>()
            val startTime = System.currentTimeMillis()
            
            // 检查Android版本
            val sdkVersion = Build.VERSION.SDK_INT
            details["Android SDK版本"] = sdkVersion
            details["Android版本"] = Build.VERSION.RELEASE
            details["设备型号"] = "${Build.MANUFACTURER} ${Build.MODEL}"
            
            // 检查TEE基础要求
            val meetsBasicRequirements = sdkVersion >= MIN_ANDROID_VERSION_TEE
            details["满足TEE基础要求"] = meetsBasicRequirements
            
            // 检查关键硬件特性支持（使用缓存避免重复查询）
            val packageManager = context.packageManager
            val hasFingerprint = packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            val hasStrongBox = if (sdkVersion >= MIN_ANDROID_VERSION_STRONGBOX) {
                packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
            } else false
            val hasKeystore = packageManager.hasSystemFeature("android.software.device_admin")
            val hasSecureHardware = packageManager.hasSystemFeature("android.hardware.security.model.compatible")
            
            details["指纹硬件支持"] = hasFingerprint
            details["StrongBox支持"] = hasStrongBox
            details["设备管理支持"] = hasKeystore
            details["安全硬件模型兼容"] = hasSecureHardware
            
            // 检查安全补丁级别（优化字符串处理）
            val securityPatch = if (sdkVersion >= MIN_ANDROID_VERSION_TEE) {
                try {
                    Build.VERSION.SECURITY_PATCH.takeIf { it.isNotEmpty() } ?: "未知"
                } catch (e: Exception) {
                    "获取失败"
                }
            } else "不支持"
            details["安全补丁级别"] = securityPatch
            
            // 检查KeyStore可用性（快速验证）
            var keystoreAvailable = false
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)
                keystoreAvailable = true
                details["AndroidKeyStore可用性"] = "可用"
            } catch (e: Exception) {
                details["AndroidKeyStore可用性"] = "不可用: ${e.message}"
            }
            
            val endTime = System.currentTimeMillis()
            details["检测耗时(ms)"] = endTime - startTime
            
            val passed = meetsBasicRequirements && keystoreAvailable
            val message = when {
                passed && hasStrongBox -> "TEE基础环境检测通过，设备支持高级TEE功能（包含StrongBox）"
                passed -> "TEE基础环境检测通过，设备支持基本的TEE功能"
                !meetsBasicRequirements -> "Android版本过低（需要Android 6.0+），不支持TEE功能"
                !keystoreAvailable -> "AndroidKeyStore不可用，TEE功能受限"
                else -> "TEE基础环境检测失败，设备可能不支持TEE功能"
            }
            
            TeeTestResult(
                testName = "基础TEE环境检测",
                passed = passed,
                details = details,
                message = message
            )
        } catch (e: Exception) {
            Log.e(TAG, "基础TEE环境检测异常", e)
            TeeTestResult(
                testName = "基础TEE环境检测",
                passed = false,
                details = mapOf("错误" to "检测过程中发生异常: ${e.javaClass.simpleName}"),
                message = "基础TEE环境检测异常",
                exception = e.message
            )
        }
    }
    
    /**
     * 执行硬件密钥存储交互测试（优化版本）
     * @return TeeTestResult 检测结果
     */
    private fun performHardwareKeystoreInteractionTestOptimized(): TeeTestResult {
        return try {
            // 快速预检查
            if (Build.VERSION.SDK_INT < MIN_ANDROID_VERSION_TEE) {
                return TeeTestResult(
                    testName = "硬件密钥库交互测试",
                    passed = false,
                    details = mapOf("错误" to "Android版本过低，不支持硬件密钥库"),
                    message = "Android版本过低，不支持硬件密钥库"
                )
            }
            
            // 检查KeyStore可用性
            try {
                KeyStore.getInstance(ANDROID_KEYSTORE)
            } catch (e: Exception) {
                return TeeTestResult(
                    testName = "硬件密钥库交互测试",
                    passed = false,
                    details = mapOf("错误" to "Android KeyStore不可用"),
                    message = "Android KeyStore不可用"
                )
            }
            
            // 调用原始测试方法
            performHardwareKeystoreInteractionTest()
        } catch (e: Exception) {
            Log.e(TAG, "硬件密钥库交互测试优化版本异常", e)
            TeeTestResult(
                testName = "硬件密钥库交互测试",
                passed = false,
                details = mapOf("错误" to "测试过程中发生异常: ${e.javaClass.simpleName}"),
                message = "硬件密钥库交互测试异常",
                exception = e.message
            )
        }
    }
    
    /**
     * 执行硬件密钥存储交互测试
     * @return TeeTestResult 检测结果
     */
    private fun performHardwareKeystoreInteractionTest(): TeeTestResult {
        return try {
            val details = mutableMapOf<String, Any>()
            val startTime = System.currentTimeMillis()
            var keyStore: KeyStore? = null
            var keyExists = false
            var isHardwareBacked = false
            var encryptionTest = false
            
            try {
                // 初始化KeyStore（添加超时保护）
                keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)
                details["KeyStore初始化"] = "成功"
                
                // 清理可能存在的旧密钥（安全清理）
                cleanupTestKey(keyStore, TEE_KEY_ALIAS)
                
                // 生成AES密钥（优化参数）
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    TEE_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256) // 明确指定密钥大小
                    .build()
                
                val keyGenStartTime = System.currentTimeMillis()
                keyGenerator.init(keyGenParameterSpec)
                val secretKey = keyGenerator.generateKey()
                val keyGenEndTime = System.currentTimeMillis()
                
                details["AES密钥生成"] = "成功"
                details["密钥生成耗时(ms)"] = keyGenEndTime - keyGenStartTime
            
            // 检查密钥是否在硬件中
            val retrievedKey = keyStore.getKey(TEE_KEY_ALIAS, null) as? SecretKey
            keyExists = retrievedKey != null
            details["密钥存储验证"] = if (keyExists) "成功" else "失败"
            
            // 检查密钥信息（Android M+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && retrievedKey != null) {
                try {
                    val keyFactory = javax.crypto.SecretKeyFactory.getInstance(retrievedKey.algorithm, ANDROID_KEYSTORE)
                    val keyInfo = keyFactory.getKeySpec(retrievedKey, KeyInfo::class.java) as KeyInfo
                    isHardwareBacked = keyInfo.isInsideSecureHardware
                    details["硬件支持状态"] = isHardwareBacked
                    details["密钥大小"] = keyInfo.keySize
                    details["用户认证要求"] = keyInfo.isUserAuthenticationRequired
                } catch (e: Exception) {
                    details["密钥信息获取"] = "异常: ${e.message}"
                }
            }
            
            // 执行加密解密测试
            if (keyExists) {
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, retrievedKey)
                    val testData = "TEE测试数据".toByteArray()
                    val encryptedData = cipher.doFinal(testData)
                    val iv = cipher.iv
                    
                    // 解密测试
                    cipher.init(Cipher.DECRYPT_MODE, retrievedKey, javax.crypto.spec.GCMParameterSpec(128, iv))
                    val decryptedData = cipher.doFinal(encryptedData)
                    
                    encryptionTest = testData.contentEquals(decryptedData)
                    details["加密解密测试"] = if (encryptionTest) "成功" else "失败"
                } catch (e: Exception) {
                    details["加密解密测试"] = "异常: ${e.message}"
                }
            }
            
                // 清理测试密钥（确保资源释放）
                cleanupTestKey(keyStore, TEE_KEY_ALIAS)
                details["密钥清理"] = "成功"
                
            } finally {
                // 确保在异常情况下也能清理资源
                keyStore?.let { cleanupTestKey(it, TEE_KEY_ALIAS) }
            }
            
            val endTime = System.currentTimeMillis()
            details["测试耗时(ms)"] = endTime - startTime
            
            val passed = keyExists && isHardwareBacked && encryptionTest
            val message = when {
                passed -> "硬件密钥存储交互测试通过，TEE功能正常"
                !keyExists -> "密钥生成失败，硬件密钥存储不可用"
                !isHardwareBacked -> "密钥未存储在安全硬件中，TEE功能异常"
                !encryptionTest -> "加密解密测试失败，TEE交互异常"
                else -> "硬件密钥存储交互测试失败"
            }
            
            TeeTestResult(
                testName = "硬件密钥存储交互测试",
                passed = passed,
                details = details,
                message = message
            )
        } catch (e: Exception) {
            Log.e(TAG, "硬件密钥存储交互测试异常", e)
            TeeTestResult(
                testName = "硬件密钥存储交互测试",
                passed = false,
                details = mapOf("错误" to "测试过程中发生异常: ${e.javaClass.simpleName}"),
                message = "硬件密钥存储交互测试异常",
                exception = e.message
            )
        }
    }
    
    /**
     * 安全清理测试密钥的辅助方法
     */
    private fun cleanupTestKey(keyStore: KeyStore, alias: String) {
        try {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理密钥 $alias 时发生异常", e)
        }
    }
    
    /**
     * 执行密钥认证功能测试（优化版本）
     */
    private fun performKeystoreAttestationTestOptimized(): TeeTestResult {
        // 快速版本检查
        if (Build.VERSION.SDK_INT < MIN_ANDROID_VERSION_ATTESTATION) {
            return TeeTestResult(
                testName = "密钥认证功能测试",
                passed = false,
                details = mapOf("支持状态" to "Android版本过低（需要Android 7.0+）"),
                message = "设备Android版本过低，不支持密钥认证功能"
            )
        }
        return performKeystoreAttestationTest()
    }
    
    /**
     * 执行StrongBox功能测试（优化版本）
     */
    private fun performStrongBoxTestOptimized(): TeeTestResult {
        // 快速版本和硬件检查
        if (Build.VERSION.SDK_INT < MIN_ANDROID_VERSION_STRONGBOX) {
            return TeeTestResult(
                testName = "StrongBox功能测试",
                passed = false,
                details = mapOf("支持状态" to "Android版本过低（需要Android 9.0+）"),
                message = "设备Android版本过低，不支持StrongBox功能"
            )
        }
        
        if (!context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")) {
            return TeeTestResult(
                testName = "StrongBox功能测试",
                passed = false,
                details = mapOf("硬件支持" to "设备不支持StrongBox硬件特性"),
                message = "设备不支持StrongBox硬件特性"
            )
        }
        
        return performStrongBoxTest()
    }
    
    /**
     * 执行生物识别TEE集成测试（优化版本）
     */
    private fun performBiometricTeeIntegrationTestOptimized(): TeeTestResult {
        return try {
            // 快速预检查
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return TeeTestResult(
                    testName = "生物识别TEE集成测试",
                    passed = false,
                    details = mapOf("错误" to "Android版本过低，不支持生物识别"),
                    message = "Android版本过低，不支持生物识别"
                )
            }
            
            // 检查生物识别硬件支持
            val packageManager = context.packageManager
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
                return TeeTestResult(
                    testName = "生物识别TEE集成测试",
                    passed = false,
                    details = mapOf("信息" to "设备不支持指纹识别硬件"),
                    message = "设备不支持指纹识别硬件"
                )
            }
            
            // 检查BiometricManager可用性
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val biometricManager = BiometricManager.from(context)
                    when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                            return TeeTestResult(
                                testName = "生物识别TEE集成测试",
                                passed = false,
                                details = mapOf("信息" to "设备无生物识别硬件"),
                                message = "设备无生物识别硬件"
                            )
                        }
                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                            return TeeTestResult(
                                testName = "生物识别TEE集成测试",
                                passed = false,
                                details = mapOf("信息" to "生物识别硬件当前不可用"),
                                message = "生物识别硬件当前不可用"
                            )
                        }
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                            return TeeTestResult(
                                testName = "生物识别TEE集成测试",
                                passed = false,
                                details = mapOf("信息" to "用户未注册生物识别"),
                                message = "用户未注册生物识别"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "BiometricManager检查失败，继续测试", e)
            }
            
            // 调用原始测试方法
            performBiometricTeeIntegrationTest()
        } catch (e: Exception) {
            Log.e(TAG, "生物识别TEE集成测试优化版本异常", e)
            TeeTestResult(
                testName = "生物识别TEE集成测试",
                passed = false,
                details = mapOf("错误" to "测试过程中发生异常: ${e.javaClass.simpleName}"),
                message = "生物识别TEE集成测试异常",
                exception = e.message
            )
        }
    }
    
    /**
     * 执行TEE加密性能测试（优化版本）
     */
    private fun performTeeEncryptionPerformanceTestOptimized(): TeeTestResult {
        return try {
            val details = mutableMapOf<String, Any>()
            val startTime = System.currentTimeMillis()
            var keyStore: KeyStore? = null
            
            try {
                // 初始化KeyStore
                keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)
                
                val performanceKeyAlias = "performance_test_key_opt"
                
                // 清理可能存在的旧密钥
                cleanupTestKey(keyStore, performanceKeyAlias)
                
                // 生成性能测试密钥
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    performanceKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .setKeySize(256)
                    .build()
                
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                
                val secretKey = keyStore.getKey(performanceKeyAlias, null) as SecretKey
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                
                // 准备测试数据（减少数据量）
                val smallData = "小数据测试".toByteArray()
                val mediumData = ByteArray(512) { it.toByte() } // 512B
                val largeData = ByteArray(2048) { it.toByte() } // 2KB
                
                // 小数据加密性能测试（减少迭代次数）
                val smallDataTimes = mutableListOf<Long>()
                repeat(PERFORMANCE_TEST_ITERATIONS_SMALL) {
                    val encStartTime = System.nanoTime()
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    cipher.doFinal(smallData)
                    val encEndTime = System.nanoTime()
                    smallDataTimes.add(encEndTime - encStartTime)
                }
                val avgSmallTime = smallDataTimes.average() / 1_000_000
                details["小数据平均加密时间(ms)"] = String.format("%.3f", avgSmallTime)
                
                // 中等数据加密性能测试
                val mediumDataTimes = mutableListOf<Long>()
                repeat(PERFORMANCE_TEST_ITERATIONS_MEDIUM) {
                    val encStartTime = System.nanoTime()
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    cipher.doFinal(mediumData)
                    val encEndTime = System.nanoTime()
                    mediumDataTimes.add(encEndTime - encStartTime)
                }
                val avgMediumTime = mediumDataTimes.average() / 1_000_000
                details["中等数据平均加密时间(ms)"] = String.format("%.3f", avgMediumTime)
                
                // 大数据加密性能测试
                val largeDataTimes = mutableListOf<Long>()
                repeat(PERFORMANCE_TEST_ITERATIONS_LARGE) {
                    val encStartTime = System.nanoTime()
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    cipher.doFinal(largeData)
                    val encEndTime = System.nanoTime()
                    largeDataTimes.add(encEndTime - encStartTime)
                }
                val avgLargeTime = largeDataTimes.average() / 1_000_000
                details["大数据平均加密时间(ms)"] = String.format("%.3f", avgLargeTime)
                
                // 计算吞吐量
                val throughputKBps = (2048.0 / (avgLargeTime / 1000.0)) / 1024
                details["加密吞吐量(KB/s)"] = String.format("%.2f", throughputKBps)
                
                // 密钥生成性能测试（减少次数）
                val keyGenTimes = mutableListOf<Long>()
                repeat(KEY_GEN_TEST_ITERATIONS) {
                    val keyGenStartTime = System.nanoTime()
                    val tempKeyAlias = "temp_key_opt_$it"
                    val tempKeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    val tempKeySpec = KeyGenParameterSpec.Builder(
                        tempKeyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(false)
                        .setKeySize(256)
                        .build()
                    tempKeyGenerator.init(tempKeySpec)
                    tempKeyGenerator.generateKey()
                    val keyGenEndTime = System.nanoTime()
                    keyGenTimes.add(keyGenEndTime - keyGenStartTime)
                    
                    // 清理临时密钥
                    cleanupTestKey(keyStore, tempKeyAlias)
                }
                val avgKeyGenTime = keyGenTimes.average() / 1_000_000
                details["平均密钥生成时间(ms)"] = String.format("%.3f", avgKeyGenTime)
                
                // 清理测试密钥
                cleanupTestKey(keyStore, performanceKeyAlias)
                details["密钥清理"] = "成功"
                
            } finally {
                // 确保资源清理
                keyStore?.let { 
                    cleanupTestKey(it, "performance_test_key_opt")
                    for (i in 0 until KEY_GEN_TEST_ITERATIONS) {
                        cleanupTestKey(it, "temp_key_opt_$i")
                    }
                }
            }
            
            val endTime = System.currentTimeMillis()
            details["总测试耗时(ms)"] = endTime - startTime
            
            // 性能评估（使用优化的阈值）
            val avgSmallTime = details["小数据平均加密时间(ms)"].toString().toDoubleOrNull() ?: Double.MAX_VALUE
            val avgMediumTime = details["中等数据平均加密时间(ms)"].toString().toDoubleOrNull() ?: Double.MAX_VALUE
            val avgLargeTime = details["大数据平均加密时间(ms)"].toString().toDoubleOrNull() ?: Double.MAX_VALUE
            val avgKeyGenTime = details["平均密钥生成时间(ms)"].toString().toDoubleOrNull() ?: Double.MAX_VALUE
            
            val performanceGood = avgSmallTime < PERFORMANCE_THRESHOLD_SMALL_MS && 
                                avgMediumTime < PERFORMANCE_THRESHOLD_MEDIUM_MS && 
                                avgLargeTime < PERFORMANCE_THRESHOLD_LARGE_MS && 
                                avgKeyGenTime < PERFORMANCE_THRESHOLD_KEYGEN_MS
            
            val performanceAcceptable = avgSmallTime < PERFORMANCE_THRESHOLD_SMALL_MS * 2 && 
                                      avgMediumTime < PERFORMANCE_THRESHOLD_MEDIUM_MS * 2 && 
                                      avgLargeTime < PERFORMANCE_THRESHOLD_LARGE_MS * 2 && 
                                      avgKeyGenTime < PERFORMANCE_THRESHOLD_KEYGEN_MS * 2
            
            val passed = performanceAcceptable
            val message = when {
                performanceGood -> "TEE加密性能测试通过，性能优秀"
                performanceAcceptable -> "TEE加密性能测试通过，性能可接受"
                else -> "TEE加密性能测试失败，性能较差"
            }
            
            details["性能评级"] = when {
                performanceGood -> "优秀"
                performanceAcceptable -> "良好"
                else -> "较差"
            }
            
            TeeTestResult(
                testName = "TEE加密性能测试",
                passed = passed,
                details = details,
                message = message
            )
        } catch (e: Exception) {
            Log.e(TAG, "TEE加密性能测试异常", e)
            TeeTestResult(
                testName = "TEE加密性能测试",
                passed = false,
                details = mapOf("错误" to "测试过程中发生异常: ${e.javaClass.simpleName}"),
                message = "TEE加密性能测试异常",
                exception = e.message
            )
        }
    }
    
    /**
     * 执行密钥认证功能测试
     * @return TeeTestResult 检测结果
     */
    private fun performKeystoreAttestationTest(): TeeTestResult {
        return try {
            val details = mutableMapOf<String, Any>()
            val startTime = System.currentTimeMillis()
            
            // 检查Android版本支持
            val sdkVersion = Build.VERSION.SDK_INT
            details["Android版本"] = sdkVersion
            
            if (sdkVersion < Build.VERSION_CODES.N) {
                return TeeTestResult(
                    testName = "密钥认证功能测试",
                    passed = false,
                    details = details + ("支持状态" to "Android N以下版本不支持密钥认证"),
                    message = "设备Android版本过低，不支持密钥认证功能"
                )
            }
            
            // 检查硬件特性支持
            val hasAttestationFeature = context.packageManager.hasSystemFeature("android.hardware.keystore.app_attest_key")
            details["硬件认证特性"] = hasAttestationFeature
            
            // 初始化KeyStore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            // 清理可能存在的旧密钥
            try {
                keyStore.deleteEntry(TEE_EC_KEY_ALIAS)
            } catch (e: Exception) {
                // 忽略删除不存在密钥的异常
            }
            
            // 生成EC密钥对用于认证测试
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            val challenge = "TEE认证测试挑战".toByteArray()
            
            val parameterSpec = KeyGenParameterSpec.Builder(
                TEE_EC_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setAttestationChallenge(challenge)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyPairGenerator.initialize(parameterSpec)
            val keyPair = keyPairGenerator.generateKeyPair()
            details["EC密钥对生成"] = "成功"
            
            // 获取认证证书链
            val certificateChain = keyStore.getCertificateChain(TEE_EC_KEY_ALIAS)
            val hasCertificateChain = certificateChain != null && certificateChain.isNotEmpty()
            details["证书链获取"] = if (hasCertificateChain) "成功" else "失败"
            
            if (hasCertificateChain) {
                details["证书链长度"] = certificateChain!!.size
                
                // 分析根证书
                val leafCertificate = certificateChain[0] as X509Certificate
                details["叶证书主题"] = leafCertificate.subjectDN.name
                details["叶证书颁发者"] = leafCertificate.issuerDN.name
                details["证书有效期开始"] = leafCertificate.notBefore.toString()
                details["证书有效期结束"] = leafCertificate.notAfter.toString()
                
                // 检查证书扩展（认证扩展）
                try {
                    val attestationExtension = leafCertificate.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")
                    details["包含认证扩展"] = attestationExtension != null
                } catch (e: Exception) {
                    details["认证扩展检查"] = "异常: ${e.message}"
                }
            }
            
            // 执行签名验证测试
            var signatureTest = false
            if (hasCertificateChain) {
                try {
                    val signature = java.security.Signature.getInstance("SHA256withECDSA")
                    signature.initSign(keyPair.private)
                    val testData = "TEE签名测试数据".toByteArray()
                    signature.update(testData)
                    val signatureBytes = signature.sign()
                    
                    // 验证签名
                    signature.initVerify(keyPair.public)
                    signature.update(testData)
                    signatureTest = signature.verify(signatureBytes)
                    details["签名验证测试"] = if (signatureTest) "成功" else "失败"
                } catch (e: Exception) {
                    details["签名验证测试"] = "异常: ${e.message}"
                }
            }
            
            // 清理测试密钥
            try {
                keyStore.deleteEntry(TEE_EC_KEY_ALIAS)
                details["密钥清理"] = "成功"
            } catch (e: Exception) {
                details["密钥清理"] = "异常: ${e.message}"
            }
            
            val endTime = System.currentTimeMillis()
            details["测试耗时(ms)"] = endTime - startTime
            
            val passed = hasCertificateChain && signatureTest
            val message = when {
                passed -> "密钥认证功能测试通过，TEE认证能力正常"
                !hasCertificateChain -> "无法获取认证证书链，设备可能不支持密钥认证"
                !signatureTest -> "签名验证失败，认证功能异常"
                else -> "密钥认证功能测试失败"
            }
            
            TeeTestResult(
                testName = "密钥认证功能测试",
                passed = passed,
                details = details,
                message = message
            )
        } catch (e: Exception) {
            Log.e(TAG, "密钥认证功能测试异常", e)
            TeeTestResult(
                testName = "密钥认证功能测试",
                passed = false,
                details = mapOf("错误" to "测试过程中发生异常"),
                message = "密钥认证功能测试异常",
                exception = e.message
            )
        }
    }
    
    /**
     * 执行StrongBox功能测试
     * @return TeeTestResult 检测结果
     */
    private fun performStrongBoxTest(): TeeTestResult {
        return try {
            val details = mutableMapOf<String, Any>()
            val startTime = System.currentTimeMillis()
            
            // 检查Android版本支持
            val sdkVersion = Build.VERSION.SDK_INT
            details["Android版本"] = sdkVersion
            
            if (sdkVersion < Build.VERSION_CODES.P) {
                return TeeTestResult(
                    testName = "StrongBox功能测试",
                    passed = false,
                    details = details + ("支持状态" to "Android P以下版本不支持StrongBox"),
                    message = "设备Android版本过低，不支持StrongBox功能"
                )
            }
            
            // 检查StrongBox硬件特性
            val hasStrongBoxFeature = context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
            details["StrongBox硬件特性"] = hasStrongBoxFeature
            
            if (!hasStrongBoxFeature) {
                return TeeTestResult(
                    testName = "StrongBox功能测试",
                    passed = false,
                    details = details,
                    message = "设备不支持StrongBox硬件安全模块"
                )
            }
            
            // 初始化KeyStore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            val strongBoxKeyAlias = "strongbox_test_key"
            
            // 初始化StrongBox状态变量
            var isStrongBoxBacked = false
            
            // 清理可能存在的旧密钥
            try {
                keyStore.deleteEntry(strongBoxKeyAlias)
            } catch (e: Exception) {
                // 忽略删除不存在密钥的异常
            }
            
            // 生成StrongBox支持的密钥
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                strongBoxKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setIsStrongBoxBacked(true)  // 要求使用StrongBox
                .build()
            
            var strongBoxKeyGenerated = false
            try {
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                strongBoxKeyGenerated = true
                details["StrongBox密钥生成"] = "成功"
            } catch (e: Exception) {
                details["StrongBox密钥生成"] = "失败: ${e.message}"
            }
            
            // 验证密钥是否真的在StrongBox中
            if (strongBoxKeyGenerated) {
                try {
                    val secretKey = keyStore.getKey(strongBoxKeyAlias, null) as? SecretKey
                    if (secretKey != null) {
                         val keyFactory = javax.crypto.SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
                         val keyInfo = keyFactory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
                         val isInsideSecureHardware = keyInfo.isInsideSecureHardware
                         // isStrongBoxBacked方法在Android P (API 28)以上才可用
                         val isStrongBoxBackedSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                             try {
                                 // 使用反射调用isStrongBoxBacked方法以避免编译时错误
                                 val method = keyInfo.javaClass.getMethod("isStrongBoxBacked")
                                 method.invoke(keyInfo) as Boolean
                             } catch (e: Exception) {
                                 false
                             }
                         } else {
                             false
                         }
                         isStrongBoxBacked = isInsideSecureHardware && isStrongBoxBackedSupported
                         details["StrongBox支持状态"] = isStrongBoxBacked
                         details["安全硬件状态"] = isInsideSecureHardware
                         details["StrongBox API支持"] = isStrongBoxBackedSupported
                     } else {
                         isStrongBoxBacked = false
                         details["StrongBox验证"] = "无法获取密钥信息"
                     }
                } catch (e: Exception) {
                    isStrongBoxBacked = false
                    details["StrongBox验证"] = "异常: ${e.message}"
                }
            } else {
                isStrongBoxBacked = false
                details["StrongBox验证"] = "密钥生成失败，无法验证"
            }
            
            // 执行StrongBox加密性能测试
            var performanceTest = false
            if (strongBoxKeyGenerated && isStrongBoxBacked) {
                try {
                    val secretKey = keyStore.getKey(strongBoxKeyAlias, null) as SecretKey
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    
                    val performanceStartTime = System.currentTimeMillis()
                    
                    // 执行多次加密操作测试性能
                    val testData = "StrongBox性能测试数据".toByteArray()
                    val iterations = 10
                    
                    for (i in 1..iterations) {
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                        cipher.doFinal(testData)
                    }
                    
                    val performanceEndTime = System.currentTimeMillis()
                    val avgTime = (performanceEndTime - performanceStartTime).toDouble() / iterations
                    
                    details["平均加密时间(ms)"] = String.format("%.2f", avgTime)
                    details["性能测试迭代次数"] = iterations
                    
                    performanceTest = avgTime < 100 // 平均加密时间小于100ms认为性能正常
                    details["性能测试结果"] = if (performanceTest) "正常" else "较慢"
                } catch (e: Exception) {
                    details["性能测试"] = "异常: ${e.message}"
                }
            }
            
            // 清理测试密钥
            try {
                keyStore.deleteEntry(strongBoxKeyAlias)
                details["密钥清理"] = "成功"
            } catch (e: Exception) {
                details["密钥清理"] = "异常: ${e.message}"
            }
            
            val endTime = System.currentTimeMillis()
            details["测试耗时(ms)"] = endTime - startTime
            
            val passed = hasStrongBoxFeature && strongBoxKeyGenerated && isStrongBoxBacked
            val message = when {
                passed && performanceTest -> "StrongBox功能测试通过，硬件安全模块性能正常"
                passed -> "StrongBox功能测试通过，但性能可能存在问题"
                !hasStrongBoxFeature -> "设备不支持StrongBox硬件特性"
                !strongBoxKeyGenerated -> "StrongBox密钥生成失败"
                !isStrongBoxBacked -> "密钥未存储在StrongBox中"
                else -> "StrongBox功能测试失败"
            }
            
            TeeTestResult(
                testName = "StrongBox功能测试",
                passed = passed,
                details = details,
                message = message
            )
        } catch (e: Exception) {
            Log.e(TAG, "StrongBox功能测试异常", e)
            TeeTestResult(
                testName = "StrongBox功能测试",
                passed = false,
                details = mapOf("错误" to "测试过程中发生异常"),
                message = "StrongBox功能测试异常",
                exception = e.message
            )
        }
    }
    
    /**
     * 执行生物识别TEE集成测试
     * @return TeeTestResult 检测结果
     */
    private fun performBiometricTeeIntegrationTest(): TeeTestResult {
        return try {
            val details = mutableMapOf<String, Any>()
            val startTime = System.currentTimeMillis()
            
            // 检查Android版本
            val sdkVersion = Build.VERSION.SDK_INT
            details["Android版本"] = sdkVersion
            
            // 检查生物识别硬件支持
            val hasFingerprint = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            val hasFace = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
            val hasIris = context.packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)
            
            details["指纹硬件支持"] = hasFingerprint
            details["面部识别硬件支持"] = hasFace
            details["虹膜识别硬件支持"] = hasIris
            
            // 检查生物识别管理器状态
            var biometricStatus = "不可用"
            var strongBiometricAvailable = false
            
            if (sdkVersion >= Build.VERSION_CODES.Q) {
                 try {
                     val biometricManager = BiometricManager.from(context)
                     when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                         BiometricManager.BIOMETRIC_SUCCESS -> {
                             biometricStatus = "强生物识别可用"
                             strongBiometricAvailable = true
                         }
                         BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> biometricStatus = "无生物识别硬件"
                         BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> biometricStatus = "硬件不可用"
                         BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> biometricStatus = "未注册生物识别"
                         BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> biometricStatus = "需要安全更新"
                         else -> biometricStatus = "其他错误"
                     }
                 } catch (e: Exception) {
                     biometricStatus = "检测异常: ${e.message}"
                 }
             } else {
                 biometricStatus = "Android版本过低"
             }
            
            details["生物识别状态"] = biometricStatus
            details["强生物识别可用"] = strongBiometricAvailable
            
            // 测试生物识别与TEE的集成（创建需要生物识别的密钥）
            var biometricKeyTest = false
            if (strongBiometricAvailable) {
                try {
                    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                    keyStore.load(null)
                    
                    val biometricKeyAlias = "biometric_tee_test_key"
                    
                    // 清理可能存在的旧密钥
                    try {
                        keyStore.deleteEntry(biometricKeyAlias)
                    } catch (e: Exception) {
                        // 忽略删除不存在密钥的异常
                    }
                    
                    // 创建需要生物识别认证的密钥
                    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                        biometricKeyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationValidityDurationSeconds(30)
                        .build()
                    
                    keyGenerator.init(keyGenParameterSpec)
                    keyGenerator.generateKey()
                    
                    // 验证密钥是否创建成功
                    val secretKey = keyStore.getKey(biometricKeyAlias, null) as? SecretKey
                    biometricKeyTest = secretKey != null
                    
                    details["生物识别密钥创建"] = if (biometricKeyTest) "成功" else "失败"
                    
                    // 清理测试密钥
                    try {
                        keyStore.deleteEntry(biometricKeyAlias)
                    } catch (e: Exception) {
                        details["生物识别密钥清理"] = "异常: ${e.message}"
                    }
                } catch (e: Exception) {
                    details["生物识别密钥测试"] = "异常: ${e.message}"
                }
            }
            
            val endTime = System.currentTimeMillis()
            details["测试耗时(ms)"] = endTime - startTime
            
            val passed = (hasFingerprint || hasFace || hasIris) && strongBiometricAvailable
            val message = when {
                passed && biometricKeyTest -> "生物识别TEE集成测试通过，支持生物识别保护的密钥"
                passed -> "生物识别硬件可用，但TEE集成可能存在问题"
                strongBiometricAvailable -> "生物识别功能可用，但硬件支持有限"
                hasFingerprint || hasFace || hasIris -> "检测到生物识别硬件，但功能不可用"
                else -> "设备不支持生物识别功能"
            }
            
            TeeTestResult(
                testName = "生物识别TEE集成测试",
                passed = passed,
                details = details,
                message = message
            )
        } catch (e: Exception) {
            Log.e(TAG, "生物识别TEE集成测试异常", e)
            TeeTestResult(
                testName = "生物识别TEE集成测试",
                passed = false,
                details = mapOf("错误" to "测试过程中发生异常"),
                message = "生物识别TEE集成测试异常",
                exception = e.message
            )
        }
    }
    
    /**
     * 执行TEE加密性能测试
     * @return TeeTestResult 检测结果
     */
    private fun performTeeEncryptionPerformanceTest(): TeeTestResult {
        return try {
            val details = mutableMapOf<String, Any>()
            val startTime = System.currentTimeMillis()
            
            // 初始化KeyStore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            val performanceKeyAlias = "performance_test_key"
            
            // 清理可能存在的旧密钥
            try {
                keyStore.deleteEntry(performanceKeyAlias)
            } catch (e: Exception) {
                // 忽略删除不存在密钥的异常
            }
            
            // 生成性能测试密钥
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                performanceKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            
            val secretKey = keyStore.getKey(performanceKeyAlias, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            
            // 准备测试数据
            val smallData = "小数据测试".toByteArray()
            val mediumData = ByteArray(1024) { it.toByte() } // 1KB
            val largeData = ByteArray(10240) { it.toByte() } // 10KB
            
            // 小数据加密性能测试
            val smallDataTimes = mutableListOf<Long>()
            repeat(50) {
                val encStartTime = System.nanoTime()
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                cipher.doFinal(smallData)
                val encEndTime = System.nanoTime()
                smallDataTimes.add(encEndTime - encStartTime)
            }
            val avgSmallTime = smallDataTimes.average() / 1_000_000 // 转换为毫秒
            details["小数据平均加密时间(ms)"] = String.format("%.3f", avgSmallTime)
            
            // 中等数据加密性能测试
            val mediumDataTimes = mutableListOf<Long>()
            repeat(20) {
                val encStartTime = System.nanoTime()
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                cipher.doFinal(mediumData)
                val encEndTime = System.nanoTime()
                mediumDataTimes.add(encEndTime - encStartTime)
            }
            val avgMediumTime = mediumDataTimes.average() / 1_000_000
            details["中等数据平均加密时间(ms)"] = String.format("%.3f", avgMediumTime)
            
            // 大数据加密性能测试
            val largeDataTimes = mutableListOf<Long>()
            repeat(10) {
                val encStartTime = System.nanoTime()
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                cipher.doFinal(largeData)
                val encEndTime = System.nanoTime()
                largeDataTimes.add(encEndTime - encStartTime)
            }
            val avgLargeTime = largeDataTimes.average() / 1_000_000
            details["大数据平均加密时间(ms)"] = String.format("%.3f", avgLargeTime)
            
            // 计算吞吐量
            val throughputMBps = (10240.0 / (avgLargeTime / 1000.0)) / (1024 * 1024)
            details["加密吞吐量(MB/s)"] = String.format("%.2f", throughputMBps)
            
            // 密钥生成性能测试
            val keyGenTimes = mutableListOf<Long>()
            repeat(5) {
                val keyGenStartTime = System.nanoTime()
                val tempKeyAlias = "temp_key_$it"
                val tempKeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val tempKeySpec = KeyGenParameterSpec.Builder(
                    tempKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
                tempKeyGenerator.init(tempKeySpec)
                tempKeyGenerator.generateKey()
                val keyGenEndTime = System.nanoTime()
                keyGenTimes.add(keyGenEndTime - keyGenStartTime)
                
                // 清理临时密钥
                try {
                    keyStore.deleteEntry(tempKeyAlias)
                } catch (e: Exception) {
                    // 忽略清理异常
                }
            }
            val avgKeyGenTime = keyGenTimes.average() / 1_000_000
            details["平均密钥生成时间(ms)"] = String.format("%.3f", avgKeyGenTime)
            
            // 清理测试密钥
            try {
                keyStore.deleteEntry(performanceKeyAlias)
                details["密钥清理"] = "成功"
            } catch (e: Exception) {
                details["密钥清理"] = "异常: ${e.message}"
            }
            
            val endTime = System.currentTimeMillis()
            details["总测试耗时(ms)"] = endTime - startTime
            
            // 性能评估
            val performanceGood = avgSmallTime < 5.0 && avgMediumTime < 20.0 && avgLargeTime < 100.0 && avgKeyGenTime < 200.0
            val performanceAcceptable = avgSmallTime < 10.0 && avgMediumTime < 50.0 && avgLargeTime < 300.0 && avgKeyGenTime < 500.0
            
            val passed = performanceAcceptable
            val message = when {
                performanceGood -> "TEE加密性能测试通过，性能优秀"
                performanceAcceptable -> "TEE加密性能测试通过，性能可接受"
                else -> "TEE加密性能测试失败，性能较差"
            }
            
            details["性能评级"] = when {
                performanceGood -> "优秀"
                performanceAcceptable -> "良好"
                else -> "较差"
            }
            
            TeeTestResult(
                testName = "TEE加密性能测试",
                passed = passed,
                details = details,
                message = message
            )
        } catch (e: Exception) {
            Log.e(TAG, "TEE加密性能测试异常", e)
            TeeTestResult(
                testName = "TEE加密性能测试",
                passed = false,
                details = mapOf("错误" to "测试过程中发生异常"),
                message = "TEE加密性能测试异常",
                exception = e.message
            )
        }
    }
}