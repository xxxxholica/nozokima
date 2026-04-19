package com.example.nozokima.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.nozokima.data.local.FinanceDao
import com.example.nozokima.data.manager.GeminiNanoModel

class ViewModelFactory(
    private val dao: FinanceDao,
    private val gemini: GeminiNanoModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(dao, gemini) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(dao, gemini) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
