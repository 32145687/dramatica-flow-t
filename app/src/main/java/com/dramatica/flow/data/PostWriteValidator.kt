package com.dramatica.flow.data

object PostWriteValidator {

    private val AI_MARKER_WORDS = listOf(
        "仿佛", "忽然", "竟然", "不禁", "宛如",
        "猛地", "顿时", "霎时", "不由得"
    )

    private val FORBIDDEN_PHRASES = listOf(
        "不是……而是……",
        "全场震惊",
        "众人哗然",
        "所有人都",
        "不言而喻"
    )

    private val META_NARRATIVE_PATTERNS = listOf(
        Pair(Regex("核心动机"), "元叙事"),
        Pair(Regex("信息落差"), "元叙事"),
        Pair(Regex("叙事节奏"), "元叙事"),
        Pair(Regex("情节推进"), "元叙事"),
        Pair(Regex("人物弧线"), "元叙事"),
        Pair(Regex("显然[，,。]"), "作者说教"),
        Pair(Regex("毫无疑问"), "作者说教")
    )

    private val REPORT_STYLE_PATTERNS = listOf(
        Regex("分析了.*?(?:情况|局势|形势)"),
        Regex("从.*?(?:角度|层面)(?:来|而言|看)"),
        Regex("综合考虑")
    )

    private val COLLECTIVE_PATTERNS = listOf(
        Regex("(?:在场|全场)(?:之人|人|众人)(?:皆|都|全)"),
        Regex("(?:众人|所有人)(?:齐声|异口同声)"),
        Regex("一时间.*?(?:哗然|震动|沸腾)")
    )

    fun validate(content: String, targetWords: Int): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val wordCount = content.length

        checkAiMarkerDensity(content, wordCount, issues)
        checkForbiddenPhrases(content, issues)
        checkMetaNarrative(content, issues)
        checkReportStyle(content, issues)
        checkCollectiveReaction(content, issues)
        checkConsecutiveLe(content, issues)
        checkLongParagraphs(content, issues)
        checkWordCountDeviation(wordCount, targetWords, issues)

        val hasError = issues.any { it.severity == "error" }
        return ValidationResult(
            passed = !hasError,
            issues = issues,
            wordCount = wordCount
        )
    }

    fun validateForMimo(content: String): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val wordCount = content.length

        checkAdjectiveDensity(content, issues)
        checkDescriptionRatio(content, issues)
        checkConsecutiveLongSentences(content, issues)

        val hasWarning = issues.any { it.severity == "warning" || it.severity == "error" }
        return ValidationResult(
            passed = !hasWarning,
            issues = issues,
            wordCount = wordCount
        )
    }

    private fun checkAiMarkerDensity(content: String, wordCount: Int, issues: MutableList<ValidationIssue>) {
        if (wordCount == 0) return
        for (word in AI_MARKER_WORDS) {
            val count = Regex(word).findAll(content).count()
            if (count == 0) continue
            val per3000 = (count.toFloat() / wordCount) * 3000
            if (per3000 > 1) {
                issues.add(ValidationIssue(
                    rule = "AI_MARKER_DENSITY",
                    severity = "warning",
                    description = "「$word」出现 $count 次（每3000字 ${String.format("%.1f", per3000)} 次，上限 1 次）",
                    excerpt = word
                ))
            }
        }
    }

    private fun checkForbiddenPhrases(content: String, issues: MutableList<ValidationIssue>) {
        for (phrase in FORBIDDEN_PHRASES) {
            if (content.contains(phrase)) {
                issues.add(ValidationIssue(
                    rule = "FORBIDDEN_PHRASE",
                    severity = "error",
                    description = "禁止句式：「$phrase」",
                    excerpt = phrase
                ))
            }
        }
    }

    private fun checkMetaNarrative(content: String, issues: MutableList<ValidationIssue>) {
        for ((pattern, label) in META_NARRATIVE_PATTERNS) {
            val matches = pattern.findAll(content).toList()
            if (matches.isNotEmpty()) {
                issues.add(ValidationIssue(
                    rule = "META_NARRATIVE",
                    severity = "warning",
                    description = "$label：「${matches.first().value}」（共 ${matches.size} 处）",
                    excerpt = matches.first().value
                ))
            }
        }
    }

    private fun checkReportStyle(content: String, issues: MutableList<ValidationIssue>) {
        for (pattern in REPORT_STYLE_PATTERNS) {
            val matches = pattern.findAll(content).toList()
            if (matches.isNotEmpty()) {
                issues.add(ValidationIssue(
                    rule = "REPORT_STYLE",
                    severity = "warning",
                    description = "报告式语言：「${matches.first().value}」",
                    excerpt = matches.first().value
                ))
            }
        }
    }

    private fun checkCollectiveReaction(content: String, issues: MutableList<ValidationIssue>) {
        for (pattern in COLLECTIVE_PATTERNS) {
            val matches = pattern.findAll(content).toList()
            if (matches.isNotEmpty()) {
                issues.add(ValidationIssue(
                    rule = "COLLECTIVE_REACTION",
                    severity = "warning",
                    description = "集体反应套话：「${matches.first().value}」",
                    excerpt = matches.first().value
                ))
            }
        }
    }

    private fun checkConsecutiveLe(content: String, issues: MutableList<ValidationIssue>) {
        val sentences = content.split(Regex("[。！？!?]"))
        var maxConsecutive = 0
        var consecutive = 0
        for (s in sentences) {
            if (s.contains("了")) {
                consecutive++
                maxConsecutive = maxOf(maxConsecutive, consecutive)
            } else {
                consecutive = 0
            }
        }
        if (maxConsecutive >= 6) {
            issues.add(ValidationIssue(
                rule = "CONSECUTIVE_LE",
                severity = "warning",
                description = "连续 $maxConsecutive 句含「了」字（上限 6 句）"
            ))
        }
    }

    private fun checkLongParagraphs(content: String, issues: MutableList<ValidationIssue>) {
        val paragraphs = content.split(Regex("\n{2,}")).filter { it.isNotBlank() }
        val longParagraphs = paragraphs.filter { it.length > 300 }
        if (longParagraphs.size >= 2) {
            issues.add(ValidationIssue(
                rule = "LONG_PARAGRAPH",
                severity = "warning",
                description = "${longParagraphs.size} 个段落超过 300 字"
            ))
        }
    }

    private fun checkWordCountDeviation(wordCount: Int, targetWords: Int, issues: MutableList<ValidationIssue>) {
        if (targetWords > 0) {
            val deviation = kotlin.math.abs(wordCount - targetWords).toFloat() / targetWords
            if (deviation > 0.2f) {
                issues.add(ValidationIssue(
                    rule = "WORD_COUNT_DEVIATION",
                    severity = "warning",
                    description = "实际 $wordCount 字，目标 $targetWords 字，偏差 ${String.format("%.0f", deviation * 100)}%（上限 20%）"
                ))
            }
        }
    }

    private fun checkAdjectiveDensity(content: String, issues: MutableList<ValidationIssue>) {
        val sentences = content.split(Regex("[。！？]")).filter { it.length > 20 }
        val highAdjSentences = sentences.filter { sentence ->
            val adjCount = Regex("[的得地]").findAll(sentence).count()
            adjCount > 2
        }
        if (highAdjSentences.size > 2) {
            issues.add(ValidationIssue(
                rule = "HIGH_ADJECTIVE_DENSITY",
                severity = "warning",
                description = "${highAdjSentences.size}个句子形容词过多（>2个的/得/地）"
            ))
        }
    }

    private fun checkDescriptionRatio(content: String, issues: MutableList<ValidationIssue>) {
        val paragraphs = content.split(Regex("\n{2,}")).filter { it.isNotBlank() }
        val overDescriptive = paragraphs.filter { para ->
            val dialogueCount = Regex("[\u300c\u300d\u201c\u201d\u0022\u0027]").findAll(para).count()
            dialogueCount == 0 && para.length > 100
        }
        if (overDescriptive.size >= 1) {
            issues.add(ValidationIssue(
                rule = "OVER_DESCRIPTIVE",
                severity = "warning",
                description = "${overDescriptive.size}个段落（>100字）完全没有对话"
            ))
        }
    }

    private fun checkConsecutiveLongSentences(content: String, issues: MutableList<ValidationIssue>) {
        val sentences = content.split(Regex("[。！？]")).filter { it.isNotBlank() }
        var maxConsecutive = 0
        var consecutive = 0
        for (s in sentences) {
            if (s.length > 50) {
                consecutive++
                maxConsecutive = maxOf(maxConsecutive, consecutive)
            } else {
                consecutive = 0
            }
        }
        if (maxConsecutive >= 2) {
            issues.add(ValidationIssue(
                rule = "CONSECUTIVE_LONG_SENTENCES",
                severity = "warning",
                description = "连续 $maxConsecutive 个长句（>50字），描写过多"
            ))
        }
    }
}

data class ValidationResult(
    val passed: Boolean,
    val issues: List<ValidationIssue>,
    val wordCount: Int
) {
    val errorCount: Int get() = issues.count { it.severity == "error" }
    val warningCount: Int get() = issues.count { it.severity == "warning" }
}

data class ValidationIssue(
    val rule: String,
    val severity: String,
    val description: String,
    val excerpt: String? = null
)
