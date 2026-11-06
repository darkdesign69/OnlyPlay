package app.onlyplay

import android.app.Application
import app.onlyplay.database.repository.VideoMetadataCacheRepository
import app.onlyplay.di.DatabaseModule
import app.onlyplay.di.FileManagerModule
import app.onlyplay.di.PreferencesModule
import app.onlyplay.presentation.crash.CrashActivity
import app.onlyplay.presentation.crash.GlobalExceptionHandler
import app.onlyplay.utils.media.MediaLibraryEvents
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
class App : Application() {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val metadataCache: VideoMetadataCacheRepository by inject()

  override fun onCreate() {
    super.onCreate()

    // First launch of a fresh install: apply the bundled default settings
    app.onlyplay.preferences.DefaultSettings.applyOnce(this)

    // Initialize Koin
    startKoin {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
        app.onlyplay.di.domainModule,
      )
    }

    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))

    FastThumbnails.initialize(this)

    // Perform cache maintenance on app startup (non-blocking)
    applicationScope.launch {
      runCatching {
        metadataCache.performMaintenance()
      }
    }
    
    // The full-storage media scan that used to run at every launch was removed: it kept the phone busy
    // right when the app opens and forced the folder list to reload. Pull down to refresh instead.
  }
  
  /**
   * Trigger a media scan on app launch to ensure MediaStore is up-to-date
   * This helps detect videos added by external apps while the app was closed
   */
  private fun triggerMediaScanOnLaunch() {
    try {
      val externalStorage = android.os.Environment.getExternalStorageDirectory()
      
      android.media.MediaScannerConnection.scanFile(
        this,
        arrayOf(externalStorage.absolutePath),
        null, // Let MediaScanner detect all media types
      ) { path, uri ->
        android.util.Log.d("App", "Launch media scan completed for: $path")
        // Notify the app that media library may have changed
        MediaLibraryEvents.notifyChanged()
      }
      
      android.util.Log.d("App", "Triggered media scan on app launch")
    } catch (e: Exception) {
      android.util.Log.e("App", "Failed to trigger media scan on launch", e)
    }
  }
}
