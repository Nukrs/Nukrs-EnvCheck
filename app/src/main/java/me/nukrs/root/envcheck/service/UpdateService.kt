package me.nukrs.root.envcheck.service

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class VersionInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val isForceUpdate: Boolean = false,
    val minSupportedVersion: Int = 1
)

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val versionInfo: VersionInfo? = null,
    val currentVersion: String,
    val currentVersionInfo: VersionInfo? = null,
    val error: String? = null
)

class UpdateService(private val context: Context) {
    
    companion object {
        private const val UPDATE_CHECK_URL = "https://envcheckupdate.tobyzilo.workers.dev/envcheckupdate"
        private const val TIMEOUT_MS = 10000
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion()
            val currentVersionCode = getCurrentVersionCode()
            
            val latestVersionInfo = fetchVersionInfo()
            
            // 使用语义化版本比较
            val hasUpdate = compareSemanticVersions(currentVersion, latestVersionInfo.versionName) < 0
            
            val currentVersionInfo = if (!hasUpdate) {
                latestVersionInfo
            } else {
                try {
                    fetchVersionInfo(currentVersion)
                } catch (e: Exception) {
                    VersionInfo(
                        versionName = currentVersion,
                        versionCode = currentVersionCode,
                        downloadUrl = "",
                        releaseNotes = "当前版本",
                        isForceUpdate = false,
                        minSupportedVersion = 1
                    )
                }
            }
            
            UpdateCheckResult(
                hasUpdate = hasUpdate,
                versionInfo = if (hasUpdate) latestVersionInfo else null,
                currentVersion = currentVersion,
                currentVersionInfo = currentVersionInfo
            )
        } catch (e: Exception) {
            UpdateCheckResult(
                hasUpdate = false,
                currentVersion = getCurrentVersion(),
                error = e.message ?: "检查更新失败"
            )
        }
    }
    
    private suspend fun fetchVersionInfo(): VersionInfo = withContext(Dispatchers.IO) {
        val url = URL(UPDATE_CHECK_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "NukrsEnvCheck/${getCurrentVersion()}")
                setRequestProperty("Accept", "application/json")
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<VersionInfo>(response)
            } else {
                throw Exception("HTTP $responseCode: ${connection.responseMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    private suspend fun fetchVersionInfo(version: String): VersionInfo = withContext(Dispatchers.IO) {
        val url = URL("$UPDATE_CHECK_URL?version=$version")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "NukrsEnvCheck/${getCurrentVersion()}")
                setRequestProperty("Accept", "application/json")
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<VersionInfo>(response)
            } else {
                throw Exception("HTTP $responseCode: ${connection.responseMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
    
    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            1
        }
    }
    
    fun isForceUpdateRequired(versionInfo: VersionInfo): Boolean {
        val currentVersionCode = getCurrentVersionCode()
        return versionInfo.isForceUpdate || currentVersionCode < versionInfo.minSupportedVersion
    }
    

    private fun compareSemanticVersions(version1: String, version2: String): Int {

        val cleanVersion1 = version1.removePrefix("v").trim()
        val cleanVersion2 = version2.removePrefix("v").trim()
        
        // 分割版本号
        val parts1 = cleanVersion1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = cleanVersion2.split(".").map { it.toIntOrNull() ?: 0 }


        val normalizedParts1 = (parts1 + listOf(0, 0, 0)).take(3)
        val normalizedParts2 = (parts2 + listOf(0, 0, 0)).take(3)

        for (i in 0..2) {
            when {
                normalizedParts1[i] < normalizedParts2[i] -> return -1
                normalizedParts1[i] > normalizedParts2[i] -> return 1
            }
        }
        
        // 所有部分都相等
        return 0
    }
}