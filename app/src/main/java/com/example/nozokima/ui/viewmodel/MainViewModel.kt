package com.example.nozokima.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nozokima.data.local.FinanceDao
import com.example.nozokima.data.local.entities.AppSettingsEntity
import com.example.nozokima.data.local.entities.ChatSessionEntity
import com.example.nozokima.data.manager.GeminiNanoModel
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val appSettings: AppSettingsEntity? = null,
    val chatSessions: List<ChatSessionEntity> = emptyList(),
    val aiStatus: Int = 0,
    val aiIsReady: Boolean = false,
    val aiIsGenerating: Boolean = false
)

class MainViewModel(
    private val dao: FinanceDao,
    private val gemini: GeminiNanoModel
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = combine(
        dao.getAppSettings(),
        dao.getAllChatSessions(),
        gemini.status,
        gemini.isReady,
        gemini.isGenerating
    ) { params ->
        val settings = params[0] as AppSettingsEntity?
        val sessions = params[1] as List<ChatSessionEntity>
        val status = params[2] as Int
        val ready = params[3] as Boolean
        val generating = params[4] as Boolean

        MainUiState(
            appSettings = settings,
            chatSessions = sessions,
            aiStatus = status,
            aiIsReady = ready,
            aiIsGenerating = generating
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun startAiDownload() {
        viewModelScope.launch {
            gemini.startDownload()
        }
    }

    fun checkAiStatus() {
        viewModelScope.launch {
            gemini.checkModelStatus()
        }
    }
}
