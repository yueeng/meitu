package io.github.yueeng.meituri

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.text.method.LinkMovementMethod
import android.view.*
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
    private val adapter by lazy { MainAdapter(supportFragmentManager) }
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
        val pager = findViewById<ViewPager>(R.id.container)
        val tabs: TabLayout = findViewById(R.id.tab)
        pager.adapter = adapter
        tabs.setupWithViewPager(pager)
    }
}

class MainAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
    val data = mutableListOf<Pair<String, String>>()
    override fun getItem(position: Int): Fragment = ListFragment().apply {
        arguments = bundleOf("url" to data[position].first, "name" to data[position].second)
    }

    override fun getCount(): Int = data.size
    override fun getPageTitle(position: Int): CharSequence {
        return data[position].second
    }
}

class ListActivity : BaseSlideCloseActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_list)
        setSupportActionBar(findViewById(R.id.toolbar))
        val bundle = if (intent.hasExtra(SearchManager.QUERY)) {
            val key = intent.getStringExtra(SearchManager.QUERY)
            val suggestions = SearchRecentSuggestions(this, SearchHistoryProvider.AUTHORITY, SearchHistoryProvider.MODE)
            suggestions.saveRecentQuery(key, null)
            bundleOf("url" to "http://www.meituri.com/search/${Uri.encode(key)}", "name" to key)
        } else intent.extras

        title = bundle.getString("name")
        setFragment<ListFragment>(R.id.container) { bundle }
    }
}

class ListFragment : Fragment() {
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.search, menu)
        val search = menu.findItem(R.id.search).actionView as SearchView
        val manager = context.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val info = manager.getSearchableInfo(ComponentName(context, ListActivity::class.java))
        search.setSearchableInfo(info)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? =
            inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = GridLayoutManager(context, 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                        when (adapter.getItemViewType(position)) {
                            ListType.Title.value -> this@apply.spanCount
                            else -> 1
                        }
            }
        }
        recycler.adapter = adapter
        recycler.loadMore(2) { query() }
        busy + view.findViewById<SwipeRefreshLayout>(R.id.swipe).apply {
            setOnRefreshListener {
                adapter.clear()
                uri = url
                query()
            }
        }
    }

    private val busy = ViewBinder(false, SwipeRefreshLayout::setRefreshing)
    private val url by lazy { arguments.getString("url")!! }
    private var uri: String? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setHasOptionsMenu(true)
        uri = url
        query()
    }

    private fun query() {
        if (busy() || uri.isNullOrEmpty()) return
        busy * true
        doAsync {
            val dom = uri!!.httpGet().jsoup()
            val list: List<Link>? = dom?.select(".hezi .title,.hezi li,.hezi_t li,.jigou li,.shoulushuliang")?.mapNotNull {
                when {
                    it.`is`(".hezi li") -> Album(it)
                    it.`is`(".hezi_t li") -> Model(it)
                    it.`is`(".jigou li") -> Organ(it)
                    it.`is`(".shoulushuliang") -> Link(it.text())
                    it.`is`(".hezi .title") -> Link(it.text())
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

    inner class TextHolder(view: View) : DataHolder<Link>(view) {
        private val text1 = view.findViewById<TextView>(R.id.text1)!!
        @SuppressLint("SetTextI18n")
        override fun bind() {
            text1.text = value.name
        }
    }

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
            is Album -> ListType.Album.value
            is Model -> ListType.Model.value
            is Organ -> ListType.Organ.value
            else -> ListType.Title.value
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataHolder<Link> = when (viewType) {
            ListType.Album.value -> AlbumHolder(parent.inflate(R.layout.list_album_item))
            ListType.Model.value -> ModelHolder(parent.inflate(R.layout.list_model_item))
            ListType.Organ.value -> OrganHolder(parent.inflate(R.layout.list_organ_item))
            ListType.Title.value -> TextHolder(parent.inflate(R.layout.list_text_item))
            else -> throw IllegalArgumentException()
        }
    }

    enum class ListType(val value: Int) {
        Album(1), Model(2), Organ(3), Title(0)
    }
}