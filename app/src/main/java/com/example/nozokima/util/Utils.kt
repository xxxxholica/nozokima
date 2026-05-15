package com.example.nozokima.util

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

fun evaluateExpression(expression: String): Int {
    if (expression.isEmpty()) return 0
    return try {
        // 全角記号の正規化、カンマの除去、不要な記号の除去
        val normalized = expression.replace("−", "-")
            .replace("×", "*")
            .replace("÷", "/")
            .replace(",", "")
            .trim()
        
        // 末尾が演算子の場合は、その演算子を除去して計算する
        var cleanExpression = normalized
        while (cleanExpression.isNotEmpty() && cleanExpression.last() in "+-*/") {
            cleanExpression = cleanExpression.dropLast(1)
        }
        
        if (cleanExpression.isEmpty()) return 0

        val tokens = cleanExpression.split(Regex("(?<=[-+*/])|(?=[-+*/])"))
        if (tokens.isEmpty()) return 0
        
        var result = tokens[0].trim().toLong()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i].trim()
            if (i + 1 >= tokens.size) break
            val nextStr = tokens[i+1].trim()
            if (nextStr.isEmpty()) { i += 2; continue }
            val next = nextStr.toLong()
            result = when(op) {
                "+" -> result + next
                "-" -> result - next
                "*" -> result * next
                "/" -> if (next != 0L) result / next else result
                else -> result
            }
            i += 2
        }
        result.toInt()
    } catch (e: Exception) {
        0
    }
}

fun generateSecurePassword(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+"
    return (1..16).map { chars.random() }.joinToString("")
}

val currencyVisualTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    val original = text.text
    if (original.isEmpty()) {
        return@VisualTransformation androidx.compose.ui.text.input.TransformedText(
            androidx.compose.ui.text.AnnotatedString("¥ 0"),
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int) = 3
                override fun transformedToOriginal(offset: Int) = 0
            }
        )
    }

    val formatted = try {
        "¥ " + String.format(java.util.Locale.JAPAN, "%,d", original.toLong())
    } catch (e: Exception) {
        "¥ $original"
    }

    val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            if (offset <= 0) return 2
            var digitCount = 0
            var i = 0
            while (digitCount < offset && i < formatted.length) {
                if (formatted[i].isDigit()) digitCount++
                i++
            }
            return maxOf(2, i)
        }

        override fun transformedToOriginal(offset: Int): Int {
            var digitCount = 0
            for (i in 0 until minOf(offset, formatted.length)) {
                if (formatted[i].isDigit()) digitCount++
            }
            return digitCount
        }
    }
    androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), offsetMapping)
}

/**
 * 簡易的なMarkdownパーサー
 * **テキスト** を太字に変換する
 */
fun parseMarkdown(text: String) = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                append(part)
            }
        } else {
            append(part)
        }
    }
}

fun formatAmountWithCommas(text: String): String {
    if (text.isEmpty()) return ""
    // 既存のカンマを一旦すべて削除して正規化
    val cleanText = text.replace(",", "")
    // 数字とそれ以外（演算子など）を分離
    val regex = Regex("(?<=[-+*/])|(?=[-+*/])")
    val tokens = cleanText.split(regex)
    return tokens.joinToString("") { token ->
        if (token.matches(Regex("\\d+"))) {
            try {
                String.format(java.util.Locale.JAPAN, "%,d", token.toLong())
            } catch (e: Exception) {
                token
            }
        } else {
            token
        }
    }
}
