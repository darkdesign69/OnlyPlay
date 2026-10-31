package app.marlboroadvance.mpvex.ui.browser.music

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * Writes lyrics and cover art INTO the audio file, so other music players see them too.
 * MP3 (ID3v2.3/2.4: USLT + APIC) and M4A/MP4 (©lyr + covr) are supported.
 * Needs "All files access" (the app already asks for it); otherwise the change stays inside the app only.
 */
object MusicTagWriter {
  private val latin1 = Charsets.ISO_8859_1

  // ---------------------------------------------------------------- public API

  /** Embeds [text] as the song's lyrics (blank text removes them) in the background. */
  fun embedLyricsAsync(
    ctx: Context,
    path: String,
    text: String,
  ) {
    if (path.isBlank()) return
    val app = ctx.applicationContext
    thread(name = "embed-lyrics") {
      val ok = write(path, lyrics = text, setLyrics = true, cover = null, changeCover = false)
      if (ok) scan(app, path) else toast(app, "Lyrics saved in the app only (couldn't write into this file type)")
    }
  }

  /** Embeds a JPEG as the song's cover ([jpeg] = null removes the cover) in the background. */
  fun embedCoverAsync(
    ctx: Context,
    trackId: Long,
    jpeg: ByteArray?,
  ) {
    val app = ctx.applicationContext
    thread(name = "embed-cover") {
      val path = pathOf(app, trackId)
      if (path == null) return@thread
      val ok = write(path, lyrics = null, setLyrics = false, cover = jpeg, changeCover = true)
      if (ok) scan(app, path) else toast(app, "Cover changed in the app only (couldn't write into this file type)")
    }
  }

  private fun pathOf(
    ctx: Context,
    id: Long,
  ): String? {
    if (id < 0) return null
    return try {
      val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
      ctx.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun scan(
    ctx: Context,
    path: String,
  ) {
    try {
      MediaScannerConnection.scanFile(ctx, arrayOf(path), null, null)
    } catch (_: Exception) {
    }
  }

  private fun toast(
    ctx: Context,
    msg: String,
  ) {
    Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
  }

  // ---------------------------------------------------------------- dispatch

  private fun write(
    path: String,
    lyrics: String?,
    setLyrics: Boolean,
    cover: ByteArray?,
    changeCover: Boolean,
  ): Boolean {
    val f = File(path)
    if (!f.exists() || !f.canRead()) return false
    val tmp = File(f.parentFile, f.name + ".tmp")
    return try {
      val head = ByteArray(12)
      val n = RandomAccessFile(f, "r").use { it.read(head) }
      if (n < 8) return false
      val isId3 = head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()
      val isMpegSync = (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0
      val isMp4 = head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() && head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()
      val written =
        when {
          isId3 || isMpegSync -> writeId3(f, tmp, isId3, lyrics, setLyrics, cover, changeCover)
          isMp4 -> writeMp4(f, tmp, lyrics, setLyrics, cover, changeCover)
          else -> false
        }
      if (!written) {
        tmp.delete()
        return false
      }
      replace(tmp, f)
    } catch (_: Throwable) {
      try {
        tmp.delete()
      } catch (_: Exception) {
      }
      false
    }
  }

  private fun replace(
    tmp: File,
    f: File,
  ): Boolean {
    if (tmp.renameTo(f)) return true
    // rename over an existing file is refused on some devices: copy the bytes in place instead
    return try {
      tmp.inputStream().use { i -> FileOutputStream(f).use { o -> i.copyTo(o) } }
      tmp.delete()
      true
    } catch (_: Throwable) {
      tmp.delete()
      false
    }
  }

  // ---------------------------------------------------------------- byte helpers

  private fun put32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

  private fun get32(
    a: ByteArray,
    o: Int,
  ): Int = ((a[o].toInt() and 0xFF) shl 24) or ((a[o + 1].toInt() and 0xFF) shl 16) or ((a[o + 2].toInt() and 0xFF) shl 8) or (a[o + 3].toInt() and 0xFF)

  private fun get64(
    a: ByteArray,
    o: Int,
  ): Long = (get32(a, o).toLong() shl 32) or (get32(a, o + 4).toLong() and 0xFFFFFFFFL)

  private fun put64(v: Long) = put32((v ushr 32).toInt()) + put32(v.toInt())

  private fun synchsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())

  private fun unsynch(
    a: ByteArray,
    o: Int,
  ): Int = ((a[o].toInt() and 0x7F) shl 21) or ((a[o + 1].toInt() and 0x7F) shl 14) or ((a[o + 2].toInt() and 0x7F) shl 7) or (a[o + 3].toInt() and 0x7F)

  private fun copyRange(
    raf: RandomAccessFile,
    from: Long,
    to: Long,
    out: OutputStream,
  ) {
    raf.seek(from)
    val buf = ByteArray(64 * 1024)
    var left = to - from
    while (left > 0) {
      val r = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
      if (r <= 0) break
      out.write(buf, 0, r)
      left -= r
    }
  }

  // ---------------------------------------------------------------- MP3 (ID3v2)

  private class Frame(
    val id: String,
    val flags: ByteArray,
    val body: ByteArray,
  )

  private fun writeId3(
    f: File,
    tmp: File,
    hasTag: Boolean,
    lyrics: String?,
    setLyrics: Boolean,
    cover: ByteArray?,
    changeCover: Boolean,
  ): Boolean {
    RandomAccessFile(f, "r").use { raf ->
      var major = 3
      var audioStart = 0L
      val frames = ArrayList<Frame>()
      if (hasTag) {
        val h = ByteArray(10)
        raf.readFully(h)
        major = h[3].toInt()
        val flags = h[5].toInt() and 0xFF
        // v2.2, unsynchronisation and extended headers are not handled: leave such files alone
        if ((major != 3 && major != 4) || (flags and 0xC0) != 0) return false
        val size = unsynch(h, 6)
        val tag = ByteArray(size)
        raf.readFully(tag)
        audioStart = 10L + size + (if ((flags and 0x10) != 0) 10 else 0)
        var p = 0
        while (p + 10 <= size) {
          if (tag[p].toInt() == 0) break
          val id = String(tag, p, 4, latin1)
          val fs = if (major == 4) unsynch(tag, p + 4) else get32(tag, p + 4)
          if (fs < 0 || p + 10 + fs > size) break
          frames.add(Frame(id, tag.copyOfRange(p + 8, p + 10), tag.copyOfRange(p + 10, p + 10 + fs)))
          p += 10 + fs
        }
      }
      val kept = frames.filter { !(setLyrics && it.id == "USLT") && !(changeCover && it.id == "APIC") }.toMutableList()
      if (setLyrics && !lyrics.isNullOrBlank()) {
        val body = ByteArrayOutputStream()
        body.write(1) // UTF-16 with BOM
        body.write("eng".toByteArray(latin1))
        body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0)) // empty descriptor
        body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        body.write(lyrics.toByteArray(Charsets.UTF_16LE))
        kept.add(Frame("USLT", byteArrayOf(0, 0), body.toByteArray()))
      }
      if (changeCover && cover != null) {
        val body = ByteArrayOutputStream()
        body.write(0)
        body.write("image/jpeg".toByteArray(latin1))
        body.write(0)
        body.write(3) // front cover
        body.write(0)
        body.write(cover)
        kept.add(Frame("APIC", byteArrayOf(0, 0), body.toByteArray()))
      }
      val frameBytes = ByteArrayOutputStream()
      for (fr in kept) {
        frameBytes.write(fr.id.toByteArray(latin1))
        frameBytes.write(if (major == 4) synchsafe(fr.body.size) else put32(fr.body.size))
        frameBytes.write(fr.flags)
        frameBytes.write(fr.body)
      }
      FileOutputStream(tmp).use { out ->
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), major.toByte(), 0, 0))
        out.write(synchsafe(frameBytes.size()))
        out.write(frameBytes.toByteArray())
        copyRange(raf, audioStart, raf.length(), out)
      }
    }
    return true
  }

  // ---------------------------------------------------------------- M4A / MP4

  private class Atom(
    val type: String,
    val pos: Int,
    val size: Int,
  )

  private fun atoms(
    buf: ByteArray,
    start: Int,
    end: Int,
  ): List<Atom> {
    val res = ArrayList<Atom>()
    var p = start
    while (p + 8 <= end) {
      val s = get32(buf, p)
      if (s < 8 || p + s > end) break
      res.add(Atom(String(buf, p + 4, 4, latin1), p, s))
      p += s
    }
    return res
  }

  private fun box(
    type: String,
    payload: ByteArray,
  ): ByteArray = put32(8 + payload.size) + type.toByteArray(latin1) + payload

  /** Rebuilds [payload] with its child [type] replaced by transform(old child payload or null). */
  private fun replaceChild(
    payload: ByteArray,
    start: Int,
    type: String,
    transform: (ByteArray?) -> ByteArray,
  ): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(payload, 0, start)
    var found = false
    for (a in atoms(payload, start, payload.size)) {
      if (a.type == type) {
        found = true
        out.write(box(type, transform(payload.copyOfRange(a.pos + 8, a.pos + a.size))))
      } else {
        out.write(payload, a.pos, a.size)
      }
    }
    if (!found) out.write(box(type, transform(null)))
    return out.toByteArray()
  }

  private fun adjustOffsets(
    buf: ByteArray,
    start: Int,
    end: Int,
    delta: Int,
  ) {
    for (a in atoms(buf, start, end)) {
      when (a.type) {
        "trak", "mdia", "minf", "stbl" -> adjustOffsets(buf, a.pos + 8, a.pos + a.size, delta)
        "stco" -> {
          val n = get32(buf, a.pos + 12)
          for (i in 0 until n) {
            val q = a.pos + 16 + 4 * i
            val v = get32(buf, q) + delta
            System.arraycopy(put32(v), 0, buf, q, 4)
          }
        }
        "co64" -> {
          val n = get32(buf, a.pos + 12)
          for (i in 0 until n) {
            val q = a.pos + 16 + 8 * i
            val v = get64(buf, q) + delta
            System.arraycopy(put64(v), 0, buf, q, 8)
          }
        }
      }
    }
  }

  private fun writeMp4(
    f: File,
    tmp: File,
    lyrics: String?,
    setLyrics: Boolean,
    cover: ByteArray?,
    changeCover: Boolean,
  ): Boolean {
    RandomAccessFile(f, "r").use { raf ->
      val len = raf.length()
      var moovPos = -1L
      var moovSize = 0L
      var mdatPos = Long.MAX_VALUE
      var p = 0L
      val h = ByteArray(16)
      while (p + 8 <= len) {
        raf.seek(p)
        raf.readFully(h, 0, 8)
        var s = get32(h, 0).toLong() and 0xFFFFFFFFL
        val t = String(h, 4, 4, latin1)
        if (s == 1L) {
          raf.readFully(h, 8, 8)
          s = get64(h, 8)
        } else if (s == 0L) {
          s = len - p
        }
        if (s < 8 || p + s > len) break
        if (t == "moov" && moovPos < 0) {
          moovPos = p
          moovSize = s
        }
        if (t == "mdat" && p < mdatPos) mdatPos = p
        p += s
      }
      if (moovPos < 0 || moovSize > 64L * 1024 * 1024) return false
      val moovPayload = ByteArray((moovSize - 8).toInt())
      raf.seek(moovPos + 8)
      raf.readFully(moovPayload)

      fun items(old: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        val o = old ?: ByteArray(0)
        for (a in atoms(o, 0, o.size)) {
          val drop = (setLyrics && a.type == "©lyr") || (changeCover && a.type == "covr")
          if (!drop) out.write(o, a.pos, a.size)
        }
        if (setLyrics && !lyrics.isNullOrBlank()) {
          out.write(box("©lyr", box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + lyrics.toByteArray(Charsets.UTF_8))))
        }
        if (changeCover && cover != null) {
          out.write(box("covr", box("data", byteArrayOf(0, 0, 0, 13, 0, 0, 0, 0) + cover)))
        }
        return out.toByteArray()
      }

      fun metaTf(old: ByteArray?): ByteArray {
        if (old == null) {
          val hdlr = box("hdlr", ByteArray(8) + "mdir".toByteArray(latin1) + "appl".toByteArray(latin1) + ByteArray(9))
          return ByteArray(4) + hdlr + box("ilst", items(null))
        }
        return replaceChild(old, 4, "ilst") { items(it) }
      }

      fun udtaTf(old: ByteArray?): ByteArray = replaceChild(old ?: ByteArray(0), 0, "meta") { metaTf(it) }

      val newPayload = replaceChild(moovPayload, 0, "udta") { udtaTf(it) }
      val newMoov = box("moov", newPayload)
      val delta = newMoov.size - moovSize.toInt()
      // chunk offsets point behind a moov that comes first: shift them by how much moov grew
      if (delta != 0 && moovPos < mdatPos) adjustOffsets(newMoov, 8, newMoov.size, delta)

      FileOutputStream(tmp).use { out ->
        copyRange(raf, 0, moovPos, out)
        out.write(newMoov)
        copyRange(raf, moovPos + moovSize, len, out)
      }
    }
    return true
  }
}
