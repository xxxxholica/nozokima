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

class GemmaModel(private val context: Context) {
    private var llmInference: LlmInference? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _copyProgress = MutableStateFlow(0)
    val copyProgress: StateFlow<Int> = _copyProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val assetFileName = "gemma.litertlm"
    private val modelFile = File(context.filesDir, assetFileName)
    private val modelPath = modelFile.absolutePath

    private val downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (_isReady.value) return@withContext

        try {
            if (!modelFile.exists() || modelFile.length() < 1000000) {
                downloadModelFromUrl()
            }

            // setTopKとsetTemperatureはSDKのバージョンにより未定義エラーが出るため除外
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            _isReady.value = true
            _errorMessage.value = null
        } catch (e: Exception) {
            Log.e("GemmaModel", "初期化エラー", e)
            _errorMessage.value = "モデルの準備に失敗しました: ${e.localizedMessage}"
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
                            val progress = (downloadedSize * 100 / totalSize).toInt()
                            _copyProgress.value = progress.coerceIn(0, 100)
                        }
                    }
                    output.flush()
                }
            }
        } catch (e: Exception) {
            Log.e("GemmaModel", "ダウンロード失敗", e)
            if (modelFile.exists()) modelFile.delete()
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