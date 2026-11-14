package app.aster.aster_keyboard.ime

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class DictionaryRepository(private val context: Context) {
    private val dictionaries = ConcurrentHashMap<String, WordLexicon>()
    fun load(language: String) {
        if (dictionaries.containsKey(language)) return
        val files = listOf("$language.txt") + if (language == "en") listOf("en_supplement.txt") else emptyList()
        val entries = files.flatMap { file ->
            context.assets.open("dictionaries/$file").bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split(' ')
                    val word = parts.firstOrNull().orEmpty()
                    val count = parts.lastOrNull()?.toIntOrNull()
                    if (count != null && word.length in 1..40 && word.all { it.isLetter() || it in "'-’" }) word to count else null
                }.toList()
            }
        }
        dictionaries[language] = WordLexicon(entries, language)
    }
    operator fun get(language: String): WordLexicon? = dictionaries[language]
}
