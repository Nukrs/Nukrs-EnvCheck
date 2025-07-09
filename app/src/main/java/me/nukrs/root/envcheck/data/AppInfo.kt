package me.nukrs.root.envcheck.data

import java.util.*

/**
 * 应用信息统一管理
 */
object AppInfo {
    
    // 应用版本信息
    const val APP_VERSION = "1.1.1"
    const val VERSION_CODE = 1
    
    // 作者信息
    const val AUTHOR_NAME = "Nukrs"
    const val AUTHOR_EMAIL = "me@nukrs.com"
    const val AUTHOR_WEBSITE = "https://nukrs.com"
    const val AUTHOR_GITHUB = "https://github.com/Nukrs"
    
    // 项目信息
    const val PROJECT_NAME = "Nukrs EnvCheck"
    const val PROJECT_DESCRIPTION = "一款Android环境安全检测工具，帮助用户检测设备的安全状态和潜在风险。"
    const val PROJECT_GITHUB = "https://github.com/Nukrs/Nukrs-EnvCheck/"
    const val PROJECT_TELEGRAM = "https://t.me/nukrsenvcheck"
    const val PROJECT_LICENSE = "MIT License"
    
    const val BUILD_TIME = "2025-07-10 00:20:00"
    const val BUILD_DATE = "2025-07-10"
    
    // 获取完整的版本信息
    fun getFullVersionInfo(): String {
        return "$PROJECT_NAME v$APP_VERSION (Build $VERSION_CODE)"
    }
    
    // 获取作者完整信息
    fun getAuthorInfo(): String {
        return "$AUTHOR_NAME\n$AUTHOR_EMAIL\n$AUTHOR_WEBSITE"
    }
    
    // 获取版权信息
    fun getCopyrightInfo(): String {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return "© $currentYear $AUTHOR_NAME. All rights reserved."
    }
    
    // 获取构建信息
    fun getBuildInfo(): String {
        return "Built on $BUILD_TIME"
    }
}