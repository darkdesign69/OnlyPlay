package app.marlboroadvance.mpvex.ui.browser.music

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Now-playing themes modelled on Oto Music (decoded from its bytecode).
 * Indices match MusicSettings.nowPlayingBg:
 * 0 Default, 1 Colored, 2 Gradient, 3 Blur, 4 Gradient Blur, 5 Glow, 6 Material You.
 */
object OtoTheme {
  const val DEFAULT = 0
  const val COLORED = 1
  const val GRADIENT = 2
  const val BLUR = 3
  const val GRADIENT_BLUR = 4
  const val GLOW = 5
  const val MATERIAL_YOU = 6

  /** Oto cover corner radius per theme (dp). */
  fun coverRadius(theme: Int): Dp = if (theme == MATERIAL_YOU) 22.dp else 18.dp

  /** Oto cover elevation per theme (dp). */
  fun coverElevation(theme: Int): Dp =
    when (theme) {
      DEFAULT, GRADIENT, BLUR, GRADIENT_BLUR -> 10.dp
      else -> 0.dp
    }

  /** Themes whose backdrop is always dark (scrim / blurred art), so text must be white. */
  fun forcesLightText(theme: Int): Boolean = theme == GRADIENT || theme == BLUR || theme == GRADIENT_BLUR
}

/** Tonal colours derived from the cover colour (Monet-style: tone 8 dark / 95 light background). */
class OtoPalette(
  val background: Color,
  val primary: Color,
  val glow: Color,
)

fun otoPalette(seed: Color, dark: Boolean): OtoPalette {
  val s = if (seed == Color.Transparent) Color(0xFF6750A4) else seed
  val lch = toLch(s)
  val chroma = max(lch[1], 45f)
  val hue = lch[2]
  val bg = fromLch(if (dark) 12f else 93f, min(chroma, 36f), hue)
  val primary = fromLch(if (dark) 80f else 40f, chroma, hue)
  val glow = fromLch(if (dark) 55f else 75f, chroma, hue)
  return OtoPalette(bg, primary, glow)
}

private fun lin(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun gam(c: Float): Float = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f

/** Returns [L, C, h(deg)]. */
private fun toLch(c: Color): FloatArray {
  val r = lin(c.red)
  val g = lin(c.green)
  val b = lin(c.blue)
  val x = (0.4124564f * r + 0.3575761f * g + 0.1804375f * b) / 0.95047f
  val y = 0.2126729f * r + 0.7151522f * g + 0.0721750f * b
  val z = (0.0193339f * r + 0.1191920f * g + 0.9503041f * b) / 1.08883f
  fun f(t: Float): Float = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
  val fx = f(x)
  val fy = f(y)
  val fz = f(z)
  val l = 116f * fy - 16f
  val a = 500f * (fx - fy)
  val bb = 200f * (fy - fz)
  var h = (atan2(bb, a) * 180f / PI.toFloat())
  if (h < 0f) h += 360f
  return floatArrayOf(l, sqrt(a * a + bb * bb), h)
}

private fun labToColor(l: Float, a: Float, b: Float): Triple<Float, Float, Float> {
  val fy = (l + 16f) / 116f
  val fx = fy + a / 500f
  val fz = fy - b / 200f
  fun inv(t: Float): Float = if (t * t * t > 0.008856f) t * t * t else (t - 16f / 116f) / 7.787f
  val x = inv(fx) * 0.95047f
  val y = inv(fy)
  val z = inv(fz) * 1.08883f
  val r = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
  val g = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
  val bl = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z
  return Triple(r, g, bl)
}

/** Lab -> sRGB with gamut clipping by lowering chroma until the colour fits. */
private fun fromLch(l: Float, c: Float, h: Float): Color {
  val rad = h * PI.toFloat() / 180f
  var chroma = c
  repeat(40) {
    val (r, g, b) = labToColor(l, chroma * cos(rad), chroma * sin(rad))
    val eps = 0.0005f
    if (r in -eps..1f + eps && g in -eps..1f + eps && b in -eps..1f + eps) {
      return Color(gam(r.coerceIn(0f, 1f)), gam(g.coerceIn(0f, 1f)), gam(b.coerceIn(0f, 1f)))
    }
    chroma *= 0.92f
  }
  val (r, g, b) = labToColor(l, 0f, 0f)
  return Color(gam(r.coerceIn(0f, 1f)), gam(g.coerceIn(0f, 1f)), gam(b.coerceIn(0f, 1f)))
}

/** Oto's blurred background: 192px art, saturation x3, OVERLAY 0x33000000, 0x40000000 tint, blur radius 25. */
fun otoBlurBitmap(src: Bitmap): Bitmap {
  val size = 192
  val scaled = Bitmap.createScaledBitmap(src, size, size, true)
  val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val canvas = AndroidCanvas(out)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(3f) })
  canvas.drawBitmap(scaled, 0f, 0f, paint)
  canvas.drawColor(0x33000000, PorterDuff.Mode.OVERLAY)
  canvas.drawColor(0x40000000)
  boxBlur(out, 25)
  return out
}

/** Three box-blur passes ~ gaussian / stack blur of the given radius (in place). */
private fun boxBlur(bmp: Bitmap, radius: Int) {
  val w = bmp.width
  val h = bmp.height
  val px = IntArray(w * h)
  bmp.getPixels(px, 0, w, 0, 0, w, h)
  val tmp = IntArray(w * h)
  val r = max(1, radius / 2)
  repeat(3) {
    blurPass(px, tmp, w, h, r, true)
    blurPass(tmp, px, w, h, r, false)
  }
  bmp.setPixels(px, 0, w, 0, 0, w, h)
}

private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
  val lines = if (horizontal) h else w
  val len = if (horizontal) w else h
  val win = 2 * r + 1
  for (line in 0 until lines) {
    var a = 0
    var rr = 0
    var g = 0
    var b = 0
    fun idx(i: Int): Int {
      val k = i.coerceIn(0, len - 1)
      return if (horizontal) line * w + k else k * w + line
    }
    for (i in -r..r) {
      val p = src[idx(i)]
      a += p ushr 24
      rr += (p shr 16) and 0xFF
      g += (p shr 8) and 0xFF
      b += p and 0xFF
    }
    for (i in 0 until len) {
      dst[idx(i)] = ((a / win) shl 24) or ((rr / win) shl 16) or ((g / win) shl 8) or (b / win)
      val add = src[idx(i + r + 1)]
      val rem = src[idx(i - r)]
      a += (add ushr 24) - (rem ushr 24)
      rr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
      g += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
      b += (add and 0xFF) - (rem and 0xFF)
    }
  }
}

/** Radial alpha profile measured from Oto's glow sprite (rho 0..1 -> alpha). */
private val glowStops =
  arrayOf(
    0.0f to 1.0f, 0.1f to 0.98f, 0.2f to 0.924f, 0.3f to 0.836f, 0.4f to 0.719f, 0.5f to 0.582f,
    0.6f to 0.431f, 0.7f to 0.279f, 0.8f to 0.135f, 0.9f to 0.019f, 1.0f to 0.0f,
  )

/**
 * Full-screen backdrop for the given theme. Draws nothing for DEFAULT (caller's surface shows).
 */
@Composable
fun NowPlayingBackdrop(
  theme: Int,
  blurred: Bitmap?,
  palette: OtoPalette,
  playing: Boolean,
  modifier: Modifier = Modifier.fillMaxSize(),
) {
  Box(modifier = modifier) {
    when (theme) {
      OtoTheme.COLORED, OtoTheme.MATERIAL_YOU -> Box(Modifier.fillMaxSize().background(palette.background))
      OtoTheme.GRADIENT -> Box(Modifier.fillMaxSize().background(palette.background))
      OtoTheme.BLUR, OtoTheme.GRADIENT_BLUR -> {
        if (blurred != null) {
          Image(
            bitmap = blurred.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(Modifier.fillMaxSize().background(palette.background))
        }
      }
      OtoTheme.GLOW -> {
        Box(Modifier.fillMaxSize().background(palette.background))
        GlowLayers(palette.glow, playing)
      }
    }
    if (theme == OtoTheme.GRADIENT || theme == OtoTheme.GRADIENT_BLUR) {
      // Oto's ShadowView: transparent -> black, top to bottom.
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
    }
  }
}

@Composable
private fun GlowLayers(color: Color, playing: Boolean) {
  val transition = rememberInfiniteTransition(label = "glow")
  val running by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
      label = "glowProgress",
    )
  // Oto holds the glow still (near 0.27) while paused.
  val p = if (playing) running else 0.27f
  Canvas(Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val radius = max(w, h) * 1.0f
    val ang = (p * 2f * PI).toFloat()
    val centers =
      listOf(
        Offset(w * (0.5f + 0.32f * cos(ang)), h * (0.38f + 0.22f * sin(ang))),
        Offset(w * (0.5f - 0.32f * cos(ang)), h * (0.62f - 0.22f * sin(ang))),
      )
    for (c in centers) {
      val brush =
        Brush.radialGradient(
          colorStops = Array(glowStops.size) { i -> glowStops[i].first to color.copy(alpha = glowStops[i].second * 0.85f) },
          center = c,
          radius = radius,
        )
      drawRect(brush = brush)
    }
  }
}

/** Picks a vivid representative colour of the art (plain averages are usually grey and wash out). */
fun dominantColor(src: Bitmap): Color {
  val b = Bitmap.createScaledBitmap(src, 24, 24, true)
  var wr = 0.0
  var wg = 0.0
  var wb = 0.0
  var wt = 0.0
  var ar = 0.0
  var ag = 0.0
  var ab = 0.0
  val hsv = FloatArray(3)
  for (y in 0 until 24) for (x in 0 until 24) {
    val p = b.getPixel(x, y)
    val r = (p shr 16) and 0xFF
    val g = (p shr 8) and 0xFF
    val bl = p and 0xFF
    ar += r; ag += g; ab += bl
    android.graphics.Color.RGBToHSV(r, g, bl, hsv)
    val w = (hsv[1] * hsv[1] * (0.2f + hsv[2])).toDouble()
    wr += r * w; wg += g * w; wb += bl * w; wt += w
  }
  return if (wt > 0.5) {
    Color((wr / wt / 255.0).toFloat(), (wg / wt / 255.0).toFloat(), (wb / wt / 255.0).toFloat())
  } else {
    Color((ar / 576 / 255.0).toFloat(), (ag / 576 / 255.0).toFloat(), (ab / 576 / 255.0).toFloat())
  }
}
