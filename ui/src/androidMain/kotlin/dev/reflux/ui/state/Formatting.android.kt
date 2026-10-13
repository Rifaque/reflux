package dev.reflux.ui.state

import java.util.Locale

actual fun languageDisplayName(code: String): String? {
    val tag = code.trim().lowercase()
    if (tag.isEmpty() || tag == "und") return null
    val name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault())
    // The JDK echoes codes it does not know.
    return name.takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
}
