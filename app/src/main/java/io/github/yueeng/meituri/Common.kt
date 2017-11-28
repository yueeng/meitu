@file:Suppress("unused", "PropertyName", "ObjectPropertyName", "MemberVisibilityCanPrivate")

package io.github.yueeng.meituri

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.FloatingActionButton
import android.support.transition.Fade
import android.support.transition.Slide
import android.support.transition.TransitionManager
import android.support.transition.TransitionSet
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.NotificationCompat
import android.support.v4.app.SharedElementCallback
import android.support.v4.content.ContextCompat
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v4.widget.SlidingPaneLayout
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.support.v7.view.menu.MenuPopupHelper
import android.support.v7.widget.*
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.drawable.ProgressBarDrawable
import com.facebook.drawee.generic.GenericDraweeHierarchy
import com.facebook.drawee.view.DraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.image.ImageInfo
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.facebook.samples.zoomable.DefaultZoomableController
import com.facebook.samples.zoomable.ZoomableDraweeView
import com.facebook.stetho.okhttp3.StethoInterceptor
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import io.reactivex.subscribers.SerializedSubscriber
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.jetbrains.anko.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.*
import java.lang.ref.WeakReference
import java.net.CookieManager
import java.net.CookiePolicy
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Common library
 * Created by Rain on 2017/8/22.
 */

val LOG_TAG = R::class.java.`package`.name.split(".").last()

fun debug(call: () -> Unit) {
    if (BuildConfig.DEBUG) call()
}

fun stack() = Thread.currentThread().stackTrace.drop(7).take(1)
        .joinToString("\n") { "${it.methodName}(${it.fileName}:${it.lineNumber})" }

fun logi(vararg msg: Any?) = debug { Log.i(LOG_TAG, stack() + " -> " + msg.joinToString(", ")) }
fun loge(vararg msg: Any?) = debug { Log.e(LOG_TAG, stack() + " -> " + msg.joinToString(", ")) }
fun logw(vararg msg: Any?) = debug { Log.w(LOG_TAG, stack() + " -> " + msg.joinToString(", ")) }
fun logd(vararg msg: Any?) = debug { Log.d(LOG_TAG, stack() + " -> " + msg.joinToString(", ")) }
fun logv(vararg msg: Any?) = debug { Log.v(LOG_TAG, stack() + " -> " + msg.joinToString(", ")) }

inline fun <reified T : Any> Any.clazz(): T? = this as? T

fun <T : Any> T?.or(other: () -> T?): T? = this ?: other()
fun <T : Any> T?.option(): List<T> = if (this != null) listOf(this) else emptyList()

val okhttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager(null, CookiePolicy.ACCEPT_ALL)))
        .addNetworkInterceptor(StethoInterceptor())
        .apply { debug { addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }) } }
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

object MtSettings {
    private val context: Context get() = MainApplication.current()
    private val config by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private const val KEY_PREVIEW_LIST_COLUMN = "app.preview_list_column"
    private const val KEY_DAY_NIGHT_MODE = "app.day_night_mode"
    val LIST_COLUMN: Int get() = context.resources.getInteger(R.integer.list_columns)
    val MAX_PREVIEW_LIST_COLUMN: Int
        get() = LIST_COLUMN + if (context.isPortrait) 1 else 2
    var PREVIEW_LIST_COLUMN: Int
        get() = Math.min(MAX_PREVIEW_LIST_COLUMN, config.getInt(KEY_PREVIEW_LIST_COLUMN, LIST_COLUMN))
        set(value) = config.edit().putInt(KEY_PREVIEW_LIST_COLUMN, value).apply()
    var DAY_NIGHT_MODE: Int
        get() = config.getInt(KEY_DAY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_AUTO)
        set(value) = config.edit().putInt(KEY_DAY_NIGHT_MODE, value).apply()
}

fun Element.attrs(vararg key: String): String? = key.firstOrNull { hasAttr(it) }?.let { attr(it) }

fun Elements.attrs(vararg key: String): String? = key.firstOrNull { hasAttr(it) }?.let { attr(it) }

fun Context.asActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> this.baseContext.asActivity()
    else -> null
}

fun Fragment.setSupportActionBar(bar: Toolbar) {
    (activity as? AppCompatActivity)?.setSupportActionBar(bar)
}

fun Fragment.ui(func: () -> Unit) {
    activity?.runOnUiThread(func)
}

fun Activity.ui(func: () -> Unit) {
    runOnUiThread(func)
}

var Fragment.title: CharSequence?
    get() = activity?.title
    set(value) {
        activity?.title = value
    }

fun <T : View> View.findViewWithTag2(tag: Any, clazz: Class<T>): T? {
    while (true) {
        val v = this.findViewWithTag<View>(tag) ?: return null
        return if (clazz.isInstance(v)) clazz.cast(v)
        else v.childrenSequence().mapNotNull { it.findViewWithTag2(tag, clazz) }.firstOrNull()
    }
}

inline fun <reified T : View> View.findViewWithTag2(tag: Any): T? = this.findViewWithTag2(tag, T::class.java)

fun ViewGroup.inflate(layout: Int, attach: Boolean = false): View = LayoutInflater.from(this.context).inflate(layout, this, attach)
val ImageView.bitmap: Bitmap? get() = (this.drawable as? BitmapDrawable)?.bitmap
fun View.delay(delay: Long, action: () -> Unit) = this.postDelayed({ action() }, delay)

infix fun <A, B> A.to4(that: B): android.support.v4.util.Pair<A, B> = android.support.v4.util.Pair(this, that)
fun <T, R> Iterable<T>.collect(clazz: Class<R>, predicate: ((R) -> Boolean) = { true }): List<R> =
        filter { clazz.isInstance(it) }.map { clazz.cast(it) }.filter(predicate)

inline fun <reified T : Fragment> AppCompatActivity.setFragment(container: Int, bundle: () -> Bundle?) {
    supportFragmentManager.run {
        val fragment = findFragmentById(container) as? T
                ?: T::class.java.newInstance().apply { arguments = bundle() }
        beginTransaction()
                .replace(container, fragment)
                .commit()
    }
}

class ViewBinder<T, V : View>(private var value: T, private val func: (V, T) -> Unit) {
    private val view = WeakHashMap<V, Boolean>()
    operator fun plus(v: V): ViewBinder<T, V> = synchronized(this) {
        view[v] = true
        func(v, value)
        this
    }

    operator fun minus(v: V): ViewBinder<T, V> = synchronized(this) {
        view.remove(v)
        this
    }

    operator fun times(v: T): ViewBinder<T, V> = synchronized(this) {
        if (value != v) {
            value = v
            view.forEach { func(it.key, value) }
        }
        this
    }

    operator fun invoke(): T = value

    fun each(func: (V) -> Unit): ViewBinder<T, V> = synchronized(this) {
        view.forEach { func(it.key) }
        this
    }
}

var RecyclerView.supportsChangeAnimations
    get() = (itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations ?: false
    set(value) {
        (itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = value
    }

open class DataHolder<out T : Any>(view: View) : RecyclerView.ViewHolder(view) {
    val context: Context get() = itemView.context
    private lateinit var _value: T
    val value: T get() = _value
    open fun bind() = Unit
    open fun bind(i: Int) = bind()
    open fun bind(i: Int, payloads: MutableList<Any>?) = bind(i)

    @Suppress("UNCHECKED_CAST")
    fun set(v: Any, i: Int, payloads: MutableList<Any>?) {
        _value = v as T
        bind(i, payloads)
    }
}

abstract class DataAdapter<T : Any, VH : DataHolder<T>> : RecyclerView.Adapter<VH>() {
    private val _data = mutableListOf<T>()
    open val data: List<T> get() = _data
    override fun getItemCount(): Int = _data.size
    fun add(item: T): DataAdapter<T, VH> {
        _data.add(item)
        notifyItemInserted(_data.size - 1)
        return this
    }

    fun add(items: Iterable<T>): DataAdapter<T, VH> {
        val start = _data.size
        _data.addAll(items)
        if (_data.size - start > 0) notifyItemRangeInserted(start + 1, _data.size - start)
        return this
    }

    open fun clear(): DataAdapter<T, VH> {
        val size = _data.size
        _data.clear()
        if (size > 0) notifyItemRangeRemoved(0, size)
        return this
    }

    fun get(position: Int) = _data[position]

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>?) {
        holder.set(get(position), position, payloads)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = Unit
}

abstract class AnimDataAdapter<T : Any, VH : DataHolder<T>> : DataAdapter<T, VH>() {
    private var last: Int = -1
    private val interpolator = DecelerateInterpolator(3F)
    private val from: Float = (MainApplication.current().windowManager.defaultDisplay).run {
        Point().apply { getSize(this) }.let { Math.max(it.x, it.y) / 4F }
    }

    override fun clear(): DataAdapter<T, VH> {
        last = -1
        return super.clear()
    }

    override fun onBindViewHolder(holder: VH, @SuppressLint("RecyclerView") position: Int, payloads: MutableList<Any>?) {
        super.onBindViewHolder(holder, position, payloads)
        animation(holder, position)
    }

    protected fun animation(holder: VH, position: Int) {
        if (position > last) {
            last = position
            val anim = ObjectAnimator.ofFloat(holder.itemView, "translationY", from, 0F)
                    .setDuration(1000)
            anim.interpolator = interpolator
            anim.start()
        }
    }
}

class FooterHolder(view: View) : DataHolder<String>(view) {
    private val text1 = view.findViewById<TextView>(R.id.text1)
    override fun bind() {
        (itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
        text1.text = value
    }
}

abstract class FooterDataAdapter<T : Any, VH : DataHolder<T>> : AnimDataAdapter<Any, DataHolder<Any>>() {
    companion object {
        const val TYPE_HEADER = -1
        const val TYPE_FOOTER = -2
    }

    fun add(type: Int, vararg items: String): FooterDataAdapter<T, VH> {
        val (list, size, pos) = when (type) {
            TYPE_HEADER -> Triple(_header, _header.size, 0)
            TYPE_FOOTER -> Triple(_footer, _footer.size, _header.size + data.size)
            else -> throw IllegalArgumentException()
        }
        list.addAll(items)
        notifyItemRangeRemoved(pos + size + 1, list.size - size)
        return this
    }

    fun clear(type: Int): FooterDataAdapter<T, VH> {
        val (list, size, pos) = when (type) {
            TYPE_HEADER -> Triple(_header, _header.size, 0)
            TYPE_FOOTER -> Triple(_footer, _footer.size, _header.size + data.size)
            else -> throw IllegalArgumentException()
        }
        list.clear()
        notifyItemRangeRemoved(pos, size)
        return this
    }

    fun replace(type: Int, position: Int, item: String): FooterDataAdapter<T, VH> {
        val (list, pos) = when (type) {
            TYPE_HEADER -> Pair(_header, position)
            TYPE_FOOTER -> Pair(_footer, position + _header.size + data.size)
            else -> throw IllegalArgumentException()
        }
        list[position] = item
        notifyItemChanged(pos)
        return this
    }

    private val _header = mutableListOf<String>()
    private val _footer = mutableListOf<String>()
    val header: List<String> get() = _header
    val footer: List<String> get() = _footer
    override fun getItemCount(): Int = super.getItemCount() + _header.size + _footer.size
    final override fun getItemViewType(position: Int): Int = when {
        position < _header.size -> TYPE_HEADER
        position < data.size + _header.size -> getItemType(position - _header.size)
        position < data.size + _header.size + _footer.size -> TYPE_FOOTER
        else -> throw IllegalArgumentException()
    }

    open fun getItemType(position: Int): Int = 0

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataHolder<Any> = when (viewType) {
        TYPE_HEADER, TYPE_FOOTER -> FooterHolder(parent.inflate(R.layout.list_text_item))
        else -> onCreateHolder(parent, viewType)
    }

    abstract fun onCreateHolder(parent: ViewGroup, viewType: Int): VH
    @Suppress("UNCHECKED_CAST")
    override val data: List<T>
        get() = super.data as List<T>

    override fun onBindViewHolder(holder: DataHolder<Any>, position: Int, payloads: MutableList<Any>?) {
        when (getItemViewType(position)) {
            -1 -> holder.set(_header[position], position, payloads)
            -2 -> holder.set(_footer[position - data.size - _header.size], position - data.size - _header.size, payloads)
            else -> holder.set(get(position - _header.size), position - _header.size, payloads)
        }
        animation(holder, position)
    }
}

abstract class DataPagerAdapter<T>(val layout: Int) : PagerAdapter() {
    val data = mutableListOf<T>()
    override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

    override fun getCount(): Int = data.size
    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as? View)
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = data[position]
        val view = container.inflate(layout)
        view.tag = item
        bind(view, item, position)
        container.addView(view)
        return view
    }

    abstract fun bind(view: View, item: T, position: Int)

    fun getView(pager: ViewPager, position: Int = -1): View? =
            pager.findViewWithTag(data[if (position == -1) pager.currentItem else position])
}

val random = Random(System.currentTimeMillis())

fun randomColor(alpha: Int = 0xFF) = android.graphics.Color.HSVToColor(alpha, arrayOf(random.nextInt(360).toFloat(), 1F, 0.5F).toFloatArray())

class Once {
    private var init = false
    fun run(call: () -> Unit) {
        synchronized(init) {
            if (init) return
            init = true
            call()
        }
    }
}

fun RecyclerView.findFirstVisibleItemPosition(): Int = layoutManager?.let { layout ->
    when (layout) {
        is StaggeredGridLayoutManager -> layout.findFirstVisibleItemPositions(null).min() ?: RecyclerView.NO_POSITION
        is GridLayoutManager -> layout.findFirstVisibleItemPosition()
        is LinearLayoutManager -> layout.findFirstVisibleItemPosition()
        else -> RecyclerView.NO_POSITION
    }
} ?: RecyclerView.NO_POSITION

fun RecyclerView.findLastVisibleItemPosition(): Int = layoutManager?.let { layout ->
    when (layout) {
        is StaggeredGridLayoutManager -> layout.findLastVisibleItemPositions(null).max() ?: RecyclerView.NO_POSITION
        is GridLayoutManager -> layout.findLastVisibleItemPosition()
        is LinearLayoutManager -> layout.findLastVisibleItemPosition()
        else -> RecyclerView.NO_POSITION
    }
} ?: RecyclerView.NO_POSITION

fun RecyclerView.loadMore(last: Int = 1, call: () -> Unit) {
    fun load(recycler: RecyclerView) {
        recycler.adapter?.let {
            if (recycler.findLastVisibleItemPosition() >= it.itemCount - last) recycler.post { call() }
        }
    }
    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> load(v as RecyclerView) }
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recycler: RecyclerView, state: Int) {
            if (state == RecyclerView.SCROLL_STATE_IDLE) load(recycler)
        }
    })
}

inline fun <reified T : RecyclerView.ViewHolder> RecyclerView.findViewHolderForAdapterPosition2(position: Int): T? =
        findViewHolderForAdapterPosition(position) as? T

class RoundedBackgroundColorSpan(private val backgroundColor: Int) : ReplacementSpan() {
    private var linePadding = 2f // play around with these as needed
    private var sidePadding = 5f // play around with these as needed
    private fun measureText(paint: Paint, text: CharSequence, start: Int, end: Int): Float =
            paint.measureText(text, start, end)

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, p4: Paint.FontMetricsInt?): Int =
            Math.round(measureText(paint, text, start, end) + (2 * sidePadding))

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val rect = RectF(x, y + paint.fontMetrics.top - linePadding,
                x + getSize(paint, text, start, end, paint.fontMetricsInt),
                y + paint.fontMetrics.bottom + linePadding)
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, 5F, 5F, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawText(text, start, end, x + sidePadding, y * 1F, paint)
    }

}

class TagClickableSpan<T>(private val tag: T, private val call: ((T) -> Unit)? = null) : ClickableSpan() {
    override fun onClick(widget: View) {
        call?.invoke(tag)
    }

    override fun updateDrawState(ds: TextPaint) {
        ds.color = 0xFFFFFFFF.toInt()
        ds.isUnderlineText = false
    }
}

fun <T> List<T>.spannable(separator: CharSequence = " ", string: (T) -> String = { "$it" }, call: ((T) -> Unit)?): SpannableStringBuilder {

    val tags = this.joinToString(separator) { string(it) }
    val span = SpannableStringBuilder(tags)
    fold(0) { i, it ->
        val p = tags.indexOf(string(it), i)
        val e = p + string(it).length
        if (call != null) span.setSpan(TagClickableSpan(it, call), p, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(RoundedBackgroundColorSpan(randomColor(0xBF)), p, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        e
    }
    return span
}

class AccentClickableSpan<in T>(private val t: T, private val call: ((T) -> Unit)?) : ClickableSpan() {
    override fun onClick(p0: View?) {
        call?.invoke(t)
    }

    override fun updateDrawState(ds: TextPaint) {
        ds.color = accentColor
        ds.isUnderlineText = false
    }
}

val accentColor get() = ContextCompat.getColor(MainApplication.current(), R.color.colorAccent)

fun String.numbers() = "\\d+".toRegex().findAll(this).map { it.value }.toList()

fun <T> String.spannable(tag: List<T>?, string: ((T) -> String) = { "$it" }, call: ((T) -> Unit)? = null): SpannableStringBuilder = SpannableStringBuilder(this).apply {
    tag?.forEach {
        string(it).toRegex().findAll(this).map { it.range }.forEach { i ->
            setSpan(AccentClickableSpan(it, call), i.first, i.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

inline fun <T> T.consumer(block: T.() -> Unit): Boolean = block(this).let { true }
inline fun consumer(fn: () -> Unit): Boolean = fn().let { true }

fun Cursor.getString(column: String): String = getString(getColumnIndex(column))
fun Cursor.getInt(column: String) = getInt(getColumnIndex(column))

object Save {
    private fun encode(path: String): String = """\/:*?"<>|""".fold(path) { r, i ->
        r.replace(i, ' ')
    }

    fun file(url: String, title: String): File = MainApplication.current().run {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "${encode(getString(R.string.app_name))}/${encode(title)}${url.right('/')}")
    }

    fun check(url: String): Int {
        MainApplication.current().downloadManager.query(DownloadManager.Query()).use { c ->
            generateSequence(c.moveToFirst().takeIf { it }, { c.moveToNext().takeIf { it } }).forEach {
                val u = c.getString(DownloadManager.COLUMN_URI)
                val s = c.getInt(DownloadManager.COLUMN_STATUS)
                if (u == url) return s
            }
            return 0
        }
    }

    fun download(url: String, file: File) = MainApplication.current().run {
        if (file.exists()) file.delete()
        val request = DownloadManager.Request(Uri.parse(url))
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file))
        downloadManager.enqueue(request)
    }

    fun download(url: String, title: String, override: Boolean = false, call: ((Int) -> Unit)? = null) {
        check(url).let {
            when (it) {
                0, DownloadManager.STATUS_FAILED -> download(url, file(url, title)).let { 0 }
                DownloadManager.STATUS_SUCCESSFUL -> if (override) download(url, file(url, title)).let { 0 } else it
                else -> it
            }.let { call?.invoke(it) }
        }
    }
}

fun Activity.permission(permission: String, message: String? = null, call: (() -> Unit)? = null) {
    if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
        call?.invoke()
    } else {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), permission.hashCode())
        } else {
            if (message != null) toast(message)
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun Activity.permissionWriteExternalStorage(call: (() -> Unit)? = null) {
    permission(Manifest.permission.WRITE_EXTERNAL_STORAGE, "没有写SD卡权限，将不能保存图片，请在权限管理中打开。", call)
}

fun String.right(c: Char, ignoreCase: Boolean = false) = this.substring(this.lastIndexOf(c, ignoreCase = ignoreCase).takeIf { it != -1 } ?: 0)
fun String.left(c: Char, ignoreCase: Boolean = false) = this.substring(0, this.indexOf(c, ignoreCase = ignoreCase).takeIf { it != -1 } ?: this.length - 1)

fun Context.delay(millis: Long, run: () -> Unit) {
    Handler(mainLooper).postDelayed({ run() }, millis)
}

fun Fragment.delay(millis: Long, run: () -> Unit) {
    context?.delay(millis, run)
}

fun Context.wrapper(theme: Int = R.style.AppTheme) = ContextThemeWrapper(this, theme)
fun Context.alert() = AlertDialog.Builder(this)
fun Context.popupMenu(view: View, gravity: Int = Gravity.NO_GRAVITY, attr: Int = 0, res: Int = R.style.AppTheme_PopupMenu) = PopupMenu(this, view, gravity, attr, res)

val Context.orientation get() = resources.configuration.orientation
val Context.isPortrait get() = orientation == Configuration.ORIENTATION_PORTRAIT
val Fragment.orientation get() = resources.configuration.orientation
val Fragment.isPortrait get() = orientation == Configuration.ORIENTATION_PORTRAIT

fun View.fadeIn() {
    visibility = View.VISIBLE
    scaleX = 5F
    scaleY = 5F
    alpha = 0F
    animate().scaleX(1F)
            .scaleY(1F)
            .alpha(1F)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator(2F))
            .start()
}

fun View.startPostponedEnterTransition() {
    viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver.removeOnPreDrawListener(this)
                    requestLayout()
                    ActivityCompat.startPostponedEnterTransition(context.asActivity()!!)
                    return false
                }
            })
}

open class ViewSharedElementCallback(private val call: () -> Pair<View, String>) : SharedElementCallback() {
    override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
        names.clear()
        sharedElements.clear()
        val (view, name) = call()
        names.add(name)
        sharedElements.put(name, view)
    }
}

fun Activity.enterSharedElementCallback(call: () -> Pair<View, String>) {
    ActivityCompat.setEnterSharedElementCallback(this, object : ViewSharedElementCallback(call) {
        override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
            ActivityCompat.setEnterSharedElementCallback(this@enterSharedElementCallback, null)
            super.onMapSharedElements(names, sharedElements)
        }
    })
}

fun Activity.exitSharedElementCallback(call: () -> Pair<View, String>) {
    ActivityCompat.setExitSharedElementCallback(this, object : ViewSharedElementCallback(call) {
        override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
            ActivityCompat.setExitSharedElementCallback(this@exitSharedElementCallback, null)
            super.onMapSharedElements(names, sharedElements)
        }
    })
}

var <V : View>BottomSheetBehavior<V>.isOpen: Boolean
    get() = this.state == BottomSheetBehavior.STATE_EXPANDED
    set(value) {
        this.state = if (value) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
    }

fun <V : View> BottomSheetBehavior<V>.open() {
    this.isOpen = true
}

fun <V : View> BottomSheetBehavior<V>.close() {
    this.isOpen = false
}

@SuppressLint("RestrictedApi")
fun PopupMenu.setForceShowIcon(show: Boolean) = try {
    javaClass.getDeclaredField("mPopup").let {
        it.isAccessible = true
        it.get(this).clazz<MenuPopupHelper>()?.setForceShowIcon(show)
    }
} catch (e: Exception) {
    e.printStackTrace()
}

fun Menu.setIconEnable(enable: Boolean) = try {
    javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.java).let {
        it.isAccessible = true
        it.invoke(this, enable)
    }
    true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

var ZoomableDraweeView.maxScaleFactor: Float
    get() = (this.zoomableController as? DefaultZoomableController)?.maxScaleFactor ?: 2F
    set(value) {
        (this.zoomableController as? DefaultZoomableController)?.maxScaleFactor = value
    }

fun <DV : DraweeView<GenericDraweeHierarchy>> DV.progress() = this.apply {
    hierarchy.setProgressBarImage(ProgressBarDrawable().apply {
        barWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2F, resources.displayMetrics).toInt()
    })
}

fun View.size(fn: (Int, Int) -> Unit) {
    if (width > 0 && height > 0) return fn(width, height)
    val once = Once()
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            once.run { fn(width, height) }
        }
    })
    viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            once.run { fn(width, height) }
            return false
        }
    })
}

fun <DV : DraweeView<GenericDraweeHierarchy>> DV.load(uri: String, sample: Boolean = true, fn: ((ImageInfo) -> Unit)? = null) = this.apply {
    val weak = WeakReference(this)
    size { w, h ->
        val request = ImageRequestBuilder.newBuilderWithSource(Uri.parse(uri))
                .setProgressiveRenderingEnabled(true)
                .apply { if (sample) resizeOptions = ResizeOptions(w, h) }
                .build()
        weak.get()?.controller = Fresco.getDraweeControllerBuilderSupplier().get()
                .setImageRequest(request)
                .setTapToRetryEnabled(true)
                .setControllerListener(object : BaseControllerListener<ImageInfo>() {
                    override fun onFinalImageSet(id: String, imageInfo: ImageInfo?, animatable: Animatable?) {
                        imageInfo?.let { info ->
                            weak.get()?.let {
                                it.aspectRatio = 1F * info.width / info.height
                                fn?.invoke(info)
                            }
                        }

                    }
                })
                .setOldController(controller)
                .build()
    }
}

@SuppressLint("Registered")
open class DayNightAppCompatActivity : AppCompatActivity() {
    override fun onCreate(state: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(MtSettings.DAY_NIGHT_MODE)
        super.onCreate(state)

        RxBus.instance.subscribe<Int>(this, "day_night") {
            recreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.instance.unsubscribe(this, "day_night")
    }
}

class PagerSlidingPaneLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : SlidingPaneLayout(context, attrs, defStyle) {
    private var mInitialMotionX: Float = 0F
    private var mInitialMotionY: Float = 0F
    private val mEdgeSlop: Float = ViewConfiguration.get(context).scaledEdgeSlop.toFloat()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean = super.onTouchEvent(ev)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                mInitialMotionX = ev.x
                mInitialMotionY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val x = ev.x
                val y = ev.y
                if (mInitialMotionX > mEdgeSlop && !isOpen && canScroll(this, false,
                        Math.round(x - mInitialMotionX), Math.round(x), Math.round(y))) {
                    return super.onInterceptTouchEvent(MotionEvent.obtain(ev).apply {
                        action = MotionEvent.ACTION_CANCEL
                    })
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}

@SuppressLint("Registered")
open class BaseSlideCloseActivity : DayNightAppCompatActivity(), SlidingPaneLayout.PanelSlideListener {

    override fun onCreate(state: Bundle?) {
        swipe()
        super.onCreate(state)
    }

    private fun swipe() {
        val swipe = PagerSlidingPaneLayout(this)
        // 通过反射改变mOverhangSize的值为0，
        // 这个mOverhangSize值为菜单到右边屏幕的最短距离，
        // 默认是32dp，现在给它改成0
        try {
            val overhang = SlidingPaneLayout::class.java.getDeclaredField("mOverhangSize")
            overhang.isAccessible = true
            overhang.set(swipe, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        swipe.setPanelSlideListener(this)
        swipe.sliderFadeColor = ContextCompat.getColor(this, android.R.color.transparent)

        // 左侧的透明视图
        val leftView = View(this)
        leftView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        swipe.addView(leftView, 0)
        swipe.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            bottomMargin = getSoftButtonsBarHeight()
        }
        val decorView = window.decorView as ViewGroup


        // 右侧的内容视图
        val decorChild = decorView.getChildAt(0) as ViewGroup
        theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground)).also {
            decorChild.setBackgroundColor(it.getColor(0, 0))
        }.recycle()
        decorView.removeView(decorChild)
        decorView.addView(swipe)

        // 为 SlidingPaneLayout 添加内容视图
        swipe.addView(decorChild, 1)
    }

    // getRealMetrics is only available with API 17 and +
    private fun getSoftButtonsBarHeight(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val usableHeight = metrics.heightPixels
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val realHeight = metrics.heightPixels
        if (realHeight > usableHeight)
            realHeight - usableHeight
        else
            0
    } else 0

    override fun onPanelSlide(panel: View, slideOffset: Float) {

    }

    override fun onPanelOpened(panel: View) {
        finish()
    }

    override fun onPanelClosed(panel: View) {

    }
}

val String.md5
    get():String {
        val md5 = MessageDigest.getInstance("MD5")
        val bytes = md5.digest(this.toByteArray())
        return bytes.joinToString("", transform = { String.format("%02x", it) })
    }

fun <T> Single<T>.io(): Single<T> = this.subscribeOn(Schedulers.io())
fun <T> Single<T>.main(): Single<T> = observeOn(AndroidSchedulers.mainThread())
fun <T> Single<T>.io2main(): Single<T> = this.io().main()
fun <T> Observable<T>.io(): Observable<T> = this.subscribeOn(Schedulers.io())
fun <T> Observable<T>.main(): Observable<T> = observeOn(AndroidSchedulers.mainThread())
fun <T> Observable<T>.io2main(): Observable<T> = this.io().main()

object RxMt {
    fun <T> create(fn: () -> T): Observable<T> = Observable.create<T> {
        try {
            it.onNext(fn())
            it.onComplete()
        } catch (e: Exception) {
            it.onError(e)
        }
    }!!
}

class RxBus {
    companion object {
        private val _instance: RxBus by lazy { RxBus() }
        val instance get() = _instance
    }

    data class RxMsg(val action: String, val event: Any)

    private val bus: FlowableProcessor<RxMsg> by lazy { PublishProcessor.create<RxMsg>().toSerialized() }
    private val map = mutableMapOf<Any, MutableMap<String, MutableList<Disposable>>>()

    fun <T : Any> post(a: String, o: T) = SerializedSubscriber(bus).onNext(RxMsg(a, o))

    fun <T> flowable(clazz: Class<T>,
                     action: String,
                     scheduler: Scheduler = AndroidSchedulers.mainThread()): Flowable<T> {
        return bus.ofType(RxMsg::class.java).filter {
            it.action == action && clazz.isInstance(it.event)
        }.map { clazz.cast(it.event) }.observeOn(scheduler)
    }

    inline fun <reified T> flowable(action: String,
                                    scheduler: Scheduler = AndroidSchedulers.mainThread()): Flowable<T> =
            flowable(T::class.java, action, scheduler)

    fun <T> subscribe(clazz: Class<T>,
                      target: Any,
                      action: String,
                      scheduler: Scheduler = AndroidSchedulers.mainThread(),
                      call: (T) -> Unit): Disposable =
            flowable(clazz, action, scheduler).subscribe { call(it) }.also { obs ->
                map.getOrPut(target, { mutableMapOf() }).getOrPut(action, { mutableListOf() }).add(obs)
            }

    inline fun <reified T> subscribe(target: Any,
                                     action: String,
                                     scheduler: Scheduler = AndroidSchedulers.mainThread(),
                                     noinline call: (T) -> Unit): Disposable =
            subscribe(T::class.java, target, action, scheduler, call)

    fun unsubscribe(target: Any, action: String? = null) {
        map[target]?.let {
            if (action != null) it.remove(action)?.onEach { it.dispose() }
            else it.onEach { it.value.forEach { it.dispose() } }.clear()
            if (it.isEmpty()) map.remove(target)
        }
    }
}

inline fun <reified T : View> ViewParent.children() = (this as? ViewGroup)?.let { view ->
    (0..view.childCount).asSequence().mapNotNull { view.getChildAt(it) as? T }
} ?: emptySequence()

@SuppressLint("WrongViewCast")
class FAB @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    init {
        LayoutInflater.from(context).inflate(R.layout.fab, this, true).apply {
            orientation = LinearLayout.HORIZONTAL
            val a = context.obtainStyledAttributes(
                    attrs, R.styleable.FAB, defStyleAttr, defStyleRes)
            fabSrc = a.getDrawable(R.styleable.FAB_fab_src)
            fabLabel = a.getString(R.styleable.FAB_fab_label)
            a.recycle()
        }
    }

    var fabSrc: Drawable?
        get() = findViewById<FloatingActionButton>(R.id.fab_button)?.drawable
        set(value) {
            findViewById<FloatingActionButton>(R.id.fab_button).setImageDrawable(value)
        }
    var fabLabel: CharSequence?
        get() = findViewById<TextView>(R.id.fab_label).text
        set(value) {
            findViewById<TextView>(R.id.fab_label).apply {
                text = value
                visibility = if (value == null) View.GONE else View.VISIBLE
            }
        }
}

class FAM @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    var famSrc: Drawable? = null
        set(value) {
            findViewById<FloatingActionButton>(R.id.fam_button)?.setImageDrawable(value)
            field = value
        }

    init {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END
        val a = context.obtainStyledAttributes(attrs, R.styleable.FAM, defStyleAttr, defStyleRes)
        famSrc = a.getDrawable(R.styleable.FAM_fam_src)
        a.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        addView(LayoutInflater.from(context).inflate(R.layout.fam, this, false))
        children<LinearLayout>().forEach { it.visibility = View.INVISIBLE }
        findViewById<FloatingActionButton>(R.id.fam_button)?.let { fab ->
            fab.setImageDrawable(famSrc)
            fab.setOnClickListener {
                TransitionManager.beginDelayedTransition(this, TransitionSet().apply {
                    addTransition(Fade())
                    addTransition(Slide(Gravity.BOTTOM))
                })
                if (fab.rotation == 135F) {
                    fab.animate().rotation(0F).start()
                    children<FAB>().forEach { it.visibility = View.INVISIBLE }
                } else {
                    fab.animate().rotation(135F).start()
                    children<FAB>().forEach { it.visibility = View.VISIBLE }
                }
            }
        }
    }
}

fun Context.showInfo(name: String, url: String, info: List<Pair<String, List<Name>>>? = null, fn: ((List<Pair<String, List<Name>>>?) -> Unit)? = null) {
    RxMt.create {
        info ?: Album.attr(url.httpGet().jsoup())
    }.io2main().subscribe {
        it?.let { info ->
            alert().apply {
                setTitle(name)
                setPositiveButton("确定", null)
                create().apply {
                    info.joinToString("\n") {
                        "${it.first}: ${it.second.joinToString(", ")}"
                    }.spannable(info.flatMap { it.second }.filter { it is Link }.map { it as Link }) {
                        context.startActivity<ListActivity>("url" to it.url!!, "name" to it.name)
                        dismiss()
                    }.let { setMessage(it) }
                    show()
                    findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                }
            }
        } ?: toast("获取信息失败，请稍后重试。")
        fn?.invoke(it)
    }
}

fun Context.downloadAll(name: String, data: List<String>) = alert().apply {
    setTitle(name)
    setMessage("该图集共有${data.size}张图片，要下载吗")
    setPositiveButton("下载全部") { _, _ ->
        data.forEach { Save.download(it, name) }
        context.toast("添加下载队列完成，从通知栏查看下载进度。")
    }
    setNegativeButton("取消", null)
    create().show()
}

fun File.listFiles(reduce: Boolean): List<File> {
    return if (reduce) {
        mutableListOf<File>().apply {
            listFiles().let {
                addAll(it)
                it.filter { it.isDirectory }.forEach { addAll(it.listFiles(reduce)) }
            }
        }
    } else this.listFiles().toList()
}

object MtBackup {
    private val context by lazy { MainApplication.current() }
    private val objectbox by lazy { File(context.filesDir, "objectbox") }
    private val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    } else {
        Environment.getExternalStorageDirectory()
    }.also {
        if (!it.exists()) it.mkdirs()
    }.let {
        File(it, "${javaClass.`package`.name}.bak")
    }

    fun backup() {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            for (file in objectbox.listFiles(true)) {
                val name = file.toRelativeString(objectbox) + if (file.isDirectory) "/" else ""
                zip.putNextEntry(ZipEntry(name))
                if (file.isDirectory) continue
                BufferedInputStream(FileInputStream(file)).use {
                    it.copyTo(zip)
                }
            }
        }
        context.toast("备份完成: ${target.path}")
        val notify = NotificationCompat.Builder(context, "")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("数据备份")
                .setContentText(target.path)
        context.notificationManager.notify(0, notify.build())
    }

    fun restore() {
        if (!target.exists()) {
            context.toast("没有发现备份文件: ${target.path}")
            return
        }
        ZipInputStream(BufferedInputStream(FileInputStream(target))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val file = File(objectbox, entry.name)
                if (entry.isDirectory) {
                    if (!file.exists()) file.mkdirs()
                } else BufferedOutputStream(FileOutputStream(file)).use {
                    zip.copyTo(it)
                }
                zip.closeEntry()
            }
        }
    }
}