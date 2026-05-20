package com.example.nozokima.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.model.ChatMessage
import com.example.nozokima.util.parseMarkdown
import ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(message: ChatMessage) {
    if (message.isUser) {
        val txCardRegex = Regex("\\[TX_CARD:(.*?)\\|(.*?)\\|(.*?)\\|(.*?)]")
        val matchResult = txCardRegex.find(message.text)
        val plainText = if (matchResult != null) message.text.replace(matchResult.value, "").trim() else message.text

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            val bubbleColor = NotionSafeGreen
            val textColor = Color.White
            val shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)

            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (plainText.isNotEmpty() || message.imageUri != null) {
                    Column(
                        modifier = Modifier
                            .background(bubbleColor, shape)
                            .padding(12.dp)
                    ) {
                        if (message.imageUri != null) {
                            Text("[添付画像]", color = textColor, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (plainText.isNotEmpty()) {
                            Text(
                                text = parseMarkdown(plainText),
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                if (matchResult != null) {
                    val (name, amountStr, category, dateStr) = matchResult.destructured
                    val amount = amountStr.toIntOrNull() ?: 0
                    val date = dateStr.toLongOrNull() ?: System.currentTimeMillis()
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        modifier = Modifier.width(260.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, NotionBorder)
                    ) {
                        AssetHistoryItem(
                            name = name,
                            amount = "¥ ${String.format(Locale.JAPAN, "%,d", amount)}",
                            memo = category,
                            balanceAfter = SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(date)),
                            color = Color(0xFFE57373),
                            icon = getCategoryIcon(category),
                            onLongClick = {}
                        )
                    }
                }
            }
        }
    } else {
        // AI Gemini Style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(NotionSafeGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NotionSafeGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("覗き魔 AI", color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (message.text == "...") {
                    ThinkingAnimation()
                } else {
                    Text(
                        text = parseMarkdown(message.text),
                        color = NotionTextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
