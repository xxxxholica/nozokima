package com.example.nozokima.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
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

@Composable
fun ChatBubble(message: ChatMessage) {
    if (message.isUser) {
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
                    .widthIn(max = 280.dp)
                    .background(bubbleColor, shape)
                    .padding(12.dp)
            ) {
                if (message.imageUri != null) {
                    Text("[添付画像]", color = textColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = parseMarkdown(message.text),
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
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
