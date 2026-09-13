package com.mccal.folio

/** Where a typed query can go. Google uses the "Web" filter (udm=14), which omits AI Overviews. */
internal enum class WebSearchTarget(val label: String, private val prefix: String) {
    GOOGLE("Google", "https://www.google.com/search?udm=14&q="),
    DUCKDUCKGO("DuckDuckGo", "https://noai.duckduckgo.com/?q="),
    CHATGPT("Ask ChatGPT", "https://chatgpt.com/?q="),
    CLAUDE("Ask Claude", "https://claude.ai/new?q="),
    PERPLEXITY("Perplexity", "https://www.perplexity.ai/search?q=");

    fun uri(query: String): android.net.Uri = android.net.Uri.parse(prefix + android.net.Uri.encode(query.trim()))
}

internal fun openWebSearch(context: android.content.Context, target: WebSearchTarget, query: String) {
    // A plain https link: the matching app opens it if installed and verified, otherwise the browser.
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, target.uri(query))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

