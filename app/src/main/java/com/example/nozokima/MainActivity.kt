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
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nozokima.model.*
import com.example.nozokima.ui.components.*
import com.example.nozokima.ui.screens.*
import com.example.nozokima.util.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.viewmodel.MainViewModel
import com.example.nozokima.ui.viewmodel.HomeViewModel
import com.example.nozokima.ui.viewmodel.ViewModelFactory
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
        onError: (String) -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt =
            BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if ((errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) && (errorCode != BiometricPrompt.ERROR_USER_CANCELED)) {
                        onError(errString.toString())
                    }
                }
                },
            )

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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                }
            }

            val dao = db.financeDao()
            val factory = remember { ViewModelFactory(dao, gemini) }
            val mainViewModel: MainViewModel = viewModel(factory = factory)
            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            
            val mainUiState by mainViewModel.uiState.collectAsState()
            val homeUiState by homeViewModel.uiState.collectAsState()
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

            var selectedTab by remember { mutableIntStateOf(0) }
            var consultingTransaction by remember { mutableStateOf<Transaction?>(null) }
            var initialHomeAdviceText by remember { mutableStateOf<String?>(null) }
            var recoveryLending by remember { mutableStateOf<LendingEntity?>(null) }
            var initialAssetCategoryFilter by remember { mutableStateOf<String?>(null) }
            var isGoalKeypadVisible by remember { mutableStateOf(value = false) }

            var backupPassword by remember { mutableStateOf("") }
            var showBackupPasswordDialog by remember { mutableStateOf(value = false) }
            var backupMode by remember { mutableStateOf("") }
            var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var showAppLockPasswordDialog by remember { mutableStateOf(value = false) }
            var appLockDialogMode by remember { mutableStateOf("set") }

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            var isAppLocked by rememberSaveable { mutableStateOf(true) }
            var isExternalActivityLaunching by rememberSaveable { mutableStateOf(false) }
            val appSettings = mainUiState.appSettings
            val isLoading = appSettings == null
            val showLockScreen = isAppLocked && (appSettings?.isAppLockEnabled == true)
            val lifecycle = LocalLifecycleOwner.current.lifecycle

            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        if (!isExternalActivityLaunching) isAppLocked = true
                    } else if (event == Lifecycle.Event.ON_RESUME) {
                        isExternalActivityLaunching = false
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            var currentChatSessionId by rememberSaveable { mutableStateOf<String?>(null) }

            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                uri?.let {
                    scope.launch {
                        try {
                            val zipData = BackupManager(dao, applicationContext).exportData(backupPassword)
                            contentResolver.openOutputStream(it)?.use { output ->
                                output.write(zipData)
                            }
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

            val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

            LaunchedEffect(Unit) {
                mainViewModel.checkAiStatus()
            }

            // デフォルトカテゴリと資産の初期化
            LaunchedEffect(Unit) {
                scope.launch {
                    if (dao.getAppSettingsSync() == null) {
                        dao.upsertAppSettings(AppSettingsEntity())
                    }
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
                    if (dao.getAllAssetsList().isEmpty()) {
                        val now = System.currentTimeMillis()
                        val defaultAssets = listOf(
                            AssetEntity(UUID.randomUUID().toString(), "現金", 0, "現金", now),
                            AssetEntity(UUID.randomUUID().toString(), "銀行", 0, "銀行", now),
                            AssetEntity(UUID.randomUUID().toString(), "電子マネー", 0, "電子マネー", now),
                            AssetEntity(UUID.randomUUID().toString(), "カード", 0, "カード", now)
                        )
                        defaultAssets.forEach { dao.insertAsset(it) }
                    }
                }
            }

            if (mainUiState.aiStatus == FeatureStatus.DOWNLOADABLE) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("追加のダウンロード") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("AI相談の利用にはAIモデル(Gemini Nano)が必要です。\nダウンロードはWi-Fi 接続を推奨します。", fontSize = 14.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { mainViewModel.startAiDownload() }) { Text("ダウンロード開始") }
                    },
                    dismissButton = {
                        TextButton(onClick = {}) { Text("キャンセル") }
                    }
                )
            }

            LaunchedEffect(mainUiState.chatSessions) {
            }

            val activity = LocalContext.current as? android.app.Activity
            DisposableEffect(showBackupPasswordDialog) {
                if (showBackupPasswordDialog && (backupMode == "export")) {
                    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose { activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
            }

            if (showAppLockPasswordDialog) {
                var currentPasswordInput by remember { mutableStateOf("") }
                var newPasswordInput by remember { mutableStateOf("") }
                var confirmPasswordInput by remember { mutableStateOf("") }
                var step by remember { mutableIntStateOf(0) }
                val isDisableMode = appLockDialogMode == "disable"
                val isChangeMode = appLockDialogMode == "change"

                ModalBottomSheet(
                    onDismissRequest = { showAppLockPasswordDialog = false },
                    containerColor = Color.White,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val title = when(appLockDialogMode) {
                            "set" -> if (step == 0) "パスワードの設定" else "パスワードの確認"
                            "change" -> when(step) {
                                0 -> "現在のパスワード"
                                1 -> "新しいパスワード"
                                else -> "パスワードの確認"
                            }
                            else -> "ロックの解除"
                        }
                        
                        Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary)
                        
                        Spacer(Modifier.height(12.dp))
                        
                        val description = when {
                            isDisableMode -> "ロックを解除するには現在のパスワードを入力してください"
                            isChangeMode -> when(step) {
                                0 -> "現在のパスワードを入力してください"
                                1 -> "新しいパスワード（4〜12桁）を入力してください"
                                else -> "もう一度入力してください"
                            }
                            else -> if (step == 0) "パスワード（4〜12桁）を入力してください" else "もう一度入力してください"
                        }
                        Text(description, fontSize = 13.sp, color = NotionTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        
                        Spacer(Modifier.height(32.dp))
                        
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
                            // パスワードの入力インジケーターをリッチに
                            repeat(12) { index ->
                                val isActive = index < currentInputText.length
                                Box(
                                    modifier = Modifier
                                        .size(if (isActive) 14.dp else 12.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) NotionSafeGreen else NotionBorder)
                                        .then(if (isActive) Modifier.border(2.dp, NotionSafeGreen.copy(alpha = 0.2f), CircleShape) else Modifier)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        
                        Spacer(Modifier.height(40.dp))
                        
                        PinKeypad(
                            onNumberClick = { num ->
                                when(step) {
                                    0 -> if (currentPasswordInput.length < 12) currentPasswordInput += num
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
                                        if (appSettings?.appLockPassword != null && currentPasswordInput == appSettings.appLockPassword) {
                                            scope.launch {
                                                dao.upsertAppSettings(appSettings.copy(isAppLockEnabled = false, isBiometricEnabled = false))
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
                                                if (currentPasswordInput == appSettings?.appLockPassword) step = 1
                                                else {
                                                    scope.launch { snackbarHostState.showSnackbar("現在のパスワードが正しくありません") }
                                                    currentPasswordInput = ""
                                                }
                                            }
                                            1 -> step = 2
                                            2 -> {
                                                if (newPasswordInput == confirmPasswordInput) {
                                                    scope.launch {
                                                        val currentSettings = appSettings ?: AppSettingsEntity()
                                                        dao.upsertAppSettings(currentSettings.copy(appLockPassword = newPasswordInput, isAppLockEnabled = true))
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
                                    else -> {
                                        if (step == 0) {
                                            if (currentPasswordInput.length >= 4) {
                                                newPasswordInput = currentPasswordInput
                                                step = 2
                                            }
                                        } else {
                                            if (newPasswordInput == confirmPasswordInput) {
                                                scope.launch {
                                                    val currentSettings = appSettings ?: AppSettingsEntity()
                                                    dao.upsertAppSettings(currentSettings.copy(appLockPassword = newPasswordInput, isAppLockEnabled = true))
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
                                0 -> currentPasswordInput.length >= 4
                                1 -> newPasswordInput.length >= 4
                                else -> confirmPasswordInput.length >= 4
                            },
                            confirmLabel = if (isDisableMode) "解除" else if (isChangeMode && step < 2 || !isChangeMode && step == 0) "次へ" else "確定"
                        )
                    }
                }
            }

            if (showBackupPasswordDialog) {
                val context = LocalContext.current
                val autofillManager = remember { context.getSystemService(android.view.autofill.AutofillManager::class.java) }
                var passwordText by remember { mutableStateOf("") }
                var confirmPasswordText by remember { mutableStateOf("") }
                var isPasswordVisible by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { showBackupPasswordDialog = false; backupPassword = "" },
                    title = { Text(if (backupMode == "export") "バックアップ用パスワード" else "復号パスワード入力") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (backupMode == "export") {
                                Text("復旧時に必要なパスワードを設定してください。忘れると復元できません。", fontSize = 12.sp, color = NotionTextSecondary)
                            } else {
                                Text("バックアップ時に設定したパスワードを入力してください", fontSize = 12.sp, color = NotionTextSecondary)
                            }
                            OutlinedTextField(
                                value = passwordText,
                                onValueChange = { passwordText = it },
                                visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = NotionTextSecondary)
                                    }
                                },
                                singleLine = true,
                                label = { Text("パスワード") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = if (backupMode == "export") androidx.compose.ui.text.input.ImeAction.Next else androidx.compose.ui.text.input.ImeAction.Done),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (backupMode == "export") {
                                OutlinedTextField(
                                    value = confirmPasswordText,
                                    onValueChange = { confirmPasswordText = it },
                                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    singleLine = true,
                                    label = { Text("パスワード（確認）") },
                                    isError = confirmPasswordText.isNotEmpty() && passwordText != confirmPasswordText,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    TextButton(onClick = { 
                                        val p = generateSecurePassword()
                                        passwordText = p
                                        confirmPasswordText = p
                                        isPasswordVisible = true
                                    }, colors = ButtonDefaults.textButtonColors(contentColor = NotionSafeGreen)) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("安全なパスワードを生成")
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        val isEnabled = if (backupMode == "export") {
                            passwordText.length >= 8 && passwordText == confirmPasswordText
                        } else {
                            passwordText.length >= 8
                        }
                        TextButton(
                            enabled = isEnabled,
                            onClick = {
                                if (backupMode != "export") autofillManager?.commit()
                                backupPassword = passwordText
                                showBackupPasswordDialog = false
                                if (backupMode == "export") {
                                    exportLauncher.launch("nozokima-${SimpleDateFormat("yyyyMMdd", Locale.JAPAN).format(Date())}.zip")
                                } else {
                                    scope.launch {
                                        try {
                                            val uri = pendingImportUri ?: return@launch
                                            contentResolver.openInputStream(uri)?.use { input ->
                                                BackupManager(dao, applicationContext).importData(input, backupPassword)
                                                snackbarHostState.showSnackbar("データをインポートしました")
                                            }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("インポートに失敗しました: ${e.message}")
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
                if (isLoading) {
                    // 読み込み中（一瞬のチラつき防止）
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // 必要に応じてロゴなどを配置
                    }
                } else if (showLockScreen) {
                    AppLockScreen(
                        correctPassword = appSettings.appLockPassword ?: "",
                        onUnlock = { isAppLocked = false },
                        failedAttempts = appSettings.failedAttempts,
                        lockoutUntil = appSettings.lockoutUntil,
                        onFailedAttempt = { attempts, until ->
                            scope.launch {
                                dao.upsertAppSettings(appSettings.copy(failedAttempts = attempts, lockoutUntil = until))
                            }
                        },
                        onSuccessfulUnlock = {
                            scope.launch {
                                dao.upsertAppSettings(appSettings.copy(failedAttempts = 0, lockoutUntil = 0L))
                            }
                        },
                        isBiometricEnabled = appSettings.isBiometricEnabled,
                        onBiometricClick = {
                            showBiometricPrompt(
                                onSuccess = {
                                    scope.launch {
                                        dao.upsertAppSettings(appSettings.copy(failedAttempts = 0, lockoutUntil = 0L))
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
                                    chatSessions = mainUiState.chatSessions,
                                    currentSessionId = currentChatSessionId,
                                    onSessionSelected = { currentChatSessionId = it },
                                    onDeleteSession = { sessionId ->
                                        scope.launch {
                                            dao.deleteChatSession(sessionId)
                                            dao.deleteMessagesForSession(sessionId)
                                            if (currentChatSessionId == sessionId) currentChatSessionId = null
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
                                bottomBar = {
                                    if (!isKeyboardVisible && selectedTab <= 4) {
                                        Spacer(modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp))
                                    }
                                }
                            ) { innerPadding ->
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AnimatedContent(
                                        targetState = selectedTab,
                                        transitionSpec = {
                                            if (targetState > initialState) {
                                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                            } else {
                                                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                                            }.using(SizeTransform(clip = false))
                                        },
                                        label = "MainTabTransition"
                                    ) { targetTab ->
                                        when (targetTab) {
                                            0 -> Box(Modifier.padding(innerPadding)) {
                                                HomeScreen(
                                                    viewModel = homeViewModel,
                                                    dao = dao,
                                                    onConsultClick = { tx ->
                                                        consultingTransaction = tx
                                                        selectedTab = 3
                                                    },
                                                    onAiAdviceClick = { advice ->
                                                        initialHomeAdviceText = advice
                                                        selectedTab = 3
                                                    },
                                                    onCategoryClick = { category ->
                                                        initialAssetCategoryFilter = category
                                                        selectedTab = 2
                                                    },
                                                    onGoalClick = { selectedTab = 6 }
                                                )
                                            }
                                            1 -> Box(Modifier.padding(innerPadding)) {
                                                InputScreen(
                                                    dao = dao, gemini = gemini, ocrManager = ocrManager,
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
                                                    dao = dao, gemini = gemini,
                                                    assets = homeViewModel.uiState.collectAsState().value.assets,
                                                    lendings = homeViewModel.uiState.collectAsState().value.lendings,
                                                    transactions = homeViewModel.uiState.collectAsState().value.transactions,
                                                    chatSessions = mainUiState.chatSessions,
                                                    drawerState = drawerState,
                                                    currentSessionId = currentChatSessionId,
                                                    onSessionSelected = { currentChatSessionId = it },
                                                    initialTransaction = consultingTransaction,
                                                    onClearConsultation = { consultingTransaction = null },
                                                    initialHomeAdviceText = initialHomeAdviceText,
                                                    onClearHomeAdvice = { initialHomeAdviceText = null },
                                                    modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
                                                )
                                            }
                                            4 -> Box(Modifier.padding(innerPadding)) {
                                                GeneralSettingsScreen(
                                                    appLockEnabled = appSettings.isAppLockEnabled,
                                                    onToggleAppLock = { enabled ->
                                                        appLockDialogMode = if (enabled) "set" else "disable"
                                                        showAppLockPasswordDialog = true
                                                    },
                                                    biometricEnabled = appSettings.isBiometricEnabled,
                                                    onToggleBiometric = { enabled ->
                                                        scope.launch {
                                                            dao.upsertAppSettings(appSettings.copy(isBiometricEnabled = enabled))
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
                                                    onImportClick = { importLauncher.launch(arrayOf("*/*")) },
                                                    onCategoryManagementClick = { selectedTab = 5 },
                                                    onBack = { selectedTab = 0 }
                                                )
                                            }
                                            5 -> Box(Modifier.padding(innerPadding)) {
                                                CategoryManagementScreen(dao = dao, onBack = { selectedTab = 4 })
                                            }
                                            6 -> Box(Modifier.padding(innerPadding)) {
                                                GoalSettingContent(
                                                    dao = dao,
                                                    aiStatus = homeUiState.aiStatus,
                                                    aiIsReady = homeUiState.isAiReady,
                                                    aiIsGenerating = homeUiState.isAiGenerating,
                                                    aiIsChecking = homeUiState.isAiCheckingStatus,
                                                    aiIsInitialized = homeUiState.isAiInitialized,
                                                    goalAiText = homeUiState.goalAiText,
                                                    onRefreshAi = { homeViewModel.triggerGoalAnalysis() },
                                                    isKeypadVisible = isGoalKeypadVisible,
                                                    onKeypadVisibilityChange = { isGoalKeypadVisible = it },
                                                    onBack = { selectedTab = 0 }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))

                            if (!isKeyboardVisible && !isGoalKeypadVisible && selectedTab <= 4) {
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
