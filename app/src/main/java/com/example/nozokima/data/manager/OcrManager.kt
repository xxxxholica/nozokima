package com.example.nozokima.data.manager

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrManager(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractAmount(uri: Uri): Int? {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        
        // シンプルな正規表現で金額らしい数値を抽出
        // 1,234 や 1234 などを対象にする
        val amountRegex = Regex("[¥|$]?\\s?(\\d{1,3}(,\\d{3})*|\\d+)")
        
        val candidates = mutableListOf<Int>()
        
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val match = amountRegex.find(line.text)
                if (match != null) {
                    val valueStr = match.groupValues[1].replace(",", "")
                    val value = valueStr.toIntOrNull()
                    if ((value != null) && (value > 0)) {
                        candidates.add(value)
                    }
                }
            }
        }
        
        // レシートの場合、一番大きい数値が合計金額である可能性が高い
        return candidates.maxOrNull()
    }

    suspend fun extractFullText(uri: Uri): String? {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return result.text.ifBlank { null }
    }
}
