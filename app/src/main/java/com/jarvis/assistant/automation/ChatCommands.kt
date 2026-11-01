package com.jarvis.assistant.automation

object ChatCommands {

    private val SEND = setOf(
        "send", "send karo", "send kar do", "send kar", "bhej do", "bhejo", "bhej",
        "सेंड", "सेंड करो", "सेंड कर दो", "भेज दो", "भेजो",
    )

    private val CHAT = Regex(
        "^(?:open\\s+)?(.+?)\\s+(?:wali|wala|vali|vala|वाली|वाला)\\s+(?:chat|चैट)" +
            "(?:\\s+(?:kholo|khol do|khol|open|खोलो|खोल दो|खोल))?(?:\\s+(?:karo|kar do|करो))?$",
        RegexOption.IGNORE_CASE,
    )

    fun matches(t: String, clean: String): Boolean = t in SEND || CHAT.matchEntire(clean) != null

    fun handle(clean: String, t: String): String? {
        if (t in SEND) {
            val s = JarvisAccessibilityService.instance
            val label = if (s?.findByText("Send") != null) "Send" else "भेजें"
            return CommandRouter.clickLabel(label)
        }
        val m = CHAT.matchEntire(clean) ?: return null
        return CommandRouter.clickLabel(m.groupValues[1].trim())
    }
}
