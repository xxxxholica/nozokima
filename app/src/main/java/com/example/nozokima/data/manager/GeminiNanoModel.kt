package com.example.nozokima.data.manager

import android.content.Context
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class GeminiNanoModel(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val generativeModel = Generation.getClient()

    private val _status = MutableStateFlow(FeatureStatus.UNAVAILABLE)
    val status: StateFlow<Int> = _status.asStateFlow()

    private val _isDownloading = MutableStateFlow(value = false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isReady = MutableStateFlow(value = false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isGenerating = MutableStateFlow(value = false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isCheckingStatus = MutableStateFlow(value = false)
    val isCheckingStatus: StateFlow<Boolean> = _isCheckingStatus.asStateFlow()

    private val _isInitialized = MutableStateFlow(value = false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var totalBytesToDownload: Long = 1L // 0除算防止

    suspend fun checkModelStatus() {
        _isCheckingStatus.value = true
        try {
            val currentStatus = generativeModel.checkStatus()
            _status.value = currentStatus
            _isReady.value = currentStatus == FeatureStatus.AVAILABLE
            _isInitialized.value = true
        } catch (_: Exception) {
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
                    }
                    is DownloadStatus.DownloadProgress -> {
                        val progress = ((downloadStatus.totalBytesDownloaded * 100) / totalBytesToDownload).toInt()
                        _downloadProgress.value = progress.coerceIn(0, 100)
                    }
                    is DownloadStatus.DownloadCompleted -> {
                        _isDownloading.value = false
                        _downloadProgress.value = 100
                        checkModelStatus()
                    }
                    is DownloadStatus.DownloadFailed -> {
                        _isDownloading.value = false
                        _errorMessage.value = "ダウンロードに失敗しました。ストレージ空き容量を確認してください。"
                    }
                }
            }
        } catch (_: Exception) {
            _isDownloading.value = false
            _errorMessage.value = "ダウンロードエラーが発生しました。"
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
            val msg = e.message ?: ""
            if (msg.contains("context", ignoreCase = true) || msg.contains("token", ignoreCase = true)) {
                "分析を完了できませんでした。"
            } else {
                "エラーが発生しました: $msg"
            }
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
        try {
            generativeModel.generateContentStream(request).collect { response ->
                val candidate = response.candidates.firstOrNull()
                val currentText = candidate?.text ?: ""
                
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
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (!msg.contains("context", ignoreCase = true) && !msg.contains("token", ignoreCase = true)) {
                emit("エラーが発生しました: $msg")
            }
        }
    }.onStart {
        _isGenerating.value = true
    }.onCompletion {
        _isGenerating.value = false
    }
}
