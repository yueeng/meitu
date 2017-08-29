package io.github.yueeng.meituri

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
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
import java.lang.IllegalArgumentException

/**
 * Main activity
 * Created by Rain on 2017/8/22.
 */

class MainActivity : AppCompatActivity() {
    private val adapter by lazy { ListAdapter(supportFragmentManager) }
    private val pager by lazy { findViewById<ViewPager>(R.id.container) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        adapter.data += listOf("http://www.meituri.com/" to "首页",
                "http://www.meituri.com/zhongguo/" to "中国美女",
                "http://www.meituri.com/riben/" to "日本美女",
                "http://www.meituri.com/taiwan/" to "台湾美女",
                "http://www.meituri.com/hanguo/" to "韩国美女",
                "http://www.meituri.com/mote/" to "美女库",
                "http://www.meituri.com/jigou/" to "写真机构"/*, "" to "分类"*/)
        val tabs: TabLayout = findViewById(R.id.tab)
        pager.adapter = adapter
        tabs.setupWithViewPager(pager)
//        setFragment<ListFragment>(R.id.container) { bundleOf("url" to "http://www.meituri.com/") }
    }
}

class ListAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
    val data = mutableListOf<Pair<String, String>>()
    override fun getItem(position: Int): Fragment = ListFragment().apply {
        arguments = bundleOf("url" to data[position].first, "name" to data[position].second)
    }

    override fun getCount(): Int = data.size
    override fun getPageTitle(position: Int): CharSequence {
        return data[position].second
    }
}

class ListActivity : AppCompatActivity() {
    private val name by lazy { intent.getStringExtra("name")!! }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_list)
        setSupportActionBar(findViewById(R.id.toolbar))
        title = name

        setFragment<ListFragment>(R.id.container) { intent.extras }
    }
}

class ListFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? {
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
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        uri = url
        query()
    }

    private fun query() {
        if (busy() || uri.isNullOrEmpty()) return
        busy * true
        doAsync {
            val dom = uri!!.httpGet().jsoup()
            val list: List<Link>? = dom?.select(".hezi li,.hezi_t li,.jigou li")?.mapNotNull {
                when {
                    it.`is`(".hezi li") -> Album(it)
                    it.`is`(".hezi_t li") -> Model(it)
                    it.`is`(".jigou li") -> Organ(it)
                    else -> null
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

    private val adapter = ListAdapter()

    inner class OrganHolder(view: View) : DataHolder<Organ>(view) {
        private val text1 = view.findViewById<TextView>(R.id.text1)!!
        private val text2 = view.findViewById<TextView>(R.id.text2)!!
        @SuppressLint("SetTextI18n")
        override fun bind() {
            text1.text = value.name
            text2.text = "${value.count}套"
            text2.visibility = if (value.count > 0) View.VISIBLE else View.GONE
        }

        init {
            view.setOnClickListener { context.startActivity<ListActivity>("url" to value.url!!, "name" to value.name) }
        }
    }

    inner class ModelHolder(view: View) : DataHolder<Model>(view) {
        private val image = view.findViewById<ImageView>(R.id.image)!!
        private val text1 = view.findViewById<TextView>(R.id.text1)!!
        private val text2 = view.findViewById<TextView>(R.id.text2)!!
        @SuppressLint("SetTextI18n")
        override fun bind() {
            glide().load(value.image).into(image)
            text1.text = value.name
            text2.text = "${value.count}套"
            text2.visibility = if (value.count > 0) View.VISIBLE else View.GONE
        }

        init {
            view.setOnClickListener { context.startActivity<ListActivity>("url" to value.url!!, "name" to value.name) }
        }
    }

    inner class AlbumHolder(view: View) : DataHolder<Album>(view) {
        private val image = view.findViewById<ImageView>(R.id.image)!!
        private val text1 = view.findViewById<TextView>(R.id.text1)!!
        private val text2 = view.findViewById<TextView>(R.id.text2)!!
        private val text3 = view.findViewById<TextView>(R.id.text3)!!
        @SuppressLint("SetTextI18n")
        override fun bind() {
            glide().load(value.image).into(image)
            text1.text = value.name
            text3.text = "${value.count}P"
            text3.visibility = if (value.count > 0) View.VISIBLE else View.GONE
            text2.text = value.info.spannable(" ", { it.name }) {
                it.url?.run { context.startActivity<ListActivity>("url" to it.url, "name" to it.name) }
            }
        }

        init {
            text2.movementMethod = LinkMovementMethod.getInstance()
            view.setOnClickListener {
                context.startActivity<PreviewActivity>(
                        "url" to value.url!!,
                        "name" to value.name,
                        "count" to value.count
                )
            }
        }
    }

    inner class ListAdapter : DataAdapter<Link, DataHolder<Link>>() {
        override fun getItemViewType(position: Int): Int = when (get(position)) {
            is Album -> 0
            is Model -> 1
            is Organ -> 2
            else -> throw IllegalArgumentException()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataHolder<Link> = when (viewType) {
            0 -> AlbumHolder(parent.inflate(R.layout.list_album_item))
            1 -> ModelHolder(parent.inflate(R.layout.list_model_item))
            2 -> OrganHolder(parent.inflate(R.layout.list_organ_item))
            else -> throw IllegalArgumentException()
        }
    }
}