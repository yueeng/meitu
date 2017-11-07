package io.github.yueeng.meituri

import android.app.Application
import android.content.SearchRecentSuggestionsProvider
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory
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

        fun current() = app!!.get()!!
    }

    init {
        app = WeakReference(this)
    }

    override fun onCreate() {
        super.onCreate()
        val config = OkHttpImagePipelineConfigFactory
                .newBuilder(this, okhttp)
                // . other setters
                // . setNetworkFetcher is already called for you
                .build()
        Fresco.initialize(this, config)
        Stetho.initializeWithDefaults(this)
        if (LeakCanary.isInAnalyzerProcess(this)) {
            LeakCanary.install(this)
        }
    }
}

class SearchHistoryProvider : SearchRecentSuggestionsProvider() {
    init {
        setupSuggestions(SearchHistoryProvider.AUTHORITY, SearchHistoryProvider.MODE)
    }

    companion object {
        val AUTHORITY: String = SearchHistoryProvider::class.java.name
        val MODE: Int = SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES
    }
}