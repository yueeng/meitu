@file:Suppress("UNUSED_PARAMETER")

package io.github.yueeng.meituri

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * Data model
 * Created by Rain on 2017/8/22.
 */

val website = "http://www.meituri.com"

open class Link(val name: String, val url: String? = null) {
    constructor(e: Elements) : this(e.text(), e.attrs("abs:href"))
    constructor(e: Element) : this(e.text(), e.attrs("abs:href"))
}

class Organ(name: String, url: String? = null) : Link(name, url) {
    private var _count = 0
    val count get() = _count

    companion object {
        val rgx = "(\\d+)\\s*套".toRegex(RegexOption.IGNORE_CASE)
    }

    constructor(e: Link) : this(e.name, e.url)
    constructor(e: Element) : this(Link(e.select("a"))) {
        _count = e.select("span").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
    }
}

class Model(name: String, url: String? = null) : Link(name, url) {
    private lateinit var _image: String
    private var _count = 0
    val image get() = _image
    val count get() = _count

    companion object {
        val rgx = "(\\d+)\\s*套".toRegex(RegexOption.IGNORE_CASE)
    }

    constructor(e: Link) : this(e.name, e.url)
    constructor(e: Element) : this(Link(e.select("p a"))) {
        _image = e.select("img").attr("abs:src")
        _count = e.select(".shuliang").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
    }
}

class Info(name: String, url: String? = null) : Link(name, url) {

    constructor(e: Link) : this(e.name, e.url)

    lateinit var image: String

    lateinit var attr: List<Pair<String, String>>

    lateinit var tag: List<Link>

    lateinit var etc: String

    constructor(e: Element) : this(Link(e.select(".renwu .right h1"))) {
        image = e.select(".renwu .left img").attr("abs:src")
        attr = e.select(".renwu .right span").filter { it.nextSibling() is TextNode }.map {
            it.text() to (it.nextSibling() as TextNode).text()
        }
        tag = e.select(".renwu .shuoming a").map { Link(it) }
        e.select(".renwu .shuoming p").remove()
        etc = e.select(".renwu .shuoming").text()
    }
}

class Album(name: String, url: String? = null) : Link(name, url) {
    private lateinit var _image: String
    private lateinit var organ: List<Link>
    private var model: Link? = null
    private lateinit var tags: List<Link>
    private var _count = 0

    val image get() = _image
    val count get() = _count

    val info: List<Link>
        get() = tags + organ + (model?.takeIf { it.url != null }?.let { listOf(it) } ?: emptyList())

    companion object {
        val rgx = "(\\d+)\\s*P".toRegex(RegexOption.IGNORE_CASE)
    }

    constructor(e: Link) : this(e.name, e.url)

    constructor(e: Element) : this(Link(e.select(".biaoti a"))) {
        _image = e.select("img").attr("abs:src")
        _count = e.select(".shuliang").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
        model = e.select("p:contains(模特：)").takeIf { it.isNotEmpty() }?.let {
            it.select("a").takeIf { it.isNotEmpty() }?.let {
                Link(it)
            } ?: Link(it.text().substring("模特：".length))
        }
        organ = e.select("p:contains(机构：) a").map { Link(it) }
        tags = e.select("p:contains(类型：) a").map { Link(it) }
    }

}