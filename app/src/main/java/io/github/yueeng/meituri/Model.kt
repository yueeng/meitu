@file:Suppress("UNUSED_PARAMETER", "unused", "PropertyName", "ClassName")

package io.github.yueeng.meituri

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToMany
import io.reactivex.disposables.Disposable
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * Data model
 * Created by Rain on 2017/8/22.
 */

val website = "http://www.meituri.com"

fun search(key: String) = "$website/search/${Uri.encode(key)}"
open class Name(val name: String) : Parcelable {
    override fun toString(): String = name
    override fun equals(other: Any?): Boolean = name == other
    override fun hashCode(): Int = name.hashCode()

    constructor(source: Parcel) : this(
            source.readString()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Name> = object : Parcelable.Creator<Name> {
            override fun createFromParcel(source: Parcel): Name = Name(source)
            override fun newArray(size: Int): Array<Name?> = arrayOfNulls(size)
        }
    }
}

open class Link(name: String, val url: String? = null) : Name(name), Parcelable {
    constructor(e: Elements) : this(e.text(), e.attrs("abs:href"))

    constructor(e: Element) : this(e.text(), e.attrs("abs:href"))

    override fun toString(): String = name
    override fun equals(other: Any?): Boolean = key == other
    override fun hashCode(): Int = key.hashCode()

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

class Organ(name: String, url: String? = null) : Link(name, url), Parcelable {
    private var _count = 0

    val count get() = _count

    constructor(e: Link) : this(e.name, e.url)

    constructor(e: Element) : this(Link(e.select("a"))) {
        _count = e.select("span").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
    }

    constructor(source: Parcel) : this(
            source.readString(),
            source.readString()
    ) {
        _count = source.readInt()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
        writeInt(_count)
    }

    companion object {
        val rgx = "(\\d+)\\s*套".toRegex(RegexOption.IGNORE_CASE)

        @JvmField
        val CREATOR: Parcelable.Creator<Organ> = object : Parcelable.Creator<Organ> {
            override fun createFromParcel(source: Parcel): Organ = Organ(source)
            override fun newArray(size: Int): Array<Organ?> = arrayOfNulls(size)
        }
    }
}

class Model(name: String, url: String? = null) : Link(name, url), Parcelable {
    private lateinit var _image: String

    private var _count = 0

    val image get() = _image

    val count get() = _count

    constructor(e: Link) : this(e.name, e.url)

    constructor(e: Element) : this(Link(e.select("p a"))) {
        _image = e.select("img").attr("abs:src")
        _count = e.select(".shuliang").text().let {
            rgx.find(it)?.let { it.groups[1]?.value?.toInt() } ?: 0
        }
    }

    constructor(source: Parcel) : this(
            source.readString(),
            source.readString()
    ) {
        _image = source.readString()
        _count = source.readInt()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
        writeString(_image)
        writeInt(_count)
    }

    companion object {
        val rgx = "(\\d+)\\s*套".toRegex(RegexOption.IGNORE_CASE)

        @JvmField
        val CREATOR: Parcelable.Creator<Model> = object : Parcelable.Creator<Model> {
            override fun createFromParcel(source: Parcel): Model = Model(source)
            override fun newArray(size: Int): Array<Model?> = arrayOfNulls(size)
        }
    }
}

class Info(name: String, url: String? = null) : Link(name, url), Parcelable {
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

    constructor(source: Parcel) : this(
            source.readString(),
            source.readString()
    ) {
        image = source.readString()
        attr = mutableListOf()
        source.readList(attr, pairClass.classLoader)
        tag = mutableListOf()
        source.readList(tag, Link::class.java.classLoader)
        etc = source.readString()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
        writeString(image)
        writeList(attr)
        writeList(tag)
        writeString(etc)
    }

    companion object {
        private val pairClass = Pair("", "").javaClass
        @JvmField
        val CREATOR: Parcelable.Creator<Info> = object : Parcelable.Creator<Info> {
            override fun createFromParcel(source: Parcel): Info = Info(source)
            override fun newArray(size: Int): Array<Info?> = arrayOfNulls(size)
        }
    }
}

class Album(name: String, url: String? = null) : Link(name, url), Parcelable {
    internal lateinit var _image: String

    internal lateinit var organ: List<Link>

    internal lateinit var model: List<Link>

    internal lateinit var tags: List<Link>

    internal var _count = 0

    val image get() = _image

    val count get() = _count

    val info: List<Link>
        get() = tags + organ + model

    constructor(e: Link) : this(e.name, e.url)

    constructor(e: Element) : this(Link(e.select(".biaoti a"))) {
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

    constructor(it: ObAlbum) : this(it.name, it.url) {
        _image = it.image
        _count = it.count
        model = it.model.map { Link(it.name, it.url) }
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
        model = mutableListOf()
        source.readList(model, Link::class.java.classLoader)
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
        writeList(model)
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
}

class Link2(val id: Long, name: String, url: String?, val size: Int) : Link(name, url), Parcelable {
    constructor(source: Parcel) : this(
            source.readLong(),
            source.readString(),
            source.readString(),
            source.readInt()
    )

    constructor(source: ObLink) : this(source.id, source.name, source.url, source.albums.size)

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeLong(id)
        writeString(name)
        writeString(url)
        writeInt(size)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Link2> = object : Parcelable.Creator<Link2> {
            override fun createFromParcel(source: Parcel): Link2 = Link2(source)
            override fun newArray(size: Int): Array<Link2?> = arrayOfNulls(size)
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
    lateinit var model: ToMany<ObLink>
    lateinit var tags: ToMany<ObLink>
    var count: Int = 0
}

object dbFav {
    private val ob by lazy { MyObjectBox.builder().androidContext(MainApplication.current()).build() }
    private val oba by lazy { ob.boxFor(ObAlbum::class.java) }
    private val obl by lazy { ob.boxFor(ObLink::class.java) }
    fun put(album: Album, fn: ((ObAlbum) -> Unit)? = null): Disposable = RxMt.create {
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

            (oba.find(ObAlbum_.url, album.url!!).firstOrNull() ?: ObAlbum()).apply {
                name = album.name
                url = album.url
                image = album.image
                count = album.count
                album.model.forEach { model.add(link2info(it, ObLink.TYPE_MODEL, this)) }
                album.organ.forEach { organ.add(link2info(it, ObLink.TYPE_ORGAN, this)) }
                album.tags.forEach { tags.add(link2info(it, ObLink.TYPE_TAGS, this)) }
            }.also { o ->
                oba.put(o)
                obl.put(o.model + o.organ + o.tags)
            }
        }
    }.io2main().subscribe {
        fn?.invoke(it)
        RxBus.instance.post("favorite", it.url)
    }

    fun exists(url: String) = !oba.find(ObAlbum_.url, url).isEmpty()
    fun del(url: String, fn: ((List<ObAlbum>) -> Unit)? = null): Disposable = RxMt.create {
        oba.find(ObAlbum_.url, url).onEach { oba.remove(it) }
    }.io2main().subscribe {
        fn?.invoke(it)
        RxBus.instance.post("favorite", url)
    }

    fun albums(offset: Long, limit: Long, fn: (List<Album>) -> Unit): Disposable = RxMt.create {
        oba.query().orderDesc(ObAlbum_.id).build().find(offset, limit).map(::Album)
    }.io2main().subscribe { fn(it) }

    fun albums(tag: Long, fn: (List<Album>) -> Unit): Disposable = RxMt.create {
        obl.get(tag).albums.sortedByDescending { it.id }.map(::Album)
    }.io2main().subscribe { fn(it) }

    fun tag(tag: Long, fn: (Link?) -> Unit): Disposable = RxMt.create {
        obl.get(tag)?.let { Link(it.name, it.url) }
    }.io2main().subscribe { fn(it) }

    fun tags(type: Int, fn: (List<Link2>) -> Unit): Disposable = RxMt.create {
        obl.query().equal(ObLink_.type, type.toLong()).build().find().filter { it.albums.isNotEmpty() }.sortedByDescending { it.albums.size }.map(::Link2)
    }.io2main().subscribe { fn(it) }
}

open class MtSequence<T>(var url: String?, val fn: (String) -> Pair<String?, List<T>>) : Sequence<List<T>> {
    override fun iterator(): Iterator<List<T>> = object : Iterator<List<T>> {
        lateinit var data: List<T>
        override fun hasNext(): Boolean = url?.let {
            val result = fn(it)
            url = result.first
            data = result.second
            data.isNotEmpty()
        } ?: false

        override fun next(): List<T> = data
    }
}

fun mtAlbumSequence(uri: String) = MtSequence(uri) {
    val first = it == uri
    val dom = it.httpGet().jsoup()
    val url = dom?.select("#pages .current+a,#pages span+a:not(.a1)")?.attr("abs:href")
    val list: List<Name>? = dom?.select(".hezi .title,.hezi li,.hezi_t li,.jigou li,.fenlei p,.shoulushuliang,.renwu")?.mapNotNull {
        when {
            it.`is`(".hezi li") -> Album(it)
            first && it.`is`(".hezi_t li") -> Model(it)
            first && it.`is`(".jigou li") -> Organ(it)
            first && it.`is`(".renwu") -> Info(it)
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
    val data = dom?.select(".content img.tupian_img")?.map { it.attr("abs:src") }
    val url = dom?.select("#pages span+a")?.let {
        !it.`is`(".a1") to it.attr("abs:href")
    }?.takeIf { it.first }?.second
    url to data.orEmpty()
}
