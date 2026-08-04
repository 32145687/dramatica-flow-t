package com.dramatica.flow.data

/**
 * 建筑师 Agent：在写手开始写作前，独立调用 AI 规划章节蓝图。
 * 
 * 对应 GitHub 项目 dramatica-flow 的 ArchitectAgent.plan_chapter()。
 * 
 * 蓝图包含：
 * - 场景结构（开场/发展/高潮/收尾）
 * - 关键节拍（每节拍的事件+情感目标）
 * - 伏笔建议（本章应埋设/推进的伏笔）
 * - 因果链衔接（与前章的因果连接点）
 * - 角色调度（本章出场角色及其作用）
 */
object ArchitectAgent {

    /**
     * 建筑师规划结果
     */
    data class Blueprint(
        val sceneStructure: String,       // 场景结构描述
        val keyBeats: String,             // 关键节拍
        val hookStrategy: String,         // 伏笔策略
        val causalBridge: String,         // 因果衔接
        val characterSchedule: String,    // 角色调度
        val emotionalArc: String,         // 本章情感弧线
        val wordBudget: String,           // 字数分配建议
        val rawText: String               // AI 原始输出（备用）
    )

    /**
     * 构建建筑师规划 prompt。
     * 输入：章节上下文、前情摘要、世界状态、角色信息
     * 输出：结构化的章节蓝图
     */
    fun buildBlueprintPrompt(
        title: String,
        genre: String,
        chapterNum: Int,
        chapterTitle: String,
        coreSetting: String,
        characters: String,
        summaryHistory: String,
        prevChapterEnding: String,
        causalChain: String,
        pendingHooks: String,
        referenceAnalysis: String = "",
        writingSkill: String = "",
        targetWords: Int = 2000
    ): String = buildString {
        append("你是一位资深小说架构师。请为以下章节规划详细的写作蓝图。\n\n")
        append("小说：《$title》 类型：$genre\n")
        append("章节：第${chapterNum}章${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}\n")
        append("目标字数：约${targetWords}字\n\n")

        if (writingSkill.isNotBlank()) {
            append("【写作风格要求】\n$writingSkill\n\n")
        }
        if (referenceAnalysis.isNotBlank()) {
            append("【参考小说分析】\n${referenceAnalysis.take(1000)}\n\n")
        }

        append("【世界观】\n${coreSetting.take(500)}\n\n")
        append("【角色设定】\n${characters.take(500)}\n\n")

        if (summaryHistory.isNotBlank()) {
            append("【前情摘要】\n${summaryHistory.take(3000)}\n\n")
        }
        if (prevChapterEnding.isNotBlank()) {
            append("【上一章结尾（必须衔接）】\n${prevChapterEnding.takeLast(500)}\n\n")
        }
        if (causalChain.isNotBlank()) {
            append("【已有因果链】\n${causalChain.take(1500)}\n\n")
        }
        if (pendingHooks.isNotBlank()) {
            append("【待回收/推进的伏笔】\n${pendingHooks.take(1000)}\n\n")
        }

        append("【规划要求——请输出以下6个部分】\n\n")
        append("1. 场景结构（scene_structure）\n")
        append("   - 开场：场景切入方式、开场动作/对话、字数（150-300字）\n")
        append("   - 发展：2-3个推进事件、对话与叙述交替、字数（800-1000字）\n")
        append("   - 高潮：冲突爆发或转折点、节奏加快、字数（300-400字）\n")
        append("   - 收尾：悬念或情感余韵、为下一章铺垫、字数（150-200字）\n\n")
        append("2. 关键节拍（key_beats）\n")
        append("   - 列出3-5个关键事件，每个标注：事件描述、涉及角色、情感目标\n")
        append("   - 示例：「主角发现密室中的信件 → 涉及：主角、管家 → 情感：震惊→怀疑」\n\n")
        append("3. 伏笔策略（hook_strategy）\n")
        append("   - 本章应推进哪些已有伏笔？\n")
        append("   - 本章应埋设哪些新伏笔？（1-2个）\n")
        append("   - 是否有伏笔可以回收？\n\n")
        append("4. 因果衔接（causal_bridge）\n")
        append("   - 本章事件如何承接前章因果链？\n")
        append("   - 本章将产生什么新的因果链？\n")
        append("   - 标注：因→事→果→决\n\n")
        append("5. 角色调度（character_schedule）\n")
        append("   - 本章出场角色列表（优先级排序）\n")
        append("   - 每个角色的本章作用（推动剧情/揭示信息/情感变化/冲突制造）\n")
        append("   - 是否有新角色出场？\n\n")
        append("6. 情感弧线（emotional_arc）\n")
        append("   - 主角本章情感变化轨迹（起始→转折→终点）\n")
        append("   - 关键配角的情感变化\n\n")
        append("7. 字数分配（word_budget）\n")
        append("   - 各场景段落的字数建议\n\n")
        append("请直接输出规划内容，用以上6个标签分隔，简洁务实，不要过度发挥。")
    }

    /**
     * 将建筑师蓝图注入到写手 prompt 中。
     * 在原有的写作 prompt 前添加蓝图指导。
     */
    fun injectBlueprintIntoPrompt(
        originalPrompt: String,
        blueprint: Blueprint
    ): String = buildString {
        append("【建筑师规划蓝图——请严格遵循以下结构创作】\n\n")
        append("=== 场景结构 ===\n${blueprint.sceneStructure}\n\n")
        if (blueprint.keyBeats.isNotBlank()) {
            append("=== 关键节拍 ===\n${blueprint.keyBeats}\n\n")
        }
        if (blueprint.hookStrategy.isNotBlank()) {
            append("=== 伏笔策略 ===\n${blueprint.hookStrategy}\n\n")
        }
        if (blueprint.causalBridge.isNotBlank()) {
            append("=== 因果衔接 ===\n${blueprint.causalBridge}\n\n")
        }
        if (blueprint.characterSchedule.isNotBlank()) {
            append("=== 角色调度 ===\n${blueprint.characterSchedule}\n\n")
        }
        if (blueprint.emotionalArc.isNotBlank()) {
            append("=== 情感弧线 ===\n${blueprint.emotionalArc}\n\n")
        }
        if (blueprint.wordBudget.isNotBlank()) {
            append("=== 字数分配 ===\n${blueprint.wordBudget}\n\n")
        }
        append("---\n\n")
        append(originalPrompt)
    }

    /**
     * 解析 AI 返回的蓝图文本，提取结构化字段。
     */
    fun parseBlueprint(rawText: String): Blueprint {
        val sceneStructure = extractSection(rawText, "场景结构", "scene_structure")
        val keyBeats = extractSection(rawText, "关键节拍", "key_beats")
        val hookStrategy = extractSection(rawText, "伏笔策略", "hook_strategy")
        val causalBridge = extractSection(rawText, "因果衔接", "causal_bridge")
        val characterSchedule = extractSection(rawText, "角色调度", "character_schedule")
        val emotionalArc = extractSection(rawText, "情感弧线", "emotional_arc")
        val wordBudget = extractSection(rawText, "字数分配", "word_budget")

        return Blueprint(
            sceneStructure = sceneStructure.ifBlank { "标准四段式：开场→发展→高潮→收尾" },
            keyBeats = keyBeats,
            hookStrategy = hookStrategy,
            causalBridge = causalBridge,
            characterSchedule = characterSchedule,
            emotionalArc = emotionalArc,
            wordBudget = wordBudget,
            rawText = rawText
        )
    }

    /**
     * 从 AI 输出中提取指定章节的内容。
     * 支持中文标签（如"场景结构"）和英文标签（如"scene_structure"）。
     */
    private fun extractSection(text: String, vararg labels: String): String {
        for (label in labels) {
            // 匹配 "1. 场景结构" 或 "### 场景结构" 或 "场景结构：" 等格式
            val pattern = Regex(
                """(?:^|\n)\s*(?:\d+\.\s*|#{1,4}\s*)?${Regex.escape(label)}[\s：:]*\n?(.*?)(?=\n\s*(?:\d+\.\s*|#{1,4}\s*)?(?:${labels.filter { it != label }.joinToString("|") { Regex.escape(it) }}|$)|\z)""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim().take(2000)
            }
        }
        // 回退：尝试按数字序号分割
        val parts = text.split(Regex("""\n\s*\d+\.\s*"""))
        if (parts.size >= 2) {
            // 找最接近的段落
            for (i in 1 until parts.size) {
                val part = parts[i]
                for (label in labels) {
                    if (part.contains(label, ignoreCase = true)) {
                        return part.replaceFirst(Regex("""^.*?${Regex.escape(label)}[\s：:]*""", RegexOption.IGNORE_CASE), "").trim().take(2000)
                    }
                }
            }
        }
        return ""
    }
}