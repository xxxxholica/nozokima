package com.example.nozokima

import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val dao: FinanceDao) {

    suspend fun exportData(password: String): String {
        val root = JSONObject()

        // Transactions
        val transactionsArray = JSONArray()
        dao.getAllTransactionsList().forEach { tx ->
            transactionsArray.put(JSONObject().apply {
                put("id", tx.id)
                put("name", tx.name)
                put("amount", tx.amount)
                put("category", tx.category)
                put("date", tx.date)
                put("assetName", tx.assetName)
                put("isExpense", tx.isExpense)
            })
        }
        root.put("transactions", transactionsArray)

        // Assets
        val assetsArray = JSONArray()
        dao.getAllAssetsList().forEach { asset ->
            assetsArray.put(JSONObject().apply {
                put("id", asset.id)
                put("name", asset.name)
                put("amount", asset.amount)
                put("category", asset.category)
                put("lastUpdated", asset.lastUpdated)
            })
        }
        root.put("assets", assetsArray)

        // Budgets
        val budgetsArray = JSONArray()
        dao.getAllBudgetsList().forEach { budget ->
            budgetsArray.put(JSONObject().apply {
                put("category", budget.category)
                put("monthlyAmount", budget.monthlyAmount)
            })
        }
        root.put("budgets", budgetsArray)

        // Lendings
        val lendingsArray = JSONArray()
        dao.getAllLendingsList().forEach { l ->
            lendingsArray.put(JSONObject().apply {
                put("id", l.id)
                put("personName", l.personName)
                put("amount", l.amount)
                put("loanAsset", l.loanAsset)
                put("memo", l.memo)
                put("date", l.date)
                put("isRecovered", l.isRecovered)
                put("returnAsset", l.returnAsset ?: JSONObject.NULL)
                put("recoveredAmount", l.recoveredAmount)
                put("recoveredDate", l.recoveredDate ?: JSONObject.NULL)
            })
        }
        root.put("lendings", lendingsArray)

        // Goal Setting
        dao.getGoalSettingSync()?.let { g ->
            root.put("goalSetting", JSONObject().apply {
                put("id", g.id)
                put("title", g.title)
                put("targetAmount", g.targetAmount)
                put("monthlyIncome", g.monthlyIncome)
                put("targetDateMillis", g.targetDateMillis)
                put("showResults", g.showResults)
                put("startDateMillis", g.startDateMillis)
            })
        }

        // Chat Sessions
        val sessionsArray = JSONArray()
        dao.getAllChatSessionsList().forEach { s ->
            sessionsArray.put(JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("lastMessageAt", s.lastMessageAt)
            })
        }
        root.put("chatSessions", sessionsArray)

        // Chat Messages
        val messagesArray = JSONArray()
        dao.getAllChatMessagesList().forEach { m ->
            messagesArray.put(JSONObject().apply {
                put("id", m.id)
                put("sessionId", m.sessionId)
                put("text", m.text)
                put("isUser", m.isUser)
                put("timestamp", m.timestamp)
            })
        }
        root.put("chatMessages", messagesArray)

        root.put("exportDate", System.currentTimeMillis())

        val jsonString = root.toString()
        return CryptoUtils.encryptData(jsonString, password)
    }

    suspend fun importData(encryptedData: String, password: String) {
        val jsonString = CryptoUtils.decryptData(encryptedData, password)
        val root = JSONObject(jsonString)

        val exportDate = root.optLong("exportDate", 0L)
        val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
        if (exportDate != 0L && exportDate < fortyEightHoursAgo) {
            throw Exception("このバックアップファイルは作成から48時間が経過しているため無効です。")
        }

        // 全データを削除してからインポート
        dao.deleteAllTransactions()
        dao.deleteAllAssets()
        dao.deleteAllBudgets()
        dao.deleteAllLendings()
        dao.deleteAllChatSessions()
        dao.deleteAllChatMessages()
        dao.deleteAllGoalSettings()

        // Transactions
        root.optJSONArray("transactions")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                dao.insertTransaction(TransactionEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    amount = obj.getInt("amount"),
                    category = obj.getString("category"),
                    date = obj.getLong("date"),
                    assetName = obj.getString("assetName"),
                    isExpense = obj.optBoolean("isExpense", true)
                ))
            }
        }

        // Assets
        root.optJSONArray("assets")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                dao.insertAsset(AssetEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    amount = obj.getInt("amount"),
                    category = obj.getString("category"),
                    lastUpdated = obj.getLong("lastUpdated")
                ))
            }
        }

        // Budgets
        root.optJSONArray("budgets")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                dao.insertBudget(BudgetEntity(
                    category = obj.getString("category"),
                    monthlyAmount = obj.getInt("monthlyAmount")
                ))
            }
        }

        // Lendings
        root.optJSONArray("lendings")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                dao.insertLending(LendingEntity(
                    id = obj.getString("id"),
                    personName = obj.getString("personName"),
                    amount = obj.getInt("amount"),
                    loanAsset = obj.getString("loanAsset"),
                    memo = obj.getString("memo"),
                    date = obj.getLong("date"),
                    isRecovered = obj.optBoolean("isRecovered", false),
                    returnAsset = if (obj.isNull("returnAsset")) null else obj.getString("returnAsset"),
                    recoveredAmount = obj.optInt("recoveredAmount", 0),
                    recoveredDate = if (obj.isNull("recoveredDate")) null else obj.getLong("recoveredDate")
                ))
            }
        }

        // Goal Setting
        root.optJSONObject("goalSetting")?.let { obj ->
            dao.upsertGoalSetting(GoalSettingEntity(
                id = obj.optInt("id", 1),
                title = obj.optString("title", ""),
                targetAmount = obj.optLong("targetAmount", 0L),
                monthlyIncome = obj.optLong("monthlyIncome", 0L),
                targetDateMillis = obj.optLong("targetDateMillis", 0L),
                showResults = obj.optBoolean("showResults", false),
                startDateMillis = obj.optLong("startDateMillis", 0L)
            ))
        }

        // Chat Sessions
        root.optJSONArray("chatSessions")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                dao.upsertChatSession(ChatSessionEntity(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    lastMessageAt = obj.getLong("lastMessageAt")
                ))
            }
        }

        // Chat Messages
        root.optJSONArray("chatMessages")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                dao.insertChatMessage(ChatMessageEntity(
                    id = obj.getString("id"),
                    sessionId = obj.getString("sessionId"),
                    text = obj.getString("text"),
                    isUser = obj.getBoolean("isUser"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
        }
    }
}
