package com.example.nozokima.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.StrokeCap
import com.example.nozokima.data.local.FinanceDao
import com.example.nozokima.data.local.entities.GoalSettingEntity
import com.example.nozokima.ui.components.CustomKeypad
import com.example.nozokima.ui.components.ScreenHeader
import com.example.nozokima.ui.viewmodel.HomeViewModel
import com.example.nozokima.util.evaluateExpression
import com.example.nozokima.util.formatAmountWithCommas
import kotlinx.coroutines.launch
import ui.theme.NotionSafeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: HomeViewModel,
    dao: FinanceDao,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val goal = uiState.goalSetting ?: GoalSettingEntity()

    // 現在の資産を計算
    val totalLendingAmount = uiState.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
    val currentAssets = (uiState.assets.sumOf { it.amount } + totalLendingAmount).toLong()
    val upcomingTotal = uiState.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
    val virtualBalance = currentAssets - upcomingTotal

    var step by remember(goal.showResults) { mutableIntStateOf(if (goal.showResults) 2 else 0) }

    var title by remember(goal) { mutableStateOf(goal.title) }
    var targetAmountText by remember(goal) { mutableStateOf(if (goal.targetAmount == 0L) "" else formatAmountWithCommas(goal.targetAmount.toString())) }
    var monthlyIncomeText by remember(goal) { mutableStateOf(if (goal.monthlyIncome == 0L) "" else formatAmountWithCommas(goal.monthlyIncome.toString())) }
    var useVirtualBalance by remember(goal) { mutableStateOf(goal.useVirtualBalance) }

    var isCalculating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = when(step) {
                0 -> "目標設定"
                1 -> "プラン選択"
                else -> "目標詳細"
            },
            navigationIcon = {
                Surface(
                    onClick = {
                        if (step == 1) step = 0
                        else onBack()
                    },
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "GoalsStepTransition",
            modifier = Modifier.weight(1f)
        ) { currentStep ->
            when (currentStep) {
                0 -> GoalInputStep(
                    title = title,
                    onTitleChange = { title = it },
                    targetAmountText = targetAmountText,
                    onTargetAmountTextChange = { targetAmountText = it },
                    monthlyIncomeText = monthlyIncomeText,
                    onMonthlyIncomeTextChange = { monthlyIncomeText = it },
                    useVirtualBalance = useVirtualBalance,
                    onUseVirtualBalanceChange = { useVirtualBalance = it },
                    isCalculating = isCalculating,
                    currentAssets = if (useVirtualBalance) virtualBalance else currentAssets,
                    onCalculate = {
                        isCalculating = true
                        scope.launch {
                            val cleanTarget = targetAmountText.replace(",", "")
                            val cleanIncome = monthlyIncomeText.replace(",", "")
                            val target = cleanTarget.toLongOrNull() ?: 0L
                            val income = cleanIncome.toLongOrNull() ?: 0L
                            val results = viewModel.calculateGoalSimulation(target, income, useVirtualBalance)
                            if (results.isNotEmpty()) {
                                val recommended = results["RECOMMENDED"] ?: ("" to 0L)
                                val relaxed = results["RELAXED"] ?: ("" to 0L)
                                val speed = results["SPEED"] ?: ("" to 0L)
                                dao.upsertGoalSetting(GoalSettingEntity(
                                    id = 1, title = title, targetAmount = target, monthlyIncome = income,
                                    useVirtualBalance = useVirtualBalance, showResults = false,
                                    aiTargetDate = recommended.first, aiMonthlyBudget = recommended.second,
                                    relaxedTargetDate = relaxed.first, relaxedMonthlyBudget = relaxed.second,
                                    speedTargetDate = speed.first, speedMonthlyBudget = speed.second,
                                    selectedPlanType = "RECOMMENDED"
                                ))
                                step = 1
                            }
                            isCalculating = false
                        }
                    }
                )
                1 -> PlanSelectionStep(
                    goal = goal,
                    onConfirmPlan = { type ->
                        scope.launch {
                            dao.upsertGoalSetting(goal.copy(selectedPlanType = type, showResults = true))
                            step = 2
                        }
                    },
                    onBack = { step = 0 }
                )
                else -> GoalDetailStep(
                    viewModel = viewModel,
                    uiState = uiState,
                    goal = goal,
                    onReset = {
                        scope.launch {
                            dao.upsertGoalSetting(GoalSettingEntity(id = 1, showResults = false))
                            step = 0
                        }
                    },
                    onEdit = { step = 0 }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalInputStep(
    title: String, onTitleChange: (String) -> Unit,
    targetAmountText: String, onTargetAmountTextChange: (String) -> Unit,
    monthlyIncomeText: String, onMonthlyIncomeTextChange: (String) -> Unit,
    useVirtualBalance: Boolean, onUseVirtualBalanceChange: (Boolean) -> Unit,
    isCalculating: Boolean, 
    currentAssets: Long,
    onCalculate: () -> Unit
) {
    var showKeypad by remember { mutableStateOf(false) }
    var activeInput by remember { mutableStateOf("none") } // "amount", "income", or "none"
    val focusManager = LocalFocusManager.current

    val cleanTarget = targetAmountText.replace(",", "")
    val target = cleanTarget.toLongOrNull() ?: 0L
    val isTargetInvalid = target > 0 && target <= currentAssets

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 目標の名称
            Surface(
                modifier = Modifier.fillMaxWidth().height(88.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                        Icon(Icons.AutoMirrored.Filled.Label, null, tint = NotionSafeGreen, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("目標の名称", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.text.BasicTextField(
                            value = title,
                            onValueChange = onTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            decorationBox = { inner ->
                                if (title.isEmpty()) Text("旅行", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 16.sp)
                                inner()
                            }
                        )
                    }
                }
            }

            // 目標金額
            Surface(
                onClick = { 
                    focusManager.clearFocus()
                    activeInput = "amount"
                    showKeypad = true 
                },
                modifier = Modifier.fillMaxWidth().height(88.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (activeInput == "amount" && showKeypad) NotionSafeGreen.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (activeInput == "amount" && showKeypad) 2.dp else 1.dp,
                    color = if (activeInput == "amount" && showKeypad) NotionSafeGreen else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = if (isTargetInvalid) Color(0xFFD32F2F).copy(alpha = 0.08f) else NotionSafeGreen.copy(alpha = 0.08f)) {
                        Icon(Icons.Default.Savings, null, tint = if (isTargetInvalid) Color(0xFFD32F2F) else NotionSafeGreen, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("目標金額", fontSize = 12.sp, color = if (isTargetInvalid) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (targetAmountText.isEmpty()) "¥ 0" else "¥ $targetAmountText",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTargetInvalid) Color(0xFFD32F2F) else if (targetAmountText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            if (isTargetInvalid) {
                Text(
                    "現在の資産より大きく設定する必要があります",
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // 月の手取り
            Surface(
                onClick = { 
                    focusManager.clearFocus()
                    activeInput = "income"
                    showKeypad = true
                },
                modifier = Modifier.fillMaxWidth().height(88.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (activeInput == "income" && showKeypad) NotionSafeGreen.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (activeInput == "income" && showKeypad) 2.dp else 1.dp,
                    color = if (activeInput == "income" && showKeypad) NotionSafeGreen else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = NotionSafeGreen.copy(alpha = 0.08f)) {
                        Icon(Icons.Default.Payments, null, tint = NotionSafeGreen, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("月の手取り", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (monthlyIncomeText.isEmpty()) "¥ 0" else "¥ $monthlyIncomeText",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (monthlyIncomeText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = useVirtualBalance, onCheckedChange = onUseVirtualBalanceChange, colors = CheckboxDefaults.colors(checkedColor = NotionSafeGreen))
                Text("支払い予定を差し引いた残高で計算", fontSize = 14.sp)
            }
            
            Spacer(Modifier.height(80.dp)) // ボタン用のスペース
        }

        // 下部の「AIでプランを提案」ボタン
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.9f), MaterialTheme.colorScheme.background),
                        startY = 0f
                    )
                )
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Button(
                onClick = onCalculate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NotionSafeGreen,
                    disabledContainerColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = title.isNotBlank() && targetAmountText.isNotBlank() && monthlyIncomeText.isNotBlank() && !isCalculating && !isTargetInvalid
            ) {
                if (isCalculating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("AIでプランを提案", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        if (showKeypad) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showKeypad = false
                    activeInput = "none"
                },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
                scrimColor = Color.Transparent
            ) {
                CustomKeypad(
                    onNumberClick = { num ->
                        if (activeInput == "amount") {
                            val next = if (targetAmountText == "0") num else targetAmountText + num
                            onTargetAmountTextChange(formatAmountWithCommas(next))
                        } else if (activeInput == "income") {
                            val next = if (monthlyIncomeText == "0") num else monthlyIncomeText + num
                            onMonthlyIncomeTextChange(formatAmountWithCommas(next))
                        }
                    },
                    onOperatorClick = { op ->
                        if (activeInput == "amount") {
                            if (targetAmountText.isNotEmpty() && !targetAmountText.last().toString().matches(Regex("[-+*/.]"))) onTargetAmountTextChange(targetAmountText + op)
                        } else if (activeInput == "income") {
                            if (monthlyIncomeText.isNotEmpty() && !monthlyIncomeText.last().toString().matches(Regex("[-+*/.]"))) onMonthlyIncomeTextChange(monthlyIncomeText + op)
                        }
                    },
                    onDeleteClick = {
                        if (activeInput == "amount") {
                            if (targetAmountText.isNotEmpty()) onTargetAmountTextChange(formatAmountWithCommas(targetAmountText.dropLast(1)))
                        } else if (activeInput == "income") {
                            if (monthlyIncomeText.isNotEmpty()) onMonthlyIncomeTextChange(formatAmountWithCommas(monthlyIncomeText.dropLast(1)))
                        }
                    },
                    onClearAllClick = {
                        if (activeInput == "amount") onTargetAmountTextChange("") else if (activeInput == "income") onMonthlyIncomeTextChange("")
                    },
                    onConfirmClick = {
                        try {
                            if (activeInput == "amount") {
                                val result = evaluateExpression(targetAmountText)
                                onTargetAmountTextChange(formatAmountWithCommas(result.toString()))
                            } else if (activeInput == "income") {
                                val result = evaluateExpression(monthlyIncomeText)
                                onMonthlyIncomeTextChange(formatAmountWithCommas(result.toString()))
                            }
                        } catch (_: Exception) {}
                    },
                    onSaveClick = {
                        // 式が残っている場合は計算して反映
                        if (activeInput == "amount" && targetAmountText.any { it in "+-*/" }) {
                            try {
                                val result = evaluateExpression(targetAmountText)
                                onTargetAmountTextChange(formatAmountWithCommas(result.toString()))
                            } catch (_: Exception) {}
                        } else if (activeInput == "income" && monthlyIncomeText.any { it in "+-*/" }) {
                            try {
                                val result = evaluateExpression(monthlyIncomeText)
                                onMonthlyIncomeTextChange(formatAmountWithCommas(result.toString()))
                            } catch (_: Exception) {}
                        }
                        showKeypad = false
                        activeInput = "none"
                    },
                    onCloseClick = { 
                        showKeypad = false
                        activeInput = "none"
                    },
                    isSaveEnabled = true,
                    actionColor = NotionSafeGreen,
                    saveLabel = "確定"
                )
            }
        }
    }
}

@Composable
fun PlanSelectionStep(goal: GoalSettingEntity, onConfirmPlan: (String) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    
    var tempSelectedPlan by remember { mutableStateOf(goal.selectedPlanType) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("ライフスタイルに合うプランを選んでください", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            PlanCard("RECOMMENDED", "👍 おすすめ", goal.aiTargetDate, goal.aiMonthlyBudget, tempSelectedPlan == "RECOMMENDED") { tempSelectedPlan = "RECOMMENDED" }
            PlanCard("RELAXED", "🐢 ゆったり", goal.relaxedTargetDate, goal.relaxedMonthlyBudget, tempSelectedPlan == "RELAXED") { tempSelectedPlan = "RELAXED" }
            PlanCard("SPEED", "🐇 スピード重視", goal.speedTargetDate, goal.speedMonthlyBudget, tempSelectedPlan == "SPEED") { tempSelectedPlan = "SPEED" }
            
            Spacer(Modifier.height(80.dp))
        }

        // 下部の「このプランで確定」ボタン
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.9f), MaterialTheme.colorScheme.background),
                        startY = 0f
                    )
                )
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Button(
                onClick = { onConfirmPlan(tempSelectedPlan) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NotionSafeGreen
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("このプランで確定", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun GoalDetailStep(viewModel: HomeViewModel, uiState: com.example.nozokima.ui.viewmodel.HomeUiState, goal: GoalSettingEntity, onReset: () -> Unit, onEdit: () -> Unit) {
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.triggerGoalAnalysis()
    }
    
    val planLabel = when(goal.selectedPlanType) { "RELAXED" -> "🐢 ゆったり"; "SPEED" -> "🐇 スピード重視"; else -> "👍 おすすめ" }
    val targetDate = when(goal.selectedPlanType) { "RELAXED" -> goal.relaxedTargetDate; "SPEED" -> goal.speedTargetDate; else -> goal.aiTargetDate }
    val budget = when(goal.selectedPlanType) { "RELAXED" -> goal.relaxedMonthlyBudget; "SPEED" -> goal.speedMonthlyBudget; else -> goal.aiMonthlyBudget }
    val accentColor = when(goal.selectedPlanType) { "SPEED" -> Color(0xFFD32F2F); "RELAXED" -> Color(0xFF1976D2); else -> NotionSafeGreen }

    // 進捗計算
    val totalLendingAmount = uiState.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
    val currentAssets = (uiState.assets.sumOf { it.amount } + totalLendingAmount).toLong()
    val upcomingTotal = uiState.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
    val virtualBalance = currentAssets - upcomingTotal
    val baseAssets = if (goal.useVirtualBalance) virtualBalance else currentAssets
    val progressRatio = (baseAssets.toFloat() / goal.targetAmount.toFloat()).coerceIn(0f, 1f)
    val remaining = (goal.targetAmount - baseAssets).coerceAtLeast(0L)
    val isAchieved = progressRatio >= 1.0f

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("目標のリセット", fontWeight = FontWeight.Bold) },
            text = { Text("現在の目標設定をリセットしてもよろしいですか？この操作は取り消せません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onReset()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                ) {
                    Text("リセットする", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("キャンセル")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ヘッダー進捗カード
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onEdit() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = goal.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }

                    Spacer(Modifier.height(12.dp))

                    // 円形プログレス
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier.size(110.dp),
                            color = accentColor,
                            strokeWidth = 10.dp,
                            trackColor = accentColor.copy(alpha = 0.1f),
                            strokeCap = StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(progressRatio * 100).toInt()}%",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = accentColor
                            )
                            Text(
                                text = "達成率",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (remaining > 0) "達成まであと ¥${formatAmountWithCommas(remaining.toString())}" else "目標達成しました！🎉",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // AIからのメッセージ
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (uiState.isAiGenerating && goal.aiMessage.isEmpty()) {
                            com.example.nozokima.ui.components.ThinkingAnimation()
                        } else {
                            Text(
                                text = goal.aiMessage.ifEmpty { "分析を開始します..." },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 四分割グリッド
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailBlock(
                        label = "目標額",
                        value = "¥ ${formatAmountWithCommas(goal.targetAmount.toString())}",
                        icon = Icons.Default.Savings,
                        modifier = Modifier.weight(1f)
                    )
                    DetailBlock(
                        label = "月額予算",
                        value = "¥ ${formatAmountWithCommas(budget.toString())}",
                        icon = Icons.Default.Payments,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailBlock(
                        label = "選択プラン",
                        value = planLabel,
                        icon = when(goal.selectedPlanType) { "SPEED" -> Icons.Default.FlashOn; "RELAXED" -> Icons.Default.Schedule; else -> Icons.Default.ThumbUp },
                        modifier = Modifier.weight(1f)
                    )
                    DetailBlock(
                        label = "達成予想",
                        value = targetDate,
                        icon = Icons.Default.Event,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 下部のボタン
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Button(
                onClick = { if (isAchieved) onReset() else showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAchieved) NotionSafeGreen else Color(0xFFD32F2F)
                )
            ) {
                Text(
                    text = if (isAchieved) "次の目標を立てる" else "目標をリセット",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun DetailBlock(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    Surface(
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background((accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PlanCard(type: String, label: String, targetDate: String, budget: Long, isSelected: Boolean, onSelect: () -> Unit) {
    val accentColor = when (type) { "SPEED" -> Color(0xFFD32F2F); "RELAXED" -> Color(0xFF1976D2); else -> NotionSafeGreen }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor)
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.CheckCircle, null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = targetDate, fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    Text(text = "達成予想", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("月間予算", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "¥ ${formatAmountWithCommas(budget.toString())}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
