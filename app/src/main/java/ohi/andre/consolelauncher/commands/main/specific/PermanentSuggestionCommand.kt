package ohi.andre.consolelauncher.commands.main.specific

import android.content.Context
import ohi.andre.consolelauncher.commands.CommandAbstraction

interface PermanentSuggestionCommand : CommandAbstraction {
    fun permanentSuggestions(context: Context): Array<String>?
    fun permanentSuggestionsExecuteOnClick(): Boolean = false
    fun permanentSuggestionReplacement(suggestion: String): String? = null
}
