@file:Suppress("UNUSED_PARAMETER")

package io.github.yueeng.meituri

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * Data model
 * Created by Rain on 2017/8/22.
 */

val website = "http://www.meituri.com"

fun search(key: String) = "$website/search/${Uri.encode(key)}"

open class Link(val name: String, val url: String? = null) : Parcelable {
    constructor(e: Elements) : this(e.text(), e.attrs("abs:href"))

    constructor(e: Element) : this(e.text(), e.attrs("abs:href"))

    override fun toString(): String = name

    val key get() = "$name:${url ?: ""}"
    val uri get() = url?.takeIf { !url.isNullOrEmpty() } ?: search(name)

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
        get() = tags + organ + model.option()

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
        tags = e.select("p:contains(类型：) a,p:contains(标签：) a").map { Link(it) }
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

        fun from(url: String, sample: Album? = null, fn: (Album?) -> Unit) {
            RxMt.create {
                val dom = url.httpGet().jsoup()
                val attr: Map<String, List<Any>>? = dom?.select(".tuji p,.shuoming p, .fenxiang_l")?.map { it.childNodes() }?.flatten()?.mapNotNull {
                    when (it) {
                        is TextNode -> it.text().trim().split("；").filter { it.isNotBlank() }
                                .map { it.split("：").joinToString("：") { it.trim() } }
                        is Element -> listOf(Link(it.text(), it.attr("abs:href")))
                        else -> emptyList()
                    }
                }?.flatten()?.fold<Any, MutableList<MutableList<Any>>>(mutableListOf()) { r, t ->
                    r.apply {
                        when (t) {
                            is String -> mutableListOf<Any>().apply {
                                r += apply { addAll(t.split("：").filter { it.isNotBlank() }) }
                            }
                            else -> r.last() += t
                        }
                    }
                }?.map { it.first().toString() to it.drop(1) }?.toMap()
                attr?.let {
                    fun attr2links(name: String) = attr[name]?.mapNotNull {
                        when (it) {
                            is Link -> it
                            is String -> Link(it)
                            else -> null
                        }
                    } ?: emptyList()
                    Album(dom.select("h1").text(), url).apply {
                        model = attr2links("出镜模特").plus(sample?.model.option()).distinctBy { it.key }.firstOrNull()
                        organ = attr2links("拍摄机构").plus((sample?.organ ?: emptyList())).distinctBy { it.key }
                        tags = attr2links("标签").plus((sample?.tags ?: emptyList())).distinctBy { it.key }
                        _image = sample?._image?.takeIf { it.isNotEmpty() } ?: dom.select(".content img.tupian_img").firstOrNull()?.attr("abs:src") ?: ""
                        _count = sample?.count?.takeIf { it != 0 } ?: attr["图片数量"]?.map { it.toString() }?.firstOrNull()?.let {
                            rgx.find(it)?.let { it.groups[1]?.value?.toInt() }
                        } ?: 0
                    }
                }
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe { fn(it) }
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
    lateinit var albums: ToMany<ObAlbum>
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
    fun put(album: Album, fn: ((ObAlbum) -> Unit)? = null) {
        RxMt.create {
            if (album.url == "") {
                throw IllegalArgumentException("album.url is null.")
            } else {
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                val info = album.info
                        .flatMap { obl.query().equal(ObLink_.name, it.name).and().equal(ObLink_.url, it.url ?: "").build().find() }
                        .map { Pair(it.key, it) }.toMap()

                fun link2info(link: Link, t: Int, oba: ObAlbum) = (info[link.key] ?: ObLink().apply {
                    name = link.name
                    url = link.url ?: ""
                }).apply {
                    type = t
                    albums.add(oba)
                }

                val o = (oba.find(ObAlbum_.url, album.url!!).firstOrNull() ?: ObAlbum()).apply {
                    name = album.name
                    url = album.url
                    image = album.image
                    count = album.count
                    album.model?.let { model.target = link2info(it, ObLink.TYPE_MODEL, this) }
                    album.organ.forEach { organ.add(link2info(it, ObLink.TYPE_ORGAN, this)) }
                    album.tags.forEach { tags.add(link2info(it, ObLink.TYPE_TAGS, this)) }
                }
                oba.put(o)
                o.model.target.option().forEach { obl.put(it) }
                o.organ.forEach { obl.put(it) }
                o.tags.forEach { obl.put(it) }
                o
            }
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe { fn?.invoke(it) }
    }

    fun exists(url: String) = !oba.find(ObAlbum_.url, url).isEmpty()
    fun del(url: String) = oba.find(ObAlbum_.url, url).forEach {
        oba.remove(it)
    }

    fun albums(offset: Long, limit: Long, fn: (List<Album>) -> Unit) {
        RxMt.create {
            oba.query().orderDesc(ObAlbum_.id).build().find(offset, limit).map(::Album)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe { fn(it) }
    }

    fun albums(tag: Long, fn: (List<Album>) -> Unit) {
        RxMt.create {
            obl.get(tag).albums.map(::Album)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe { fn(it) }
    }

    fun tags(type: Int, fn: (List<ObLink>) -> Unit) {
        RxMt.create {
            obl.query().equal(ObLink_.type, type.toLong()).build().find().filter { it.albums.isNotEmpty() }.sortedByDescending { it.albums.size }
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe { fn(it) }
    }
}
