package app.onlyplay.ui.player.controls.components.sheets

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.onlyplay.preferences.AppearancePreferences
import app.onlyplay.preferences.PlayerButton
import app.onlyplay.preferences.PlayerPreferences
import app.onlyplay.preferences.preference.collectAsState
import app.onlyplay.presentation.components.PlayerSheet
import app.onlyplay.ui.player.Panels
import app.onlyplay.ui.player.Sheets
import app.onlyplay.ui.theme.AlertDialog
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject

private data class MxItem(
  val label: String,
  val icon: ImageVector,
  val onClick: () -> Unit,
)

private val shortcutChoices =
  listOf(
    "Screen Rotation" to PlayerButton.SCREEN_ROTATION,
    "Playback Speed" to PlayerButton.PLAYBACK_SPEED,
    "Background Play" to PlayerButton.BACKGROUND_PLAYBACK,
    "Loop" to PlayerButton.REPEAT_MODE,
    "Shuffle" to PlayerButton.SHUFFLE,
    "A-B Repeat" to PlayerButton.AB_LOOP,
    "Aspect Ratio" to PlayerButton.ASPECT_RATIO,
    "Video Zoom" to PlayerButton.VIDEO_ZOOM,
    "Picture-in-Picture" to PlayerButton.PICTURE_IN_PICTURE,
    "Screenshot / Frames" to PlayerButton.FRAME_NAVIGATION,
    "Decoder" to PlayerButton.DECODER,
    "Chapters" to PlayerButton.BOOKMARKS_CHAPTERS,
  )

private val ffSpeeds = listOf(1.5f, 2f, 3f, 4f, 5f)

/** MX Player style three-dot menu: a grid of player actions plus a "Shortcuts" switch. */
@Composable
fun MxMenuSheet(
  remainingTime: Int,
  onStartTimer: (Int) -> Unit,
  onShowSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current
  val playerPreferences = koinInject<PlayerPreferences>()
  val appearance = koinInject<AppearancePreferences>()
  val holdSpeed by playerPreferences.holdForMultipleSpeed.collectAsState()
  val portraitCsv by appearance.portraitBottomControls.collectAsState()

  var showShortcuts by remember { mutableStateOf(false) }
  var showDisplay by remember { mutableStateOf(true) }
  var showInfo by remember { mutableStateOf(false) }
  var showTimer by remember { mutableStateOf(false) }

  fun open(sheet: Sheets) = onShowSheet(sheet)

  val items =
    listOf(
      MxItem("Audio Track", Icons.Outlined.Audiotrack) { open(Sheets.AudioTracks) },
      MxItem("Subtitle", Icons.Outlined.Subtitles) { open(Sheets.SubtitleTracks) },
      MxItem("Aspect Ratio", Icons.Outlined.AspectRatio) { open(Sheets.AspectRatios) },
      MxItem("Speed FF (Long Press)\n${"%.1f".format(holdSpeed)}X", Icons.Outlined.FastForward) {
        val next = ffSpeeds.firstOrNull { it > holdSpeed + 0.01f } ?: ffSpeeds.first()
        playerPreferences.holdForMultipleSpeed.set(next)
      },
      MxItem("Playback Speed", Icons.Outlined.Speed) { open(Sheets.PlaybackSpeed) },
      MxItem("Display Settings", Icons.Outlined.Tune) {
        onDismissRequest()
        onOpenPanel(Panels.VideoFilters)
      },
      MxItem("Playlists", Icons.AutoMirrored.Outlined.PlaylistPlay) { open(Sheets.Playlist) },
      MxItem("Sleep Timer", Icons.Outlined.Timer) { showTimer = true },
      MxItem("Information", Icons.Outlined.Info) { showInfo = true },
      MxItem("Share", Icons.Outlined.Share) {
        runCatching {
          val data = (context as? android.app.Activity)?.intent?.data
          val path = MPVLib.getPropertyString("path")
          val send = Intent(Intent.ACTION_SEND)
          if (data != null && data.scheme == "content") {
            send.type = "video/*"
            send.putExtra(Intent.EXTRA_STREAM, data)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          } else {
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_TEXT, path ?: data?.toString() ?: "")
          }
          context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        onDismissRequest()
      },
    )

  PlayerSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier =
        Modifier
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      MxSwitchRow("Video Display", showDisplay) { showDisplay = it }
      if (showDisplay) {
        items.chunked(3).forEach { row ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item ->
              Column(
                modifier =
                  Modifier
                    .weight(1f)
                    .clickable { item.onClick() }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Text(
                  item.label,
                  style = MaterialTheme.typography.labelMedium,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(top = 4.dp),
                )
              }
            }
            repeat(3 - row.size) { Column(modifier = Modifier.weight(1f)) {} }
          }
        }
      }
      HorizontalDivider()
      MxSwitchRow("Shortcuts", showShortcuts) { showShortcuts = it }
      if (showShortcuts) {
        val enabled = portraitCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        shortcutChoices.forEach { (label, button) ->
          val checked = button.name in enabled
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable {
                  val updated =
                    if (checked) {
                      enabled.filter { it != button.name }
                    } else {
                      // keep the "more" button last so the order stays tidy
                      val more = PlayerButton.MORE_OPTIONS.name
                      enabled.filter { it != more } + button.name + (if (more in enabled) listOf(more) else emptyList())
                    }
                  appearance.portraitBottomControls.set(updated.joinToString(","))
                },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(label, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
          }
        }
      }
    }
  }

  if (showTimer) {
    TimePickerDialog(
      remainingTime = remainingTime,
      onDismissRequest = { showTimer = false },
      onTimeSelect = onStartTimer,
    )
  }

  if (showInfo) {
    fun p(name: String) = runCatching { MPVLib.getPropertyString(name) }.getOrNull()
    val text =
      listOf(
        "Title" to p("media-title"),
        "File" to p("path"),
        "Format" to p("file-format"),
        "Size" to p("video-params/w")?.let { w -> "$w x ${p("video-params/h")}" },
        "Video codec" to p("video-format"),
        "Audio codec" to p("audio-codec-name"),
        "FPS" to p("container-fps"),
        "Duration" to p("duration"),
      ).filter { !it.second.isNullOrBlank() }.joinToString("\n") { "${it.first}: ${it.second}" }
    AlertDialog(
      onDismissRequest = { showInfo = false },
      title = { Text("Information") },
      text = { Text(text.ifBlank { "No information available" }) },
      confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
    )
  }
}

@Composable
private fun MxSwitchRow(
  label: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
