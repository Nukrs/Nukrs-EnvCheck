package me.nukrs.root.envcheck.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import me.nukrs.root.envcheck.model.CheckStatus
import me.nukrs.root.envcheck.model.CheckDetails
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import javax.net.ssl.SSLContext
import java.security.cert.X509Certificate

/**
 * Android网络安全检测器
 * 负责检测网络配置和证书安全
 */
class AndroidNetworkSecurityChecker(private val context: Context) {
    
    /**
     * 执行网络安全检测
     * 检测网络配置和证书安全
     */
    suspend fun performNetworkSecurityCheck(): Flow<Pair<CheckStatus, CheckDetails?>> = flow {
        emit(Pair(CheckStatus.RUNNING, null))
        try {
            delay(1500)
            
            val networkResults = mutableListOf<Boolean>()
            val warningChecks = mutableListOf<String>()
            val detailedInfo = mutableListOf<String>()
            
            // 1. 网络连接状态检查
            val networkConnected = checkNetworkConnectivity()
            networkResults.add(networkConnected.first)
            if (networkConnected.second.isNotEmpty()) {
                detailedInfo.add("网络连接: ${networkConnected.second}")
            }
            
            // 2. WiFi安全检查
            val wifiSecurity = checkWiFiSecurity()
            networkResults.add(wifiSecurity.first)
            if (wifiSecurity.second.isNotEmpty()) {
                detailedInfo.add("WiFi安全: ${wifiSecurity.second}")
                if (!wifiSecurity.first) {
                    warningChecks.add("WiFi安全风险: ${wifiSecurity.second}")
                }
            }
            
            // 3. 代理和VPN检查
            val proxyVpnCheck = checkProxyAndVPN()
            networkResults.add(proxyVpnCheck.first)
            if (proxyVpnCheck.second.isNotEmpty()) {
                detailedInfo.add("代理/VPN: ${proxyVpnCheck.second}")
                if (!proxyVpnCheck.first) {
                    warningChecks.add("检测到代理或VPN连接")
                }
            }
            
            // 4. DNS安全检查
            val dnsCheck = checkDNSSecurity()
            networkResults.add(dnsCheck.first)
            if (dnsCheck.second.isNotEmpty()) {
                detailedInfo.add("DNS配置: ${dnsCheck.second}")
            }
            
            // 5. SSL/TLS配置检查
            val sslCheck = checkSSLConfiguration()
            networkResults.add(sslCheck.first)
            if (sslCheck.second.isNotEmpty()) {
                detailedInfo.add("SSL/TLS: ${sslCheck.second}")
            }
            
            // 6. 网络接口检查
            val interfaceCheck = checkNetworkInterfaces()
            networkResults.add(interfaceCheck.first)
            if (interfaceCheck.second.isNotEmpty()) {
                detailedInfo.add("网络接口: ${interfaceCheck.second}")
            }
            
            // 7. 证书存储检查
            val certCheck = checkCertificateStore()
            networkResults.add(certCheck.first)
            if (certCheck.second.isNotEmpty()) {
                detailedInfo.add("证书存储: ${certCheck.second}")
                if (!certCheck.first) {
                    warningChecks.add("证书存储异常")
                }
            }
            
            val passedChecks = networkResults.count { it }
            val totalChecks = networkResults.size
            val checkNames = listOf(
                "网络连接状态", "WiFi安全配置", "代理/VPN检测", 
                "DNS安全配置", "SSL/TLS配置", "网络接口检查", "证书存储检查"
            )
            
            // 构建详细推荐信息
            val recommendationBuilder = StringBuilder()
            when {
                passedChecks == totalChecks && warningChecks.isEmpty() -> {
                    recommendationBuilder.append("网络安全检测全部通过，网络环境安全可靠。")
                }
                passedChecks >= totalChecks * 0.8 -> {
                    recommendationBuilder.append("网络安全检测大部分通过，存在轻微安全风险。")
                }
                passedChecks >= totalChecks * 0.6 -> {
                    recommendationBuilder.append("网络安全检测部分通过，建议检查网络配置。")
                }
                else -> {
                    recommendationBuilder.append("网络安全检测发现多项问题，存在较高安全风险。")
                }
            }
            
            // 添加详细信息
            if (detailedInfo.isNotEmpty()) {
                recommendationBuilder.append("\n\n详细信息:")
                detailedInfo.forEach { info ->
                    recommendationBuilder.append("\n• $info")
                }
            }
            
            // 添加安全建议
            if (warningChecks.isNotEmpty()) {
                recommendationBuilder.append("\n\n安全建议:")
                if (warningChecks.any { it.contains("WiFi") }) {
                    recommendationBuilder.append("\n• 避免连接不安全的WiFi网络")
                }
                if (warningChecks.any { it.contains("代理") || it.contains("VPN") }) {
                    recommendationBuilder.append("\n• 检查是否有未授权的代理或VPN连接")
                }
                if (warningChecks.any { it.contains("证书") }) {
                    recommendationBuilder.append("\n• 检查系统证书存储是否被篡改")
                }
            }
            
            val details = CheckDetails(
                passedChecks = checkNames.filterIndexed { index, _ -> networkResults.getOrNull(index) == true },
                failedChecks = checkNames.filterIndexed { index, _ -> networkResults.getOrNull(index) == false },
                warningChecks = warningChecks,
                score = "${(passedChecks.toFloat() / totalChecks * 100).toInt()}%",
                recommendation = recommendationBuilder.toString()
            )
            
            when {
                passedChecks == totalChecks && warningChecks.isEmpty() -> emit(Pair(CheckStatus.PASSED, details))
                passedChecks >= totalChecks * 0.6 || warningChecks.isNotEmpty() -> emit(Pair(CheckStatus.WARNING, details))
                else -> emit(Pair(CheckStatus.FAILED, details))
            }
            
        } catch (e: Exception) {
            val details = CheckDetails(
                passedChecks = emptyList(),
                failedChecks = listOf("网络安全检测异常: ${e.message}"),
                warningChecks = emptyList(),
                score = "0%",
                recommendation = "网络安全检测过程中发生异常，请检查设备兼容性。\n错误详情: ${e.message}"
            )
            emit(Pair(CheckStatus.FAILED, details))
        }
    }
    
    /**
     * 检查网络连接状态
     * 检测网络可用性和连接类型
     */
    private fun checkNetworkConnectivity(): Pair<Boolean, String> {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            if (networkCapabilities == null) {
                return Pair(false, "无网络连接")
            }
            
            val connectionType = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                else -> "未知类型"
            }
            
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            
            val status = if (isValidated && hasInternet) "正常" else "受限"
            Pair(isValidated && hasInternet, "$connectionType ($status)")
        } catch (e: Exception) {
            Pair(false, "检测异常: ${e.message}")
        }
    }
    
    /**
     * 检查WiFi安全配置
     * 检测WiFi加密类型和安全性
     */
    private fun checkWiFiSecurity(): Pair<Boolean, String> {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            
            if (wifiInfo.networkId == -1) {
                return Pair(true, "未连接WiFi")
            }
            
            val ssid = wifiInfo.ssid?.replace("\"", "") ?: "未知网络"
            val bssid = wifiInfo.bssid ?: "未知"
            
            // 检查WiFi安全类型（需要权限，可能无法获取详细信息）
            val securityInfo = try {
                val configuredNetworks = wifiManager.configuredNetworks
                val currentConfig = configuredNetworks?.find { it.networkId == wifiInfo.networkId }
                when {
                    currentConfig?.allowedKeyManagement?.get(1) == true -> "WPA/WPA2" // WPA_PSK
                    currentConfig?.allowedKeyManagement?.get(2) == true -> "WPA2-Enterprise" // WPA_EAP
                    currentConfig?.allowedKeyManagement?.get(0) == true -> "开放网络" // NONE
                    else -> "未知加密"
                }
            } catch (e: Exception) {
                "无法检测加密类型"
            }
            
            val isSecure = !securityInfo.contains("开放") && !securityInfo.contains("未知")
            Pair(isSecure, "$ssid ($securityInfo)")
        } catch (e: Exception) {
            Pair(true, "WiFi检测异常")
        }
    }
    
    /**
     * 检查代理和VPN配置
     * 检测系统代理设置和VPN连接
     */
    private fun checkProxyAndVPN(): Pair<Boolean, String> {
        return try {
            val details = mutableListOf<String>()
            var hasRisk = false
            
            // 检查系统代理设置
            val proxyHost = System.getProperty("http.proxyHost")
            val proxyPort = System.getProperty("http.proxyPort")
            val httpsProxyHost = System.getProperty("https.proxyHost")
            val httpsProxyPort = System.getProperty("https.proxyPort")
            
            if (!proxyHost.isNullOrEmpty() || !proxyPort.isNullOrEmpty()) {
                details.add("HTTP代理: $proxyHost:$proxyPort")
                hasRisk = true
            }
            
            if (!httpsProxyHost.isNullOrEmpty() || !httpsProxyPort.isNullOrEmpty()) {
                details.add("HTTPS代理: $httpsProxyHost:$httpsProxyPort")
                hasRisk = true
            }
            
            // 检查VPN连接
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val hasVpn = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            
            if (hasVpn) {
                details.add("检测到VPN连接")
                hasRisk = true
            }
            
            if (details.isEmpty()) {
                details.add("无代理或VPN")
            }
            
            Pair(!hasRisk, details.joinToString(", "))
        } catch (e: Exception) {
            Pair(true, "检测异常")
        }
    }
    
    /**
     * 检查DNS安全配置
     * 检测DNS设置和安全性
     */
    private fun checkDNSSecurity(): Pair<Boolean, String> {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            
            val dnsServers = linkProperties?.dnsServers ?: emptyList()
            if (dnsServers.isEmpty()) {
                return Pair(false, "无DNS服务器配置")
            }
            
            val dnsInfo = dnsServers.take(3).map { it.hostAddress }.joinToString(", ")
            
            // 检查是否使用了常见的安全DNS服务器
            val secureDnsServers = setOf(
                "8.8.8.8", "8.8.4.4", // Google DNS
                "1.1.1.1", "1.0.0.1", // Cloudflare DNS
                "208.67.222.222", "208.67.220.220", // OpenDNS
                "9.9.9.9", "149.112.112.112" // Quad9 DNS
            )
            
            val hasSecureDns = dnsServers.any { secureDnsServers.contains(it.hostAddress) }
            val status = if (hasSecureDns) "安全DNS" else "默认DNS"
            
            Pair(true, "$status ($dnsInfo)")
        } catch (e: Exception) {
            Pair(true, "DNS检测异常")
        }
    }
    
    /**
     * 检查SSL/TLS配置
     * 检测SSL/TLS协议支持和安全性
     */
    private fun checkSSLConfiguration(): Pair<Boolean, String> {
        return try {
            val sslContext = SSLContext.getDefault()
            val supportedProtocols = sslContext.supportedSSLParameters.protocols
            
            val tlsVersions = supportedProtocols.filter { it.startsWith("TLS") }
            val hasModernTLS = tlsVersions.any { it.contains("1.2") || it.contains("1.3") }
            
            val protocolInfo = tlsVersions.takeLast(3).joinToString(", ")
            val status = if (hasModernTLS) "支持现代TLS" else "TLS版本较旧"
            
            Pair(hasModernTLS, "$status ($protocolInfo)")
        } catch (e: Exception) {
            Pair(true, "SSL检测异常")
        }
    }
    
    /**
     * 检查网络接口
     * 检测网络接口配置和异常接口
     */
    private fun checkNetworkInterfaces(): Pair<Boolean, String> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val activeInterfaces = interfaces.filter { it.isUp && !it.isLoopback }
            
            val interfaceInfo = activeInterfaces.map { iface ->
                val addresses = iface.inetAddresses.toList()
                    .filter { !it.isLoopbackAddress }
                    .map { it.hostAddress }
                    .take(2)
                    .joinToString(", ")
                "${iface.name}($addresses)"
            }.take(3).joinToString("; ")
            
            // 检查是否有可疑的虚拟网络接口
            val suspiciousInterfaces = activeInterfaces.filter { iface ->
                val name = iface.name.lowercase()
                name.contains("tun") || name.contains("tap") || 
                name.contains("vpn") || name.contains("ppp")
            }
            
            val hasSuspicious = suspiciousInterfaces.isNotEmpty()
            val status = if (hasSuspicious) "检测到虚拟接口" else "正常接口"
            
            Pair(!hasSuspicious, "$status: $interfaceInfo")
        } catch (e: Exception) {
            Pair(true, "接口检测异常")
        }
    }
    
    /**
     * 检查证书存储
     * 检测系统证书存储和用户添加的证书
     */
    private fun checkCertificateStore(): Pair<Boolean, String> {
        return try {
            val systemCertDir = File("/system/etc/security/cacerts")
            val userCertDir = File("/data/misc/user/0/cacerts-added")
            
            val systemCertCount = try {
                systemCertDir.listFiles()?.size ?: 0
            } catch (e: Exception) {
                0
            }
            
            val userCertCount = try {
                userCertDir.listFiles()?.size ?: 0
            } catch (e: Exception) {
                0
            }
            
            val details = mutableListOf<String>()
            if (systemCertCount > 0) {
                details.add("系统证书: ${systemCertCount}个")
            }
            if (userCertCount > 0) {
                details.add("用户证书: ${userCertCount}个")
            }
            
            // 用户添加的证书可能存在安全风险
            val hasRisk = userCertCount > 0
            val status = if (hasRisk) "存在用户证书" else "仅系统证书"
            
            if (details.isEmpty()) {
                details.add("无法访问证书存储")
            }
            
            Pair(!hasRisk, "$status (${details.joinToString(", ")})")
        } catch (e: Exception) {
            Pair(true, "证书检测异常")
        }
    }
}