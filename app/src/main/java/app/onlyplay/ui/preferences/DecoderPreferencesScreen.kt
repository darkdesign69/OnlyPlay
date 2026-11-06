package app.onlyplay.ui.preferences

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import app.onlyplay.ui.theme.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.onlyplay.R
import app.onlyplay.preferences.DecoderPreferences
import app.onlyplay.preferences.preference.collectAsState
import app.onlyplay.presentation.Screen
import app.onlyplay.ui.player.Debanding
import app.onlyplay.ui.player.MPVProfile
import app.onlyplay.ui.utils.LocalBackStack
import app.onlyplay.ui.preferences.VulkanUtils
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

enum class DecoderModeOption(val value: String, val label: String, val hint: String) {
  Auto("auto", "Auto (mpvKt style)", "Lets mpv pick the best hardware decoder for each video. Best for most videos."),
  Hw("hw", "HW (fastest)", "Hardware only, no copying. Fastest, but some videos flicker or show black."),
  HwCopy("hwcopy", "HW+ (compatible)", "Hardware decoding with a copy step. Fixes flicker on many phones."),
  Sw("sw", "SW (software)", "Always software. Most compatible, but heavier on the battery and CPU."),
  ;

  override fun toString() = label

  companion object {
    fun fromValue(v: String) = entries.firstOrNull { it.value == v } ?: Auto
  }
}

enum class SkipLoopOption(val value: String, val label: String, val hint: String) {
  Default("default", "Default", "Normal quality."),
  NonRef("nonref", "Skip non-reference frames", "Lighter on the CPU for heavy 1080p and 4K software playback."),
  All("all", "Skip all", "Lightest on the CPU, but the picture looks blockier."),
  ;

  override fun toString() = label

  companion object {
    fun fromValue(v: String) = entries.firstOrNull { it.value == v } ?: Default
  }
}

enum class FrameDropOption(val value: String, val label: String, val hint: String) {
  Default("default", "Default", "Drops frames only when the player thinks it has to."),
  Vo("vo", "Drop at display", "Keeps video in sync with audio when the screen can't keep up."),
  Both("decoder+vo", "Drop at decoder and display", "Best for slow phones. Keeps sound in sync, may skip frames."),
  ;

  override fun toString() = label

  companion object {
    fun fromValue(v: String) = entries.firstOrNull { it.value == v } ?: Default
  }
}

@Serializable
object DecoderPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<DecoderPreferences>()
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val isVulkanSupported = remember { VulkanUtils.isVulkanSupported(context) }
    var showGpuNextWarning by remember { mutableStateOf(false) }
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_decoder),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack,
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
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_decoder))
          }

          item {
            PreferenceCard {
              val profile by preferences.profile.collectAsState()
              val currentProfile = MPVProfile.fromValue(profile)
              ListPreference(
                value = currentProfile,
                onValueChange = { preferences.profile.set(it.value) },
                values = MPVProfile.entries,
                title = { Text(stringResource(R.string.pref_decoder_profile_title)) },
                summary = {
                  Text(
                    currentProfile.displayName,
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val tryHWDecoding by preferences.tryHWDecoding.collectAsState()
              SwitchPreference(
                value = tryHWDecoding,
                onValueChange = {
                  preferences.tryHWDecoding.set(it)
                },
                title = { Text(stringResource(R.string.pref_decoder_try_hw_dec_title)) },
              )

              PreferenceDivider()

              val decoderMode by preferences.decoderMode.collectAsState()
              val currentMode = DecoderModeOption.fromValue(decoderMode)
              ListPreference(
                value = currentMode,
                onValueChange = { preferences.decoderMode.set(it.value) },
                values = DecoderModeOption.entries,
                title = { Text("Decoder mode") },
                summary = { Text(currentMode.hint, color = MaterialTheme.colorScheme.outline) },
              )

              PreferenceDivider()

              val cpuThreads by preferences.cpuThreads.collectAsState()
              ListPreference(
                value = cpuThreads,
                onValueChange = { preferences.cpuThreads.set(it) },
                values = listOf(0, 1, 2, 3, 4, 6, 8),
                valueToText = { AnnotatedString(if (it == 0) "Auto" else "$it cores") },
                title = { Text("CPU core limit") },
                summary = {
                  Text(
                    "Cores used by the software decoder. Auto is best for most phones.",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val skipLoop by preferences.skipLoopFilter.collectAsState()
              val currentSkip = SkipLoopOption.fromValue(skipLoop)
              ListPreference(
                value = currentSkip,
                onValueChange = { preferences.skipLoopFilter.set(it.value) },
                values = SkipLoopOption.entries,
                title = { Text("Skip loop filter") },
                summary = {
                  Text(
                    currentSkip.hint,
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val fastDecode by preferences.fastDecode.collectAsState()
              SwitchPreference(
                value = fastDecode,
                onValueChange = { preferences.fastDecode.set(it) },
                title = { Text("Use speedup tricks") },
                summary = {
                  Text(
                    "Faster software decoding with a small drop in quality.",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val frameDrop by preferences.frameDrop.collectAsState()
              val currentDrop = FrameDropOption.fromValue(frameDrop)
              ListPreference(
                value = currentDrop,
                onValueChange = { preferences.frameDrop.set(it.value) },
                values = FrameDropOption.entries,
                title = { Text("Drop frames when slow") },
                summary = { Text(currentDrop.hint, color = MaterialTheme.colorScheme.outline) },
              )

              PreferenceDivider()

              val gpuNext by preferences.gpuNext.collectAsState()
              val useVulkan by preferences.useVulkan.collectAsState() // Added to check Vulkan state
              SwitchPreference(
                value = gpuNext,
                onValueChange = { enabled ->
                    if (enabled && !gpuNext && !useVulkan) { // Only show warning if Vulkan is disabled
                        showGpuNextWarning = true
                    } else {
                        preferences.gpuNext.set(enabled)
                        if (enabled && !useVulkan) { // Only disable Anime4K if Vulkan is disabled
                            preferences.enableAnime4K.set(false)
                        }
                    }
                },
                title = { Text(stringResource(R.string.pref_decoder_gpu_next_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_decoder_gpu_next_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              if (showGpuNextWarning) {
                  AlertDialog(
                      onDismissRequest = { showGpuNextWarning = false },
                      title = { Text(stringResource(R.string.pref_decoder_gpu_next_enable_title)) },
                      text = {
                          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                              Text(stringResource(R.string.pref_decoder_gpu_next_warning))
                              Text(stringResource(R.string.pref_decoder_gpu_next_purple_screen_fix))
                              
                              Surface(
                                  color = MaterialTheme.colorScheme.errorContainer,
                                  shape = MaterialTheme.shapes.small
                              ) {
                                  Column(modifier = Modifier.padding(8.dp)) {
                                      Text(
                                          text = stringResource(R.string.pref_anime4k_incompatibility),
                                          style = MaterialTheme.typography.titleSmall,
                                          color = MaterialTheme.colorScheme.onErrorContainer
                                      )
                                      Text(
                                          text = stringResource(R.string.pref_anime4k_gpu_next_error),
                                          style = MaterialTheme.typography.bodySmall,
                                          color = MaterialTheme.colorScheme.onErrorContainer
                                      )
                                  }
                              }
                          }
                      },
                      confirmButton = {
                          Button(onClick = {
                              preferences.gpuNext.set(true)
                              preferences.enableAnime4K.set(false) // Ensure Anime4K is disabled on confirmation
                              showGpuNextWarning = false
                          }) {
                              Text(stringResource(R.string.pref_decoder_gpu_next_enable_anyway))
                          }
                      },
                      dismissButton = {
                          TextButton(onClick = { showGpuNextWarning = false }) {
                              Text(stringResource(R.string.generic_cancel))
                          }
                      }
                  )
              }

              PreferenceDivider()

              // val useVulkan by preferences.useVulkan.collectAsState() // Moved up for gpuNext logic
              SwitchPreference(
                value = useVulkan,
                onValueChange = { enabled ->
                  preferences.useVulkan.set(enabled)
                  // When Vulkan is disabled, ensure Anime4K and GPU Next are not both enabled
                  if (!enabled) {
                    val anime4kEnabled = preferences.enableAnime4K.get()
                    val gpuNextEnabled = preferences.gpuNext.get()
                    if (anime4kEnabled && gpuNextEnabled) {
                      // Disable GPU Next to keep Anime4K
                      preferences.gpuNext.set(false)
                    }
                  }
                },
                enabled = isVulkanSupported,
                title = { Text(stringResource(R.string.pref_decoder_vulkan_title) + " (Experimental)") },
                summary = {
                  Text(
                    stringResource(
                      if (isVulkanSupported) R.string.pref_decoder_vulkan_summary
                      else R.string.pref_decoder_vulkan_not_supported
                    ),
                    color = if (isVulkanSupported) MaterialTheme.colorScheme.outline
                           else MaterialTheme.colorScheme.error,
                  )
                },
              )

              PreferenceDivider()

              val debanding by preferences.debanding.collectAsState()
              ListPreference(
                value = debanding,
                onValueChange = { preferences.debanding.set(it) },
                values = Debanding.entries,
                title = { Text(stringResource(R.string.pref_decoder_debanding_title)) },
                summary = {
                  Text(
                    debanding.name,
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val useYUV420p by preferences.useYUV420P.collectAsState()
              SwitchPreference(
                value = useYUV420p,
                onValueChange = {
                  preferences.useYUV420P.set(it)
                },
                title = { Text(stringResource(R.string.pref_decoder_yuv420p_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_decoder_yuv420p_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()
              
              val enableAnime4K by preferences.enableAnime4K.collectAsState()
              SwitchPreference(
                value = enableAnime4K,
                onValueChange = { enabled ->
                    preferences.enableAnime4K.set(enabled)
                    if (enabled && !useVulkan) { // Only disable GPU Next if Vulkan is disabled
                        preferences.gpuNext.set(false)
                    }
                },
                title = { Text(stringResource(R.string.pref_anime4k_title)) },
                summary = {
                  Column {
                    Text(
                      stringResource(R.string.pref_anime4k_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                      text = "github.com/bloc97/Anime4K",
                      color = MaterialTheme.colorScheme.primary,
                      style = MaterialTheme.typography.bodySmall,
                      textDecoration = TextDecoration.Underline,
                      modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bloc97/Anime4K"))
                        context.startActivity(intent)
                      }
                    )
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

object VulkanUtils {
    private const val TAG = "VulkanUtils"

    /**
     * Checks if the device supports Vulkan for MPV rendering
     *
     * Requirements for MPV androidvk context:
     * - Android 13 (API 33) minimum for Vulkan 1.3
     * - Vulkan 1.3 (0x00403000) hardware version
     * - GPU must also support OpenGL ES 3.1 or higher
     *
     * @return true if Vulkan 1.3+ is supported for MPV, false otherwise
     */
    fun isVulkanSupported(context: Context): Boolean {
        try {
            // Vulkan 1.3 requires Android 13 (API 33) minimum
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Log.d(TAG, "Vulkan not supported: Android version ${Build.VERSION.SDK_INT} < 33 (Tiramisu)")
                return false
            }

            val packageManager = context.packageManager

            // Check for OpenGL ES 3.1+ support (required by Android for Vulkan)
            val configInfo = packageManager.systemAvailableFeatures
                .firstOrNull { it.name == null }

            val glesVersion = configInfo?.reqGlEsVersion ?: 0
            val glesMajor = glesVersion shr 16
            val glesMinor = glesVersion and 0xFFFF

            Log.d(TAG, "Device OpenGL ES version: $glesMajor.$glesMinor (raw: 0x${glesVersion.toString(16)})")

            // OpenGL ES 3.1 = 0x00030001
            if (glesVersion < 0x00030001) {
                Log.d(TAG, "Vulkan not supported: OpenGL ES $glesMajor.$glesMinor < 3.1")
                return false
            }

            // Check for Vulkan 1.3 hardware version (required for proper MPV support)
            if (packageManager.hasSystemFeature(
                    PackageManager.FEATURE_VULKAN_HARDWARE_VERSION,
                    0x00403000 // Vulkan 1.3
                )) {
                Log.d(TAG, "Vulkan 1.3 supported ✓")
                return true
            }

            Log.d(TAG, "Vulkan not supported: Vulkan 1.3 not available")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error checking Vulkan support", e)
            return false
        }
    }
}
