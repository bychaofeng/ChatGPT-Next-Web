package cn.rehab.trainer.core

/** Small, dependency-free JSON codec for the offline core. Rejects duplicates and limits depth/size. */
object Json {
    fun parse(text: String): Any? {
        require(text.length <= 8_000_000) { "JSON too large" }
        return Reader(text).read()
    }
    fun stringify(value: Any?): String = buildString { encode(value, this, 0) }
    private fun encode(v: Any?, out: StringBuilder, depth: Int) {
        require(depth <= 64) { "JSON nesting limit" }
        when (v) {
            null -> out.append("null")
            is String -> {
                out.append('"')
                v.forEach { c -> when (c) {
                    '"' -> out.append("\\\""); '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n"); '\r' -> out.append("\\r"); '\t' -> out.append("\\t")
                    else -> if (c.code < 32 || c.isSurrogate()) out.append("\\u%04x".format(c.code)) else out.append(c)
                } }; out.append('"')
            }
            is Boolean, is Byte, is Short, is Int, is Long -> out.append(v.toString())
            is Double -> { require(v.isFinite()); out.append(v.toString()) }
            is Float -> { require(v.isFinite()); out.append(v.toString()) }
            is Map<*, *> -> {
                out.append('{'); v.entries.forEachIndexed { i, e ->
                    require(e.key is String); if (i > 0) out.append(',')
                    encode(e.key, out, depth + 1); out.append(':'); encode(e.value, out, depth + 1)
                }; out.append('}')
            }
            is List<*> -> { out.append('['); v.forEachIndexed { i, item -> if (i > 0) out.append(','); encode(item, out, depth + 1) }; out.append(']') }
            else -> error("Unsupported JSON value")
        }
    }
    private class Reader(val s: String) {
        var p = 0
        fun read(): Any? { val v = value(0); ws(); require(p == s.length) { "Trailing JSON" }; return v }
        fun ws() { while (p < s.length && s[p] in " \n\r\t") p++ }
        fun value(depth: Int): Any? {
            require(depth <= 64); ws(); require(p < s.length) { "Truncated JSON" }
            return when (s[p]) {
                '{' -> {
                    p++; val m = linkedMapOf<String, Any?>(); ws()
                    if (take('}')) m else {
                        while (true) { ws(); require(p < s.length && s[p] == '"'); val k = str(); require(!m.containsKey(k)) { "Duplicate JSON key" }; ws(); expect(':'); m[k] = value(depth + 1); ws(); if (take('}')) break; expect(',') }; m
                    }
                }
                '[' -> { p++; val a = mutableListOf<Any?>(); ws(); if (take(']')) a else { while (true) { a.add(value(depth + 1)); ws(); if (take(']')) break; expect(',') }; a } }
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> number()
                else -> error("Invalid JSON token")
            }
        }
        fun take(c: Char): Boolean = if (p < s.length && s[p] == c) { p++; true } else false
        fun expect(c: Char) { require(take(c)) { "Expected JSON delimiter" } }
        fun literal(t: String, v: Any?): Any? { require(s.startsWith(t, p)); p += t.length; return v }
        fun str(): String {
            expect('"'); val b = StringBuilder()
            while (p < s.length) {
                val c = s[p++]
                if (c == '"') return b.toString()
                require(c.code >= 32) { "Unescaped control" }
                if (c != '\\') b.append(c) else {
                    require(p < s.length)
                    when (val e = s[p++]) {
                        '"', '\\', '/' -> b.append(e)
                        'b' -> b.append('\b'); 'f' -> b.append('\u000c'); 'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t')
                        'u' -> { require(p + 4 <= s.length); val hex = s.substring(p, p + 4); require(hex.all { it in "0123456789abcdefABCDEF" }); b.append(hex.toInt(16).toChar()); p += 4 }
                        else -> error("Invalid JSON escape")
                    }
                }
            }; error("Unclosed JSON string")
        }
        fun number(): Number {
            val begin = p; take('-'); require(p < s.length)
            if (!take('0')) { require(s[p] in '1'..'9'); while (p < s.length && s[p] in '0'..'9') p++ }
            var decimal = false
            if (take('.')) { decimal = true; val d = p; while (p < s.length && s[p] in '0'..'9') p++; require(p > d) }
            if (p < s.length && s[p] in "eE") { decimal = true; p++; if (p < s.length && s[p] in "+-") p++; val d = p; while (p < s.length && s[p] in '0'..'9') p++; require(p > d) }
            val text = s.substring(begin, p)
            return if (decimal) text.toDouble().also { require(it.isFinite()) } else text.toLong()
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: error("Expected JSON object")
fun Map<String, Any?>.text(key: String): String = this[key] as? String ?: error("Missing string: $key")
fun Map<String, Any?>.bool(key: String): Boolean = this[key] as? Boolean ?: error("Missing boolean: $key")
fun Map<String, Any?>.long(key: String): Long = this[key] as? Long ?: (this[key] as? Int)?.toLong() ?: error("Missing integer: $key")
fun Map<String, Any?>.items(key: String): List<Any?> = this[key] as? List<Any?> ?: error("Missing array: $key")
fun Map<String, Any?>.keysExactly(vararg keys: String) { require(this.keys == keys.toSet()) { "Unexpected or missing fields" } }
fun Map<String, Any?>.limitedText(key: String, max: Int = 4000): String = text(key).also { require(it.length in 1..max) { "Text length invalid: $key" } }
