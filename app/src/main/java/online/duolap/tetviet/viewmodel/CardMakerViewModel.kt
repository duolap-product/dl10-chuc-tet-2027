package online.duolap.tetviet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.duolap.tetviet.data.model.CardBackground
import online.duolap.tetviet.data.model.CardBackgroundManager
import online.duolap.tetviet.data.model.CardColorManager
import online.duolap.tetviet.data.model.CardMakerDefaults
import online.duolap.tetviet.data.model.CardStickerItem
import online.duolap.tetviet.data.model.CardStickerManager
import online.duolap.tetviet.data.repository.CardMakerRepository
import javax.inject.Inject

@HiltViewModel
class CardMakerViewModel @Inject constructor(
    private val repository: CardMakerRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val backgrounds: StateFlow<List<CardBackground>> = repository.backgroundsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList() // CardBackgroundManager.BACKGROUND
        )

    val stickers: StateFlow<List<CardStickerItem>> = repository.stickersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CardStickerManager.STICKERS
        )

    val colors: StateFlow<List<Pair<String, String>>> = repository.colorsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CardColorManager.COLORS.map { Pair(it.name, it.colorHex) }
        )

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.initializeData(forceRefresh)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

