package app.onlyplay.preferences

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * Applies the settings bundled in assets/default_settings.xml the first time the app is opened on a
 * fresh install. People can change every one of them afterwards, and an app that already has saved
 * settings (an update) is never touched.
 */
object DefaultSettings {
  private const val DONE_KEY = "bundled_defaults_applied"

  fun applyOnce(context: Context) {
    try {
      val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
      if (prefs.contains(DONE_KEY) || prefs.all.isNotEmpty()) {
        if (!prefs.contains(DONE_KEY)) prefs.edit().putBoolean(DONE_KEY, true).commit()
        return
      }
      val editor = prefs.edit()
      val storeEditors = mutableMapOf<String, android.content.SharedPreferences.Editor>()
      context.assets.open("default_settings.xml").use { input ->
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
          if (event == XmlPullParser.START_TAG && parser.name == "preference") {
            val fullKey = parser.getAttributeValue(null, "key")
            val type = parser.getAttributeValue(null, "type")
            val value = parser.getAttributeValue(null, "value")
            if (fullKey != null && type != null && value != null) {
              // "store::key" entries are music settings and go to their own store
              val store = fullKey.substringBefore(SettingsManager.STORE_SEPARATOR, "")
              val isMusic = store in SettingsManager.MUSIC_STORES
              val key = if (isMusic) fullKey.substringAfter(SettingsManager.STORE_SEPARATOR) else fullKey
              val e =
                if (isMusic) {
                  storeEditors.getOrPut(store) { context.getSharedPreferences(store, Context.MODE_PRIVATE).edit() }
                } else {
                  editor
                }
              when (type) {
                "string" -> e.putString(key, value)
                "int" -> value.toIntOrNull()?.let { e.putInt(key, it) }
                "long" -> value.toLongOrNull()?.let { e.putLong(key, it) }
                "float" -> value.toFloatOrNull()?.let { e.putFloat(key, it) }
                "boolean" -> e.putBoolean(key, value.toBoolean())
              }
            }
          }
          event = parser.next()
        }
      }
      storeEditors.values.forEach { it.commit() }
      editor.putBoolean(DONE_KEY, true)
      editor.commit()
    } catch (e: Exception) {
      // Defaults are a nicety: if anything goes wrong the app just starts with its normal settings
    }
  }
}
