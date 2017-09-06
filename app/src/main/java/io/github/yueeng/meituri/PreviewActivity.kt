package io.github.yueeng.meituri

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.*
import android.widget.TextView
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.samples.zoomable.ZoomableDraweeView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread


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
}

@SuppressLint("SetTextI18n")
class PreviewFragment : Fragment() {
    private val url by lazy { arguments.getString("url") }
    private val count by lazy { arguments.getInt("count") }
    private var uri: String? = null
    private val adapter = PreviewAdapter()
    private val busy = ViewBinder<Boolean, View>(false) { v, vt -> v.visibility = if (vt) View.VISIBLE else View.INVISIBLE }
    private val page by lazy { ViewBinder<Int, TextView>(-1) { v, vt -> v.text = "${vt + 1}/$count" } }
    private var current
        get() = view?.findViewById<ViewPager>(R.id.pager)?.currentItem ?: -1
        set(value) {
            view?.findViewById<ViewPager>(R.id.pager)?.let { it.currentItem = value }
        }
    private val sliding get() = view?.findViewById<DrawerLayout>(R.id.sliding)
    private val thumb = ThumbAdapter()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? =
            inflater.inflate(R.layout.fragment_preview, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        busy + view.findViewById(R.id.busy)
        val pager = view.findViewById<ViewPager>(R.id.pager)
        page + view.findViewById(R.id.text1)
        pager.adapter = adapter
        pager.offscreenPageLimit = 2
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (position >= adapter.data.size - 3) query()
                page * position
            }
        })
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recycler.adapter = thumb
        recycler.loadMore(2) { query() }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        uri = url
        query()
    }

    private fun query() {
        if (busy() || uri == null) return
        busy * true
        doAsync {
            val dom = uri!!.httpGet().jsoup()
            val list = dom?.select(".content img.tupian_img")?.map { it.attr("abs:src") }
            val next = dom?.select("#pages span+a")?.let {
                !it.`is`(".a1") to it.attr("abs:href")
            }
            uiThread {
                busy * false
                uri = if (next?.first == true) next.second else null
                if (list != null) {
                    thumb.add(list)
                    adapter.data.addAll(list)
                    adapter.notifyDataSetChanged()
                    page * current
                }
            }
        }
    }

    inner class PreviewAdapter : DataPagerAdapter<String>() {
        override fun bind(view: View, item: String, position: Int) {
            view.findViewById<ZoomableDraweeView>(R.id.image)
                    .progress().load(item)
                    .setTapListener(object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent?): Boolean {
                            current++
                            return super.onSingleTapUp(e)
                        }
                    })
        }
    }

    inner class ThumbHolder(view: View) : DataHolder<String>(view) {
        val text: TextView = view.findViewById(R.id.text1)
        val image: SimpleDraweeView = view.findViewById(R.id.image)
        override fun bind() {
            image.progress().load(value)
            text.text = "${adapter.data.indexOf(value) + 1}"
        }

        init {
            view.setOnClickListener {
                current = adapter.data.indexOf(value)
                sliding?.closeDrawers()
            }
        }
    }

    inner class ThumbAdapter : DataAdapter<String, ThumbHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbHolder =
                ThumbHolder(parent.inflate(R.layout.preview_thumb_item))

    }
}