package io.github.yueeng.meitu

import android.net.Uri
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Model
 * Created by Rain on 2017/11/30.
 */
val website = "http://www.meituri.com"

val homes = listOf(website to "首页",
        "$website/zhongguo/" to "中国美女",
        "$website/riben/" to "日本美女",
        "$website/taiwan/" to "台湾美女",
        "$website/hanguo/" to "韩国美女",
        "$website/mote/" to "美女库",
        "$website/jigou/" to "写真机构"/*, "" to "分类"*/)

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
    fun from(e: Element) = Model(Link(e.select("p a"))).apply {
        image = e.select("img").attr("abs:src")
        count = e.select(".shuliang").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
    }
}

object InfoEx {

    fun from(e: Element) = Info(Link(e.select(".renwu .right h1"))).apply {
        image = e.select(".renwu .left img").attr("abs:src")
        attr = e.select(".renwu .right span").filter { it.nextSibling() is TextNode }.map {
            it.text() to (it.nextSibling() as TextNode).text()
        }
        tag = e.select(".renwu .shuoming a").map { Link(it) }
        e.select(".renwu .shuoming p").remove()
        etc = e.select(".renwu .shuoming").text()
    }
}

object AlbumEx {
    private val rgx = "(\\d+)\\s*P".toRegex(RegexOption.IGNORE_CASE)

    fun from(e: Element) = Album(Link(e.select(".biaoti a"))).apply {
        _image = e.select("img").attr("abs:src")
        _count = e.select(".shuliang").text().let {
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

    fun attr(dom: Document?): List<Pair<String, List<Name>>>? = dom?.select(".tuji p,.shuoming p, .fenxiang_l")?.flatMap { it.childNodes() }?.flatMap {
        when (it) {
            is TextNode -> it.text().trim().split("；").filter { it.isNotBlank() }
                    .map { it.split("：").joinToString("：") { it.trim() } }
            is Element -> listOf(Link(it.text(), it.attr("abs:href")))
            else -> emptyList()
        }
    }?.fold<Any, MutableList<MutableList<Name>>>(mutableListOf()) { r, t ->
        r.apply {
            when (t) {
                is String -> mutableListOf<Name>().apply {
                    r += apply { addAll(t.split("：").filter { it.isNotBlank() }.map(::Name)) }
                }
                is Link -> r.last() += t
            }
        }
    }?.map { it.first().toString() to it.drop(1) }

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
                        model = attr2links("出镜模特").plus((sample?.model ?: emptyList())).distinctBy { it.key }
                        organ = attr2links("拍摄机构").plus((sample?.organ ?: emptyList())).distinctBy { it.key }
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
    val list: List<Name>? = dom?.select(".hezi .title,.hezi li,.hezi_t li,.jigou li,.fenlei p,.shoulushuliang,.renwu")?.mapNotNull {
        when {
            it.`is`(".hezi li") -> AlbumEx.from(it)
            first && it.`is`(".hezi_t li") -> ModelEx.from(it)
            first && it.`is`(".jigou li") -> OrganEx.from(it)
            first && it.`is`(".renwu") -> InfoEx.from(it)
            first && it.`is`(".shoulushuliang") -> Name(it.text())
            first && it.`is`(".hezi .title") -> Name(it.text())
            first && it.`is`(".fenlei p") -> Name(it.text())
            else -> null
        }
    }
    val categories = it.takeIf { first && it == "$website/mote/" }?.let {
        dom?.select("#tag_ul li a")?.map { Link(it) }
    }
    url to categories?.takeIf { it.isNotEmpty() }?.let {
        listOf(Name("分类")) + it + Name("模特")
    }.orEmpty() + list.orEmpty()
}

fun mtCollectSequence(uri: String) = MtSequence(uri) {
    val dom = it.httpGet().jsoup()
    val data = dom?.select(".content img.tupian_img")?.map { Name(it.attr("abs:src")) }
    val url = dom?.select("#pages span+a:not(.a1)")?.attr("abs:href")
    url to data.orEmpty()
}
