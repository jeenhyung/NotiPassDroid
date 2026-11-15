package com.notipass.droid.model

enum class MatchMode {
    IGNORE,   // Always match (don't filter on this field)
    CONTAINS, // Text contains keyword
    EXACT,    // Exact match
    REGEX     // Regular expression match
}
