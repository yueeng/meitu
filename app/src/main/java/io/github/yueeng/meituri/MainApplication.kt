package io.github.yueeng.meituri

import android.app.Application
import android.content.SearchRecentSuggestionsProvider
import java.lang.ref.WeakReference


/**
 * Main application
 * Created by Rain on 2017/8/22.
 */

class MainApplication : Application() {
    companion object {
        private var app: WeakReference<MainApplication>? = null

        fun current() = app!!.get()!!
    }

    init {
        app = WeakReference(this)
    }
}

class SearchHistoryProvider : SearchRecentSuggestionsProvider() {
    init {
        setupSuggestions(SearchHistoryProvider.AUTHORITY, SearchHistoryProvider.MODE)
    }

    companion object {
        val AUTHORITY = "${SearchHistoryProvider::class.java.`package`.name}.SuggestionProvider"
        val MODE: Int = SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES
    }
}