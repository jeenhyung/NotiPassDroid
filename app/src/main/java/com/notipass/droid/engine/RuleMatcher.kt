package com.notipass.droid.engine

import com.notipass.droid.model.MatchMode
import com.notipass.droid.model.Rule

data class NotificationPayload(
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val subText: String = "",
    val senderDisplay: String = "",
    val postTime: Long = System.currentTimeMillis()
)

data class MatchResult(
    val isMatched: Boolean,
    val regexGroups: Map<String, String> = emptyMap()
)

object RuleMatcher {

    fun match(rule: Rule, payload: NotificationPayload): MatchResult {
        if (!rule.isEnabled) return MatchResult(false)

        // 1. Package filter
        if (!rule.targetPackage.isNullOrBlank()) {
            if (!payload.packageName.equals(rule.targetPackage.trim(), ignoreCase = true)) {
                return MatchResult(false)
            }
        }

        val groups = mutableMapOf<String, String>()

        // 2. Title filter
        val titleMatch = matchText(rule.titleMatchMode, rule.titleKeyword, payload.title, "title", groups)
        if (!titleMatch) return MatchResult(false)

        // 3. Content filter
        val contentMatch = matchText(rule.contentMatchMode, rule.contentKeyword, payload.content, "content", groups)
        if (!contentMatch) return MatchResult(false)

        return MatchResult(true, groups)
    }

    private fun matchText(
        mode: MatchMode,
        pattern: String,
        target: String,
        groupPrefix: String,
        outGroups: MutableMap<String, String>
    ): Boolean {
        return when (mode) {
            MatchMode.IGNORE -> true
            MatchMode.CONTAINS -> {
                if (pattern.isBlank()) true
                else target.contains(pattern.trim(), ignoreCase = true)
            }
            MatchMode.EXACT -> {
                target.trim().equals(pattern.trim(), ignoreCase = false)
            }
            MatchMode.REGEX -> {
                if (pattern.isBlank()) return true
                try {
                    val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    val match = regex.find(target) ?: return false
                    match.groups.forEachIndexed { index, matchGroup ->
                        if (matchGroup != null) {
                            outGroups["${groupPrefix}_group$index"] = matchGroup.value
                            outGroups["group$index"] = matchGroup.value
                        }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
}
