package app.aster.aster_keyboard.settings

import android.content.Context
import app.aster.aster_keyboard.ime.ClipboardEntry
import app.aster.aster_keyboard.ime.ClipboardHistory
import org.json.JSONArray
import org.json.JSONObject

/** Separate private storage, excluded from backup by the application's manifest. */
class ClipboardStore(context: Context) {
    private val preferences = context.getSharedPreferences("aster_clipboard", Context.MODE_PRIVATE)
    private val history = ClipboardHistory(runCatching {
        val data = JSONArray(preferences.getString("entries", "[]"))
        (0 until minOf(data.length(), ClipboardHistory.MAX_ENTRIES)).map { index ->
            val item = data.getJSONObject(index)
            ClipboardEntry(item.getString("text"), item.getLong("copiedAt"), item.optBoolean("pinned"))
        }
    }.getOrDefault(emptyList()))
    fun entries(): List<ClipboardEntry> = history.entries().also { persist(it) }
    fun record(text: String) { history.record(text); persist(history.entries()) }
    fun togglePin(text: String) { history.togglePin(text); persist(history.entries()) }
    fun remove(text: String) { history.remove(text); persist(history.entries()) }
    fun clear() { history.clear(); preferences.edit().clear().apply() }
    private fun persist(entries: List<ClipboardEntry>) {
        val data = JSONArray()
        entries.forEach { entry -> data.put(JSONObject().apply {
            put("text", entry.text); put("copiedAt", entry.copiedAt); put("pinned", entry.pinned)
        }) }
        preferences.edit().putString("entries", data.toString()).apply()
    }
}
