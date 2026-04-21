package com.example.nozokima

import android.app.Application
import androidx.room.Room

class NozokimaApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "nozokima-db"
        ).fallbackToDestructiveMigration().build()
    }

    val gemmaModel by lazy { GemmaModel(this) }
}
