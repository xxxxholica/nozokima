package works.hinata.nozokima.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import works.hinata.nozokima.data.local.FinanceDao
import works.hinata.nozokima.data.local.entities.AppSettingsEntity
import works.hinata.nozokima.data.local.entities.ChatSessionEntity
import works.hinata.nozokima.data.manager.GeminiNanoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val appSettings: AppSettingsEntity? = null,
    val chatSessions: List<ChatSessionEntity> = emptyList(),
    val aiStatus: Int = 0,
    val aiIsReady: Boolean = false,
    val aiIsGenerating: Boolean = false,
    val isLoaded: Boolean = false,
)

class MainViewModel(
    dao: FinanceDao,
    private val gemini: GeminiNanoModel,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = combine(
        dao.getAppSettings(),
        dao.getAllChatSessions(),
        gemini.status,
        gemini.isReady,
        gemini.isGenerating
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val settings = params[0] as AppSettingsEntity?
        @Suppress("UNCHECKED_CAST")
        val sessions = params[1] as List<ChatSessionEntity>
        val status = params[2] as Int
        val ready = params[3] as Boolean
        val generating = params[4] as Boolean

        MainUiState(
            appSettings = settings,
            chatSessions = sessions,
            aiStatus = status,
            aiIsReady = ready,
            aiIsGenerating = generating,
            isLoaded = true
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun checkAiStatus() {
        viewModelScope.launch {
            gemini.checkModelStatus()
        }
    }
}
