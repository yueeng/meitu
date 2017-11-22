package io.github.yueeng.meituri

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ComponentName
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.support.design.widget.NavigationView
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.CheckBox
import android.widget.TextView
import com.facebook.drawee.view.SimpleDraweeView
import org.jetbrains.anko.*

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
        val list = listOf(website to "首页",
                "$website/zhongguo/" to "中国美女",
                "$website/riben/" to "日本美女",
                "$website/taiwan/" to "台湾美女",
                "$website/hanguo/" to "韩国美女",
                "$website/mote/" to "美女库",
                "$website/jigou/" to "写真机构"/*, "" to "分类"*/)
        adapter.data += list
        val pager = findViewById<ViewPager>(R.id.container)
        pager.adapter = adapter
        val tabs: TabLayout = findViewById(R.id.tab)
        tabs.setupWithViewPager(pager)
        val drawer = findViewById<DrawerLayout>(R.id.drawer)
        val toggle = ActionBarDrawerToggle(this, drawer,
                findViewById(R.id.toolbar),
                R.string.app_name, R.string.app_name)
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        val navigation = findViewById<NavigationView>(R.id.navigation)
        list.forEachIndexed { i, it ->
            navigation.menu.add(when (i) {
                0 -> 0
                in 1..4 -> 1
                else -> 2
            }, 0x1000 + i, Menu.NONE, it.second).apply {
                icon = ContextCompat.getDrawable(this@MainActivity, when (i) {
                    0 -> R.drawable.ic_home
                    in 1..4 -> R.drawable.ic_girl
                    else -> R.drawable.ic_category
                })
            }
        }
        navigation.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.menu_favorite -> {
                    startActivity<FavoriteActivity>()
                    drawer.closeDrawer(navigation)
                    true
                }
                in 0x1000..0x1000 + list.size -> {
                    pager.currentItem = it.itemId - 0x1000
                    drawer.closeDrawer(navigation)
                    true
                }
                else -> false
            }
        }
    }

    override fun onBackPressed() {
        findViewById<DrawerLayout>(R.id.drawer).takeIf { it.isDrawerOpen(Gravity.START) }?.run {
            closeDrawer(Gravity.START)
        } ?: { super.onBackPressed() }()
    }
}

class MainAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
    val data = mutableListOf<Pair<String, String>>()
    override fun getItem(position: Int): Fragment = ListFragment().apply {
        arguments = bundleOf("url" to data[position].first, "name" to data[position].second)
    }

    override fun getCount(): Int = data.size
    override fun getPageTitle(position: Int): CharSequence = data[position].second
}

class FavoriteTagActivity : BaseSlideCloseActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_list)
        setSupportActionBar(findViewById(R.id.toolbar))
        title = intent.getStringExtra("name")
        setFragment<FavoriteFragment>(R.id.container) { intent.extras }
    }
}

class FavoriteActivity : BaseSlideCloseActivity() {
    private val adapter by lazy { FavoriteAdapter(supportFragmentManager) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_favorite)
        setSupportActionBar(findViewById(R.id.toolbar))
        title = "收藏"
        val pager = findViewById<ViewPager>(R.id.container)
        val tabs: TabLayout = findViewById(R.id.tab)
        pager.adapter = adapter
        tabs.setupWithViewPager(pager)
    }

    class FavoriteAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getItem(position: Int): Fragment = when (position) {
            0 -> FavoriteFragment()
            else -> FavoriteTagsFragment().apply {
                arguments = bundleOf("type" to position)
            }
        }

        override fun getCount(): Int = 4
        override fun getPageTitle(position: Int): CharSequence = when (position) {
            0 -> "图集"
            1 -> "模特"
            2 -> "机构"
            3 -> "标签"
            else -> throw IllegalArgumentException()
        }
    }
}

class FavoriteTagsFragment : Fragment() {
    private val type by lazy { arguments?.getInt("type")!! }
    private val adapter = ListAdapter()
    private val busy = ViewBinder(false, SwipeRefreshLayout::setRefreshing)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? =
            inflater.inflate(R.layout.fragment_list, container, false)

    override fun onDestroyView() {
        super.onDestroyView()
        view?.findViewById<RecyclerView>(R.id.recycler)?.adapter = null
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.list_columns))
        recycler.adapter = adapter
        recycler.loadMore { query() }
        busy + view.findViewById<SwipeRefreshLayout>(R.id.swipe).apply {
            setOnRefreshListener {
                adapter.clear()
                page = 0L
                query()
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        state?.let {
            page = state.getLong("page")
            adapter.add(state.getParcelableArrayList("data"))
        } ?: { query() }()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putLong("page", page)
        state.putParcelableArrayList("data", ArrayList(adapter.data))
    }

    private var page = 0L
    private fun query() {
        if (page == -1L || busy()) return
        busy * true
        dbFav.tags(type) {
            adapter.add(it)
            page = -1
            busy * false
        }
    }

    class TextHolder(view: View) : DataHolder<Link2>(view) {
        private val text1 = view.findViewById<TextView>(R.id.text1)
        private val text2 = view.findViewById<TextView>(R.id.text2)
        @SuppressLint("SetTextI18n")
        override fun bind() {
            text1.text = value.name
            text2.text = "${value.size}套"
        }

        init {
            view.setOnClickListener {
                value.url?.takeIf { it.isNotEmpty() }?.let {
                    context.startActivity<FavoriteTagActivity>("tag" to value.id, "url" to it, "name" to value.name)
                }
            }
        }
    }

    class ListAdapter : DataAdapter<Link2, DataHolder<Link2>>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataHolder<Link2> =
                TextHolder(parent.inflate(R.layout.list_organ_item))
    }
}

class FavoriteFragment : Fragment() {
    private val tag by lazy { arguments?.getLong("tag") ?: 0L }
    private val adapter = ListAdapter()
    private val busy = ViewBinder(false, SwipeRefreshLayout::setRefreshing)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? =
            inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.list_columns))
        recycler.adapter = adapter
        recycler.loadMore { query() }
        busy + view.findViewById<SwipeRefreshLayout>(R.id.swipe).apply {
            setOnRefreshListener {
                adapter.clear()
                page = 0L
                query()
            }
        }
        RxBus.instance.subscribe<String>(this, "favorite") { uri ->
            adapter.data.asSequence().mapIndexed { i, v -> i to v }
                    .filter { it.second.url == uri }.forEach { adapter.notifyItemChanged(it.first) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        RxBus.instance.unsubscribe(this)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        state?.let {
            page = state.getLong("page")
            adapter.add(state.getParcelableArrayList("data"))
        } ?: { query() }()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putLong("page", page)
        state.putParcelableArrayList("data", ArrayList(adapter.data))
    }

    private var page = 0L
    private val size = 10L
    private fun query() {
        if (page == -1L || busy()) return
        busy * true
        if (tag > 0) {
            dbFav.albums(tag) {
                adapter.add(it)
                page = -1
                busy * false
            }
        } else {
            dbFav.albums(page * size, size) {
                adapter.add(it)
                page = if (it.size.toLong() == size) page + 1 else -1
                busy * false
            }
        }
    }

    class ListAdapter : DataAdapter<Album, DataHolder<Album>>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataHolder<Album> =
                ListFragment.AlbumHolder(parent.inflate(R.layout.list_album_item))
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
            bundleOf("url" to search(key), "name" to key)
        } else intent.extras

        title = bundle.getString("name")
        setFragment<ListFragment>(R.id.container) { bundle }
    }
}

class ListFragment : Fragment() {
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.search, menu)
        val search = menu.findItem(R.id.search).actionView as SearchView
        val info = context?.searchManager?.getSearchableInfo(ComponentName(context, ListActivity::class.java))
        search.setSearchableInfo(info)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.favorite -> context?.startActivity<FavoriteActivity>().let { true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? =
            inflater.inflate(R.layout.fragment_list, container, false)

    override fun onDestroyView() {
        super.onDestroyView()
        view?.findViewById<RecyclerView>(R.id.recycler)?.adapter = null
        RxBus.instance.unsubscribe(this)
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.list_columns) * 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                        when (adapter.getItemViewType(position)) {
                            ListType.Title.value -> this@apply.spanCount
                            ListType.Info.value -> this@apply.spanCount
                            ListType.Category.value -> 1
                            else -> 2
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
        RxBus.instance.subscribe<String>(this, "favorite") { uri ->
            adapter.data.asSequence().mapIndexed { i, v -> i to v }
                    .filter { it.second is Album }.map { it.first to it.second as Album }
                    .filter { it.second.url == uri }.forEach { adapter.notifyItemChanged(it.first) }
        }
    }

    private val adapter = ListAdapter()
    private val busy = ViewBinder(false, SwipeRefreshLayout::setRefreshing)
    private val url by lazy { arguments?.getString("url")!! }
    private var uri: String? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        retainInstance = true
        setHasOptionsMenu(true)
        uri = url
        state?.let {
            uri = state.getString("uri")
            adapter.add(state.getParcelableArrayList("data"))
        } ?: { query() }()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString("uri", uri)
        state.putParcelableArrayList("data", ArrayList(adapter.data))
    }

    private fun query() {
        if (busy() || uri.isNullOrEmpty()) return
        busy * true
        val first = adapter.data.isEmpty()
        doAsync {
            val dom = uri!!.httpGet().jsoup()
            val list: List<Name>? = dom?.select(".hezi .title,.hezi li,.hezi_t li,.jigou li,.fenlei p,.shoulushuliang,.renwu")?.mapNotNull {
                when {
                    it.`is`(".hezi li") -> Album(it)
                    first && it.`is`(".hezi_t li") -> Model(it)
                    first && it.`is`(".jigou li") -> Organ(it)
                    first && it.`is`(".renwu") -> Info(it)
                    first && it.`is`(".shoulushuliang") -> Name(it.text())
                    first && it.`is`(".hezi .title") -> Name(it.text())
                    first && it.`is`(".fenlei p") -> Name(it.text())
                    else -> null
                }
            }
            val categories = uri?.takeIf { it == "$website/mote/" }?.let {
                dom?.select("#tag_ul li a")?.map { Link(it) }
            }
            val next = dom?.select("#pages .current+a,#pages span+a:not(.a1)")?.attr("abs:href")
            uiThread {
                busy * false
                uri = next
                if (categories?.size ?: 0 > 0) {
                    adapter.add(Name("分类"))
                    adapter.add(categories!!)
                    adapter.add(Name("模特"))
                }
                if (list != null) adapter.add(list)
            }
        }
    }

    class NameHolder(view: View) : DataHolder<Name>(view) {
        private val text1 = view.findViewById<TextView>(R.id.text1)
        override fun bind() {
            text1.text = value.name.spannable(value.name.numbers())
        }
    }

    class TextHolder(view: View) : DataHolder<Link>(view) {
        private val text1 = view.findViewById<TextView>(R.id.text1)
        override fun bind() {
            text1.text = value.name.spannable(value.name.numbers())
        }

        init {
            view.setOnClickListener {
                context.startActivity<ListActivity>("url" to value.uri, "name" to value.name)
            }
        }
    }

    class InfoHolder(view: View) : DataHolder<Info>(view) {
        private val image = view.findViewById<SimpleDraweeView>(R.id.image)
        private val text1 = view.findViewById<TextView>(R.id.text1)
        private val text2 = view.findViewById<TextView>(R.id.text2)
        private val text3 = view.findViewById<TextView>(R.id.text3)
        private val text4 = view.findViewById<TextView>(R.id.text4)
        override fun bind() {
            image.progress().load(value.image)
            text1.text = value.name
            text2.text = value.attr.joinToString { "${it.first}${it.second}" }
            text3.text = value.tag.spannable(" ", { it.name }) {
                context.startActivity<ListActivity>("url" to it.uri, "name" to it.name)
            }
            text3.visibility = if (value.tag.isEmpty()) View.GONE else View.VISIBLE
            text4.text = value.etc
        }
    }

    class OrganHolder(view: View) : DataHolder<Organ>(view) {
        private val text1 = view.findViewById<TextView>(R.id.text1)
        private val text2 = view.findViewById<TextView>(R.id.text2)
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

    class ModelHolder(view: View) : DataHolder<Model>(view) {
        private val image = view.findViewById<SimpleDraweeView>(R.id.image)
        private val text1 = view.findViewById<TextView>(R.id.text1)
        private val text2 = view.findViewById<TextView>(R.id.text2)
        @SuppressLint("SetTextI18n")
        override fun bind() {
            image.progress().load(value.image)
            text1.text = value.name
            text2.text = "${value.count}套"
            text2.visibility = if (value.count > 0) View.VISIBLE else View.GONE
        }

        init {
            view.setOnClickListener { context.startActivity<ListActivity>("url" to value.url!!, "name" to value.name) }
        }
    }

    class AlbumHolder(view: View) : DataHolder<Album>(view) {
        private val image: SimpleDraweeView = view.findViewById(R.id.image)
        private val text1: TextView = view.findViewById(R.id.text1)
        private val text2: TextView = view.findViewById(R.id.text2)
        private val text3: TextView = view.findViewById(R.id.text3)
        private val check: CheckBox = view.findViewById(R.id.check)
        @SuppressLint("SetTextI18n")
        override fun bind() {
            image.progress().load(value.image)
            text1.text = value.name
            text3.text = "${value.count}P"
            text3.visibility = if (value.count > 0) View.VISIBLE else View.GONE
            text2.text = value.info.spannable(" ", { it.name }) {
                context.startActivity<ListActivity>("url" to it.uri, "name" to it.name)
            }
            check.isChecked = value.url?.let { dbFav.exists(it) } ?: false
        }

        init {
            text2.movementMethod = LinkMovementMethod.getInstance()
            view.setOnClickListener {
                context.startActivity<CollectActivity>("album" to value)
            }
            check.setOnClickListener {
                value.url?.let { url ->
                    if (check.isChecked)
                        Album.from(url, value) {
                            dbFav.put(it ?: value)
                        }
                    else dbFav.del(url)
                }
            }
        }
    }

    class ListAdapter : DataAdapter<Name, DataHolder<Name>>() {
        override fun getItemViewType(position: Int): Int = when (get(position)) {
            is Album -> ListType.Album.value
            is Model -> ListType.Model.value
            is Organ -> ListType.Organ.value
            is Info -> ListType.Info.value
            is Link -> ListType.Category.value
            else -> ListType.Title.value
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataHolder<Name> = when (viewType) {
            ListType.Album.value -> AlbumHolder(parent.inflate(R.layout.list_album_item))
            ListType.Model.value -> ModelHolder(parent.inflate(R.layout.list_model_item))
            ListType.Organ.value -> OrganHolder(parent.inflate(R.layout.list_organ_item))
            ListType.Title.value -> NameHolder(parent.inflate(R.layout.list_text_item))
            ListType.Category.value -> TextHolder(parent.inflate(R.layout.list_text_item))
            ListType.Info.value -> InfoHolder(parent.inflate(R.layout.list_info_item))
            else -> throw IllegalArgumentException()
        }
    }

    enum class ListType(val value: Int) {
        Title(0), Album(1), Model(2), Organ(3), Info(4), Category(5)
    }
}