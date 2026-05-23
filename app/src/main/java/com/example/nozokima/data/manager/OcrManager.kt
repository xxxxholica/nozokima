package com.example.nozokima.data.manager

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrManager(private val context: Context) {
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    suspend fun extractAmount(uri: Uri): Int? {
        val candidates = extractAmountCandidates(uri)
        // レシートの場合、一番大きい数値が合計金額である可能性が高い
        return candidates.maxOrNull()
    }

    suspend fun extractAmountCandidates(uri: Uri): List<Int> {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        
        // シンプルな正規表現で金額らしい数値を抽出
        // 1,234 や 1234、￥1,234、1234円などを対象にする
        // 日本語エンジンなので全角も考慮されるが、ML Kit側で半角に正規化されることが多い
        val amountRegex = Regex("[¥￥$]?\\s?(\\d{1,3}(?:,\\d{3})+|\\d{2,})")
        
        val candidates = mutableSetOf<Int>()
        
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val match = amountRegex.find(line.text)
                if (match != null) {
                    val valueStr = match.groupValues[1].replace(",", "")
                    val value = valueStr.toIntOrNull()
                    // 10円以上かつ100万円以下の現実的な範囲を候補とする
                    if ((value != null) && (value in 10..1000000)) {
                        candidates.add(value)
                    }
                }
            }
        }
        
        return candidates.asSequence().sortedDescending().toList()
    }

    suspend fun extractFullText(uri: Uri): String? {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return result.text.ifBlank { null }
    }
}
