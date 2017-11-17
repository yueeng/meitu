package io.github.yueeng.meituri

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.samples.zoomable.DoubleTapGestureListener
import com.facebook.samples.zoomable.ZoomableDraweeView
import org.jetbrains.anko.*


/**
 * Preview activity
 * Created by Rain on 2017/8/23.
 */

class PreviewActivity : BaseSlideCloseActivity() {

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_preview)
        setSupportActionBar(findViewById(R.id.toolbar))
        setFragment<PreviewFragment>(R.id.container) { intent.extras }
    }

    override fun onBackPressed() {
        val preview = supportFragmentManager.findFragmentById(R.id.container) as PreviewFragment
        if (preview.onBackPressed()) return
        super.onBackPressed()
    }
}

@SuppressLint("SetTextI18n")
class PreviewFragment : Fragment() {
    private val album by lazy { arguments?.getParcelable<Album>("data")!! }
    private val name by lazy { album.name }
    private val url by lazy { album.url!! }
    private val count by lazy { album.count }
    private var uri: String? = null
    private val adapter by lazy { PreviewAdapter(name) }
    private val busy = ViewBinder<Boolean, View>(false) { v, vt -> v.visibility = if (vt) View.VISIBLE else View.INVISIBLE }
    private val page by lazy { ViewBinder<Int, TextView>(-1) { v, vt -> v.text = "${vt + 1}/$count" } }
    private var current
        get() = view?.findViewById<ViewPager>(R.id.pager)?.currentItem ?: -1
        set(value) {
            view?.findViewById<ViewPager>(R.id.pager)?.let { it.currentItem = value }
        }
    private val sliding get() = view?.findViewById<View>(R.id.sliding)?.let { BottomSheetBehavior.from(it) }
    private val thumb by lazy { ThumbAdapter(name) }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? =
            inflater.inflate(R.layout.fragment_preview, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        busy + view.findViewById(R.id.busy)
        val pager = view.findViewById<ViewPager>(R.id.pager)
        page + view.findViewById(R.id.text1)
        pager.adapter = adapter
//        pager.offscreenPageLimit = 2
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (position >= adapter.data.size - 3) query()
                page * position
            }
        })
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        recycler.adapter = thumb
        recycler.loadMore(2) { query() }

        sliding?.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(sheet: View, offset: Float) {
            }

            var last: Int = BottomSheetBehavior.STATE_COLLAPSED
            override fun onStateChanged(sheet: View, state: Int) {
                if (last == BottomSheetBehavior.STATE_COLLAPSED && state == BottomSheetBehavior.STATE_EXPANDED) {
                    recycler.smoothScrollToPosition(pager.currentItem)
                }
                when (state) {
                    BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_COLLAPSED -> last = state
                }
            }
        })

        view.findViewById<FloatingActionButton>(R.id.button1).setOnClickListener {
            activity?.permissionWriteExternalStorage {
                adapter.data[current].let { url ->
                    Save.download(url, name) {
                        if (it == DownloadManager.STATUS_SUCCESSFUL)
                            context?.toast("已经下载完成")
                        else
                            context?.toast("已经在下载队列中")
                    }
                }
            }
        }
        view.findViewById<View>(R.id.button2).setOnClickListener {
            context?.let { context ->
                doAsync {
                    info = info ?: Album.attr(url.httpGet().jsoup())
                    uiThread {
                        info?.let { info ->
                            AlertDialog.Builder(context)
                                    .setTitle(name)
                                    .setPositiveButton("确定", null)
                                    .create()
                                    .apply {
                                        info.joinToString("\n") {
                                            "${it.first}: ${it.second.joinToString(", ")}"
                                        }.spannable(info.flatMap { it.second }.filter { it is Link }.map { it as Link }) {
                                            context.startActivity<ListActivity>("url" to it.url!!, "name" to it.name)
                                            dismiss()
                                        }.let { setMessage(it) }
                                        show()
                                        findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                                    }
                        } ?: { context.toast("获取信息失败，请稍后重试。") }()
                    }
                }
            }
        }
        view.findViewById<View>(R.id.button3).setOnClickListener {
            context?.let { context ->
                PopupMenu(context, it).apply {
                    setForceShowIcon(true)
                    inflate(R.menu.preivew_more)
                    menu.findItem(R.id.menu_favorite).isChecked = dbFav.exists(album.url!!)
                    setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.menu_download_all -> activity?.permissionWriteExternalStorage { download() }
                            R.id.menu_favorite -> if (dbFav.exists(album.url!!)) dbFav.del(album.url!!) else Album.from(album.url!!, album) { dbFav.put(it ?: album) }
                            R.id.menu_thumb -> sliding?.open()
                        }
                        true
                    }
                }.show()
            }
        }
        context?.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private val tapPreview = RxBus.instance.flowable<Int>("tap_preview").subscribe {
        if (sliding?.isOpen == true)
            sliding?.close()
        else
            current++
    }
    private val tapThumb = RxBus.instance.flowable<Int>("tap_thumb").subscribe {
        current = it
        sliding?.close()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        context?.unregisterReceiver(receiver)
        view?.findViewById<RecyclerView>(R.id.recycler)?.adapter = null
    }

    override fun onDestroy() {
        super.onDestroy()
        tapPreview.dispose()
        tapThumb.dispose()
    }

    fun onBackPressed(): Boolean = sliding?.state?.takeIf { it == BottomSheetBehavior.STATE_EXPANDED }?.let {
        sliding?.close()
        true
    } ?: false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            context.downloadManager.query(DownloadManager.Query().apply {
                setFilterById(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1))
            }).takeIf { it.moveToFirst() }?.let { c ->
                c.getInt(DownloadManager.COLUMN_STATUS)
                        .takeIf { it == DownloadManager.STATUS_SUCCESSFUL }?.let {
                    view?.findViewById<ViewPager>(R.id.pager)
                            ?.findViewWithTag<View>(c.getString(DownloadManager.COLUMN_URI))
                            ?.findViewById<ImageView>(R.id.image2)
                            ?.fadeIn()
                }
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        uri = url
        retainInstance = true
        state?.let {
            page * state.getInt("page")
            uri = state.getString("uri")
            adapter.data.addAll(state.getStringArrayList("data"))
            thumb.add(state.getStringArrayList("thumb"))
            info = state.getParcelableArrayList<Bundle>("info")?.map {
                Pair<String, List<Name>>(it.getString("key"), it.getParcelableArrayList("value"))
            }
        } ?: { query() }()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putInt("page", page())
        state.putString("uri", uri)
        state.putStringArrayList("data", ArrayList(adapter.data))
        state.putStringArrayList("thumb", ArrayList(thumb.data))

        state.putParcelableArrayList("info", info?.map {
            bundleOf("key" to it.first).apply {
                putParcelableArrayList("value", ArrayList(it.second))
            }
        }?.let { ArrayList(it) })
    }

    private fun download() {
        query {
            if (uri != null)
                if (busy())
                    delay(500) { download() }
                else
                    download()
            else
                adapter.data.forEach { Save.download(it, name) }
        }
    }

    private var info: List<Pair<String, List<Name>>>? = null

    private fun query(call: (() -> Unit)? = null): Boolean {
        if (busy() || uri == null) {
            call?.invoke()
            return false
        }
        busy * true
        doAsync {
            val dom = uri!!.httpGet().jsoup()
            val list = dom?.select(".content img.tupian_img")?.map { it.attr("abs:src") }
            val next = dom?.select("#pages span+a")?.let {
                !it.`is`(".a1") to it.attr("abs:href")
            }
            val attr = info ?: Album.attr(dom)
            uiThread {
                busy * false
                uri = if (next?.first == true) next.second else null
                info = attr
                if (list != null) {
                    thumb.add(list)
                    adapter.data.addAll(list)
                    adapter.notifyDataSetChanged()
                    page * current
                }
                call?.invoke()
            }
        }
        return true
    }

    class PreviewAdapter(val name: String) : DataPagerAdapter<String>(R.layout.preview_item) {
        override fun bind(view: View, item: String, position: Int) {
            val image2: ImageView = view.findViewById(R.id.image2)
            image2.visibility = if (Save.file(item, name).exists()) View.VISIBLE else View.INVISIBLE
            view.findViewById<ZoomableDraweeView>(R.id.image)
                    .progress().load(item)
                    .setTapListener(object : DoubleTapGestureListener(view.findViewById(R.id.image)) {
                        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                            RxBus.instance.post("tap_preview", 1)
                            return true
                        }
                    })
        }
    }

    class ThumbHolder(view: View, val name: String) : DataHolder<String>(view) {
        private val text: TextView = view.findViewById(R.id.text1)
        private val image: SimpleDraweeView = view.findViewById(R.id.image)
        private val image2: ImageView = view.findViewById(R.id.image2)
        override fun bind(i: Int) {
            image.load(value).aspectRatio = 3F / 4F
            text.text = "${i + 1}"
            image2.visibility = if (Save.file(value, name).exists()) View.VISIBLE else View.INVISIBLE
        }

        init {
            view.setOnClickListener {
                RxBus.instance.post("tap_thumb", adapterPosition)
            }
            image.progress()
        }
    }

    class ThumbAdapter(val name: String) : DataAdapter<String, ThumbHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbHolder =
                ThumbHolder(parent.inflate(R.layout.preview_thumb_item), name)

    }
}