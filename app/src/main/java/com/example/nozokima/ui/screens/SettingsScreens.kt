@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.nozokima.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.*
import com.example.nozokima.model.*
import com.example.nozokima.data.local.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.components.*
import kotlinx.coroutines.launch
import ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GeneralSettingsScreen(
    appLockEnabled: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    biometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    isBiometricAvailable: Boolean,
    onChangePassword: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onCategoryManagementClick: () -> Unit,
    onRecurringManagementClick: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NotionBackground)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(title = "設定")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // カスタマイズセクション
            SettingsSection(title = "カスタマイズ") {
                SettingsItem(
                    icon = Icons.Default.Category,
                    title = "カテゴリ管理",
                    description = "収支カテゴリの追加・編集・並べ替え",
                    onClick = onCategoryManagementClick
                )
                HorizontalDivider(color = NotionBorder, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Autorenew,
                    title = "固定費設定",
                    description = "毎月の自動入力を設定・管理",
                    onClick = onRecurringManagementClick
                )
            }

            // セキュリティセクション
            SettingsSection(title = "セキュリティ") {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "アプリロック",
                    description = "起動時にパスワード入力を求める",
                    trailing = {
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { onToggleAppLock(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NotionSafeGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = NotionBorder
                            )
                        )
                    }
                )
                if (appLockEnabled) {
                    HorizontalDivider(color = NotionBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.Password,
                        title = "パスワードの変更",
                        description = "現在のロック用パスワードを更新",
                        onClick = onChangePassword
                    )
                    if (isBiometricAvailable) {
                        HorizontalDivider(color = NotionBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Default.Fingerprint,
                            title = "生体認証を使用",
                            description = "指紋や顔認証でロックを解除する",
                            trailing = {
                                Switch(
                                    checked = biometricEnabled,
                                    onCheckedChange = { onToggleBiometric(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NotionSafeGreen,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = NotionBorder
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // データ管理セクション
            SettingsSection(title = "データ管理") {
                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = "データのエクスポート",
                    description = "現在のデータを暗号化してファイルに保存",
                    onClick = onExportClick
                )
                HorizontalDivider(color = NotionBorder, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = "データのインポート",
                    description = "バックアップファイルからデータを復旧",
                    onClick = onImportClick
                )
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = NotionTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, NotionBorder),
            content = { Column(content = content) }
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(description, fontSize = 12.sp, color = NotionTextSecondary) },
        leadingContent = { 
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NotionSafeGreen.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = NotionSafeGreen, modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = trailing,
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    dao: FinanceDao,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val categories by dao.getAllCategories().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var nameText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("EXPENSE") }
    var selectedIcon by remember { mutableStateOf("MoreHoriz") }
    val scope = rememberCoroutineScope()

    val iconOptions = listOf("ShoppingCart", "Build", "Place", "Favorite", "Star", "Face", "Info", "MoreHoriz", "AccountBalance")

    if (showAddDialog || editCategory != null) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; editCategory = null },
            title = { Text(if (editCategory == null) "カテゴリ追加" else "カテゴリ編集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = nameText, onValueChange = { nameText = it }, label = { Text("カテゴリ名") }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = selectedType == "EXPENSE", onClick = { selectedType = "EXPENSE" }, label = { Text("支出") })
                        FilterChip(selected = selectedType == "INCOME", onClick = { selectedType = "INCOME" }, label = { Text("収入") })
                    }
                    Text("アイコンを選択", fontSize = 12.sp, color = NotionTextSecondary)
                    LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(110.dp)) {
                        items(iconOptions) { iconName ->
                            val icon = when(iconName) {
                                "ShoppingCart" -> Icons.Default.ShoppingCart
                                "Build" -> Icons.Default.Build
                                "Place" -> Icons.Default.Place
                                "Favorite" -> Icons.Default.Favorite
                                "Star" -> Icons.Default.Star
                                "Face" -> Icons.Default.Face
                                "Info" -> Icons.Default.Info
                                "MoreHoriz" -> Icons.Default.MoreHoriz
                                "AccountBalance" -> Icons.Default.AccountBalance
                                else -> Icons.Default.MoreHoriz
                            }
                            val isSelected = selectedIcon == iconName
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NotionSafeGreen.copy(alpha = 0.1f) else Color.Transparent)
                                    .clickable { selectedIcon = iconName },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = if (isSelected) NotionSafeGreen else NotionTextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameText.isNotBlank(),
                    onClick = {
                    scope.launch {
                        if (editCategory == null) {
                            dao.insertCategory(CategoryEntity(UUID.randomUUID().toString(), nameText, selectedType, selectedIcon))
                        } else {
                            dao.updateCategory(editCategory!!.copy(name = nameText, type = selectedType, iconName = selectedIcon))
                        }
                        showAddDialog = false
                        editCategory = null
                        nameText = ""
                        selectedIcon = "MoreHoriz"
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editCategory = null }) { Text("キャンセル") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(NotionBackground)) {
        ScreenHeader(title = "カテゴリ管理", navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) {
            IconButton(onClick = { showAddDialog = true; nameText = ""; selectedType = "EXPENSE"; selectedIcon = "MoreHoriz" }) { Icon(Icons.Default.Add, null) }
        }
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(categories) { cat ->
                val icon = when(cat.iconName) {
                    "ShoppingCart" -> Icons.Default.ShoppingCart
                    "Build" -> Icons.Default.Build
                    "Place" -> Icons.Default.Place
                    "Favorite" -> Icons.Default.Favorite
                    "Star" -> Icons.Default.Star
                    "Face" -> Icons.Default.Face
                    "Info" -> Icons.Default.Info
                    "MoreHoriz" -> Icons.Default.MoreHoriz
                    "AccountBalance" -> Icons.Default.AccountBalance
                    else -> Icons.Default.MoreHoriz
                }
                ListItem(
                    leadingContent = { 
                        Box(
                            modifier = Modifier.size(36.dp).background(NotionSafeGreen.copy(alpha = 0.05f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = NotionSafeGreen, modifier = Modifier.size(20.dp))
                        }
                    },
                    headlineContent = { Text(cat.name) },
                    supportingContent = { Text(if (cat.type == "EXPENSE") "支出" else "収入") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { 
                                editCategory = cat
                                nameText = cat.name
                                selectedType = cat.type
                                selectedIcon = cat.iconName
                            }) { Icon(Icons.Default.Edit, null) }
                            if (!cat.isDefault) {
                                IconButton(onClick = { scope.launch { dao.deleteCategory(cat) } }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionManagementScreen(
    dao: FinanceDao,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val recurringList by dao.getAllRecurringTransactions().collectAsState(initial = emptyList())
    val assets by dao.getAllAssets().collectAsState(initial = emptyList())
    val categories by dao.getAllCategories().collectAsState(initial = emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editRecurring by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var nameText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedDay by remember { mutableIntStateOf(1) }
    var selectedAsset by remember { mutableStateOf<AssetEntity?>(null) }
    var selectedCategoryName by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    
    var showAssetSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDaySheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val iconMap = mapOf(
        "ShoppingCart" to Icons.Default.ShoppingCart,
        "Build" to Icons.Default.Build,
        "Place" to Icons.Default.Place,
        "Favorite" to Icons.Default.Favorite,
        "Star" to Icons.Default.Star,
        "Face" to Icons.Default.Face,
        "Info" to Icons.Default.Info,
        "MoreHoriz" to Icons.Default.MoreHoriz,
        "AccountBalance" to Icons.Default.AccountBalance
    )

    if (showAddDialog || editRecurring != null) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; editRecurring = null },
            title = { Text(if (editRecurring == null) "固定費の追加" else "固定費の編集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = nameText, onValueChange = { nameText = it }, label = { Text("名称（例: 家賃）") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = amountText, 
                        onValueChange = { amountText = it }, 
                        label = { Text("金額") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isExpense, onClick = { isExpense = true }, label = { Text("支出") })
                        FilterChip(selected = !isExpense, onClick = { isExpense = false }, label = { Text("収入") })
                    }
                    
                    InputTile(
                        icon = Icons.Default.CalendarMonth,
                        label = "毎月の該当日",
                        value = "${selectedDay}日",
                        onClick = { showDaySheet = true },
                        accentColor = NotionSafeGreen
                    )
                    
                    InputTile(
                        icon = Icons.Default.AccountBalanceWallet,
                        label = "資産",
                        value = selectedAsset?.name ?: "未選択",
                        onClick = { showAssetSheet = true },
                        accentColor = NotionSafeGreen,
                        isPlaceholder = selectedAsset == null
                    )
                    
                    InputTile(
                        icon = iconMap[categories.find { it.name == selectedCategoryName }?.iconName] ?: Icons.Default.Category,
                        label = "カテゴリ",
                        value = if (selectedCategoryName.isEmpty()) "未選択" else selectedCategoryName,
                        onClick = { showCategorySheet = true },
                        accentColor = NotionSafeGreen,
                        isPlaceholder = selectedCategoryName.isEmpty()
                    )
                }
            },
            confirmButton = {
                val isValid = nameText.isNotBlank() && amountText.toIntOrNull() != null && selectedAsset != null && selectedCategoryName.isNotEmpty()
                TextButton(
                    enabled = isValid,
                    onClick = {
                    scope.launch {
                        val entity = RecurringTransactionEntity(
                            id = editRecurring?.id ?: UUID.randomUUID().toString(),
                            name = nameText,
                            amount = amountText.toIntOrNull() ?: 0,
                            category = selectedCategoryName,
                            assetName = selectedAsset?.name ?: "",
                            dayOfMonth = selectedDay,
                            isExpense = isExpense,
                            lastProcessedDate = editRecurring?.lastProcessedDate ?: 0L
                        )
                        if (editRecurring == null) {
                            dao.insertRecurringTransaction(entity)
                        } else {
                            dao.updateRecurringTransaction(entity)
                        }
                        showAddDialog = false
                        editRecurring = null
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editRecurring = null }) { Text("キャンセル") } }
        )
    }

    if (showAssetSheet) {
        ModalBottomSheet(onDismissRequest = { showAssetSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("資産を選択", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                assets.forEach { asset ->
                    ListItem(
                        headlineContent = { Text(asset.name) },
                        trailingContent = { Text("¥ ${String.format(Locale.JAPAN, "%,d", asset.amount)}") },
                        modifier = Modifier.clickable { 
                            selectedAsset = asset
                            showAssetSheet = false 
                        }
                    )
                }
            }
        }
    }

    if (showCategorySheet) {
        val filteredCategories = categories.filter { if (isExpense) it.type == "EXPENSE" else it.type == "INCOME" }
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("カテゴリを選択", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.padding(horizontal = 12.dp)) {
                    items(filteredCategories) { cat ->
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { 
                                selectedCategoryName = cat.name
                                showCategorySheet = false 
                            }.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = if (selectedCategoryName == cat.name) NotionSafeGreen.copy(alpha = 0.1f) else NotionBackground) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(iconMap[cat.iconName] ?: Icons.Default.MoreHoriz, null, tint = if (selectedCategoryName == cat.name) NotionSafeGreen else NotionTextSecondary)
                                }
                            }
                            Text(cat.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showDaySheet) {
        ModalBottomSheet(onDismissRequest = { showDaySheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("該当日を選択", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.padding(12.dp)) {
                    items(31) { i ->
                        val day = i + 1
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selectedDay == day) NotionSafeGreen else Color.Transparent)
                                .clickable { selectedDay = day; showDaySheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$day", color = if (selectedDay == day) Color.White else NotionTextPrimary)
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(NotionBackground)) {
        ScreenHeader(title = "固定費設定", navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) {
            IconButton(onClick = { 
                showAddDialog = true
                nameText = ""
                amountText = ""
                selectedDay = 1
                selectedAsset = null
                selectedCategoryName = ""
                isExpense = true
                editRecurring = null
            }) { Icon(Icons.Default.Add, null) }
        }
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(recurringList) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text("毎月 ${item.dayOfMonth}日 / ¥${String.format(Locale.JAPAN, "%,d", item.amount)} / ${item.assetName}") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                editRecurring = item
                                nameText = item.name
                                amountText = item.amount.toString()
                                selectedDay = item.dayOfMonth
                                selectedAsset = assets.find { it.name == item.assetName }
                                selectedCategoryName = item.category
                                isExpense = item.isExpense
                            }) { Icon(Icons.Default.Edit, null) }
                            IconButton(onClick = { scope.launch { dao.deleteRecurringTransaction(item) } }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        }
                    }
                )
            }
        }
    }
}
