package io.github.yueeng.meitu

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ComponentName
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager.widget.ViewPager
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import io.reactivex.rxkotlin.toObservable
import org.jetbrains.anko.searchManager
import org.jetbrains.anko.startActivity
import java.util.*

/**
 * Main activity
 * Created by Rain on 2017/8/22.
 */

class MainActivity : DayNightAppCompatActivity() {
    private val adapter by lazy { MainAdapter(supportFragmentManager) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        adapter.data += homes
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
        homes.forEachIndexed { i, it ->
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
                R.id.menu_favorite -> consumer {
                    startActivity<FavoriteActivity>()
                    drawer.closeDrawer(navigation)
                }
                in 0x1000..0x1000 + homes.size -> consumer {
                    pager.currentItem = it.itemId - 0x1000
                    drawer.closeDrawer(navigation)
                }
                else -> false
            }
        }
        update(true)
    }

    override fun onBackPressed() {
        findViewById<DrawerLayout>(R.id.drawer).takeIf { it.isDrawerOpen(GravityCompat.START) }?.run {
            closeDrawer(GravityCompat.START)
        } ?: super.onBackPressed()
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        permissionWriteExternalStorage(true) { dbFav.reset { MtBackup.backup(true) } }
//    }
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
        @Suppress("ConstantConditionIf")
        val data = mutableListOf(0 to "图集").also {
            if (BuildConfig.fav_model) it.add(1 to "模特")
            if (BuildConfig.fav_organ) it.add(2 to "机构")
            it.add(3 to "标签")
        }.toList()

        override fun getItem(position: Int): Fragment = when (position) {
            0 -> FavoriteFragment()
            else -> FavoriteTagsFragment().apply {
                arguments = bundleOf("type" to data[position].first)
            }
        }

        override fun getCount(): Int = data.size
        override fun getPageTitle(position: Int): CharSequence = data[position].second
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
        recycler.supportsChangeAnimations = false
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
            adapter.add(state.getParcelableArrayList("data")!!)
        } ?: query()
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
                context.startActivity<FavoriteTagActivity>("tag" to value.id, "url" to value.uri, "name" to value.name)
            }
        }
    }

    class ListAdapter : AnimDataAdapter<Link2, DataHolder<Link2>>() {
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
        recycler.supportsChangeAnimations = false
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
                    .filter { it.second.url == uri }.forEach { adapter.notifyItemChanged(it.first, "favorite") }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        RxBus.instance.unsubscribe(this)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.NONE, 0x1000, Menu.NONE, "打开").setIcon(R.drawable.ic_open).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        0x1000 -> consumer {
            dbFav.tag(tag) {
                it?.let { link ->
                    context?.startActivity<ListActivity>("url" to link.uri, "name" to link.name)
                }
            }
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (tag > 0) setHasOptionsMenu(true)
        state?.let {
            page = state.getLong("page")
            adapter.add(state.getParcelableArrayList("data")!!)
        } ?: query()
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

    class ListAdapter : AnimDataAdapter<Album, DataHolder<Album>>() {
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
        menu.setIconEnable(true)
        inflater.inflate(R.menu.search, menu)
        val search = menu.findItem(R.id.search).actionView as SearchView
        val info = context?.searchManager?.getSearchableInfo(ComponentName(context!!, ListActivity::class.java))
        search.setSearchableInfo(info)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.favorite -> consumer { context?.startActivity<FavoriteActivity>() }
        R.id.daynight -> consumer {
            val current = MtSettings.DAY_NIGHT_MODE
            val items = listOf(AppCompatDelegate.MODE_NIGHT_AUTO to "自动",
                    AppCompatDelegate.MODE_NIGHT_NO to "白天",
                    AppCompatDelegate.MODE_NIGHT_YES to "夜晚",
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to "跟随系统")
            activity?.alert()
                    ?.setTitle("夜间模式")
                    ?.setSingleChoiceItems(items.map { it.second }.toTypedArray(), items.indexOfFirst { it.first == current }, null)
                    ?.setPositiveButton("确定") { d, _ ->
                        (d as? AlertDialog)?.listView?.checkedItemPosition?.let {
                            items[it].first.takeIf { mode -> mode != MtSettings.DAY_NIGHT_MODE }?.let { m ->
                                MtSettings.DAY_NIGHT_MODE = m
                                RxBus.instance.post("day_night", MtSettings.DAY_NIGHT_MODE)
                            }
                        }
                    }
                    ?.setNegativeButton("取消", null)
                    ?.create()?.show()
        }
        R.id.backup -> consumer {
            context?.alert()
                    ?.setTitle("备份")
                    ?.setMessage("备份本地收藏数据，重装应用后可以恢复之前的收藏。" +
                            MtBackup.time.takeIf { it != 0L }?.let { "\n最后备份时间：${Date(it)}" }.orEmpty())
                    ?.setPositiveButton("备份") { _, _ ->
                        activity?.permissionWriteExternalStorage { dbFav.reset { MtBackup.backup() } }
                    }
                    ?.setNeutralButton("还原") { _, _ ->
                        activity?.permissionWriteExternalStorage {
                            dbFav.reset { MtBackup.restore() }
                            RxBus.instance.post("day_night", MtSettings.DAY_NIGHT_MODE)
                        }
                    }
                    ?.setNegativeButton("取消", null)
                    ?.create()?.show()
        }
        R.id.update -> consumer { context?.update() }
        R.id.about -> consumer {
            context?.alert()?.setTitle(R.string.app_name)
                    ?.setMessage("当前版本：$version")
                    ?.setPositiveButton("发布页") { _, _ -> context?.openWeb(github) }
                    ?.setNeutralButton("检测升级") { _, _ -> context?.update() }
                    ?.setNegativeButton("取消", null)
                    ?.create()?.show()
        }
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
                            ListType.Sp.value, ListType.Name.value, ListType.Title.value, ListType.Info.value -> this@apply.spanCount
                            ListType.Link.value -> 1
                            ListType.Album.value, ListType.Model.value, ListType.Organ.value -> 2
                            else -> this@apply.spanCount
                        }
            }
        }
        recycler.adapter = adapter
        recycler.itemAnimator = null
        recycler.loadMore(2) { query() }
        busy + view.findViewById<SwipeRefreshLayout>(R.id.swipe).apply {
            setOnRefreshListener {
                adapter.clear()
                mtseq(url)
                query()
            }
        }
        RxBus.instance.subscribe<String>(this, "favorite") { uri ->
            adapter.data.asSequence().mapIndexed { i, v -> i to v }
                    .filter { it.second is Album }.map { it.first to it.second as Album }
                    .filter { it.second.url == uri }.forEach {
                        adapter.notifyItemChanged(it.first, "favorite")
                    }
        }
        RxBus.instance.subscribe<String>(this, "command") {
            adapter.clear()
            mtseq(url)
            query()
        }
    }

    private val adapter = ListAdapter()
    private val busy = ViewBinder(false, SwipeRefreshLayout::setRefreshing)
    private val url by lazy { arguments?.getString("url")!! }
    private val mtseq by lazy { mtAlbumSequence(url).apply { ob = { ui { footer() } } } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        retainInstance = true
        setHasOptionsMenu(true)
        state?.let {
            mtseq(state.getBundle("uri")!!)
            adapter.add(state.getParcelableArrayList("data")!!)
        } ?: query()
    }

    private fun footer() {
        val msg = if (mtseq.empty()) "没有更多了" else "加载中，请稍候。"
        if (adapter.footer.isEmpty()) adapter.add(FooterDataAdapter.TYPE_FOOTER, msg)
        else adapter.replace(FooterDataAdapter.TYPE_FOOTER, 0, msg)
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putBundle("uri", mtseq())
        state.putParcelableArrayList("data", ArrayList(adapter.data))
    }

    @SuppressLint("CheckResult")
    private fun query() {
        if (busy() || mtseq.empty()) return
        busy * true
        val title = listOf(Name::class.java, Title::class.java, Info::class.java)
        mtseq.toObservable().take(20).io2main().doOnComplete { busy * false }.subscribe { item ->
            adapter.data.lastOrNull()?.takeIf { it.javaClass != item.javaClass }?.let { last ->
                if (!title.any { it == last.javaClass || it == item.javaClass }) adapter.add(Name(""))
            }
            adapter.add(item)
        }
    }

    class NameHolder(view: View) : DataHolder<Name>(view) {
        private val text1 = view.findViewById<TextView>(R.id.text1)
        override fun bind() {
            text1.text = value.name.spannable(value.name.numbers())
        }
    }

    class TitleHolder(view: View) : DataHolder<Title>(view) {
        private val text1 = view.findViewById<TextView>(R.id.text1)
        override fun bind() {
            text1.text = value.name
        }

        init {
            view.setOnClickListener {
                if (value.url?.startsWith("cmd:") == true)
                    RxBus.instance.post("cmd", value.url?.substring("cmd:".length) ?: "")
                else
                    context.startActivity<ListActivity>("url" to value.uri, "name" to value.name)
            }
        }
    }

    class LinkHolder(view: View) : DataHolder<Link>(view) {
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
        private val image = view.findViewById<ImageView>(R.id.image)
        private val text1 = view.findViewById<TextView>(R.id.text1)
        private val text2 = view.findViewById<TextView>(R.id.text2)
        private val text3 = view.findViewById<TextView>(R.id.text3)
        private val text4 = view.findViewById<TextView>(R.id.text4)
        private val progress: ProgressBar = view.findViewById(R.id.progress)
        override fun bind() {
            GlideApp.with(image).load(value.image referer value.referer).crossFade()
                    .progress(value.image, progress).into(image)
            text1.text = value.name
            text3.text = value.tag.spannable(" ", { it.name }) {
                context.startActivity<ListActivity>("url" to it.uri, "name" to it.name)
            }
            text3.visibility = if (value.tag.isEmpty()) View.GONE else View.VISIBLE
            if (value.attr.isNotEmpty()) {
                text2.text = value.attr.joinToString { "${it.first}${it.second}" }
                text4.text = value.etc
            } else {
                text2.text = value.etc
                text4.text = ""
            }
            text4.visibility = if (text4.text.isNotEmpty()) View.VISIBLE else View.GONE
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
        private val image = view.findViewById<ImageView>(R.id.image)
        private val text1 = view.findViewById<TextView>(R.id.text1)
        private val text2 = view.findViewById<TextView>(R.id.text2)
        private val progress: ProgressBar = view.findViewById(R.id.progress)
        @SuppressLint("SetTextI18n")
        override fun bind() {
            GlideApp.with(image).load(value.image referer value.referer).crossFade()
                    .progress(value.image, progress).into(image)
            text1.text = value.name
            text1.visibility = if (value.flag == 0 && value.name.isNotEmpty()) View.VISIBLE else View.GONE
            text2.text = "${value.count}套"
            text2.visibility = if (value.count > 0) View.VISIBLE else View.GONE
        }

        init {
            view.setOnClickListener { context.startActivity<ListActivity>("url" to value.url!!, "name" to value.name) }
        }
    }

    class AlbumHolder(view: View) : DataHolder<Album>(view) {
        private val image: ImageView = view.findViewById(R.id.image)
        private val check: CheckBox = view.findViewById(R.id.check)
        private val text1: TextView = view.findViewById(R.id.text1)
        private val text2: TextView = view.findViewById(R.id.text2)
        private val text3: TextView = view.findViewById(R.id.text3)
        private val progress: ProgressBar = view.findViewById(R.id.progress)

        @SuppressLint("SetTextI18n")
        override fun bind(i: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) {
                GlideApp.with(image).load(value.image referer value.referer).crossFade()
                        .progress(value.image, progress)
                        .into(image)
                text1.text = value.name
                text3.text = "${value.count}P"
                text3.visibility = if (value.count > 0) View.VISIBLE else View.GONE
                text2.text = value.info.spannable(" ", { it.name }) {
                    context.startActivity<ListActivity>("url" to it.uri, "name" to it.name)
                }
            }
            check.isChecked = value.url?.let { dbFav.exists(it) } ?: false
        }

        init {
            text2.movementMethod = LinkMovementMethod.getInstance()
            view.setOnClickListener {
                context.startActivity<CollectActivity>("album" to value)
            }
            view.setOnLongClickListener {
                consumer {
                    val album = value
                    context.asActivity()?.let { activity ->
                        activity.wrapper().popupMenu(image).apply {
                            setForceShowIcon(true)
                            inflate(R.menu.list_more)
                            menu.findItem(R.id.menu_favorite).isChecked = dbFav.exists(album.url!!)
                            setOnMenuItemClickListener { mi ->
                                consumer {
                                    when (mi.itemId) {
                                        R.id.menu_download_all -> activity.permissionWriteExternalStorage {
                                            mtCollectSequence(album.url).toObservable().toList()
                                                    .io2main().subscribe { list ->
                                                        activity.downloadAll(album.name, list)
                                                    }
                                        }
                                        R.id.menu_favorite -> if (dbFav.exists(album.url)) dbFav.del(album.url) else AlbumEx.from(album.url, album) { ab ->
                                            dbFav.put(ab ?: album)
                                        }
                                        R.id.menu_thumb -> activity.startActivity<PreviewActivity>("album" to album)
                                        R.id.menu_info -> context.showInfo(album.name, album.url)
                                    }
                                }
                            }
                            show()
                        }
                    }
                }
            }
            check.setOnClickListener {
                value.url?.let { url ->
                    if (check.isChecked)
                        AlbumEx.from(url, value) { album ->
                            dbFav.put(album ?: value)
                        }
                    else dbFav.del(url)
                }
            }
        }
    }

    class ListAdapter : FooterDataAdapter<Name, DataHolder<Name>>() {
        override fun getItemType(position: Int): Int = when (get(position)) {
            is Album -> ListType.Album.value
            is Model -> ListType.Model.value
            is Organ -> ListType.Organ.value
            is Info -> ListType.Info.value
            is Title -> ListType.Title.value
            is Link -> ListType.Link.value
            else -> get(position).takeIf { it.name.isNotEmpty() }?.let { ListType.Name.value }
                    ?: ListType.Sp.value
        }

        override fun onCreateHolder(parent: ViewGroup, viewType: Int): DataHolder<Name> = when (viewType) {
            ListType.Album.value -> AlbumHolder(parent.inflate(R.layout.list_album_item))
            ListType.Model.value -> ModelHolder(parent.inflate(R.layout.list_model_item))
            ListType.Organ.value -> OrganHolder(parent.inflate(R.layout.list_organ_item))
            ListType.Title.value -> TitleHolder(parent.inflate(R.layout.list_text_item))
            ListType.Link.value -> LinkHolder(parent.inflate(R.layout.list_category_item))
            ListType.Name.value -> NameHolder(parent.inflate(R.layout.list_text_item))
            ListType.Info.value -> InfoHolder(parent.inflate(R.layout.list_info_item))
            ListType.Sp.value -> DataHolder(View(parent.context).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0) })
            else -> throw IllegalArgumentException()
        }
    }

    enum class ListType(val value: Int) {
        Name(0), Album(1),
        Model(2), Organ(3),
        Info(4), Link(5),
        Title(6), Sp(7)
    }
}