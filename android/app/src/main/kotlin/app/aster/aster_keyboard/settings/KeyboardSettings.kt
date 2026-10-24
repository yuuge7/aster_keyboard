package app.aster.aster_keyboard.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.File

data class KeyboardSettings(
    val theme: String = "system", val language: String = "en",
    val englishEnabled: Boolean = true, val romanianEnabled: Boolean = true,
    val autoCorrect: Boolean = true, val suggestions: Boolean = true,
    val autoCapitalization: Boolean = true, val punctuationSpace: Boolean = true,
    val doubleSpacePeriod: Boolean = true, val restoreDiacritics: Boolean = true,
    val numberRow: Boolean = false, val haptics: Boolean = false, val sound: Boolean = false,
    val keyHeight: Int = 52, val wallpaperDim: Float = .35f,
    val personalWordsEn: String = "", val personalWordsRo: String = "",
) {
    val enabledLanguages get() = listOfNotNull("en".takeIf { englishEnabled }, "ro".takeIf { romanianEnabled })
    fun toMap(): Map<String, Any> = mapOf(
        "theme" to theme, "language" to language, "englishEnabled" to englishEnabled,
        "romanianEnabled" to romanianEnabled, "autoCorrect" to autoCorrect, "suggestions" to suggestions,
        "autoCapitalization" to autoCapitalization, "punctuationSpace" to punctuationSpace,
        "doubleSpacePeriod" to doubleSpacePeriod, "restoreDiacritics" to restoreDiacritics,
        "numberRow" to numberRow, "haptics" to haptics, "sound" to sound, "keyHeight" to keyHeight,
        "wallpaperDim" to wallpaperDim.toDouble(), "personalWordsEn" to personalWordsEn,
        "personalWordsRo" to personalWordsRo,
    )
}

class SettingsStore(private val context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("aster_settings", Context.MODE_PRIVATE)
    fun read(): KeyboardSettings {
        val p = preferences
        return KeyboardSettings(
            theme = p.getString("theme", "system")!!, language = p.getString("language", "en")!!,
            englishEnabled = p.getBoolean("englishEnabled", true), romanianEnabled = p.getBoolean("romanianEnabled", true),
            autoCorrect = p.getBoolean("autoCorrect", true), suggestions = p.getBoolean("suggestions", true),
            autoCapitalization = p.getBoolean("autoCapitalization", true), punctuationSpace = p.getBoolean("punctuationSpace", true),
            doubleSpacePeriod = p.getBoolean("doubleSpacePeriod", true), restoreDiacritics = p.getBoolean("restoreDiacritics", true),
            numberRow = p.getBoolean("numberRow", false), haptics = p.getBoolean("haptics", false), sound = p.getBoolean("sound", false),
            keyHeight = p.getInt("keyHeight", 52), wallpaperDim = p.getFloat("wallpaperDim", .35f),
            personalWordsEn = p.getString("personalWordsEn", "")!!, personalWordsRo = p.getString("personalWordsRo", "")!!,
        )
    }
    fun snapshot(): Map<String, Any> = read().toMap() + mapOf(
        "wallpaperPath" to wallpaperFile()?.absolutePath.orEmpty(),
        "wallpaperRevision" to preferences.getLong("wallpaperRevision", 0L),
        "appVersion" to context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
    )
    fun update(patch: Map<*, *>) {
        val values = read().toMap().toMutableMap()
        patch.forEach { (key, value) ->
            require(key is String && key in values) { "Unknown keyboard setting" }
            val old = values[key]
            values[key] = when (old) {
                is Boolean -> { require(value is Boolean); value }
                is Int -> { require(value is Number); value.toInt().coerceIn(42, 66) }
                is Double -> { require(value is Number && value.toDouble().isFinite()); value.toDouble().coerceIn(0.0, .85) }
                else -> { require(value is String); value }
            }
        }
        require(values["theme"] in listOf("system", "light", "dark")) { "Choose a valid theme" }
        require(values["englishEnabled"] == true || values["romanianEnabled"] == true) { "Keep at least one language enabled" }
        val languages = listOfNotNull("en".takeIf { values["englishEnabled"] == true }, "ro".takeIf { values["romanianEnabled"] == true })
        if (values["language"] !in languages) values["language"] = languages.first()
        listOf("personalWordsEn", "personalWordsRo").forEach { key ->
            val words = (values[key] as String).lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            require(words.size <= 500 && words.all { it.length <= 40 && it.all { c -> c.isLetter() || c in "'-’" } }) {
                "Use up to 500 words per language, one word per line (letters, apostrophes and hyphens only)"
            }
            values[key] = words.joinToString("\n")
        }
        preferences.edit().apply {
            values.forEach { (key, value) -> when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Double -> putFloat(key, value.toFloat())
                is String -> putString(key, value)
            } }
        }.apply()
    }
    fun wallpaperFile(): File? = File(context.filesDir, "keyboard-wallpaper.jpg").takeIf { it.isFile }
    fun removeWallpaper() {
        wallpaperFile()?.delete()
        preferences.edit().putLong("wallpaperRevision", System.currentTimeMillis()).apply()
    }
    fun recentEmoji(): List<String> = preferences.getString("recentEmoji", "")!!.split('|').filter { it.isNotBlank() }.take(24)
    fun rememberEmoji(emoji: String) {
        preferences.edit().putString("recentEmoji", (listOf(emoji) + recentEmoji()).distinct().take(24).joinToString("|")).apply()
    }
}
