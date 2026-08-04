package com.dramatica.flow.data

/**
 * 文本解析工具，从 AI 分析的「标签：内容」格式中提取字段。
 */
object TextParser {

    /** 提取单行短字段（如书名、题材），截取前 100 字符 */
    fun extractField(text: String, field: String): String {
        val pattern = Regex("""($field)[：:]\s*(.+?)(?=\n\S+[：:]|\n\n|\Z)""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.get(2)?.trim()?.take(100) ?: ""
    }

    /** 提取多行长字段（如世界观、角色），截取前 300 字符 */
    fun extractFieldMulti(text: String, field: String): String {
        val pattern = Regex("""($field)[：:]\s*([\s\S]+?)(?=\n\n|\n\S+[：:]|\Z)""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.get(2)?.trim()?.take(300) ?: ""
    }
}