package me.nukrs.root.envcheck.service

import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

class AndroidSecurityDetector {
    
    // 危险应用列表 - 包含各种破解工具、Hook框架、隐私绕过工具等
    private val dangerousApps = setOf(
        "com.silverlab.app.deviceidchanger.free", "me.bingyue.IceCore", "com.modify.installer", "o.dyoo",
        "com.zhufucdev.motion_emulator", "me.simpleHook", "com.cshlolss.vipkill", "io.github.a13e300.ksuwebui",
        "com.demo.serendipity", "me.iacn.biliroaming", "me.teble.xposed.autodaily", "com.example.ourom",
        "dialog.box", "top.hookvip.pro", "tornaco.apps.shortx", "moe.fuqiuluo.portal",
        "com.github.tianma8023.xposed.smscode", "moe.shizuku.privileged.api", "lin.xposed", "com.lerist.fakelocation",
        "com.yxer.packageinstalles", "xzr.hkf", "web1n.stopapp", "Hook.JiuWu.Xp",
        "io.github.qauxv", "com.houvven.guise", "xzr.konabess", "com.xayah.databackup.foss",
        "com.sevtinge.hyperceiler", "github.tornaco.android.thanos", "nep.timeline.freezer", "cn.geektang.privacyspace",
        "org.lsposed.lspatch", "zako.zako.zako", "com.topmiaohan.hidebllist", "com.tsng.hidemyapplist",
        "com.tsng.pzyhrx.hma", "com.rifsxd.ksunext", "com.byyoung.setting", "com.omarea.vtools",
        "cn.myflv.noactive", "io.github.vvb2060.magisk", "com.bug.hookvip", "com.junge.algorithmAidePro",
        "bin.mt.termex", "tmgp.atlas.toolbox", "com.wn.app.np", "com.sukisu.ultra",
        "ru.maximoff.apktool", "top.bienvenido.saas.i18n", "com.syyf.quickpay", "tornaco.apps.shortx.ext",
        "com.mio.kitchen", "eu.faircode.xlua", "com.dna.tools", "cn.myflv.monitor.noactive",
        "com.yuanwofei.cardemulator.pro", "com.termux", "com.suqi8.oshin", "me.hd.wauxv",
        "have.fun", "miko.client", "com.kooritea.fcmfix", "com.twifucker.hachidori",
        "com.luckyzyx.luckytool", "com.padi.hook.hookqq", "cn.lyric.getter", "com.parallelc.micts",
        "me.plusne", "com.hchen.appretention", "com.hchen.switchfreeform", "name.monwf.customiuizer",
        "com.houvven.impad", "cn.aodlyric.xiaowine", "top.sacz.timtool", "nep.timeline.re_telegram",
        "com.fuck.android.rimet", "cn.kwaiching.hook", "cn.android.x", "cc.aoeiuv020.iamnotdisabled.hook",
        "vn.kwaiching.tao", "com.nnnen.plusne", "com.fkzhang.wechatxposed", "one.yufz.hmspush",
        "cn.fuckhome.xiaowine", "com.fankes.tsbattery", "com.rifsxd.ksunext", "com.rkg.IAMRKG",
        "me.gm.cleaner", "moe.shizuku.redirectstorage", "com.ddm.qute", "io.github.vvb2060.magisk",
        "kk.dk.anqu", "com.qq.qcxm", "com.wei.vip", "dknb.con", "dknb.coo8", "com.tencent.jingshi",
        "com.tencent.JYNB", "com.apocalua.run", "com.coderstory.toolkit", "com.didjdk.adbhelper",
        "org.lsposed.manager", "io.github.Retmon403.oppotheme", "com.fankes.enforcehighrefreshrate",
        "es.chiteroman.bootloaderspoofer", "com.hchai.rescueplan",
        // 原有的危险应用
        "com.topjohnwu.magisk", "com.sukisu.ultra"
    )
    
    /**
     * 枚举Android数据目录
     * 使用零宽空格绕过访问限制
     */
    private fun enumerateAndroidDataDirs(): Set<String> {
        val basePath = "/sdcard/Android/data/"
        // 零宽空格字符用于绕过访问限制
        val bypassChar = "\u200B" // Unicode零宽空格
        val bypassPath = basePath.substring(0, basePath.length - 1) + bypassChar + basePath.last()
        
        val dirs = mutableSetOf<String>()
        
        try {
            val process = Runtime.getRuntime().exec("ls -l $bypassPath")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            reader.useLines { lines ->
                lines.forEach { line ->
                    // 简化的目录名解析
                    val dirName = line.substringAfterLast(' ').trimEnd('/')
                    if (dirName.isNotEmpty() && dirName != "." && dirName != "..") {
                        dirs.add(dirName)
                    }
                }
            }
            
            process.waitFor()
        } catch (e: Exception) {
            // 静默处理异常，避免暴露检测逻辑
        }
        
        return dirs
    }

    fun isDangerousEnvironment(): Boolean {
        val existingDirs = enumerateAndroidDataDirs()
        
        // 检查是否有任何危险应用存在
        return dangerousApps.any { packageName ->
            existingDirs.contains(packageName)
        }
    }

    fun getDetectedDangerousApps(): List<String> {
        val existingDirs = enumerateAndroidDataDirs()
        
        return dangerousApps.filter { packageName ->
            existingDirs.contains(packageName)
        }
    }

    fun performSecurityCheck(): SecurityCheckResult {
        val detectedApps = getDetectedDangerousApps()
        
        return SecurityCheckResult(
            isDangerous = detectedApps.isNotEmpty(),
            detectedAppsCount = detectedApps.size,
            detectedApps = detectedApps
        )
    }
}

data class SecurityCheckResult(
    val isDangerous: Boolean,
    val detectedAppsCount: Int,
    val detectedApps: List<String>
)