@file:Suppress("unused")

package io.github.yueeng.meituri

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.squareup.picasso.Picasso
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import java.lang.ref.WeakReference
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

/**
 * Common library
 * Created by Rain on 2017/8/22.
 */


val LOG_TAG = "Meituri"

fun logi(vararg msg: Any?) = Log.i(LOG_TAG, msg.joinToString(", "))
fun loge(vararg msg: Any?) = Log.e(LOG_TAG, msg.joinToString(", "))
fun logw(vararg msg: Any?) = Log.w(LOG_TAG, msg.joinToString(", "))
fun logd(vararg msg: Any?) = Log.d(LOG_TAG, msg.joinToString(", "))
fun logv(vararg msg: Any?) = Log.v(LOG_TAG, msg.joinToString(", "))

val okhttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager(null, CookiePolicy.ACCEPT_ALL)))
        .addInterceptor(HttpLoggingInterceptor())
        .build()

fun String.httpGet() = try {
    val html = okhttp.newCall(Request.Builder().url(this).build()).execute().body()?.string()
    Pair(this, html)
} catch (e: Exception) {
    e.printStackTrace()
    Pair(this, null)
}

fun Pair<String, String?>.jsoup() = try {
    Jsoup.parse(this.second, this.first)
} catch (e: Exception) {
    e.printStackTrace()
    null
}

fun Context.asActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> this.baseContext.asActivity()
    else -> null
}

fun ViewGroup.inflate(layout: Int, attach: Boolean = false): View = LayoutInflater.from(this.context).inflate(layout, this, attach)
fun ImageView.picasso(uri: String?) = uri?.let { Picasso.with(this.context).load(uri).into(this) }

class ViewBinder<T, V : View>(private var value: T, private val func: (V, T) -> Unit) {
    private val view = mutableListOf<WeakReference<V>>()
    operator fun plus(v: V): ViewBinder<T, V> = synchronized(this) {
        view += WeakReference(v)
        func(v, value)
        return this
    }

    operator fun minus(v: V): ViewBinder<T, V> = synchronized(this) {
        view -= view.filter { it.get() == v || it.get() == null }
        return this
    }

    operator fun times(v: T): ViewBinder<T, V> = synchronized(this) {
        value = v
        view -= view.filter { it.get() == null }
        view.map { it.get()!! }.forEach { func(it, value) }
        return this
    }

    operator fun invoke(): T {
        return value
    }

    fun each(func: (V) -> Unit): ViewBinder<T, V> = synchronized(this) {
        view -= view.filter { it.get() == null }
        view.map { it.get()!! }.forEach { func(it) }
        return this
    }
}

open class DataHolder<T : Any>(view: View) : RecyclerView.ViewHolder(view) {
    protected lateinit var value: T
    protected open fun bind() {}
    fun set(v: T) {
        value = v
        bind()
    }
}

abstract class DataAdapter<T : Any, VH : DataHolder<T>> : RecyclerView.Adapter<VH>() {
    private val data = mutableListOf<T>()
    override fun getItemCount(): Int = data.size
    fun add(vararg items: T): DataAdapter<T, VH> {
        val start = data.size
        data.addAll(items)
        notifyItemRangeInserted(start, data.size - start)
        return this
    }

    fun add(items: Iterable<T>): DataAdapter<T, VH> {
        val start = data.size
        data.addAll(items)
        notifyItemRangeInserted(start, data.size - start)
        return this
    }

    fun clear(): DataAdapter<T, VH> {
        val size = data.size
        data.clear()
        notifyItemRangeRemoved(0, size)
        return this
    }

    fun get(position: Int) = data[position]

    override fun onBindViewHolder(holder: VH?, position: Int) {
        holder?.set(get(position))
    }
}