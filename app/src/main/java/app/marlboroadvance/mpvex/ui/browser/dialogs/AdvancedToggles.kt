package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.storage.FileFilterUtils
import org.koin.compose.koinInject

/** The "Advanced" switches of the Sort & view window. Same list in every view mode. */
@Composable
fun rememberAdvancedToggles(): List<VisibilityToggle> {
  val browserPreferences = koinInject<BrowserPreferences>()
  val showDurationOverlay by browserPreferences.showDurationOverlay.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showHiddenFiles by browserPreferences.showHiddenFiles.collectAsState()
  val recognizeNoMedia by browserPreferences.recognizeNoMedia.collectAsState()

  return listOf(
    VisibilityToggle(
      label = "Display length over thumbnail",
      checked = showDurationOverlay,
      onCheckedChange = { browserPreferences.showDurationOverlay.set(it) },
    ),
    VisibilityToggle(
      label = "Show watch progress bar",
      checked = showProgressBar,
      onCheckedChange = { browserPreferences.showProgressBar.set(it) },
    ),
    VisibilityToggle(
      label = "Show hidden files and folders",
      checked = showHiddenFiles,
      onCheckedChange = {
        browserPreferences.showHiddenFiles.set(it)
        FileFilterUtils.configure(it, browserPreferences.recognizeNoMedia.get())
        MediaFileRepository.clearCache()
        MediaLibraryEvents.notifyChanged()
      },
    ),
    VisibilityToggle(
      label = "Recognize .nomedia",
      checked = recognizeNoMedia,
      onCheckedChange = {
        browserPreferences.recognizeNoMedia.set(it)
        FileFilterUtils.configure(browserPreferences.showHiddenFiles.get(), it)
        MediaFileRepository.clearCache()
        MediaLibraryEvents.notifyChanged()
      },
    ),
  )
}
