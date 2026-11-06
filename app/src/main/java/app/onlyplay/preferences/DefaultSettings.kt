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
      context.assets.open("default_settings.xml").use { input ->
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
          if (event == XmlPullParser.START_TAG && parser.name == "preference") {
            val key = parser.getAttributeValue(null, "key")
            val type = parser.getAttributeValue(null, "type")
            val value = parser.getAttributeValue(null, "value")
            if (key != null && type != null && value != null) {
              when (type) {
                "string" -> editor.putString(key, value)
                "int" -> value.toIntOrNull()?.let { editor.putInt(key, it) }
                "long" -> value.toLongOrNull()?.let { editor.putLong(key, it) }
                "float" -> value.toFloatOrNull()?.let { editor.putFloat(key, it) }
                "boolean" -> editor.putBoolean(key, value.toBoolean())
              }
            }
          }
          event = parser.next()
        }
      }
      editor.putBoolean(DONE_KEY, true)
      editor.commit()
    } catch (e: Exception) {
      // Defaults are a nicety: if anything goes wrong the app just starts with its normal settings
    }
  }
}
