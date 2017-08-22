package io.github.yueeng.meituri

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.jetbrains.anko.bundleOf
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class MainActivity : AppCompatActivity() {

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportFragmentManager.run {
            val fragment = findFragmentById(R.id.container) as? ListFragment
                    ?: ListFragment().apply { arguments = bundleOf("url" to "http://www.meituri.com/") }
            beginTransaction()
                    .replace(R.id.container, fragment)
                    .commit()
        }
    }
}

class ListFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recycler.adapter = adapter
        busy + view.findViewById<SwipeRefreshLayout>(R.id.swipe).apply {
            setOnRefreshListener {
                adapter.clear()
                query()
            }
        }
        return view
    }

    val busy = ViewBinder(false, SwipeRefreshLayout::setRefreshing)
    val url by lazy { arguments.getString("url")!! }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        query()
    }

    fun query() {
        if (busy()) return
        busy * true
        doAsync {
            val dom = url.httpGet().jsoup()
            val list = dom?.select(".hezi li")?.map {
                Album(it.select(".biaoti a").attr("abs:href"),
                        it.select(".biaoti a").text()).apply {
                    image = it.select("img").attr("abs:src")
                    organ = Link(it.select("p:contains(机构) a"))
                    model = Link(it.select("p:contains(模特) a"))
                    tags = it.select("p:contains(类型) a").map { Link(it) }
                }
            }
            uiThread {
                busy * false
                if (list != null) adapter.add(list)
            }
        }
    }

    val adapter = ImageAdapter()

    class ImageHolder(view: View) : DataHolder<Album>(view) {
        val image = view.findViewById<ImageView>(R.id.image)!!
        override fun bind() {
            image.picasso(value.image)
        }
    }

    class ImageAdapter : DataAdapter<Album, ImageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageHolder
                = ImageHolder(parent.inflate(R.layout.list_item))
    }
}