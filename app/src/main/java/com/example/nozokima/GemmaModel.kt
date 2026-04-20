package com.example.nozokima

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class GemmaModel(private val context: Context) {
    private var llmInference: LlmInference? = null
    private val downloadStarted = AtomicBoolean(false)
    private val notificationHelper = DownloadNotificationHelper(context)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _copyProgress = MutableStateFlow(0)
    val copyProgress: StateFlow<Int> = _copyProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _needsDownloadPermission = MutableStateFlow(false)
    val needsDownloadPermission: StateFlow<Boolean> = _needsDownloadPermission.asStateFlow()

    private val assetFileName = "gemma.litertlm"
    private val modelFile = File(context.filesDir, assetFileName)
    private val modelPath = modelFile.absolutePath
    private val markerFile = File(context.filesDir, "gemma.litertlm.complete") // 完了マーカー
    private val downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"

    /** モデルが正常にダウンロード完了した状態かを確認 */
    private fun isModelValid(): Boolean =
        modelFile.exists() && markerFile.exists() && modelFile.length() > 100_000_000L // 100MB以上

    fun requestModelDownload() {
        if (!_isReady.value && !downloadStarted.get()) {
            _needsDownloadPermission.value = true
        }
    }

    fun declineModelDownload() {
        _needsDownloadPermission.value = false
    }

    suspend fun startDownloadIfPermitted() {
        if (downloadStarted.get()) return
        downloadStarted.set(true)
        _needsDownloadPermission.value = false
        initialize()
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (_isReady.value) return@withContext

        try {
            if (!isModelValid()) {
                // 破損・不完全なファイルがあれば削除してリセット
                if (modelFile.exists()) {
                    modelFile.delete()
                    Log.w("GemmaModel", "不完全なモデルファイルを削除しました")
                }
                markerFile.delete()
                if (!downloadStarted.get()) {
                    _errorMessage.value = "モデルのダウンロードは未承認です。"
                    // ダウンロード済みフラグをリセットして再ダウンロード可能にする
                    downloadStarted.set(false)
                    _needsDownloadPermission.value = true
                    return@withContext
                }
                downloadModelFromUrl()
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            _isReady.value = true
            _errorMessage.value = null
        } catch (e: Exception) {
            Log.e("GemmaModel", "初期化エラー", e)
            // 初期化失敗 = モデルが壊れている可能性 → 削除して再ダウンロード可能にする
            modelFile.delete()
            markerFile.delete()
            downloadStarted.set(false)
            _errorMessage.value = "モデルの読み込みに失敗しました。再ダウンロードが必要です。"
            _needsDownloadPermission.value = true
            _isReady.value = false
        }
    }

    private suspend fun downloadModelFromUrl() = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            val totalSize = connection.contentLengthLong
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var bytesRead: Int
                    var downloadedSize = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        if (totalSize > 0) {
                            val progress = (downloadedSize * 100 / totalSize).toInt().coerceIn(0, 100)
                            _copyProgress.value = progress
                            notificationHelper.showProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            notificationHelper.showComplete()
            markerFile.createNewFile() // 正常完了マーカーを書き込む
        } catch (e: Exception) {
            Log.e("GemmaModel", "ダウンロード失敗", e)
            if (modelFile.exists()) modelFile.delete()
            markerFile.delete()
            notificationHelper.showError()
            downloadStarted.set(false) // 再試行を許可
            _errorMessage.value = "ダウンロードに失敗しました。空き容量を確認してください。"
            throw e
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (!_isReady.value) initialize()
        if (!_isReady.value) return@withContext "AIの準備が完了するまでお待ちください。"

        val formattedPrompt = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
        try {
            llmInference?.generateResponse(formattedPrompt) ?: "応答の生成に失敗しました。"
        } catch (e: Exception) {
            Log.e("GemmaModel", "生成エラー", e)
            "生成エラー: ${e.message}"
        }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
        _isReady.value = false
    }
}