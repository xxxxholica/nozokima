@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.nozokima

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import ui.theme.*
import androidx.room.Room
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- 自作コントラクト ---

class TakePictureWithExplicitGrants : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: android.net.Uri): Intent {
        return super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newRawUri(null, input)
        }
    }
}

// --- データモデル ---

data class CategoryData(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

data class AssetTypeUiSpec(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: Color
)

private val defaultAssetTypeUiSpec = AssetTypeUiSpec(
    icon = Icons.Outlined.AccountBalanceWallet,
    accentColor = Color(0xFF607D8B)
)

private val assetTypeUiSpecMap = mapOf(
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

private fun assetTypeUiSpec(category: String): AssetTypeUiSpec {
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
    val imageUri: android.net.Uri? = null,
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

// --- メインアクティビティ ---

class MainActivity : FragmentActivity() {
    private val db by lazy { (application as NozokimaApplication).database }
    private val gemini by lazy { (application as NozokimaApplication).geminiModel }
    private val ocrManager by lazy { OcrManager(this) }

    private fun showBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        onError(errString.toString())
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("生体認証")
            .setSubtitle("ロックを解除してください")
            .setNegativeButtonText("パスワードを使用")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Android 13+ で通知権限をリクエスト
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        setContent {
            // ステータスバーのアイコン色を調整
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                }
            }
            var selectedTab by remember { mutableIntStateOf(0) }
            var consultingTransaction by remember { mutableStateOf<Transaction?>(null) }
            var initialHomeAdviceText by remember { mutableStateOf<String?>(null) }
            var recoveryLending by remember { mutableStateOf<LendingEntity?>(null) }
            var initialAssetCategoryFilter by remember { mutableStateOf<String?>(null) }
            var isGoalKeypadVisible by remember { mutableStateOf(false) }

            val snackbarHostState = remember { SnackbarHostState() }
            var backupPassword by remember { mutableStateOf("") }
            var showBackupPasswordDialog by remember { mutableStateOf(false) }
            var backupMode by remember { mutableStateOf("") } // "export" or "import"
            var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var showAppLockPasswordDialog by remember { mutableStateOf(false) }
            var appLockDialogMode by remember { mutableStateOf("set") } // "set", "change", "disable"

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val dao = db.financeDao()

            // --- 全体データ収集 ---
            val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
            val assets by dao.getAllAssets().collectAsState(initial = emptyList())
            val lendings by dao.getAllLendings().collectAsState(initial = emptyList())
            val budgets by dao.getAllBudgets().collectAsState(initial = emptyList())
            val goalSetting by dao.getGoalSetting().collectAsState(initial = null)
            val chatSessions by dao.getAllChatSessions().collectAsState(initial = emptyList())
            val appSettings by dao.getAppSettings().collectAsState(initial = null)

            var isAppLocked by rememberSaveable { mutableStateOf(true) }
            var isExternalActivityLaunching by rememberSaveable { mutableStateOf(false) }
            val showLockScreen = isAppLocked && appSettings?.isAppLockEnabled == true
            val lifecycle = LocalLifecycleOwner.current.lifecycle

            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        if (!isExternalActivityLaunching) {
                            isAppLocked = true
                        }
                    } else if (event == Lifecycle.Event.ON_RESUME) {
                        isExternalActivityLaunching = false
                    }
                }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            // AI 状態管理 (Activityレベルで保持してタブ遷移中も継続) ---
            var homeAiText by rememberSaveable { mutableStateOf("") }
            var goalAiText by rememberSaveable { mutableStateOf("") }

            var currentChatSessionId by rememberSaveable { mutableStateOf<String?>(null) }

            val aiStatus by gemini.status.collectAsState()
            val aiIsReady by gemini.isReady.collectAsState()
            val aiIsGenerating by gemini.isGenerating.collectAsState()
            val scope = rememberCoroutineScope()

            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                uri?.let {
                    scope.launch {
                        try {
                            val encryptedData = BackupManager(dao).exportData(backupPassword)
                            contentResolver.openOutputStream(it)?.use { output ->
                                output.write(encryptedData.toByteArray())
                            }
                            // バックアップ履歴を保存
                            val fileName = run {
                                var name: String? = null
                                if (it.scheme == "content") {
                                    contentResolver.query(it, null, null, null, null)?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                            if (index != -1) name = cursor.getString(index)
                                        }
                                    }
                                }
                                name ?: it.path?.substringAfterLast('/') ?: "backup_${SimpleDateFormat("yyyyMMdd", Locale.JAPAN).format(Date())}.dat"
                            }
                            dao.insertBackupHistory(BackupHistoryEntity(
                                id = UUID.randomUUID().toString(),
                                date = System.currentTimeMillis(),
                                password = backupPassword,
                                fileName = fileName
                            ))
                            snackbarHostState.showSnackbar("データをエクスポートしました")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("エクスポートに失敗しました: ${e.message}")
                        } finally {
                            backupPassword = ""
                        }
                    }
                }
            }

            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let {
                    pendingImportUri = it
                    backupMode = "import"
                    showBackupPasswordDialog = true
                }
            }

            // キーボードの表示状態を監視
            val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

            // --- AI 分析実行ロジック ---

            fun triggerHomeAnalysis() {
                if (!aiIsReady || aiIsGenerating) return
                
                // データの計算
                val totalLendingAmount = lendings.filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
                val currentAssets = assets.sumOf { it.amount } + totalLendingAmount
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val startOfMonth = calendar.timeInMillis
                val spentThisMonth = transactions
                    .filter { it.date >= startOfMonth && it.isExpense && it.category != "貸付" }
                    .sumOf { it.amount }
                val defaultBudget = budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000L else it.toLong() }
                
                val currentGoal = goalSetting
                val goalMonthlyBudget = if (currentGoal != null && currentGoal.showResults && currentGoal.targetAmount > 0) {
                    val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                    val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
                    val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
                    val totalSpendable = (currentAssets + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
                    if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
                } else null
                val monthlyBudget = goalMonthlyBudget ?: defaultBudget

                val hasGoal = currentGoal != null && currentGoal.targetAmount > 0 && currentGoal.showResults
                val goalProgressRatio = if (hasGoal) (currentAssets.toFloat() / currentGoal.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f

                val prompt = buildString {
                    appendLine("あなたは家計管理AIアシスタントです。以下の家計データをもとに日本語で100〜150字の簡潔なアドバイスを1つだけ返してください。余計な挨拶・前置きは不要です。")
                    appendLine("今月の支出: ¥${String.format(Locale.JAPAN, "%,d", spentThisMonth)}")
                    appendLine("月の予算: ¥${String.format(Locale.JAPAN, "%,d", monthlyBudget)}")
                    appendLine("予算消化率: ${if (monthlyBudget > 0) "${(spentThisMonth.toFloat() / monthlyBudget * 100).toInt()}%" else "不明"}")
                    appendLine("総資産: ¥${String.format(Locale.JAPAN, "%,d", currentAssets)}")
                    if (hasGoal) appendLine("貯金目標: ¥${String.format(Locale.JAPAN, "%,d", currentGoal.targetAmount)}（達成率${(goalProgressRatio * 100).toInt()}%）")
                    val recentCats = transactions.take(5).joinToString("、") { it.category }
                    if (recentCats.isNotEmpty()) appendLine("直近の支出カテゴリ: $recentCats")
                }

                homeAiText = "" // 生成開始前にクリアして "..." を出さない
                scope.launch {
                    var result = ""
                    try {
                        gemini.generateResponseStream(prompt).collect { chunk ->
                            result += chunk
                            homeAiText = result
                        }
                    } catch (ignore: Exception) {
                        // エラーハンドリング（必要ならメッセージを表示）
                    }
                }
            }

            fun triggerGoalAnalysis() {
                if (!aiIsReady || aiIsGenerating || goalSetting == null) return
                val currentGoal = goalSetting!!
                val totalLendingAmount = lendings.filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
                val actualTotalAssets = assets.sumOf { it.amount }.toLong() + totalLendingAmount
                val targetAmount = currentGoal.targetAmount
                val isAchieved = actualTotalAssets >= targetAmount && targetAmount > 0
                val progressRatio = if (targetAmount > 0) (actualTotalAssets.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f) else 0f
                val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
                val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
                val totalSpendable = (actualTotalAssets + totalExpectedIncome - targetAmount).coerceAtLeast(0L)
                val monthlyBudget = if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
                val dailyLimit = if (remainingDays > 0) totalSpendable / remainingDays else 0L
                val difficulty = when { targetAmount == 0L -> "—"; dailyLimit < 1000 -> "スパルタ"; dailyLimit < 3000 -> "普通"; else -> "余裕" }

                val prompt = buildString {
                    if (isAchieved) {
                        appendLine("あなたは家計管理AIアシスタントです。ユーザーが貯金目標を達成しました！最高のお祝いの言葉と、これからの資産運用や次へのステップに向けたアドバイスを日本語で100〜150字で返してください。余計な挨拶・前置きは不要です。")
                    } else {
                        appendLine("あなたは家計管理AIアシスタントです。以下の目標データをもとに日本語で100〜150字の簡潔な励ましのアドバイスを1つだけ返してください。余計な挨拶・前置きは不要です。")
                    }
                    appendLine("目標: ${if (currentGoal.title.isNotEmpty()) currentGoal.title else "貯金"}")
                    appendLine("目標金額: ¥${String.format(Locale.JAPAN, "%,d", targetAmount)}")
                    appendLine("現在の資産: ¥${String.format(Locale.JAPAN, "%,d", actualTotalAssets)}")
                    appendLine("達成率: ${(progressRatio * 100).toInt()}%")
                    if (!isAchieved) {
                        appendLine("残り日数: $remainingDays 日")
                        appendLine("月の予算: ¥${String.format(Locale.JAPAN, "%,d", monthlyBudget)}")
                        appendLine("難易度: $difficulty")
                    }
                }

                goalAiText = ""
                scope.launch {
                    var result = ""
                    try {
                        gemini.generateResponseStream(prompt).collect { chunk ->
                            result += chunk
                            goalAiText = result
                        }
                    } catch (ignore: Exception) {
                        // エラーハンドリング
                    }
                }
            }

            // アプリ起動時に常にAI状態を確認する
            LaunchedEffect(Unit) {
                gemini.checkModelStatus()
            }

            // 初回生成の自動トリガー（Home -> Goal の順）
            LaunchedEffect(aiIsReady, assets, transactions, lendings) {
                if (aiIsReady && homeAiText.isEmpty() && assets.isNotEmpty() && !aiIsGenerating) {
                    triggerHomeAnalysis()
                }
            }

            // 固定費のチェックと処理
            LaunchedEffect(Unit) {
                scope.launch {
                    val recurringList = dao.getAllRecurringTransactionsListSync()
                    val now = Calendar.getInstance()
                    val today = now.get(Calendar.DAY_OF_MONTH)
                    val todayMillis = now.timeInMillis
                    
                    recurringList.forEach { recurring ->
                        // 本日が指定日かつ、最後に処理したのが本日でない場合
                        if (recurring.dayOfMonth == today) {
                            val lastProcessed = Calendar.getInstance().apply { timeInMillis = recurring.lastProcessedDate }
                            val isAlreadyProcessedToday = recurring.lastProcessedDate != 0L && 
                                lastProcessed.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                lastProcessed.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                                lastProcessed.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
                            
                            if (!isAlreadyProcessedToday) {
                                val asset = dao.getAssetByName(recurring.assetName)
                                if (asset != null) {
                                    dao.insertTransaction(TransactionEntity(
                                        id = UUID.randomUUID().toString(),
                                        name = "[固定費] ${recurring.name}",
                                        amount = recurring.amount,
                                        category = recurring.category,
                                        date = todayMillis,
                                        assetName = recurring.assetName,
                                        isExpense = recurring.isExpense
                                    ))
                                    dao.updateAsset(asset.copy(
                                        amount = if (recurring.isExpense) asset.amount - recurring.amount else asset.amount + recurring.amount,
                                        lastUpdated = todayMillis
                                    ))
                                    dao.updateRecurringTransaction(recurring.copy(lastProcessedDate = todayMillis))
                                }
                            }
                        }
                    }
                }
            }

            // デフォルトカテゴリの初期化
            LaunchedEffect(Unit) {
                scope.launch {
                    if (dao.getCategoryCount() == 0) {
                        val defaultCategories = listOf(
                            CategoryEntity(UUID.randomUUID().toString(), "食費", "EXPENSE", "ShoppingCart", true, 0),
                            CategoryEntity(UUID.randomUUID().toString(), "日用品", "EXPENSE", "Build", true, 1),
                            CategoryEntity(UUID.randomUUID().toString(), "交通費", "EXPENSE", "Place", true, 2),
                            CategoryEntity(UUID.randomUUID().toString(), "交際費", "EXPENSE", "Favorite", true, 3),
                            CategoryEntity(UUID.randomUUID().toString(), "娯楽", "EXPENSE", "Star", true, 4),
                            CategoryEntity(UUID.randomUUID().toString(), "美容", "EXPENSE", "Face", true, 5),
                            CategoryEntity(UUID.randomUUID().toString(), "健康", "EXPENSE", "Info", true, 6),
                            CategoryEntity(UUID.randomUUID().toString(), "その他", "EXPENSE", "MoreHoriz", true, 7),
                            CategoryEntity(UUID.randomUUID().toString(), "給与", "INCOME", "AccountBalance", true, 8),
                            CategoryEntity(UUID.randomUUID().toString(), "賞与", "INCOME", "Star", true, 9),
                            CategoryEntity(UUID.randomUUID().toString(), "副業", "INCOME", "Build", true, 10),
                            CategoryEntity(UUID.randomUUID().toString(), "お小遣い", "INCOME", "Favorite", true, 11),
                            CategoryEntity(UUID.randomUUID().toString(), "還付金", "INCOME", "Info", true, 12)
                        )
                        defaultCategories.forEach { dao.insertCategory(it) }
                    }
                }
            }

            // Home の生成が完了した、または目標が開始されたら Goal をトリガー
            LaunchedEffect(homeAiText, aiIsGenerating, goalSetting?.showResults) {
                if (aiIsReady && (homeAiText.isNotEmpty() || (goalSetting != null && goalSetting!!.showResults)) && 
                    !aiIsGenerating && goalAiText.isEmpty() && goalSetting != null && goalSetting!!.showResults) {
                    triggerGoalAnalysis()
                }
            }

            // ダウンロード許可ダイアログ
            if (aiStatus == FeatureStatus.DOWNLOADABLE) {
                AlertDialog(
                    onDismissRequest = { /* システム側で管理するため何もしない */ },
                    title = { Text("追加のダウンロード") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("AI相談の利用にはAIモデル(Gemini Nano)が必要です。\nダウンロードはWi-Fi 接続を推奨します。", fontSize = 14.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch { gemini.startDownload() }
                        }) { Text("ダウンロード開始") }
                    },
                    dismissButton = {
                        TextButton(onClick = { /* キャンセル */ }) { Text("キャンセル") }
                    }
                )
            }

            // 48時間以上経過したバックアップ履歴を自動削除
            LaunchedEffect(chatSessions) { // トリガーとして適当なStateを使用
                val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
                dao.deleteOldBackupHistory(fortyEightHoursAgo)
            }

            // エクスポート画面でのスクリーンショット禁止制御
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            DisposableEffect(showBackupPasswordDialog) {
                if (showBackupPasswordDialog && backupMode == "export") {
                    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose {
                    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            if (showAppLockPasswordDialog) {
                var currentPasswordInput by remember { mutableStateOf("") }
                var newPasswordInput by remember { mutableStateOf("") }
                var confirmPasswordInput by remember { mutableStateOf("") }
                
                // 0: Initial/Disable, 1: New Password, 2: Confirm New Password
                var step by remember { mutableIntStateOf(0) }
                
                val isDisableMode = appLockDialogMode == "disable"
                val isChangeMode = appLockDialogMode == "change"

                AlertDialog(
                    onDismissRequest = { showAppLockPasswordDialog = false },
                    title = { 
                        Text(when(appLockDialogMode) {
                            "set" -> if (step == 0) "パスワードの設定" else "パスワードの確認"
                            "change" -> when(step) {
                                0 -> "現在のパスワード"
                                1 -> "新しいパスワード"
                                else -> "パスワードの確認"
                            }
                            else -> "ロックの解除"
                        })
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val description = when {
                                isDisableMode -> "ロックを解除するには現在のパスワードを入力してください"
                                isChangeMode -> when(step) {
                                    0 -> "現在のパスワードを入力してください"
                                    1 -> "新しいパスワード（4〜12桁）を入力してください"
                                    else -> "もう一度入力してください"
                                }
                                else -> if (step == 0) "パスワード（4〜12桁）を入力してください" else "もう一度入力してください"
                            }
                            Text(description, fontSize = 12.sp, color = NotionTextSecondary)
                            Spacer(Modifier.height(24.dp))
                            
                            val currentInputText = when(step) {
                                0 -> currentPasswordInput
                                1 -> newPasswordInput
                                else -> confirmPasswordInput
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.weight(1f))
                                repeat(currentInputText.length) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(NotionSafeGreen)
                                            .border(1.dp, NotionSafeGreen, CircleShape)
                                    )
                                }
                                if (currentInputText.isEmpty()) {
                                    Text(" ", fontSize = 16.sp) // Maintain height
                                }
                                Spacer(Modifier.weight(1f))
                            }
                            
                            Spacer(Modifier.height(32.dp))
                            
                            PinKeypad(
                                onNumberClick = { num ->
                                    when(step) {
                                        0 -> currentPasswordInput += num
                                        1 -> if (newPasswordInput.length < 12) newPasswordInput += num
                                        2 -> if (confirmPasswordInput.length < 12) confirmPasswordInput += num
                                    }
                                },
                                onDeleteClick = {
                                    when(step) {
                                        0 -> if (currentPasswordInput.isNotEmpty()) currentPasswordInput = currentPasswordInput.dropLast(1)
                                        1 -> if (newPasswordInput.isNotEmpty()) newPasswordInput = newPasswordInput.dropLast(1)
                                        2 -> if (confirmPasswordInput.isNotEmpty()) confirmPasswordInput = confirmPasswordInput.dropLast(1)
                                    }
                                },
                                onConfirmClick = {
                                    when {
                                        isDisableMode -> {
                                            if (currentPasswordInput == appSettings?.appLockPassword) {
                                                scope.launch {
                                                    dao.upsertAppSettings(appSettings!!.copy(
                                                        isAppLockEnabled = false,
                                                        isBiometricEnabled = false
                                                    ))
                                                    showAppLockPasswordDialog = false
                                                }
                                            } else {
                                                scope.launch { snackbarHostState.showSnackbar("パスワードが正しくありません") }
                                                currentPasswordInput = ""
                                            }
                                        }
                                        isChangeMode -> {
                                            when(step) {
                                                0 -> {
                                                    if (currentPasswordInput == appSettings?.appLockPassword) {
                                                        step = 1
                                                    } else {
                                                        scope.launch { snackbarHostState.showSnackbar("現在のパスワードが正しくありません") }
                                                        currentPasswordInput = ""
                                                    }
                                                }
                                                1 -> step = 2
                                                2 -> {
                                                    if (newPasswordInput == confirmPasswordInput) {
                                                        scope.launch {
                                                            val currentSettings = appSettings ?: AppSettingsEntity()
                                                            dao.upsertAppSettings(currentSettings.copy(
                                                                appLockPassword = newPasswordInput,
                                                                isAppLockEnabled = true
                                                            ))
                                                            showAppLockPasswordDialog = false
                                                            snackbarHostState.showSnackbar("パスワードを変更しました")
                                                        }
                                                    } else {
                                                        scope.launch { snackbarHostState.showSnackbar("パスワードが一致しません") }
                                                        confirmPasswordInput = ""
                                                    }
                                                }
                                            }
                                        }
                                        else -> { // Set mode
                                            if (step == 0) {
                                                step = 2 // Skip 1 for consistency if needed, but let's just use 0 and 2
                                            } else {
                                                if (currentPasswordInput == confirmPasswordInput) {
                                                    scope.launch {
                                                        val currentSettings = appSettings ?: AppSettingsEntity()
                                                        dao.upsertAppSettings(currentSettings.copy(
                                                            appLockPassword = currentPasswordInput,
                                                            isAppLockEnabled = true
                                                        ))
                                                        showAppLockPasswordDialog = false
                                                        snackbarHostState.showSnackbar("パスワードを設定しました")
                                                    }
                                                } else {
                                                    scope.launch { snackbarHostState.showSnackbar("パスワードが一致しません") }
                                                    confirmPasswordInput = ""
                                                }
                                            }
                                        }
                                    }
                                },
                                isConfirmEnabled = when(step) {
                                    0 -> currentPasswordInput.isNotEmpty()
                                    1 -> newPasswordInput.length >= 4
                                    else -> confirmPasswordInput.length >= 4
                                },
                                confirmLabel = if (isDisableMode) "解除" else if (isChangeMode && step < 2 || !isChangeMode && step == 0) "次へ" else "確定"
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAppLockPasswordDialog = false }) { Text("キャンセル") }
                    }
                )
            }

            if (showBackupPasswordDialog) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val autofillManager = remember { context.getSystemService(android.view.autofill.AutofillManager::class.java) }
                var passwordText by remember { mutableStateOf(if (backupMode == "export") generateSecurePassword() else "") }
                var isPasswordVisible by remember { mutableStateOf(backupMode == "export") }

                AlertDialog(
                    onDismissRequest = { showBackupPasswordDialog = false; backupPassword = "" },
                    title = { Text(if (backupMode == "export") "バックアップ用パスワード" else "復号パスワード入力") },
                    text = {
                        Column {
                            if (backupMode == "export") {
                                Text("復旧時に必要なパスワードを自動生成しました。セキュリティのためスクリーンショットは禁止されています。設定の「エクスポート履歴」から48時間以内のみ確認可能です。", fontSize = 12.sp, color = NotionTextSecondary)
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NotionBackground, RoundedCornerShape(12.dp))
                                        .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = passwordText,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NotionTextPrimary,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    TextButton(
                                        onClick = { passwordText = generateSecurePassword() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = NotionSafeGreen)
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("別のパスワードを生成")
                                    }
                                }
                            } else {
                                Text("8文字以上のパスワードを入力してください", fontSize = 12.sp, color = NotionTextSecondary)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = passwordText,
                                    onValueChange = { passwordText = it },
                                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = NotionTextSecondary
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    label = { Text("パスワード") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = passwordText.length >= 8,
                            onClick = {
                                if (backupMode != "export") {
                                    autofillManager?.commit()
                                }
                                backupPassword = passwordText
                                showBackupPasswordDialog = false
                                if (backupMode == "export") {
                                    exportLauncher.launch("nozokima_backup_${SimpleDateFormat("yyyyMMdd", Locale.JAPAN).format(Date())}.dat")
                                } else {
                                    scope.launch {
                                        try {
                                            val uri = pendingImportUri ?: return@launch
                                            val encryptedData = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                            if (encryptedData != null) {
                                                BackupManager(dao).importData(encryptedData, backupPassword)
                                                snackbarHostState.showSnackbar("データをインポートしました")
                                            }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("インポートに失敗しました。パスワードが違うか、ファイルが壊れています。")
                                        } finally {
                                            backupPassword = ""
                                            pendingImportUri = null
                                        }
                                    }
                                }
                            }
                        ) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBackupPasswordDialog = false; backupPassword = ""; pendingImportUri = null }) { Text("キャンセル") }
                    }
                )
            }

            Surface(modifier = Modifier.fillMaxSize(), color = NotionBackground) {
                if (showLockScreen) {
                    AppLockScreen(
                        correctPassword = appSettings?.appLockPassword ?: "",
                        onUnlock = { isAppLocked = false },
                        failedAttempts = appSettings?.failedAttempts ?: 0,
                        lockoutUntil = appSettings?.lockoutUntil ?: 0L,
                        onFailedAttempt = { attempts, until ->
                            scope.launch {
                                val current = appSettings ?: AppSettingsEntity()
                                dao.upsertAppSettings(current.copy(failedAttempts = attempts, lockoutUntil = until))
                            }
                        },
                        onSuccessfulUnlock = {
                            scope.launch {
                                val current = appSettings ?: AppSettingsEntity()
                                dao.upsertAppSettings(current.copy(failedAttempts = 0, lockoutUntil = 0L))
                            }
                        },
                        isBiometricEnabled = appSettings?.isBiometricEnabled == true,
                        onBiometricClick = {
                            showBiometricPrompt(
                                onSuccess = {
                                    scope.launch {
                                        val current = appSettings ?: AppSettingsEntity()
                                        dao.upsertAppSettings(current.copy(failedAttempts = 0, lockoutUntil = 0L))
                                    }
                                    isAppLocked = false
                                }
                            )
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = selectedTab == 3,
                        drawerContent = {
                            if (selectedTab == 3) {
                                ChatHistoryDrawerContent(
                                    chatSessions = chatSessions,
                                    currentSessionId = currentChatSessionId,
                                    onSessionSelected = { currentChatSessionId = it },
                                    onDeleteSession = { sessionId ->
                                        scope.launch {
                                            dao.deleteChatSession(sessionId)
                                            dao.deleteMessagesForSession(sessionId)
                                            if (currentChatSessionId == sessionId) {
                                                currentChatSessionId = null
                                            }
                                        }
                                    },
                                    drawerState = drawerState,
                                    scope = scope
                                )
                            }
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Scaffold(
                                contentWindowInsets = WindowInsets.systemBars,
                                // bottomBarはスペーサーのみ
                                bottomBar = {
                                    if (!isKeyboardVisible) {
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .navigationBarsPadding()
                                                .height(80.dp)
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(modifier = Modifier.fillMaxSize()) {
                                    when (selectedTab) {
                                        0 -> Box(Modifier.padding(innerPadding)) {
                                            HomeScreen(
                                                dao = dao,
                                                gemini = gemini,
                                                onConsultClick = { tx ->
                                                    consultingTransaction = tx
                                                    selectedTab = 3
                                                },
                                                onAiAdviceClick = { advice ->
                                                    initialHomeAdviceText = advice
                                                    selectedTab = 3
                                                },
                                                onNavigateToSettings = {
                                                    selectedTab = 5
                                                },
                                                onCategoryClick = { category ->
                                                    initialAssetCategoryFilter = category
                                                    selectedTab = 2
                                                },
                                                homeAiText = homeAiText,
                                                onRefreshAi = { triggerHomeAnalysis() }
                                            )
                                        }
                                        1 -> Box(Modifier.padding(innerPadding)) {
                                            InputScreen(
                                                dao = dao,
                                                gemini = gemini,
                                                ocrManager = ocrManager,
                                                initialRecovery = recoveryLending,
                                                onRecoveryHandled = { recoveryLending = null },
                                                onExternalActivityLaunch = { isExternalActivityLaunching = true }
                                            )
                                        }
                                        2 -> Box(Modifier.padding(innerPadding)) {
                                            AssetsScreen(
                                                dao = dao,
                                                onRecoverClick = { lending ->
                                                    recoveryLending = lending
                                                    selectedTab = 1
                                                },
                                                initialCategoryFilter = initialAssetCategoryFilter
                                            )
                                        }
                                        3 -> {
                                            ConsultationScreen(
                                                dao = dao,
                                                gemini = gemini,
                                                assets = assets,
                                                lendings = lendings,
                                                transactions = transactions,
                                                chatSessions = chatSessions,
                                                drawerState = drawerState,
                                                currentSessionId = currentChatSessionId,
                                                onSessionSelected = { currentChatSessionId = it },
                                                initialTransaction = consultingTransaction,
                                                onClearConsultation = { consultingTransaction = null },
                                                initialHomeAdviceText = initialHomeAdviceText,
                                                onClearHomeAdvice = { initialHomeAdviceText = null },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(innerPadding)
                                                    .consumeWindowInsets(innerPadding)
                                            )
                                        }
                                        4 -> Box(Modifier.padding(innerPadding)) {
                                            BudgetSettingsScreen(
                                                dao = dao,
                                                gemini = gemini,
                                                goalAiText = goalAiText,
                                                onRefreshAi = { triggerGoalAnalysis() },
                                                isKeypadVisible = isGoalKeypadVisible,
                                                onKeypadVisibilityChange = { isGoalKeypadVisible = it },
                                                onBack = null
                                            )
                                        }
                                        5 -> Box(Modifier.padding(innerPadding)) {
                                            GeneralSettingsScreen(
                                                dao = dao,
                                                appLockEnabled = appSettings?.isAppLockEnabled == true,
                                                onToggleAppLock = { enabled ->
                                                    if (enabled) {
                                                        appLockDialogMode = "set"
                                                        showAppLockPasswordDialog = true
                                                    } else {
                                                        appLockDialogMode = "disable"
                                                        showAppLockPasswordDialog = true
                                                    }
                                                },
                                                biometricEnabled = appSettings?.isBiometricEnabled == true,
                                                onToggleBiometric = { enabled ->
                                                    scope.launch {
                                                        val current = appSettings ?: AppSettingsEntity()
                                                        dao.upsertAppSettings(current.copy(isBiometricEnabled = enabled))
                                                    }
                                                },
                                                isBiometricAvailable = isBiometricAvailable(),
                                                onChangePassword = {
                                                    appLockDialogMode = "change"
                                                    showAppLockPasswordDialog = true
                                                },
                                                onExportClick = {
                                                    backupMode = "export"
                                                    showBackupPasswordDialog = true
                                                },
                                                onImportClick = {
                                                    importLauncher.launch(arrayOf("*/*"))
                                                },
                                                onCategoryManagementClick = { selectedTab = 6 },
                                                onRecurringManagementClick = { selectedTab = 7 },
                                                onBack = {
                                                    selectedTab = 0
                                                }
                                            )
                                        }
                                        6 -> Box(Modifier.padding(innerPadding)) {
                                            CategoryManagementScreen(dao = dao, onBack = { selectedTab = 5 })
                                        }
                                        7 -> Box(Modifier.padding(innerPadding)) {
                                            RecurringTransactionManagementScreen(dao = dao, onBack = { selectedTab = 5 })
                                        }
                                    }
                                }
                            }
                            
                            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))

                            // FloatingNavBarをDrawerの下（およびそのScrimの下）に配置
                            if (!isKeyboardVisible && !isGoalKeypadVisible && selectedTab <= 5) {
                                FloatingNavBar(
                                    selectedTab = selectedTab,
                                    onTabSelected = { index ->
                                        if (index != 3) consultingTransaction = null
                                        if (index != 2) initialAssetCategoryFilter = null
                                        selectedTab = index
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 設定関連画面 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    dao: FinanceDao,
    onBack: () -> Unit
) {
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

// --- アプリロック画面 ---

@Composable
fun AppLockScreen(
    correctPassword: String,
    onUnlock: () -> Unit,
    failedAttempts: Int,
    lockoutUntil: Long,
    onFailedAttempt: (Int, Long) -> Unit,
    onSuccessfulUnlock: () -> Unit,
    isBiometricEnabled: Boolean = false,
    onBiometricClick: () -> Unit = {}
) {
    var inputPassword by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isLockedOut = lockoutUntil > currentTime
    val remainingLockoutSeconds = if (isLockedOut) ((lockoutUntil - currentTime) / 1000).toInt() else 0

    LaunchedEffect(isLockedOut) {
        while (currentTime < lockoutUntil) {
            kotlinx.coroutines.delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    // アプリが前面に戻った際や起動時に自動で生体認証を起動
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, isBiometricEnabled, isLockedOut) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isBiometricEnabled && !isLockedOut) {
                    onBiometricClick()
                }
            }
        }
        lifecycle.addObserver(observer)
        // 表示された時点で既にRESUMEDなら即座に実行
        if (lifecycle.currentState == Lifecycle.State.RESUMED) {
            if (isBiometricEnabled && !isLockedOut) {
                onBiometricClick()
            }
        }
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NotionBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(64.dp).then(
                if (isBiometricEnabled && !isLockedOut) Modifier.clickable { onBiometricClick() } else Modifier
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isLockedOut -> Icons.Default.Timer
                    else -> Icons.Default.Lock
                },
                contentDescription = null,
                tint = if (isLockedOut) Color(0xFFE57373) else NotionSafeGreen,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (isLockedOut) "入力を一時制限しています" else "アプリはロックされています",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = NotionTextPrimary
        )
        
        if (isLockedOut) {
            Text(
                text = "残り $remainingLockoutSeconds 秒後に再試行できます",
                color = Color(0xFFE57373),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (!isLockedOut) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                repeat(inputPassword.length) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(NotionSafeGreen)
                            .border(1.dp, NotionSafeGreen, CircleShape)
                    )
                }
                if (inputPassword.isEmpty()) {
                    Text(" ", fontSize = 16.sp) // Maintain height
                }
            }
        } else {
            Spacer(Modifier.height(16.dp)) // Maintain height occupied by indicators
        }
        
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            if (isError && !isLockedOut) {
                Text(
                    text = "パスワードが正しくありません (残り ${3 - failedAttempts} 回)",
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PinKeypad(
            onNumberClick = { num ->
                if (inputPassword.length < 12 && !isLockedOut) {
                    inputPassword += num
                    isError = false
                }
            },
            onDeleteClick = {
                if (inputPassword.isNotEmpty() && !isLockedOut) {
                    inputPassword = inputPassword.dropLast(1)
                    isError = false
                }
            },
            onConfirmClick = {
                if (inputPassword == correctPassword) {
                    onSuccessfulUnlock()
                    onUnlock()
                } else {
                    val nextFailedAttempts = failedAttempts + 1
                    var nextLockoutUntil = 0L
                    if (nextFailedAttempts >= 3) {
                        nextLockoutUntil = System.currentTimeMillis() + 60 * 1000
                    }
                    onFailedAttempt(if (nextFailedAttempts >= 3) 0 else nextFailedAttempts, nextLockoutUntil)
                    isError = true
                    inputPassword = ""
                }
            },
            isConfirmEnabled = inputPassword.length >= 4 && !isLockedOut,
            confirmLabel = "解除"
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = onBiometricClick,
            enabled = isBiometricEnabled && !isLockedOut,
            colors = ButtonDefaults.textButtonColors(
                contentColor = NotionSafeGreen,
                disabledContentColor = NotionTextSecondary.copy(alpha = 0.4f)
            )
        ) {
            Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("生体認証を使用", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PinKeypad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmClick: () -> Unit,
    isConfirmEnabled: Boolean,
    confirmLabel: String,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("BS", "0", confirmLabel)
    )

    Column(
        modifier = modifier.width(280.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    val isAction = key == confirmLabel
                    val isDelete = key == "BS"
                    
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Button(
                            onClick = {
                                when {
                                    isDelete -> onDeleteClick()
                                    isAction -> onConfirmClick()
                                    else -> onNumberClick(key)
                                }
                            },
                            modifier = Modifier.weight(1f).aspectRatio(1.2f),
                            enabled = if (isAction) isConfirmEnabled else true,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAction) NotionSafeGreen else NotionWhite,
                                contentColor = if (isAction) Color.White else NotionTextPrimary,
                                disabledContainerColor = NotionBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = if (!isAction) BorderStroke(1.dp, NotionBorder) else null,
                            contentPadding = PaddingValues(0.dp),
                            elevation = null
                        ) {
                            if (isDelete) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, null, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = key,
                                    fontSize = if (isAction) 15.sp else 22.sp,
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

// --- フローティングナビゲーションバー ---

@Composable
fun FloatingNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf("ホーム", "記録", "資産状況", "AI相談", "目標", "設定")
    val selectedIcons = listOf(
        Icons.Filled.Home,
        Icons.Filled.AddCircle,
        Icons.Filled.BarChart,
        Icons.AutoMirrored.Filled.Chat,
        Icons.Filled.TrackChanges,
        Icons.Filled.Settings
    )
    val unselectedIcons = listOf(
        Icons.Outlined.Home,
        Icons.Outlined.AddCircle,
        Icons.Outlined.BarChart,
        Icons.AutoMirrored.Outlined.Chat,
        Icons.Outlined.TrackChanges,
        Icons.Outlined.Settings
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        NotionBackground.copy(alpha = 0.5f),
                        NotionBackground
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color(0x11000000),
                    spotColor = Color(0x22000000)
                )
                .background(NotionWhite, RoundedCornerShape(12.dp))
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, label ->
                val isSelected = selectedTab == index
                
                // 目標(index 4)と設定(index 5)の間に縦線を入れる
                if (index == 5) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(NotionBorder)
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // アイコン＋ラベルをまとめてピルで囲む（選択範囲と強調範囲を一致させる）
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                color = if (isSelected) Color(0xFFD6F0EB) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(index) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) selectedIcons[index] else unselectedIcons[index],
                            contentDescription = label,
                            tint = if (isSelected) NotionSafeGreen else NotionTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- 1. ホーム画面 ---

@Composable
fun HomeScreen(
    dao: FinanceDao,
    gemini: GeminiNanoModel? = null,
    onConsultClick: (Transaction) -> Unit = {},
    onAiAdviceClick: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    homeAiText: String = "",
    onRefreshAi: () -> Unit = {}
) {
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val assets by dao.getAllAssets().collectAsState(initial = emptyList())
    val lendings by dao.getAllLendings().collectAsState(initial = emptyList())
    val budgets by dao.getAllBudgets().collectAsState(initial = emptyList())
    val goalSetting by dao.getGoalSetting().collectAsState(initial = null)

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..10 -> listOf(
                "Good morning! ☀️", "おはようございます 🌅", "Bonjour 🥐", "¡Buenos días! 🌻",
                "Guten Morgen 🥨", "좋은 아침! 🐣", "早安 ✨", "Buongiorno ☕", "Bom dia 🌿", "God morgon ☁️"
            )
            in 11..16 -> listOf(
                "Hello! 👋", "こんにちは 😊", "Salut! 🇫🇷", "¡Hola! 🍊", "Ciao! 🍕",
                "你好 🌈", "Hallo! 🍺", "안녕하세요 🍃", "Hi there! 🎈", "Hi! ✨"
            )
            in 17..20 -> listOf(
                "Good evening ✨", "こんばんは 🌆", "Bonsoir 🍷", "¡Buenas tardes! 🌇", "Buonasera 🌙",
                "Guten Abend 🥨", "晚上好 🍵", "저녁이에요 🌿", "Boa tarde 🌊", "Relax time 🛋️"
            )
            else -> listOf(
                "Good night 🌙", "おやすみなさい 💤", "Bonne nuit 🧸", "¡Buenas noches! ⭐", "Buonanotte 🌌",
                "Gute Nacht 😴", "晚安 ✨", "안녕히 주무세요 ☁️", "Sweet dreams 🦄", "Night 🌑"
            )
        }.random()
    }

    var progressTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        progressTrigger = true
    }

    val totalLendingAmount = remember(lendings) { lendings.filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount } }
    val currentAssets = remember(assets, totalLendingAmount) { assets.sumOf { it.amount } + totalLendingAmount }

    // 今月の支出を計算
    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    val spentThisMonth = remember(transactions, startOfMonth) {
        transactions
            .filter { it.date >= startOfMonth && it.isExpense && it.category != "貸付" }
            .sumOf { it.amount }
    }

    // 予算ゲージ用
    val currentGoal = goalSetting
    val goalMonthlyBudget = remember(currentGoal, currentAssets) {
        if (currentGoal != null && currentGoal.showResults && currentGoal.targetAmount > 0) {
            val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
            val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
            val totalSpendable = (currentAssets + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
            if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
        } else {
            null
        }
    }

    val defaultBudget = budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000L else it.toLong() }
    val monthlyBudget = goalMonthlyBudget ?: defaultBudget
    val daysUntilReset = remember {
        val cal = Calendar.getInstance()
        cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp, vertical = 0.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = greeting,
                color = NotionTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {

        // ダッシュボードカード
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, NotionBorder, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    var isAssetsVisible by remember { mutableStateOf(true) }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("資産総額", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            IconButton(
                                onClick = { isAssetsVisible = !isAssetsVisible },
                                modifier = Modifier.size(24.dp).padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAssetsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Assets Visibility",
                                    modifier = Modifier.size(14.dp),
                                    tint = NotionTextSecondary
                                )
                            }
                        }
                        Text(
                            text = if (isAssetsVisible) "¥ ${String.format(Locale.JAPAN, "%,d", currentAssets)}" else "¥ ••••••••",
                            color = NotionTextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("リセットまで", color = NotionTextSecondary, fontSize = 10.sp)
                        Text("$daysUntilReset 日", color = NotionSafeGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 予算進捗を強調（使用率で色分け）
                val spentRatio = if (monthlyBudget > 0) spentThisMonth.toFloat() / monthlyBudget.toFloat() else 0f
                val progressColor = when {
                    spentRatio > 0.75f -> Color(0xFFE57373) // 赤：75%超
                    spentRatio > 0.5f -> Color(0xFFFFB74D)  // 黄：50%超
                    else -> NotionSafeGreen
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // 上段：短期予算バー
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("あといくら使える？", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "¥ ${String.format(Locale.JAPAN, "%,d", spentThisMonth)} / ¥ ${String.format(Locale.JAPAN, "%,d", monthlyBudget)}",
                            color = NotionTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // 予算バーは「使った割合」で左から伸びるカウントアップ表示
                    val spentProgress = if (progressTrigger && monthlyBudget > 0) (spentThisMonth.toFloat() / monthlyBudget.toFloat()).coerceIn(0f, 1f) else 0f
                    val animatedSpentProgress by animateFloatAsState(
                        targetValue = spentProgress,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "spentProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedSpentProgress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = progressColor,
                        trackColor = NotionBorder,
                        strokeCap = StrokeCap.Round
                    )

                    // 下段：長期目標バー（常に表示）
                    Spacer(modifier = Modifier.height(20.dp))

                    val goalForCard = goalSetting
                    val hasGoal = goalForCard != null && goalForCard.targetAmount > 0 && goalForCard.showResults
                    val actualAssetsForGoal = assets.sumOf { it.amount }.toLong()
                    val goalProgressRatio = if (progressTrigger && hasGoal) (actualAssetsForGoal.toFloat() / goalForCard!!.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f
                    val animatedGoalProgress by animateFloatAsState(
                        targetValue = goalProgressRatio,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "goalProgress"
                    )
                    val goalDisplayTitle = when {
                        !hasGoal -> "目標未設定"
                        goalForCard!!.title.isNotEmpty() -> goalForCard.title
                        else -> "目標の貯金"
                    }
                    val goalAmountText = if (hasGoal)
                        "¥ ${String.format(Locale.JAPAN, "%,d", actualAssetsForGoal)} / ¥ ${String.format(Locale.JAPAN, "%,d", goalForCard!!.targetAmount)}"
                    else "— 設定してください"
                    val goalBarColor = if (hasGoal) Color(0xFF2196F3) else NotionBorder
                    val goalTrackColor = NotionBorder

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !hasGoal) { onNavigateToSettings() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = goalDisplayTitle,
                            color = if (hasGoal) NotionTextSecondary else NotionTextSecondary.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = goalAmountText,
                            color = if (hasGoal) NotionTextPrimary else NotionTextSecondary.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedGoalProgress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = goalBarColor,
                        trackColor = goalTrackColor,
                        strokeCap = StrokeCap.Round
                    )

                    // 覗き魔 AI によるホーム分析
                    Spacer(modifier = Modifier.height(20.dp))

                    val aiIsReady by (gemini?.isReady ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
                    val aiIsGenerating by (gemini?.isGenerating ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
                    val aiIsChecking by (gemini?.isCheckingStatus ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

                    // 表示ステートを判定
                    val isWaitingForCheck = aiIsChecking && homeAiText.isEmpty()
                    val isGeneratingNow = aiIsGenerating && homeAiText.isEmpty()
                    val showProgress = isWaitingForCheck || isGeneratingNow
                    val statusLabel = when {
                        isWaitingForCheck -> "AI を確認中..."
                        isGeneratingNow   -> "分析中..."
                        else              -> null
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp) // 最初から高さを確保してレイアウトの跳ねを防止
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                            .then(if (homeAiText.isNotEmpty() && !aiIsGenerating) Modifier.clickable { onAiAdviceClick(homeAiText) } else Modifier)
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(NotionSafeGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = NotionSafeGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "覗き魔 AI",
                                    color = NotionSafeGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                // 手動再生成ボタン（Gemini 利用可能かつ生成・確認中でないとき）
                                if (aiIsReady && !aiIsGenerating) {
                                    IconButton(
                                        onClick = { onRefreshAi() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "再生成",
                                            tint = NotionSafeGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // プログレスバー（確認中 or 生成中）
                            if (showProgress) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                                    color = NotionSafeGreen,
                                    trackColor = NotionSafeGreen.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            // ステータスラベル or テキスト
            if (statusLabel != null) {
                                Text(
                                    text = statusLabel,
                                    color = NotionTextSecondary.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            } else if (homeAiText.isNotEmpty()) {
                                Text(
                                    text = homeAiText,
                                    color = NotionTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // カテゴリアイコンマップ（両セクションで共用）
        val categoryIconMap = mapOf(
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

        // 最近の支出（支出・収入両方、最新5件）
        Text("最近の記録", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
        ) {
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("記録がまだありません", color = NotionTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                Column {
                    transactions.take(5).forEachIndexed { index, entity ->
                        val tx = Transaction(
                            id = entity.id,
                            name = entity.name,
                            amount = entity.amount,
                            category = entity.category,
                            date = entity.date
                        )
                        val isExpenseTx = entity.isExpense
                        val iconColor = when {
                            tx.category == "貸付" -> Color(0xFFFFB300)
                            tx.category == "回収" -> Color(0xFF00897B)
                            isExpenseTx -> Color(0xFFE57373)
                            else -> NotionSafeGreen
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            iconColor.copy(alpha = 0.08f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIconMap[tx.category] ?: Icons.Default.MoreHoriz,
                                        contentDescription = tx.category,
                                        tint = iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(tx.name, color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(tx.category, color = NotionTextSecondary, fontSize = 12.sp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "¥ ${String.format(Locale.JAPAN, "%,d", tx.amount)}",
                                    color = iconColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { if (isExpenseTx) onConsultClick(tx) },
                                    modifier = Modifier.size(32.dp).background(NotionBackground, CircleShape),
                                    enabled = isExpenseTx
                                ) {
                                    Icon(Icons.Default.Face, contentDescription = "AI相談",
                                        tint = if (isExpenseTx) NotionSafeGreen else NotionTextSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (index < minOf(transactions.size, 5) - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                thickness = 0.5.dp,
                                color = NotionBorder
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("今月の統計", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(12.dp))

        // 今月の支出をカテゴリ別に集計し、上位3件（貸付は除外）
        val monthlyCategoryGroups = remember(transactions, startOfMonth) {
            transactions
                .filter { it.date >= startOfMonth && it.isExpense && it.category != "貸付" }
                .groupBy { it.category }
                .mapValues { it.value.sumOf { t -> t.amount } }
                .toList()
                .sortedByDescending { it.second }
                .take(3)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
        ) {
            if (monthlyCategoryGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("今月の支出はまだありません", color = NotionTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                Column {
                    monthlyCategoryGroups.forEachIndexed { index, (category, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCategoryClick(category) }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIconMap[category] ?: Icons.Default.MoreHoriz,
                                        contentDescription = category,
                                        tint = NotionTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = category,
                                    color = NotionTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "¥ ${String.format(Locale.JAPAN, "%,d", amount)}",
                                    color = NotionTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = NotionTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (index < monthlyCategoryGroups.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                thickness = 0.5.dp,
                                color = NotionBorder
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        } // end inner Column
    }
}


// --- 2. 記録画面 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(
    dao: FinanceDao,
    gemini: GeminiNanoModel? = null,
    ocrManager: OcrManager? = null,
    initialRecovery: LendingEntity? = null,
    onRecoveryHandled: () -> Unit = {},
    onExternalActivityLaunch: () -> Unit = {}
) {
    val modes = listOf("支出", "収入", "振替", "貸付", "回収")
    var selectedMode by remember { mutableStateOf(if (initialRecovery != null) "回収" else "支出") }
    
    var amountText by remember { mutableStateOf(if (initialRecovery != null) (initialRecovery.amount - initialRecovery.recoveredAmount).toString() else "") }
    var memoText by remember { mutableStateOf(if (initialRecovery != null) initialRecovery.memo else "") }
    var personName by remember { mutableStateOf(if (initialRecovery != null) initialRecovery.personName else "") }
    var selectedLending by remember { mutableStateOf(initialRecovery) }
    
    var selectedAssetEntity by remember { mutableStateOf<AssetEntity?>(null) }
    var selectedToAssetEntity by remember { mutableStateOf<AssetEntity?>(null) }
    var showAssetSheet by remember { mutableStateOf(false) }
    var showToAssetSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showLendingSheet by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MM月dd日(E)", Locale.JAPAN) }
    var selectedCategory by remember { mutableStateOf<CategoryData?>(null) }
    var showKeypad by remember { mutableStateOf(false) }
    var showOcrOptions by remember { mutableStateOf(false) }
    
    var isAnalyzingOcr by remember { mutableStateOf(false) }
    var successInfo by remember { mutableStateOf<SavedRecordInfo?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        "Build" to Icons.Default.Build,
        "Place" to Icons.Default.Place,
        "Favorite" to Icons.Default.Favorite,
        "Star" to Icons.Default.Star,
        "Face" to Icons.Default.Face,
        "Info" to Icons.Default.Info,
        "MoreHoriz" to Icons.Default.MoreHoriz,
        "AccountBalance" to Icons.Default.AccountBalance
    )

    val expenseCategories = remember(customCategories) {
        customCategories.filter { it.type == "EXPENSE" }.map { 
            CategoryData(it.name, iconMap[it.iconName] ?: Icons.Default.MoreHoriz)
        }.ifEmpty {
            listOf(
                CategoryData("食費", Icons.Default.ShoppingCart),
                CategoryData("日用品", Icons.Default.Build),
                CategoryData("交通費", Icons.Default.Place),
                CategoryData("交際費", Icons.Default.Favorite),
                CategoryData("娯楽", Icons.Default.Star),
                CategoryData("美容", Icons.Default.Face),
                CategoryData("健康", Icons.Default.Info),
                CategoryData("その他", Icons.Default.MoreHoriz)
            )
        }
    }

    val incomeCategories = remember(customCategories) {
        customCategories.filter { it.type == "INCOME" }.map {
            CategoryData(it.name, iconMap[it.iconName] ?: Icons.Default.MoreHoriz)
        }.ifEmpty {
            listOf(
                CategoryData("給与", Icons.Default.AccountBalance),
                CategoryData("賞与", Icons.Default.Star),
                CategoryData("副業", Icons.Default.Build),
                CategoryData("お小遣い", Icons.Default.Favorite),
                CategoryData("還付金", Icons.Default.Info),
                CategoryData("その他", Icons.Default.MoreHoriz)
            )
        }
    }

    fun processOcrWithAi(uri: android.net.Uri) {
        isAnalyzingOcr = true
        scope.launch {
            try {
                val fullText = ocrManager?.extractFullText(uri)
                if (fullText.isNullOrBlank()) {
                    snackbarHostState.showSnackbar("テキストを読み取れませんでした")
                    return@launch
                }

                if (gemini == null || !gemini.isReady.value) {
                    val amount = ocrManager?.extractAmount(uri)
                    if (amount != null) amountText = amount.toString()
                    snackbarHostState.showSnackbar("AIが準備中のため金額のみ抽出しました")
                    return@launch
                }

                val assetNames = dbAssets.joinToString(", ") { it.name }
                val categoryNames = expenseCategories.joinToString(", ") { it.name }
                val today = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(Date())
                
                val prompt = """
                    あなたは家計簿の入力補助を行うAIです。提供されたレシートのテキストから、正確に金額、日付、支払元資産、カテゴリ、品目（メモ）を抽出してください。
                    
                    【レシートテキスト】
                    $fullText
                    
                    【支払元資産の候補】
                    $assetNames
                    
                    【カテゴリの候補】
                    $categoryNames
                    
                    【抽出ルール】
                    1. 金額 (AMOUNT): 支払い合計金額を数値のみで。カンマは不要です。
                    2. 日付 (DATE): レシートに記載された日付を YYYY/MM/DD 形式で。記載がない場合は今日の日付 ($today) にしてください。
                    3. 資産 (ASSET): 候補リストの中から最も適切な支払元を選んでください。判断できない場合は「未選択」としてください。
                    4. カテゴリ (CATEGORY): 候補リストの中から最も適切な支出カテゴリを「カテゴリの候補」の中から1つ選んでください。候補にない場合は「その他」としてください。
                    5. 品目 (MEMO): 店名や主要な購入品を20文字以内で簡潔に。
                    
                    【出力形式】
                    必ず以下の形式で、値のみを返してください。説明は一切不要です。
                    AMOUNT: [数値]
                    DATE: [YYYY/MM/DD]
                    ASSET: [資産名]
                    CATEGORY: [カテゴリ名]
                    MEMO: [内容]
                """.trimIndent()

                val response = gemini.generateResponse(prompt)
                
                val lines = response.lines()
                lines.forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("AMOUNT:") -> {
                            val value = trimmed.substringAfter("AMOUNT:").trim().filter { it.isDigit() }
                            if (value.isNotEmpty()) amountText = value
                        }
                        trimmed.startsWith("DATE:") -> {
                            val dateStr = trimmed.substringAfter("DATE:").trim()
                            val format = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
                            try {
                                val date = format.parse(dateStr)
                                if (date != null) selectedDate = date.time
                            } catch (e: Exception) {}
                        }
                        trimmed.startsWith("ASSET:") -> {
                            val assetName = trimmed.substringAfter("ASSET:").trim()
                            if (assetName != "未選択") {
                                val foundAsset = dbAssets.find { it.name == assetName }
                                if (foundAsset != null) {
                                    selectedAssetEntity = foundAsset
                                }
                            }
                        }
                        trimmed.startsWith("CATEGORY:") -> {
                            val categoryName = trimmed.substringAfter("CATEGORY:").trim()
                            val foundCategory = expenseCategories.find { it.name == categoryName }
                            if (foundCategory != null) {
                                selectedCategory = foundCategory
                            }
                        }
                        trimmed.startsWith("MEMO:") -> {
                            val memo = trimmed.substringAfter("MEMO:").trim()
                            if (memo != "[内容]" && memo.isNotEmpty()) memoText = memo
                        }
                    }
                }
                snackbarHostState.showSnackbar("AIによる分析が完了しました")
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
                            val currentRecoveryAmount = amountValue
                            val newTotalRecovered = lending.recoveredAmount + currentRecoveryAmount
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
                                amount = currentRecoveryAmount,
                                category = "回収",
                                date = selectedDate,
                                assetName = asset.name,
                                isExpense = false
                            ))
                            dao.updateAsset(asset.copy(
                                amount = asset.amount + currentRecoveryAmount,
                                lastUpdated = System.currentTimeMillis()
                            ))
                        }
                    }
                }
                
                // AIアドバイスをバックグラウンドで生成して反映
                if (gemini != null && currentMode == "支出") {
                    scope.launch {
                        try {
                            val prompt = "私は今、$amountValue 円を「$currentCategory」に使いました（メモ：$currentMemo）。これに対する1行の短い節約アドバイスをください。挨拶は不要です。"
                            val advice = gemini.generateResponse(prompt)
                            successInfo = successInfo?.copy(aiAdvice = advice)
                        } catch (e: Exception) {
                            successInfo = successInfo?.copy(aiAdvice = "記録が完了しました！")
                        }
                    }
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

    Box(modifier = Modifier.fillMaxSize().background(NotionBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            ScreenHeader(title = "記録")

            // セグメントコントロール
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Start
                ) {
                    modes.forEach { mode ->
                        val isSelected = selectedMode == mode
                        Box(
                            modifier = Modifier
                                .clickable { selectedMode = mode }
                                .padding(horizontal = 12.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = mode,
                                    color = if (isSelected) NotionTextPrimary else NotionTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(2.dp)
                                        .background(if (isSelected) NotionTextPrimary else Color.Transparent)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = NotionBorder)
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                    containerColor = Color.White
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                        Text("レシート読み取り", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                        ListItem(
                            headlineContent = { Text("カメラで撮影") },
                            leadingContent = { Icon(Icons.Default.CameraAlt, null, tint = NotionSafeGreen) },
                            modifier = Modifier.clickable {
                                showOcrOptions = false
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    onExternalActivityLaunch()
                                    cameraLauncher.launch(tempImageUri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                        HorizontalDivider(color = NotionBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("アルバムから選択") },
                            leadingContent = { Icon(Icons.Default.PhotoLibrary, null, tint = NotionSafeGreen) },
                            modifier = Modifier.clickable {
                                showOcrOptions = false
                                onExternalActivityLaunch()
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedMode == "回収") {
                    InputTile(
                        icon = Icons.AutoMirrored.Filled.List,
                        label = "対象の貸付",
                        value = selectedLending?.let { "${it.personName} (${it.memo.ifBlank { "無題" }})" } ?: "選択してください",
                        onClick = { showLendingSheet = true },
                        accentColor = accentColor,
                        isPlaceholder = selectedLending == null
                    )
                    HorizontalDivider(thickness = 1.dp, color = NotionBorder, modifier = Modifier.padding(vertical = 4.dp))
                }

                val isDetailEnabled = selectedMode != "回収" || selectedLending != null
                val detailAlpha = if (isDetailEnabled) 1f else 0.5f

                Text(
                    "詳細情報",
                    color = NotionTextPrimary.copy(alpha = detailAlpha),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )

                InputTile(
                    icon = Icons.Default.CalendarMonth,
                    label = "日付",
                    value = dateFormatter.format(java.util.Date(selectedDate)),
                    onClick = { showDatePicker = true },
                    accentColor = accentColor,
                    enabled = isDetailEnabled
                )

                if (selectedMode == "支出" || selectedMode == "収入") {
                    InputTile(
                        icon = selectedCategory?.icon ?: Icons.Default.Category,
                        label = "カテゴリー",
                        value = selectedCategory?.name ?: "未選択",
                        onClick = { showCategorySheet = true },
                        accentColor = accentColor,
                        isPlaceholder = selectedCategory == null,
                        enabled = isDetailEnabled
                    )
                }
                
                if (selectedMode == "貸付") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().alpha(detailAlpha),
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
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = isDetailEnabled,
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                    decorationBox = { inner ->
                                        if (personName.isEmpty()) Text("名前を入力", color = NotionTextSecondary.copy(alpha = 0.5f), fontSize = 15.sp)
                                        inner()
                                    }
                                )
                            }
                        }
                    }
                }

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

                // Memo Field
                Surface(
                    modifier = Modifier.fillMaxWidth().alpha(detailAlpha),
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
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = memoText,
                            onValueChange = { memoText = it },
                            modifier = Modifier.weight(1f),
                            enabled = isDetailEnabled,
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = NotionTextPrimary),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                            decorationBox = { inner ->
                                if (memoText.isEmpty()) Text("メモを入力", color = NotionTextSecondary.copy(alpha = 0.5f), fontSize = 15.sp)
                                inner()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Footer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
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
                        "支出" -> "支出を記録する"
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

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp))

        if (showKeypad) {
            ModalBottomSheet(
                onDismissRequest = { showKeypad = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) },
                scrimColor = Color.Transparent
            ) {
                CustomKeypad(
                    onNumberClick = { num -> if (amountText == "0") amountText = num else amountText += num },
                    onOperatorClick = { op -> if (amountText.isNotEmpty() && !amountText.last().toString().matches(Regex("[-+*/.]"))) amountText += op },
                    onDeleteClick = { if (amountText.isNotEmpty()) amountText = amountText.dropLast(1) },
                    onClearAllClick = { amountText = "" },
                    onConfirmClick = {
                        try {
                            amountText = evaluateExpression(amountText).toString()
                        } catch (e: Exception) {}
                    },
                    onSaveClick = {
                        if (amountText.any { it in "+-*/" }) {
                            try {
                                amountText = evaluateExpression(amountText).toString()
                            } catch (e: Exception) {}
                        } else {
                            onSave()
                            showKeypad = false
                        }
                    },
                    onCloseClick = { showKeypad = false },
                    isSaveEnabled = isSaveEnabled,
                    actionColor = accentColor
                )
            }
        }

        if (showCategorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showCategorySheet = false },
                containerColor = Color.White
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text("カテゴリーを選択", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.padding(horizontal = 12.dp)) {
                        items(categories) { cat ->
                            Column(
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { selectedCategory = cat; showCategorySheet = false }.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = if (selectedCategory == cat) accentColor.copy(alpha = 0.1f) else NotionBackground) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(cat.icon, null, tint = if (selectedCategory == cat) accentColor else NotionTextSecondary)
                                    }
                                }
                                Text(cat.name, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showAssetSheet) {
            ModalBottomSheet(onDismissRequest = { showAssetSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("資産を選択", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    dbAssets.forEach { asset ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedAssetEntity = asset; showAssetSheet = false }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(asset.name)
                            Text("¥ ${String.format(Locale.JAPAN, "%,d", asset.amount)}")
                        }
                    }
                }
            }
        }

        if (showToAssetSheet) {
            ModalBottomSheet(onDismissRequest = { showToAssetSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("振替先資産を選択", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    dbAssets.forEach { asset ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedToAssetEntity = asset; showToAssetSheet = false }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(asset.name)
                            Text("¥ ${String.format(Locale.JAPAN, "%,d", asset.amount)}")
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
                                amountText = (lending.amount - lending.recoveredAmount).toString()
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
                            Text("¥ ${String.format("%,d", lending.amount - lending.recoveredAmount)}", color = Color(0xFFFB8C00), fontWeight = FontWeight.Bold)
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
    }
}

@Composable
fun SuccessOverlay(
    info: SavedRecordInfo,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val displayDuration = 5000L // 5秒
    var progress by remember { mutableFloatStateOf(1f) }
    
    LaunchedEffect(Unit) {
        visible = true
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < displayDuration) {
            val elapsed = System.currentTimeMillis() - startTime
            progress = 1f - (elapsed.toFloat() / displayDuration)
            kotlinx.coroutines.delay(50)
        }
        visible = false
        kotlinx.coroutines.delay(400)
        onDismiss()
    }

    val accentColor = when (info.mode) {
        "支出" -> Color(0xFFD32F2F)
        "収入" -> NotionSafeGreen
        "振替" -> Color(0xFF1976D2)
        "貸付" -> Color(0xFFFB8C00)
        "回収" -> Color(0xFF00897B)
        else -> NotionSafeGreen
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 1.1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // チェックマーク
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(accentColor.copy(alpha = 0.1f), CircleShape)
                        .border(4.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = info.memo,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NotionTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "¥ ${String.format(Locale.JAPAN, "%,d", info.amount)}",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    letterSpacing = (-1).sp
                )

                if (info.mode == "支出") {
                    Spacer(modifier = Modifier.height(48.dp))
                    Surface(
                        color = Color(0xFFF9F9F9),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NotionBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = NotionSafeGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("覗き魔 AI アドバイス", fontSize = 12.sp, color = NotionSafeGreen, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (info.aiAdvice != null) {
                                Text(
                                    text = info.aiAdvice,
                                    fontSize = 14.sp,
                                    color = NotionTextPrimary,
                                    lineHeight = 20.sp
                                )
                            } else {
                                ThinkingAnimation()
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(64.dp))
                
                // 戻るボタン (インジケーター付き)
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(NotionBorder.copy(alpha = 0.3f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    // プログレス背景
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .background(accentColor.copy(alpha = 0.2f))
                    )
                    
                    Text(
                        text = "戻る",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ChatHistoryDrawerContent(
    chatSessions: List<ChatSessionEntity>,
    currentSessionId: String?,
    onSessionSelected: (String?) -> Unit,
    onDeleteSession: (String) -> Unit = {},
    drawerState: DrawerState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var sessionToDelete by remember { mutableStateOf<ChatSessionEntity?>(null) }
    val haptic = LocalHapticFeedback.current

    if (sessionToDelete != null) {
        DeleteConfirmDialog(
            text = "このチャット履歴「${sessionToDelete?.title}」を削除しますか？",
            onDismiss = { sessionToDelete = null },
            onConfirm = {
                sessionToDelete?.id?.let { onDeleteSession(it) }
                sessionToDelete = null
            }
        )
    }

    ModalDrawerSheet(
        modifier = Modifier
            .width(280.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "履歴",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        NavigationDrawerItem(
            label = { Text("新しいチャットを開始", fontWeight = FontWeight.Medium) },
            selected = currentSessionId == null,
            onClick = {
                onSessionSelected(null)
                scope.launch { drawerState.close() }
            },
            icon = { Icon(Icons.Default.Add, null) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = NotionSafeGreen.copy(alpha = 0.1f),
                selectedTextColor = NotionSafeGreen,
                selectedIconColor = NotionSafeGreen
            ),
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), color = NotionBorder)
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(chatSessions) { session ->
                val isSelected = currentSessionId == session.id
                Surface(
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                onSessionSelected(session.id)
                                scope.launch { drawerState.close() }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                sessionToDelete = session
                            }
                        ),
                    color = if (isSelected) NotionSafeGreen.copy(alpha = 0.1f) else Color.Transparent,
                    contentColor = if (isSelected) NotionSafeGreen else NotionTextPrimary,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = if (isSelected) NotionSafeGreen else NotionTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN).format(Date(session.lastMessageAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) NotionSafeGreen.copy(alpha = 0.7f) else NotionTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InputTile(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    accentColor: Color,
    isPlaceholder: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f),
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
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = NotionTextSecondary)
                Text(
                    value,
                    fontSize = 15.sp,
                    color = if (isPlaceholder) NotionTextSecondary.copy(alpha = 0.5f) else NotionTextPrimary,
                    fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.SemiBold
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = NotionTextSecondary.copy(alpha = 0.3f)
            )
        }
    }
}



@Composable
fun CustomKeypad(
    onNumberClick: (String) -> Unit,
    onOperatorClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    isSaveEnabled: Boolean,
    actionColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
        val keys = listOf(
            listOf("AC", "÷", "×", "BS"),
            listOf("7", "8", "9", "−"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "="),
            listOf("00", "0", ".", "確定")
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val isAction = key == "確定"
                    val isNumber = key.all { it.isDigit() } || key == "." || key == "00"
                    val isDelete = key == "BS"
                    val isClear = key == "AC"
                    val isEqual = key == "="

                    Button(
                        onClick = {
                            when {
                                isClear -> onClearAllClick()
                                isDelete -> onDeleteClick()
                                isAction -> onSaveClick()
                                isEqual -> onConfirmClick()
                                isNumber -> onNumberClick(key)
                                else -> {
                                    val op = when (key) {
                                        "÷" -> "/"
                                        "×" -> "*"
                                        "−" -> "-"
                                        else -> key
                                    }
                                    onOperatorClick(op)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(60.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAction) actionColor else if (isNumber) Color(0xFFF7F7F7) else Color.White,
                            contentColor = if (isAction) Color.White else NotionTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isAction) null else BorderStroke(1.dp, NotionBorder),
                        elevation = null
                    ) {
                        if (isDelete) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "削除",
                                modifier = Modifier.size(20.dp),
                                tint = NotionTextPrimary
                            )
                        } else {
                            Text(
                                text = key,
                                fontSize = 18.sp,
                                fontWeight = if (isAction) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}



fun evaluateExpression(expression: String): Int {
    if (expression.isEmpty()) return 0
    return try {
        // 全角記号の正規化と不要な記号の除去
        val normalized = expression.replace("−", "-")
            .replace("×", "*")
            .replace("÷", "/")
            .trim()
        
        // 末尾が演算子の場合は、その演算子を除去して計算する
        var cleanExpression = normalized
        while (cleanExpression.isNotEmpty() && cleanExpression.last() in "+-*/") {
            cleanExpression = cleanExpression.dropLast(1)
        }
        
        if (cleanExpression.isEmpty()) return 0

        val tokens = cleanExpression.split(Regex("(?<=[-+*/])|(?=[-+*/])"))
        if (tokens.isEmpty()) return 0
        
        var result = tokens[0].trim().toLong()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i].trim()
            if (i + 1 >= tokens.size) break
            val nextStr = tokens[i+1].trim()
            if (nextStr.isEmpty()) { i += 2; continue }
            val next = nextStr.toLong()
            result = when(op) {
                "+" -> result + next
                "-" -> result - next
                "*" -> result * next
                "/" -> if (next != 0L) result / next else result
                else -> result
            }
            i += 2
        }
        result.toInt()
    } catch (e: Exception) {
        0
    }
}

private fun generateSecurePassword(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+"
    return (1..16).map { chars.random() }.joinToString("")
}

// --- 3. 資産状況画面 ---

@Composable
fun AssetsScreen(
    dao: FinanceDao,
    onRecoverClick: (LendingEntity) -> Unit = {},
    initialCategoryFilter: String? = null
) {
    val assetsFromDb by dao.getAllAssets().collectAsState(initial = emptyList())
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showGroupSheet by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedGroupTitle by remember { mutableStateOf("") }
    var editNameText by remember { mutableStateOf("") }
    var editAmountText by remember { mutableStateOf("") }

    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    val assetGroups = listOf("現金", "銀行", "電子マネー", "カード", "貯蓄", "投資", "貸付", "カードローン", "ローン", "保険", "デビットカード", "その他")
    
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 履歴（デフォルト）, 1: 残高内訳
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
    var assetEditName by remember { mutableStateOf("") }
    var assetEditCategory by remember { mutableStateOf("") }
    var showAssetAmountEdit by remember { mutableStateOf(false) }
    var showAssetDeleteConfirm by remember { mutableStateOf(false) }

    val assetCategoryOrder = remember(assetGroups) { assetGroups.withIndex().associateBy({ it.value }, { it.index }) }
    val sortedAssets = assetsFromDb.sortedWith(
        compareBy<AssetEntity>(
            { assetCategoryOrder[it.category] ?: Int.MAX_VALUE },
            { it.name }
        )
    )

    val lendings by dao.getAllLendings().collectAsState(initial = emptyList())
    val totalLendingAsset = lendings.filter { !it.isRecovered }.sumOf { it.amount }

    val totalAssets = assetsFromDb.filter { it.amount > 0 }.sumOf { it.amount } + totalLendingAsset
    val totalLiabilities = assetsFromDb.filter { it.amount < 0 }.sumOf { it.amount }.let { if (it < 0) it * -1 else it }
    val totalNet = totalAssets - totalLiabilities

    Box(modifier = Modifier.fillMaxSize().background(NotionBackground)) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp, vertical = 0.dp).verticalScroll(rememberScrollState())) {
        ScreenHeader(title = "資産状況") {
            IconButton(
                onClick = { showGroupSheet = true },
                modifier = Modifier.background(NotionSafeGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NotionSafeGreen)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {

        // セグメントコントロール
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            listOf("履歴", "残高内訳", "貸付").forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clickable { selectedTab = index }
                        .padding(horizontal = 16.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            color = if (isSelected) NotionTextPrimary else NotionTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .background(if (isSelected) NotionTextPrimary else Color.Transparent)
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = NotionBorder)

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedTab == 1) {
            // 残高内訳タブ
            Column {
                // 資産総額サマリー（残高内訳タブのトップに移動）
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
                            editingAssetEntity = asset
                            assetEditName = asset.name
                            assetEditCategory = asset.category
                        },
                        onLongClick = {
                            editingAssetEntity = asset
                            assetEditName = asset.name
                            assetEditCategory = asset.category
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (totalLendingAsset > 0) {
                    UnifiedAssetCardRow(
                        title = "未回収の貸付金総額",
                        subtitle = "貸付",
                        amount = totalLendingAsset,
                        icon = assetTypeUiSpec("貸付").icon,
                        accentColor = assetTypeUiSpec("貸付").accentColor,
                        onClick = {},
                        onLongClick = {}
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        } else if (selectedTab == 0) {
            // 履歴タブ
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

            // フィルタリング処理（資産、ジャンル、期間、収支）
            val filteredTransactions = transactions
                .filter { tx -> selectedHistoryAssetName == null || tx.assetName == selectedHistoryAssetName }
                .filter { tx -> selectedHistoryCategory == null || tx.category == selectedHistoryCategory }
                .filter { tx ->
                    val txDate = tx.date
                    txDate >= selectedStartDate && txDate <= selectedEndDate
                }
                .filter { tx ->
                    when (selectedExpenseFilter) {
                        "支出のみ" -> tx.isExpense
                        "収入のみ" -> !tx.isExpense
                        else -> true
                    }
                }
                .sortedByDescending { it.date }

              // ---- フィルタバー ----
              val isAnyFilterActive = selectedHistoryAssetName != null || selectedHistoryCategory != null || selectedExpenseFilter != "全て" || selectedStartDate > 0L
              val resetAction: () -> Unit = {
                  selectedHistoryAssetName = null
                  selectedHistoryCategory = null
                  selectedExpenseFilter = "全て"
                  selectedStartDate = 0L
                  selectedEndDate = System.currentTimeMillis()
                  selectedPeriodLabel = "全て"
              }

              // アクティブなフィルタを先頭に並べ替え（解除ボタンは別途最左固定）
              data class FilterItem(val key: String, val active: Boolean)
              val filterOrder = listOf(
                  FilterItem("資産", selectedHistoryAssetName != null),
                  FilterItem("ジャンル", selectedHistoryCategory != null),
                  FilterItem("期間", selectedStartDate > 0L),
                  FilterItem("収支", selectedExpenseFilter != "全て")
              ).sortedByDescending { it.active }

              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .horizontalScroll(rememberScrollState())
                      .height(IntrinsicSize.Max),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  // --- 解除ボタン（常に最左端） ---
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

                  // --- アクティブ優先順に並べたフィルタボタン ---
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
                                      listOf("全て","食費","日用品","交通費","交際費","娯楽","美容","健康","その他","給与","賞与","副業","お小遣い","還付金").forEach { cat ->
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
                                          PO("全て",0L,now2), PO("今月",tmCal.timeInMillis,now2),
                                          PO("先月",lmCal.timeInMillis,lmEnd.timeInMillis), PO("今年",tyCal.timeInMillis,now2),
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

            if (filteredTransactions.isEmpty()) {
                Text(
                    if (selectedHistoryAssetName == null) "記録がありません" else "この財布の履歴はまだありません",
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = NotionTextSecondary
                )
            }
            // 日付ごとにグループ化して見出し表示
            val groupedByDate = filteredTransactions.groupBy { dateFormatter.format(Date(it.date)) }
            groupedByDate.forEach { (dateLabel, txList) ->
                // 日付見出し
                Text(
                    text = dateLabel,
                    color = NotionTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                // その日の取引をまとめて1ブロックで表示
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
                                amount = "¥ ${String.format("%,d", tx.amount)}",
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
        } else if (selectedTab == 2) {
            // 貸付タブ
            val activeLendings = lendings.filter { !it.isRecovered }.sortedByDescending { it.date }
            val recoveredLendings = lendings.filter { it.isRecovered }.sortedByDescending { it.recoveredDate }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (activeLendings.isEmpty() && recoveredLendings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("貸付の記録はありません", color = NotionTextSecondary)
                    }
                } else {
                    if (activeLendings.isNotEmpty()) {
                        Text("未回収", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NotionTextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                        ) {
                            Column {
                                activeLendings.forEachIndexed { idx, loan ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .background(Color(0xFFFFB300).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                                    .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.RequestPage,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFB300),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(loan.personName, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                Text("${loan.memo.ifEmpty { "無題の貸付" }} ・ ${loan.loanAsset}", color = NotionTextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                        Text(
                                            text = "¥ ${String.format(Locale.JAPAN, "%,d", loan.amount - loan.recoveredAmount)}",
                                            color = Color(0xFFFFB300),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (idx < activeLendings.lastIndex) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
                                    }
                                }
                            }
                        }
                    }

                    if (recoveredLendings.isNotEmpty()) {
                        if (activeLendings.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                        }
                        Text("回収済み", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NotionTextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                        ) {
                            Column {
                                recoveredLendings.forEachIndexed { idx, loan ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .background(NotionTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                                    .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Handshake,
                                                    contentDescription = null,
                                                    tint = NotionTextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(loan.personName, color = NotionTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                Text("${loan.memo.ifEmpty { "無題" }} ・ ${loan.returnAsset}", color = NotionTextSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
                                            }
                                        }
                                        Text(
                                            text = "¥ ${String.format(Locale.JAPAN, "%,d", loan.recoveredAmount)}",
                                            color = NotionTextSecondary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                        )
                                    }
                                    if (idx < recoveredLendings.lastIndex) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        } // end inner Column
    } // end outer Column
    } // end Box

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
                    OutlinedTextField(
                        value = editAmountText, 
                        onValueChange = { editAmountText = it }, 
                        label = { Text("金額（マイナス可）") }, 
                        singleLine = true, 
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = editAmountText.replace("−", "-").toIntOrNull() ?: 0
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
                }) { Text("追加", color = NotionSafeGreen) }
            },
            dismissButton = { TextButton(onClick = { showAddItemDialog = false }) { Text("キャンセル") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
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

    // --- 残高内訳 資産タップ時のアクションメニュー ---
    editingAssetEntity?.let { asset ->
        if (!showAssetAmountEdit && !showAssetDeleteConfirm) {
            ModalBottomSheet(
                onDismissRequest = { editingAssetEntity = null },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp)) {
                    Text(asset.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
                    Text("¥ ${String.format(Locale.JAPAN, "%,d", asset.amount)}", fontSize = 13.sp, color = NotionTextSecondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    ListItem(
                        headlineContent = { Text("金額を編集", color = NotionTextPrimary) },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = NotionSafeGreen) },
                        modifier = Modifier.clickable {
                            assetEditName = asset.name
                            assetEditCategory = asset.category
                            editAmountText = asset.amount.toString()
                            showAssetAmountEdit = true
                        }
                    )
                    HorizontalDivider(color = NotionBorder)
                    ListItem(
                        headlineContent = { Text("削除", color = Color(0xFFD32F2F)) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F)) },
                        modifier = Modifier.clickable { showAssetDeleteConfirm = true }
                    )
                }
            }
        }
    }

    // --- 金額編集ダイアログ ---
    if (showAssetAmountEdit) {
        val asset = editingAssetEntity
        if (asset != null) {
            AlertDialog(
                onDismissRequest = { showAssetAmountEdit = false; editingAssetEntity = null },
                title = { Text("金額を編集", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(asset.name, fontSize = 14.sp, color = NotionTextSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editAmountText,
                            onValueChange = { editAmountText = it },
                            label = { Text("金額（マイナス可）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newAmount = editAmountText.replace("−", "-").toIntOrNull() ?: asset.amount
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
                    }) { Text("保存", color = NotionSafeGreen) }
                },
                dismissButton = {
                    TextButton(onClick = { showAssetAmountEdit = false; editingAssetEntity = null }) { Text("キャンセル") }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            )
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




// --- プリセット質問 ---
private val PRESET_QUESTIONS = listOf(
    "今月の収支を分析して",
    "節約のアドバイスをちょうだい",
    "無駄遣いしていないかチェックして",
    "今の資産で目標達成できる？",
    "貸付金の状況を教えて"
)

@Composable
fun ConsultationScreen(
    modifier: Modifier = Modifier,
    dao: FinanceDao,
    gemini: GeminiNanoModel? = null,
    assets: List<AssetEntity> = emptyList(),
    lendings: List<LendingEntity> = emptyList(),
    transactions: List<TransactionEntity> = emptyList(),
    chatSessions: List<ChatSessionEntity> = emptyList(),
    drawerState: DrawerState,
    currentSessionId: String? = null,
    onSessionSelected: (String?) -> Unit = {},
    initialTransaction: Transaction? = null,
    onClearConsultation: () -> Unit = {},
    initialHomeAdviceText: String? = null,
    onClearHomeAdvice: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var suggestedQuestions by remember { mutableStateOf(PRESET_QUESTIONS.shuffled().take(3)) }

    val messages by if (currentSessionId != null) {
        dao.getMessagesForSession(currentSessionId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<ChatMessageEntity>()) }
    }

    // --- 各種状態取得 ---
    val isReady by (gemini?.isReady?.collectAsState() ?: remember { mutableStateOf(false) })
    val isDownloading by (gemini?.isDownloading?.collectAsState() ?: remember { mutableStateOf(false) })
    val progress by (gemini?.downloadProgress?.collectAsState() ?: remember { mutableIntStateOf(0) })
    val errorMsg by (gemini?.errorMessage?.collectAsState() ?: remember { mutableStateOf<String?>(null) })
    val isGenerating by (gemini?.isGenerating?.collectAsState() ?: remember { mutableStateOf(false) })

    // --- サジェスト生成ロジック ---
    fun generateSuggestions(currentMessages: List<ChatMessageEntity>) {
        if (gemini == null || !isReady || isGenerating) return
        scope.launch {
            val assetContext = buildString {
                if (assets.isNotEmpty()) {
                    appendLine("【現在の資産状況】")
                    assets.forEach { appendLine("- ${it.name}: ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})") }
                }
                val activeLendings = lendings.filter { !it.isRecovered }
                if (activeLendings.isNotEmpty()) {
                    appendLine("【貸付状況】")
                    activeLendings.forEach { appendLine("- ${it.personName}への貸付: ¥${String.format(Locale.JAPAN, "%,d", it.amount - it.recoveredAmount)}") }
                }
            }
            val historyContext = "【最近の会話】\n" + currentMessages.takeLast(5).joinToString("\n") {
                "${if (it.isUser) "ユーザー" else "AI"}: ${it.text}"
            }
            val prompt = """
                あなたは家計管理AIアシスタント「覗き魔AI」です。
                これまでのユーザーとの会話や現在の資産状況を踏まえて、ユーザーが次に聞きそうな質問を3つ、日本語で20文字以内の短い文章で提案してください。
                
                $assetContext
                
                $historyContext
                
                出力形式は必ず以下のように、1行に1つの質問のみを記述してください。余計な説明や挨拶は不要です。
                質問1
                質問2
                質問3
            """.trimIndent()
            
            try {
                val response = gemini.generateResponse(prompt)
                suggestedQuestions = response.lines().filter { it.isNotBlank() }.take(3)
            } catch (e: Exception) {
                suggestedQuestions = emptyList()
            }
        }
    }

    // 画面表示時またはセッション初期化時にサジェストを更新
    LaunchedEffect(currentSessionId) {
        if (currentSessionId == null) {
            suggestedQuestions = PRESET_QUESTIONS.shuffled().take(3)
        }
    }

    // 初期相談データの処理
    LaunchedEffect(initialTransaction, initialHomeAdviceText) {
        if (initialTransaction != null) {
            val sessionTitle = "支出「${initialTransaction.name}」の相談"
            val sessionId = UUID.randomUUID().toString()
            
            scope.launch {
                dao.upsertChatSession(ChatSessionEntity(id = sessionId, title = sessionTitle, lastMessageAt = System.currentTimeMillis()))
                val userMsg = "支出「${initialTransaction.name}」(¥${String.format(Locale.JAPAN, "%,d", initialTransaction.amount)})について相談したいです。"
                dao.insertChatMessage(ChatMessageEntity(
                    sessionId = sessionId,
                    text = userMsg,
                    isUser = true
                ))
                
                onSessionSelected(sessionId)
                onClearConsultation()

                // AI応答の生成
                if (gemini != null && isReady) {
                    val assetContext = buildString {
                        if (assets.isNotEmpty()) {
                            appendLine("【現在の資産状況】")
                            assets.forEach { appendLine("- ${it.name}: ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})") }
                        }
                        val activeLendings = lendings.filter { !it.isRecovered }
                        if (activeLendings.isNotEmpty()) {
                            appendLine("【貸付状況】")
                            activeLendings.forEach { appendLine("- ${it.personName}への貸付: ¥${String.format(Locale.JAPAN, "%,d", it.amount - it.recoveredAmount)}") }
                        }
                        appendLine()
                    }

                    val recentTxContext = if (transactions.isNotEmpty()) {
                        "【直近の支出記録】\n" + transactions.take(10).joinToString("\n") { 
                            "- ${SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(it.date))}: ${it.name} ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})"
                        } + "\n\n"
                    } else ""

                    val fullPrompt = """
                        あなたは家計管理AIアシスタント「覗き魔AI」です。
                        ユーザーが特定の支出について相談を始めました。
                        
                        $assetContext
                        $recentTxContext
                        ユーザーの相談: $userMsg
                        
                        この支出について、家計の状況を考慮しつつ、共感したり、時には厳しく指摘したりして、短い対話を始めてください。
                    """.trimIndent()

                    val aiMsgId = UUID.randomUUID().toString()
                    var accumulatedText = ""
                    dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = "...", isUser = false))

                    try {
                        gemini.generateResponseStream(fullPrompt).collect { chunk ->
                            accumulatedText += chunk
                            dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = accumulatedText, isUser = false))
                        }
                        // 生成完了後にサジェストを生成
                        generateSuggestions(dao.getMessagesForSessionSync(sessionId))
                    } catch (e: Exception) {
                        dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = "分析中にエラーが発生しました。", isUser = false))
                    }
                } else {
                    dao.insertChatMessage(ChatMessageEntity(
                        sessionId = sessionId,
                        text = "「${initialTransaction.name}」ですね。${initialTransaction.category}カテゴリの支出ですが、これは未来の自分への投資になりそうですか？それとも単なる浪費でしたか？",
                        isUser = false
                    ))
                }
            }
        } else if (initialHomeAdviceText != null) {
            val sessionTitle = "家計分析の深掘り"
            val sessionId = UUID.randomUUID().toString()
            
            scope.launch {
                dao.upsertChatSession(ChatSessionEntity(id = sessionId, title = sessionTitle, lastMessageAt = System.currentTimeMillis()))
                val userMsg = "ホーム画面で提示された「$initialHomeAdviceText」というアドバイスについて、もっと詳しく教えてください。"
                dao.insertChatMessage(ChatMessageEntity(
                    sessionId = sessionId,
                    text = userMsg,
                    isUser = true
                ))
                
                onSessionSelected(sessionId)
                onClearHomeAdvice()

                // AI応答の生成
                if (gemini != null && isReady) {
                    val assetContext = buildString {
                        if (assets.isNotEmpty()) {
                            appendLine("【現在の資産状況】")
                            assets.forEach { appendLine("- ${it.name}: ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})") }
                        }
                        val activeLendings = lendings.filter { !it.isRecovered }
                        if (activeLendings.isNotEmpty()) {
                            appendLine("【貸付状況】")
                            activeLendings.forEach { appendLine("- ${it.personName}への貸付: ¥${String.format(Locale.JAPAN, "%,d", it.amount - it.recoveredAmount)}") }
                        }
                        appendLine()
                    }

                    val recentTxContext = if (transactions.isNotEmpty()) {
                        "【直近の支出記録】\n" + transactions.take(10).joinToString("\n") { 
                            "- ${SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(it.date))}: ${it.name} ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})"
                        } + "\n\n"
                    } else ""

                    val fullPrompt = """
                        あなたは家計管理AIアシスタント「覗き魔AI」です。
                        ユーザーがホーム画面でのあなたのアドバイスについて深掘りした質問をしました。
                        
                        $assetContext
                        $recentTxContext
                        ユーザーの相談: $userMsg
                        
                        提示したアドバイスの意図や、具体的なアクションプラン、注意点などを日本語で親身に解説してください。
                    """.trimIndent()

                    val aiMsgId = UUID.randomUUID().toString()
                    var accumulatedText = ""
                    dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = "...", isUser = false))

                    try {
                        gemini.generateResponseStream(fullPrompt).collect { chunk ->
                            accumulatedText += chunk
                            dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = accumulatedText, isUser = false))
                        }
                        // 生成完了後にサジェストを生成
                        generateSuggestions(dao.getMessagesForSessionSync(sessionId))
                    } catch (e: Exception) {
                        dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = "分析中にエラーが発生しました。", isUser = false))
                    }
                } else {
                    dao.insertChatMessage(ChatMessageEntity(
                        sessionId = sessionId,
                        text = "あのアドバイスが気になりましたか？具体的にどの部分を詳しく知りたいですか？",
                        isUser = false
                    ))
                }
            }
        }
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // キーボードが表示されたときも最下部にスクロール
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // チャット画面ヘッダー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, contentDescription = "メニュー")
            }
            val currentSession = chatSessions.find { it.id == currentSessionId }
            Text(
                text = if (currentSessionId == null) "新しい相談" else (currentSession?.title ?: "AI相談"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isReady) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = NotionBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NotionBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (errorMsg != null) {
                        Text(errorMsg ?: "", fontSize = 12.sp, color = Color(0xFFE57373))
                    } else if (isDownloading) {
                        Text("AIモデルを準備中... $progress%", fontSize = 12.sp, color = NotionTextSecondary)
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = NotionSafeGreen)
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty() && currentSessionId == null) {
                // 新規チャット時の空画面
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(NotionSafeGreen.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = NotionSafeGreen, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("覗き魔 AI に相談しましょう", color = NotionTextPrimary, fontWeight = FontWeight.Bold)
                    Text("資産や支出について質問してください", color = NotionTextSecondary, fontSize = 14.sp)

                    if (suggestedQuestions.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            suggestedQuestions.forEach { question ->
                                SuggestionChip(
                                    onClick = {
                                        if (isReady && !isGenerating) {
                                            val userMsg = question
                                            scope.launch {
                                                val sessionId = UUID.randomUUID().toString()
                                                dao.insertChatMessage(ChatMessageEntity(sessionId = sessionId, text = userMsg, isUser = true))
                                                dao.upsertChatSession(ChatSessionEntity(id = sessionId, title = userMsg.take(20), lastMessageAt = System.currentTimeMillis()))
                                                onSessionSelected(sessionId)
                                                
                                                val assetContext = buildString {
                                                    if (assets.isNotEmpty()) {
                                                        appendLine("【現在の資産状況】")
                                                        assets.forEach { appendLine("- ${it.name}: ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})") }
                                                    }
                                                    val activeLendings = lendings.filter { !it.isRecovered }
                                                    if (activeLendings.isNotEmpty()) {
                                                        appendLine("【貸付状況】")
                                                        activeLendings.forEach { appendLine("- ${it.personName}への貸付: ¥${String.format(Locale.JAPAN, "%,d", it.amount - it.recoveredAmount)}") }
                                                    }
                                                }
                                                val recentTxContext = if (transactions.isNotEmpty()) {
                                                    "【直近の支出記録】\n" + transactions.take(10).joinToString("\n") { 
                                                        "- ${SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(it.date))}: ${it.name} ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})"
                                                    } + "\n\n"
                                                } else ""
                                                
                                                val fullPrompt = """
                                                    あなたは家計管理AIアシスタント「覗き魔AI」です。
                                                    ユーザーの資産状況と支出履歴をもとに、親身かつ少し鋭い視点でアドバイスしてください。
                                                    
                                                    $assetContext
                                                    $recentTxContext
                                                    ユーザーの質問: $userMsg
                                                """.trimIndent()

                                                val aiMsgId = UUID.randomUUID().toString()
                                                var accumulatedText = ""
                                                dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = "...", isUser = false))
                                                suggestedQuestions = emptyList()

                                                gemini?.generateResponseStream(fullPrompt)?.collect { chunk ->
                                                    accumulatedText += chunk
                                                    dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = accumulatedText, isUser = false))
                                                }
                                                generateSuggestions(dao.getMessagesForSessionSync(sessionId))
                                            }
                                        }
                                    },
                                    label = { Text(question, fontSize = 13.sp, textAlign = TextAlign.Center) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = NotionSafeGreen.copy(alpha = 0.05f),
                                        labelColor = NotionSafeGreen
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        borderColor = NotionSafeGreen.copy(alpha = 0.2f),
                                        enabled = true
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(ChatMessage(id = msg.id, text = msg.text, isUser = msg.isUser))
                    }
                }
            }
            
        }

        // サジェストされた質問（チャット進行中のみ表示。空画面時は中央に表示するため）
        if (suggestedQuestions.isNotEmpty() && !isGenerating && messages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedQuestions.forEach { question ->
                    SuggestionChip(
                        onClick = {
                            if (isReady && !isGenerating) {
                                val userMsg = question
                                scope.launch {
                                    val sessionId = currentSessionId ?: UUID.randomUUID().toString()
                                    dao.insertChatMessage(ChatMessageEntity(sessionId = sessionId, text = userMsg, isUser = true))
                                    if (currentSessionId == null) {
                                        dao.upsertChatSession(ChatSessionEntity(id = sessionId, title = userMsg.take(20), lastMessageAt = System.currentTimeMillis()))
                                        onSessionSelected(sessionId)
                                    }
                                    
                                    // メッセージ送信後の共通処理を共通化したいが、ここではinputText送信と同じロジックを記述
                                    // 実際には handleSendMessage(userMsg) のような関数を作るのが良い
                                    
                                    val assetContext = buildString {
                                        if (assets.isNotEmpty()) {
                                            appendLine("【現在の資産状況】")
                                            assets.forEach { appendLine("- ${it.name}: ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})") }
                                        }
                                        val activeLendings = lendings.filter { !it.isRecovered }
                                        if (activeLendings.isNotEmpty()) {
                                            appendLine("【貸付状況】")
                                            activeLendings.forEach { appendLine("- ${it.personName}への貸付: ¥${String.format(Locale.JAPAN, "%,d", it.amount - it.recoveredAmount)}") }
                                        }
                                    }
                                    val recentTxContext = if (transactions.isNotEmpty()) {
                                        "【直近の支出記録】\n" + transactions.take(10).joinToString("\n") { 
                                            "- ${SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(it.date))}: ${it.name} ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})"
                                        } + "\n\n"
                                    } else ""
                                    
                                    val fullPrompt = """
                                        あなたは家計管理AIアシスタント「覗き魔AI」です。
                                        ユーザーの資産状況と支出履歴をもとに、親身かつ少し鋭い視点でアドバイスしてください。
                                        
                                        $assetContext
                                        $recentTxContext
                                        ユーザーの質問: $userMsg
                                    """.trimIndent()

                                    val aiMsgId = UUID.randomUUID().toString()
                                    var accumulatedText = ""
                                    dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = "...", isUser = false))
                                    suggestedQuestions = emptyList() // 送信時にクリア

                                    gemini?.generateResponseStream(fullPrompt)?.collect { chunk ->
                                        accumulatedText += chunk
                                        dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = accumulatedText, isUser = false))
                                    }
                                    generateSuggestions(dao.getMessagesForSessionSync(sessionId))
                                }
                            }
                        },
                        label = { Text(question, fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = NotionSafeGreen.copy(alpha = 0.05f),
                            labelColor = NotionSafeGreen
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            borderColor = NotionSafeGreen.copy(alpha = 0.2f),
                            enabled = true
                        )
                    )
                }
            }
        }

        // 入力フォーム
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 0.dp)
                .imePadding()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Color(0x22000000),
                        spotColor = Color(0x33000000)
                    ),
                shape = RoundedCornerShape(12.dp),
                color = NotionWhite,
                border = BorderStroke(1.dp, NotionBorder),
                tonalElevation = 0.dp
            ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputText.isEmpty()) Text("相談内容を入力", color = NotionTextSecondary, fontSize = 15.sp)
                            androidx.compose.foundation.text.BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = NotionTextPrimary),
                                maxLines = 5
                            )
                        }
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() && gemini != null) {
                                    val userMsg = inputText
                                    inputText = ""
                                    scope.launch {
                                        val sessionId = currentSessionId ?: UUID.randomUUID().toString()
                                        
                                        // ユーザーメッセージ保存
                                        dao.insertChatMessage(ChatMessageEntity(sessionId = sessionId, text = userMsg, isUser = true))
                                        
                                        if (currentSessionId == null) {
                                            dao.upsertChatSession(ChatSessionEntity(id = sessionId, title = userMsg.take(20), lastMessageAt = System.currentTimeMillis()))
                                            onSessionSelected(sessionId)
                                        } else {
                                            chatSessions.find { it.id == sessionId }?.let { session ->
                                                dao.upsertChatSession(session.copy(lastMessageAt = System.currentTimeMillis()))
                                            }
                                        }

                                        // AIへのプロンプト構築
                                        val assetContext = buildString {
                                            if (assets.isNotEmpty()) {
                                                appendLine("【現在の資産状況】")
                                                assets.forEach { appendLine("- ${it.name}: ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})") }
                                            }
                                            val activeLendings = lendings.filter { !it.isRecovered }
                                            if (activeLendings.isNotEmpty()) {
                                                appendLine("【貸付状況】")
                                                activeLendings.forEach { appendLine("- ${it.personName}への貸付: ¥${String.format(Locale.JAPAN, "%,d", it.amount - it.recoveredAmount)}") }
                                            }
                                            appendLine()
                                        }

                                        val recentTxContext = if (transactions.isNotEmpty()) {
                                            "【直近の支出記録】\n" + transactions.take(10).joinToString("\n") { 
                                                "- ${SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(it.date))}: ${it.name} ¥${String.format(Locale.JAPAN, "%,d", it.amount)} (${it.category})"
                                            } + "\n\n"
                                        } else ""

                                        val historyContext = "【これまでの会話】\n" + messages.takeLast(5).joinToString("\n") { 
                                            "${if (it.isUser) "ユーザー" else "AI"}: ${it.text}"
                                        } + "\n\n"

                                        val fullPrompt = """
                                            あなたは家計管理AIアシスタント「覗き魔AI」です。
                                            ユーザーの資産状況と支出履歴をもとに、親身かつ少し鋭い視点でアドバイスしてください。
                                            
                                            $assetContext
                                            $recentTxContext
                                            $historyContext
                                            ユーザーの質問: $userMsg
                                        """.trimIndent()

                                        val aiMsgId = UUID.randomUUID().toString()
                                        var accumulatedText = ""
                                        dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = "...", isUser = false))
                                        suggestedQuestions = emptyList() // 送信時にクリア

                                        gemini.generateResponseStream(fullPrompt).collect { chunk ->
                                            accumulatedText += chunk
                                            dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = accumulatedText, isUser = false))
                                        }
                                        // 生成完了後にサジェストを生成
                                        generateSuggestions(dao.getMessagesForSessionSync(sessionId))
                                        
                                        // セッションの最終更新日時を再度更新
                                        chatSessions.find { it.id == sessionId }?.let { session ->
                                            dao.upsertChatSession(session.copy(lastMessageAt = System.currentTimeMillis()))
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.background(if (inputText.isNotBlank()) NotionSafeGreen else NotionBorder, CircleShape).size(36.dp),
                            enabled = inputText.isNotBlank() && isReady
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }


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

@Composable
fun ThinkingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 0
                1f at 400
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 200
                1f at 600
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 400
                1f at 800
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NotionSafeGreen.copy(alpha = alpha1)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NotionSafeGreen.copy(alpha = alpha2)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NotionSafeGreen.copy(alpha = alpha3)))
    }
}



/**
 * 簡易的なMarkdownパーサー
 * **テキスト** を太字に変換する
 */
fun parseMarkdown(text: String) = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                append(part)
            }
        } else {
            append(part)
        }
    }
}



// --- 5. 設定画面（貯金目標シミュレーション） ---

val currencyVisualTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    val original = text.text
    if (original.isEmpty()) {
        return@VisualTransformation androidx.compose.ui.text.input.TransformedText(
            androidx.compose.ui.text.AnnotatedString("¥ 0"),
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int) = 3
                override fun transformedToOriginal(offset: Int) = 0
            }
        )
    }

    val formatted = try {
        "¥ " + String.format(Locale.JAPAN, "%,d", original.toLong())
    } catch (e: Exception) {
        "¥ $original"
    }

    val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            if (offset <= 0) return 2
            var digitCount = 0
            var i = 0
            while (digitCount < offset && i < formatted.length) {
                if (formatted[i].isDigit()) digitCount++
                i++
            }
            return maxOf(2, i)
        }

        override fun transformedToOriginal(offset: Int): Int {
            var digitCount = 0
            for (i in 0 until minOf(offset, formatted.length)) {
                if (formatted[i].isDigit()) digitCount++
            }
            return digitCount
        }
    }
    androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), offsetMapping)
}

@Composable
fun GeneralSettingsScreen(
    dao: FinanceDao,
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
    var showBackupHistory by remember { mutableStateOf(false) }

    BackHandler {
        if (showBackupHistory) {
            showBackupHistory = false
        } else {
            onBack()
        }
    }

    if (showBackupHistory) {
        BackupHistoryScreen(
            dao = dao,
            onBack = { showBackupHistory = false }
        )
    } else {
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
                    HorizontalDivider(color = NotionBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.History,
                        title = "エクスポート履歴",
                        description = "過去のバックアップ用パスワードを確認",
                        onClick = { showBackupHistory = true }
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
fun BackupHistoryScreen(
    dao: FinanceDao,
    onBack: () -> Unit
) {
    val history by dao.getAllBackupHistory().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var historyToDelete by remember { mutableStateOf<BackupHistoryEntity?>(null) }

    if (historyToDelete != null) {
        DeleteConfirmDialog(
            text = "このバックアップ履歴を削除しますか？\n（ファイル自体は削除されません）",
            onDismiss = { historyToDelete = null },
            onConfirm = {
                scope.launch {
                    historyToDelete?.let { dao.deleteBackupHistory(it) }
                    historyToDelete = null
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NotionBackground)
    ) {
        ScreenHeader(
            title = "エクスポート履歴",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                }
            }
        )

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("履歴はありません", color = NotionTextSecondary)
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp))
                ) {
                    Column {
                        history.forEachIndexed { index, item ->
                            val haptic = LocalHapticFeedback.current

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            historyToDelete = item
                                        }
                                    )
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(NotionSafeGreen.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = NotionSafeGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.fileName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NotionTextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN).format(Date(item.date)),
                                            fontSize = 11.sp,
                                            color = NotionTextSecondary
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(NotionBackground, RoundedCornerShape(6.dp))
                                    ) {
                                        Text(
                                            text = item.password,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = NotionTextPrimary,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            if (index < history.lastIndex) {
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSettingsScreen(
    dao: FinanceDao,
    gemini: GeminiNanoModel? = null,
    goalAiText: String = "",
    onRefreshAi: () -> Unit = {},
    isKeypadVisible: Boolean = false,
    onKeypadVisibilityChange: (Boolean) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val assets by dao.getAllAssets().collectAsState(initial = emptyList())
    val lendings by dao.getAllLendings().collectAsState(initial = emptyList())
    val goalSetting by dao.getGoalSetting().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val defaultDateMillis = remember { Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.timeInMillis }

    var titleText by rememberSaveable { mutableStateOf("") }
    var targetAmountText by rememberSaveable { mutableStateOf("") }
    var targetDateMillis by rememberSaveable { mutableLongStateOf(defaultDateMillis) }
    var startDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val totalLendingAmount = lendings.filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
    val actualTotalAssets = assets.sumOf { it.amount }.toLong() + totalLendingAmount
    var monthlyIncomeText by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var goalKeypadTarget by remember { mutableStateOf("amount") } // "amount" or "income"

    val aiIsReady by (gemini?.isReady ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val aiIsGenerating by (gemini?.isGenerating ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val aiIsChecking by (gemini?.isCheckingStatus ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    val isAiWaiting = aiIsChecking && goalAiText.isEmpty()
    val isAiGeneratingNow = aiIsGenerating && goalAiText.isEmpty()
    val showAiProgress = isAiWaiting || isAiGeneratingNow
    val aiStatusLabel = when {
        isAiWaiting -> "AI を確認中..."
        isAiGeneratingNow -> "分析中..."
        else -> null
    }

    // DBから読み込み（初回のみ）
    var loadedFromDb by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(goalSetting) {
        if (!loadedFromDb && goalSetting != null) {
            val g = goalSetting!!
            if (g.title.isNotEmpty()) titleText = g.title
            if (g.targetAmount > 0) targetAmountText = g.targetAmount.toString()
            if (g.monthlyIncome > 0) monthlyIncomeText = g.monthlyIncome.toString()
            if (g.targetDateMillis > 0) targetDateMillis = g.targetDateMillis
            if (g.startDateMillis > 0) startDateMillis = g.startDateMillis
            showResults = g.showResults
            loadedFromDb = true
        }
    }

    // 変更をDBに保存するヘルパー
    fun saveGoal() {
        scope.launch {
            dao.upsertGoalSetting(
                GoalSettingEntity(
                    title = titleText,
                    targetAmount = targetAmountText.toLongOrNull() ?: 0L,
                    monthlyIncome = monthlyIncomeText.toLongOrNull() ?: 0L,
                    targetDateMillis = targetDateMillis,
                    showResults = showResults,
                    startDateMillis = startDateMillis
                )
            )
        }
    }

    // 計算
    val remainingDays = ((targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
    val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
    val targetAmount = targetAmountText.toLongOrNull() ?: 0L
    val monthlyIncome = monthlyIncomeText.toLongOrNull() ?: 0L
    val totalExpectedIncome = (monthlyIncome * remainingMonths).toLong()
    val totalSpendable = (actualTotalAssets + totalExpectedIncome - targetAmount).coerceAtLeast(0L)
    val monthlyBudget = if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
    val dailyLimit = if (remainingDays > 0) totalSpendable / remainingDays else 0L

    val animatedMonthly by animateIntAsState(targetValue = monthlyBudget.toInt(), label = "monthly")
    val animatedDaily by animateIntAsState(targetValue = dailyLimit.toInt(), label = "daily")
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN) }

    val goalAchieved = actualTotalAssets >= targetAmount && targetAmount > 0L && showResults
    val currentStep = when { goalAchieved -> 2; showResults -> 1; else -> 0 }
    val canStart = targetAmount > 0L && monthlyIncomeText.isNotEmpty()

    BackHandler(enabled = isKeypadVisible) { onKeypadVisibilityChange(false) }

    Box(modifier = Modifier.fillMaxSize().background(NotionBackground)) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // タイトル行
        val screenTitle = when {
            goalAchieved -> "目標達成"
            showResults -> "目標経過"
            else -> "目標設定"
        }

        ScreenHeader(
            title = screenTitle,
            navigationIcon = onBack?.let {
                {
                    IconButton(onClick = it) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {

        GoalStepper(currentStep = currentStep)

        Spacer(Modifier.height(12.dp))

        // ━━━━━━━━━━━ 達成画面 ━━━━━━━━━━━
        if (goalAchieved) {
            GoalAchievedView(
                titleText = titleText,
                targetAmount = targetAmount,
                startDateMillis = startDateMillis,
                targetDateMillis = targetDateMillis,
                aiIsReady = aiIsReady,
                aiIsGenerating = aiIsGenerating,
                showAiProgress = showAiProgress,
                aiStatusLabel = aiStatusLabel,
                goalAiText = goalAiText,
                onRefreshAi = onRefreshAi,
                onResetGoal = {
                    titleText = ""
                    targetAmountText = ""
                    monthlyIncomeText = ""
                    targetDateMillis = Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.timeInMillis
                    startDateMillis = System.currentTimeMillis()
                    showResults = false
                    saveGoal()
                }
            )
        } else if (!showResults) {
            // ━━━━━━━━━━━ 設定画面 ━━━━━━━━━━━
            GoalSetupView(
                titleText = titleText,
                onTitleChange = { titleText = it; saveGoal() },
                targetAmountText = targetAmountText,
                onAmountClick = { 
                    focusManager.clearFocus()
                    goalKeypadTarget = "amount"
                    onKeypadVisibilityChange(true)
                },
                monthlyIncomeText = monthlyIncomeText,
                onIncomeClick = { 
                    focusManager.clearFocus()
                    goalKeypadTarget = "income"
                    onKeypadVisibilityChange(true)
                },
                targetDateMillis = targetDateMillis,
                onDateClick = { 
                    focusManager.clearFocus()
                    showDatePicker = true
                },
                dateFormatter = dateFormatter,
                canStart = canStart,
                actualTotalAssets = actualTotalAssets,
                totalSpendable = totalSpendable,
                monthlyBudget = monthlyBudget,
                onStart = {
                    showResults = true
                    if (startDateMillis == 0L || goalSetting?.startDateMillis?.let { it == 0L } == true) {
                        startDateMillis = System.currentTimeMillis()
                    }
                    saveGoal()
                }
            )
        } else {
            // ━━━━━━━━━━━ 継続画面 ━━━━━━━━━━━
            GoalProgressView(
                titleText = titleText,
                actualTotalAssets = actualTotalAssets,
                targetAmount = targetAmount,
                startDateMillis = startDateMillis,
                targetDateMillis = targetDateMillis,
                remainingDays = remainingDays,
                totalSpendable = totalSpendable,
                monthlyBudget = monthlyBudget,
                aiIsReady = aiIsReady,
                aiIsGenerating = aiIsGenerating,
                showAiProgress = showAiProgress,
                aiStatusLabel = aiStatusLabel,
                goalAiText = goalAiText,
                onRefreshAi = onRefreshAi,
                onEditClick = { showResults = false; saveGoal() }
            )
        }

        Spacer(Modifier.height(40.dp))
        } // end inner Column
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    targetDateMillis = datePickerState.selectedDateMillis ?: targetDateMillis
                    showDatePicker = false
                    saveGoal()
                }) {
                    Text("OK", color = NotionSafeGreen)
                }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } }
        ) { DatePicker(state = datePickerState) }
    }

    // 目標設定用キーパッド（記録画面と同じ ModalBottomSheet 形式）
    if (isKeypadVisible) {
        ModalBottomSheet(
            onDismissRequest = { saveGoal(); onKeypadVisibilityChange(false) },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) },
            scrimColor = Color.Transparent
        ) {
            CustomKeypad(
                onNumberClick = { num ->
                    if (goalKeypadTarget == "amount") {
                        if (targetAmountText == "0") targetAmountText = num else targetAmountText += num
                    } else {
                        if (monthlyIncomeText == "0") monthlyIncomeText = num else monthlyIncomeText += num
                    }
                },
                onOperatorClick = { op ->
                    if (goalKeypadTarget == "amount") {
                        if (targetAmountText.isNotEmpty() && !targetAmountText.last().toString().matches(Regex("[-+*/.]"))) targetAmountText += op
                    } else {
                        if (monthlyIncomeText.isNotEmpty() && !monthlyIncomeText.last().toString().matches(Regex("[-+*/.]"))) monthlyIncomeText += op
                    }
                },
                onDeleteClick = {
                    if (goalKeypadTarget == "amount") {
                        if (targetAmountText.isNotEmpty()) targetAmountText = targetAmountText.dropLast(1)
                    } else {
                        if (monthlyIncomeText.isNotEmpty()) monthlyIncomeText = monthlyIncomeText.dropLast(1)
                    }
                },
                onClearAllClick = {
                    if (goalKeypadTarget == "amount") {
                        targetAmountText = ""
                    } else {
                        monthlyIncomeText = ""
                    }
                },
                onConfirmClick = {
                    if (goalKeypadTarget == "amount") {
                        try { targetAmountText = evaluateExpression(targetAmountText).toString() } catch (e: Exception) {}
                    } else {
                        try { monthlyIncomeText = evaluateExpression(monthlyIncomeText).toString() } catch (e: Exception) {}
                    }
                    saveGoal()
                },
                onSaveClick = {
                    if (goalKeypadTarget == "amount") {
                        if (targetAmountText.any { it in "+-*/" }) {
                            try { targetAmountText = evaluateExpression(targetAmountText).toString() } catch (e: Exception) {}
                        } else {
                            onKeypadVisibilityChange(false)
                        }
                    } else {
                        if (monthlyIncomeText.any { it in "+-*/" }) {
                            try { monthlyIncomeText = evaluateExpression(monthlyIncomeText).toString() } catch (e: Exception) {}
                        } else {
                            onKeypadVisibilityChange(false)
                        }
                    }
                    saveGoal()
                },
                onCloseClick = { saveGoal(); onKeypadVisibilityChange(false) },
                isSaveEnabled = true,
                actionColor = NotionSafeGreen
            )
        }
    }

} // end Box
} // end BudgetSettingsScreen

@Composable
fun GoalAchievedView(
    titleText: String,
    targetAmount: Long,
    startDateMillis: Long,
    targetDateMillis: Long,
    aiIsReady: Boolean,
    aiIsGenerating: Boolean,
    showAiProgress: Boolean,
    aiStatusLabel: String?,
    goalAiText: String,
    onRefreshAi: () -> Unit,
    onResetGoal: () -> Unit
) {
    // お祝いカード
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF9E6), RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0xFFFFD54F), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("🎉 Congratulations! 🎉", color = Color(0xFFF57F17), fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            val achievementTitle = if (titleText.isNotEmpty()) "「${titleText}」達成おめでとうございます" else "目標達成おめでとうございます"
            Text(achievementTitle, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("¥ ${String.format(Locale.JAPAN, "%,d", targetAmount)}", color = Color(0xFFF57F17), fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
        }
    }

    Spacer(Modifier.height(32.dp))

    // 覗き魔 AI アドバイス
    AiAdvisorCard(
        aiIsReady = aiIsReady,
        aiIsGenerating = aiIsGenerating,
        showAiProgress = showAiProgress,
        aiStatusLabel = aiStatusLabel,
        goalAiText = goalAiText,
        onRefreshAi = onRefreshAi,
        defaultText = "目標達成おめでとうございます！これからもあなたの資産を覗き続けますよ。"
    )

    Spacer(Modifier.height(32.dp))

    // 次の目標へボタン
    Button(
        onClick = onResetGoal,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen)
    ) {
        Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("次の目標を設定する", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GoalSetupView(
    titleText: String,
    onTitleChange: (String) -> Unit,
    targetAmountText: String,
    onAmountClick: () -> Unit,
    monthlyIncomeText: String,
    onIncomeClick: () -> Unit,
    targetDateMillis: Long,
    onDateClick: () -> Unit,
    dateFormatter: SimpleDateFormat,
    canStart: Boolean,
    actualTotalAssets: Long,
    totalSpendable: Long,
    monthlyBudget: Long,
    onStart: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    SectionLabel("目標の定義")
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 目標タイトル
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, NotionBorder)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                    Icon(Icons.Default.Flag, null, tint = NotionSafeGreen, modifier = Modifier.padding(10.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("目標タイトル", fontSize = 12.sp, color = NotionTextSecondary)
                    androidx.compose.foundation.text.BasicTextField(
                        value = titleText,
                        onValueChange = onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        decorationBox = { inner ->
                            if (titleText.isEmpty()) Text("旅行のための貯金", color = NotionTextSecondary.copy(alpha = 0.5f), fontSize = 15.sp)
                            inner()
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            }
        }

        // 目標貯金額
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, NotionBorder)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                    Icon(Icons.Default.Star, null, tint = NotionSafeGreen, modifier = Modifier.padding(10.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f).clickable { onAmountClick() }) {
                    Text("目標貯金額", fontSize = 12.sp, color = NotionTextSecondary)
                    Text(
                        text = if (targetAmountText.isEmpty()) "¥ " else "¥ ${String.format(Locale.JAPAN, "%,d", targetAmountText.toLongOrNull() ?: 0L)}",
                        color = if (targetAmountText.isEmpty()) NotionSafeGreen.copy(alpha = 0.5f) else NotionSafeGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 月収
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, NotionBorder)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = NotionSafeGreen, modifier = Modifier.padding(10.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f).clickable { onIncomeClick() }) {
                    Text("月平均の手取り収入", fontSize = 12.sp, color = NotionTextSecondary)
                    Text(
                        text = if (monthlyIncomeText.isEmpty()) "¥ " else "¥ ${String.format(Locale.JAPAN, "%,d", monthlyIncomeText.toLongOrNull() ?: 0L)}",
                        color = if (monthlyIncomeText.isEmpty()) NotionSafeGreen.copy(alpha = 0.5f) else NotionSafeGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 目標達成日
        InputTile(
            icon = Icons.Default.CalendarMonth,
            label = "目標達成日",
            value = dateFormatter.format(java.util.Date(targetDateMillis)),
            onClick = onDateClick,
            accentColor = NotionSafeGreen
        )
    }

    Spacer(Modifier.height(24.dp))

    // シミュレーション結果ブロック
    SectionLabel("シミュレーション結果")
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 現在の保有資産
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, NotionBorder)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFFFB74D).copy(alpha = 0.08f)) {
                    Icon(Icons.Default.AccountBalance, null, tint = Color(0xFFFFB74D), modifier = Modifier.padding(10.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("現在の保有資産", fontSize = 12.sp, color = NotionTextSecondary)
                    Text("¥ ${String.format(Locale.JAPAN, "%,d", actualTotalAssets)}", color = Color(0xFFFFB74D), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (canStart) {
            // 許容支出（合計）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, NotionBorder)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF2196F3).copy(alpha = 0.08f)) {
                        Icon(Icons.Default.Wallet, null, tint = Color(0xFF2196F3), modifier = Modifier.padding(10.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("合計の許容支出", fontSize = 12.sp, color = NotionTextSecondary)
                        Text("¥ ${String.format(Locale.JAPAN, "%,d", totalSpendable)}", color = Color(0xFF2196F3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 月の予算
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, NotionBorder)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF2196F3).copy(alpha = 0.08f)) {
                        Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF2196F3), modifier = Modifier.padding(10.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("月の予算", fontSize = 12.sp, color = NotionTextSecondary)
                        Text("¥ ${String.format(Locale.JAPAN, "%,d", monthlyBudget)}", color = Color(0xFF2196F3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    // 貯蓄を開始するボタン
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = canStart,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen, disabledContainerColor = NotionBorder)
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("貯蓄を開始する", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GoalProgressView(
    titleText: String,
    actualTotalAssets: Long,
    targetAmount: Long,
    startDateMillis: Long,
    targetDateMillis: Long,
    remainingDays: Int,
    totalSpendable: Long,
    monthlyBudget: Long,
    aiIsReady: Boolean,
    aiIsGenerating: Boolean,
    showAiProgress: Boolean,
    aiStatusLabel: String?,
    goalAiText: String,
    onRefreshAi: () -> Unit,
    onEditClick: () -> Unit
) {
    val progressRatio = if (targetAmount > 0) (actualTotalAssets.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f) else 0f
    val progressPercent = (progressRatio * 100).toInt()

    val totalGoalDays = ((targetDateMillis - startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(1L)
    val passedDays = ((System.currentTimeMillis() - startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
    val timeProgressRatio = (passedDays.toFloat() / totalGoalDays.toFloat()).coerceIn(0f, 1f)

    // 1枚の大きなカードにまとめる
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column {
            val statusTitle = if (titleText.isNotEmpty()) titleText else "貯金"
            SectionLabel("$statusTitle の進捗")
            Spacer(modifier = Modifier.height(8.dp))
            
            // 現在の進捗ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) {
                                append("¥ ${String.format(Locale.JAPAN, "%,d", actualTotalAssets)}")
                            }
                            withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = NotionTextSecondary)) {
                                append(" / ¥ ${String.format(Locale.JAPAN, "%,d", targetAmount)}")
                            }
                        },
                        color = Color(0xFF2196F3),
                        letterSpacing = (-1).sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 目標達成プログレス
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("目標達成", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "$progressPercent%",
                        color = NotionTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressRatio },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = Color(0xFF2196F3),
                    trackColor = NotionBorder,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 期間経過プログレス
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("期限", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("あと $remainingDays 日", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { timeProgressRatio },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = NotionSafeGreen,
                    trackColor = NotionBorder,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 覗き魔 AI アドバイス (カード内に配置)
            AiAdvisorCard(
                aiIsReady = aiIsReady,
                aiIsGenerating = aiIsGenerating,
                showAiProgress = showAiProgress,
                aiStatusLabel = aiStatusLabel,
                goalAiText = goalAiText,
                onRefreshAi = onRefreshAi
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel("現在のシミュレーション")
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, NotionBorder)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF2196F3).copy(alpha = 0.08f)) {
                            Icon(Icons.Default.Wallet, null, tint = Color(0xFF2196F3), modifier = Modifier.padding(10.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("合計の許容支出", fontSize = 12.sp, color = NotionTextSecondary)
                            Text("¥ ${String.format(Locale.JAPAN, "%,d", totalSpendable)}", color = Color(0xFF2196F3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, NotionBorder)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF2196F3).copy(alpha = 0.08f)) {
                            Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF2196F3), modifier = Modifier.padding(10.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("月の予算", fontSize = 12.sp, color = NotionTextSecondary)
                            Text("¥ ${String.format(Locale.JAPAN, "%,d", monthlyBudget)}", color = Color(0xFF2196F3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    
    // 設定変更ボタン（より控えめに）
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(
            onClick = onEditClick,
            colors = ButtonDefaults.textButtonColors(contentColor = NotionTextSecondary)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("設定を変更する", fontSize = 13.sp)
        }
    }
}

@Composable
fun AiAdvisorCard(
    aiIsReady: Boolean,
    aiIsGenerating: Boolean,
    showAiProgress: Boolean,
    aiStatusLabel: String?,
    goalAiText: String,
    onRefreshAi: () -> Unit,
    defaultText: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp) // 最初から高さを確保してレイアウトの跳ねを防止
            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(NotionSafeGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, "AI", tint = NotionSafeGreen, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("覗き魔 AI", color = NotionSafeGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (aiIsReady && !aiIsGenerating) {
                    IconButton(onClick = onRefreshAi, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, "再生成", tint = NotionSafeGreen, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (showAiProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                    color = NotionSafeGreen,
                    trackColor = NotionSafeGreen.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
            }
            if (aiStatusLabel != null) {
                Text(aiStatusLabel, color = NotionTextSecondary.copy(alpha = 0.6f), fontSize = 12.sp)
            } else if (goalAiText.isNotEmpty()) {
                Text(goalAiText, color = NotionTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            } else if (defaultText != null) {
                Text(defaultText, color = NotionTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

// --- ヘルパー Composable ---

@Composable
private fun GoalStepper(currentStep: Int) {
    val steps = listOf("設定", "継続", "達成")
    val stepColors = listOf(NotionSafeGreen, Color(0xFF2196F3), Color(0xFFFFB74D))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val isActive = index == currentStep
            val isDone = index < currentStep
            val color = when {
                isActive || isDone -> stepColors[index]
                else -> NotionBorder
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(20.dp).background(color.copy(alpha = if (isActive) 0.15f else 0.05f), CircleShape).border(1.5.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(10.dp))
                    } else {
                        Text("${index + 1}", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Text(label, color = if (isActive) color else NotionTextSecondary, fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
            }
            if (index < steps.size - 1) {
                Box(modifier = Modifier.weight(1f).height(1.dp).padding(horizontal = 6.dp).background(if (index < currentStep) stepColors[index] else NotionBorder))
            }
        }
    }
}



@Composable
private fun SectionLabel(text: String) {
    Text(text, color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
}

@Composable
fun UnifiedAssetCardRow(
    title: String,
    subtitle: String,
    amount: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            }
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, NotionBorder, RoundedCornerShape(14.dp))
            .then(interactionModifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = subtitle, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = NotionTextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "¥ ${String.format(Locale.JAPAN, "%,d", amount)}",
            color = if (amount < 0) Color(0xFFE57373) else NotionTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AssetHistoryItem(
    name: String,
    amount: String,
    memo: String,
    balanceAfter: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.MoreHoriz,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = memo, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(name, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("$memo・$balanceAfter", color = NotionTextSecondary, fontSize = 12.sp)
            }
        }
        Text(amount, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AssetGroupItemRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NotionTextPrimary, fontSize = 15.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = NotionTextSecondary)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
}

@Composable
fun DeleteConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit = {}) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除の確認", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = NotionTextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除", color = Color(0xFFE57373)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        containerColor = Color.White, shape = RoundedCornerShape(12.dp)
    )
}

// --- 共通ヘッダーコンポーネント ---

@Composable
fun ScreenHeader(
    title: String,
    navigationIcon: @Composable (() -> Unit)? = null,
    titleStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (navigationIcon != null) {
            navigationIcon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            color = NotionTextPrimary,
            style = titleStyle,
            modifier = Modifier.weight(1f)
        )
        if (trailingContent != null) trailingContent()
    }
}
}
