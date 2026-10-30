package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.SliderColorScheme
import app.marlboroadvance.mpvex.ui.theme.spacing
import org.koin.compose.koinInject
import kotlin.math.roundToInt

fun percentage(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
): Float = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

fun percentage(
  value: Int,
  range: ClosedRange<Int>,
): Float = ((value - range.start - 0f) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

@Composable
fun VerticalSlider(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
  overflowValue: Float? = null,
  overflowRange: ClosedFloatingPointRange<Float>? = null,
) {
  val coercedValue = value.coerceIn(range)
  Box(
    modifier =
      modifier
        .height(120.dp)
        .aspectRatio(0.2f)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.background)
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          shape = RoundedCornerShape(16.dp),
        ),
    contentAlignment = Alignment.BottomCenter,
  ) {
    val targetHeight by animateFloatAsState(percentage(coercedValue, range), label = "vsliderheight")
    Box(
      Modifier
        .fillMaxWidth()
        .fillMaxHeight(targetHeight)
        .background(MaterialTheme.colorScheme.tertiary),
    )
    if (overflowRange != null && overflowValue != null) {
      val overflowHeight by animateFloatAsState(
        percentage(overflowValue, overflowRange),
        label = "vslideroverflowheight",
      )
      Box(
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(overflowHeight)
          .background(MaterialTheme.colorScheme.errorContainer),
      )
    }
  }
}

@Composable
fun VerticalSlider(
  value: Int,
  range: ClosedRange<Int>,
  modifier: Modifier = Modifier,
  overflowValue: Int? = null,
  overflowRange: ClosedRange<Int>? = null,
) {
  val coercedValue = value.coerceIn(range)
  Box(
    modifier =
      modifier
        .height(120.dp)
        .aspectRatio(0.2f)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.background)
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          shape = RoundedCornerShape(16.dp),
        ),
    contentAlignment = Alignment.BottomCenter,
  ) {
    val targetHeight by animateFloatAsState(percentage(coercedValue, range), label = "vsliderheight")
    Box(
      Modifier
        .fillMaxWidth()
        .fillMaxHeight(targetHeight)
        .background(MaterialTheme.colorScheme.tertiary),
    )
    if (overflowRange != null && overflowValue != null) {
      val overflowHeight by animateFloatAsState(
        percentage(overflowValue, overflowRange),
        label = "vslideroverflowheight",
      )
      Box(
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(overflowHeight)
          .background(MaterialTheme.colorScheme.errorContainer),
      )
    }
  }
}

/**
 * Tall pill bar with a gradient (white at the bottom, [topColor] at the top), a number above it and an
 * icon below it. The gradient always spans the whole bar and is revealed as the level goes up.
 */
@Composable
private fun PillSlider(
  fraction: Float,
  topColor: Color,
  bottomColor: Color = Color.White,
  iconRes: Int,
  label: String,
  modifier: Modifier = Modifier,
) {
  val animatedFraction by animateFloatAsState(
    targetValue = fraction.coerceIn(0f, 1f),
    animationSpec = tween(durationMillis = 80),
    label = "pillsliderfill",
  )
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = label,
      color = Color.White,
      style = MaterialTheme.typography.labelLarge,
      textAlign = TextAlign.Center,
    )
    Box(
      modifier =
        Modifier
          .width(26.dp)
          .height(190.dp)
          .clip(RoundedCornerShape(13.dp))
          .background(Color.White.copy(alpha = 0.22f)),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .drawWithContent {
              clipRect(top = size.height * (1f - animatedFraction)) {
                this@drawWithContent.drawContent()
              }
            }
            .background(
              Brush.verticalGradient(
                colors = listOf(topColor, bottomColor),
              ),
            ),
      )
    }
    Icon(
      painter = painterResource(iconRes),
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(28.dp),
    )
  }
}

@Composable
fun BrightnessSlider(
  brightness: Float,
  range: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
) {
  val fraction = percentage(brightness.coerceIn(range), range)
  val sliderPrefs = koinInject<PlayerPreferences>()
  val scheme by sliderPrefs.sliderColorScheme.collectAsState()
  val custom by sliderPrefs.sliderCustomBrightnessColor.collectAsState()
  val customBottom by sliderPrefs.sliderCustomBrightnessBottomColor.collectAsState()
  PillSlider(
    fraction = fraction,
    topColor =
      if (scheme == SliderColorScheme.Custom) Color(custom) else scheme.brightnessTop ?: MaterialTheme.colorScheme.tertiary,
    bottomColor = if (scheme == SliderColorScheme.Custom) Color(customBottom) else Color.White,
    iconRes = R.drawable.ic_slider_brightness,
    label = (fraction * 100).roundToInt().toString(),
    modifier = modifier,
  )
}

/**
 * Volume on a 0..200 scale: 0..100 is the phone's own volume (0 to its maximum), 101..200 is the
 * boosted volume above the phone's maximum. When boost is switched off the scale is 0..100.
 */
@Composable
fun VolumeSlider(
  volume: Int,
  mpvVolume: Int,
  range: ClosedRange<Int>,
  boostRange: ClosedRange<Int>?,
  modifier: Modifier = Modifier,
  displayAsPercentage: Boolean = false,
) {
  val maxVolume = range.endInclusive.coerceAtLeast(1)
  val boostCap = boostRange?.endInclusive ?: 0
  val top = if (boostCap > 0) 200f else 100f
  val level =
    if (boostCap > 0 && mpvVolume > 100 && volume >= maxVolume) {
      100f + (mpvVolume - 100).coerceAtMost(boostCap) * 100f / boostCap
    } else {
      volume * 100f / maxVolume
    }
  val sliderPrefs = koinInject<PlayerPreferences>()
  val scheme by sliderPrefs.sliderColorScheme.collectAsState()
  val custom by sliderPrefs.sliderCustomVolumeColor.collectAsState()
  val customBottom by sliderPrefs.sliderCustomVolumeBottomColor.collectAsState()
  PillSlider(
    fraction = level / top,
    topColor =
      if (scheme == SliderColorScheme.Custom) Color(custom) else scheme.volumeTop ?: MaterialTheme.colorScheme.primary,
    bottomColor = if (scheme == SliderColorScheme.Custom) Color(customBottom) else Color.White,
    iconRes = R.drawable.ic_slider_volume,
    label = level.roundToInt().toString(),
    modifier = modifier,
  )
}

val getVolumeSliderText: @Composable (Int, Int, Int, Int, Boolean) -> String =
  { volume, mpvVolume, boostVolume, percentage, displayAsPercentage ->
    when {
      mpvVolume == 100 ->
        if (displayAsPercentage) {
          "$percentage"
        } else {
          "$volume"
        }

      mpvVolume > 100 -> {
        if (displayAsPercentage) {
          "${percentage + boostVolume}"
        } else {
          stringResource(R.string.volume_slider_absolute_value, volume + boostVolume)
        }
      }

      mpvVolume < 100 -> {
        if (displayAsPercentage) {
          "${percentage + boostVolume}"
        } else {
          stringResource(R.string.volume_slider_absolute_value, volume + boostVolume)
        }
      }

      else -> {
        if (displayAsPercentage) {
          "$percentage"
        } else {
          "$volume"
        }
      }
    }
  }
