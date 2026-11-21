package com.plaintxtoffice.plainrss.data

import android.content.Context
import android.content.SharedPreferences
import com.plaintxtoffice.plainrss.model.ArticleOrder
import com.plaintxtoffice.plainrss.model.DateDisplay
import com.plaintxtoffice.plainrss.model.FeedOrder
import com.plaintxtoffice.plainrss.model.ReaderPreferences
import androidx.core.content.edit

/**
 * Persistence boundary for reading and updating non-sensitive reader display and refresh preferences.
 */
interface PreferencesRepository {
  fun load(): ReaderPreferences
  fun save(preferences: ReaderPreferences)
}

/**
 * Stores [ReaderPreferences] in app-private [SharedPreferences].
 */
class SharedPreferencesRepository(context: Context) : PreferencesRepository {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun load(): ReaderPreferences {
    val retention = prefs.getInt(KEY_RETENTION, ReaderPreferences.DEFAULT_RETENTION)
    val validRetention =
      if (retention in ReaderPreferences.RETENTION_OPTIONS) {
        retention
      } else {
        ReaderPreferences.DEFAULT_RETENTION
      }
    val refreshOnOpen = prefs.getBoolean(KEY_REFRESH_ON_OPEN, true)
    val articleOrder = ArticleOrder.fromStoredValue(prefs.getString(KEY_ARTICLE_ORDER, null))
    val feedOrder = FeedOrder.fromStoredValue(prefs.getString(KEY_FEED_ORDER, null))
    val dateDisplay = DateDisplay.fromStoredValue(prefs.getString(KEY_DATE_DISPLAY, null))
    val lastAutoRefreshMillis = prefs.getLong(KEY_LAST_AUTO_REFRESH, 0L)

    return ReaderPreferences(
      articleRetention = validRetention,
      refreshOnOpen = refreshOnOpen,
      articleOrder = articleOrder,
      feedOrder = feedOrder,
      dateDisplay = dateDisplay,
      lastAutoRefreshMillis = lastAutoRefreshMillis,
    )
  }

  override fun save(preferences: ReaderPreferences) {
    prefs
      .edit {
          putInt(KEY_RETENTION, preferences.articleRetention)
              .putBoolean(KEY_REFRESH_ON_OPEN, preferences.refreshOnOpen)
              .putString(KEY_ARTICLE_ORDER, preferences.articleOrder.name)
              .putString(KEY_FEED_ORDER, preferences.feedOrder.name)
              .putString(KEY_DATE_DISPLAY, preferences.dateDisplay.name)
              .putLong(KEY_LAST_AUTO_REFRESH, preferences.lastAutoRefreshMillis)
      }
  }

  companion object {
    private const val PREFS_NAME = "plain_rss_preferences"
    private const val KEY_RETENTION = "articleRetention"
    private const val KEY_REFRESH_ON_OPEN = "refreshOnOpen"
    private const val KEY_ARTICLE_ORDER = "articleOrder"
    private const val KEY_FEED_ORDER = "feedOrder"
    private const val KEY_DATE_DISPLAY = "dateDisplay"
    private const val KEY_LAST_AUTO_REFRESH = "lastAutoRefresh"
  }
}

/**
 * In-memory [PreferencesRepository] for unit tests and Compose previews.
 */
class InMemoryPreferencesRepository(
  initial: ReaderPreferences = ReaderPreferences(),
) : PreferencesRepository {
  private var current: ReaderPreferences = initial

  override fun load(): ReaderPreferences = current

  override fun save(preferences: ReaderPreferences) {
    current = preferences
  }
}
