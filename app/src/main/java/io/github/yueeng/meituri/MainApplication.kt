package io.github.yueeng.meituri

import android.app.Application
import com.squareup.picasso.Picasso
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
        val picasso = Picasso.Builder(this)
                .indicatorsEnabled(true)
                .loggingEnabled(true)
                .build()
        Picasso.setSingletonInstance(picasso)
    }
}