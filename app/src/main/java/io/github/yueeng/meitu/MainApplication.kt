package io.github.yueeng.meitu

import android.app.Application
import android.content.SearchRecentSuggestionsProvider
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.stetho.Stetho
import com.squareup.leakcanary.LeakCanary
import java.lang.ref.WeakReference


/**
 * Main application
 * Created by Rain on 2017/8/22.
 */

class MainApplication : Application() {
    companion object {
        private var app: WeakReference<MainApplication>? = null

        fun instance() = app!!.get()!!
    }

    init {
        app = WeakReference(this)
    }

    override fun onCreate() {
        AppCompatDelegate.setDefaultNightMode(MtSettings.DAY_NIGHT_MODE)
        super.onCreate()
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return
        }
        Stetho.initializeWithDefaults(this)
        LeakCanary.install(this)
    }
}

class SearchHistoryProvider : SearchRecentSuggestionsProvider() {
    init {
        setupSuggestions(SearchHistoryProvider.AUTHORITY, SearchHistoryProvider.MODE)
    }

    companion object {
        const val AUTHORITY: String = "${BuildConfig.APPLICATION_ID}.SearchHistoryProvider"
        const val MODE: Int = SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES
    }
}