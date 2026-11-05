package app.onlyplay.ui.browser.folderlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Home screen header: search, big app name, privacy lock, list/grid switch and settings. */
@Composable
fun HomeHeader(
  title: String,
  isGrid: Boolean,
  onSearchClick: () -> Unit,
  onToggleLayout: () -> Unit,
  onSortClick: () -> Unit,
  onSettingsClick: () -> Unit,
  onPrivacyClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onSearchClick) {
      Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
      text = title,
      fontSize = 30.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      modifier = Modifier.weight(1f).padding(start = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
      // Privacy (hidden videos) lock
      IconButton(onClick = onPrivacyClick) {
        Icon(Icons.Filled.Lock, contentDescription = "Privacy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      // The box icon opens Sort & view options
      IconButton(onClick = onSortClick) {
        Icon(
          imageVector = Icons.Filled.GridView,
          contentDescription = "Sort & view options",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      // A settings icon that goes straight to Settings
      IconButton(onClick = onSettingsClick) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
