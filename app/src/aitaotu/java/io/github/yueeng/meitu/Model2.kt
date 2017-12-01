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

    fun dz_tag(e: Element) = Model(Link(""/*e.attr("data-tagname")*/, e.attr("abs:href"))).apply {
        image = "https://img.aitaotu.cc:8089/Thumb/Tagbg/${e.attr("data-tagcode")}_dz.jpg"
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

    fun from(e: Element) = Album(Link(e.selects(".title a", "p a", "a"))).apply {
        _image = e.selects(".item_t .img img", "img")?.attrs("abs:data-original", "abs:src") ?: ""
        _count = e.select(".items_likes").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: e.select(".item_h_info_r_dt").text().tryInt()
        }
        model = emptyList()
        organ = emptyList()
        tags = e.select(".items_comment a").map(::Link)
    }

    fun attr(dom: Document?): List<Pair<String, List<Name>>>? = dom?.let { document ->
        document.select("p.buchongshuoming").forEach { it.text(it.text()) }
        document.select(".c_l p,p.buchongshuoming, .fenxiang_l")
                .flatMap { it.childNodes() }
                .flatMap {
                    when (it) {
                        is TextNode -> it.text().trim().split("；").filter { it.isNotBlank() }
                                .map { it.split("：").joinToString("：") { it.trim() } }
                        is Element -> if (it.tagName() == "a") listOf(Link(it.text(), it.attr("abs:href"))) else emptyList()
                        else -> emptyList()
                    }
                }.fold<Any, MutableList<MutableList<Name>>>(mutableListOf()) { r, t ->
            r.apply {
                when (t) {
                    is String -> mutableListOf<Name>().apply {
                        r += apply { addAll(t.split("：").filter { it.isNotBlank() }.map(::Name)) }
                    }
                    is Link -> r.last() += t
                }
            }
        }.map { it.first().toString() to it.drop(1) }
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
                        model = attr2links("模特姓名").plus((sample?.model ?: emptyList())).distinctBy { it.key }
                        organ = attr2links("发行机构").plus((sample?.organ ?: emptyList())).distinctBy { it.key }
                        tags = attr2links("标签").plus((sample?.tags ?: emptyList())).distinctBy { it.key }
                        _image = sample?._image?.takeIf { it.isNotEmpty() } ?: dom.select(".content img.tupian_img").firstOrNull()?.attr("abs:src") ?: ""
                        _count = sample?.count?.takeIf { it != 0 } ?: it["图片数量"]?.map { it.toString() }?.firstOrNull()?.let {
                            rgx.find(it)?.let { it.groups[1]?.value?.toInt() }
                        } ?: 0
                    }
                }
            }
        }.io2main().subscribe { fn(it) }
    }

}

fun mtAlbumSequence(uri: String) = mtSequence(uri) {
    //    val first = it == uri
    val dom = it.httpGet().jsoup()
    val url = dom?.select("#pageNum a.thisclass+a")?.attr("abs:href")
    val fn = listOf<Pair<String, (Element) -> List<Name>>>(
            ".taotu-main li:not(.longword)" to { e -> AlbumEx.from(e).option() },
            ".taotu-nav>a" to { e -> Link(e).option() },
            ".main .imgtag" to { e -> ModelEx.from(e).option() },
            "#Pnav3.Wc .index-kcont-bt strong a,#Pnav3.Wc~.Wc .index-kcont-bt strong a" to { e -> Title(Link(e)).option() },
            "#Pnav3.Wc .index-list-c a,#Pnav3.Wc~.Wc .index-list-c a" to { e -> AlbumEx.from(e).option() },
            ".item_list .item" to { e -> AlbumEx.from(e).option() }, //https://www.aitaotu.com/meinv/
            "#mainbody li" to { e -> AlbumEx.from(e).option() }, //https://www.aitaotu.com/tag/weimeinvsheng.html
            ".dz_nav a" to { e ->
                listOf(Title(Link(e))) + dom?.select(".dz_tag li")?.get(e.elementSiblingIndex())?.let {
                    it.select("a").map { ModelEx.dz_tag(it) }
                }.orEmpty()
            } //https://www.aitaotu.com/
    )
    val title = listOf(Name::class.java, Title::class.java, Info::class.java)
    val list: List<Name>? = dom?.select(fn.joinToString(",") { it.first })?.flatMap { e ->
        fn.firstOrNull { e.`is`(it.first) }?.second?.invoke(e) ?: emptyList()
    }?.fold(mutableListOf()) { acc, name ->
        acc.apply {
            lastOrNull()?.takeIf { it.javaClass != name.javaClass }?.let { last ->
                if (!title.any { it == last.javaClass || it == name.javaClass }) add(Name(""))
            }
            add(name)
        }
    }
    url to list.orEmpty()
}

fun mtCollectSequence(uri: String) = mtSequence(uri) {
    val dom = it.httpGet().jsoup()
    val data = dom?.select(".big-pic img")?.map { Name(it.attr("abs:src")) }
    val url = dom?.select(".pages .thisclass+li a")?.attr("abs:href")
    url to data.orEmpty()
}
