@file:Suppress("UNUSED_PARAMETER")

package io.github.yueeng.meituri

import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Data model
 * Created by Rain on 2017/8/22.
 */

open class Link(val name: String, val url: String?) {
    constructor(e: Elements) : this(e.text(), e.attr("abs:href"))
    constructor(e: Element) : this(e.text(), e.attr("abs:href"))
}

class Album(name: String, url: String?) : Link(name, url) {
    var image: String? = null
    var organ: Link? = null
    var model: Link? = null
    var tags: List<Link>? = null
}