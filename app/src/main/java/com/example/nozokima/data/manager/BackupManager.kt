package com.example.nozokima.data.manager

import android.content.Context
import com.example.nozokima.data.local.FinanceDao
import com.example.nozokima.data.local.entities.*
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

class BackupManager(private val dao: FinanceDao, private val context: Context) {

    private fun escapeCsv(value: String?): String {
        if (value == null) return ""
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun List<String>.toCsvRow(): String = joinToString(",") + "\n"

    private fun parseCsv(content: String): List<Map<String, String>> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        
        val header = splitCsvRow(lines[0])
        return lines.drop(1).map { line ->
            val values = splitCsvRow(line)
            header.zip(values).toMap()
        }
    }

    private fun splitCsvRow(row: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < row.length && row[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    suspend fun exportData(password: String): ByteArray {
        val tempDir = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val filesToZip = mutableListOf<File>()

            // Transactions
            val txFile = File(tempDir, "transactions.csv")
            txFile.writeText("id,name,amount,category,date,assetName,isExpense\n", Charsets.UTF_8)
            dao.getAllTransactionsList().forEach { tx ->
                txFile.appendText(listOf(tx.id, tx.name, tx.amount.toString(), tx.category, tx.date.toString(), tx.assetName, tx.isExpense.toString()).map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(txFile)

            // Assets
            val assetFile = File(tempDir, "assets.csv")
            assetFile.writeText("id,name,amount,category,lastUpdated\n", Charsets.UTF_8)
            dao.getAllAssetsList().forEach { asset ->
                assetFile.appendText(listOf(asset.id, asset.name, asset.amount.toString(), asset.category, asset.lastUpdated.toString()).map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(assetFile)

            // Budgets
            val budgetFile = File(tempDir, "budgets.csv")
            budgetFile.writeText("category,monthlyAmount\n", Charsets.UTF_8)
            dao.getAllBudgetsList().forEach { budget ->
                budgetFile.appendText(listOf(budget.category, budget.monthlyAmount.toString()).map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(budgetFile)

            // Lendings
            val lendingFile = File(tempDir, "lendings.csv")
            lendingFile.writeText("id,personName,amount,loanAsset,memo,date,isRecovered,returnAsset,recoveredAmount,recoveredDate\n", Charsets.UTF_8)
            dao.getAllLendingsList().forEach { l ->
                lendingFile.appendText(listOf(l.id, l.personName, l.amount.toString(), l.loanAsset, l.memo, l.date.toString(), l.isRecovered.toString(), l.returnAsset ?: "", l.recoveredAmount.toString(), l.recoveredDate?.toString() ?: "").map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(lendingFile)

            // Goal Setting
            val goalFile = File(tempDir, "goal_setting.csv")
            goalFile.writeText("id,title,targetAmount,monthlyIncome,targetDateMillis,showResults,startDateMillis\n", Charsets.UTF_8)
            dao.getGoalSettingSync()?.let { g ->
                goalFile.appendText(listOf(g.id.toString(), g.title, g.targetAmount.toString(), g.monthlyIncome.toString(), g.targetDateMillis.toString(), g.showResults.toString(), g.startDateMillis.toString()).map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(goalFile)

            // Chat Sessions
            val sessionFile = File(tempDir, "chat_sessions.csv")
            sessionFile.writeText("id,title,lastMessageAt\n", Charsets.UTF_8)
            dao.getAllChatSessionsList().forEach { s ->
                sessionFile.appendText(listOf(s.id, s.title, s.lastMessageAt.toString()).map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(sessionFile)

            // Chat Messages
            val messageFile = File(tempDir, "chat_messages.csv")
            messageFile.writeText("id,sessionId,text,isUser,timestamp\n", Charsets.UTF_8)
            dao.getAllChatMessagesList().forEach { m ->
                messageFile.appendText(listOf(m.id, m.sessionId, m.text, m.isUser.toString(), m.timestamp.toString()).map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(messageFile)
            
            // Categories
            val categoryFile = File(tempDir, "categories.csv")
            categoryFile.writeText("id,name,type,iconName,isDefault,order\n", Charsets.UTF_8)
            dao.getAllCategoriesListSync().forEach { c ->
                categoryFile.appendText(listOf(c.id, c.name, c.type, c.iconName, c.isDefault.toString(), c.order.toString()).map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(categoryFile)
            
            // Recurring Transactions
            val recurringFile = File(tempDir, "recurring_transactions.csv")
            recurringFile.writeText("id,name,amount,category,assetName,dayOfMonth,isExpense,lastProcessedDate\n", Charsets.UTF_8)
            dao.getAllRecurringTransactionsListSync().forEach { r ->
                recurringFile.appendText(listOf(r.id, r.name, r.amount.toString(), r.category, r.assetName, r.dayOfMonth.toString(), r.isExpense.toString(), r.lastProcessedDate.toString()).map { escapeCsv(it) }.toCsvRow(), Charsets.UTF_8)
            }
            filesToZip.add(recurringFile)

            // Export Info
            val infoFile = File(tempDir, "info.txt")
            infoFile.writeText("exportDate=${System.currentTimeMillis()}\n", Charsets.UTF_8)
            filesToZip.add(infoFile)

            // Zip it
            val zipFile = File(tempDir, "backup.zip")
            val zipParameters = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            
            val zip = ZipFile(zipFile, password.toCharArray())
            zip.addFiles(filesToZip, zipParameters)
            
            return zipFile.readBytes()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun importData(inputStream: InputStream, password: String) {
        val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val zipFile = File(tempDir, "import.zip")
        
        try {
            zipFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            
            val zip = ZipFile(zipFile, password.toCharArray())
            if (!zip.isValidZipFile) {
                throw Exception("無効なZIPファイルです。")
            }
            if (zip.isEncrypted) {
                try {
                    zip.extractAll(tempDir.absolutePath)
                } catch (e: Exception) {
                    throw Exception("パスワードが正しくありません。")
                }
            } else {
                zip.extractAll(tempDir.absolutePath)
            }
            
            // 全データを削除してからインポート
            dao.deleteAllTransactions()
            dao.deleteAllAssets()
            dao.deleteAllBudgets()
            dao.deleteAllLendings()
            dao.deleteAllChatSessions()
            dao.deleteAllChatMessages()
            dao.deleteAllGoalSettings()
            dao.deleteAllCategories()
            dao.deleteAllRecurringTransactions()

            // Transactions
            File(tempDir, "transactions.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).forEach { row ->
                    dao.insertTransaction(TransactionEntity(
                        id = row["id"] ?: "",
                        name = row["name"] ?: "",
                        amount = row["amount"]?.toIntOrNull() ?: 0,
                        category = row["category"] ?: "",
                        date = row["date"]?.toLongOrNull() ?: 0L,
                        assetName = row["assetName"] ?: "",
                        isExpense = row["isExpense"]?.toBoolean() ?: true
                    ))
                }
            }

            // Assets
            File(tempDir, "assets.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).forEach { row ->
                    dao.insertAsset(AssetEntity(
                        id = row["id"] ?: "",
                        name = row["name"] ?: "",
                        amount = row["amount"]?.toIntOrNull() ?: 0,
                        category = row["category"] ?: "",
                        lastUpdated = row["lastUpdated"]?.toLongOrNull() ?: 0L
                    ))
                }
            }

            // Budgets
            File(tempDir, "budgets.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).forEach { row ->
                    dao.insertBudget(BudgetEntity(
                        category = row["category"] ?: "",
                        monthlyAmount = row["monthlyAmount"]?.toIntOrNull() ?: 0
                    ))
                }
            }

            // Lendings
            File(tempDir, "lendings.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).forEach { row ->
                    dao.insertLending(LendingEntity(
                        id = row["id"] ?: "",
                        personName = row["personName"] ?: "",
                        amount = row["amount"]?.toIntOrNull() ?: 0,
                        loanAsset = row["loanAsset"] ?: "",
                        memo = row["memo"] ?: "",
                        date = row["date"]?.toLongOrNull() ?: 0L,
                        isRecovered = row["isRecovered"]?.toBoolean() ?: false,
                        returnAsset = row["returnAsset"].takeIf { !it.isNullOrBlank() },
                        recoveredAmount = row["recoveredAmount"]?.toIntOrNull() ?: 0,
                        recoveredDate = row["recoveredDate"]?.toLongOrNull()
                    ))
                }
            }

            // Goal Setting
            File(tempDir, "goal_setting.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).firstOrNull()?.let { row ->
                    dao.upsertGoalSetting(GoalSettingEntity(
                        id = row["id"]?.toIntOrNull() ?: 1,
                        title = row["title"] ?: "",
                        targetAmount = row["targetAmount"]?.toLongOrNull() ?: 0L,
                        monthlyIncome = row["monthlyIncome"]?.toLongOrNull() ?: 0L,
                        targetDateMillis = row["targetDateMillis"]?.toLongOrNull() ?: 0L,
                        showResults = row["showResults"]?.toBoolean() ?: false,
                        startDateMillis = row["startDateMillis"]?.toLongOrNull() ?: 0L
                    ))
                }
            }

            // Chat Sessions
            File(tempDir, "chat_sessions.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).forEach { row ->
                    dao.upsertChatSession(ChatSessionEntity(
                        id = row["id"] ?: "",
                        title = row["title"] ?: "",
                        lastMessageAt = row["lastMessageAt"]?.toLongOrNull() ?: 0L
                    ))
                }
            }

            // Chat Messages
            File(tempDir, "chat_messages.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).forEach { row ->
                    dao.insertChatMessage(ChatMessageEntity(
                        id = row["id"] ?: "",
                        sessionId = row["sessionId"] ?: "",
                        text = row["text"] ?: "",
                        isUser = row["isUser"]?.toBoolean() ?: false,
                        timestamp = row["timestamp"]?.toLongOrNull() ?: 0L
                    ))
                }
            }
            
            // Categories
            File(tempDir, "categories.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).forEach { row ->
                    dao.insertCategory(CategoryEntity(
                        id = row["id"] ?: "",
                        name = row["name"] ?: "",
                        type = row["type"] ?: "EXPENSE",
                        iconName = row["iconName"] ?: "MoreHoriz",
                        isDefault = row["isDefault"]?.toBoolean() ?: false,
                        order = row["order"]?.toIntOrNull() ?: 0
                    ))
                }
            }
            
            // Recurring Transactions
            File(tempDir, "recurring_transactions.csv").takeIf { it.exists() }?.let { file ->
                parseCsv(file.readText()).forEach { row ->
                    dao.insertRecurringTransaction(RecurringTransactionEntity(
                        id = row["id"] ?: "",
                        name = row["name"] ?: "",
                        amount = row["amount"]?.toIntOrNull() ?: 0,
                        category = row["category"] ?: "",
                        assetName = row["assetName"] ?: "",
                        dayOfMonth = row["dayOfMonth"]?.toIntOrNull() ?: 1,
                        isExpense = row["isExpense"]?.toBoolean() ?: true,
                        lastProcessedDate = row["lastProcessedDate"]?.toLongOrNull() ?: 0L
                    ))
                }
            }

        } finally {
            tempDir.deleteRecursively()
        }
    }
}
