package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One settings entry shown as a tile: round icon, title, short summary and a chevron. */
class PrefTile(
  val title: String,
  val summary: String,
  val icon: ImageVector,
  val onClick: () -> Unit,
)

/** One or two tiles side by side (a single tile fills the whole row). */
@Composable
fun PreferenceTileRow(
  first: PrefTile,
  second: PrefTile? = null,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .padding(horizontal = 16.dp, vertical = 5.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    PreferenceTile(first, Modifier.weight(1f).fillMaxHeight())
    if (second != null) PreferenceTile(second, Modifier.weight(1f).fillMaxHeight())
  }
}

@Composable
private fun PreferenceTile(
  tile: PrefTile,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = tile.onClick,
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(tile.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = tile.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = tile.summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
      )
    }
  }
}
