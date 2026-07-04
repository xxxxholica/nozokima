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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nozokima.ui.components.*
import com.example.nozokima.ui.screens.*
import com.example.nozokima.util.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.viewmodel.MainViewModel
import com.example.nozokima.ui.viewmodel.HomeViewModel
import com.example.nozokima.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch
import ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.IntOffset

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
            val dao = remember { db.financeDao() }
            val factory = remember { ViewModelFactory(dao, gemini) }
            val mainViewModel: MainViewModel = viewModel(factory = factory)
            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            
            val mainUiState by mainViewModel.uiState.collectAsState()

            val appSettings = mainUiState.appSettings ?: AppSettingsEntity()
            val themeMode = appSettings.themeMode

            NozokimaTheme(themeMode = themeMode) {
                val view = androidx.compose.ui.platform.LocalView.current
                val isDark = isSystemInDarkTheme()
                val currentThemeMode = themeMode
                
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as android.app.Activity).window
                        val isAppearanceLight: Boolean = when (currentThemeMode) {
                            "LIGHT" -> true
                            "DARK" -> false
                            else -> !isDark
                        }
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isAppearanceLight
                    }
                }

                val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

            var selectedTab by remember { mutableIntStateOf(0) }
            var initialHomeAdviceText by remember { mutableStateOf<String?>(null) }
            var recoveryLending by remember { mutableStateOf<LendingEntity?>(null) }
            var initialInputMode by remember { mutableStateOf<String?>(null) }
            var initialHistoryMode by remember { mutableStateOf(value = false) }
            var initialScheduledMode by remember { mutableStateOf(value = false) }

            var backupPassword by remember { mutableStateOf("") }
            var showBackupPasswordDialog by remember { mutableStateOf(value = false) }
            var backupMode by remember { mutableStateOf("") }
            var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var showAppLockPasswordDialog by remember { mutableStateOf(value = false) }
            var appLockDialogMode by remember { mutableStateOf("set") }

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            val isAppLocked = rememberSaveable { mutableStateOf(value = true) }
            val isExternalActivityLaunching = rememberSaveable { mutableStateOf(false) }
            val appSettings = mainUiState.appSettings ?: AppSettingsEntity()
            val isLoading = !mainUiState.isLoaded
            val isSetupCompleted = appSettings.isSetupCompleted
            val showLockScreen = isAppLocked.value && appSettings.isAppLockEnabled
            val lifecycle = LocalLifecycleOwner.current.lifecycle

            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        if (!isExternalActivityLaunching.value) isAppLocked.value = true
                    } else if (event == Lifecycle.Event.ON_RESUME) {
                        isExternalActivityLaunching.value = false
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

            LaunchedEffect(Unit) {
                mainViewModel.checkAiStatus()
            }

            val context = LocalContext.current
            val activity = remember(context) { context as? android.app.Activity }
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
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
                        
                        Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        
                        Spacer(Modifier.height(12.dp))
                        
                        val description = when {
                            isDisableMode -> "ロックを解除するには現在のパスワードを入力してください"
                            isChangeMode -> when(step) {
                                0 -> "現在のパスワードを入力してください"
                                1 -> "新しいパスワードを入力してください"
                                else -> "もう一度入力してください"
                            }
                            else -> if (step == 0) "パスワードを入力してください" else "もう一度入力してください"
                        }
                        Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        
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
                            repeat(4) { index ->
                                val isActive = index < currentInputText.length
                                Box(
                                    modifier = Modifier
                                        .size(if (isActive) 16.dp else 14.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) NotionSafeGreen else MaterialTheme.colorScheme.outline)
                                        .then(if (isActive) Modifier.border(2.dp, NotionSafeGreen.copy(alpha = 0.2f), CircleShape) else Modifier)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        
                        Spacer(Modifier.height(40.dp))
                        
                        PinKeypad(
                            onNumberClick = { num ->
                                when(step) {
                                    0 -> if (currentPasswordInput.length < 4) currentPasswordInput += num
                                    1 -> if (newPasswordInput.length < 4) newPasswordInput += num
                                    2 -> if (confirmPasswordInput.length < 4) confirmPasswordInput += num
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
                                        if ((appSettings.appLockPassword != null) && (currentPasswordInput == appSettings.appLockPassword)) {
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
                                                if (currentPasswordInput == appSettings.appLockPassword) step = 1
                                                else {
                                                    scope.launch { snackbarHostState.showSnackbar("現在のパスワードが正しくありません") }
                                                    currentPasswordInput = ""
                                                }
                                            }
                                            1 -> step = 2
                                            2 -> {
                                                if (newPasswordInput == confirmPasswordInput) {
                                                    scope.launch {
                                                        dao.upsertAppSettings(appSettings.copy(appLockPassword = newPasswordInput, isAppLockEnabled = true))
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
                                                    dao.upsertAppSettings(appSettings.copy(appLockPassword = newPasswordInput, isAppLockEnabled = true, isBiometricEnabled = true))
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
                    containerColor = Color.White,
                    title = { Text("パスワード設定", color = Color.Black) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (backupMode == "export") {
                                Text("復旧時に必要なパスワードを設定してください。忘れると復元できません。", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                Text("バックアップ時に設定したパスワードを入力してください", fontSize = 12.sp, color = Color.Gray)
                            }
                            OutlinedTextField(
                                value = passwordText,
                                onValueChange = { passwordText = it },
                                visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Color.Gray)
                                    }
                                },
                                singleLine = true,
                                label = { Text("パスワード") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NotionSafeGreen,
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedLabelColor = NotionSafeGreen,
                                    unfocusedLabelColor = Color.Gray,
                                    cursorColor = NotionSafeGreen,
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black
                                ),
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
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NotionSafeGreen,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedLabelColor = NotionSafeGreen,
                                        unfocusedLabelColor = Color.Gray,
                                        cursorColor = NotionSafeGreen,
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        errorBorderColor = Color.Red,
                                        errorLabelColor = Color.Red
                                    ),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    TextButton(
                                        onClick = { 
                                            val p = generateSecurePassword()
                                            passwordText = p
                                            confirmPasswordText = p
                                            isPasswordVisible = true
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = NotionSafeGreen)
                                    ) {
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

            androidx.activity.compose.BackHandler(enabled = selectedTab != 0) {
                selectedTab = when (selectedTab) {
                    5 -> 0 // Goals -> Home
                    6 -> 4 // CategoryManagement -> Settings
                    else -> 0
                }
            }

            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                    },
                color = MaterialTheme.colorScheme.background
            ) {
                if (isLoading) {
                    // 読み込み中（一瞬のチラつき防止）
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // 必要に応じてロゴなどを配置
                    }
                } else if (!isSetupCompleted) {
                    SetupWizardScreen(dao = dao, onComplete = { /* UI will refresh */ })
                } else if (showLockScreen) {
                    AppLockScreen(
                        correctPassword = appSettings.appLockPassword ?: "",
                        onUnlock = { isAppLocked.value = false },
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
                                    isAppLocked.value = false
                                }
                            )
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
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
                                                                onInputClick = { mode ->
                                                                    initialInputMode = mode
                                                                    selectedTab = 1
                                                                },
                                                                onConsultClick = { 
                                                                    selectedTab = 3 
                                                                },
                                                                onAiAdviceClick = { advice ->
                                                                    initialHomeAdviceText = advice
                                                                    selectedTab = 3
                                                                },
                                                                onAssetsClick = { isScheduled ->
                                                                    initialHistoryMode = isScheduled
                                                                    initialScheduledMode = isScheduled
                                                                    selectedTab = 2 
                                                                },
                                                                onHistoryClick = {
                                                                    initialHistoryMode = true
                                                                    initialScheduledMode = false
                                                                    selectedTab = 2
                                                                },
                                                                onGoalsClick = {
                                                                    selectedTab = 5
                                                                },
                                                                onSettingsClick = { selectedTab = 4 }
                                                            )
                                                        }
                                                        1 -> Box(Modifier.padding(innerPadding)) {
                                                            InputScreen(
                                                                dao = dao, gemini = gemini, ocrManager = ocrManager,
                                                                initialRecovery = recoveryLending,
                                                                initialMode = initialInputMode,
                                                                onRecoveryHandled = { 
                                                                    recoveryLending = null
                                                                    initialInputMode = null
                                                                },
                                                                onExternalActivityLaunch = { isExternalActivityLaunching.value = true },
                                                                onBack = { selectedTab = 0 }
                                                            )
                                                        }
                                                        2 -> Box(Modifier.padding(innerPadding)) {
                                                            AssetsScreen(
                                                                dao = dao,
                                                                initialHistoryMode = initialHistoryMode,
                                                                initialScheduledMode = initialScheduledMode,
                                                                onBack = { 
                                                                    initialHistoryMode = false
                                                                    initialScheduledMode = false
                                                                    selectedTab = 0 
                                                                }
                                                            )
                                                        }
                                                        3 -> {
                                                            val drawerWidth = 280.dp
                                                            val drawerWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { drawerWidth.toPx() }
                                                            val drawerExpanded by animateFloatAsState(
                                                                targetValue = if (drawerState.isOpen) 1f else 0f,
                                                                label = "DrawerAnimation"
                                                            )

                                                            Box(modifier = Modifier.fillMaxSize()) {
                                                                // メインコンテンツ (ConsultationScreen) を左に押し出す
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .offset { IntOffset(x = (-drawerWidthPx * drawerExpanded).toInt(), y = 0) }
                                                                ) {
                                                                    ConsultationScreen(
                                                                        dao = dao, gemini = gemini,
                                                                        ocrManager = ocrManager,
                                                                        assets = homeViewModel.uiState.collectAsState().value.assets,
                                                                        lendings = homeViewModel.uiState.collectAsState().value.lendings,
                                                                        transactions = homeViewModel.uiState.collectAsState().value.transactions,
                                                                        categories = homeViewModel.uiState.collectAsState().value.categories,
                                                                        chatSessions = mainUiState.chatSessions,
                                                                        drawerState = drawerState,
                                                                        currentSessionId = currentChatSessionId,
                                                                        onSessionSelected = { currentChatSessionId = it },
                                                                        initialHomeAdviceText = initialHomeAdviceText,
                                                                        onClearHomeAdvice = { initialHomeAdviceText = null },
                                                                        onBack = { selectedTab = 0 },
                                                                        modifier = Modifier.fillMaxSize()
                                                                    )
                                                                }

                                                                // 右側からスライドインするドロワー
                                                                if (drawerExpanded > 0f) {
                                                                    // 背景の薄暗い部分（スクリム）
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .background(Color.Black.copy(alpha = 0.3f * drawerExpanded))
                                                                            .clickable(
                                                                                interactionSource = remember { MutableInteractionSource() },
                                                                                indication = null
                                                                            ) {
                                                                                scope.launch { drawerState.close() }
                                                                            }
                                                                    )

                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(drawerWidth)
                                                                            .fillMaxHeight()
                                                                            .align(Alignment.CenterEnd)
                                                                            .offset { IntOffset(x = (drawerWidthPx * (1f - drawerExpanded)).toInt(), y = 0) }
                                                                            .background(Color(0xFFF5F5F5))
                                                                    ) {
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
                                                            }
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
                                                                onCategoryManagementClick = { selectedTab = 6 },
                                                                themeMode = themeMode,
                                                                onThemeModeChange = { newMode ->
                                                                    scope.launch {
                                                                        dao.upsertAppSettings(appSettings.copy(themeMode = newMode))
                                                                    }
                                                                },
                                                                onBack = { selectedTab = 0 }
                                                            )
                                                        }
                                                        5 -> Box(Modifier.padding(innerPadding)) {
                                                            GoalsScreen(
                                                                viewModel = homeViewModel,
                                                                dao = dao,
                                                                onBack = { 
                                                                    selectedTab = 0 
                                                                }
                                                            )
                                                        }
                                                        6 -> Box(Modifier.padding(innerPadding)) {
                                                            CategoryManagementScreen(dao = dao, onBack = { selectedTab = 4 })
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                                        }
                                    }
                }
            }
            } // End of NozokimaTheme
        }
    }
}
