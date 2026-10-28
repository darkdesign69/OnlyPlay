package app.marlboroadvance.mpvex.utils.storage

import android.util.Log
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import java.io.File

/**
 * File Filter Utilities
 * Handles directory and file exclusion logic (hidden files, .nomedia, junk folders).
 */
object FileFilterUtils {
  private const val TAG = "FileFilterUtils"

  /** Show files and folders whose name starts with a dot. */
  @Volatile
  var showHidden: Boolean = false

  /** Skip folders that contain a .nomedia file. */
  @Volatile
  var recognizeNoMedia: Boolean = true

  fun configure(showHidden: Boolean, recognizeNoMedia: Boolean) {
    this.showHidden = showHidden
    this.recognizeNoMedia = recognizeNoMedia
  }

  /** Reads the two switches from the app settings. */
  fun syncFromPreferences() {
    runCatching {
      val prefs = org.koin.core.context.GlobalContext.get().get<BrowserPreferences>()
      configure(prefs.showHiddenFiles.get(), prefs.recognizeNoMedia.get())
    }
  }

  // Folders to skip during scanning (system/cache folders)
  private val SKIP_FOLDERS = setOf(
    // System & OS Junk
    "android", "data", "obb", "system", "lost.dir", ".android_secure", "android_secure",

    // Hidden & Temp Files
    ".thumbnails", "thumbnails", "thumbs", ".thumbs",
    ".cache", "cache", "temp", "tmp", ".temp", ".tmp",

    // Trash & Recycle Bins
    ".trash", "trash", ".trashbin", ".trashed", "recycle", "recycler",

    // App Clutters
    "log", "logs", "backup", "backups",
    "stickers", "whatsapp stickers", "telegram stickers"
  )

  /**
   * Checks if a folder contains a .nomedia file
   */
  fun hasNoMediaFile(folder: File): Boolean {
    if (!folder.isDirectory || !folder.canRead()) {
      return false
    }

    return try {
      val noMediaFile = File(folder, ".nomedia")
      noMediaFile.exists()
    } catch (e: Exception) {
      Log.w(TAG, "Error checking for .nomedia file in: ${folder.absolutePath}", e)
      false
    }
  }

  /**
   * Checks if a folder should be skipped during scanning
   */
  fun shouldSkipFolder(folder: File): Boolean {
    val name = folder.name.lowercase()
    if (name.startsWith(".")) {
      if (!showHidden) return true
    } else if (SKIP_FOLDERS.contains(name)) {
      return true
    }
    return recognizeNoMedia && hasNoMediaFile(folder)
  }

  /**
   * Checks if a file should be skipped during file listing
   */
  fun shouldSkipFile(file: File): Boolean {
    return !showHidden && file.name.startsWith(".")
  }
}
