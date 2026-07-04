package works.hinata.nozokima.ui.components

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
    val items = listOf("ホーム", "記録", "資産", "AI", "設定")
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
                .padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, label ->
                if (index == 4) {
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .width(1.dp)
                            .background(NotionBorder)
                    )
                }

                val isSelected = selectedTab == index

                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // アイコンを囲む枠（正方形）
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = if (isSelected) Color(0xFFD6F0EB) else Color.Transparent
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(index) },
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
