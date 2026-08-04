package com.dramatica.flow.data

/**
 * 写作后整改管线
 * 在 AI 写作完成后，以"编辑"身份审视内容，自动修正问题并拟人化润色
 */
object ContentRectifier {

    /**
     * 构建整改 prompt
     * @param content 原始内容
     * @param title 小说标题
     * @param genre 小说类型
     * @param chapterNum 章节号
     * @param chapterTitle 章节标题
     */
    fun buildRectifyPrompt(
        content: String,
        title: String,
        genre: String,
        chapterNum: Int,
        chapterTitle: String,
        colloquialStyle: Boolean = false,
        useMemes: Boolean = false
    ): String = buildString {
        append("你是一位资深小说编辑，请对以下章节内容进行专业整改。\n\n")
        append("小说：《$title》 类型：$genre\n")
        append("章节：第${chapterNum}章 ${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}\n\n")
        append("【整改要求——按优先级执行】\n\n")
        append("=== 第一优先级：逻辑与一致 ===\n")
        append("1. 检查前后矛盾：同一段内角色位置、时间、道具不能矛盾\n")
        append("2. 检查角色名字一致性：所有角色名必须与原文一致，不能换名\n")
        append("3. 修复不通顺的句子：超过50字的长句拆分，语序混乱的重组\n\n")
        append("=== 第二优先级：去AI味 ===\n")
        append("4. 删除AI套话残留：仿佛、忽然、竟然、不禁、宛如、猛地、顿时、霎时、在这个瞬间\n")
        append("5. 删除报告式语言：\"他知道……\"\"她意识到……\"\"从某种意义上说……\"\"这标志着……\"\n")
        append("6. 删除流水账：\"先是……然后……接着……最后……\"改为有详有略\n")
        append("7. 删除概括性总结段（\"总的来说\"\"总而言之\"\"由此可见\"开头的段落）\n\n")
        append("=== 第三优先级：拟人化增强 ===\n")
        append("8. 句子节奏改造：每段至少含1句极短句（3-7字）和1句中长句，打破匀速\n")
        append("9. 对话改造：加入1-2句\"废话\"（\"嗯\"\"行了行了\"\"怎么又是你\"等），让对话更真实\n")
        append("10. 心理描写改造：把\"他感到XXX\"改为动作暗示，把\"他心想XXX\"改为碎碎念式独白\n")
        append("11. 感官锚点改造：空白环境描写加入角色感官（\"风吹过来，有股腥味\"而非\"一阵风吹过\"）\n")
        if (colloquialStyle) {
            append("12. 口语化改造：将书面语改为大白话，对话中加入语气词（嘛、呗、哈、啦、呀）\n")
        }
        if (useMemes) {
            append("13. 玩梗检查：确保梗融入自然，删除突兀插入的梗，删除括号解释\n")
        }
        append("\n【整改原则】\n")
        append("- 只改有问题的地方，不要重写整章，保持原文风格\n")
        append("- 每处修改都应该有明确的理由\n")
        append("- 不要改变情节走向和角色性格\n")
        append("- 直接输出整改后的完整章节内容，不要加任何解释或标注\n\n")
        append("【原文】\n$content")
    }

    /**
     * 构建选中文字重写 prompt
     * @param selectedText 用户选中的文字
     * @param fullContext 完整上下文（前后各200字）
     * @param instruction 用户修改指令（可选）
     */
    fun buildSelectedRewritePrompt(
        selectedText: String,
        fullContext: String,
        instruction: String = ""
    ): String = buildString {
        append("请重写以下小说片段中选中的文字。\n\n")
        append("【上下文】\n${fullContext.take(800)}\n\n")
        append("【选中文字（需要重写的部分）】\n$selectedText\n\n")
        append("【重写要求】\n")
        if (instruction.isNotBlank()) {
            append("用户指令：$instruction\n")
        } else {
            append("使表达更自然、更有画面感，消除AI痕迹\n")
        }
        append("1. 保持原文意思和情节不变\n")
        append("2. 与上下文风格一致，衔接自然\n")
        append("3. 用拟人化写作方式：短句、动作描写、对话推进\n")
        append("4. 禁止AI套话：仿佛、忽然、竟然、不禁、宛如、猛地、顿时、霎时\n")
        append("5. 只输出重写后的文字，不要加任何解释或标注\n")
    }

    /**
     * 重复叙述检测：检查是否有连续重复的句子/段落
     */
    fun detectRepetition(content: String): List<String> {
        val issues = mutableListOf<String>()
        val sentences = content.split(Regex("[。！？.!?]"))
        val seen = mutableSetOf<String>()
        for (s in sentences) {
            val trimmed = s.trim().take(20)
            if (trimmed.length >= 8 && !seen.add(trimmed)) {
                issues.add("重复叙述：\"${trimmed}...\"")
            }
        }
        return issues
    }

    /**
     * 段落长度均匀度检测：过于均匀的段落是AI特征
     */
    fun detectUniformParagraphs(content: String): List<String> {
        val issues = mutableListOf<String>()
        val paragraphs = content.split("\n\n").filter { it.isNotBlank() }
        if (paragraphs.size < 3) return issues
        val lengths = paragraphs.map { it.length }
        val avg = lengths.average()
        val deviations = lengths.map { kotlin.math.abs(it - avg) }
        // 如果大多数段落长度偏差在10%以内，说明太均匀
        val uniformCount = deviations.count { it < avg * 0.1 }
        if (uniformCount > paragraphs.size * 0.7) {
            issues.add("段落长度过于均匀（${paragraphs.size}段中${uniformCount}段长度接近），建议调整段落节奏")
        }
        return issues
    }
}