@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.nozokima.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.*
import com.example.nozokima.model.*
import com.example.nozokima.data.local.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.components.ChatBubble
import com.example.nozokima.ui.components.DeleteConfirmDialog
import com.example.nozokima.ui.components.ThinkingAnimation
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.launch
import ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// --- プリセット質問 ---
private val PRESET_QUESTIONS = listOf(
    "今月の収支を分析して",
    "節約のアドバイスをちょうだい",
    "無駄遣いしていないかチェックして",
    "今の資産で目標達成できる？",
    "貸付金の状況を教えて"
)

@OptIn(ExperimentalMaterial3Api::class)
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
    val aiStatus by (gemini?.status?.collectAsState() ?: remember { mutableStateOf(FeatureStatus.UNAVAILABLE) })
    val isAvailable = aiStatus != FeatureStatus.UNAVAILABLE

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

        val showStatusBox = !isReady && (errorMsg != null || isDownloading || aiStatus == FeatureStatus.UNAVAILABLE)
        if (showStatusBox) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = NotionBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NotionBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (aiStatus == FeatureStatus.UNAVAILABLE) {
                        val uriHandler = LocalUriHandler.current
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFFE57373).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Info, null, tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("覗き魔AI は利用できません", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                            }
                            Text(
                                text = "Gemini Nanoに対応したデバイスのみ利用可能です。\n対応デバイスは下記リンクをご覧ください。",
                                fontSize = 12.sp,
                                color = NotionTextPrimary,
                                lineHeight = 18.sp
                            )
                            Text(
                                text = "https://developers.google.com/ml-kit/genai?hl=ja",
                                color = Color(0xFF1976D2),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { 
                                    uriHandler.openUri("https://developers.google.com/ml-kit/genai?hl=ja")
                                }
                            )
                        }
                    } else if (errorMsg != null) {
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
                            modifier = Modifier.padding(horizontal = 24.dp).alpha(if (isAvailable) 1f else 0.5f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            suggestedQuestions.forEach { question ->
                                SuggestionChip(
                                    enabled = isReady,
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
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 2.dp)
                    .alpha(if (isAvailable) 1f else 0.5f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedQuestions.forEach { question ->
                    SuggestionChip(
                        enabled = isReady,
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
                .alpha(if (isAvailable) 1f else 0.5f)
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
                                enabled = isReady,
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
