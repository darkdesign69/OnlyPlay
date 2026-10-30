package app.marlboroadvance.mpvex.ui.browser.music

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lets the home screen ask the music screen to open straight on Now playing. */
internal object MusicNav {
  var openNow = false

  fun takeOpenNow(): Boolean {
    val v = openNow
    openNow = false
    return v
  }
}

private fun barTime(ms: Int): String {
  val total = (ms / 1000).coerceAtLeast(0)
  val h = total / 3600
  val m = total % 3600 / 60
  val s = total % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Small player bar for the home screen: shows the song that is playing (close, cover, title, time,
 * play/pause and a button for the music library). It only shows while a song is loaded.
 */
@Composable
fun HomeMiniAudioBar(
  onOpenNow: () -> Unit,
  onOpenLibrary: () -> Unit,
) {
  val t = MusicController.current ?: return
  val context = LocalContext.current
  var pos by remember { mutableIntStateOf(0) }
  LaunchedEffect(t.id, MusicController.isPlaying) {
    while (true) {
      pos = MusicController.position()
      delay(500)
    }
  }
  val art by produceState<Bitmap?>(null, t.id) { value = withContext(Dispatchers.IO) { loadArt(context, t, 160) } }
  MusicTypographyTheme {
    SolidThemed {
      Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        // Same bar as inside the music screen: swipe down to close, previous / play / next
        MiniPlayer(onOpen = onOpenNow)
      }
    }
  }
}
