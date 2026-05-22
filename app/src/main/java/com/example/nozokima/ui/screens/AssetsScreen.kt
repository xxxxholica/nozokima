package com.example.nozokima.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RequestPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.model.*
import com.example.nozokima.data.local.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.components.*
import com.example.nozokima.util.evaluateExpression
import com.example.nozokima.util.formatAmountWithCommas
import kotlinx.coroutines.launch
import ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssetsScreen(
    dao: FinanceDao,
    onRecoverClick: (LendingEntity) -> Unit = {},
    initialCategoryFilter: String? = null,
    initialHistoryMode: Boolean = false,
    onBack: () -> Unit = {},
) {
    val assetsFromDb by dao.getAllAssets().collectAsState(initial = emptyList())
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val scheduledExpenses by dao.getAllScheduledExpenses().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // 期限が来た支払い予定を自動処理
    LaunchedEffect(scheduledExpenses) {
        val now = System.currentTimeMillis()
        scheduledExpenses.filter { !it.isCompleted && it.date <= now }.forEach { expense ->
            dao.insertTransaction(TransactionEntity(
                id = UUID.randomUUID().toString(),
                name = expense.name,
                amount = expense.amount,
                category = expense.category,
                date = expense.date,
                assetName = expense.assetName,
                isExpense = true
            ))
            val asset = dao.getAssetByName(expense.assetName)
            if (asset != null) {
                dao.updateAsset(asset.copy(amount = asset.amount - expense.amount, lastUpdated = System.currentTimeMillis()))
            }
            dao.updateScheduledExpense(expense.copy(isCompleted = true))
        }
    }

    var isHistoryMode by remember { mutableStateOf(initialHistoryMode) }
    var isScheduledHistoryMode by remember { mutableStateOf(false) }

    // システムの戻るボタンに対応
    BackHandler(enabled = isHistoryMode) {
        if (initialHistoryMode) {
            onBack()
        } else {
            isHistoryMode = false
            isScheduledHistoryMode = false
        }
    }

    var showGroupSheet by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedGroupTitle by remember { mutableStateOf("") }
    var editNameText by remember { mutableStateOf("") }
    var editAmountText by remember { mutableStateOf("") }

    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var scheduledExpenseToDelete by remember { mutableStateOf<ScheduledExpenseEntity?>(null) }

    val assetGroups = listOf("現金", "銀行", "電子マネー", "カード", "貯蓄", "投資", "貸付", "カードローン", "ローン", "保険", "デビットカード", "その他")
    
    var selectedHistoryAssetName by remember { mutableStateOf<String?>(null) }

    // 履歴フィルタ用の状態変数
    var selectedHistoryCategory by remember(initialCategoryFilter) { mutableStateOf(initialCategoryFilter) }
    var selectedExpenseFilter by remember { mutableStateOf("全て") } // "全て", "支出のみ", "収入のみ"
    var selectedStartDate by remember { mutableLongStateOf(0L) }
    var selectedEndDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedPeriodLabel by remember { mutableStateOf("全て") }
    var showAssetFilterMenu by remember { mutableStateOf(false) }
    var showCategoryFilterMenu by remember { mutableStateOf(false) }
    var showPeriodFilterMenu by remember { mutableStateOf(false) }
    var showExpenseFilterMenu by remember { mutableStateOf(false) }

    // 資産編集シート用
    var editingAssetEntity by remember { mutableStateOf<AssetEntity?>(null) }
    var showAssetAmountEdit by remember { mutableStateOf(false) }
    var showAssetNameEdit by remember { mutableStateOf(false) }
    var showAssetDeleteConfirm by remember { mutableStateOf(false) }

    val assetCategoryOrder = remember(assetGroups) {
        assetGroups.withIndex().associateBy(
            keySelector = { it.value },
            valueTransform = { it.index },
        )
    }
    val sortedAssets = assetsFromDb.sortedWith(
        compareBy<AssetEntity>(
            { assetCategoryOrder[it.category] ?: Int.MAX_VALUE },
            { it.name }
        )
    )

    val lendings by dao.getAllLendings().collectAsState(initial = emptyList())
    val totalLendingAsset = lendings.filter { !it.isRecovered }.sumOf { it.amount }

    val totalAssets = assetsFromDb.asSequence().filter { it.amount > 0 }.sumOf { it.amount } + totalLendingAsset
    val totalLiabilities = assetsFromDb.asSequence().filter { it.amount < 0 }.sumOf { it.amount }.let { if (it < 0) it * -1 else it }
    val totalNet = totalAssets - totalLiabilities

    val upcomingTotal = scheduledExpenses.filter { !it.isCompleted }.sumOf { it.amount }
    val virtualBalance = totalNet - upcomingTotal

    val sortedScheduledExpenses = scheduledExpenses.sortedByDescending { it.date }

    Box(modifier = Modifier.fillMaxSize().background(NotionBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = if (isHistoryMode) {
                    when {
                        isScheduledHistoryMode -> "支払い予定"
                        selectedHistoryAssetName != null -> selectedHistoryAssetName ?: "履歴"
                        selectedHistoryCategory == "貸付" -> "貸付"
                        else -> "履歴"
                    }
                } else "資産状況",
                navigationIcon = {
                    Surface(
                        onClick = {
                            if (isHistoryMode && !initialHistoryMode) {
                                isHistoryMode = false
                                isScheduledHistoryMode = false
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = NotionTextSecondary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            ) {
                if (!isHistoryMode) {
                    Surface(
                        onClick = { showGroupSheet = true },
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = NotionSafeGreen.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = NotionSafeGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                AnimatedContent(
                    targetState = isHistoryMode,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "AssetsScreenTransition"
                ) { targetHistoryMode ->
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        if (!targetHistoryMode) {
                            // 残高内訳表示
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(assetTypeUiSpec("総額").accentColor.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                        .border(1.dp, assetTypeUiSpec("総額").accentColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 20.dp, vertical = 16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(assetTypeUiSpec("総額").accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(assetTypeUiSpec("総額").icon, contentDescription = null, tint = assetTypeUiSpec("総額").accentColor, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("資産総額", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text(
                                                "¥ ${String.format(Locale.JAPAN, "%,d", totalNet)}",
                                                color = assetTypeUiSpec("総額").accentColor,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = (-0.5).sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 実質残高・予定ボックス
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1976D2).copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                        .border(1.dp, Color(0xFF1976D2).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 20.dp, vertical = 16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color(0xFF1976D2).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Savings, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("自由に使える残高", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text(
                                                "¥ ${String.format(Locale.JAPAN, "%,d", virtualBalance)}",
                                                color = Color(0xFF1976D2),
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = (-0.5).sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 履歴 (History)
                                    Surface(
                                        onClick = {
                                            selectedHistoryAssetName = null
                                            selectedHistoryCategory = null
                                            isHistoryMode = true
                                            isScheduledHistoryMode = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, NotionBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.History, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("履歴", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
                                        }
                                    }

                                    // 支払い予定 (Payment Schedule)
                                    Surface(
                                        onClick = {
                                            selectedHistoryAssetName = null
                                            selectedHistoryCategory = null
                                            isHistoryMode = true
                                            isScheduledHistoryMode = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, NotionBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Event, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("支払い予定", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                if (sortedAssets.isEmpty()) {
                                    Text(
                                        "項目がありません",
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        color = NotionTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }

                                sortedAssets.forEach { asset ->
                                    val spec = assetTypeUiSpec(asset.category)
                                    UnifiedAssetCardRow(
                                        title = asset.name,
                                        subtitle = asset.category,
                                        amount = asset.amount,
                                        icon = spec.icon,
                                        accentColor = spec.accentColor,
                                        onClick = {
                                            selectedHistoryAssetName = asset.name
                                            selectedHistoryCategory = null
                                            isHistoryMode = true
                                            isScheduledHistoryMode = false
                                        },
                                        onLongClick = {
                                            editingAssetEntity = asset
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                UnifiedAssetCardRow(
                                    title = "貸付金",
                                    subtitle = "貸付",
                                    amount = totalLendingAsset,
                                    icon = assetTypeUiSpec("貸付").icon,
                                    accentColor = assetTypeUiSpec("貸付").accentColor,
                                    onClick = {
                                        selectedHistoryAssetName = null
                                        selectedHistoryCategory = "貸付"
                                        isHistoryMode = true
                                        isScheduledHistoryMode = false
                                    },
                                    onLongClick = {}
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        } else {
                            // 履歴表示 (isHistoryMode == true)
                            val historyIconMap = mapOf(
                                "食費" to Icons.Default.ShoppingCart,
                                "日用品" to Icons.Default.Build,
                                "交通費" to Icons.Default.Place,
                                "交際費" to Icons.Default.Favorite,
                                "娯楽" to Icons.Default.Star,
                                "美容" to Icons.Default.Face,
                                "健康" to Icons.Default.Info,
                                "その他" to Icons.Default.MoreHoriz,
                                "給与" to Icons.Default.AccountBalance,
                                "賞与" to Icons.Default.Star,
                                "副業" to Icons.Default.Build,
                                "お小遣い" to Icons.Default.Favorite,
                                "還付金" to Icons.Default.Info,
                                "貸付" to Icons.Outlined.RequestPage,
                                "回収" to Icons.Default.Handshake
                            )
                            val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)

                            val filteredTransactions = transactions.asSequence()
                                .filter { selectedHistoryAssetName == null || it.assetName == selectedHistoryAssetName }
                                .filter { selectedHistoryCategory == null || it.category == selectedHistoryCategory }
                                .filter { it.date in selectedStartDate..selectedEndDate }
                                .filter {
                                    when (selectedExpenseFilter) {
                                        "支出のみ" -> it.isExpense
                                        "収入のみ" -> !it.isExpense
                                        else -> true
                                    }
                                }
                                .sortedByDescending { it.date }
                                .toList()

                            val filteredScheduledExpenses = sortedScheduledExpenses.asSequence()
                                .filter { selectedHistoryAssetName == null || it.assetName == selectedHistoryAssetName }
                                .filter { selectedHistoryCategory == null || it.category == selectedHistoryCategory }
                                .filter { (it.date >= selectedStartDate) && (selectedPeriodLabel == "全て" || (it.date <= selectedEndDate)) }
                                .toList()

                            Column {
                                // フィルタバー
                                val isAnyFilterActive = selectedHistoryAssetName != null || selectedHistoryCategory != null || (selectedExpenseFilter != "全て" && !isScheduledHistoryMode) || selectedStartDate > 0L
                                val resetAction: () -> Unit = {
                                    selectedHistoryAssetName = null
                                    selectedHistoryCategory = null
                                    selectedExpenseFilter = "全て"
                                    selectedStartDate = 0L
                                    selectedEndDate = System.currentTimeMillis()
                                    selectedPeriodLabel = "全て"
                                }

                                data class FilterItem(val key: String, val active: Boolean)
                                val filterOrder = listOfNotNull(
                                    FilterItem("資産", selectedHistoryAssetName != null),
                                    FilterItem("ジャンル", selectedHistoryCategory != null),
                                    FilterItem("期間", selectedStartDate > 0L),
                                    if (!isScheduledHistoryMode) FilterItem("収支", selectedExpenseFilter != "全て") else null
                                ).asSequence()
                                    .filter { (selectedHistoryAssetName == null) || (it.key != "資産") }
                                    .sortedByDescending { it.active }
                                    .toList()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .height(IntrinsicSize.Max),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .fillMaxHeight()
                                            .background(if (isAnyFilterActive) NotionSafeGreen else Color.White, RoundedCornerShape(10.dp))
                                            .border(1.dp, if (isAnyFilterActive) NotionSafeGreen else NotionBorder, RoundedCornerShape(10.dp))
                                            .clickable(enabled = isAnyFilterActive, onClick = resetAction),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "フィルタ解除", tint = if (isAnyFilterActive) Color.White else NotionTextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                                    }

                                    filterOrder.forEach { filter ->
                                        when (filter.key) {
                                            "資産" -> {
                                                val active = selectedHistoryAssetName != null
                                                Box {
                                                    Surface(modifier = Modifier.width(100.dp).clickable { showAssetFilterMenu = true }, shape = RoundedCornerShape(10.dp), color = if (active) NotionSafeGreen.copy(alpha = 0.12f) else Color.White, border = BorderStroke(1.dp, if (active) NotionSafeGreen else NotionBorder)) {
                                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                            Text("資産", fontSize = 10.sp, color = NotionTextSecondary, fontWeight = FontWeight.Medium)
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(text = selectedHistoryAssetName ?: "全て", fontSize = 12.sp, color = if (active) NotionSafeGreen else NotionTextPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                    DropdownMenu(expanded = showAssetFilterMenu, onDismissRequest = { showAssetFilterMenu = false }, modifier = Modifier.background(Color.White)) {
                                                        DropdownMenuItem(text = { Text("全て", fontSize = 13.sp, color = if (selectedHistoryAssetName == null) NotionSafeGreen else NotionTextPrimary, fontWeight = if (selectedHistoryAssetName == null) FontWeight.Bold else FontWeight.Normal) }, onClick = { selectedHistoryAssetName = null; showAssetFilterMenu = false })
                                                        assetsFromDb.sortedBy { it.name }.forEach { asset ->
                                                            DropdownMenuItem(text = { Text(asset.name, fontSize = 13.sp, color = if (selectedHistoryAssetName == asset.name) NotionSafeGreen else NotionTextPrimary, fontWeight = if (selectedHistoryAssetName == asset.name) FontWeight.Bold else FontWeight.Normal) }, onClick = { selectedHistoryAssetName = asset.name; showAssetFilterMenu = false })
                                                        }
                                                    }
                                                }
                                            }
                                            "ジャンル" -> {
                                                val active = selectedHistoryCategory != null
                                                Box {
                                                    Surface(modifier = Modifier.width(100.dp).clickable { showCategoryFilterMenu = true }, shape = RoundedCornerShape(10.dp), color = if (active) NotionSafeGreen.copy(alpha = 0.12f) else Color.White, border = BorderStroke(1.dp, if (active) NotionSafeGreen else NotionBorder)) {
                                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                            Text("ジャンル", fontSize = 10.sp, color = NotionTextSecondary, fontWeight = FontWeight.Medium)
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(text = selectedHistoryCategory ?: "全て", fontSize = 12.sp, color = if (active) NotionSafeGreen else NotionTextPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                    DropdownMenu(expanded = showCategoryFilterMenu, onDismissRequest = { showCategoryFilterMenu = false }, modifier = Modifier.background(Color.White)) {
                                                        val catList = if (isScheduledHistoryMode) {
                                                            listOf("全て","食費","日用品","交通費","交際費","娯楽","美容","健康","その他")
                                                        } else {
                                                            listOf("全て","食費","日用品","交通費","交際費","娯楽","美容","健康","その他","給与","賞与","副業","お小遣い","還付金")
                                                        }
                                                        catList.forEach { cat ->
                                                            DropdownMenuItem(text = { Text(cat, fontSize = 13.sp, color = if ((cat=="全て" && selectedHistoryCategory==null) || selectedHistoryCategory==cat) NotionSafeGreen else NotionTextPrimary, fontWeight = if ((cat=="全て" && selectedHistoryCategory==null) || selectedHistoryCategory==cat) FontWeight.Bold else FontWeight.Normal) }, onClick = { selectedHistoryCategory = if (cat=="全て") null else cat; showCategoryFilterMenu = false })
                                                        }
                                                    }
                                                }
                                            }
                                            "期間" -> {
                                                val active = selectedStartDate > 0L
                                                Box {
                                                    Surface(modifier = Modifier.width(100.dp).clickable { showPeriodFilterMenu = true }, shape = RoundedCornerShape(10.dp), color = if (active) NotionSafeGreen.copy(alpha = 0.12f) else Color.White, border = BorderStroke(1.dp, if (active) NotionSafeGreen else NotionBorder)) {
                                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                            Text("期間", fontSize = 10.sp, color = NotionTextSecondary, fontWeight = FontWeight.Medium)
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(text = selectedPeriodLabel, fontSize = 12.sp, color = if (active) NotionSafeGreen else NotionTextPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                    DropdownMenu(expanded = showPeriodFilterMenu, onDismissRequest = { showPeriodFilterMenu = false }, modifier = Modifier.background(Color.White)) {
                                                        data class PO(val label: String, val start: Long, val end: Long)
                                                        val now2 = System.currentTimeMillis()
                                                        val tmCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
                                                        val lmCal = (tmCal.clone() as Calendar).apply { add(Calendar.MONTH,-1) }
                                                        val tyCal = Calendar.getInstance().apply { set(Calendar.MONTH,0); set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
                                                        val lmEnd = (tmCal.clone() as Calendar).apply { add(Calendar.MILLISECOND,-1) }
                                                        listOf(
                                                            PO("全て",0L,now2 + 31536000000L), PO("今月",tmCal.timeInMillis,now2 + 31536000000L),
                                                            PO("先月",lmCal.timeInMillis,lmEnd.timeInMillis), PO("今年",tyCal.timeInMillis,now2 + 31536000000L),
                                                            PO("過去3ヶ月",(tmCal.clone() as Calendar).apply{add(Calendar.MONTH,-2)}.timeInMillis,now2),
                                                            PO("過去6ヶ月",(tmCal.clone() as Calendar).apply{add(Calendar.MONTH,-5)}.timeInMillis,now2)
                                                        ).forEach { opt ->
                                                            val isSel = selectedPeriodLabel == opt.label
                                                            DropdownMenuItem(text = { Text(opt.label, fontSize = 13.sp, color = if (isSel) NotionSafeGreen else NotionTextPrimary, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }, onClick = { selectedPeriodLabel = opt.label; selectedStartDate = opt.start; selectedEndDate = opt.end; showPeriodFilterMenu = false })
                                                        }
                                                    }
                                                }
                                            }
                                            "収支" -> {
                                                val active = selectedExpenseFilter != "全て"
                                                Box {
                                                    Surface(modifier = Modifier.width(100.dp).clickable { showExpenseFilterMenu = true }, shape = RoundedCornerShape(10.dp), color = if (active) NotionSafeGreen.copy(alpha = 0.12f) else Color.White, border = BorderStroke(1.dp, if (active) NotionSafeGreen else NotionBorder)) {
                                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                            Text("収支", fontSize = 10.sp, color = NotionTextSecondary, fontWeight = FontWeight.Medium)
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(text = selectedExpenseFilter, fontSize = 12.sp, color = if (active) NotionSafeGreen else NotionTextPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                    DropdownMenu(expanded = showExpenseFilterMenu, onDismissRequest = { showExpenseFilterMenu = false }, modifier = Modifier.background(Color.White)) {
                                                        listOf("全て","支出のみ","収入のみ").forEach { opt ->
                                                            val isSel = selectedExpenseFilter == opt
                                                            DropdownMenuItem(text = { Text(opt, fontSize = 13.sp, color = if (isSel) NotionSafeGreen else NotionTextPrimary, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }, onClick = { selectedExpenseFilter = opt; showExpenseFilterMenu = false })
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isScheduledHistoryMode) {
                                    if (filteredScheduledExpenses.isEmpty()) {
                                        Text(
                                            "予定された記録がありません",
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            color = NotionTextSecondary
                                        )
                                    } else {
                                        ScheduledExpenseSection(
                                            expenses = filteredScheduledExpenses,
                                            onComplete = { expense ->
                                                scope.launch {
                                                    dao.insertTransaction(TransactionEntity(
                                                        id = UUID.randomUUID().toString(),
                                                        name = expense.name,
                                                        amount = expense.amount,
                                                        category = expense.category,
                                                        date = System.currentTimeMillis(),
                                                        assetName = expense.assetName,
                                                        isExpense = true
                                                    ))
                                                    val asset = dao.getAssetByName(expense.assetName)
                                                    if (asset != null) {
                                                        dao.updateAsset(asset.copy(amount = asset.amount - expense.amount, lastUpdated = System.currentTimeMillis()))
                                                    }
                                                    dao.updateScheduledExpense(expense.copy(isCompleted = true))
                                                }
                                            },
                                            onDelete = { expense -> scheduledExpenseToDelete = expense }
                                        )
                                    }
                                } else {
                                    if (filteredTransactions.isEmpty()) {
                                        Text(
                                            if (selectedHistoryAssetName == null) "記録がありません" else "この財布の履歴はまだありません",
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            color = NotionTextSecondary
                                        )
                                    }

                                    val groupedByDate = filteredTransactions.groupBy { dateFormatter.format(Date(it.date)) }
                                    groupedByDate.forEach { (dateLabel, txList) ->
                                        Text(
                                            text = dateLabel,
                                            color = NotionTextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White, RoundedCornerShape(12.dp))
                                                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                                        ) {
                                            Column {
                                                txList.forEachIndexed { idx, tx ->
                                                    val historyItemColor = when {
                                                        tx.category == "貸付" -> Color(0xFFFFB300)
                                                        tx.category == "回収" -> Color(0xFF00897B)
                                                        tx.isExpense -> Color(0xFFE57373)
                                                        else -> NotionSafeGreen
                                                    }
                                                    AssetHistoryItem(
                                                        name = tx.name,
                                                        amount = "¥ ${String.format(Locale.JAPAN, "%,d", tx.amount)}",
                                                        memo = tx.category,
                                                        balanceAfter = tx.assetName,
                                                        color = historyItemColor,
                                                        icon = historyIconMap[tx.category] ?: Icons.Default.MoreHoriz,
                                                        onLongClick = { transactionToDelete = tx }
                                                    )
                                                    if (idx < txList.lastIndex) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 16.dp),
                                                            thickness = 0.5.dp,
                                                            color = NotionBorder
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showGroupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGroupSheet = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                Text("ジャンルを選択", modifier = Modifier.padding(vertical = 16.dp), color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    assetGroups.chunked(4).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { group ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AssetCategoryTile(group) {
                                        selectedGroupTitle = group
                                        editNameText = ""
                                        editAmountText = ""
                                        showGroupSheet = false
                                        showAddItemDialog = true
                                    }
                                }
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        ModalBottomSheet(
            onDismissRequest = { showAddItemDialog = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val spec = assetTypeUiSpec(selectedGroupTitle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { focusManager.clearFocus() }
                    .padding(horizontal = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(spec.accentColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(spec.icon, null, tint = spec.accentColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(text = "$selectedGroupTitle に追加", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
                }
                
                Spacer(Modifier.height(24.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, NotionBorder)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Default.Label, null, tint = NotionSafeGreen, modifier = Modifier.padding(10.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("資産の名称", fontSize = 12.sp, color = NotionTextSecondary)
                            androidx.compose.foundation.text.BasicTextField(
                                value = editNameText,
                                onValueChange = { editNameText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                decorationBox = { inner ->
                                    if (editNameText.isEmpty()) Text("〇〇銀行", color = NotionTextSecondary.copy(alpha = 0.5f), fontSize = 15.sp)
                                    inner()
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = NotionBackground,
                    border = BorderStroke(1.dp, NotionBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("初期残高", fontSize = 12.sp, color = NotionTextSecondary)
                        Text(
                            text = if (editAmountText.isEmpty()) "¥ 0" else "¥ $editAmountText",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = spec.accentColor
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                CustomKeypad(
                    onNumberClick = { num ->
                        val next = if (editAmountText == "0") num else editAmountText + num
                        editAmountText = formatAmountWithCommas(next)
                    },
                    onOperatorClick = { op ->
                        if (editAmountText.isNotEmpty() && !editAmountText.last().toString().matches(Regex("[-+*/.]"))) editAmountText += op
                    },
                    onDeleteClick = {
                        if (editAmountText.isNotEmpty()) {
                            editAmountText = formatAmountWithCommas(editAmountText.dropLast(1))
                        }
                    },
                    onClearAllClick = { editAmountText = "" },
                    onConfirmClick = {
                        try {
                            editAmountText = formatAmountWithCommas(evaluateExpression(editAmountText).toString())
                        } catch (_: Exception) {
                        }
                    },
                    onSaveClick = {
                        val cleanAmountText = editAmountText.replace(",", "").replace("¥", "").trim()
                        val amountText = if (cleanAmountText.any { it in "+-*/" }) {
                            try { evaluateExpression(cleanAmountText).toString() } catch (e: Exception) { cleanAmountText }
                        } else cleanAmountText
                        
                        val amount = amountText.replace("−", "-").toIntOrNull() ?: 0
                        scope.launch {
                            dao.insertAsset(AssetEntity(
                                id = UUID.randomUUID().toString(),
                                name = editNameText.ifBlank { "新規項目" },
                                amount = amount,
                                category = selectedGroupTitle,
                                lastUpdated = System.currentTimeMillis()
                            ))
                        }
                        showAddItemDialog = false
                    },
                    onCloseClick = { showAddItemDialog = false },
                    isSaveEnabled = editNameText.isNotBlank(),
                    actionColor = spec.accentColor
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    transactionToDelete?.let { tx ->
        DeleteConfirmDialog(
            text = "この履歴「${tx.name}」を削除し、残高を取り消しますか？",
            onDismiss = { transactionToDelete = null }
        ) {
            scope.launch {
                val asset = dao.getAssetByName(tx.assetName)
                if (asset != null) {
                    val restoredAsset = asset.copy(
                        amount = asset.amount + tx.amount,
                        lastUpdated = System.currentTimeMillis()
                    )
                    dao.updateAsset(restoredAsset)
                }
                dao.deleteTransaction(tx)
                transactionToDelete = null
            }
        }
    }

    scheduledExpenseToDelete?.let { expense ->
        DeleteConfirmDialog(
            text = "この予定「${expense.name}」を削除しますか？",
            onDismiss = { scheduledExpenseToDelete = null }
        ) {
            scope.launch {
                dao.deleteScheduledExpense(expense)
                scheduledExpenseToDelete = null
            }
        }
    }

    // --- 残高内訳 資産タップ時のアクションメニュー ---
    editingAssetEntity?.let { asset ->
        if (!showAssetAmountEdit && !showAssetNameEdit && !showAssetDeleteConfirm) {
            val spec = assetTypeUiSpec(asset.category)
            ModalBottomSheet(
                onDismissRequest = { editingAssetEntity = null },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                    // ヘッダー的な資産情報カード
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = NotionBackground,
                        border = BorderStroke(1.dp, NotionBorder)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(NotionBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(spec.icon, null, tint = spec.accentColor, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(asset.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
                                Text(asset.category, fontSize = 12.sp, color = NotionTextSecondary)
                            }
                            Text(
                                "¥ ${String.format(Locale.JAPAN, "%,d", asset.amount)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = spec.accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("アクション", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NotionTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = NotionBackground,
                        border = BorderStroke(1.dp, NotionBorder)
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text("名称を変更する", fontSize = 15.sp, fontWeight = FontWeight.Medium) },
                                supportingContent = { Text("資産の名称を編集します", fontSize = 12.sp) },
                                leadingContent = { 
                                    Box(modifier = Modifier.size(40.dp).background(NotionBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Label, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(20.dp))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    editNameText = asset.name
                                    showAssetNameEdit = true
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NotionBorder)
                            ListItem(
                                headlineContent = { Text("金額を修正する", fontSize = 15.sp, fontWeight = FontWeight.Medium) },
                                supportingContent = { Text("現在の残高を直接書き換えます", fontSize = 12.sp) },
                                leadingContent = { 
                                    Box(modifier = Modifier.size(40.dp).background(NotionBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(20.dp))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    editAmountText = formatAmountWithCommas(asset.amount.toString())
                                    showAssetAmountEdit = true
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NotionBorder)
                            ListItem(
                                headlineContent = { Text("この資産を削除", color = Color(0xFFD32F2F), fontSize = 15.sp, fontWeight = FontWeight.Medium) },
                                supportingContent = { Text("資産一覧から削除します（履歴は保持されます）", fontSize = 12.sp) },
                                leadingContent = { 
                                    Box(modifier = Modifier.size(40.dp).background(NotionBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { showAssetDeleteConfirm = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- 名称編集シート ---
    if (showAssetNameEdit) {
        val asset = editingAssetEntity
        if (asset != null) {
            val spec = assetTypeUiSpec(asset.category)
            ModalBottomSheet(
                onDismissRequest = { showAssetNameEdit = false; editingAssetEntity = null },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(spec.accentColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(spec.icon, null, tint = spec.accentColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(text = "名称を変更", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
                    }
                    
                    Spacer(Modifier.height(24.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, NotionBorder)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                                Icon(Icons.Default.Label, null, tint = NotionSafeGreen, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("資産の名称", fontSize = 12.sp, color = NotionTextSecondary)
                                androidx.compose.foundation.text.BasicTextField(
                                    value = editNameText,
                                    onValueChange = { editNameText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                    decorationBox = { inner ->
                                        if (editNameText.isEmpty()) Text("名称を入力", color = NotionTextSecondary.copy(alpha = 0.5f), fontSize = 15.sp)
                                        inner()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (editNameText.isNotBlank()) {
                                val oldName = asset.name
                                val newName = editNameText.trim()
                                scope.launch {
                                    dao.updateAsset(asset.copy(name = newName, lastUpdated = System.currentTimeMillis()))
                                    if (oldName != newName) {
                                        dao.updateTransactionAssetName(oldName, newName)
                                        dao.updateTransactionToAssetName(oldName, newName)
                                        dao.updateScheduledExpenseAssetName(oldName, newName)
                                        dao.updateLendingLoanAsset(oldName, newName)
                                        dao.updateLendingReturnAsset(oldName, newName)
                                    }
                                }
                                showAssetNameEdit = false
                                editingAssetEntity = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = spec.accentColor),
                        shape = RoundedCornerShape(16.dp),
                        enabled = editNameText.isNotBlank()
                    ) {
                        Text("保存する", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- 金額編集シート ---
    if (showAssetAmountEdit) {
        val asset = editingAssetEntity
        if (asset != null) {
            val spec = assetTypeUiSpec(asset.category)
            ModalBottomSheet(
                onDismissRequest = { showAssetAmountEdit = false; editingAssetEntity = null },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(spec.accentColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(spec.icon, null, tint = spec.accentColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(text = "金額を修正", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
                            Text(asset.name, fontSize = 12.sp, color = NotionTextSecondary)
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = NotionBackground,
                        border = BorderStroke(1.dp, NotionBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("現在の残高", fontSize = 12.sp, color = NotionTextSecondary)
                            Text(
                                text = "¥ $editAmountText",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = spec.accentColor
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    CustomKeypad(
                        onNumberClick = { num ->
                            val next = if (editAmountText == "0") num else editAmountText + num
                            editAmountText = formatAmountWithCommas(next)
                        },
                        onOperatorClick = { op ->
                            if (editAmountText.isNotEmpty() && !editAmountText.last().toString().matches(Regex("[-+*/.]"))) editAmountText += op
                        },
                        onDeleteClick = {
                            if (editAmountText.isNotEmpty()) {
                                editAmountText = formatAmountWithCommas(editAmountText.dropLast(1))
                            }
                        },
                        onClearAllClick = { editAmountText = "" },
                        onConfirmClick = {
                            try {
                                editAmountText = formatAmountWithCommas(evaluateExpression(editAmountText).toString())
                            } catch (_: Exception) {
                            }
                        },
                        onSaveClick = {
                            val cleanAmountText = editAmountText.replace(",", "").replace("¥", "").trim()
                            val amountText = if (cleanAmountText.any { it in "+-*/" }) {
                                try { evaluateExpression(cleanAmountText).toString() } catch (e: Exception) { cleanAmountText }
                            } else cleanAmountText

                            val newAmount = amountText.replace("−", "-").toIntOrNull() ?: asset.amount
                            val diff = newAmount - asset.amount
                            scope.launch {
                                dao.updateAsset(asset.copy(amount = newAmount, lastUpdated = System.currentTimeMillis()))
                                if (diff != 0) {
                                    dao.insertTransaction(TransactionEntity(
                                        id = UUID.randomUUID().toString(),
                                        name = "${asset.name} 残高修正",
                                        amount = kotlin.math.abs(diff),
                                        category = asset.category,
                                        date = System.currentTimeMillis(),
                                        assetName = asset.name,
                                        isExpense = diff < 0
                                    ))
                                }
                            }
                            showAssetAmountEdit = false
                            editingAssetEntity = null
                        },
                        onCloseClick = { showAssetAmountEdit = false; editingAssetEntity = null },
                        isSaveEnabled = true,
                        actionColor = spec.accentColor
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // --- 削除確認ダイアログ ---
    if (showAssetDeleteConfirm) {
        val asset = editingAssetEntity
        if (asset != null) {
            DeleteConfirmDialog(
                text = "「${asset.name}」を削除しますか？",
                onDismiss = { showAssetDeleteConfirm = false; editingAssetEntity = null }
            ) {
                scope.launch { dao.deleteAsset(asset) }
                showAssetDeleteConfirm = false
                editingAssetEntity = null
            }
        }
    }
}

@Composable
fun ScheduledExpenseSection(
    expenses: List<ScheduledExpenseEntity>,
    onComplete: (ScheduledExpenseEntity) -> Unit,
    onDelete: (ScheduledExpenseEntity) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN) }
    
    Column {
        if (expenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(1.dp, NotionBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("予定はありません", color = NotionTextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        } else {
            val groupedByDate = expenses.groupBy { dateFormatter.format(Date(it.date)) }
            groupedByDate.forEach { (dateLabel, expenseList) ->
                Text(
                    text = dateLabel,
                    color = NotionTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    expenseList.forEach { expense ->
                        ScheduledExpenseRow(
                            expense = expense,
                            onComplete = { onComplete(expense) },
                            onDelete = { onDelete(expense) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduledExpenseRow(
    expense: ScheduledExpenseEntity,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("M/d", Locale.JAPAN) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (expense.isCompleted) Color(0xFFF8F8F8) else Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, if (expense.isCompleted) Color.Transparent else NotionBorder, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = onDelete
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (expense.isCompleted) NotionTextSecondary.copy(alpha = 0.1f) 
                        else NotionSafeGreen.copy(alpha = 0.08f), 
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expense.isRecurring) Icons.Default.Autorenew else Icons.Default.Event,
                    contentDescription = null,
                    tint = if (expense.isCompleted) NotionTextSecondary.copy(alpha = 0.4f) else NotionSafeGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.name,
                    color = if (expense.isCompleted) NotionTextSecondary else NotionTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expense.isRecurring) "毎月 ${SimpleDateFormat("d", Locale.JAPAN).format(Date(expense.date))}日" else dateFormatter.format(Date(expense.date)),
                        color = NotionTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(expense.assetName, color = NotionTextSecondary, fontSize = 12.sp)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "¥ ${String.format(Locale.JAPAN, "%,d", expense.amount)}",
                    color = if (expense.isCompleted) NotionTextSecondary else NotionTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (!expense.isCompleted) {
                    Surface(
                        onClick = onComplete,
                        shape = RoundedCornerShape(8.dp),
                        color = NotionSafeGreen,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "支払完了",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NotionSafeGreen.copy(alpha = 0.1f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "完了",
                                tint = NotionSafeGreen.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
