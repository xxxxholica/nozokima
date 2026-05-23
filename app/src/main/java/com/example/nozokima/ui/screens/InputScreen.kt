package com.example.nozokima.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.RequestPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

import com.example.nozokima.model.*
import com.example.nozokima.data.local.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.components.AssetCategoryTile
import com.example.nozokima.ui.components.CustomKeypad
import com.example.nozokima.ui.components.InputTile
import com.example.nozokima.ui.components.ScreenHeader
import com.example.nozokima.ui.components.SuccessOverlay
import com.example.nozokima.util.evaluateExpression
import com.example.nozokima.util.formatAmountWithCommas
import kotlinx.coroutines.launch
import ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TakePictureWithExplicitGrants : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newRawUri(null, input)
        }
    }
}

data class OcrPrediction(
    val amount: String,
    val date: Long,
    val assetName: String,
    val categoryName: String,
    val memo: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InputScreen(
    dao: FinanceDao,
    gemini: GeminiNanoModel? = null,
    ocrManager: OcrManager? = null,
    initialRecovery: LendingEntity? = null,
    initialMode: String? = null,
    onRecoveryHandled: () -> Unit = {},
    onExternalActivityLaunch: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val modes = listOf("収入", "支出", "振替", "貸付", "回収")
    var selectedMode by remember(initialRecovery, initialMode) { 
        mutableStateOf(
            when {
                initialRecovery != null -> "回収"
                (initialMode != null) && (initialMode in modes) -> initialMode
                else -> "支出"
            },
        ) 
    }
    var selectedPaymentType by remember { mutableStateOf("即時") }
    
    var amountText by remember { mutableStateOf(if (initialRecovery != null) (initialRecovery.amount - initialRecovery.recoveredAmount).toString() else "") }
    var memoText by remember { mutableStateOf(initialRecovery?.memo ?: "") }
    var personName by remember { mutableStateOf(initialRecovery?.personName ?: "") }
    var selectedLending by remember { mutableStateOf(initialRecovery) }
    
    var selectedAssetEntity by remember { mutableStateOf<AssetEntity?>(null) }
    var selectedToAssetEntity by remember { mutableStateOf<AssetEntity?>(null) }
    var showAssetSheet by remember { mutableStateOf(value = false) }
    var showToAssetSheet by remember { mutableStateOf(value = false) }
    var showCategorySheet by remember { mutableStateOf(value = false) }
    var showTypeSheet by remember { mutableStateOf(value = false) }
    var showPaymentMethodSheet by remember { mutableStateOf(value = false) }
    var showLendingSheet by remember { mutableStateOf(value = false) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MM月dd日(E)", Locale.JAPAN) }
    var selectedCategory by remember { mutableStateOf<CategoryData?>(null) }
    var showKeypad by remember { mutableStateOf(false) }
    var showOcrOptions by remember { mutableStateOf(false) }
    
    var isAnalyzingOcr by remember { mutableStateOf(false) }
    var ocrPredictions by remember { mutableStateOf<List<OcrPrediction>>(emptyList()) }
    var showOcrResultPage by remember { mutableStateOf(false) }
    var successInfo by remember { mutableStateOf<SavedRecordInfo?>(null) }

    val memoBringIntoViewRequester = remember { BringIntoViewRequester() }
    val personBringIntoViewRequester = remember { BringIntoViewRequester() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val ime = WindowInsets.ime
    val isKeyboardVisible by remember {
        derivedStateOf {
            ime.getBottom(density) > 0
        }
    }

    val dbAssets by dao.getAllAssets().collectAsState(initial = emptyList())
    val customCategories by dao.getAllCategories().collectAsState(initial = emptyList())
    val allLendings by dao.getAllLendings().collectAsState(initial = emptyList())
    val activeLendings = allLendings.filter { !it.isRecovered }

    val tempImageUri = remember {
        val file = File(context.cacheDir, "temp_ocr_image.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val iconMap = mapOf(
        "ShoppingCart" to Icons.Default.ShoppingCart,
        "Restaurant" to Icons.Default.Restaurant,
        "LocalMall" to Icons.Default.LocalMall,
        "Checkroom" to Icons.Default.Checkroom,
        "LocalCafe" to Icons.Default.LocalCafe,
        "DirectionsCar" to Icons.Default.DirectionsCar,
        "DirectionsBus" to Icons.Default.DirectionsBus,
        "Flight" to Icons.Default.Flight,
        "Home" to Icons.Default.Home,
        "Wifi" to Icons.Default.Wifi,
        "PhoneIphone" to Icons.Default.PhoneIphone,
        "School" to Icons.Default.School,
        "Build" to Icons.Default.Build,
        "FitnessCenter" to Icons.Default.FitnessCenter,
        "MedicalServices" to Icons.Default.MedicalServices,
        "Payments" to Icons.Default.Payments,
        "Savings" to Icons.Default.Savings,
        "CardGiftcard" to Icons.Default.CardGiftcard,
        "Celebration" to Icons.Default.Celebration,
        "TheaterComedy" to Icons.Default.TheaterComedy,
        "Movie" to Icons.Default.Movie,
        "Pets" to Icons.Default.Pets,
        "Brush" to Icons.Default.Brush,
        "Code" to Icons.Default.Code,
        "Place" to Icons.Default.Place,
        "Favorite" to Icons.Default.Favorite,
        "Star" to Icons.Default.Star,
        "Face" to Icons.Default.Face,
        "Info" to Icons.Default.Info,
        "AccountBalance" to Icons.Default.AccountBalance,
        "Refresh" to Icons.Default.Refresh,
        "History" to Icons.Default.History,
        "ShowChart" to Icons.AutoMirrored.Filled.ShowChart,
        "MoreHoriz" to Icons.Default.MoreHoriz
    )

    val expenseCategories = remember(customCategories) {
        customCategories.asSequence().filter { it.type == "EXPENSE" }.map { 
            CategoryData(it.name, iconMap[it.iconName] ?: Icons.Default.MoreHoriz)
        }.toList().ifEmpty {
            listOf(
                CategoryData("食生活", Icons.Default.Restaurant),
                CategoryData("住まい", Icons.Default.Home),
                CategoryData("インフラ", Icons.Default.Wifi),
                CategoryData("日用雑貨", Icons.Default.LocalMall),
                CategoryData("移動・交通", Icons.Default.Place),
                CategoryData("健康・医療", Icons.Default.MedicalServices),
                CategoryData("自分磨き", Icons.Default.School),
                CategoryData("レジャー", Icons.Default.Star),
                CategoryData("交際・贈答", Icons.Default.Favorite),
                CategoryData("美容・装い", Icons.Default.Face),
                CategoryData("特別な支出", Icons.Default.CardGiftcard),
                CategoryData("その他", Icons.Default.MoreHoriz)
            )
        }
    }

    val incomeCategories = remember(customCategories) {
        customCategories.asSequence().filter { it.type == "INCOME" }.map {
            CategoryData(it.name, iconMap[it.iconName] ?: Icons.Default.MoreHoriz)
        }.toList().ifEmpty {
            listOf(
                CategoryData("給与", Icons.Default.AccountBalance),
                CategoryData("事業・副業", Icons.Default.Build),
                CategoryData("資産運用", Icons.Default.Savings),
                CategoryData("臨時収入", Icons.Default.Star),
                CategoryData("給付・手当", Icons.Default.Info),
                CategoryData("還付・返金", Icons.Default.Refresh),
                CategoryData("贈与・祝金", Icons.Default.Favorite),
                CategoryData("ポイ活", Icons.Default.Payments),
                CategoryData("不用品売却", Icons.Default.LocalMall),
                CategoryData("繰越金", Icons.Default.History),
                CategoryData("利息・配当", Icons.AutoMirrored.Filled.ShowChart),
                CategoryData("その他", Icons.Default.MoreHoriz)
            )
        }
    }

    fun processOcrWithAi(uri: Uri) {
        isAnalyzingOcr = true
        showOcrResultPage = true
        scope.launch {
            try {
                val fullText = ocrManager?.extractFullText(uri)
                if (fullText.isNullOrBlank()) {
                    snackbarHostState.showSnackbar("テキストを読み取れませんでした")
                    return@launch
                }

                if ((gemini == null) || !gemini.isReady.value) {
                    val amount = ocrManager.extractAmount(uri)
                    if (amount != null) {
                    amountText = amount.toString()
                }
                    snackbarHostState.showSnackbar("AIが準備中のため金額のみ抽出しました")
                    return@launch
                }

                val assetNames = dbAssets.joinToString(", ") { it.name }
                val categoryNames = expenseCategories.joinToString(", ") { it.name }
                val today = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(Date())
                
                val prompt = """
                    あなたは家計簿の入力補助を行うAIです。提供されたレシートのテキストから、正確に金額、日付、支払元資産、ジャンル、品目（メモ）を抽出してください。
                    
                    必ず3つの異なる推測パターンを提示してください。
                    パターン1: 最も可能性が高い標準的な解釈
                    パターン2: 店名や品目が異なる別の可能性（または合算金額の別の候補）
                    パターン3: 別の解釈や簡略化した内容
                    
                    【レシートテキスト】
                    $fullText
                    
                    【支払元資産の候補】
                    $assetNames
                    
                    【ジャンルの候補】
                    $categoryNames
                    
                    【抽出ルール】
                    1. 金額 (AMOUNT): 支払い合計金額を数値のみで。
                    2. 日付 (DATE): YYYY/MM/DD 形式で。記載がない場合は今日の日付 ($today) にしてください。
                    3. 資産 (ASSET): 候補リストから選んでください。
                    4. ジャンル (GENRE): 候補リストから選んでください。
                    5. 品目 (MEMO): 20文字以内で。
                    
                    【注意事項】
                    ・情報を読み取れない場合でも、前後の文脈からできるだけ推測して埋めてください。
                    ・「不明」「解析不能」「データなし」などの言葉は絶対に使用しないでください。
                    ・どうしても特定できない項目は、無理に埋めず空欄のままにしてください。
                    ・金額が特定できない場合は、そのパターン自体を出力しないでください。
                    
                    【出力形式】
                    必ず以下の形式を3回繰り返してください。説明は一切不要です。
                    ---PATTERN---
                    AMOUNT: [数値]
                    DATE: [YYYY/MM/DD]
                    ASSET: [資産名]
                    GENRE: [ジャンル名]
                    MEMO: [内容]
                """.trimIndent()

                val response = gemini.generateResponse(prompt)
                
                val predictions = mutableListOf<OcrPrediction>()
                val patternSections = response.split("---PATTERN---").filter { it.contains("AMOUNT:") }
                
                patternSections.take(3).forEach { section ->
                    var pAmount = ""
                    var pDate = selectedDate
                    var pAsset = "未選択"
                    var pGenre = "その他"
                    var pMemo = ""
                    
                    section.lines().forEach { line ->
                        val trimmed = line.trim()
                        when {
                            trimmed.startsWith("AMOUNT:") -> pAmount = trimmed.substringAfter("AMOUNT:").trim().filter { it.isDigit() }
                            trimmed.startsWith("DATE:") -> {
                                val dateStr = trimmed.substringAfter("DATE:").trim()
                                try {
                                    val date = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).parse(dateStr)
                                    if (date != null) pDate = date.time
                                } catch (_: Exception) {}
                            }
                            trimmed.startsWith("ASSET:") -> pAsset = trimmed.substringAfter("ASSET:").trim()
                            trimmed.startsWith("GENRE:") -> pGenre = trimmed.substringAfter("GENRE:").trim()
                            trimmed.startsWith("MEMO:") -> pMemo = trimmed.substringAfter("MEMO:").trim()
                        }
                    }
                    
                    if (pAmount.isNotEmpty() && pAmount != "0") {
                        val finalMemo = pMemo.trim().ifEmpty { "レシート内容" }
                        // AIが「不明」といった文言を返した場合や、内容が空すぎる場合は候補から外す
                        if (!finalMemo.contains("不明") && !finalMemo.contains("解析") &&
                            !pAsset.contains("不明") && !pGenre.contains("不明")) {
                            predictions.add(
                                OcrPrediction(
                                    amount = formatAmountWithCommas(pAmount),
                                    date = pDate,
                                    assetName = pAsset,
                                    categoryName = pGenre,
                                    memo = finalMemo
                                )
                            )
                        }
                    }
                }
                
                ocrPredictions = predictions
                
                // デフォルトで最初の候補をメイン画面に反映
                predictions.firstOrNull()?.let { first ->
                    amountText = first.amount
                    selectedDate = first.date
                    memoText = first.memo
                    dbAssets.find { it.name == first.assetName }?.let { selectedAssetEntity = it }
                    expenseCategories.find { it.name == first.categoryName }?.let { selectedCategory = it }
                }

                snackbarHostState.showSnackbar("AIによる分析が完了しました")
                showOcrResultPage = true
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("分析中にエラーが発生しました: ${e.message}")
            } finally {
                isAnalyzingOcr = false
            }
        }
    }
    
    val categories = remember(selectedMode, expenseCategories, incomeCategories) {
        when (selectedMode) {
            "支出" -> expenseCategories
            "収入" -> incomeCategories
            "振替" -> listOf(CategoryData("振替", Icons.AutoMirrored.Filled.CompareArrows))
            "貸付" -> listOf(CategoryData("貸付", Icons.Outlined.RequestPage))
            "回収" -> listOf(CategoryData("回収", Icons.Default.Handshake))
            else -> expenseCategories
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            processOcrWithAi(it)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(TakePictureWithExplicitGrants()) { success ->
        if (success) {
            processOcrWithAi(tempImageUri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            onExternalActivityLaunch()
            cameraLauncher.launch(tempImageUri)
        }
    }

    // モード切替時に初期化
    LaunchedEffect(selectedMode) {
        if (selectedMode != "回収" || selectedLending == null) {
            selectedCategory = categories.firstOrNull()
        }
        selectedPaymentType = "即時"
    }

    // 日付選択時に支払種別を自動調整
    LaunchedEffect(selectedDate, selectedMode) {
        if (selectedMode == "支出") {
            val now = Calendar.getInstance()
            val todayStart = now.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val selectedCal = Calendar.getInstance().apply {
                timeInMillis = selectedDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val selectedDateStart = selectedCal.timeInMillis
            
            if (selectedDateStart > todayStart) {
                if (selectedPaymentType == "即時") {
                    selectedPaymentType = "後払い"
                }
            } else {
                if (selectedPaymentType == "後払い") {
                    selectedPaymentType = "即時"
                }
            }
        }
    }

    val accentColor = when (selectedMode) {
        "支出" -> Color(0xFFD32F2F)
        "収入" -> NotionSafeGreen
        "振替" -> Color(0xFF1976D2)
        "貸付" -> Color(0xFFFB8C00)
        "回収" -> Color(0xFF00897B)
        else -> NotionTextPrimary
    }

    val isSaveEnabled = remember(amountText, selectedAssetEntity, selectedMode, personName, selectedToAssetEntity, selectedLending) {
        amountText.isNotEmpty() && selectedAssetEntity != null && 
                       (selectedMode != "貸付" || personName.isNotEmpty()) &&
                       (selectedMode != "振替" || (selectedToAssetEntity != null && selectedAssetEntity != selectedToAssetEntity)) &&
                       (selectedMode != "回収" || (selectedLending != null && run {
                           val valForCheck = evaluateExpression(amountText)
                           valForCheck > 0 && valForCheck <= (selectedLending!!.amount - selectedLending!!.recoveredAmount)
                       }))
    }

    val onSave = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val amountValue = evaluateExpression(amountText)
        val asset = selectedAssetEntity
        if (asset != null) {
            val currentMode = selectedMode
            val currentMemo = memoText
            val currentCategory = selectedCategory?.name ?: "その他"
            val toAsset = selectedToAssetEntity
            
            // 成功オーバーレイを即座に表示してレスポンスを改善
            successInfo = SavedRecordInfo(
                amount = amountValue,
                mode = currentMode,
                category = if (currentMode == "支出" || currentMode == "収入") currentCategory else null,
                assetName = asset.name,
                memo = currentMemo.ifBlank { currentCategory },
                aiAdvice = null
            )

            scope.launch {
                when (currentMode) {
                    "支出", "収入" -> {
                        val isExp = currentMode == "支出"
                        if (isExp && selectedPaymentType != "即時") {
                            dao.insertScheduledExpense(
                                ScheduledExpenseEntity(
                                name = currentMemo.ifBlank { currentCategory },
                                amount = amountValue,
                                category = currentCategory,
                                date = selectedDate,
                                assetName = asset.name,
                                isRecurring = selectedPaymentType == "固定費"
                            ))
                        } else {
                            dao.insertTransaction(TransactionEntity(
                                id = UUID.randomUUID().toString(),
                                name = currentMemo.ifBlank { currentCategory },
                                amount = amountValue,
                                category = currentCategory,
                                date = selectedDate,
                                assetName = asset.name,
                                isExpense = isExp
                            ))
                            dao.updateAsset(asset.copy(
                                amount = if (isExp) asset.amount - amountValue else asset.amount + amountValue,
                                lastUpdated = System.currentTimeMillis()
                            ))
                        }
                    }
                    "振替" -> {
                        if (toAsset != null) {
                            dao.insertTransaction(TransactionEntity(
                                id = UUID.randomUUID().toString(),
                                name = currentMemo.ifBlank { "${asset.name} ➡ ${toAsset.name}" },
                                amount = amountValue,
                                category = "振替",
                                date = selectedDate,
                                assetName = asset.name,
                                isExpense = false,
                                toAssetName = toAsset.name,
                                isTransfer = true
                            ))
                            dao.updateAsset(asset.copy(
                                amount = asset.amount - amountValue,
                                lastUpdated = System.currentTimeMillis()
                            ))
                            dao.updateAsset(toAsset.copy(
                                amount = toAsset.amount + amountValue,
                                lastUpdated = System.currentTimeMillis()
                            ))
                        }
                    }
                    "貸付" -> {
                        val lending = LendingEntity(
                            id = UUID.randomUUID().toString(),
                            personName = personName,
                            amount = amountValue,
                            loanAsset = asset.name,
                            memo = currentMemo,
                            date = selectedDate
                        )
                        dao.insertLending(lending)
                        dao.insertTransaction(TransactionEntity(
                            id = UUID.randomUUID().toString(),
                            name = personName,
                            amount = amountValue,
                            category = "貸付",
                            date = selectedDate,
                            assetName = asset.name,
                            isExpense = true
                        ))
                        dao.updateAsset(asset.copy(
                            amount = asset.amount - amountValue,
                            lastUpdated = System.currentTimeMillis()
                        ))
                    }
                    "回収" -> {
                        selectedLending?.let { lending ->
                            val newTotalRecovered = lending.recoveredAmount + amountValue
                            val isFullyRecovered = newTotalRecovered >= lending.amount
                            
                            val updatedLending = lending.copy(
                                isRecovered = isFullyRecovered,
                                returnAsset = asset.name,
                                recoveredAmount = newTotalRecovered,
                                recoveredDate = selectedDate
                            )
                            dao.updateLending(updatedLending)
                            dao.insertTransaction(TransactionEntity(
                                id = UUID.randomUUID().toString(),
                                name = "${lending.personName}${if (!isFullyRecovered) " (一部)" else ""}",
                                amount = amountValue,
                                category = "回収",
                                date = selectedDate,
                                assetName = asset.name,
                                isExpense = false
                            ))
                            dao.updateAsset(asset.copy(
                                amount = asset.amount + amountValue,
                                lastUpdated = System.currentTimeMillis()
                            ))
                        }
                    }
                }
                
                // AIアドバイスをバックグラウンドで生成して反映
                if (gemini != null && currentMode == "支出") {
                    scope.launch {
                        try {
                            val prompt = """
                                ユーザーが支出を記録しました：$amountValue 円（$currentCategory）
                                メモ：$currentMemo
                                資産（${asset.name}）の現在の残高：${asset.amount} 円
                                
                                あなたは「家計の覗き魔」として、この支出に対して丁寧な言葉遣いながらも、痛いところを突き、ユーザーの浪費を煽るような皮肉めいた一言を1行で返してください。
                                
                                【性格・トーン】
                                ・表面上は丁寧（です・ます調）ですが、内容は非常に手厳しく、ユーザーの自尊心を少し刺激するような「煽り」を重視してください。
                                ・残高が十分にある場合でも、「それがいつまで続くでしょうか？」「油断していませんか？」といった形で、将来の不安や現在の緩みを突いてください。
                                ・「楽しめましたか？」「高価だとは思いませんか？」といった、相手の良心や理性に問いかける表現を好みます。
                                
                                【構成のヒント】
                                1. 支出への形式的な肯定や問いかけ
                                2. 事実（残高や金額）に基づいた鋭い皮肉や指摘
                                3. 自制や将来を案じさせる煽りの問いかけ
                                
                                【禁止事項】
                                ・「ユーモアを交えて回答します」といった自己言及
                                ・自己紹介、挨拶、タメ口、精神論
                                ・「明日からは、必要でしたか？」のような、時系列の矛盾した不自然な日本語
                                
                                自然かつ最も「刺さる」煽りの指摘のみを「直接」1行で出力してください。
                            """.trimIndent()
                            val advice = gemini.generateResponse(prompt)
                            successInfo = successInfo?.copy(aiAdvice = advice)
                        } catch (_: Exception) {
                            successInfo = successInfo?.copy(aiAdvice = "おや、記録だけは一人前ですね。それで、いつになったら貯金という文字を覚えるのでしょうか？")
                        }
                    }
                } else {
                    successInfo = successInfo?.copy(aiAdvice = "おや、記録だけは一人前ですね。それで、いつになったら貯金という文字を覚えるのでしょうか？")
                }
                
                // リセット
                amountText = ""
                memoText = ""
                personName = ""
                selectedLending = null
                selectedAssetEntity = null
                selectedToAssetEntity = null
                showKeypad = false
                onRecoveryHandled()
            }
        }
    }

    BackHandler(enabled = showKeypad) { showKeypad = false }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scrollState = rememberScrollState()

    // キーボード表示時にフォーカスされている要素へスクロール
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            // キーボードが閉じたら最上部（または適切な位置）に戻す
            scrollState.animateScrollTo(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NotionBackground)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = selectedMode,
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
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // 金額ボックス
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showKeypad = true },
                        shape = RoundedCornerShape(20.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.5f))
                    ) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 28.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (amountText.isEmpty()) "¥0" else "¥$amountText",
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = (-1).sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selectedMode == "支出") {
                                    IconButton(
                                        onClick = { showOcrOptions = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(accentColor.copy(alpha = 0.1f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "OCR",
                                            tint = accentColor
                                        )
                                    }
                                }
                            }

                            if (isAnalyzingOcr) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = accentColor,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text("AIがレシートを分析中...", fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (showOcrOptions) {
                    ModalBottomSheet(
                        onDismissRequest = { showOcrOptions = false },
                        containerColor = Color.White,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                            Text("レシート読み取り", modifier = Modifier.padding(vertical = 16.dp), color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AssetCategoryTile(
                                        label = "カメラで撮影",
                                        icon = Icons.Default.CameraAlt,
                                        color = NotionSafeGreen,
                                        onClick = {
                                            showOcrOptions = false
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                onExternalActivityLaunch()
                                                cameraLauncher.launch(tempImageUri)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AssetCategoryTile(
                                        label = "アルバムから",
                                        icon = Icons.Default.PhotoLibrary,
                                        color = NotionSafeGreen,
                                        onClick = {
                                            showOcrOptions = false
                                            onExternalActivityLaunch()
                                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                }
                                // 3列目と4列目の調整（ジャンルメニューの幅と合わせるため）
                                Spacer(modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        modes.forEach { mode ->
                            val isSelected = selectedMode == mode
                            val modeColor = when (mode) {
                                "支出" -> Color(0xFFD32F2F)
                                "収入" -> NotionSafeGreen
                                "振替" -> Color(0xFF1976D2)
                                "貸付" -> Color(0xFFFB8C00)
                                "回収" -> Color(0xFF00897B)
                                else -> NotionTextPrimary
                            }
                            
                            val modeIcon = when (mode) {
                                "収入" -> Icons.Default.ArrowDownward
                                "支出" -> Icons.Default.ArrowUpward
                                "振替" -> Icons.Default.SyncAlt
                                "貸付" -> Icons.Default.FileUpload
                                "回収" -> Icons.Default.FileDownload
                                else -> Icons.Default.Category
                            }
                            
                            Surface(
                                onClick = { selectedMode = mode },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) modeColor.copy(alpha = 0.12f) else Color.White,
                                border = BorderStroke(1.dp, if (isSelected) modeColor else NotionBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = modeIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) modeColor else NotionTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = mode,
                                        color = if (isSelected) modeColor else NotionTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = NotionBorder, modifier = Modifier.padding(vertical = 4.dp))

                    val isDetailEnabled = selectedMode != "回収" || selectedLending != null
                    val detailAlpha = if (isDetailEnabled) 1f else 0.5f

                    Text(
                        "詳細情報",
                        color = NotionTextPrimary.copy(alpha = detailAlpha),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    if (selectedMode == "支出") {
                        InputTile(
                            icon = Icons.Default.Payments,
                            label = "支払方法",
                            value = selectedPaymentType,
                            onClick = { showPaymentMethodSheet = true },
                            accentColor = accentColor,
                            enabled = isDetailEnabled
                        )
                    }

                    InputTile(
                        icon = Icons.Default.CalendarMonth,
                        label = "支払日",
                        value = dateFormatter.format(Date(selectedDate)),
                        onClick = { showDatePicker = true },
                        accentColor = accentColor,
                        enabled = isDetailEnabled
                    )

                    InputTile(
                        icon = Icons.Default.AccountBalanceWallet,
                        label = if (selectedMode == "貸付") "貸し出し元" else if (selectedMode == "回収") "受け取り先" else if (selectedMode == "振替") "振替元" else "資産",
                        value = selectedAssetEntity?.name ?: "未選択",
                        onClick = { showAssetSheet = true },
                        accentColor = accentColor,
                        isPlaceholder = selectedAssetEntity == null,
                        enabled = isDetailEnabled
                    )

                    if (selectedMode == "振替") {
                        InputTile(
                            icon = Icons.AutoMirrored.Filled.CompareArrows,
                            label = "振替先",
                            value = selectedToAssetEntity?.name ?: "未選択",
                            onClick = { showToAssetSheet = true },
                            accentColor = accentColor,
                            isPlaceholder = selectedToAssetEntity == null,
                            enabled = isDetailEnabled
                        )
                    }

                    if (selectedMode == "回収") {
                        InputTile(
                            icon = Icons.AutoMirrored.Filled.List,
                            label = "対象の貸付",
                            value = selectedLending?.let { "${it.personName} (${it.memo.ifBlank { "無題" }})" } ?: "選択してください",
                            onClick = { showLendingSheet = true },
                            accentColor = accentColor,
                            isPlaceholder = selectedLending == null,
                            enabled = isDetailEnabled
                        )
                    }

                    if (selectedMode == "支出" || selectedMode == "収入") {
                        InputTile(
                            icon = selectedCategory?.icon ?: Icons.Outlined.Category,
                            label = "ジャンル",
                            value = selectedCategory?.name ?: "未選択",
                            onClick = { showCategorySheet = true },
                            accentColor = accentColor,
                            isPlaceholder = selectedCategory == null,
                            enabled = isDetailEnabled
                        )
                    }

                    // Memo Field
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(detailAlpha)
                            .bringIntoViewRequester(memoBringIntoViewRequester),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, NotionBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = accentColor.copy(alpha = 0.08f)
                            ) {
                                Icon(
                                    Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("メモ", fontSize = 12.sp, color = NotionTextSecondary)
                                androidx.compose.foundation.text.BasicTextField(
                                    value = memoText,
                                    onValueChange = { memoText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { 
                                            if (it.isFocused) { 
                                                scope.launch { 
                                                    // 反応を速くするためディレイを短縮
                                                    kotlinx.coroutines.delay(60)
                                                    // 下方向に余裕（500px分）を持たせてスクロール
                                                    memoBringIntoViewRequester.bringIntoView(Rect(0f, 0f, 0f, 500f))
                                                } 
                                            } 
                                        },
                                    enabled = isDetailEnabled,
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 15.sp, color = NotionTextPrimary, fontWeight = FontWeight.SemiBold),
                                    cursorBrush = SolidColor(accentColor),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = if (selectedMode == "貸付") ImeAction.Next else ImeAction.Done
                                    ),
                                    decorationBox = { inner ->
                                        if (memoText.isEmpty()) Text("メモを入力", color = NotionTextSecondary.copy(alpha = 0.5f), fontSize = 15.sp)
                                        inner()
                                    }
                                )
                            }
                        }
                    }

                    if (selectedMode == "貸付") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(detailAlpha)
                                .bringIntoViewRequester(personBringIntoViewRequester),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, NotionBorder)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = accentColor.copy(alpha = 0.08f)) {
                                    Icon(Icons.Default.Person, null, tint = accentColor, modifier = Modifier.padding(10.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("貸した相手", fontSize = 12.sp, color = NotionTextSecondary)
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = personName,
                                        onValueChange = { personName = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { 
                                                if (it.isFocused) { 
                                                    scope.launch { 
                                                        // 反応を速くするためディレイを短縮
                                                        kotlinx.coroutines.delay(60)
                                                        // 下方向に余裕（500px分）を持たせてスクロール
                                                        personBringIntoViewRequester.bringIntoView(Rect(0f, 0f, 0f, 500f))
                                                    } 
                                                } 
                                            },
                                        enabled = isDetailEnabled,
                                        singleLine = true,
                                        textStyle = TextStyle(color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                        keyboardOptions = KeyboardOptions(
                                            imeAction = ImeAction.Done,
                                            capitalization = KeyboardCapitalization.Words
                                        ),
                                        decorationBox = { inner ->
                                            if (personName.isEmpty()) Text("名前を入力", color = NotionTextSecondary.copy(alpha = 0.5f), fontSize = 15.sp)
                                            inner()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 物理的な大きなスペーサーを一番下に配置して、強制的にスクロール可能にする
                Spacer(modifier = Modifier.height(300.dp))
            }
        }

        // Footer - 常に下部に表示
        if (!isKeyboardVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, NotionBackground.copy(alpha = 0.9f), NotionBackground),
                            startY = 0f
                        )
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 24.dp)
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = NotionBorder
                    ),
                    shape = RoundedCornerShape(16.dp),
                    enabled = isSaveEnabled
                ) {
                    Text(
                        text = when(selectedMode) {
                            "支出" -> if (selectedPaymentType == "即時") "支出を記録する" else "予定に追加する"
                            "収入" -> "収入を記録する"
                            "振替" -> "振替を記録する"
                            "貸付" -> "貸付を記録する"
                            "回収" -> "回収を記録する"
                            else -> "記録する"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState, 
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isKeyboardVisible) 0.dp else 100.dp)
        )

        if (showKeypad) {
            ModalBottomSheet(
                onDismissRequest = { showKeypad = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) },
                scrimColor = Color.Transparent
            ) {
                CustomKeypad(
                    onNumberClick = { num -> 
                        val newAmount = if (amountText == "0") num else amountText + num
                        amountText = formatAmountWithCommas(newAmount)
                    },
                    onOperatorClick = { op -> 
                        if (amountText.isNotEmpty() && !amountText.last().toString().matches(Regex("[-+*/.]"))) {
                            amountText += op
                        }
                    },
                    onDeleteClick = { 
                        if (amountText.isNotEmpty()) {
                            val dropped = amountText.dropLast(1)
                            amountText = formatAmountWithCommas(dropped)
                        }
                    },
                    onClearAllClick = { amountText = "" },
                    onConfirmClick = {
                        try {
                            amountText = formatAmountWithCommas(evaluateExpression(amountText).toString())
                        } catch (_: Exception) {}
                    },
                    onSaveClick = {
                        if (amountText.any { it in "+-*/" }) {
                            try {
                                amountText = formatAmountWithCommas(evaluateExpression(amountText).toString())
                            } catch (_: Exception) {}
                        } else {
                            // 金額を確定させてキーパッドを閉じるだけにする（記録は画面下部のボタンで行う）
                            showKeypad = false
                        }
                    },
                    onCloseClick = { showKeypad = false },
                    isSaveEnabled = isSaveEnabled,
                    actionColor = accentColor
                )
            }
        }

        if (showTypeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTypeSheet = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                    Text("タイプを選択", modifier = Modifier.padding(vertical = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NotionTextPrimary)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        modes.chunked(4).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { mode ->
                                    val isSelected = selectedMode == mode
                                    val modeColor = when (mode) {
                                        "支出" -> Color(0xFFD32F2F)
                                        "収入" -> NotionSafeGreen
                                        "振替" -> Color(0xFF1976D2)
                                        "貸付" -> Color(0xFFFB8C00)
                                        "回収" -> Color(0xFF00897B)
                                        else -> NotionTextPrimary
                                    }
                                    val modeIcon = when (mode) {
                                        "収入" -> Icons.Default.ArrowDownward
                                        "支出" -> Icons.Default.ArrowUpward
                                        "振替" -> Icons.Default.SyncAlt
                                        "貸付" -> Icons.Default.FileUpload
                                        "回収" -> Icons.Default.FileDownload
                                        else -> Icons.Default.Category
                                    }
                                    
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        AssetCategoryTile(
                                            label = mode,
                                            icon = modeIcon,
                                            color = if (isSelected) modeColor else NotionTextSecondary,
                                            onClick = { 
                                                selectedMode = mode
                                                showTypeSheet = false 
                                            }
                                        )
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

        if (showPaymentMethodSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPaymentMethodSheet = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                    Text("支払方法を選択", modifier = Modifier.padding(vertical = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NotionTextPrimary)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("即時", "後払い", "固定費").forEach { type ->
                            val isSelected = selectedPaymentType == type
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                AssetCategoryTile(
                                    label = type,
                                    icon = when(type) {
                                        "即時" -> Icons.Default.FlashOn
                                        "後払い" -> Icons.Default.Schedule
                                        else -> Icons.Default.Autorenew
                                    },
                                    color = if (isSelected) accentColor else NotionTextSecondary,
                                    onClick = { 
                                        selectedPaymentType = type
                                        showPaymentMethodSheet = false 
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (showCategorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showCategorySheet = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                    Text("ジャンルを選択", modifier = Modifier.padding(vertical = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NotionTextPrimary)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        categories.chunked(4).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { cat ->
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        val isSelected = selectedCategory == cat
                                        AssetCategoryTile(
                                            label = cat.name,
                                            icon = cat.icon,
                                            color = if (isSelected) accentColor else NotionTextSecondary,
                                            onClick = { selectedCategory = cat; showCategorySheet = false }
                                        )
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

        if (showAssetSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAssetSheet = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                    Text("資産を選択", modifier = Modifier.padding(vertical = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NotionTextPrimary)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        dbAssets.chunked(4).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { asset ->
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        val isSelected = selectedAssetEntity == asset
                                        AssetCategoryTile(
                                            label = asset.name,
                                            subLabel = "¥${String.format(Locale.JAPAN, "%,d", asset.amount)}",
                                            color = if (isSelected) accentColor else null,
                                            onClick = { selectedAssetEntity = asset; showAssetSheet = false }
                                        )
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

        if (showToAssetSheet) {
            ModalBottomSheet(
                onDismissRequest = { showToAssetSheet = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                    Text("振替先を選択", modifier = Modifier.padding(vertical = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NotionTextPrimary)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        dbAssets.chunked(4).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { asset ->
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        val isSelected = selectedToAssetEntity == asset
                                        AssetCategoryTile(
                                            label = asset.name,
                                            subLabel = "¥${String.format(Locale.JAPAN, "%,d", asset.amount)}",
                                            color = if (isSelected) accentColor else null,
                                            onClick = { selectedToAssetEntity = asset; showToAssetSheet = false }
                                        )
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

        if (showLendingSheet) {
            ModalBottomSheet(onDismissRequest = { showLendingSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("回収する貸付を選択", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    if (activeLendings.isEmpty()) {
                        Text("未回収の貸付はありません", modifier = Modifier.padding(32.dp), color = NotionTextSecondary)
                    }
                    activeLendings.forEach { lending ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { 
                                selectedLending = lending
                                amountText = formatAmountWithCommas((lending.amount - lending.recoveredAmount).toString())
                                memoText = lending.memo
                                showLendingSheet = false 
                                // 回収する貸付を選んだら、自動的に入金先資産の選択を開く
                                showAssetSheet = true
                            }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(lending.personName, fontWeight = FontWeight.Bold)
                                Text(lending.memo.ifBlank { "無題" }, fontSize = 12.sp, color = NotionTextSecondary)
                            }
                            Text("¥ ${String.format(Locale.JAPAN, "%,d", lending.amount - lending.recoveredAmount)}", color = Color(0xFFFB8C00), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        selectedDate = datePickerState.selectedDateMillis ?: selectedDate
                        showDatePicker = false
                    }) { Text("決定", color = accentColor) }
                }
            ) { DatePicker(state = datePickerState) }
        }

        // 成功オーバーレイ
        successInfo?.let { info ->
            SuccessOverlay(
                info = info,
                onDismiss = { successInfo = null }
            )
        }

        if (showOcrResultPage) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = NotionBackground
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScreenHeader(
                            title = "レシート読み取り",
                            navigationIcon = {
                                Surface(
                                    onClick = { showOcrResultPage = false },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = NotionTextSecondary.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, contentDescription = "閉じる", tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 24.dp)
                        ) {
                            if (isAnalyzingOcr) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    repeat(3) {
                                        PatternCardSkeleton()
                                    }
                                }
                            } else if (ocrPredictions.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .background(NotionBorder.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.List,
                                                contentDescription = null,
                                                tint = NotionTextSecondary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(40.dp)
                                            )
                                            // 失敗を示すバツ印を重ねる
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                tint = Color(0xFFD32F2F).copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp).align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp)
                                            )
                                        }
                                        Spacer(Modifier.height(20.dp))
                                        Text(
                                            "レシートの読み取りに失敗しました",
                                            color = NotionTextSecondary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "明るい場所で再度お試しください",
                                            color = NotionTextSecondary.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    ocrPredictions.forEachIndexed { index, prediction ->
                                        val isSelected = amountText == prediction.amount && memoText == prediction.memo
                                        
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp)
                                                .clickable {
                                                    amountText = prediction.amount
                                                    selectedDate = prediction.date
                                                    memoText = prediction.memo
                                                    dbAssets.find { it.name == prediction.assetName }?.let { selectedAssetEntity = it }
                                                    expenseCategories.find { it.name == prediction.categoryName }?.let { selectedCategory = it }
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.2.dp, if (isSelected) accentColor else NotionBorder)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Surface(
                                                            modifier = Modifier.size(24.dp),
                                                            shape = CircleShape,
                                                            color = if (isSelected) accentColor else NotionTextSecondary.copy(alpha = 0.1f)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    text = (index + 1).toString(),
                                                                    color = if (isSelected) Color.White else NotionTextSecondary,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("パターン ${index + 1}", color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    if (isSelected) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                Row(verticalAlignment = Alignment.Bottom) {
                                                    Text("¥", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.padding(bottom = 4.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(prediction.amount, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accentColor, letterSpacing = (-1).sp)
                                                }
                                                
                                                Text(prediction.memo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NotionTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    PredictionTag(prediction.categoryName, accentColor)
                                                    PredictionTag(prediction.assetName, NotionTextSecondary)
                                                    PredictionTag(SimpleDateFormat("MM月dd日", Locale.JAPAN).format(Date(prediction.date)), NotionTextSecondary)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }

                        // Bottom Action Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, NotionBackground.copy(alpha = 0.9f), NotionBackground),
                                        startY = 0f
                                    )
                                )
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 24.dp)
                        ) {
                            Button(
                                onClick = { showOcrResultPage = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "完了",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PredictionTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PatternCardSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.LightGray.copy(alpha = 0.3f),
            Color.LightGray.copy(alpha = 0.5f),
            Color.LightGray.copy(alpha = 0.3f),
        ),
        start = androidx.compose.ui.geometry.Offset(translateAnim - 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, 0f)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, NotionBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}
