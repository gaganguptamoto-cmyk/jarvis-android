package com.jarvis.assistant.automation

object Hinglish {

    private val WORDS = mapOf(
        "बाय" to "bye", "हैलो" to "hello", "हेलो" to "hello", "हाय" to "hi",
        "ओके" to "ok", "थैंक्यू" to "thank you", "सॉरी" to "sorry", "प्लीज" to "please",
        "गुड" to "good", "मॉर्निंग" to "morning", "नाइट" to "night",
        "यस" to "yes", "नो" to "no", "मम्मी" to "mummy", "पापा" to "papa",
        "में" to "mein", "मैं" to "main", "हूँ" to "hoon", "नहीं" to "nahi", "ठीक" to "theek",
    )

    private val CONS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
    )

    private val VOW = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "oo",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
    )

    private val MATRA = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "oo",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o",
    )

    private fun isDev(c: Char) = c in '\u0900'..'\u097F'

    fun convert(text: String): String {
        if (text.none { isDev(it) }) return text
        return text.split(" ").joinToString(" ") { WORDS[it] ?: word(it) }
    }

    private fun word(w: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < w.length) {
            val ch = w[i]
            val cons = CONS[ch]
            if (cons != null) {
                sb.append(cons)
                var j = i + 1
                if (j < w.length && w[j] == '\u093C') j++
                val nx = if (j < w.length) w[j] else null
                if (nx == '\u094D') {
                    j++
                } else if (nx != null && MATRA.containsKey(nx)) {
                    val atEnd = j + 1 >= w.length
                    sb.append(if (nx == '\u093E' && atEnd) "a" else MATRA[nx])
                    j++
                } else if (nx == null || !isDev(nx)) {
                    // word end: drop the inherent "a"
                } else {
                    sb.append("a")
                }
                i = j
            } else {
                val v = VOW[ch] ?: MATRA[ch]
                when {
                    v != null -> sb.append(v)
                    ch == '\u0902' || ch == '\u0901' -> sb.append("n")
                    ch == '\u0903' -> sb.append("h")
                    ch == '\u0964' -> sb.append(".")
                    ch == '\u093C' || ch == '\u094D' -> {}
                    else -> sb.append(ch)
                }
                i++
            }
        }
        return sb.toString()
    }
}
