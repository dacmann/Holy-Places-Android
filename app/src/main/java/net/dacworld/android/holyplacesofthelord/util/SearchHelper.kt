package net.dacworld.android.holyplacesofthelord.util

object SearchHelper {
    
    /**
     * Parses a search query into individual terms and applies AND logic.
     * Multiple terms are separated by spaces and all terms must be found for a match.
     * 
     * @param query The search query string
     * @param searchableTexts List of text fields to search in
     * @return true if all terms are found in any of the searchable texts (case-insensitive)
     */
    fun matchesAllTerms(query: String, searchableTexts: List<String?>): Boolean {
        if (query.isBlank()) return true
        
        val terms = query.trim().split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
        
        if (terms.isEmpty()) return true
        
        val searchableTextsLower = searchableTexts
            .filterNotNull()
            .map { it.lowercase() }
        
        return terms.all { term ->
            searchableTextsLower.any { text ->
                text.contains(term)
            }
        }
    }
    
    /**
     * Convenience method for single text field search with AND logic
     */
    fun matchesAllTerms(query: String, text: String?): Boolean {
        return matchesAllTerms(query, listOf(text))
    }
}
