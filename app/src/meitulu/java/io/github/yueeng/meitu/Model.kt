package io.github.yueeng.meitu

import android.net.Uri
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Model
 * Created by Rain on 2017/11/30.
 */
val website = "https://www.meitulu.com"

val homes = listOf(website to "首页",
        "$website/xihuan/" to "精选美女",
        "$website/rihan/" to "日韩美女",
        "$website/gangtai/" to "港台美女",
        "$website/guochan/" to "国产美女")

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

    fun from(e: Element) = Album(Link(e.select(".p_title a"))).apply {
        _image = e.select("img").attr("abs:src")
        _count = e.select("p:contains(张)").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
        model = e.select("p:contains(模特：)").flatMap { it.childNodes() }.mapNotNull {
            when (it) {
                is TextNode -> it.text().trim().takeIf { it.isNotEmpty() }?.let { Link(it) }
                is Element -> Link(it.text(), it.attr("abs:href"))
                else -> null
            }
        }.drop(1).distinctBy { it.key }
        organ = e.select("p:contains(机构：) a").map { Link(it) }
        tags = e.select("p:contains(类型：) a,p:contains(标签：) a").map { Link(it) }
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

fun mtAlbumSequence(uri: String) = MtSequence(uri) {
    val first = it == uri
    val dom = it.httpGet().jsoup()
    val url = dom?.select("#pages .current+a,#pages span+a:not(.a1)")?.attr("abs:href")
    val list: List<Name>? = dom?.select(".zuixin,.boxs li,.hot_meinv li,.model_source li,.listtags,a.huanyipi")?.mapNotNull {
        when {
            it.`is`(".boxs li") -> AlbumEx.from(it)
            first && it.`is`(".hot_meinv li") -> ModelEx.from(it)
//            first && it.`is`(".listtags") -> OrganEx.from(it)
            first && it.`is`(".listtags") -> InfoEx.from(it)
            first && it.`is`(".model_source li") -> Link(it.select("a"))
            first && it.`is`(".zuixin") -> Name(it.text())
            first && it.`is`("a.huanyipi") -> Cmd(it.text(), "refresh")
            else -> null
        }
    }
    val categories = it.takeIf { first && it == "$website/xihuan/" }?.let {
        dom?.select("#tag_ul li a")?.map { Link(it) }
    }
    url to list.orEmpty() + categories?.takeIf { it.isNotEmpty() }?.let {
        listOf(Name("分类")) + it
    }.orEmpty()
}

fun mtCollectSequence(uri: String) = MtSequence(uri) {
    val dom = it.httpGet().jsoup()
    val data = dom?.select(".content img.content_img")?.map { it.attr("abs:src") }
    val url = dom?.select("#pages span+a:not(.a1)")?.attr("abs:href")
    url to data.orEmpty()
}
