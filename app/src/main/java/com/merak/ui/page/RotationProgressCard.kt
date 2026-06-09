package com.merak.ui.page

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.merak.utils.ThemeRotationManager
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RotationProgressCard() {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var elapsedText by remember { mutableStateOf("") }
    var remainingText by remember { mutableStateOf("") }
    var currentTheme by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ThemeRotationManager.ACTION_ROTATION_COMPLETED) {
                    refreshTick++
                }
            }
        }
        val filter = IntentFilter(ThemeRotationManager.ACTION_ROTATION_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(refreshTick) {
        while (true) {
            val intervalMinutes = ThemeRotationManager.getIntervalMinutes()
            val intervalMs = intervalMinutes * 60_000L
            val nextTriggerTime = ThemeRotationManager.getNextTriggerTime()
            val now = System.currentTimeMillis()

            if (nextTriggerTime > 0) {
                val remainingMs = (nextTriggerTime - now).coerceAtLeast(0)
                val progressValue = if (intervalMs > 0) {
                    ((intervalMs - remainingMs).toFloat() / intervalMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
                progress = progressValue
                
                // 检查是否处于挂起等待息屏状态
                val isPending = ThemeRotationManager.isPending()
                
                if (isPending && remainingMs == 0L) {
                    // 挂起且时间已到 -> 显示等待息屏
                    elapsedText = formatTime((intervalMs / 60_000).toInt())
                    remainingText = "0m"
                    statusText = "等待息屏中..."
                } else {
                    // 正常倒计时
                    val remainingMin = (remainingMs / 60_000).toInt()
                    val elapsedMin = ((intervalMs - remainingMs) / 60_000).toInt().coerceAtLeast(0)
                    elapsedText = formatTime(elapsedMin)
                    remainingText = if (remainingMs == 0L) {
                        "即将触发..."
                    } else {
                        formatTime(remainingMin)
                    }
                    statusText = ""
                }
            } else {
                progress = 0f
                elapsedText = "0m"
                remainingText = "${intervalMinutes}m"
                statusText = "等待首次轮换..."
            }

            currentTheme = ThemeRotationManager.getCurrentFileName()
            if (currentTheme.isEmpty()) {
                currentTheme = "等待轮换"
            }
            delay(1000)
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "自动轮换", color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "${(progress * 100).toInt()}%", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MiuixTheme.colorScheme.primary,
                trackColor = MiuixTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "已用: $elapsedText", color = MiuixTheme.colorScheme.onSurface, fontSize = 12.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "剩余: $remainingText", color = MiuixTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "当前: $currentTheme", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = statusText, color = MiuixTheme.colorScheme.outline, fontSize = 12.sp)
            }
        }
    }
}

private fun formatTime(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
