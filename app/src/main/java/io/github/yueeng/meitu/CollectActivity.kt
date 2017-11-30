package io.github.yueeng.meitu

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.support.transition.TransitionManager
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import io.reactivex.rxkotlin.toObservable
import org.jetbrains.anko.toast

class CollectActivity : BaseSlideCloseActivity() {

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_container)
        setFragment<CollectFragment>(R.id.container) { intent.extras }
    }
}

class CollectFragment : Fragment() {
    private val album by lazy { arguments?.getParcelable<Album>("album")!! }
    private val name by lazy { album.name }
    private val url by lazy { album.url!! }
    private val mtseq by lazy { mtCollectSequence(url).apply { ob = { ui { footer() } } } }
    private val adapter by lazy { ImageAdapter() }
    private val busy = ViewBinder(false, SwipeRefreshLayout::setRefreshing)
    private var info: List<Pair<String, List<Name>>>? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? =
            inflater.inflate(R.layout.fragment_collect, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        setSupportActionBar(view.findViewById(R.id.toolbar))
        title = name
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = StaggeredGridLayoutManager(MtSettings.PREVIEW_LIST_COLUMN, StaggeredGridLayoutManager.VERTICAL)
        recycler.adapter = adapter
        recycler.supportsChangeAnimations = false
        recycler.loadMore { query() }
        busy + view.findViewById<SwipeRefreshLayout>(R.id.swipe).apply {
            setOnRefreshListener {
                adapter.clear()
                mtseq(url)
                query()
            }
        }
        view.findViewById<FAB>(R.id.button1).setOnClickListener {
            TransitionManager.beginDelayedTransition(recycler)
            (recycler.layoutManager as? StaggeredGridLayoutManager)?.let {
                it.spanCount = (it.spanCount + 1).takeIf { it <= MtSettings.MAX_PREVIEW_LIST_COLUMN } ?: 1
                MtSettings.PREVIEW_LIST_COLUMN = it.spanCount
            }
        }
        fun favStat(anim: Boolean = true) {
            val fab = view.findViewById<FAB>(R.id.button2)
            if (anim) TransitionManager.beginDelayedTransition(fab.findViewById<View>(R.id.fab_label).parent as ViewGroup)
            if (dbFav.exists(album.url!!)) {
                fab.fabLabel = "已收藏"
                fab.fabSrc = ContextCompat.getDrawable(context!!, R.drawable.ic_favorite_white)
            } else {
                fab.fabLabel = "收藏"
                fab.fabSrc = ContextCompat.getDrawable(context!!, R.drawable.ic_favorite_border)
            }
        }
        favStat(false)
        view.findViewById<FAB>(R.id.button2).setOnClickListener {
            if (dbFav.exists(album.url!!)) dbFav.del(album.url!!) else AlbumEx.from(album.url!!, album) {
                dbFav.put(it ?: album)
            }
        }
        view.findViewById<FAB>(R.id.button3).setOnClickListener {
            if (busy()) context?.toast("正在请求数据，请稍后重试。")
            else activity?.permissionWriteExternalStorage {
                query(true) { context?.downloadAll(name, adapter.data) }
            }
        }
        view.findViewById<FAB>(R.id.button4).setOnClickListener {
            context?.showInfo(name, url, info) { info = it }
        }
        RxBus.instance.subscribe<Int>(this, "hack_shared_elements") {
            recycler?.adapter?.notifyItemChanged(it)
        }
        RxBus.instance.subscribe<String>(this, "favorite") {
            favStat()
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        retainInstance = true
        setHasOptionsMenu(true)
        state?.let {
            mtseq(state.getBundle("uri"))
            adapter.add(state.getStringArrayList("data"))
        } ?: query()
        RxBus.instance.subscribe<Pair<Bundle, List<String>>>(this, "update_collect") {
            mtseq(it.first)
            adapter.add(it.second)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.instance.unsubscribe(this, "update_collect")
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putBundle("uri", mtseq())
        state.putStringArrayList("data", ArrayList(adapter.data))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        view?.findViewById<RecyclerView>(R.id.recycler)?.adapter = null
        RxBus.instance.unsubscribe(this, "hack_shared_elements")
        RxBus.instance.unsubscribe(this, "favorite")
    }

    private fun footer() {
        val msg = if (mtseq.empty()) "没有更多了" else "加载中，请稍候。"
        if (adapter.footer.isEmpty()) adapter.add(FooterDataAdapter.TYPE_FOOTER, msg)
        else adapter.replace(FooterDataAdapter.TYPE_FOOTER, 0, msg)
    }

    private fun query(all: Boolean = false, fn: (() -> Unit)? = null) {
        if (busy() || mtseq.empty()) {
            if (mtseq.empty()) fn?.invoke()
            return
        }
        busy * true
        mtseq.toObservable().let {
            if (all) it else it.take(10)
        }.toList().io2main().subscribe { list ->
            busy * false
            adapter.add(list)
            fn?.invoke()
        }
    }

    inner class ImageHolder(view: View) : DataHolder<String>(view) {
        private val text: TextView = view.findViewById(R.id.text1)
        private val image: ImageView = view.findViewById(R.id.image)
        private val progress: ProgressBar = view.findViewById(R.id.progress)
        private val image2: ImageView = view.findViewById(R.id.image2)
        @SuppressLint("SetTextI18n")
        override fun bind(i: Int) {
            GlideApp.with(image).load(value).crossFade().progress(value, progress).into(image)
            text.text = "${i + 1}"
            image2.visibility = if (Save.file(value, name).exists()) View.VISIBLE else View.INVISIBLE
        }

        init {
            view.setOnClickListener {
                activity?.let {
                    it.startActivity(Intent(it, PreviewActivity::class.java)
                            .putExtra("album", album)
                            .putExtra("data", ArrayList(adapter.data))
                            .putExtra("uri", mtseq())
                            .putExtra("index", adapterPosition))
                }
            }
        }
    }

    inner class ImageAdapter : FooterDataAdapter<String, ImageHolder>() {
        override fun onCreateHolder(parent: ViewGroup, viewType: Int): ImageHolder =
                ImageHolder(parent.inflate(R.layout.list_collect_item))

    }
}