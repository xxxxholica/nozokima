package com.example.nozokima.data.manager

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class GeminiNanoModel(private val context: Context) {
    private val generativeModel = Generation.getClient()

    private val _status = MutableStateFlow<Int>(FeatureStatus.UNAVAILABLE)
    val status: StateFlow<Int> = _status.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isCheckingStatus = MutableStateFlow(false)
    val isCheckingStatus: StateFlow<Boolean> = _isCheckingStatus.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var totalBytesToDownload: Long = 1L // 0除算防止

    suspend fun checkModelStatus() {
        _isCheckingStatus.value = true
        try {
            val currentStatus = generativeModel.checkStatus()
            _status.value = currentStatus
            _isReady.value = currentStatus == FeatureStatus.AVAILABLE
            _isInitialized.value = true
            Log.d("GeminiNanoModel", "Model status: $currentStatus")
        } catch (e: Exception) {
            Log.e("GeminiNanoModel", "Error checking status", e)
            _errorMessage.value = "AIの状態確認に失敗しました。"
        } finally {
            _isCheckingStatus.value = false
        }
    }

    suspend fun startDownload() {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _errorMessage.value = null

        try {
            generativeModel.download().collect { downloadStatus ->
                when (downloadStatus) {
                    is DownloadStatus.DownloadStarted -> {
                        totalBytesToDownload = downloadStatus.bytesToDownload.coerceAtLeast(1L)
                        Log.d("GeminiNanoModel", "Download started: $totalBytesToDownload bytes")
                    }
                    is DownloadStatus.DownloadProgress -> {
                        val progress = (downloadStatus.totalBytesDownloaded * 100 / totalBytesToDownload).toInt()
                        _downloadProgress.value = progress.coerceIn(0, 100)
                    }
                    is DownloadStatus.DownloadCompleted -> {
                        _isDownloading.value = false
                        _downloadProgress.value = 100
                        checkModelStatus()
                        Log.i("GeminiNanoModel", "Download completed")
                    }
                    is DownloadStatus.DownloadFailed -> {
                        _isDownloading.value = false
                        _errorMessage.value = "ダウンロードに失敗しました。ストレージ空き容量を確認してください。"
                        Log.e("GeminiNanoModel", "Download failed: ${downloadStatus.e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            _isDownloading.value = false
            _errorMessage.value = "ダウンロードエラーが発生しました。"
            Log.e("GeminiNanoModel", "Error during download", e)
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (!_isReady.value) {
            checkModelStatus()
            if (!_isReady.value) return@withContext "AIの準備が完了するまでお待ちください。"
        }

        _isGenerating.value = true
        try {
            // ML Kit Prompt API beta2 では String を直接渡す generateContent が用意されている
            val response = generativeModel.generateContent(prompt)
            response.candidates.firstOrNull()?.text ?: "応答が空でした。"
        } catch (e: Exception) {
            Log.e("GeminiNanoModel", "Generation error", e)
            "エラーが発生しました: ${e.message}"
        } finally {
            _isGenerating.value = false
        }
    }

    fun generateResponseStream(prompt: String): Flow<String> = flow {
        if (!_isReady.value) {
            checkModelStatus()
            if (!_isReady.value) {
                emit("AIの準備が完了するまでお待ちください。")
                return@flow
            }
        }

        // 出力上限を拡張（APIの最大許容値である 256 に設定）
        val request = generateContentRequest(TextPart(prompt)) {
            maxOutputTokens = 256
        }

        var lastText = ""
        var wasInterruptedByTokenLimit = false
        try {
            generativeModel.generateContentStream(request).collect { response ->
                val candidate = response.candidates.firstOrNull()
                val currentText = candidate?.text ?: ""
                
                // トークン上限に達したかチェック
                if (candidate?.finishReason == Candidate.FinishReason.MAX_TOKENS) {
                    wasInterruptedByTokenLimit = true
                }
                
                // APIが累積テキストを返す場合と差分を返す場合の両方に対応するため、
                // 前回のテキストとの比較を行い、増分（delta）のみを送信する。
                val delta = if (currentText.startsWith(lastText)) {
                    currentText.substring(lastText.length)
                } else {
                    currentText
                }
                
                if (delta.isNotEmpty()) {
                    emit(delta)
                }
                lastText = currentText
            }
            
            // トークン制限で中断された場合にメッセージを追記
            if (wasInterruptedByTokenLimit) {
                emit("\n\n(※回答が長くなったため、途中で制限されました。)")
            }
        } catch (e: Exception) {
            Log.e("GeminiNanoModel", "Streaming generation error", e)
            emit("エラーが発生しました: ${e.message}")
        }
    }.onStart {
        _isGenerating.value = true
    }.onCompletion {
        _isGenerating.value = false
    }
}
