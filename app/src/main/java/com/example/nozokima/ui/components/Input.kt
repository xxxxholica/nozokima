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
        color = Color.White,
        border = BorderStroke(1.dp, NotionBorder)
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
                Text(label, fontSize = 12.sp, color = NotionTextSecondary)
                Text(
                    value,
                    fontSize = 15.sp,
                    color = if (isPlaceholder) NotionTextSecondary.copy(alpha = 0.5f) else NotionTextPrimary,
                    fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.SemiBold
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = NotionTextSecondary.copy(alpha = 0.3f)
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
    actionColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
        val keys = listOf(
            listOf("AC", "÷", "×", "BS"),
            listOf("7", "8", "9", "−"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "="),
            listOf("00", "0", ".", "確定")
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val isAction = key == "確定"
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
                            containerColor = if (isAction) actionColor else if (isNumber) Color(0xFFF7F7F7) else Color.White,
                            contentColor = if (isAction) Color.White else NotionTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isAction) null else BorderStroke(1.dp, NotionBorder),
                        elevation = null
                    ) {
                        if (isDelete) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "削除",
                                modifier = Modifier.size(20.dp),
                                tint = NotionTextPrimary
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
