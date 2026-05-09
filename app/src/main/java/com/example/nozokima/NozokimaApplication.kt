package com.example.nozokima

import android.app.Application
import androidx.room.Room
import com.example.nozokima.data.local.AppDatabase
import com.example.nozokima.data.manager.GeminiNanoModel

class NozokimaApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "nozokima-db"
        ).fallbackToDestructiveMigration().build()
    }

    val geminiModel by lazy { GeminiNanoModel(this) }
}
