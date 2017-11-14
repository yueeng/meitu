@file:Suppress("UNUSED_PARAMETER")

package io.github.yueeng.meituri

import android.os.Parcel
import android.os.Parcelable
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * Data model
 * Created by Rain on 2017/8/22.
 */

val website = "http://www.meituri.com"

open class Link(val name: String, val url: String? = null) : Parcelable {
    constructor(e: Elements) : this(e.text(), e.attrs("abs:href"))

    constructor(e: Element) : this(e.text(), e.attrs("abs:href"))

    override fun toString(): String = name

    val key get() = "$name:${url ?: ""}"

    constructor(source: Parcel) : this(
            source.readString(),
            source.readString()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Link> = object : Parcelable.Creator<Link> {
            override fun createFromParcel(source: Parcel): Link = Link(source)
            override fun newArray(size: Int): Array<Link?> = arrayOfNulls(size)
        }
    }
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

class Album(name: String, url: String? = null) : Link(name, url), Parcelable {
    internal lateinit var _image: String

    internal lateinit var organ: List<Link>

    internal var model: Link? = null

    internal lateinit var tags: List<Link>

    internal var _count = 0

    val image get() = _image

    val count get() = _count

    val info: List<Link>
        get() = tags + organ + (model?.takeIf { it.url != null }?.let { listOf(it) } ?: emptyList())

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

    constructor(it: ObAlbum) : this(it.name, it.url) {
        _image = it.image
        _count = it.count
        model = it.model.target?.let { Link(it.name, it.url) }
        organ = it.organ.map { Link(it.name, it.url) }
        tags = it.tags.map { Link(it.name, it.url) }
    }

    constructor(source: Parcel) : this(
            source.readString(),
            source.readString()
    ) {
        _image = source.readString()
        organ = mutableListOf()
        source.readList(organ, Link::class.java.classLoader)
        model = source.readParcelable(Link::class.java.classLoader)
        tags = mutableListOf()
        source.readList(tags, Link::class.java.classLoader)
        _count = source.readInt()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
        writeString(_image)
        writeList(organ)
        writeParcelable(model, 0)
        writeList(tags)
        writeInt(_count)
    }

    companion object {
        val rgx = "(\\d+)\\s*P".toRegex(RegexOption.IGNORE_CASE)

        @JvmField
        val CREATOR: Parcelable.Creator<Album> = object : Parcelable.Creator<Album> {
            override fun createFromParcel(source: Parcel): Album = Album(source)
            override fun newArray(size: Int): Array<Album?> = arrayOfNulls(size)
        }
    }
}

@Entity
open class ObLink(@Id var id: Long = 0) {
    @Index
    lateinit var name: String
    @Index
    lateinit var url: String
    var type: Int = 0
    lateinit var album: ToMany<ObAlbum>
    val key get() = "$name:$url"

    companion object {
        const val TYPE_MODEL = 1
        const val TYPE_ORGAN = 2
        const val TYPE_TAGS = 3
    }
}

@Entity
data class ObAlbum(@Id var id: Long = 0) {
    @Index
    lateinit var name: String
    @Index
    lateinit var url: String
    lateinit var image: String
    lateinit var organ: ToMany<ObLink>
    lateinit var model: ToOne<ObLink>
    lateinit var tags: ToMany<ObLink>
    var count: Int = 0
}

object dbFav {
    val ob by lazy { MyObjectBox.builder().androidContext(MainApplication.current()).build() }
    val oba by lazy { ob.boxFor(ObAlbum::class.java) }
    val obl by lazy { ob.boxFor(ObLink::class.java) }
    fun put(album: Album) {
        if (album.url == "") return
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val info = album.info
                .flatMap { obl.query().equal(ObLink_.name, it.name).and().equal(ObLink_.url, it.url ?: "").build().find() }
                .map { Pair(it.key, it) }.toMap()

        fun ifo(link: Link, t: Int, oba: ObAlbum) = (info[link.key] ?: ObLink().apply {
            name = link.name
            url = link.url ?: ""
        }).apply {
            this.type = t
            this.album.add(oba)
        }

        val o = oba.find(ObAlbum_.url, album.url!!).firstOrNull() ?: ObAlbum()
        o.apply {
            name = album.name
            url = album.url
            image = album.image
            count = album.count
            album.model?.let {
                model.target = ifo(it, ObLink.TYPE_MODEL, o)
            }
            album.organ.forEach { organ.add(ifo(it, ObLink.TYPE_ORGAN, o)) }
            album.tags.forEach { tags.add(ifo(it, ObLink.TYPE_TAGS, o)) }
        }
        oba.put(o)
    }

    fun exists(url: String) = !oba.find(ObAlbum_.url, url).isEmpty()
    fun del(url: String) = oba.find(ObAlbum_.url, url).forEach { oba.remove(it) }
    fun albums(): List<Album> = oba.query().orderDesc(ObAlbum_.id).build().find().map(::Album)
}