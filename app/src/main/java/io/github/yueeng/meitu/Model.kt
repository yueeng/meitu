@file:Suppress("UNUSED_PARAMETER", "unused", "PropertyName", "ClassName", "ObjectPropertyName")

package io.github.yueeng.meitu

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToMany
import io.reactivex.disposables.Disposable
import org.jetbrains.anko.bundleOf
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*
import kotlin.collections.ArrayList
import kotlin.properties.Delegates

/**
 * Data model
 * Created by Rain on 2017/8/22.
 */

open class Name(val name: String) : Parcelable {
    var referer: String? = null
    override fun toString(): String = name
    override fun equals(other: Any?): Boolean = name == other
    override fun hashCode(): Int = name.hashCode()

    constructor(source: Parcel) : this(source.readString()) {
        referer = source.readString()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(referer)
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
    constructor(e: Elements?) : this(e?.text() ?: "", e?.attrs("abs:href"))

    constructor(e: Element?) : this(e?.text() ?: "", e?.attrs("abs:href"))

    override fun toString(): String = name
    override fun equals(other: Any?): Boolean = key == other
    override fun hashCode(): Int = key.hashCode()

    val key get() = "$name:${url ?: ""}"
    val uri get() = url?.takeIf { !url.isNullOrEmpty() } ?: search(name)

    constructor(source: Parcel) : this(source.readString(), source.readString()) {
        referer = source.readString()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
        writeString(referer)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Link> = object : Parcelable.Creator<Link> {
            override fun createFromParcel(source: Parcel): Link = Link(source)
            override fun newArray(size: Int): Array<Link?> = arrayOfNulls(size)
        }
    }
}

class Title(name: String, url: String? = null) : Link(name, url), Parcelable {
    constructor(e: Link) : this(e.name, e.url)
    constructor(source: Parcel) : this(source.readString(), source.readString()) {
        referer = source.readString()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
        writeString(referer)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Title> = object : Parcelable.Creator<Title> {
            override fun createFromParcel(source: Parcel): Title = Title(source)
            override fun newArray(size: Int): Array<Title?> = arrayOfNulls(size)
        }
    }
}

class Organ(name: String, url: String? = null) : Link(name, url), Parcelable {
    var count = 0

    constructor(e: Link) : this(e.name, e.url)

    constructor(source: Parcel) : this(source.readString(), source.readString()) {
        referer = source.readString()
        count = source.readInt()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
        writeString(referer)
        writeInt(count)
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<Organ> = object : Parcelable.Creator<Organ> {
            override fun createFromParcel(source: Parcel): Organ = Organ(source)
            override fun newArray(size: Int): Array<Organ?> = arrayOfNulls(size)
        }
    }
}

class Model(name: String, url: String? = null) : Link(name, url), Parcelable {
    lateinit var image: String

    var count = 0
    var flag = 0

    constructor(e: Link) : this(e.name, e.url)

    constructor(source: Parcel) : this(source.readString(), source.readString()) {
        referer = source.readString()
        image = source.readString()
        count = source.readInt()
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(url)
        writeString(referer)
        writeString(image)
        writeInt(count)
    }

    companion object {

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


    constructor(source: Parcel) : this(source.readString(), source.readString()) {
        referer = source.readString()
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
        writeString(referer)
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

    constructor(it: ObAlbum) : this(it.name, it.url) {
        referer = it.referer
        _image = it.image
        _count = it.count
        model = it.model.map { Link(it.name, it.url) }
        organ = it.organ.map { Link(it.name, it.url) }
        tags = it.tags.map { Link(it.name, it.url) }
    }

    constructor(source: Parcel) : this(source.readString(), source.readString()) {
        referer = source.readString()
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
        writeString(referer)
        writeString(_image)
        writeList(organ)
        writeList(model)
        writeList(tags)
        writeInt(_count)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Album> = object : Parcelable.Creator<Album> {
            override fun createFromParcel(source: Parcel): Album = Album(source)
            override fun newArray(size: Int): Array<Album?> = arrayOfNulls(size)
        }
    }
}

class Link2(val id: Long, name: String, url: String?, val size: Int) : Link(name, url), Parcelable {
    constructor(source: Parcel) : this(
            source.readLong(),
            source.readString(),
            source.readString(),
            source.readInt()
    ) {
        referer = source.readString()
    }

    constructor(source: ObLink) : this(source.id, source.name, source.url, source.albums.size)

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeLong(id)
        writeString(name)
        writeString(url)
        writeInt(size)
        writeString(referer)
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
    var referer: String? = null
    lateinit var image: String
    lateinit var organ: ToMany<ObLink>
    lateinit var model: ToMany<ObLink>
    lateinit var tags: ToMany<ObLink>
    var count: Int = 0
}

object dbFav {
    private class cob {
        val ob: BoxStore by lazy { MyObjectBox.builder().androidContext(MainApplication.instance()).build() }
        val oba: Box<ObAlbum> by lazy { ob.boxFor(ObAlbum::class.java) }
        val obl: Box<ObLink> by lazy { ob.boxFor(ObLink::class.java) }
    }

    private var _cob: cob? = null
    private val lck = Any()
    fun reset(fn: (() -> Unit)? = null) {
        synchronized(lck) {
            _cob?.ob?.close()
            fn?.invoke()
            _cob = cob()
        }
    }

    init {
        reset()
    }

    private val ob get() = _cob!!.ob
    private val oba get() = _cob!!.oba
    private val obl get() = _cob!!.obl

    fun put(album: Album, fn: ((ObAlbum) -> Unit)? = null): Disposable = RxMt.create {
        if (album.url == "") {
            throw IllegalArgumentException("album.url is null.")
        } else {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            val info = album.info
                    .flatMap {
                        obl.query().equal(ObLink_.name, it.name).and().equal(ObLink_.url, it.url
                                ?: "").build().find()
                    }
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
                referer = album.referer
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

//inline fun <reified T : Parcelable> mtSequence(uri: String?, noinline fn: (String) -> Pair<String?, List<T>>) = MtSequence(uri, fn)
class MtSequence<out T : Name>(uri: String?, val fn: (String) -> Pair<String?, List<T>>) : Sequence<T> {
    var ob: (() -> Unit)? = null
    private val data = LinkedList<T>()
    private var url by Delegates.observable(uri) { _, o, n ->
        if (o != n) ob?.invoke()
    }

    operator fun invoke() = bundleOf("url" to url).apply {
        putParcelableArrayList("data", ArrayList(data))
    }

    operator fun invoke(bundle: Bundle) {
        data.clear()
        data.addAll(bundle.getParcelableArrayList("data"))
        url = bundle.getString("url")
    }

    operator fun invoke(uri: String) {
        url = uri
        data.clear()
    }

    fun empty() = data.isEmpty() && url?.isEmpty() ?: true

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        override fun hasNext(): Boolean = data.isNotEmpty() || url?.let { uri ->
            val result = fn(uri)
            url = result.first
            result.second.forEach { it.referer = uri }
            data.addAll(result.second)
            data.isNotEmpty()
        } ?: false

        override fun next(): T = data.pop()
    }
}