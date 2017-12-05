package io.github.yueeng.meitu

import android.net.Uri
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Model
 * Created by Rain on 2017/11/30.
 */
val website = "https://www.aitaotu.com"

val homes = listOf("$website/" to "首页",
        "$website/taotu/" to "美女套图",
        "$website/meinv/" to "美女大全",
        "$website/mxtp/" to "明星图片",
//        "$website/tushuo/" to "图说天下",
        "$website/weimei/" to "唯美图片",
        "$website/dmtp/" to "动漫图片",
        "$website/dwtp/" to "动物图片",
        "$website/dttp/" to "动态图片",
        "$website/yxtp/" to "游戏图片",
        "$website/cysj/" to "创意图片",
        "$website/zxtp/" to "装修图片",
        "$website/shxz/" to "生活写照",
        "$website/sjbz/" to "手机壁纸",
        "$website/zhuomian/" to "桌面壁纸",
        "$website/touxiang/" to "QQ头像"
)

fun search(key: String) = "$website/search/${Uri.encode(key)}"

object OrganEx {
    private val rgx = "(\\d+)\\s*套".toRegex(RegexOption.IGNORE_CASE)
    fun from(e: Element) = Organ(Link(e.select("a"))).apply {
        count = e.select("span").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
    }
}

object ModelEx {
    private val rgx = "(\\d+)\\s*套".toRegex(RegexOption.IGNORE_CASE)
    fun from(e: Element) = Model(Link(e.select("img").attr("alt"), e.select("a").attr("abs:href"))).apply {
        image = e.select("img").attr("abs:src")
//        count = e.select(".shuliang").text().let {
//            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
//        }
    }

    fun dz_tag(e: Element) = Model(Link(e.attr("data-tagname"), e.attr("abs:href"))).apply {
        image = "https://img.aitaotu.cc:8089/Thumb/Tagbg/${e.attr("data-tagcode")}_dz.jpg"
        flag = 1
    }
}

object InfoEx {
    fun from(e: Element) = Info(Link(e.select(".listtags_r h1"))).apply {
        image = e.select(".listtags_l img").attr("abs:src")
        attr = emptyList()
        tag = e.select(".listtags_r a").map { Link(it) }
        etc = e.select(".listtags_r p").map { it.text().trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}

object AlbumEx {
    private val rgx = "(\\d+)\\s*张".toRegex(RegexOption.IGNORE_CASE)

    fun from(e: Element) = Album(Link(e.selects(".thumb+.text>.txtc", ".title a", "span a", "p a", "a")?.text() ?: "", e.selects(".thumb a", ".title a", "span a", "p a", "a")?.attr("abs:href"))).apply {
        _image = e.selects(".item_t .img img", ".thumb img", "img")?.attrs("abs:data-original", "abs:src") ?: ""
        _count = e.select(".items_likes").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: e.select(".item_h_info_r_dt").text().tryInt()
        }
        model = emptyList()
        organ = emptyList()
        tags = e.select(".items_comment a,.thumb+.text>a").map(::Link)
    }

    fun attr(dom: Document?): List<Pair<String, List<Name>>>? = dom?.let { document ->
        document.select(".tsmaincont-main-cont-desc,.tsmaincont-desc span,.photo-fbl .fbl")
                .flatMap { it.childNodes() }
                .flatMap {
                    when (it) {
                        is TextNode -> it.text()?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
                        is Element -> when (it.tagName()) {
                            "a", "A" -> listOf(Link(it.text(), it.attr("abs:href")))
                            "span" -> it.text()?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
                            else -> emptyList()
                        }
                        else -> emptyList()
                    }
                }.fold<Any, MutableList<MutableList<Name>>>(mutableListOf()) { r, t ->
            r.apply {
                when (t) {
                    is Link -> r.last() += t
                    is String -> mutableListOf<Name>().apply {
                        r += apply { addAll(t.split("：").filter { it.isNotBlank() }.map(::Name)) }
                    }
                }
            }
        }.map { it.first().toString() to it.drop(1) }.filterNot { it.first.startsWith("提示") }
    }

    fun from(url: String, sample: Album? = null, fn: (Album?) -> Unit) {
        RxMt.create {
            url.httpGet().jsoup()?.let { dom ->
                attr(dom)?.toMap()?.let {
                    fun attr2links(name: String) = it[name]?.map {
                        when (it) {
                            is Link -> it
                            else -> Link(it.name)
                        }
                    } ?: emptyList()
                    Album(dom.select("h1").text(), url).apply {
                        model = emptyList()// attr2links("模特姓名").plus((sample?.model ?: emptyList())).distinctBy { it.key }
                        organ = emptyList()//attr2links("发行机构").plus((sample?.organ ?: emptyList())).distinctBy { it.key }
                        tags = attr2links("来源").plus(attr2links("标签")).plus((sample?.tags ?: emptyList())).distinctBy { it.key }
                        _image = sample?._image?.takeIf { it.isNotEmpty() } ?: dom.select(".big-pic img").firstOrNull()?.attr("abs:src") ?: ""
                        _count = 0/*sample?.count?.takeIf { it != 0 } ?: it["图片数量"]?.map { it.toString() }?.firstOrNull()?.let {
                            rgx.find(it)?.let { it.groups[1]?.value?.toInt() }
                        } ?: 0*/
                    }
                }
            }
        }.io2main().subscribe { fn(it) }
    }

}

fun mtAlbumSequence(uri: String) = mtSequence(uri) {
    val first = it == uri
    val dom = it.httpGet().jsoup()
    val url = dom?.select("#pageNum a.thisclass+a")?.attr("abs:href")
    val fn = listOf<Pair<String, (Element) -> Pair<Int, List<Name>>>>(
            ".taotu-main li:not(.longword)" to { e -> 0 to AlbumEx.from(e).option() },
            ".taotu-nav>a" to { e -> 0 to Link(e).option() },
            ".main .imgtag" to { e -> 0 to ModelEx.from(e).option() },
            "#Pnav3.Wc .index-kcont-bt strong a,#Pnav3.Wc~.Wc .index-kcont-bt strong a" to { e -> 0 to Title(Link(e)).option() },
            "#Pnav3.Wc .index-list-c a,#Pnav3.Wc~.Wc .index-list-c a" to { e -> 0 to AlbumEx.from(e).option() },
            ".item_list .item" to { e -> 0 to AlbumEx.from(e).option() }, //https://www.aitaotu.com/meinv/
            "#mainbody li" to { e -> 0 to AlbumEx.from(e).option() }, //https://www.aitaotu.com/tag/weimeinvsheng.html
            ".sut_lbtC_rnowc .sut_mxbt_L a" to { e -> 0 to Title(Link(e)).option() },
            ".sut_lbtC_rnowc .sliderbox dd" to { e -> 0 to AlbumEx.from(e).option() },
            ".topic_top .top_content p" to { e -> 0 to if (first) Name(e.text()).option() else emptyList() },
            ".ai-l-cls a" to { e -> 0 to if (first) Link(e).option() else emptyList() },
            ".list-topbq-c a" to { e -> 0 to if (first) Link(e).option() else emptyList() },
            ".Clbc_Game_r:eq(0) .lbc_Star_r_bt" to { e -> 1 to if (first) Name(e.text()).option() else emptyList() },
            ".Clbc_Game_r:eq(0) .Clbc_r_cont li" to { e -> 1 to if (first) AlbumEx.from(e).option() else emptyList() },
            ".dz_nav a:not(:contains(图说词条))" to { e ->
                0 to listOf(Title(Link(e))) + dom?.select(".dz_tag li")?.get(e.elementSiblingIndex())?.let {
                    it.select("a").map { ModelEx.dz_tag(it) }
                }.orEmpty()
            } //https://www.aitaotu.com/
    )
    val list: List<Name>? = dom?.select(fn.joinToString(",") { it.first })?.map { e ->
        fn.firstOrNull { e.`is`(it.first) }?.second?.invoke(e) ?: (0 to emptyList())
    }?.sortedByDescending { it.first }?.flatMap { it.second }
    url to list.orEmpty()
}

fun mtCollectSequence(uri: String) = mtSequence(uri) {
    val dom = it.httpGet().jsoup()
    val data = dom?.select(".big-pic img")?.map { Name(it.attr("abs:src")) }
    val url = dom?.select(".pages .thisclass+li a")?.attr("abs:href")
    url to data.orEmpty()
}
