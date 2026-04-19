package com.example.nozokima

import android.app.Application
import androidx.room.Room
import com.example.nozokima.data.local.AppDatabase
import com.example.nozokima.data.local.entities.AssetEntity
import com.example.nozokima.data.local.entities.CategoryEntity
import com.example.nozokima.data.manager.GeminiNanoModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class NozokimaApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "nozokima-db",
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    val geminiModel by lazy { GeminiNanoModel() }

    override fun onCreate() {
        super.onCreate()
        initializeDefaultData()
    }

    private fun initializeDefaultData() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val dao = database.financeDao()

            // Initialize Assets if empty
            val assets = dao.getAllAssetsList()
            if (assets.isEmpty()) {
                val defaultAssets = listOf(
                    AssetEntity(UUID.randomUUID().toString(), "現金", 0, "現金", System.currentTimeMillis()),
                    AssetEntity(UUID.randomUUID().toString(), "銀行", 0, "銀行", System.currentTimeMillis()),
                    AssetEntity(UUID.randomUUID().toString(), "電子マネー", 0, "電子マネー", System.currentTimeMillis()),
                    AssetEntity(UUID.randomUUID().toString(), "カード", 0, "カード", System.currentTimeMillis())
                )
                defaultAssets.forEach { dao.insertAsset(it) }
            }

            // Initialize Categories if empty
            if (dao.getCategoryCount() == 0) {
                val expenseCategories = listOf(
                    "食費" to "ShoppingCart", "日用品" to "Build", "交通費" to "Place",
                    "交際費" to "Favorite", "娯楽" to "Star", "美容" to "Face",
                    "健康" to "MedicalServices", "その他" to "MoreHoriz"
                )
                expenseCategories.forEachIndexed { index, (name, icon) ->
                    dao.insertCategory(CategoryEntity(UUID.randomUUID().toString(), name, "EXPENSE", icon, true, index))
                }

                val incomeCategories = listOf(
                    "給与" to "AccountBalance", "賞与" to "Star", "副業" to "Build",
                    "お小遣い" to "Favorite", "還付金" to "Info"
                )
                incomeCategories.forEachIndexed { index, (name, icon) ->
                    dao.insertCategory(CategoryEntity(UUID.randomUUID().toString(), name, "INCOME", icon, true, index))
                }
            }
        }
    }
}
