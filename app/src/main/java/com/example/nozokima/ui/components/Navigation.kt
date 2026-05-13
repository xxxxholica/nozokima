package com.example.nozokima.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.theme.*

@Composable
fun FloatingNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf("ホーム", "記録", "資産状況", "AI相談", "設定")
    val selectedIcons = listOf(
        Icons.Filled.Home,
        Icons.Filled.AddCircle,
        Icons.Filled.BarChart,
        Icons.AutoMirrored.Filled.Chat,
        Icons.Filled.Settings
    )
    val unselectedIcons = listOf(
        Icons.Outlined.Home,
        Icons.Outlined.AddCircle,
        Icons.Outlined.BarChart,
        Icons.AutoMirrored.Outlined.Chat,
        Icons.Outlined.Settings
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        NotionBackground.copy(alpha = 0.5f),
                        NotionBackground
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color(0x11000000),
                    spotColor = Color(0x22000000)
                )
                .background(NotionWhite, RoundedCornerShape(12.dp))
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, label ->
                val isSelected = selectedTab == index
                
                // 設定(index 4)の前に縦線を入れる
                if (index == 4) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(NotionBorder)
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // アイコン＋ラベルをまとめてピルで囲む（選択範囲と強調範囲を一致させる）
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                color = if (isSelected) Color(0xFFD6F0EB) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(index) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) selectedIcons[index] else unselectedIcons[index],
                            contentDescription = label,
                            tint = if (isSelected) NotionSafeGreen else NotionTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
