package com.ruizurraca.carapp.buildlogic.source

/** Offset-preserving lexical views for the existing textual source checks. */
internal object KotlinSourceText {
    fun code(source: String): String {
        val result = source.toCharArray()
        var cursor = 0
        while (cursor < source.length) {
            val end = nonCodeEnd(source, cursor)
            if (end > cursor) {
                blank(result, cursor, end)
                cursor = end
            } else {
                cursor += 1
            }
        }
        return String(result)
    }

    fun declarations(source: String): String {
        val masked = code(source)
        val result = masked.toCharArray()
        var cursor = 0
        while (cursor < masked.length) {
            val end = if (masked[cursor] == '@') annotationEnd(masked, cursor) else cursor
            if (end > cursor) {
                blank(result, cursor, end)
                cursor = end
            } else {
                cursor += 1
            }
        }
        return String(result)
    }

    fun stripLeadingAnnotations(source: String): String {
        val masked = code(source)
        var cursor = 0
        while (cursor < masked.length && masked[cursor].isWhitespace()) cursor += 1
        while (cursor < masked.length && masked[cursor] == '@') {
            val end = annotationEnd(masked, cursor)
            if (end <= cursor) break
            cursor = end
            while (cursor < masked.length && masked[cursor].isWhitespace()) cursor += 1
        }
        return source.substring(cursor)
    }

    fun braceDepths(code: String): IntArray {
        val result = IntArray(code.length + 1)
        code.forEachIndexed { index, character ->
            result[index + 1] = result[index] + when (character) {
                '{' -> 1
                '}' -> -1
                else -> 0
            }
        }
        return result
    }

    private fun annotationEnd(code: String, from: Int): Int {
        var cursor = from + 1
        while (cursor < code.length &&
            (code[cursor].isLetterOrDigit() || code[cursor] in "_:.")) {
            cursor += 1
        }
        if (cursor == from + 1) return from
        while (cursor < code.length && code[cursor].isWhitespace()) cursor += 1
        if (cursor >= code.length || code[cursor] != '(') return cursor
        var depth = 0
        for (index in cursor until code.length) {
            when (code[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return index + 1
                }
            }
        }
        return from
    }

    private fun blank(result: CharArray, from: Int, until: Int) {
        for (index in from until until) {
            if (result[index] != '\n' && result[index] != '\r') result[index] = ' '
        }
    }

    private fun nonCodeEnd(source: String, from: Int): Int = when {
        source.startsWith("//", from) -> source.indexOf('\n', from).let { if (it < 0) source.length else it }
        source.startsWith("/*", from) -> blockCommentEnd(source, from)
        source.startsWith("\"\"\"", from) -> quotedEnd(source, from, raw = true)
        source[from] == '"' -> quotedEnd(source, from, raw = false)
        source[from] == '\'' -> characterEnd(source, from)
        else -> from
    }

    private fun blockCommentEnd(source: String, from: Int): Int {
        var depth = 1
        var cursor = from + 2
        while (cursor < source.length) {
            when {
                source.startsWith("/*", cursor) -> {
                    depth += 1
                    cursor += 2
                }
                source.startsWith("*/", cursor) -> {
                    depth -= 1
                    cursor += 2
                    if (depth == 0) return cursor
                }
                else -> cursor += 1
            }
        }
        return source.length
    }

    private fun quotedEnd(source: String, from: Int, raw: Boolean): Int {
        val delimiter = if (raw) "\"\"\"" else "\""
        var cursor = from + delimiter.length
        while (cursor < source.length) {
            when {
                !raw && source[cursor] == '\\' -> cursor = (cursor + 2).coerceAtMost(source.length)
                source.startsWith("\${", cursor) -> cursor = templateEnd(source, cursor + 2)
                source.startsWith(delimiter, cursor) -> return cursor + delimiter.length
                else -> cursor += 1
            }
        }
        return source.length
    }

    private fun templateEnd(source: String, from: Int): Int {
        var depth = 1
        var cursor = from
        while (cursor < source.length) {
            val end = nonCodeEnd(source, cursor)
            if (end > cursor) {
                cursor = end
            } else {
                when (source[cursor]) {
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return cursor + 1
                    }
                }
                cursor += 1
            }
        }
        return source.length
    }

    private fun characterEnd(source: String, from: Int): Int {
        var cursor = from + 1
        while (cursor < source.length) {
            when (source[cursor]) {
                '\\' -> cursor = (cursor + 2).coerceAtMost(source.length)
                '\'' -> return cursor + 1
                else -> cursor += 1
            }
        }
        return source.length
    }
}
