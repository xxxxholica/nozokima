package works.hinata.nozokima.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.hinata.nozokima.data.local.FinanceDao
import works.hinata.nozokima.data.local.entities.AppSettingsEntity
import works.hinata.nozokima.data.local.entities.AssetEntity
import works.hinata.nozokima.model.assetTypeUiSpec
import works.hinata.nozokima.ui.components.*
import works.hinata.nozokima.util.evaluateExpression
import works.hinata.nozokima.util.formatAmountWithCommas
import kotlinx.coroutines.launch
import ui.theme.*
import java.util.UUID

@Composable
fun SetupWizardScreen(
    dao: FinanceDao,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = NotionBackground) {
        Box(modifier = Modifier.safeDrawingPadding()) {
            InitialAssetStep(
                dao = dao,
                onNext = {
                    scope.launch {
                        val settings = dao.getAppSettingsSync() ?: AppSettingsEntity()
                        dao.upsertAppSettings(settings.copy(isSetupCompleted = true))
                        onComplete()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialAssetStep(dao: FinanceDao, onNext: () -> Unit) {
    val assetsFromDb by dao.getAllAssets().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showGroupSheet by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedGroupTitle by remember { mutableStateOf("") }
    var editNameText by remember { mutableStateOf("") }
    var editAmountText by remember { mutableStateOf("") }
    var editPointAmountText by remember { mutableStateOf("") }
    var assetToDelete by remember { mutableStateOf<AssetEntity?>(null) }

    val assetGroups = listOf("現金", "銀行", "電子マネー", "カード", "貯蓄", "投資", "貸付", "カードローン", "ローン", "保険", "デビットカード", "その他")

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("資産を登録", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
        Spacer(Modifier.height(12.dp))
        Text("現在の手元の現金や銀行残高を入力してください。\n複数登録可能です。", fontSize = 14.sp, color = NotionTextSecondary, textAlign = TextAlign.Center)
        
        Spacer(Modifier.height(32.dp))
        
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            assetsFromDb.forEach { asset ->
                val spec = assetTypeUiSpec(asset.category)
                UnifiedAssetCardRow(
                    title = asset.name,
                    subtitle = asset.category,
                    amount = asset.amount,
                    icon = spec.icon,
                    accentColor = spec.accentColor,
                    onClick = { /* Could add edit */ },
                    onLongClick = { assetToDelete = asset },
                    pointAmount = asset.pointAmount
                )
            }

            Surface(
                onClick = { showGroupSheet = true },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null, tint = NotionSafeGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("資産を追加", color = NotionSafeGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen)
        ) {
            Text(if (assetsFromDb.isEmpty()) "後で登録する" else "次へ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    if (showGroupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGroupSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                Text("カテゴリを選択", modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    assetGroups.chunked(4).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { group ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AssetCategoryTile(group) {
                                        selectedGroupTitle = group
                                        showGroupSheet = false
                                        editNameText = ""
                                        editAmountText = ""
                                        editPointAmountText = ""
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
            onDismissRequest = {
                showAddItemDialog = false
            },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            val focusManager = LocalFocusManager.current
            val spec = assetTypeUiSpec(selectedGroupTitle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
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
                    Text(text = "$selectedGroupTitle に追加", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                
                Spacer(Modifier.height(24.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                            Icon(Icons.AutoMirrored.Filled.Label, null, tint = NotionSafeGreen, modifier = Modifier.padding(10.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("資産の名称", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.foundation.text.BasicTextField(
                                value = editNameText,
                                onValueChange = { editNameText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                decorationBox = { inner ->
                                    if (editNameText.isEmpty()) Text("名称を入力", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 15.sp)
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
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("初期残高", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (editAmountText.isEmpty()) "¥ 0" else "¥ $editAmountText",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = spec.accentColor
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("内 ポイント", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (editPointAmountText.isEmpty()) "¥ 0" else "¥ $editPointAmountText",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                var keypadTarget by remember { mutableStateOf("amount") } // "amount" or "point"
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = { keypadTarget = "amount" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (keypadTarget == "amount") spec.accentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (keypadTarget == "amount") spec.accentColor else MaterialTheme.colorScheme.outline)
                    ) {
                        Text("残高を入力", modifier = Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (keypadTarget == "amount") spec.accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        onClick = { keypadTarget = "point" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (keypadTarget == "point") spec.accentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (keypadTarget == "point") spec.accentColor else MaterialTheme.colorScheme.outline)
                    ) {
                        Text("ポイントを入力", modifier = Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (keypadTarget == "point") spec.accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                CustomKeypad(
                    onNumberClick = { num ->
                        if (keypadTarget == "amount") {
                            val next = if (editAmountText == "0") num else editAmountText + num
                            editAmountText = formatAmountWithCommas(next)
                        } else {
                            val next = if (editPointAmountText == "0") num else editPointAmountText + num
                            editPointAmountText = formatAmountWithCommas(next)
                        }
                    },
                    onOperatorClick = { op ->
                        if (keypadTarget == "amount") {
                            if (editAmountText.isNotEmpty() && !editAmountText.last().toString().matches(Regex("[-+*/.]"))) editAmountText += op
                        } else {
                            if (editPointAmountText.isNotEmpty() && !editPointAmountText.last().toString().matches(Regex("[-+*/.]"))) editPointAmountText += op
                        }
                    },
                    onDeleteClick = {
                        if (keypadTarget == "amount") {
                            if (editAmountText.isNotEmpty()) editAmountText = formatAmountWithCommas(editAmountText.dropLast(1))
                        } else {
                            if (editPointAmountText.isNotEmpty()) editPointAmountText = formatAmountWithCommas(editPointAmountText.dropLast(1))
                        }
                    },
                    onClearAllClick = { if (keypadTarget == "amount") editAmountText = "" else editPointAmountText = "" },
                    onConfirmClick = {
                        if (keypadTarget == "amount") {
                            try { editAmountText = formatAmountWithCommas(evaluateExpression(editAmountText).toString()) } catch (_: Exception) {}
                        } else {
                            try { editPointAmountText = formatAmountWithCommas(evaluateExpression(editPointAmountText).toString()) } catch (_: Exception) {}
                        }
                    },
                    onSaveClick = {
                        val cleanAmountText = editAmountText.replace(",", "").replace("¥", "").trim()
                        val amountValue = if (cleanAmountText.any { it in "+-*/" }) {
                            try { evaluateExpression(cleanAmountText).toString() } catch (_: Exception) { cleanAmountText }
                        } else cleanAmountText
                        
                        val cleanPointText = editPointAmountText.replace(",", "").replace("¥", "").trim()
                        val pointValue = if (cleanPointText.any { it in "+-*/" }) {
                            try { evaluateExpression(cleanPointText).toString() } catch (_: Exception) { cleanPointText }
                        } else cleanPointText
                        
                        val amount = amountValue.replace("−", "-").toIntOrNull() ?: 0
                        val pointAmount = pointValue.toIntOrNull() ?: 0
                        scope.launch {
                            dao.insertAsset(AssetEntity(
                                id = UUID.randomUUID().toString(),
                                name = editNameText.ifBlank { "新規資産" },
                                amount = amount,
                                pointAmount = pointAmount,
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

    assetToDelete?.let { asset ->
        DeleteConfirmDialog(
            text = "「${asset.name}」を登録解除しますか？",
            onDismiss = { assetToDelete = null }
        ) {
            scope.launch {
                dao.deleteAsset(asset)
            }
            assetToDelete = null
        }
    }
}
