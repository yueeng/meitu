package io.github.yueeng.meituri

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

/**
 * Preview activity
 * Created by Rain on 2017/8/23.
 */


class PreviewActivity : AppCompatActivity() {

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        setFragment<PreviewFragment>(R.id.container) { intent.extras }
    }
}

class PreviewFragment : Fragment() {
    private val url by lazy { arguments.getString("url") }
    private var uri: String? = null
    private val adapter = PreviewAdapter()
    private val busy = ViewBinder<Boolean, View>(false) { v, vt -> v.visibility = if (vt) View.VISIBLE else View.INVISIBLE }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_preview, container, false)
        val pager = view.findViewById<ViewPager>(R.id.pager)
        pager.adapter = adapter
        pager.offscreenPageLimit = 2
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (position >= adapter.data.size - 3) query()
            }
        })
        return view
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
                    adapter.data.addAll(list)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    inner class PreviewAdapter : PagerAdapter() {
        val data = mutableListOf<String>()
        override fun isViewFromObject(view: View?, `object`: Any?): Boolean = view == `object`

        override fun getCount(): Int = data.size
        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any?) {
            container.removeView(`object` as? View)
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val item = data[position]
            val view = container.inflate(R.layout.preview_item)
            val image = view.findViewById<ImageView>(R.id.image)
            glide().load(item).into(image)
            container.addView(view)
            return view
        }
    }
}