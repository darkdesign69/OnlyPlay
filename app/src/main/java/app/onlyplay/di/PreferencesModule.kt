package app.onlyplay.di

import app.onlyplay.database.OnlyPlayDatabase
import app.onlyplay.preferences.AdvancedPreferences
import app.onlyplay.preferences.AppearancePreferences
import app.onlyplay.preferences.AudioPreferences
import app.onlyplay.preferences.BrowserPreferences
import app.onlyplay.preferences.DecoderPreferences
import app.onlyplay.preferences.FoldersPreferences
import app.onlyplay.preferences.GesturePreferences
import app.onlyplay.preferences.PlayerPreferences
import app.onlyplay.preferences.SettingsManager
import app.onlyplay.preferences.SubtitlesPreferences
import app.onlyplay.preferences.preference.AndroidPreferenceStore
import app.onlyplay.preferences.preference.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val PreferencesModule =
  module {
    single { AndroidPreferenceStore(androidContext()) }.bind(PreferenceStore::class)

    single { AppearancePreferences(get()) }
    singleOf(::PlayerPreferences)
    singleOf(::GesturePreferences)
    singleOf(::DecoderPreferences)
    singleOf(::SubtitlesPreferences)
    singleOf(::AudioPreferences)
    singleOf(::AdvancedPreferences)
    single { BrowserPreferences(get(), androidContext()) }
    singleOf(::FoldersPreferences)
    singleOf(::SettingsManager)
  }
