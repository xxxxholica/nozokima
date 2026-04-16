@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.nozokima

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.thema.*
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

// --- データモデル ---

data class AssetItemData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Int
)

data class AssetCategoryData(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    val items: SnapshotStateList<AssetItemData>
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val imageUri: android.net.Uri? = null,
    val isUser: Boolean = true
)

// --- メインアクティビティ ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selectedTab by remember { mutableIntStateOf(0) }

            Surface(modifier = Modifier.fillMaxSize(), color = NotionBackground) {
                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = NotionWhite, tonalElevation = 0.dp) {
                            val items = listOf("ホーム", "記録", "資産状況", "AI相談")
                            val icons = listOf(Icons.Default.Home, Icons.Default.Add, Icons.Default.List, Icons.Default.Face)
                            items.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    icon = { Icon(icons[index], contentDescription = item) },
                                    label = { Text(item, fontSize = 10.sp) },
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NotionSafeGreen,
                                        unselectedIconColor = NotionTextSecondary,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> HomeScreen()
                            1 -> InputScreen()
                            2 -> AssetsScreen()
                            3 -> ConsultationScreen()
                        }
                    }
                }
            }
        }
    }
}

// --- 1. ホーム画面 ---

@Composable
fun HomeScreen() {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..10 -> "おはようございます"
            in 11..17 -> "こんにちは"
            else -> "こんばんは"
        }
    }

    val initialAssets = 300000
    val currentAssets = 158000
    val assetProgress = currentAssets.toFloat() / initialAssets.toFloat()

    val goalName = "サイドFIRE"
    val goalProgress = 0.45f
    val remainingDays = 450
    val acceleration = "+ 1.2 日"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 40.dp, top = 8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.nozokima),
                contentDescription = "App Icon",
                modifier = Modifier.size(40.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = greeting,
                color = NotionTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        Text("利用可能な資産", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "¥ ${String.format("%,d", currentAssets)}",
                color = NotionTextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Text(
                text = " / ¥ ${String.format("%,d", initialAssets)}",
                color = NotionTextSecondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { assetProgress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = NotionSafeGreen,
            trackColor = NotionBorder,
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("リセットまであと 15 日", color = NotionTextSecondary, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(48.dp))

        Text("今月の統計", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "合計",
                value = "¥ 62,400",
                comparison = "-12%",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            SummaryCard(
                title = "最大",
                value = "¥ 32,800",
                subValue = "高性能キーボード",
                comparison = "+5%",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFFE8F0FE), shape = RoundedCornerShape(50.dp)) {
                            Text(
                                text = "AI分析",
                                color = Color(0xFF1A73E8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ウィークリーサマリー", color = NotionTextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "今週は食費が前週比で20%抑制されており、理想的な推移です。最大の出費となった「キーボード」は自己研鑽のスコアが高いため、投資としてカウントされています。目標達成がさらに0.5日加速する見込みです。",
                    color = NotionTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text("目標", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = goalName, color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "残り $remainingDays 日", color = NotionTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "${(goalProgress * 100).toInt()}%", color = NotionSafeGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = NotionSafeGreen,
                    trackColor = NotionBorder,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目標達成までの加速", color = NotionTextSecondary, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = acceleration, color = NotionSafeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "「今日お金を使わない」という選択が未来を引き寄せました。",
                    color = NotionTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SummaryCard(title: String, value: String, subValue: String? = null, comparison: String? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier.border(1.dp, NotionBorder, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).padding(20.dp)) {
        Column {
            Text(text = title, color = NotionTextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, color = NotionTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subValue != null) {
                Text(text = subValue, color = NotionTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (comparison != null) {
            val isIncrease = comparison.startsWith("+")
            val badgeBgColor = if (isIncrease) Color(0xFFFFF1F1) else Color(0xFFF0F9F4)
            val badgeTextColor = if (isIncrease) Color(0xFFE57373) else NotionSafeGreen
            Surface(modifier = Modifier.align(Alignment.TopEnd).widthIn(min = 56.dp), color = badgeBgColor, shape = RoundedCornerShape(50.dp)) {
                Text(text = comparison, color = badgeTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

// --- 2. 記録画面 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen() {
    var amountText by remember { mutableStateOf("") }
    var selfBurdenText by remember { mutableStateOf("") } // 自己負担額
    var memoText by remember { mutableStateOf("") }
    var selectedAsset by remember { mutableStateOf("未選択") }
    var showAssetSheet by remember { mutableStateOf(false) }

    // 数値計算ロジック
    val totalAmount = amountText.toIntOrNull() ?: 0
    val selfBurden = selfBurdenText.toIntOrNull() ?: totalAmount
    // 返済予定額 = 総額 - 自己負担額 (マイナスにならないよう調整)
    val reimbursement = if (amountText.isNotEmpty()) (totalAmount - selfBurden).coerceAtLeast(0) else 0

    val assetOptions = listOf("現金", "楽天銀行", "PayPay", "PASMO", "ANA PAY")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("消費の記録", color = NotionTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        // 1. 金額入力セクション
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("支払総額", color = NotionTextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = amountText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) amountText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = NotionTextPrimary,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (amountText.isEmpty()) {
                            Text("¥ 0", color = NotionBorder, fontSize = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. 詳細設定（支払い元・負担区分）
        Text("支払い詳細", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
        ) {
            // 支払い元
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAssetSheet = true }.padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("支払い元", color = NotionTextPrimary, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = selectedAsset, color = if (selectedAsset == "未選択") NotionTextSecondary else Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = NotionTextSecondary)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = NotionBorder)

            // 自己負担額の入力
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("うち自己負担額", color = NotionTextPrimary, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¥ ", color = NotionTextSecondary, fontSize = 16.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = selfBurdenText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) selfBurdenText = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = NotionTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier.width(100.dp),
                        decorationBox = { innerTextField ->
                            if (selfBurdenText.isEmpty()) {
                                Text(amountText.ifEmpty { "0" }, color = NotionTextSecondary, textAlign = TextAlign.End)
                            }
                            innerTextField()
                        }
                    )
                }
            }

            // 返済予定額の表示（自動計算結果）
            if (reimbursement > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F9F4)) // わずかに緑背景（返ってくるポジティブな色）
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("返済予定額（立替）", color = NotionSafeGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("¥ ${String.format("%,d", reimbursement)}", color = NotionSafeGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = NotionBorder)

            // メモ
            Column(modifier = Modifier.padding(20.dp)) {
                Text("品目・メモ", color = NotionTextPrimary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = NotionTextPrimary, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (memoText.isEmpty()) {
                            Text("何にお金を使いましたか？", color = NotionTextSecondary, fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { /* 記録確定ロジック */ },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen, disabledContainerColor = NotionBorder),
            shape = RoundedCornerShape(12.dp),
            enabled = amountText.isNotEmpty() && selectedAsset != "未選択"
        ) {
            Text("記録を保存する", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    // --- 支払い元選択ボトムシート (以前のまま) ---
    if (showAssetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAssetSheet = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("支払い元を選択", modifier = Modifier.padding(16.dp), color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                assetOptions.forEach { asset ->
                    AssetGroupItemRow(asset) { selectedAsset = asset; showAssetSheet = false }
                }
            }
        }
    }
}

// --- 3. 資産状況画面 ---

@Composable
fun AssetsScreen() {
    val categories = remember {
        mutableStateListOf(
            AssetCategoryData(title = "現金", items = mutableStateListOf(AssetItemData(name = "現金", amount = 1500))),
            AssetCategoryData(title = "銀行", items = mutableStateListOf(AssetItemData(name = "楽天銀行", amount = 515046))),
            AssetCategoryData(title = "電子マネー", items = mutableStateListOf(AssetItemData(name = "PayPay", amount = 394)))
        )
    }

    var showGroupSheet by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedGroupTitle by remember { mutableStateOf("") }
    var editNameText by remember { mutableStateOf("") }
    var editAmountText by remember { mutableStateOf("") }

    var editingCategory by remember { mutableStateOf<AssetCategoryData?>(null) }
    var editingItem by remember { mutableStateOf<Pair<AssetCategoryData, AssetItemData>?>(null) }
    var itemToDelete by remember { mutableStateOf<Pair<AssetCategoryData, AssetItemData>?>(null) }
    var categoryToDelete by remember { mutableStateOf<AssetCategoryData?>(null) }

    val assetGroups = listOf("現金", "銀行", "電子マネー", "カード", "貯蓄", "投資", "カードローン", "ローン", "保険", "デビットカード", "その他")
    val totalAssets = categories.sumOf { cat -> cat.items.sumOf { it.amount } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("資産状況", color = NotionTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { showGroupSheet = true },
                modifier = Modifier.background(NotionSafeGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NotionSafeGreen)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, NotionBorder, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                SummaryItemSmall("資産", totalAssets, Color(0xFF2196F3))
                SummaryItemSmall("負債", 0, Color(0xFFE57373))
                SummaryItemSmall("合計", totalAssets, NotionTextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        categories.forEach { category ->
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = category.title,
                    color = NotionTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.combinedClickable(
                        onClick = { editingCategory = category; editNameText = category.title },
                        onLongClick = { if (category.title != "カテゴリ未設定") categoryToDelete = category }
                    ).padding(vertical = 4.dp, horizontal = 8.dp)
                )
                Box(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, NotionBorder, RoundedCornerShape(12.dp))) {
                    Column {
                        if (category.items.isEmpty()) {
                            Text("項目がありません", modifier = Modifier.padding(16.dp).fillMaxWidth(), textAlign = TextAlign.Center, color = NotionTextSecondary, fontSize = 13.sp)
                        }
                        category.items.forEach { item ->
                            AssetListItemRow(item, onClick = { editingItem = category to item; editNameText = item.name }, onLongClick = { itemToDelete = category to item })
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showGroupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGroupSheet = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("追加する資産カテゴリを選択", modifier = Modifier.padding(16.dp), color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                assetGroups.forEach { group ->
                    AssetGroupItemRow(group) {
                        selectedGroupTitle = group
                        editNameText = ""
                        editAmountText = ""
                        showGroupSheet = false
                        showAddItemDialog = true
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("$selectedGroupTitle に追加", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = editNameText, onValueChange = { editNameText = it }, label = { Text("名称（例: 楽天銀行）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = editAmountText, onValueChange = { editAmountText = it }, label = { Text("金額") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = editAmountText.toIntOrNull() ?: 0
                    var targetCategory = categories.find { it.title == selectedGroupTitle }
                    if (targetCategory == null) {
                        targetCategory = AssetCategoryData(title = selectedGroupTitle, items = mutableStateListOf())
                        categories.add(targetCategory)
                    }
                    targetCategory.items.add(AssetItemData(name = editNameText.ifBlank { "新規項目" }, amount = amount))
                    showAddItemDialog = false
                }) { Text("追加", color = NotionSafeGreen) }
            },
            dismissButton = { TextButton(onClick = { showAddItemDialog = false }) { Text("キャンセル") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }

    editingCategory?.let { category ->
        NameEditDialog("カテゴリ名の変更", editNameText, onValueChange = { editNameText = it }, onDismiss = { editingCategory = null }) {
            val index = categories.indexOf(category)
            if (index != -1) categories[index] = category.copy(title = editNameText)
            editingCategory = null
        }
    }
    editingItem?.let { (category, item) ->
        NameEditDialog("項目の名称変更", editNameText, onValueChange = { editNameText = it }, onDismiss = { editingItem = null }) {
            val index = category.items.indexOf(item)
            if (index != -1) category.items[index] = item.copy(name = editNameText)
            editingItem = null
        }
    }
    itemToDelete?.let { (category, item) ->
        DeleteConfirmDialog("${item.name} を削除しますか？", onDismiss = { itemToDelete = null }) {
            category.items.remove(item)
            itemToDelete = null
        }
    }
}

// --- 4. AI相談画面 ---

@Composable
fun ConsultationScreen() {
    val messages = remember {
        mutableStateListOf(
            ChatMessage("1", "こんにちは！「覗き魔」AIコンシェルジュです。Gemma-4-E4Bがあなたの支出判定や未来設計をサポートします。レシート画像を送っていただければ内容の解析も可能です。", isUser = false)
        )
    }
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("AI相談", modifier = Modifier.padding(24.dp), color = NotionTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            messages.forEach { msg ->
                ChatBubble(msg)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            tonalElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, NotionBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { }) { Icon(Icons.Default.Add, contentDescription = null, tint = NotionTextSecondary) }
                Box(
                    modifier = Modifier.weight(1f).background(NotionBackground, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (inputText.isEmpty()) {
                        Text("相談内容を入力...", color = NotionTextSecondary, fontSize = 14.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            messages.add(ChatMessage(text = inputText, isUser = true))
                            inputText = ""
                            messages.add(ChatMessage("ai_reply", "ご相談ありがとうございます。その支出は「自己研鑽」のスコアが高そうですね！", isUser = false))
                        }
                    },
                    modifier = Modifier.background(if (inputText.isNotBlank()) NotionSafeGreen else NotionBorder, RoundedCornerShape(50.dp)).size(36.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White).border(1.dp, NotionBorder, CircleShape).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.nozokima), contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        val bubbleColor = if (message.isUser) NotionSafeGreen else Color.White
        val textColor = if (message.isUser) Color.White else NotionTextPrimary
        val shape = if (message.isUser) RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)

        Column(
            modifier = Modifier.widthIn(max = 260.dp).background(bubbleColor, shape).border(1.dp, if (message.isUser) Color.Transparent else NotionBorder, shape).padding(12.dp)
        ) {
            if (message.imageUri != null) {
                Text("[添付画像]", color = textColor, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(text = message.text, color = textColor, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

// --- 共通コンポーネント ---

@Composable
fun AssetGroupItemRow(name: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Text(text = name, modifier = Modifier.padding(16.dp).fillMaxWidth(), color = NotionTextPrimary, fontSize = 16.sp)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
    }
}

@Composable
fun SummaryItemSmall(label: String, amount: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = NotionTextSecondary, fontSize = 12.sp)
        Text("¥ ${String.format("%,d", amount)}", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AssetListItemRow(item: AssetItemData, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, modifier = Modifier.weight(1f), color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = "¥ ${String.format("%,d", item.amount)}", color = if (item.amount > 0) Color(0xFF2196F3) else NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
    }
}

@Composable
fun NameEditDialog(title: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold) }, text = { OutlinedTextField(value = value, onValueChange = onValueChange, singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = onConfirm) { Text("保存", color = NotionSafeGreen) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = NotionTextSecondary) } }, containerColor = Color.White, shape = RoundedCornerShape(12.dp))
}

@Composable
fun DeleteConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("確認", fontSize = 18.sp, fontWeight = FontWeight.Bold) }, text = { Text(text) }, confirmButton = { TextButton(onClick = onConfirm) { Text("削除", color = Color(0xFFE57373)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = NotionTextSecondary) } }, containerColor = Color.White, shape = RoundedCornerShape(12.dp))
}