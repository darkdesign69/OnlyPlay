package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

private val QuickColors =
  listOf(
    0xFF7B1F1E, 0xFFC2410C, 0xFFB45309, 0xFF166534, 0xFF0F766E,
    0xFF1D4ED8, 0xFF2D4AAE, 0xFF6D28D9, 0xFFBE185D, 0xFF52525B,
    0xFFFFFFFF, 0xFF000000,
  ).map { it.toInt() }

private fun hexOf(
  hue: Float,
  saturation: Float,
  value: Float,
): String = "%06X".format(Color.hsv(hue, saturation, value).toArgb() and 0xFFFFFF)

/** A settings row that shows a colour swatch; tap it to change the colour. */
@Composable
fun SliderColorRow(
  title: String,
  color: Int,
  onClick: () -> Unit,
) {
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = {
      Text(
        text = "#%06X".format(color and 0xFFFFFF),
        color = MaterialTheme.colorScheme.outline,
      )
    },
    trailingContent = {
      Box(
        modifier =
          Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
      )
    },
  )
}

/**
 * Colour picker for a slider bar: hue / saturation / brightness sliders, a hex field and a few quick
 * colours, with a live preview of the bar (top colour fading into the bottom colour).
 */
@Composable
fun SliderColorPickerDialog(
  title: String,
  initialColor: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
  /** The colour at the other end of the bar, used for the preview. */
  otherColor: Int = 0xFFFFFFFF.toInt(),
  /** True when this picker chooses the bottom colour of the bar. */
  isBottom: Boolean = false,
) {
  val start = remember(initialColor) { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) } }
  var hue by remember { mutableFloatStateOf(start[0]) }
  var sat by remember { mutableFloatStateOf(start[1]) }
  var value by remember { mutableFloatStateOf(start[2]) }
  var hex by remember { mutableStateOf(hexOf(start[0], start[1], start[2])) }
  val color = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

  fun pick(argb: Int) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hue = hsv[0]
    sat = hsv[1]
    value = hsv[2]
    hex = hexOf(hsv[0], hsv[1], hsv[2])
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Color", style = MaterialTheme.typography.labelMedium)
            Slider(
              value = hue,
              onValueChange = {
                hue = it
                hex = hexOf(it, sat, value)
              },
              valueRange = 0f..360f,
            )
            Text("Strength", style = MaterialTheme.typography.labelMedium)
            Slider(
              value = sat,
              onValueChange = {
                sat = it
                hex = hexOf(hue, it, value)
              },
              valueRange = 0f..1f,
            )
            Text("Brightness", style = MaterialTheme.typography.labelMedium)
            Slider(
              value = value,
              onValueChange = {
                value = it
                hex = hexOf(hue, sat, it)
              },
              valueRange = 0f..1f,
            )
          }
          Box(
            modifier =
              Modifier
                .width(24.dp)
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(if (isBottom) listOf(Color(otherColor), color) else listOf(color, Color(otherColor)))),
          )
        }
        OutlinedTextField(
          value = hex,
          onValueChange = { raw ->
            val cleaned = raw.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
            hex = cleaned
            if (cleaned.length == 6) {
              pick((0xFF000000L or cleaned.toLong(16)).toInt())
            }
          },
          label = { Text("Hex colour") },
          prefix = { Text("#") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        QuickColors.chunked(5).forEach { rowColors ->
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowColors.forEach { argb ->
              Box(
                modifier =
                  Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { pick(argb) },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(color.toArgb()) }) { Text("Save") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
