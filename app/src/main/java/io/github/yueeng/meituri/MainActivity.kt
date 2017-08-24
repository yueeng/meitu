package io.github.yueeng.meituri

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.jetbrains.anko.bundleOf
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.uiThread

/**
 * Main activity
 * Created by Rain on 2017/8/22.
 */

class MainActivity : AppCompatActivity() {

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        setFragment<ListFragment>(R.id.container) { bundleOf("url" to "http://www.meituri.com/") }
    }
}

class ListActivity : AppCompatActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        setFragment<ListFragment>(R.id.container) { intent.extras }
    }
}

class ListFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? {
        activity.title = name
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recycler.adapter = adapter
        recycler.loadMore(2) { query() }
        busy + view.findViewById<SwipeRefreshLayout>(R.id.swipe).apply {
            setOnRefreshListener {
                adapter.clear()
                uri = url
                query()
            }
        }
        return view
    }

    private val busy = ViewBinder(false, SwipeRefreshLayout::setRefreshing)
    private val url by lazy { arguments.getString("url")!! }
    private var uri: String? = null
    private val name by lazy { arguments.getString("name", "")!! }
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
            val list = dom?.select(".hezi li")?.map {
                Album(it.select(".biaoti a").text(),
                        it.select(".biaoti a").attr("abs:href")).apply {
                    image = it.select("img").attr("abs:src")
                    organ = Link(it.select("p:contains(机构) a"))
                    model = Link(it.select("p:contains(模特) a"))
                    tags = it.select("p:contains(类型) a").map { Link(it) }
                }
            }
            val next = dom?.select("#pages .current+a")?.attr("abs:href")
            uiThread {
                busy * false
                uri = next
                if (list != null) adapter.add(list)
            }
        }
    }

    private val adapter = ImageAdapter()

    inner class ImageHolder(view: View) : DataHolder<Album>(view) {
        private val image = view.findViewById<ImageView>(R.id.image)!!
        private val text1 = view.findViewById<TextView>(R.id.text1)!!
        val text2 = view.findViewById<TextView>(R.id.text2)!!
        override fun bind() {
            glide().load(value.image).into(image)
            text1.text = value.name
            text2.text = value.tags?.spannable(" ", { it.name }) {
                context.startActivity<ListActivity>("url" to it.url!!, "name" to it.name)
            }
        }

        init {
            text2.movementMethod = LinkMovementMethod.getInstance()
            view.setOnClickListener {
                context.startActivity<PreviewActivity>("url" to value.url!!, "name" to value.name)
            }
        }
    }

    inner class ImageAdapter : DataAdapter<Album, ImageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageHolder
                = ImageHolder(parent.inflate(R.layout.list_item))
    }
}