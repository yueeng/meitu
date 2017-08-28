@file:Suppress("UNUSED_PARAMETER")

package io.github.yueeng.meituri

import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Data model
 * Created by Rain on 2017/8/22.
 */

open class Link(val name: String, val url: String? = null) {
    constructor(e: Elements) : this(e.text(), e.attr("abs:href"))
    constructor(e: Element) : this(e.text(), e.attr("abs:href"))
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
        val rgx = "(\\d+)P".toRegex(RegexOption.IGNORE_CASE)
    }

    constructor(e: Element) :
            this(e.select(".biaoti a").text(), e.select(".biaoti a").attr("abs:href")) {
        _image = e.select("img").attr("abs:src")
        _count = e.select(".shuliang").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
        model = e.select("p:contains(模特)").takeIf { it.isNotEmpty() }?.let {
            it.select("a").takeIf { it.isNotEmpty() }?.let {
                Link(it)
            } ?: Link(it.text().substring("模特：".length))
        }
        organ = e.select("p:contains(机构) a").map { Link(it) }
        tags = e.select("p:contains(类型) a").map { Link(it) }
    }

}