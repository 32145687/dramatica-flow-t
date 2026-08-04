package com.dramatica.flow.data.model

data class Hook(
    val id: String = "",
    val type: String = "foreshadow",
    val description: String = "",
    val setupChapter: Int = 0,
    val resolvedChapter: Int? = null,
    val status: String = "open",
    val expectedResolveChapter: Int? = null,
    val relatedCharacters: List<String> = emptyList(),
    val notes: String = ""
)

data class CausalLink(
    val chapter: Int = 0,
    val cause: String = "",
    val event: String = "",
    val consequence: String = "",
    val decision: String = ""
)

data class Relationship(
    val characterA: String = "",
    val characterB: String = "",
    val type: String = "neutral",
    val strength: Int = 0,
    val reason: String = "",
    val lastChange: String = ""
)

data class EmotionalArc(
    val characterId: String = "",
    val characterName: String = "",
    val emotion: String = "",
    val intensity: Int = 0,
    val chapter: Int = 0,
    val trigger: String = ""
)
