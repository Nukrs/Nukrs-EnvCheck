package me.nukrs.root.envcheck.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.nukrs.root.envcheck.model.CheckItem
import me.nukrs.root.envcheck.model.CheckStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckItemCard(
    checkItem: CheckItem,
    onItemClick: () -> Unit,
    enabled: Boolean = true,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    
    val cardColors = when (checkItem.status) {
        CheckStatus.PASSED -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
        CheckStatus.FAILED -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
        CheckStatus.WARNING -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
        else -> CardDefaults.cardColors()
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // 主要内容区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onItemClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Icon(
                    imageVector = checkItem.icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 文本内容
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = checkItem.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = checkItem.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 显示评分
                    if (checkItem.details != null && checkItem.details.score.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "安全评分: ${checkItem.details.score}",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (checkItem.status) {
                                CheckStatus.PASSED -> Color(0xFF4CAF50)
                                CheckStatus.FAILED -> MaterialTheme.colorScheme.error
                                CheckStatus.WARNING -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 状态指示器
                StatusIndicator(status = checkItem.status)
                
                // 展开/折叠按钮
                if (checkItem.details != null && 
                    (checkItem.details.passedChecks.isNotEmpty() || 
                     checkItem.details.failedChecks.isNotEmpty() ||
                     checkItem.details.recommendation.isNotEmpty())) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onExpandedChange(!expanded) }
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "收起详情" else "展开详情"
                        )
                    }
                }
            }
            
            // 详细信息区域
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                checkItem.details?.let { details ->
                    CheckDetailsContent(details = details)
                }
            }
        }
    }
}

@Composable
fun CheckDetailsContent(
    details: me.nukrs.root.envcheck.model.CheckDetails,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Divider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        
        // 通过的检查项
        if (details.passedChecks.isNotEmpty()) {
            Text(
                text = "✅ 通过的检查项",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            details.passedChecks.forEach { check ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = check,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // 失败的检查项
        if (details.failedChecks.isNotEmpty()) {
            Text(
                text = "❌ 未通过的检查项",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            details.failedChecks.forEach { check ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = check,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // 警告的检查项
        if (details.warningChecks.isNotEmpty()) {
            Text(
                text = "⚠️ 警告的检查项",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF9800),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            details.warningChecks.forEach { check ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFF9800)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = check,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // 建议
        if (details.recommendation.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "💡 安全建议",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = details.recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun StatusIndicator(
    status: CheckStatus,
    modifier: Modifier = Modifier
) {
    when (status) {
        CheckStatus.PENDING -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "待检测",
                modifier = modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        CheckStatus.RUNNING -> {
            val infiniteTransition = rememberInfiniteTransition(label = "rotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            
            CircularProgressIndicator(
                modifier = modifier
                    .size(24.dp)
                    .rotate(rotation),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        CheckStatus.PASSED -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "检测通过",
                modifier = modifier.size(24.dp),
                tint = Color(0xFF4CAF50)
            )
        }
        CheckStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "检测失败",
                modifier = modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        CheckStatus.WARNING -> {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "检测警告",
                modifier = modifier.size(24.dp),
                tint = Color(0xFFFF9800)
            )
        }
    }
}