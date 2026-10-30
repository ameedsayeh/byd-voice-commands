package com.offline.bydvoice.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { IDLE, LOADING, LISTENING, PROCESSING, SUCCESS, ERROR }

data class VoiceUiState(
    val phase: Phase = Phase.IDLE,
    /** What the recognizer is hearing right now (free-form). */
    val transcript: String = "",
    /** Mic level 0..1 for animations. */
    val level: Float = 0f,
    /** Spoken/shown reply for the last command. */
    val reply: String? = null,
    val commandTitle: String? = null,
    /** True when the mic delivers pure digital silence (permission / routing problem). */
    val micSilent: Boolean = false,
    val serviceRunning: Boolean = false,
    val modelReady: Boolean = false,
)

data class HistoryItem(
    val time: Long,
    val heard: String,
    val commandTitle: String?,
    val reply: String,
    val ok: Boolean,
)

/** Process-wide live state shared by the service, the overlay and the UI. */
object VoiceBus {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    fun update(block: (VoiceUiState) -> VoiceUiState) = _state.update(block)

    fun log(item: HistoryItem) = _history.update { (listOf(item) + it).take(30) }
}
