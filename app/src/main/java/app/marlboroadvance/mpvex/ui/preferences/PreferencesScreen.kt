package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import app.marlboroadvance.mpvex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistScreen
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.NetworkStreamingScreen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_preferences),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          // Search bar - full width, prominent placement
          item {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { backstack.add(SettingsSearchScreen) },
              shape = RoundedCornerShape(28.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              tonalElevation = 2.dp,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = stringResource(R.string.settings_search_hint),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }
          
          item { PreferenceSectionHeader(title = "UI & Appearance") }
          item {
            PreferenceTileRow(
              PrefTile(stringResource(R.string.pref_appearance_title), stringResource(R.string.pref_appearance_summary), Icons.Outlined.Palette) { backstack.add(AppearancePreferencesScreen) },
              PrefTile("Customize theme", "Background picture or color, accent color, transparency and blur", Icons.Outlined.Image) { backstack.add(CustomizeThemeScreen) },
            )
          }
          item {
            PreferenceTileRow(
              PrefTile(stringResource(R.string.pref_layout_title), stringResource(R.string.pref_layout_summary), Icons.AutoMirrored.Outlined.ViewQuilt) { backstack.add(PlayerControlsPreferencesScreen) },
              PrefTile("Font", "Font family, custom font, size, bold and italic", Icons.Outlined.TextFields) { backstack.add(FontSettingsScreen) },
            )
          }

          item { PreferenceSectionHeader(title = "Playback & Controls") }
          item {
            PreferenceTileRow(
              PrefTile(stringResource(R.string.pref_player), stringResource(R.string.pref_player_summary), Icons.Outlined.PlayCircle) { backstack.add(PlayerPreferencesScreen) },
              PrefTile(stringResource(R.string.pref_gesture), stringResource(R.string.pref_gesture_summary), Icons.Outlined.Gesture) { backstack.add(GesturePreferencesScreen) },
            )
          }

          item { PreferenceSectionHeader(title = "Media Settings") }
          item {
            PreferenceTileRow(
              PrefTile(stringResource(R.string.pref_decoder), stringResource(R.string.pref_decoder_summary), Icons.Outlined.Memory) { backstack.add(DecoderPreferencesScreen) },
              PrefTile(stringResource(R.string.pref_subtitles), stringResource(R.string.pref_subtitles_summary), Icons.Outlined.Subtitles) { backstack.add(SubtitlesPreferencesScreen) },
            )
          }
          item {
            PreferenceTileRow(
              PrefTile(stringResource(R.string.pref_audio), stringResource(R.string.pref_audio_summary), Icons.Outlined.Audiotrack) { backstack.add(AudioPreferencesScreen) },
            )
          }

          item { PreferenceSectionHeader(title = "File Management & Library") }
          item {
            PreferenceTileRow(
              PrefTile(stringResource(R.string.pref_folders_title), stringResource(R.string.pref_folders_summary), Icons.Outlined.Folder) { backstack.add(FoldersPreferencesScreen) },
              PrefTile("Recents", "Videos you played recently", Icons.Outlined.History) { backstack.add(RecentlyPlayedScreen) },
            )
          }
          item {
            PreferenceTileRow(
              PrefTile("Playlists", "Your video playlists", Icons.AutoMirrored.Outlined.PlaylistPlay) { backstack.add(PlaylistScreen) },
              PrefTile("Network", "Stream from network shares and links", Icons.Outlined.Language) { backstack.add(NetworkStreamingScreen) },
            )
          }

          item { PreferenceSectionHeader(title = "Backup & Restore") }
          item { BackupRestoreRows() }

          item {
            Text(
              text = "OnlyPlay  •  Created by darkdesign69",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            )
          }
        }
      }
    }
  }
}
