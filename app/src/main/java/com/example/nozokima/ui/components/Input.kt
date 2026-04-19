package com.example.nozokima.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.theme.*

@Composable
fun InputTile(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    accentColor: Color,
    isPlaceholder: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.08f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    fontSize = 15.sp,
                    color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.SemiBold
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun CustomKeypad(
    onNumberClick: (String) -> Unit,
    onOperatorClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    isSaveEnabled: Boolean,
    actionColor: Color,
    saveLabel: String = "保存"
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
        val keys = listOf(
            listOf("AC", "÷", "×", "BS"),
            listOf("7", "8", "9", "−"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "="),
            listOf("00", "0", ".", saveLabel)
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val isAction = key == saveLabel
                    val isNumber = key.all { it.isDigit() } || key == "." || key == "00"
                    val isDelete = key == "BS"
                    val isClear = key == "AC"
                    val isEqual = key == "="

                    Button(
                        onClick = {
                            when {
                                isClear -> onClearAllClick()
                                isDelete -> onDeleteClick()
                                isAction -> onSaveClick()
                                isEqual -> onConfirmClick()
                                isNumber -> onNumberClick(key)
                                else -> {
                                    val op = when (key) {
                                        "÷" -> "/"
                                        "×" -> "*"
                                        "−" -> "-"
                                        else -> key
                                    }
                                    onOperatorClick(op)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(60.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAction) actionColor else if (isNumber) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
                            contentColor = if (isAction) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isAction) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = null
                    ) {
                        if (isDelete) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "削除",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = key,
                                fontSize = 18.sp,
                                fontWeight = if (isAction) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
