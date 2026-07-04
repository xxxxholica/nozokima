package works.hinata.nozokima.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.hinata.nozokima.model.SavedRecordInfo
import ui.theme.*
import java.util.Locale

@Composable
fun SuccessOverlay(
    info: SavedRecordInfo,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val displayDuration = 5000L // 5秒
    var progress by remember { mutableFloatStateOf(1f) }
    
    LaunchedEffect(info.aiAdvice) {
        if (info.aiAdvice != null || info.mode != "支出") {
            visible = true
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < displayDuration) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = 1f - (elapsed.toFloat() / displayDuration)
                kotlinx.coroutines.delay(50)
            }
            visible = false
            kotlinx.coroutines.delay(400)
            onDismiss()
        } else {
            // AIアドバイス待ち
            visible = true
        }
    }

    val accentColor = when (info.mode) {
        "支出" -> Color(0xFFD32F2F)
        "収入" -> NotionSafeGreen
        "振替" -> Color(0xFF1976D2)
        "貸付" -> Color(0xFFFB8C00)
        "回収" -> Color(0xFF00897B)
        else -> NotionSafeGreen
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 1.1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // チェックマーク
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(accentColor.copy(alpha = 0.1f), CircleShape)
                        .border(4.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = info.memo,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NotionTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "¥ ${String.format(Locale.JAPAN, "%,d", info.amount)}",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    letterSpacing = (-1).sp
                )

                if (info.mode == "支出") {
                    Spacer(modifier = Modifier.height(48.dp))
                    Surface(
                        color = Color(0xFFF9F9F9),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NotionBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = NotionSafeGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("覗き魔 AI アドバイス", fontSize = 12.sp, color = NotionSafeGreen, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (info.aiAdvice != null) {
                                Text(
                                    text = info.aiAdvice,
                                    fontSize = 14.sp,
                                    color = NotionTextPrimary,
                                    lineHeight = 20.sp
                                )
                            } else {
                                ThinkingAnimation()
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(64.dp))
                
                // 戻るボタン (インジケーター付き)
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(NotionBorder.copy(alpha = 0.3f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    // プログレス背景
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .background(accentColor.copy(alpha = 0.2f))
                    )
                    
                    Text(
                        text = "戻る",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 0
                1f at 400
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 200
                1f at 600
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 400
                1f at 800
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NotionSafeGreen.copy(alpha = alpha1)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NotionSafeGreen.copy(alpha = alpha2)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NotionSafeGreen.copy(alpha = alpha3)))
    }
}
