package me.nukrs.root.envcheck.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.viewmodel.compose.viewModel
import me.nukrs.root.envcheck.ui.components.CheckItemCard
import me.nukrs.root.envcheck.viewmodel.MainViewModel
import me.nukrs.root.envcheck.model.CheckItem
import me.nukrs.root.envcheck.model.CheckStatus
import me.nukrs.root.envcheck.data.AppInfo
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
import me.nukrs.root.envcheck.viewmodel.UpdateViewModel
import me.nukrs.root.envcheck.ui.components.UpdateDialog
import androidx.compose.material.icons.filled.Update
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel()
) {
    val checkItems by viewModel.checkItemsList.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    
    // 滚动状态
    val listState = rememberLazyListState()
    
    // 更新相关状态
    val updateState by updateViewModel.updateState.collectAsState()
    val showUpdateDialog by updateViewModel.showUpdateDialog.collectAsState()
    val updateResult by updateViewModel.updateResult.collectAsState()
    
    // 免责声明弹窗状态
    var showDisclaimerDialog by remember { mutableStateOf(true) }
    var disclaimerCountdown by remember { mutableStateOf(3) }
    var canDismissDisclaimer by remember { mutableStateOf(false) }
    
    // 获取当前应用版本
    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    // 当前时间状态
    var currentTime by remember { mutableStateOf("") }
    
    // 更新时间
    LaunchedEffect(Unit) {
        while (true) {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTime = formatter.format(Date())
            delay(60000) // 每分钟更新一次
        }
    }
    
    // 计算标题栏收缩状态
    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
        }
    }
    
    // 启动时自动检查更新
    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdates()
    }
    
    // 免责声明倒计时
    LaunchedEffect(showDisclaimerDialog) {
        if (showDisclaimerDialog && disclaimerCountdown > 0) {
            while (disclaimerCountdown > 0) {
                delay(1000)
                disclaimerCountdown--
            }
            canDismissDisclaimer = true
        }
    }
    
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
    

    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        // 主内容区域
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (isCollapsed) 80.dp else 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 完整标题区域（仅在未收缩时显示）
            if (!isCollapsed) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(32.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Nukrs EnvCheck",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "🛡️ 娱乐化设备检测工具",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        
            // 操作按钮区域
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isCollapsed) 8.dp else 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 开始检测按钮
                    Button(
                        onClick = { viewModel.startAllChecks() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = !isChecking,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "检测中",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "开始检测",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // 重置按钮
                    OutlinedButton(
                        onClick = { viewModel.resetAllChecks() },
                        modifier = Modifier
                            .weight(0.6f)
                            .height(56.dp),
                        enabled = !isChecking,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "重置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        
            // 安全检测综合评价
            if (hasResults) {
                item {
                    SecurityResultCard(
                        checkItems = checkItems,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
                
            // 检测项目列表
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
            
            // Root建议模块
            item {
                RootSuggestionsCard(
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
            
            // 关于项目卡片
            item {
                ProjectInfoCard(
                    modifier = Modifier.padding(top = 20.dp),
                    onCheckUpdate = { updateViewModel.checkForUpdates() }
                )
            }
            
            // 作者信息卡片
            item {
                AuthorInfoCard(
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }
        
        // 顶部收缩标题栏
        if (isCollapsed) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nukrs EnvCheck",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = currentTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.zIndex(1f),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
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
    
    // 更新对话框
    if (showUpdateDialog) {
        UpdateDialog(
            updateState = updateState,
            currentVersion = currentVersion,
            currentVersionInfo = updateResult?.currentVersionInfo,
            onDismiss = { updateViewModel.dismissUpdateDialog() }
        )
    }
    
    // 免责声明对话框
    if (showDisclaimerDialog) {
        DisclaimerDialog(
            countdown = disclaimerCountdown,
            canDismiss = canDismissDisclaimer,
            onDismiss = { 
                showDisclaimerDialog = false
                disclaimerCountdown = 3
                canDismissDisclaimer = false
            }
        )
    }
}

@Composable
fun RootSuggestionsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
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
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp)
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
                text = "开发者: ${AppInfo.AUTHOR_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 个人网站
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        uriHandler.openUri(AppInfo.AUTHOR_WEBSITE)
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
                    text = "个人网站: ${AppInfo.AUTHOR_WEBSITE.removePrefix("https://")}",
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
                    text = "版本: v${AppInfo.APP_VERSION}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "构建: ${AppInfo.BUILD_TIME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DisclaimerDialog(
    countdown: Int,
    canDismiss: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "⚠️ 重要声明",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        text = {
            Column {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "本检测系娱乐，如果您有异议，那一定您是对的。",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "• 本应用仅供娱乐和学习目的\n• 检测结果不代表设备真实安全状态\n• 请勿将检测结果用于任何商业或安全决策",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                enabled = canDismiss,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (!canDismiss) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (canDismiss) "我已知晓" else "请等待 ${countdown}s",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp
    )
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
fun SecurityResultCard(
    checkItems: List<CheckItem>,
    modifier: Modifier = Modifier
) {
    val hasResults = checkItems.any { it.status != CheckStatus.PENDING }
    val allPassed = checkItems.all { it.status == CheckStatus.PASSED } && hasResults
    val failedCount = checkItems.count { it.status == CheckStatus.FAILED }
    val warningCount = checkItems.count { it.status == CheckStatus.WARNING }
    
    if (hasResults) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = if (allPassed) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (allPassed) {
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
                                )
                            }
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 大型黄脸emoji表示检测结果
                Text(
                    text = if (allPassed) "😊" else if (failedCount > 0) "😰" else "😐",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 简化的综合评价
                Text(
                    text = when {
                        allPassed -> "设备安全检查全部通过"
                        failedCount > 2 -> "设备存在严重安全风险"
                        failedCount > 0 -> "设备存在安全风险"
                        warningCount > 0 -> "设备基本安全"
                        else -> "检测完成"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (allPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                
                // 简要统计
                if (hasResults) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "通过: ${checkItems.count { it.status == CheckStatus.PASSED }} | " +
                               "失败: $failedCount | " +
                               "警告: $warningCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
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

@Composable
fun ProjectInfoCard(
    modifier: Modifier = Modifier,
    onCheckUpdate: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp)
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
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "关于项目",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // 项目描述
            Text(
                text = AppInfo.PROJECT_DESCRIPTION,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // GitHub链接
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        uriHandler.openUri(AppInfo.PROJECT_GITHUB)
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
                    text = "GitHub: ${AppInfo.PROJECT_GITHUB.removePrefix("https://github.com/").removeSuffix("/")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Telegram群组链接
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        uriHandler.openUri(AppInfo.PROJECT_TELEGRAM)
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
                    text = "Telegram: ${AppInfo.PROJECT_TELEGRAM.removePrefix("https://t.me/")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 检查更新按钮
            Button(
                onClick = onCheckUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "检查更新",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 感谢信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "🙏 特别感谢",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                uriHandler.openUri("https://github.com/lshwjgpt25")
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "lshwjgpt - 提供危险应用检测方法",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 开源许可证信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "📄 本项目基于 ${AppInfo.PROJECT_LICENSE} 开源，欢迎贡献代码和提交问题反馈！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}