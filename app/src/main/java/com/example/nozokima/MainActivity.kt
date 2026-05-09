@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.nozokima

import android.os.Bundle
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.nozokima.model.*
import com.example.nozokima.ui.components.*
import com.example.nozokima.ui.screens.*
import com.example.nozokima.util.*
import com.example.nozokima.data.local.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.launch
import ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

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
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
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
            val activity = LocalContext.current as? android.app.Activity
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
                val context = LocalContext.current
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
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
                                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
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
}
