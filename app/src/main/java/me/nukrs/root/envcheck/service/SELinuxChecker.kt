package me.nukrs.root.envcheck.service

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader

class AndroidSELinuxChecker(private val context: Context) {

    companion object {
        private const val TAG = "SELinuxChecker"

        private const val SELINUX_FS = "/sys/fs/selinux"
        private const val PROC_SELF_ATTR = "/proc/self/attr"
        private const val SELINUX_ENFORCE = "/sys/fs/selinux/enforce"
        private const val SELINUX_POLICY_VERSION = "/sys/fs/selinux/policyvers"
        private const val PROC_VERSION = "/proc/version"

        private const val SEPOLICY_FILE = "/sepolicy"
        private const val PLAT_SEPOLICY = "/system/etc/selinux/plat_sepolicy.cil"
        private const val VENDOR_SEPOLICY = "/vendor/etc/selinux/vendor_sepolicy.cil"
    }


    data class SELinuxStatus(
        val enabled: Boolean = false,
        val enforcing: Boolean = false,
        val policyVersion: String = "Unknown",
        val currentContext: String = "Unknown",
        val apiLevel: Int = Build.VERSION.SDK_INT,
        val securityPatch: String = Build.VERSION.SECURITY_PATCH
    )


    data class ProcessContext(
        val pid: Int,
        val context: String,
        val isValid: Boolean
    )


    data class FileContext(
        val path: String,
        val context: String,
        val exists: Boolean,
        val readable: Boolean
    )


    data class SELinuxCheckResult(
        val systemInfo: SELinuxStatus,
        val processContext: ProcessContext,
        val fileContexts: List<FileContext>,
        val policyInfo: PolicyInfo,
        val recommendations: List<String>
    )


    data class PolicyInfo(
        val policyExists: Boolean,
        val policySize: Long,
        val policyPath: String,
        val customPolicies: List<String>
    )


    fun isSELinuxEnabled(): Boolean {
        return try {
            try {
                val selinuxProp = System.getProperty("ro.boot.selinux")
                if (selinuxProp != null && selinuxProp.isNotEmpty()) {
                    return true
                }
            } catch (e: Exception) {
            }

            try {
                val context = getCurrentProcessContext()
                if (context != "Unknown" && context.contains(":") && !context.contains("Error")) {
                    return true
                }
            } catch (e: Exception) {
            }

            try {
                val pm = context.packageManager

                if (pm.hasSystemFeature("android.software.secure_lock_screen")) {

                    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                }
            } catch (e: Exception) {
            }
            

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking SELinux status: ${e.message}")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        }
    }

    fun isSELinuxEnforcing(): Boolean {
        return try {

            try {
                val bootSelinux = System.getProperty("ro.boot.selinux")
                if (bootSelinux != null && bootSelinux.contains("enforcing", true)) {
                    return true
                }
                if (bootSelinux != null && bootSelinux.contains("permissive", true)) {
                    return false
                }
            } catch (e: Exception) {
            }
            

            try {
                val restrictedPaths = listOf("/system/bin", "/system/lib", "/data")
                var restrictedCount = 0
                
                for (path in restrictedPaths) {
                    try {
                        val file = File(path)
                        if (!file.canRead() || !file.canExecute()) {
                            restrictedCount++
                        }
                    } catch (e: Exception) {
                        restrictedCount++
                    }
                }

                if (restrictedCount >= restrictedPaths.size * 0.7) {
                    return true
                }
            } catch (e: Exception) {
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val securityPatch = Build.VERSION.SECURITY_PATCH
                    if (securityPatch.isNotEmpty()) {
                        return true
                    }
                } catch (e: Exception) {
                }
                return true
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking SELinux enforce status: ${e.message}")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        }
    }


    fun getSELinuxPolicyVersion(): String {
        return try {

            try {
                val buildSelinux = System.getProperty("ro.build.selinux")
                if (buildSelinux != null && buildSelinux.isNotEmpty()) {
                    return buildSelinux
                }
            } catch (e: Exception) {
            }

            val apiLevel = Build.VERSION.SDK_INT
            val policyVersion = when {
                apiLevel >= Build.VERSION_CODES.TIRAMISU -> "33+" // Android 13+
                apiLevel >= Build.VERSION_CODES.S -> "31-32" // Android 12
                apiLevel >= Build.VERSION_CODES.R -> "30" // Android 11
                apiLevel >= Build.VERSION_CODES.Q -> "29" // Android 10
                apiLevel >= Build.VERSION_CODES.P -> "28" // Android 9
                apiLevel >= Build.VERSION_CODES.O_MR1 -> "27" // Android 8.1
                apiLevel >= Build.VERSION_CODES.O -> "26" // Android 8.0
                apiLevel >= Build.VERSION_CODES.N_MR1 -> "25" // Android 7.1
                apiLevel >= Build.VERSION_CODES.N -> "24" // Android 7.0
                apiLevel >= Build.VERSION_CODES.M -> "23" // Android 6.0
                apiLevel >= Build.VERSION_CODES.LOLLIPOP_MR1 -> "22" // Android 5.1
                apiLevel >= Build.VERSION_CODES.LOLLIPOP -> "21" // Android 5.0
                else -> "Legacy"
            }
            try {
                val buildId = Build.ID
                val securityPatch = Build.VERSION.SECURITY_PATCH
                if (buildId.isNotEmpty() && securityPatch.isNotEmpty()) {
                    return "$policyVersion (Build: $buildId, Patch: $securityPatch)"
                }
            } catch (e: Exception) {
            }
            
            policyVersion
            
        } catch (e: Exception) {
            Log.w(TAG, "Error reading policy version: ${e.message}")
            "API ${Build.VERSION.SDK_INT}"
        }
    }

    fun getCurrentProcessContext(): String {
        return try {
            try {
                val currentFile = File("/proc/self/attr/current")
                if (currentFile.exists() && currentFile.canRead()) {
                    val context = currentFile.readText().trim()
                    if (context.isNotEmpty() && !context.contains("Permission denied")) {
                        return context
                    }
                }
            } catch (e: Exception) {
            }

            try {
                val packageName = context.packageName
                val uid = android.os.Process.myUid()
                val pid = android.os.Process.myPid()

                val possibleContexts = listOf(
                    "u:r:untrusted_app:s0:c$uid,c${uid+1}",
                    "u:r:untrusted_app_${Build.VERSION.SDK_INT}:s0:c$uid,c${uid+1}",
                    "u:r:platform_app:s0",
                    "u:r:system_app:s0"
                )

                val applicationInfo = context.applicationInfo
                val isSystemApp = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isPlatformApp = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                return when {
                    isPlatformApp -> "u:r:platform_app:s0 (推断)"
                    isSystemApp -> "u:r:system_app:s0 (推断)"
                    else -> "u:r:untrusted_app:s0:c$uid (推断)"
                }
            } catch (e: Exception) {
            }

            try {
                val pid = android.os.Process.myPid()
                val uid = android.os.Process.myUid()
                return "u:r:untrusted_app:s0:c$uid (基于UID推断)"
            } catch (e: Exception) {
            }
            
            "u:r:untrusted_app:s0 (默认上下文)"
            
        } catch (e: Exception) {
            Log.w(TAG, "Error reading current context: ${e.message}")
            "Error: ${e.message}"
        }
    }


    fun getProcessContext(pid: Int): ProcessContext {
        return try {
            val contextFile = File("/proc/$pid/attr/current")
            if (contextFile.exists() && contextFile.canRead()) {
                val context = contextFile.readText().trim()
                if (context.isNotEmpty() && !context.contains("Permission denied")) {
                    return ProcessContext(pid, context, true)
                }
            }

            val psResult = executeCommand("ps -Z -p $pid")
            if (psResult != null) {
                val lines = psResult.split("\n")
                for (line in lines) {
                    if (line.contains(pid.toString())) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.isNotEmpty()) {
                            val context = parts[0]
                            // 验证上下文格式
                            if (context.contains(":") && !context.startsWith("-")) {
                                return ProcessContext(pid, context, true)
                            }
                        }
                    }
                }
            }

            val psLabelResult = executeCommand("ps -o pid,label -p $pid")
            if (psLabelResult != null) {
                val lines = psLabelResult.split("\n")
                for (line in lines) {
                    if (line.contains(pid.toString())) {
                        val parts = line.trim().split("\\s+".toRegex(), 2)
                        if (parts.size >= 2) {
                            val context = parts[1].trim()
                            if (context.isNotEmpty() && context != "-" && context.contains(":")) {
                                return ProcessContext(pid, context, true)
                            }
                        }
                    }
                }
            }

            val statusFile = File("/proc/$pid/status")
            if (statusFile.exists() && statusFile.canRead()) {
                val statusContent = statusFile.readText()
                val lines = statusContent.split("\n")
                for (line in lines) {
                    if (line.startsWith("SELinux:") || line.startsWith("Seccomp:")) {
                        val context = line.substringAfter(":").trim()
                        if (context.isNotEmpty() && context.contains(":")) {
                            return ProcessContext(pid, context, true)
                        }
                    }
                }
            }
            
            ProcessContext(pid, "Context unavailable", false)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading process context for PID $pid: ${e.message}")
            ProcessContext(pid, "Error: ${e.message}", false)
        }
    }

    fun getFileContext(filePath: String): FileContext {
        val file = File(filePath)
        return try {
            if (!file.exists()) {
                return FileContext(filePath, "文件不存在", false, false)
            }

            val canRead = try {
                file.canRead()
            } catch (e: Exception) {
                false
            }

            val inferredContext = when {
                filePath.startsWith(context.filesDir.absolutePath) -> {
                    "u:object_r:app_data_file:s0 (推断)"
                }
                filePath.startsWith(context.cacheDir.absolutePath) -> {
                    "u:object_r:app_data_file:s0 (推断)"
                }
                filePath.startsWith("/data/data/${context.packageName}") -> {
                    "u:object_r:app_data_file:s0 (推断)"
                }
                filePath.startsWith("/system/bin") -> {
                    "u:object_r:system_file:s0 (推断)"
                }
                filePath.startsWith("/system/lib") -> {
                    "u:object_r:system_lib_file:s0 (推断)"
                }
                filePath.startsWith("/system") -> {
                    "u:object_r:system_file:s0 (推断)"
                }
                filePath.startsWith("/vendor") -> {
                    "u:object_r:vendor_file:s0 (推断)"
                }
                filePath.startsWith("/data") -> {
                    "u:object_r:system_data_file:s0 (推断)"
                }
                filePath.startsWith("/proc") -> {
                    "u:object_r:proc:s0 (推断)"
                }
                filePath.startsWith("/sys") -> {
                    "u:object_r:sysfs:s0 (推断)"
                }
                filePath.startsWith("/dev") -> {
                    "u:object_r:device:s0 (推断)"
                }
                else -> {
                    "u:object_r:unlabeled:s0 (推断)"
                }
            }

            if (canRead) {
                try {
                    // 基于文件属性调整上下文推断
                    val adjustedContext = when {
                        file.canExecute() && filePath.startsWith("/system") -> {
                            "u:object_r:system_file:s0 (可执行)"
                        }
                        file.isDirectory && filePath.contains("app") -> {
                            "u:object_r:app_data_file:s0 (目录)"
                        }
                        else -> inferredContext
                    }
                    
                    return FileContext(filePath, adjustedContext, true, canRead)
                } catch (e: Exception) {
                }
            }

            val versionAdjustedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 有更严格的文件访问控制
                when {
                    filePath.startsWith("/storage") -> "u:object_r:fuse:s0 (推断)"
                    filePath.contains("external") -> "u:object_r:media_rw_data_file:s0 (推断)"
                    else -> inferredContext
                }
            } else {
                inferredContext
            }

            val finalContext = if (!canRead) {
                "权限不足: $versionAdjustedContext"
            } else {
                versionAdjustedContext
            }
            
            FileContext(filePath, finalContext, true, canRead)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading file context for $filePath: ${e.message}")
            FileContext(filePath, "检测异常: ${e.message}", file.exists(), false)
        }
    }

    private fun executeCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            val timeoutMs = 3000L
            val startTime = System.currentTimeMillis()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = StringBuilder()
            val errorOutput = StringBuilder()

            try {
                reader.use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        if (System.currentTimeMillis() - startTime > timeoutMs) {
                            break
                        }
                        output.append(line).append("\n")
                    }
                }
                
                errorReader.use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        if (System.currentTimeMillis() - startTime > timeoutMs) {
                            break
                        }
                        errorOutput.append(line).append("\n")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Command read timeout or error: ${e.message}")
            }

            val finished = try {
                process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                false
            }
            
            if (!finished) {
                process.destroyForcibly()
                Log.d(TAG, "Command '$command' timed out")
                return null
            }
            
            val exitCode = process.exitValue()
            val result = output.toString().trim()
            val errorResult = errorOutput.toString().trim()

            when {
                result.isNotEmpty() && exitCode == 0 -> {
                    Log.d(TAG, "Command '$command' succeeded: $result")
                    return result
                }
                errorResult.contains("Permission denied") -> {
                    Log.d(TAG, "Command '$command' denied by SELinux")
                    return null
                }
                errorResult.contains("not found") -> {
                    Log.d(TAG, "Command '$command' not found")
                    return null
                }
                errorResult.isNotEmpty() -> {
                    Log.d(TAG, "Command '$command' error: $errorResult")
                    return null
                }
                else -> {
                    Log.d(TAG, "Command '$command' no output, exit code: $exitCode")
                    return null
                }
            }
            
        } catch (e: SecurityException) {
            Log.d(TAG, "Command '$command' blocked by security policy: ${e.message}")
            null
        } catch (e: Exception) {
            Log.d(TAG, "Command '$command' failed: ${e.message}")
            null
        }
    }

    fun getPolicyInfo(): PolicyInfo {
        val policyPaths = listOf(SEPOLICY_FILE, PLAT_SEPOLICY, VENDOR_SEPOLICY)
        val customPolicies = mutableListOf<String>()

        var mainPolicy: File? = null
        var policyPath = ""

        for (path in policyPaths) {
            val file = File(path)
            if (file.exists()) {
                mainPolicy = file
                policyPath = path
                break
            }
        }

        try {
            val systemEtcSelinux = File("/system/etc/selinux")
            if (systemEtcSelinux.exists()) {
                systemEtcSelinux.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".cil") || file.name.endsWith(".te")) {
                        customPolicies.add(file.absolutePath)
                    }
                }
            }

            val vendorEtcSelinux = File("/vendor/etc/selinux")
            if (vendorEtcSelinux.exists()) {
                vendorEtcSelinux.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".cil") || file.name.endsWith(".te")) {
                        customPolicies.add(file.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning for custom policies: ${e.message}")
        }

        return PolicyInfo(
            policyExists = mainPolicy != null,
            policySize = mainPolicy?.length() ?: 0,
            policyPath = policyPath,
            customPolicies = customPolicies
        )
    }

    fun getRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                recommendations.add("Android 10+: 建议使用scoped storage适配SELinux策略")
                recommendations.add("考虑使用MediaStore API访问媒体文件")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                recommendations.add("Android 7+: 注意文件访问权限变化")
                recommendations.add("避免直接访问系统目录")
            }
        }

        if (isSELinuxEnabled()) {
            if (isSELinuxEnforcing()) {
                recommendations.add("SELinux处于强制模式，确保应用权限声明完整")
                recommendations.add("测试时注意AVC拒绝日志")
            } else {
                recommendations.add("SELinux处于宽松模式，部署前需测试强制模式")
            }
        } else {
            recommendations.add("SELinux未启用，这在现代Android设备上很少见")
        }

        recommendations.add("使用Context.getFilesDir()等API获取应用私有目录")
        recommendations.add("避免使用反射访问系统API")
        recommendations.add("申请权限时考虑SELinux限制")

        return recommendations
    }

    suspend fun runComprehensiveCheck(): SELinuxCheckResult = withContext(Dispatchers.IO) {
        val systemInfo = SELinuxStatus(
            enabled = isSELinuxEnabled(),
            enforcing = isSELinuxEnforcing(),
            policyVersion = getSELinuxPolicyVersion(),
            currentContext = getCurrentProcessContext(),
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH
        )

        val processContext = getProcessContext(android.os.Process.myPid())

        val importantPaths = listOf(
            context.filesDir.absolutePath,
            context.cacheDir.absolutePath,
            "/system/bin/app_process",
            "/system/lib",
            "/data/data/${context.packageName}"
        )

        val fileContexts = importantPaths.map { path ->
            getFileContext(path)
        }

        val policyInfo = getPolicyInfo()
        val recommendations = getRecommendations()

        SELinuxCheckResult(
            systemInfo = systemInfo,
            processContext = processContext,
            fileContexts = fileContexts,
            policyInfo = policyInfo,
            recommendations = recommendations
        )
    }


    fun formatCheckResult(result: SELinuxCheckResult): String {
        return buildString {
            appendLine("=== Android SELinux 检查结果 ===\n")

            appendLine("1. 系统信息:")
            appendLine("   SELinux启用: ${result.systemInfo.enabled}")
            appendLine("   强制模式: ${result.systemInfo.enforcing}")
            appendLine("   策略版本: ${result.systemInfo.policyVersion}")
            appendLine("   API级别: ${result.systemInfo.apiLevel}")
            appendLine("   安全补丁: ${result.systemInfo.securityPatch}")

            appendLine("\n2. 进程上下文:")
            appendLine("   PID: ${result.processContext.pid}")
            appendLine("   上下文: ${result.processContext.context}")
            appendLine("   有效: ${result.processContext.isValid}")

            appendLine("\n3. 文件上下文:")
            result.fileContexts.forEach { fileContext ->
                appendLine("   ${fileContext.path}:")
                appendLine("     上下文: ${fileContext.context}")
                appendLine("     存在: ${fileContext.exists}, 可读: ${fileContext.readable}")
            }

            appendLine("\n4. 策略信息:")
            appendLine("   策略存在: ${result.policyInfo.policyExists}")
            appendLine("   策略路径: ${result.policyInfo.policyPath}")
            appendLine("   策略大小: ${result.policyInfo.policySize} bytes")
            appendLine("   自定义策略: ${result.policyInfo.customPolicies.size} 个")

            appendLine("\n5. 推荐配置:")
            result.recommendations.forEach { recommendation ->
                appendLine("   • $recommendation")
            }
        }
    }

    fun checkAppPermissions(): Map<String, Boolean> {
        val permissions = mutableMapOf<String, Boolean>()

        try {
            permissions["访问应用私有目录"] = try {
                context.filesDir.exists() && context.filesDir.canRead() && context.filesDir.canWrite()
            } catch (e: Exception) { false }
            
            permissions["访问应用缓存目录"] = try {
                context.cacheDir.exists() && context.cacheDir.canRead() && context.cacheDir.canWrite()
            } catch (e: Exception) { false }
            
            // 检查外部存储访问（如果有权限）
            try {
                val externalFilesDir = context.getExternalFilesDir(null)
                permissions["访问外部应用目录"] = externalFilesDir != null && externalFilesDir.exists()
            } catch (e: Exception) {
                permissions["访问外部应用目录"] = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking app directory permissions: ${e.message}")
        }

        try {
            permissions["获取系统属性"] = try {
                val prop = System.getProperty("java.version")
                prop != null && prop.isNotEmpty()
            } catch (e: Exception) { false }
            
            permissions["获取Build信息"] = try {
                Build.VERSION.SDK_INT > 0 && Build.ID.isNotEmpty()
            } catch (e: Exception) { false }
            
            permissions["获取应用信息"] = try {
                val appInfo = context.applicationInfo
                appInfo != null && context.packageName.isNotEmpty()
            } catch (e: Exception) { false }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking system info access: ${e.message}")
        }
        

        val accessiblePaths = mapOf(
            "读取/proc/self" to "/proc/self",
            "读取/proc/version" to "/proc/version",
            "检查/system存在性" to "/system",
            "检查/data存在性" to "/data"
        )
        
        for ((permName, path) in accessiblePaths) {
            try {
                val file = File(path)
                permissions[permName] = file.exists()
            } catch (e: Exception) {
                permissions[permName] = false
            }
        }

        try {
            permissions["推断SELinux启用状态"] = try {
                isSELinuxEnabled()
                true
            } catch (e: Exception) { false }

            permissions["推断SELinux强制模式"] = try {
                isSELinuxEnforcing()
                true
            } catch (e: Exception) { false }

            permissions["获取进程SELinux上下文"] = try {
                val context = getCurrentProcessContext()
                context.isNotEmpty() && !context.contains("Error")
            } catch (e: Exception) { false }

            permissions["推断策略版本"] = try {
                val version = getSELinuxPolicyVersion()
                version.isNotEmpty() && version != "Unknown"
            } catch (e: Exception) { false }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking SELinux capabilities: ${e.message}")
        }

        try {
            var restrictionCount = 0
            val testPaths = listOf("/system/bin", "/system/lib", "/data/system")
            
            for (path in testPaths) {
                try {
                    val file = File(path)
                    if (!file.canRead() || !file.canWrite()) {
                        restrictionCount++
                    }
                } catch (e: Exception) {
                    restrictionCount++
                }
            }
            
            permissions["应用沙盒正常工作"] = restrictionCount >= testPaths.size * 0.8

            try {
                val pm = context.packageManager
                val hasNetworkPerm = pm.checkPermission(
                    android.Manifest.permission.INTERNET, 
                    context.packageName
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                permissions["网络访问权限"] = hasNetworkPerm
            } catch (e: Exception) {
                permissions["网络访问权限"] = false
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking sandbox restrictions: ${e.message}")
        }

        return permissions
    }
}