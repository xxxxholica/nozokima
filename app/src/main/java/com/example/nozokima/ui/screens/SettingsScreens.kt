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
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NotionBackground)
    ) {
        ScreenHeader(
            title = "設定",
            navigationIcon = {
                Surface(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = NotionTextSecondary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
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

@Composable
fun CategoryManagementScreen(
    dao: FinanceDao,
    onBack: () -> Unit
) {
    val categories by dao.getAllCategories().collectAsState(initial = emptyList())
    var editCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    BackHandler {
        if (isEditing) {
            isEditing = false
            editCategory = null
        } else {
            onBack()
        }
    }

    if (isEditing) {
        CategoryEditScreen(
            category = editCategory,
            onSave = { name, type, icon ->
                scope.launch {
                    if (editCategory == null) {
                        dao.insertCategory(CategoryEntity(UUID.randomUUID().toString(), name, type, icon))
                    } else {
                        dao.updateCategory(editCategory!!.copy(name = name, type = type, iconName = icon))
                    }
                    isEditing = false
                    editCategory = null
                }
            },
            onBack = {
                isEditing = false
                editCategory = null
            }
        )
    } else {
        CategoryListScreen(
            categories = categories,
            onAdd = {
                editCategory = null
                isEditing = true
            },
            onEdit = {
                editCategory = it
                isEditing = true
            },
            onDelete = {
                scope.launch { dao.deleteCategory(it) }
            },
            onBack = onBack
        )
    }
}

@Composable
fun CategoryListScreen(
    categories: List<CategoryEntity>,
    onAdd: () -> Unit,
    onEdit: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 支出, 1: 収入
    val filteredCategories = categories.filter {
        if (selectedTab == 0) it.type == "EXPENSE" else it.type == "INCOME"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NotionBackground)
    ) {
        ScreenHeader(
            title = "カテゴリ管理",
            titleStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            navigationIcon = {
                Surface(
                    onClick = onBack,
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
            Surface(
                onClick = onAdd,
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = NotionSafeGreen.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = NotionSafeGreen, modifier = Modifier.size(20.dp))
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = NotionSafeGreen,
            divider = { HorizontalDivider(color = NotionBorder) }
        ) {
            listOf("支出", "収入").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    selectedContentColor = NotionSafeGreen,
                    unselectedContentColor = NotionTextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredCategories) { cat ->
                val icon = mapIconNameToVector(cat.iconName)
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, NotionBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(NotionSafeGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = NotionSafeGreen, modifier = Modifier.size(20.dp))
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NotionTextPrimary)
                            Text(if (cat.type == "EXPENSE") "支出" else "収入", fontSize = 12.sp, color = NotionTextSecondary)
                        }
                        
                        Row {
                            IconButton(onClick = { onEdit(cat) }) {
                                Icon(Icons.Default.Edit, null, tint = NotionTextSecondary, modifier = Modifier.size(20.dp))
                            }
                            if (!cat.isDefault) {
                                IconButton(onClick = { onDelete(cat) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFE57373), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    category: CategoryEntity?,
    onSave: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var nameText by remember { mutableStateOf(category?.name ?: "") }
    var selectedType by remember { mutableStateOf(category?.type ?: "EXPENSE") }
    var selectedIcon by remember { mutableStateOf(category?.iconName ?: "MoreHoriz") }

    val iconOptions = listOf(
        "ShoppingCart", "Restaurant", "LocalMall", "Checkroom", "LocalCafe",
        "DirectionsCar", "Flight", "Home", "School", "Build", "FitnessCenter", 
        "MedicalServices", "Payments", "Savings", "CardGiftcard", "Celebration", 
        "Code", "Place", "Favorite", "Star", "Face", "Info", "AccountBalance", "MoreHoriz"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NotionBackground)
    ) {
        ScreenHeader(
            title = if (category == null) "カテゴリ追加" else "カテゴリ編集",
            titleStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            navigationIcon = {
                Surface(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = NotionTextSecondary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == "EXPENSE",
                    onClick = { selectedType = "EXPENSE" },
                    label = { Text("支出") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NotionSafeGreen.copy(alpha = 0.12f),
                        selectedLabelColor = NotionSafeGreen
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = NotionBorder,
                        selectedBorderColor = NotionSafeGreen,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp,
                        enabled = true,
                        selected = selectedType == "EXPENSE"
                    )
                )
                FilterChip(
                    selected = selectedType == "INCOME",
                    onClick = { selectedType = "INCOME" },
                    label = { Text("収入") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NotionSafeGreen.copy(alpha = 0.12f),
                        selectedLabelColor = NotionSafeGreen
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = NotionBorder,
                        selectedBorderColor = NotionSafeGreen,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp,
                        enabled = true,
                        selected = selectedType == "INCOME"
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, NotionBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("カテゴリの名称", fontSize = 12.sp, color = NotionTextSecondary)
                    androidx.compose.foundation.text.BasicTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = NotionTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        decorationBox = { inner ->
                            if (nameText.isEmpty()) Text(
                                "趣味",
                                color = NotionTextSecondary.copy(alpha = 0.5f),
                                fontSize = 16.sp
                            )
                            inner()
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "アイコンを選択",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = NotionTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            // アイコングリッド（スクロール可能にするために LazyVerticalGrid ではなく Column + Row を使用）
            // verticalScroll の中なので Lazy ではない
            val chunkedIcons = iconOptions.chunked(4)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                chunkedIcons.forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowIcons.forEach { iconName ->
                            val icon = mapIconNameToVector(iconName)
                            val isSelected = selectedIcon == iconName
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            if (isSelected) NotionSafeGreen.copy(alpha = 0.1f)
                                            else Color.White,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) NotionSafeGreen else NotionBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { selectedIcon = iconName },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        icon,
                                        null,
                                        tint = if (isSelected) NotionSafeGreen else NotionTextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        repeat(4 - rowIcons.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { onSave(nameText, selectedType, selectedIcon) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen),
                enabled = nameText.isNotBlank()
            ) {
                Text("保存", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

fun mapIconNameToVector(name: String): ImageVector {
    return when (name) {
        "ShoppingCart" -> Icons.Default.ShoppingCart
        "Restaurant" -> Icons.Default.Restaurant
        "LocalMall" -> Icons.Default.LocalMall
        "Checkroom" -> Icons.Default.Checkroom
        "LocalCafe" -> Icons.Default.LocalCafe
        "DirectionsCar" -> Icons.Default.DirectionsCar
        "DirectionsBus" -> Icons.Default.DirectionsBus
        "Flight" -> Icons.Default.Flight
        "Home" -> Icons.Default.Home
        "Wifi" -> Icons.Default.Wifi
        "PhoneIphone" -> Icons.Default.PhoneIphone
        "School" -> Icons.Default.School
        "Build" -> Icons.Default.Build
        "FitnessCenter" -> Icons.Default.FitnessCenter
        "MedicalServices" -> Icons.Default.MedicalServices
        "Payments" -> Icons.Default.Payments
        "Savings" -> Icons.Default.Savings
        "CardGiftcard" -> Icons.Default.CardGiftcard
        "Celebration" -> Icons.Default.Celebration
        "TheaterComedy" -> Icons.Default.TheaterComedy
        "Movie" -> Icons.Default.Movie
        "Pets" -> Icons.Default.Pets
        "Brush" -> Icons.Default.Brush
        "Code" -> Icons.Default.Code
        "Place" -> Icons.Default.Place
        "Favorite" -> Icons.Default.Favorite
        "Star" -> Icons.Default.Star
        "Face" -> Icons.Default.Face
        "Info" -> Icons.Default.Info
        "AccountBalance" -> Icons.Default.AccountBalance
        else -> Icons.Default.MoreHoriz
    }
}
