package app.aster.aster_keyboard.ime

import android.content.ClipDescription
import android.content.ClipboardManager

/** Reads plain text only, after checking both metadata snapshots for sensitive content. */
object ClipboardText {
    private fun sensitive(description: ClipDescription) =
        description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true

    fun read(clipboard: ClipboardManager): String? = runCatching {
        val description = clipboard.primaryClipDescription ?: return null
        if (sensitive(description)) return null
        val clip = clipboard.primaryClip ?: return null
        if (sensitive(clip.description) || clip.itemCount == 0) return null
        // Never coerce URIs/Intents or read a content provider.
        clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() && it.length <= ClipboardHistory.MAX_TEXT_LENGTH }
    }.getOrNull()
}
