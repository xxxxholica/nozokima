package com.example.nozokima.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.data.local.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.components.CustomKeypad
import com.example.nozokima.ui.components.ScreenHeader
import com.example.nozokima.util.evaluateExpression
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.launch
import ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSettingContent(
    dao: FinanceDao,
    aiStatus: Int,
    aiIsReady: Boolean,
    aiIsGenerating: Boolean,
    aiIsChecking: Boolean,
    aiIsInitialized: Boolean = false,
    goalAiText: String = "",
    onRefreshAi: () -> Unit = {},
    onAiAdviceClick: (String) -> Unit = {},
    isKeypadVisible: Boolean = false,
    onKeypadVisibilityChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {}
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
    var useVirtualBalance by rememberSaveable { mutableStateOf(false) }
    var isSimulationView by rememberSaveable { mutableStateOf(false) }
    var goalKeypadTarget by remember { mutableStateOf("amount") } // "amount" or "income"

    val isAiWaiting = (!aiIsInitialized || aiIsChecking) && goalAiText.isEmpty()
    val isAiGeneratingNow = aiIsGenerating && goalAiText.isEmpty()
    val showAiProgress = isAiWaiting || isAiGeneratingNow
    val aiStatusLabel = when {
        isAiWaiting && aiIsInitialized -> "AI を確認中..."
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
            useVirtualBalance = g.useVirtualBalance
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
                    startDateMillis = startDateMillis,
                    useVirtualBalance = useVirtualBalance
                )
            )
        }
    }

    // 計算
    val upcomingTotal = (lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount } + 
                        (dao.getAllScheduledExpenses().collectAsState(initial = emptyList()).value.asSequence().filter { !it.isCompleted }.sumOf { it.amount })).toLong()
    // DAOからすべてのデータを取得するのは重いので、本来はViewModelで行うべきだが、既存コードに合わせる
    val virtualBalanceForGoal = actualTotalAssets - upcomingTotal // 簡易的に貸付を引く

    val currentAssetsForCalc = if (useVirtualBalance) virtualBalanceForGoal else actualTotalAssets

    val remainingDays = ((targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
    val targetAmount = targetAmountText.toLongOrNull() ?: 0L
    val monthlyIncome = monthlyIncomeText.toLongOrNull() ?: 0L
    val totalExpectedIncome = (monthlyIncome * remainingMonths).toLong()
    val totalSpendable = (currentAssetsForCalc + totalExpectedIncome - targetAmount).coerceAtLeast(0L)
    val monthlyBudget = if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN) }

    val goalAchieved = currentAssetsForCalc >= targetAmount && targetAmount > 0L && showResults
    val isAmountTooLow = targetAmount > 0L && targetAmount <= currentAssetsForCalc
    val currentStep = when {
        goalAchieved -> 3
        showResults -> 2
        isSimulationView -> 1
        else -> 0
    }
    val canStart = targetAmount > actualTotalAssets && monthlyIncomeText.isNotEmpty()

    // 自動トリガー
    LaunchedEffect(aiIsReady, currentStep) {
        if (aiIsReady && (currentStep == 1 || currentStep == 2 || currentStep == 3) && !aiIsGenerating) {
            onRefreshAi()
        }
    }

    BackHandler(enabled = isKeypadVisible) { onKeypadVisibilityChange(false) }
    BackHandler(enabled = !isKeypadVisible) { onBack() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            val pageTitle = when (currentStep) {
                3 -> "目標達成"
                2 -> "目標経過"
                1 -> "シミュレーション結果"
                else -> "目標定義"
            }
            ScreenHeader(
                title = pageTitle,
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
            // 固定ヘッダー
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                GoalStepper(currentStep = currentStep)
            }

            // スクロール可能なコンテンツエリア
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 
                            AnimatedContentTransitionScope.SlideDirection.Left 
                        else 
                            AnimatedContentTransitionScope.SlideDirection.Right
                        
                        slideIntoContainer(direction) + fadeIn() togetherWith
                        slideOutOfContainer(direction) + fadeOut()
                    },
                    label = "GoalContentTransition"
                ) { targetStep ->
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Spacer(Modifier.height(12.dp))
                            when (targetStep) {
                                3 -> {
                                    GoalAchievedView(
                                        actualTotalAssets = currentAssetsForCalc,
                                        targetAmount = targetAmount,
                                        totalSpendable = totalSpendable,
                                        monthlyBudget = monthlyBudget,
                                        startDateMillis = startDateMillis,
                                        targetDateMillis = targetDateMillis,
                                        remainingDays = remainingDays,
                                        aiStatus = aiStatus,
                                        aiIsGenerating = aiIsGenerating,
                                        showAiProgress = showAiProgress,
                                        aiStatusLabel = aiStatusLabel,
                                        goalAiText = goalAiText,
                                        onRefreshAi = onRefreshAi,
                                        onAiAdviceClick = onAiAdviceClick,
                                        isAiWaiting = isAiWaiting
                                    )
                                }
                                2 -> {
                                    GoalProgressView(
                                        actualTotalAssets = currentAssetsForCalc,
                                        targetAmount = targetAmount,
                                        totalSpendable = totalSpendable,
                                        monthlyBudget = monthlyBudget,
                                        startDateMillis = startDateMillis,
                                        targetDateMillis = targetDateMillis,
                                        remainingDays = remainingDays,
                                        aiStatus = aiStatus,
                                        aiIsGenerating = aiIsGenerating,
                                        showAiProgress = showAiProgress,
                                        aiStatusLabel = aiStatusLabel,
                                        goalAiText = goalAiText,
                                        onRefreshAi = onRefreshAi,
                                        onAiAdviceClick = onAiAdviceClick,
                                        isAiWaiting = isAiWaiting
                                    )
                                }
                                1 -> {
                                    GoalSimulationView(
                                        actualTotalAssets = currentAssetsForCalc,
                                        totalSpendable = totalSpendable,
                                        monthlyBudget = monthlyBudget
                                    )
                                }
                                else -> {
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
                                        isAmountTooLow = isAmountTooLow,
                                        useVirtualBalance = useVirtualBalance,
                                        onUseVirtualBalanceChange = { useVirtualBalance = it; saveGoal() }
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }

            // 下部固定ボタンエリア
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                when (currentStep) {
                    3 -> {
                        Button(
                            onClick = {
                                titleText = ""
                                targetAmountText = ""
                                monthlyIncomeText = ""
                                targetDateMillis = Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.timeInMillis
                                startDateMillis = System.currentTimeMillis()
                                showResults = false
                                isSimulationView = false
                                saveGoal()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen)
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("次の目標を設定する", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    2 -> {
                        OutlinedButton(
                            onClick = {
                                showResults = false
                                isSimulationView = true
                                saveGoal()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("設定を変更する", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    1 -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { isSimulationView = false },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("戻る", color = MaterialTheme.colorScheme.onSurface)
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        showResults = true
                                        if (startDateMillis == 0L || (goalSetting?.startDateMillis ?: 0L) == 0L) {
                                            startDateMillis = System.currentTimeMillis()
                                        }
                                        saveGoal()
                                    }
                                },
                                modifier = Modifier.weight(2f).height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("貯蓄を開始する", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = { isSimulationView = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = canStart,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen, disabledContainerColor = MaterialTheme.colorScheme.outline)
                        ) {
                            Text("試算する", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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

        if (isKeypadVisible) {
            ModalBottomSheet(
                onDismissRequest = { saveGoal(); onKeypadVisibilityChange(false) },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
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
                        if (goalKeypadTarget == "amount") targetAmountText = "" else monthlyIncomeText = ""
                    },
                    onConfirmClick = {
                        if (goalKeypadTarget == "amount") {
                            try { targetAmountText = evaluateExpression(targetAmountText).toString() } catch (_: Exception) {}
                        } else {
                            try { monthlyIncomeText = evaluateExpression(monthlyIncomeText).toString() } catch (e: Exception) {}
                        }
                        saveGoal()
                    },
                    onSaveClick = {
                        if (goalKeypadTarget == "amount") {
                            if (targetAmountText.any { it in "+-*/" }) {
                                try { targetAmountText = evaluateExpression(targetAmountText).toString() } catch (_: Exception) {}
                            } else onKeypadVisibilityChange(false)
                        } else {
                            if (monthlyIncomeText.any { it in "+-*/" }) {
                                try { monthlyIncomeText = evaluateExpression(monthlyIncomeText).toString() } catch (e: Exception) {}
                            } else onKeypadVisibilityChange(false)
                        }
                        saveGoal()
                    },
                    onCloseClick = { saveGoal(); onKeypadVisibilityChange(false) },
                    isSaveEnabled = true,
                    actionColor = NotionSafeGreen
                )
            }
        }
    }
}

@Composable
fun GoalAchievedView(
    actualTotalAssets: Long,
    targetAmount: Long,
    totalSpendable: Long,
    monthlyBudget: Long,
    startDateMillis: Long,
    targetDateMillis: Long,
    remainingDays: Int,
    aiStatus: Int,
    aiIsGenerating: Boolean,
    showAiProgress: Boolean,
    aiStatusLabel: String?,
    goalAiText: String,
    onRefreshAi: () -> Unit,
    onAiAdviceClick: (String) -> Unit,
    isAiWaiting: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = if (isSystemInDarkTheme()) Color(0xFF2D2400) else Color(0xFFFFF9E6),
            border = BorderStroke(2.dp, Color(0xFFFFD54F))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp)
            ) {
                Text("🎉 Congratulations! 🎉", color = Color(0xFFFFD54F), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text("目標を達成しました！", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        GoalProgressView(
            actualTotalAssets = actualTotalAssets,
            targetAmount = targetAmount,
            totalSpendable = totalSpendable,
            monthlyBudget = monthlyBudget,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            remainingDays = remainingDays,
            aiStatus = aiStatus,
            aiIsGenerating = aiIsGenerating,
            showAiProgress = showAiProgress,
            aiStatusLabel = aiStatusLabel,
            goalAiText = goalAiText,
            onRefreshAi = onRefreshAi,
            onAiAdviceClick = onAiAdviceClick,
            showSimulationTiles = false,
            isAiWaiting = isAiWaiting
        )
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
    isAmountTooLow: Boolean,
    useVirtualBalance: Boolean,
    onUseVirtualBalanceChange: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SetupTile(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Flag,
            label = "目標タイトル",
            color = NotionSafeGreen
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = titleText,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                decorationBox = { inner ->
                    if (titleText.isEmpty()) Text("旅行など", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 16.sp)
                    inner()
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() })
            )
        }
        SetupTile(
            modifier = Modifier.fillMaxWidth().clickable { onAmountClick() },
            icon = Icons.Default.Star,
            label = "目標貯金額",
            color = if (isAmountTooLow) Color(0xFFE57373) else NotionSafeGreen
        ) {
            Text(
                text = if (targetAmountText.isEmpty()) "¥ 0" else "¥ ${String.format(Locale.JAPAN, "%,d", targetAmountText.toLongOrNull() ?: 0L)}",
                color = if (isAmountTooLow) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(36.dp), shape = RoundedCornerShape(10.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = NotionSafeGreen, modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("計算に使用する資産", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (useVirtualBalance) "自由に使える残高" else "実資産（全て）", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = useVirtualBalance,
                            onCheckedChange = onUseVirtualBalanceChange,
                            colors = SwitchDefaults.colors(checkedThumbColor = NotionSafeGreen, checkedTrackColor = NotionSafeGreen.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }

        SetupTile(
            modifier = Modifier.fillMaxWidth().clickable { onIncomeClick() },
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            label = "平均月収",
            color = NotionSafeGreen
        ) {
            Text(
                text = if (monthlyIncomeText.isEmpty()) "¥ 0" else "¥ ${String.format(Locale.JAPAN, "%,d", monthlyIncomeText.toLongOrNull() ?: 0L)}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
        }
        SetupTile(
            modifier = Modifier.fillMaxWidth().clickable { onDateClick() },
            icon = Icons.Default.CalendarMonth,
            label = "目標達成日",
            color = NotionSafeGreen
        ) {
            Text(
                text = dateFormatter.format(Date(targetDateMillis)),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun SetupTile(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(36.dp), shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.08f)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                content()
            }
        }
    }
}

@Composable
fun GoalSimulationView(
    actualTotalAssets: Long,
    totalSpendable: Long,
    monthlyBudget: Long
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SimulationTile(icon = Icons.Default.AccountBalance, label = "現在の保有資産", value = actualTotalAssets, color = Color(0xFFFFB74D))
        SimulationTile(icon = Icons.Default.Wallet, label = "合計の許容支出", value = totalSpendable, color = Color(0xFF2196F3))
        SimulationTile(icon = Icons.Default.CalendarMonth, label = "月の予算", value = monthlyBudget, color = Color(0xFF2196F3))
    }
}

@Composable
fun SimulationTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: Long, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(36.dp), shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.08f)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Text("¥ ${String.format(Locale.JAPAN, "%,d", value)}", color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun GoalProgressView(
    actualTotalAssets: Long,
    targetAmount: Long,
    totalSpendable: Long,
    monthlyBudget: Long,
    startDateMillis: Long,
    targetDateMillis: Long,
    remainingDays: Int,
    aiStatus: Int,
    aiIsGenerating: Boolean,
    showAiProgress: Boolean,
    aiStatusLabel: String?,
    goalAiText: String,
    onRefreshAi: () -> Unit,
    onAiAdviceClick: (String) -> Unit,
    showSimulationTiles: Boolean = true,
    isAiWaiting: Boolean = false
) {
    val progressRatio = if (targetAmount > 0) (actualTotalAssets.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f) else 0f
    val progressPercent = (progressRatio * 100).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("現在の達成率", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("$progressPercent%", fontSize = 32.sp, color = NotionSafeGreen, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progressRatio },
                    modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)),
                    color = NotionSafeGreen,
                    trackColor = MaterialTheme.colorScheme.outline,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PriceProgressTile(
                actual = actualTotalAssets,
                target = targetAmount,
                modifier = Modifier.weight(1.2f)
            )
            ProgressTile(
                icon = Icons.Default.Timer,
                label = "期限まで",
                extra = if (remainingDays > 0) "$remainingDays 日" else "本日",
                ratio = 0f,
                color = NotionTextPrimary,
                modifier = Modifier.weight(0.8f),
                showBar = false
            )
        }

        if (showSimulationTiles) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("1ヶ月の予算", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥ ${String.format(Locale.JAPAN, "%,d", monthlyBudget)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NotionSafeGreen)
                    }
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline).align(Alignment.CenterVertically))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("合計許容支出", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥ ${String.format(Locale.JAPAN, "%,d", totalSpendable)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        AiAdvisorCard(
            aiStatus = aiStatus,
            aiIsGenerating = aiIsGenerating,
            showAiProgress = showAiProgress,
            aiStatusLabel = aiStatusLabel,
            isAiWaiting = isAiWaiting,
            goalAiText = goalAiText,
            onRefreshAi = onRefreshAi,
            onAiAdviceClick = onAiAdviceClick
        )
    }
}

@Composable
fun PriceProgressTile(
    actual: Long,
    target: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2196F3).copy(alpha = 0.08f)
                ) {
                    Icon(Icons.Default.AccountBalance, null, tint = Color(0xFF2196F3), modifier = Modifier.padding(6.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("貯蓄状況", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "¥ ${String.format(Locale.JAPAN, "%,d", actual)}",
                    color = Color(0xFF2196F3),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "/ ¥ ${String.format(Locale.JAPAN, "%,d", target)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}



@Composable
fun ProgressTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    percent: Int? = null,
    extra: String? = null,
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
    showBar: Boolean = true
) {
    Surface(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.08f)
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.padding(6.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                if (percent != null) {
                    Text("$percent", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("%", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp, start = 1.dp))
                }
                if (extra != null) {
                    Text(extra, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }
            
            if (showBar) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.outline,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}



@Composable
fun CompactSimulationTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.08f)
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.padding(6.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "¥ ${String.format(Locale.JAPAN, "%,d", value)}",
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

@Composable
fun AiAdvisorCard(
    aiStatus: Int,
    aiIsGenerating: Boolean,
    showAiProgress: Boolean,
    aiStatusLabel: String?,
    isAiWaiting: Boolean,
    goalAiText: String,
    onRefreshAi: () -> Unit,
    onAiAdviceClick: (String) -> Unit,
    defaultText: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        contentAlignment = if (isAiWaiting) Alignment.Center else Alignment.TopStart
    ) {
        if (isAiWaiting) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = NotionSafeGreen,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("AIの状態を確認中...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val isUnavailable = aiStatus == FeatureStatus.UNAVAILABLE
                    val themeColor = if (isUnavailable) Color(0xFFE57373) else NotionSafeGreen
                    Box(
                        modifier = Modifier.size(28.dp).background(themeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isUnavailable) Icons.Default.Info else Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = themeColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(text = if (isUnavailable) "覗き魔AI は利用できません" else "覗き魔 AI", color = themeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (!isUnavailable) {
                        val hasText = goalAiText.isNotEmpty()
                        
                        // 再生成ボタン (アイコンのみ)
                        Surface(
                            onClick = onRefreshAi,
                            enabled = !aiIsGenerating,
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (aiIsGenerating) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else themeColor.copy(alpha = 0.1f),
                            contentColor = if (aiIsGenerating) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else themeColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // 詳しく聞くボタン
                        Surface(
                            onClick = { onAiAdviceClick(goalAiText) },
                            enabled = !aiIsGenerating && hasText,
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (aiIsGenerating || !hasText) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else themeColor.copy(alpha = 0.1f),
                            contentColor = if (aiIsGenerating || !hasText) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else themeColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("詳しく聞く", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (showAiProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = NotionSafeGreen,
                        trackColor = NotionSafeGreen.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (aiStatusLabel != null) {
                    Text(aiStatusLabel, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                } else if (aiStatus == FeatureStatus.UNAVAILABLE) {
                    val uriHandler = LocalUriHandler.current
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Gemini Nanoに対応したデバイスのみ利用可能です。\n対応デバイスは下記リンクをご覧ください。", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, lineHeight = 18.sp)
                        Text(text = "https://developers.google.com/ml-kit/genai?hl=ja", color = Color(0xFF1976D2), fontSize = 12.sp, modifier = Modifier.clickable { uriHandler.openUri("https://developers.google.com/ml-kit/genai?hl=ja") })
                    }
                } else if (goalAiText.isNotEmpty()) {
                    Text(goalAiText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                } else if (defaultText != null) {
                    Text(defaultText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun GoalStepper(currentStep: Int) {
    val steps = listOf("設定", "試算", "経過", "達成")
    val stepColors = listOf(NotionSafeGreen, Color(0xFF9C27B0), Color(0xFF2196F3), Color(0xFFFFB74D))

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, label ->
            val isActive = index == currentStep
            val isDone = index < currentStep
            val color = if (isActive || isDone) stepColors[index] else MaterialTheme.colorScheme.outline
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(20.dp).background(color.copy(alpha = if (isActive) 0.15f else 0.05f), CircleShape).border(1.5.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(10.dp))
                    else Text("${index + 1}", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                Text(label, color = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
            }
            if (index < steps.size - 1) {
                Box(modifier = Modifier.weight(1f).height(1.dp).padding(horizontal = 6.dp).background(if (index < currentStep) stepColors[index] else MaterialTheme.colorScheme.outline))
            }
        }
    }
}


