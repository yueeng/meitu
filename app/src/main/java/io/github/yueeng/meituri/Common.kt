@file:Suppress("unused")

package io.github.yueeng.meituri

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import java.lang.ref.WeakReference
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Common library
 * Created by Rain on 2017/8/22.
 */

val LOG_TAG = "Meituri"

fun debug(call: () -> Unit) {
    if (BuildConfig.DEBUG) call()
}

fun logi(vararg msg: Any?) = debug { Log.i(LOG_TAG, msg.joinToString(", ")) }
fun loge(vararg msg: Any?) = debug { Log.e(LOG_TAG, msg.joinToString(", ")) }
fun logw(vararg msg: Any?) = debug { Log.w(LOG_TAG, msg.joinToString(", ")) }
fun logd(vararg msg: Any?) = debug { Log.d(LOG_TAG, msg.joinToString(", ")) }
fun logv(vararg msg: Any?) = debug { Log.v(LOG_TAG, msg.joinToString(", ")) }

val okhttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager(null, CookiePolicy.ACCEPT_ALL)))
        .apply { debug { addInterceptor(HttpLoggingInterceptor()) } }
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
fun Fragment.glide(): RequestManager = Glide.with(this)
val ImageView.bitmap: Bitmap? get() = (this.drawable as? BitmapDrawable)?.bitmap
val View.bgColor: Int get() = (this.background as? ColorDrawable)?.color ?: 0

object Animator {
    fun argb(begin: Int, over: Int, duration: Long, call: (Int) -> Unit): ValueAnimator =
            ObjectAnimator.ofObject(ArgbEvaluator(), begin, over).apply {
                this.duration = duration
                addUpdateListener { call(it.animatedValue as Int) }
            }
}

inline fun <reified T : Fragment> AppCompatActivity.setFragment(container: Int, bundle: () -> Bundle) {
    supportFragmentManager.run {
        val fragment = findFragmentById(container) as? T
                ?: T::class.java.newInstance().apply { arguments = bundle() }
        beginTransaction()
                .replace(container, fragment)
                .commit()
    }
}

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

abstract class DataPagerAdapter<T> : PagerAdapter() {
    val data = mutableListOf<T>()
    override fun isViewFromObject(view: View?, `object`: Any?): Boolean = view == `object`

    override fun getCount(): Int = data.size
    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any?) {
        container.removeView(`object` as? View)
    }

    override fun setPrimaryItem(container: ViewGroup?, position: Int, `object`: Any?) {
        super.setPrimaryItem(container, position, `object`)
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = data[position]
        val view = container.inflate(R.layout.preview_item)
        view.tag = item
        bind(view, item)
        container.addView(view)
        return view
    }

    abstract fun bind(view: View, item: T)

    fun getView(pager: ViewPager, position: Int = -1): View? =
            pager.findViewWithTag(data[if (position == -1) pager.currentItem else position])
}

val random = Random(System.currentTimeMillis())

fun randomColor(alpha: Int = 0xFF) = android.graphics.Color.HSVToColor(alpha, arrayOf(random.nextInt(360).toFloat(), 1F, 0.5F).toFloatArray())
