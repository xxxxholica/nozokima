package com.example.nozokima.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ui.theme.*

@Composable
fun AppLockScreen(
    correctPassword: String,
    onUnlock: () -> Unit,
    failedAttempts: Int,
    lockoutUntil: Long,
    onFailedAttempt: (Int, Long) -> Unit,
    onSuccessfulUnlock: () -> Unit,
    isBiometricEnabled: Boolean = false,
    onBiometricClick: () -> Unit = {}
) {
    var inputPassword by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isLockedOut = lockoutUntil > currentTime
    val remainingLockoutSeconds = if (isLockedOut) ((lockoutUntil - currentTime) / 1000).toInt() else 0

    LaunchedEffect(isLockedOut) {
        while (currentTime < lockoutUntil) {
            kotlinx.coroutines.delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    // アプリが前面に戻った際や起動時に自動で生体認証を起動
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, isBiometricEnabled, isLockedOut) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isBiometricEnabled && !isLockedOut) {
                    onBiometricClick()
                }
            }
        }
        lifecycle.addObserver(observer)
        // 表示された時点で既にRESUMEDなら即座に実行
        if (lifecycle.currentState == Lifecycle.State.RESUMED) {
            if (isBiometricEnabled && !isLockedOut) {
                onBiometricClick()
            }
        }
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(64.dp).then(
                if (isBiometricEnabled && !isLockedOut) Modifier.clickable { onBiometricClick() } else Modifier
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isLockedOut -> Icons.Default.Timer
                    else -> Icons.Default.Lock
                },
                contentDescription = null,
                tint = if (isLockedOut) Color(0xFFE57373) else NotionSafeGreen,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (isLockedOut) "入力を一時制限しています" else "アプリはロックされています",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        if (isLockedOut) {
            Text(
                text = "残り $remainingLockoutSeconds 秒後に再試行できます",
                color = Color(0xFFE57373),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (!isLockedOut) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { index ->
                    val isActive = index < inputPassword.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isActive) NotionSafeGreen else Color.Transparent)
                            .border(1.5.dp, NotionSafeGreen, CircleShape)
                    )
                }
            }
        } else {
            Spacer(Modifier.height(16.dp)) // Maintain height occupied by indicators
        }
        
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            if (isError && !isLockedOut) {
                Text(
                    text = "パスワードが正しくありません (残り ${3 - failedAttempts} 回)",
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PinKeypad(
            onNumberClick = { num ->
                if (inputPassword.length < 4 && !isLockedOut) {
                    inputPassword += num
                    isError = false
                }
            },
            onDeleteClick = {
                if (inputPassword.isNotEmpty() && !isLockedOut) {
                    inputPassword = inputPassword.dropLast(1)
                    isError = false
                }
            },
            onConfirmClick = {
                if (inputPassword == correctPassword) {
                    onSuccessfulUnlock()
                    onUnlock()
                } else {
                    val nextFailedAttempts = failedAttempts + 1
                    var nextLockoutUntil = 0L
                    if (nextFailedAttempts >= 3) {
                        nextLockoutUntil = System.currentTimeMillis() + 60 * 1000
                    }
                    onFailedAttempt(if (nextFailedAttempts >= 3) 0 else nextFailedAttempts, nextLockoutUntil)
                    isError = true
                    inputPassword = ""
                }
            },
            isConfirmEnabled = inputPassword.length >= 4 && !isLockedOut,
            confirmLabel = "解除"
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = onBiometricClick,
            enabled = isBiometricEnabled && !isLockedOut,
            colors = ButtonDefaults.textButtonColors(
                contentColor = NotionSafeGreen,
                disabledContentColor = NotionTextSecondary.copy(alpha = 0.4f)
            )
        ) {
            Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("生体認証を使用", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PinKeypad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmClick: () -> Unit,
    isConfirmEnabled: Boolean,
    confirmLabel: String,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("BS", "0", "OK") // Use "OK" internally, we'll label it as confirmLabel
    )

    Column(
        modifier = modifier.width(280.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { key ->
                    val isAction = key == "OK"
                    val isDelete = key == "BS"
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.2f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isAction) (if (isConfirmEnabled) NotionSafeGreen else MaterialTheme.colorScheme.outline) else MaterialTheme.colorScheme.surface)
                            .then(if (!isAction || isConfirmEnabled) Modifier.clickable {
                                when {
                                    isDelete -> onDeleteClick()
                                    isAction -> onConfirmClick()
                                    else -> onNumberClick(key)
                                }
                            } else Modifier)
                            .border(1.dp, if (isAction) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isDelete -> Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                            isAction -> Text(text = confirmLabel, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            else -> Text(text = key, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
