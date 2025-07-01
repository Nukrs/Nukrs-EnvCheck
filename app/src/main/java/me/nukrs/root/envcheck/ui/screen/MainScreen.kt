package me.nukrs.root.envcheck.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import me.nukrs.root.envcheck.ui.components.CheckItemCard
import me.nukrs.root.envcheck.viewmodel.MainViewModel
import me.nukrs.root.envcheck.model.CheckItem
import me.nukrs.root.envcheck.model.CheckStatus
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val checkItems by viewModel.checkItemsList.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    
    // 动画状态
    var showCelebration by remember { mutableStateOf(false) }
    var showFailureEffect by remember { mutableStateOf(false) }
    
    // 展开状态管理
    var expandedItems by remember { mutableStateOf(setOf<String>()) }
    
    // 检查是否全部通过
    val allPassed = checkItems.all { it.status == CheckStatus.PASSED }
    val hasResults = checkItems.any { it.status != CheckStatus.PENDING }
    
    // 监听检测结果变化
    LaunchedEffect(checkItems, isChecking) {
        if (!isChecking && hasResults) {
            if (allPassed) {
                showCelebration = true
            } else {
                showFailureEffect = true
            }
            // 自动展开所有详情
            delay(500) // 稍微延迟以确保动画效果先显示
            expandedItems = checkItems.map { it.id }.toSet()
        }
    }
    
    // 一键通过功能
    fun simulateAllPass() {
        viewModel.simulateAllPass()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nukrs EnvCheck",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "设备环境安全检测工具",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // 操作按钮区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.startAllChecks() },
                modifier = Modifier.weight(1f),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("开始测试")
            }
            
            // 一键通过按钮
            SimulatePassButton(
                onClick = { simulateAllPass() },
                enabled = !isChecking,
                modifier = Modifier.weight(1f)
            )
            
            OutlinedButton(
                onClick = { viewModel.resetAllChecks() },
                modifier = Modifier.weight(0.8f),
                enabled = !isChecking
            ) {
                Text("重置")
            }
        }
        
        // 检测项目列表
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(checkItems) { item ->
                CheckItemCard(
                    checkItem = item,
                    onItemClick = { viewModel.startCheck(item.id) },
                    enabled = !isChecking,
                    expanded = expandedItems.contains(item.id),
                    onExpandedChange = { expanded ->
                        expandedItems = if (expanded) {
                            expandedItems + item.id
                        } else {
                            expandedItems - item.id
                        }
                    }
                )
            }
            
            // 底部汇总卡片
            item {
                FailedChecksSummaryCard(
                    checkItems = checkItems,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            // Root建议模块
            item {
                RootSuggestionsCard(
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            // 作者信息卡片
            item {
                AuthorInfoCard(
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        
        // 庆祝动画效果
        if (showCelebration) {
            CelebrationEffect(
                onAnimationEnd = { showCelebration = false }
            )
        }
        
        // 失败效果
        if (showFailureEffect) {
            FailureEffect(
                onAnimationEnd = { showFailureEffect = false }
            )
        }
    }
}

@Composable
fun RootSuggestionsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Root 使用建议",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            
            // 建议列表
            val suggestions = listOf(
                "谨慎授予Root权限，合理利用app profile，请确保su仅对可信应用开放：开源的高star项目，付费的用户基数大的商业软件",
                "不要在生产环境或重要设备上Root，除非你一直十分谨慎",
                "定期备份重要数据，防止系统损坏",
                "不要执行加密的elf或者shell文件，除非你能100%保证它是安全的"
            )
            
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                    )
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 底部提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "💡 提示：Root权限虽然提供了更多自由度，但也带来了安全风险，请根据实际需求谨慎使用，并始终保持安全意识。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun AuthorInfoCard(
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "关于作者",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // 作者信息
            Text(
                text = "开发者: Nukrs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 个人网站
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        uriHandler.openUri("https://nukrs.com")
                    }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "个人网站: nukrs.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 软件版本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "版本: v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "构建: 2025.07.01",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SimulatePassButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Text("一键通过")
    }
}

@Composable
fun CelebrationEffect(
    onAnimationEnd: () -> Unit
) {
    val animationDuration = 2000
    val hapticFeedback = LocalHapticFeedback.current
    
    // 动画进度
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(animationDuration, easing = LinearEasing),
        finishedListener = { onAnimationEnd() }
    )
    
    // 文字缩放动画
    val textScale by animateFloatAsState(
        targetValue = if (animatedProgress < 0.2f) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    // 震动效果 - 仅在动画开始时触发一次
    LaunchedEffect(Unit) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(300)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        // 成功文字
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (animatedProgress < 0.8f) 1f else 1f - (animatedProgress - 0.8f) / 0.2f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.scale(textScale)
                )
                Text(
                    text = "全部检测通过！",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.scale(textScale)
                )
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.scale(textScale)
                )
            }
        }
    }
}

@Composable
fun FailureEffect(
    onAnimationEnd: () -> Unit
) {
    val animationDuration = 2000
    
    // 震动动画
    val shakeOffset by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = LinearEasing
        ),
        finishedListener = { onAnimationEnd() }
    )
    
    // 创建震动效果
    val shakeAnimation = remember {
        infiniteRepeatable(
            animation = keyframes {
                durationMillis = 100
                0f at 0
                10f at 25
                -10f at 50
                10f at 75
                0f at 100
            },
            repeatMode = RepeatMode.Restart
        )
    }
    
    val shakeX by animateFloatAsState(
        targetValue = 0f,
        animationSpec = shakeAnimation
    )
    
    LaunchedEffect(Unit) {
        delay(animationDuration.toLong())
        onAnimationEnd()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .offset(x = shakeX.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .alpha(0.9f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚠️ 检测未完全通过",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请查看详细检测结果",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}



@Composable
fun FailedChecksSummaryCard(
    checkItems: List<CheckItem>,
    modifier: Modifier = Modifier
) {
    val failedItems = checkItems.filter { 
        it.status == CheckStatus.FAILED || it.status == CheckStatus.WARNING 
    }
    
    val passedItems = checkItems.filter { it.status == CheckStatus.PASSED }
    val hasResults = checkItems.any { it.status != CheckStatus.PENDING }
    val allPassed = checkItems.all { it.status == CheckStatus.PASSED } && hasResults
    
    val allFailedChecks = failedItems.flatMap { item ->
        item.details?.failedChecks?.map { "${item.title}: $it" } ?: emptyList()
    }
    
    val allWarningChecks = failedItems.flatMap { item ->
        item.details?.warningChecks?.map { "${item.title}: $it" } ?: emptyList()
    }
    
    // 显示汇总卡片：全部通过时显示成功卡片，否则显示失败/警告卡片
    if (allPassed || allFailedChecks.isNotEmpty() || allWarningChecks.isNotEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (allPassed) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (allPassed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (allPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (allPassed) "✅ 安全检查全部通过" else "安全检查汇总报告",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (allPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // 标题水印
                    if (allPassed) {
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp)
                        )
                    }
                }
                
                // 全部通过时显示成功信息
                if (allPassed) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🎉 恭喜！所有安全检查项目均已通过",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // 模拟效果水印
                        Text(
                            text = "模拟效果",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp)
                        )
                    }
                    
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✅ 已通过的检查项明细 (${passedItems.size}项)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // 模拟数据水印
                        Text(
                            text = "模拟数据",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                        )
                    }
                    
                    passedItems.forEach { item ->
                        val passedChecks = item.details?.passedChecks ?: emptyList()
                        passedChecks.forEach { check ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                                )
                                Text(
                                    text = "${item.title}: $check",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                if (allFailedChecks.isNotEmpty()) {
                    Text(
                        text = "❌ 未通过的检查项明细 (${allFailedChecks.size}项)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    allFailedChecks.forEach { check ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                            )
                            Text(
                                text = check,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    if (allWarningChecks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                
                if (allWarningChecks.isNotEmpty()) {
                    Text(
                        text = "⚠️ 警告的检查项明细 (${allWarningChecks.size}项)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    allWarningChecks.forEach { check ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9800),
                                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                            )
                            Text(
                                text = check,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // 总体安全建议
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "🔒 总体安全建议",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = when {
                                    allPassed -> "🏆 设备安全检查全部通过，符合金融级安全标准！您的设备具备完整的安全防护能力，可以安全地进行各类敏感操作。"
                                    allFailedChecks.size > 3 -> "设备存在严重安全风险，强烈建议更换为支持完整安全功能的设备，或联系技术支持进行安全加固。"
                                    allFailedChecks.isNotEmpty() -> "设备存在一定安全风险，建议根据上述明细进行相应的安全配置调整。"
                                    allWarningChecks.isNotEmpty() -> "设备基本安全，但建议关注警告项目以提升整体安全性。"
                                    else -> "设备安全检查全部通过，符合金融级安全标准。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // 多个水印效果
                        if (allPassed) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "演示模式",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = "仅供测试",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    text = "模拟结果",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                     }
                 }
            }
        }
    }
}