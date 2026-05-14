@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.nozokima.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.model.assetTypeUiSpec
import ui.theme.*
import java.util.Locale

@Composable
fun ScreenHeader(
    title: String,
    navigationIcon: @Composable (() -> Unit)? = null,
    titleStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (navigationIcon != null) {
            navigationIcon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            color = NotionTextPrimary,
            style = titleStyle,
            modifier = Modifier.weight(1f)
        )
        if (trailingContent != null) trailingContent()
    }
}

@Composable
fun UnifiedAssetCardRow(
    title: String,
    subtitle: String,
    amount: Int,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            }
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, NotionBorder, RoundedCornerShape(14.dp))
            .then(interactionModifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = subtitle, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = NotionTextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "¥ ${String.format(Locale.JAPAN, "%,d", amount)}",
            color = if (amount < 0) Color(0xFFE57373) else NotionTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AssetHistoryItem(
    name: String,
    amount: String,
    memo: String,
    balanceAfter: String,
    color: Color,
    icon: ImageVector = Icons.Default.MoreHoriz,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = memo, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(name, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("$memo・$balanceAfter", color = NotionTextSecondary, fontSize = 12.sp)
            }
        }
        Text(amount, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AssetGroupItemRow(label: String, onClick: () -> Unit) {
    val spec = assetTypeUiSpec(label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(spec.accentColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(spec.icon, null, tint = spec.accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(label, color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = NotionTextSecondary, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = NotionBorder)
}

@Composable
fun AssetCategoryTile(label: String, icon: ImageVector? = null, color: Color? = null, subLabel: String? = null, onClick: () -> Unit) {
    val spec = if (icon == null) assetTypeUiSpec(label) else null
    val targetIcon = icon ?: spec?.icon ?: Icons.Default.MoreHoriz
    val targetColor = color ?: spec?.accentColor ?: NotionTextPrimary

    Column(
        modifier = Modifier
            .width(74.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(targetColor.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .border(1.dp, targetColor.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(targetIcon, null, tint = targetColor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = NotionTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                color = NotionTextSecondary,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DeleteConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit = {}) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除の確認", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = NotionTextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除", color = Color(0xFFE57373)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        containerColor = Color.White, shape = RoundedCornerShape(12.dp)
    )
}
