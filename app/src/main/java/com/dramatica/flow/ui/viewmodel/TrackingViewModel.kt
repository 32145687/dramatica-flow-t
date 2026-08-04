package com.dramatica.flow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dramatica.flow.data.LocalRepository
import com.dramatica.flow.data.model.CausalLink
import com.dramatica.flow.data.model.EmotionalArc
import com.dramatica.flow.data.model.Hook
import com.dramatica.flow.data.model.Relationship
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val repository: LocalRepository = LocalRepository.getInstance()
) : ViewModel() {

    // 伏笔数据
    private val _hooks = MutableStateFlow<List<Hook>>(emptyList())
    val hooks: StateFlow<List<Hook>> = _hooks.asStateFlow()

    // 因果链数据
    private val _causalChain = MutableStateFlow<List<CausalLink>>(emptyList())
    val causalChain: StateFlow<List<CausalLink>> = _causalChain.asStateFlow()

    // 关系网络数据
    private val _relationships = MutableStateFlow<List<Relationship>>(emptyList())
    val relationships: StateFlow<List<Relationship>> = _relationships.asStateFlow()

    // 情感弧线数据
    private val _emotions = MutableStateFlow<List<EmotionalArc>>(emptyList())
    val emotions: StateFlow<List<EmotionalArc>> = _emotions.asStateFlow()

    fun loadHooks(bookId: String) {
        viewModelScope.launch {
            repository.getHooks(bookId).collect { entities ->
                _hooks.value = entities.map { entity ->
                    Hook(
                        id = entity.id,
                        type = entity.type,
                        description = entity.description,
                        setupChapter = entity.plantedChapter,
                        resolvedChapter = entity.resolvedChapter,
                        status = entity.status,
                        relatedCharacters = if (entity.relatedCharacters.isNotBlank()) {
                            entity.relatedCharacters.split(",").map { it.trim() }
                        } else emptyList()
                    )
                }
            }
        }
    }

    fun loadCausalChain(bookId: String) {
        viewModelScope.launch {
            repository.getCausalChain(bookId).collect { entities ->
                _causalChain.value = entities.map { entity ->
                    CausalLink(
                        chapter = entity.chapter,
                        cause = entity.cause,
                        event = entity.event,
                        consequence = entity.consequence,
                        decision = entity.decision
                    )
                }
            }
        }
    }

    fun loadRelationships(bookId: String) {
        viewModelScope.launch {
            repository.getRelationships(bookId).collect { entities ->
                _relationships.value = entities.map { entity ->
                    Relationship(
                        characterA = entity.characterA,
                        characterB = entity.characterB,
                        type = entity.type,
                        strength = entity.strength,
                        reason = entity.reason
                    )
                }
            }
        }
    }

    fun loadEmotions(bookId: String) {
        viewModelScope.launch {
            repository.getEmotions(bookId).collect { entities ->
                _emotions.value = entities.map { entity ->
                    EmotionalArc(
                        characterId = entity.characterId,
                        emotion = entity.emotion,
                        intensity = entity.intensity,
                        chapter = entity.chapter,
                        trigger = entity.trigger
                    )
                }
            }
        }
    }
}
