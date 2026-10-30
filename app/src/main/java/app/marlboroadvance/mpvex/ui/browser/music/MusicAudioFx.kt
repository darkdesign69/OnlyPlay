package app.marlboroadvance.mpvex.ui.browser.music

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs

/**
 * In-app audio effects (equalizer, bass boost, surround, volume boost) like Oto Music's
 * Equalizer page. Uses android.media.audiofx on the MediaPlayer's audio session.
 */
object MusicAudioFx {
  private var prefs: SharedPreferences? = null
  private var eq: Equalizer? = null
  private var bass: BassBoost? = null
  private var virt: Virtualizer? = null
  private var loud: LoudnessEnhancer? = null

  var enabled by mutableStateOf(false)
  var bassStrength by mutableIntStateOf(0) // 0..1000
  var surroundStrength by mutableIntStateOf(0) // 0..1000
  var boostMb by mutableIntStateOf(0) // 0..1000 millibels
  var bandCount by mutableIntStateOf(0)
  var minLevel by mutableIntStateOf(-1500)
  var maxLevel by mutableIntStateOf(1500)
  var freqsHz by mutableStateOf<List<Int>>(emptyList())
  val levels = mutableStateListOf<Int>()

  private fun init(ctx: Context) {
    if (prefs != null) return
    val p = ctx.applicationContext.getSharedPreferences("onlyplay_fx", Context.MODE_PRIVATE)
    prefs = p
    enabled = p.getBoolean("on", false)
    bassStrength = p.getInt("bass", 0)
    surroundStrength = p.getInt("surround", 0)
    boostMb = p.getInt("boost", 0)
    val saved = p.getString("levels", "") ?: ""
    levels.clear()
    saved.split(",").mapNotNull { it.toIntOrNull() }.forEach { levels.add(it) }
  }

  private fun save() {
    prefs
      ?.edit()
      ?.putBoolean("on", enabled)
      ?.putInt("bass", bassStrength)
      ?.putInt("surround", surroundStrength)
      ?.putInt("boost", boostMb)
      ?.putString("levels", levels.joinToString(","))
      ?.apply()
  }

  /** Called by the controller each time a track is prepared. */
  fun attach(
    ctx: Context,
    sessionId: Int,
  ) {
    if (sessionId == 0) return
    init(ctx)
    release()
    try {
      val e = Equalizer(0, sessionId)
      val n = e.numberOfBands.toInt()
      bandCount = n
      minLevel = e.bandLevelRange[0].toInt()
      maxLevel = e.bandLevelRange[1].toInt()
      freqsHz = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
      if (levels.size != n) {
        levels.clear()
        repeat(n) { levels.add(0) }
      }
      eq = e
    } catch (_: Throwable) {
      eq = null
    }
    try {
      bass = BassBoost(0, sessionId)
    } catch (_: Throwable) {
      bass = null
    }
    try {
      virt = Virtualizer(0, sessionId)
    } catch (_: Throwable) {
      virt = null
    }
    try {
      loud = LoudnessEnhancer(sessionId)
    } catch (_: Throwable) {
      loud = null
    }
    apply()
  }

  fun release() {
    try { eq?.release() } catch (_: Throwable) {}
    try { bass?.release() } catch (_: Throwable) {}
    try { virt?.release() } catch (_: Throwable) {}
    try { loud?.release() } catch (_: Throwable) {}
    eq = null
    bass = null
    virt = null
    loud = null
  }

  private fun apply() {
    try {
      eq?.let { e ->
        e.enabled = enabled
        for (i in 0 until minOf(levels.size, bandCount)) {
          e.setBandLevel(i.toShort(), levels[i].coerceIn(minLevel, maxLevel).toShort())
        }
      }
    } catch (_: Throwable) {
    }
    try {
      bass?.let { b ->
        b.enabled = enabled && bassStrength > 0
        if (b.strengthSupported) b.setStrength(bassStrength.toShort())
      }
    } catch (_: Throwable) {
    }
    try {
      virt?.let { v ->
        v.enabled = enabled && surroundStrength > 0
        if (v.strengthSupported) v.setStrength(surroundStrength.toShort())
      }
    } catch (_: Throwable) {
    }
    try {
      loud?.let { l ->
        l.setTargetGain(boostMb)
        l.enabled = boostMb > 0
      }
    } catch (_: Throwable) {
    }
  }

  fun switchOn(on: Boolean) { enabled = on; apply(); save() }
  fun setLevel(band: Int, mb: Int) { if (band in levels.indices) { levels[band] = mb; apply() } }
  fun setBass(v: Int) { bassStrength = v; apply() }
  fun setSurround(v: Int) { surroundStrength = v; apply() }
  fun setBoost(v: Int) { boostMb = v; apply() }
  fun commit() = save()

  val presetNames = listOf("Flat", "Bass+", "Treble+", "V-shape")

  fun usePreset(name: String) {
    val n = levels.size
    if (n == 0) return
    for (i in 0 until n) {
      val pos = if (n == 1) 0f else i / (n - 1f)
      val f =
        when (name) {
          "Bass+" -> (1f - pos * 2f).coerceAtLeast(0f) * 0.6f
          "Treble+" -> (pos * 2f - 1f).coerceAtLeast(0f) * 0.6f
          "V-shape" -> abs(pos * 2f - 1f) * 0.5f
          else -> 0f
        }
      levels[i] = (f * maxLevel).toInt()
    }
    apply()
    save()
  }
}

@Composable
fun EqualizerDialog(onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(28.dp), color = popupColor()) {
      Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Equalizer", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
          Switch(checked = MusicAudioFx.enabled, onCheckedChange = { MusicAudioFx.switchOn(it) })
        }
        Spacer(Modifier.height(12.dp))
        if (MusicAudioFx.bandCount == 0) {
          Text("Play a song first, then try again", color = MaterialTheme.colorScheme.outline)
        } else {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicAudioFx.presetNames.forEach { name ->
              FilterChip(selected = false, onClick = { MusicAudioFx.usePreset(name) }, label = { Text(name) })
            }
          }
          Spacer(Modifier.height(8.dp))
          for (i in 0 until MusicAudioFx.bandCount) {
            val hz = MusicAudioFx.freqsHz.getOrNull(i) ?: 0
            val label = if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz"
            val lv = MusicAudioFx.levels.getOrNull(i) ?: 0
            FxSlider(label, "%+.1f dB".format(lv / 100f), lv.toFloat(), MusicAudioFx.minLevel.toFloat()..MusicAudioFx.maxLevel.toFloat(), { MusicAudioFx.setLevel(i, it.toInt()) })
          }
        }
        Spacer(Modifier.height(8.dp))
        FxSlider("Bass boost", "${MusicAudioFx.bassStrength / 10}%", MusicAudioFx.bassStrength.toFloat(), 0f..1000f, { MusicAudioFx.setBass(it.toInt()) })
        FxSlider("Surround sound", "${MusicAudioFx.surroundStrength / 10}%", MusicAudioFx.surroundStrength.toFloat(), 0f..1000f, { MusicAudioFx.setSurround(it.toInt()) })
        FxSlider("Volume boost", "+%.1f dB".format(MusicAudioFx.boostMb / 100f), MusicAudioFx.boostMb.toFloat(), 0f..1000f, { MusicAudioFx.setBoost(it.toInt()) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("Close") }
        }
      }
    }
  }
}

@Composable
private fun FxSlider(
  title: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    Row {
      Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
      Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
    Slider(value = value, onValueChange = onChange, valueRange = range, onValueChangeFinished = { MusicAudioFx.commit() })
  }
}
