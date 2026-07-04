package com.example.nozokima.model

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RequestPage
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID

data class CategoryData(
    val name: String,
    val icon: ImageVector
)

data class AssetTypeUiSpec(
    val icon: ImageVector,
    val accentColor: Color
)

val defaultAssetTypeUiSpec = AssetTypeUiSpec(
    icon = Icons.Outlined.AccountBalanceWallet,
    accentColor = Color(0xFF607D8B)
)

val assetTypeUiSpecMap = mapOf(
    "総額" to AssetTypeUiSpec(Icons.Outlined.AccountBalanceWallet, Color(0xFF2E7D32)),
    "現金" to AssetTypeUiSpec(Icons.Outlined.AccountBalanceWallet, Color(0xFFEF6C00)),
    "銀行" to AssetTypeUiSpec(Icons.Outlined.AccountBalance, Color(0xFF1976D2)),
    "電子マネー" to AssetTypeUiSpec(Icons.Outlined.Payments, Color(0xFF00897B)),
    "カード" to AssetTypeUiSpec(Icons.Outlined.CreditCard, Color(0xFFC62828)),
    "貯蓄" to AssetTypeUiSpec(Icons.Outlined.Savings, Color(0xFF00897B)),
    "投資" to AssetTypeUiSpec(Icons.AutoMirrored.Outlined.ShowChart, Color(0xFF6A1B9A)),
    "貸付" to AssetTypeUiSpec(Icons.Outlined.RequestPage, Color(0xFFFB8C00)),
    "カードローン" to AssetTypeUiSpec(Icons.Outlined.CreditCard, Color(0xFFAD1457)),
    "ローン" to AssetTypeUiSpec(Icons.Outlined.AttachMoney, Color(0xFFD84315)),
    "保険" to AssetTypeUiSpec(Icons.Outlined.Shield, Color(0xFF3949AB)),
    "デビットカード" to AssetTypeUiSpec(Icons.Outlined.CreditCard, Color(0xFF00838F)),
    "その他" to AssetTypeUiSpec(Icons.Outlined.MoreHoriz, Color(0xFF546E7A))
)

fun assetTypeUiSpec(category: String): AssetTypeUiSpec {
    return assetTypeUiSpecMap[category] ?: defaultAssetTypeUiSpec
}

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Int,
    val category: String,
    val date: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val imageUri: Uri? = null,
    val isUser: Boolean = true
)

data class SavedRecordInfo(
    val amount: Int,
    val mode: String,
    val category: String? = null,
    val assetName: String,
    val memo: String,
    val aiAdvice: String? = null
)

data class DisplayExpense(
    val name: String,
    val amount: Int,
    val category: String,
    val date: Long
)
