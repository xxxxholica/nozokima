package com.example.nozokima.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RequestPage
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

fun getCategoryIcon(category: String): ImageVector = when(category) {
    "食生活" -> Icons.Default.Restaurant
    "住まい" -> Icons.Default.Home
    "インフラ" -> Icons.Default.Wifi
    "日用雑貨" -> Icons.Default.LocalMall
    "移動・交通" -> Icons.Default.Place
    "健康・医療" -> Icons.Default.MedicalServices
    "自分磨き" -> Icons.Default.School
    "レジャー" -> Icons.Default.Star
    "交際・贈答" -> Icons.Default.Favorite
    "美容・装い" -> Icons.Default.Face
    "特別な支出" -> Icons.Default.CardGiftcard
    "給与" -> Icons.Default.AccountBalance
    "事業・副業" -> Icons.Default.Build
    "資産運用" -> Icons.Default.Savings
    "臨時収入" -> Icons.Default.Star
    "給付・手当" -> Icons.Default.Info
    "還付・返金" -> Icons.Default.Refresh
    "贈与・祝金" -> Icons.Default.Favorite
    "ポイ活" -> Icons.Default.Payments
    "不用品売却" -> Icons.Default.LocalMall
    "繰越金" -> Icons.Default.History
    "利息・配当" -> Icons.AutoMirrored.Filled.ShowChart
    "貸付" -> Icons.Outlined.RequestPage
    "回収" -> Icons.Default.Handshake
    else -> Icons.Default.MoreHoriz
}
