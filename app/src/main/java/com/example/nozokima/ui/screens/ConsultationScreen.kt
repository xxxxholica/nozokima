@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.nozokima.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.nozokima.model.*
import com.example.nozokima.data.local.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.components.ChatBubble
import com.example.nozokima.ui.components.DeleteConfirmDialog
import com.example.nozokima.ui.components.ScreenHeader
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
    "貸付金の状況を教えて",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationScreen(
    modifier: Modifier = Modifier,
    dao: FinanceDao,
    gemini: GeminiNanoModel? = null,
    ocrManager: OcrManager? = null,
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
    onClearHomeAdvice: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTxForConsult by remember { mutableStateOf<TransactionEntity?>(null) }
    var showTxPicker by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var suggestedQuestions by remember { mutableStateOf(PRESET_QUESTIONS.shuffled().take(3)) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
    }

    val messages by if (currentSessionId != null) {
        dao.getMessagesForSession(currentSessionId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    // --- 各種状態取得 ---
    val isReady by (gemini?.isReady?.collectAsState() ?: remember { mutableStateOf(value = false) })
    val isDownloading by (gemini?.isDownloading?.collectAsState() ?: remember { mutableStateOf(value = false) })
    val progress by (gemini?.downloadProgress?.collectAsState() ?: remember { mutableIntStateOf(0) })
    val errorMsg by (gemini?.errorMessage?.collectAsState() ?: remember { mutableStateOf(null) })
    val isGenerating by (gemini?.isGenerating?.collectAsState() ?: remember { mutableStateOf(value = false) })
    val aiStatus by (gemini?.status?.collectAsState() ?: remember { mutableIntStateOf(FeatureStatus.UNAVAILABLE) })
    val isInitialized by (gemini?.isInitialized?.collectAsState() ?: remember { mutableStateOf(value = false) })
    val isChecking by (gemini?.isCheckingStatus?.collectAsState() ?: remember { mutableStateOf(value = false) })
    val isAvailable = aiStatus != FeatureStatus.UNAVAILABLE

    val showLoading = !isInitialized || isChecking

    // --- サジェスト生成ロジック ---
    fun generateSuggestions(currentMessages: List<ChatMessageEntity>) {
        if ((gemini == null) || !isReady || isGenerating) return
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
                あなたは実用的で実利的な家計の相棒です。
                これまでのユーザーとの会話や現在の資産状況を踏まえて、ユーザーが次に聞きそうな「具体的なデータ確認」や「現実的な相談」の質問を3つ提案してください。
                
                $assetContext
                
                $historyContext
                
                【出力ルール】
                ・質問文のみを1行に1つ、合計3行で出力してください。
                ・「1.」「2.」といった数字や記号、箇条書きのマークは一切含めないでください。
                ・丁寧な言葉遣い（です・ます調）で、文脈を汲み取った自然な日本語の質問にしてください。
                ・質問は20文字以内で、答えやすい内容にしてください。
                
                自己紹介、挨拶、タメ口、自身の回答方針への言及は禁止です。
            """.trimIndent()
            
            try {
                val response = gemini.generateResponse(prompt)
                suggestedQuestions = response.lineSequence().filter { it.isNotBlank() }.take(3).toList()
            } catch (_: Exception) {
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
                dao.insertChatMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        text = userMsg,
                        isUser = true,
                    ),
                )
                
                onSessionSelected(sessionId)
                onClearConsultation()
                suggestedQuestions = emptyList()

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
                        あなたは家計の専門家です。
                        ユーザーの特定の支出について、データに基づいた鋭い分析を直接伝えてください。
                        
                        $assetContext
                        $recentTxContext
                        ユーザーの相談: $userMsg
                        
                        【回答ルール】
                        ・「深掘りする問いかけ：」「問いかけ：」などの見出しやラベルは絶対に書かないでください。
                        ・自己紹介、挨拶、タメ口、精神論は不要です。
                        ・丁寧な言葉遣い（です・ます調）で回答してください。
                        ・回答の最後に、自然な会話の流れで一つだけ、ユーザーが答えやすい質問を添えてください。
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
                    } catch (_: Exception) {
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
                suggestedQuestions = emptyList()

                // AI応答
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
                        あなたは家計の専門家です。
                        ホーム画面でのアドバイスについて、具体的な根拠やアクションを実利的な視点で解説してください。
                        
                        $assetContext
                        $recentTxContext
                        ユーザーの相談: $userMsg
                        
                        【回答ルール】
                        ・「深掘りする問いかけ：」「問いかけ：」などの見出しやラベルは絶対に書かないでください。
                        ・自己紹介、タメ口、自身の性格設定への言及は不要です。
                        ・丁寧な言葉遣い（です・ます調）で、端的に回答してください。
                        ・回答の最後に、より理解を深めるための自然な質問を一文添えてください。
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
                    } catch (_: Exception) {
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val ime = WindowInsets.ime
    val isKeyboardVisible by remember {
        derivedStateOf {
            ime.getBottom(density) > 0
        }
    }
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // チャット画面ヘッダー
        ScreenHeader(
            title = if (currentSessionId == null) "新しい相談" else (chatSessions.find { it.id == currentSessionId }?.title ?: "AI相談"),
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
            },
            trailingContent = {
                Surface(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = NotionTextSecondary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, contentDescription = "メニュー", tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        val showStatusBox = !isReady && (errorMsg != null || isDownloading || aiStatus == FeatureStatus.UNAVAILABLE)
        if (showStatusBox) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = NotionBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NotionBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (showLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = NotionSafeGreen, strokeWidth = 2.dp)
                        }
                    } else if (aiStatus == FeatureStatus.UNAVAILABLE) {
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
                                            scope.launch {
                                                val sessionId = UUID.randomUUID().toString()
                                                dao.insertChatMessage(ChatMessageEntity(sessionId = sessionId, text = question, isUser = true))
                                                dao.upsertChatSession(ChatSessionEntity(id = sessionId, title = question.take(20), lastMessageAt = System.currentTimeMillis()))
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
                                                    あなたは家計の専門家です。現状を分析し、150文字以内で回答してください。
                                                    
                                                    【回答ルール】
                                                    ・「深掘りする問いかけ：」「問いかけ：」などの見出しやラベル、記号は一切含めないでください。
                                                    ・質問の範囲が指定されていない場合は、まず全体的な概要を回答してください。
                                                    ・自己紹介、精神論、タメ口は禁止です。
                                                    ・丁寧な言葉遣い（です・ます調）で、数字に基づく話をしてください。
                                                    ・回答の最後に、自然な会話の流れでユーザーへの質問を一つ添えてください。
                                                    
                                                    $assetContext
                                                    $recentTxContext
                                                    ユーザーの質問: $question
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
                                        containerColor = Color.Transparent,
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
                                scope.launch {
                                    val sessionId = currentSessionId ?: UUID.randomUUID().toString()
                                    dao.insertChatMessage(ChatMessageEntity(sessionId = sessionId, text = question, isUser = true))
                                    if (currentSessionId == null) {
                                        dao.upsertChatSession(ChatSessionEntity(id = sessionId, title = question.take(20), lastMessageAt = System.currentTimeMillis()))
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
                                        あなたは家計の専門家です。資産状況と支出履歴をもとに、150文字以内でアドバイスしてください。
                                        
                                        【回答ルール】
                                        ・「深掘りする問いかけ：」「問いかけ：」などの見出しやラベルは絶対に書かないでください。
                                        ・自己紹介、精神論、タメ口は禁止です。
                                        ・丁寧な言葉遣い（です・ます調）で、事実を端的に伝えてください。
                                        ・回答の最後に、自然な流れでユーザーへの問いかけを一つ添えてください。
                                        
                                        $assetContext
                                        $recentTxContext
                                        ユーザーの質問: $question
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
                        label = { Text(question, fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color.Transparent,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NotionWhite)
                .alpha(if (isAvailable) 1f else 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // 画像プレビューと選択された支出の表示
                if (selectedImageUri != null || selectedTxForConsult != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (selectedImageUri != null) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(selectedImageUri),
                                    contentDescription = "選択された画像",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.5f)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.padding(2.dp))
                                }
                            }
                        }

                        if (selectedTxForConsult != null) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = NotionBackground,
                                border = BorderStroke(1.dp, NotionBorder)
                            ) {
                                Box(modifier = Modifier.padding(12.dp)) {
                                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                        Text(
                                            text = "対象の支出",
                                            fontSize = 10.sp,
                                            color = NotionTextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = selectedTxForConsult!!.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NotionTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "¥${String.format(Locale.JAPAN, "%,d", selectedTxForConsult!!.amount)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE57373)
                                        )
                                    }
                                    Surface(
                                        onClick = { selectedTxForConsult = null },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp),
                                        shape = CircleShape,
                                        color = NotionTextSecondary.copy(alpha = 0.2f)
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = NotionTextSecondary, modifier = Modifier.padding(2.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = NotionWhite,
                    border = BorderStroke(1.dp, NotionBorder.copy(alpha = 0.5f)),
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 1行目: テキスト入力領域
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    "相談内容を入力",
                                    color = NotionTextSecondary.copy(alpha = 0.5f),
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                enabled = isReady,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    color = NotionTextPrimary,
                                    lineHeight = 22.sp
                                ),
                                maxLines = 5
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2行目: アクションボタン
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 添付ボタン
                                Surface(
                                    onClick = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedImageUri != null) NotionSafeGreen.copy(alpha = 0.1f) else NotionTextSecondary.copy(alpha = 0.05f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "画像を添付",
                                            tint = if (selectedImageUri != null) NotionSafeGreen else NotionTextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // 支出選択ボタン
                                Surface(
                                    onClick = { showTxPicker = true },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedTxForConsult != null) Color(0xFFE57373).copy(alpha = 0.1f) else NotionTextSecondary.copy(alpha = 0.05f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = "支出を選択",
                                            tint = if (selectedTxForConsult != null) Color(0xFFE57373) else NotionTextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = {
                                    if ((inputText.isNotBlank() || selectedImageUri != null || selectedTxForConsult != null) && gemini != null) {
                                        val userMsgText = inputText
                                        val imageUri = selectedImageUri
                                        val tx = selectedTxForConsult
                                        
                                        inputText = ""
                                        selectedImageUri = null
                                        selectedTxForConsult = null
                                        
                                        scope.launch {
                                            val sessionId = currentSessionId ?: UUID.randomUUID().toString()
                                            
                                            var finalUserMsg = userMsgText
                                            var aiContextAdd = ""

                                            // 画像OCR処理
                                            if (imageUri != null && ocrManager != null) {
                                                val extracted = ocrManager.extractFullText(imageUri)
                                                if (!extracted.isNullOrBlank()) {
                                                    aiContextAdd += "\n【添付画像のテキスト内容】\n$extracted\n"
                                                    if (finalUserMsg.isBlank()) finalUserMsg = "この画像について教えてください。"
                                                }
                                            }

                                            // 選択された支出のコンテキスト
                                            if (tx != null) {
                                                aiContextAdd += "\n【相談対象의 支出】\n名称: ${tx.name}\n金額: ¥${tx.amount}\nカテゴリ: ${tx.category}\n日付: ${SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(Date(tx.date))}\n"
                                                if (finalUserMsg.isBlank()) finalUserMsg = "この支出について相談したいです。"
                                            }

                                            // ユーザーメッセージ表示用テキスト
                                            val displayUserMsg = buildString {
                                                append(userMsgText)
                                                if (tx != null) {
                                                    if (isNotEmpty()) append("\n")
                                                    append("支出「${tx.name}」(¥${String.format(Locale.JAPAN, "%,d", tx.amount)})")
                                                }
                                                if (imageUri != null) {
                                                    if (isNotEmpty()) append("\n")
                                                    append("(画像を送信しました)")
                                                }
                                            }

                                            dao.insertChatMessage(ChatMessageEntity(sessionId = sessionId, text = displayUserMsg, isUser = true))
                                            
                                            if (currentSessionId == null) {
                                                dao.upsertChatSession(ChatSessionEntity(id = sessionId, title = displayUserMsg.take(20), lastMessageAt = System.currentTimeMillis()))
                                                onSessionSelected(sessionId)
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

                                            val fullPrompt = """
                                                あなたは家計の専門家です。150文字以内で端的に回答してください。
                                                
                                                【回答ルール】
                                                ・「深掘りする問いかけ：」「問いかけ：」などのラベル表示は厳禁です。
                                                ・自己紹介、精神論、タメ口は禁止です。
                                                ・丁寧な言葉遣い（です・ます調）で、数字から言えることを伝えてください。
                                                ・回答の最後に、文脈を汲み取った自然な聞き方で、ユーザーへの質問を一つ添えてください。
                                                
                                                $assetContext
                                                $recentTxContext
                                                $aiContextAdd
                                                ユーザーの質問: $finalUserMsg
                                            """.trimIndent()

                                            val aiMsgId = UUID.randomUUID().toString()
                                            var accumulatedText = ""
                                            dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = "...", isUser = false))
                                            suggestedQuestions = emptyList()

                                            gemini.generateResponseStream(fullPrompt).collect { chunk ->
                                                accumulatedText += chunk
                                                dao.insertChatMessage(ChatMessageEntity(id = aiMsgId, sessionId = sessionId, text = accumulatedText, isUser = false))
                                            }
                                            generateSuggestions(dao.getMessagesForSessionSync(sessionId))
                                        }
                                    }
                                },
                                modifier = Modifier.background(if (inputText.isNotBlank() || selectedImageUri != null || selectedTxForConsult != null) NotionSafeGreen else NotionBorder, CircleShape).size(36.dp),
                                enabled = (inputText.isNotBlank() || selectedImageUri != null || selectedTxForConsult != null) && isReady
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTxPicker) {
        ModalBottomSheet(
            onDismissRequest = { showTxPicker = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)) {
                Text(
                    "相談する支出を選択",
                    modifier = Modifier.padding(24.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NotionTextPrimary
                )
                val expenseList = transactions.filter { it.isExpense && !it.isTransfer }
                if (expenseList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("支出の記録がありません", color = NotionTextSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(expenseList.take(20)) { tx ->
                            ListItem(
                                headlineContent = { Text(tx.name, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("${SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(tx.date))} • ${tx.category}") },
                                trailingContent = { Text("¥${String.format(Locale.JAPAN, "%,d", tx.amount)}", fontWeight = FontWeight.Bold, color = Color(0xFFE57373)) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier.size(40.dp).background(NotionBorder.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(getCategoryIcon(tx.category), null, tint = NotionTextSecondary, modifier = Modifier.size(20.dp))
                                    }
                                },
                                modifier = Modifier.clickable {
                                    selectedTxForConsult = tx
                                    showTxPicker = false
                                }
                            )
                        }
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
        ) {
            sessionToDelete?.id?.let { onDeleteSession(it) }
            sessionToDelete = null
        }
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
