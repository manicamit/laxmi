package com.laxmi.app.util

/**
 * Models wrap JSON in prose or markdown fences more often than not. Find the
 * first balanced top-level JSON array in [raw] rather than assuming raw IS
 * the JSON — see PLAN.md engineering note on JSON hardening.
 */
/** Object twin of [extractJsonArray] — first balanced top-level {...} in [raw]. */
fun extractJsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    if (start == -1) return null
    var depth = 0
    var inString = false
    var escape = false
    for (i in start until raw.length) {
        val c = raw[i]
        if (inString) {
            when {
                escape -> escape = false
                c == '\\' -> escape = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return raw.substring(start, i + 1)
            }
        }
    }
    return null
}

fun extractJsonArray(raw: String): String? {
    val start = raw.indexOf('[')
    if (start == -1) return null
    var depth = 0
    var inString = false
    var escape = false
    for (i in start until raw.length) {
        val c = raw[i]
        if (inString) {
            when {
                escape -> escape = false
                c == '\\' -> escape = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '[' -> depth++
            ']' -> {
                depth--
                if (depth == 0) return raw.substring(start, i + 1)
            }
        }
    }
    return null
}
