package com.plaintxtoffice.plainrss.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.plaintxtoffice.plainrss.R

/** Address that receives bug reports and feature requests. */
const val FEEDBACK_ADDRESS = "feedback@plaintxtoffice.com"

/** Contact page that receives bug reports, feature requests, and privacy questions. */
const val CONTACT_URL = "https://plaintxtoffice.com/contact/"

/** Kind of feedback a drafted email is about. */
enum class FeedbackKind {
  BUG_REPORT,
  FEATURE_REQUEST,
}

/**
 * Opens the user's email app with a drafted message of the given [kind] to [FEEDBACK_ADDRESS].
 *
 * The draft carries only the subject and the device summary. Feed addresses, article content and
 * the library are never added. Nothing is sent unless the user sends it. Returns false when no
 * email app can take the draft.
 */
fun openFeedbackEmail(context: Context, kind: FeedbackKind): Boolean {
  val kindLabel = context.getString(
    when (kind) {
      FeedbackKind.BUG_REPORT -> R.string.feedback_kind_bug
      FeedbackKind.FEATURE_REQUEST -> R.string.feedback_kind_feature
    }
  )
  val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
    .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_ADDRESS))
    .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject, kindLabel))
    .putExtra(Intent.EXTRA_TEXT, feedbackEmailBody(deviceSummary(context)))
  return start(context, intent)
}

/** Opens [CONTACT_URL] in the browser. Returns false when no browser is available. */
fun openContactForm(context: Context): Boolean =
  start(context, Intent(Intent.ACTION_VIEW, CONTACT_URL.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))

/** App and device facts that help reproduce a report. Never includes feeds or articles. */
fun deviceSummary(context: Context): String {
  val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
  } else {
    context.packageManager.getPackageInfo(context.packageName, 0)
  }
  return formatDeviceSummary(
    appName = context.getString(R.string.app_name),
    versionName = info.versionName.orEmpty(),
    versionCode = PackageInfoCompat.getLongVersionCode(info),
    androidRelease = Build.VERSION.RELEASE.orEmpty(),
    sdkInt = Build.VERSION.SDK_INT,
    manufacturer = Build.MANUFACTURER.orEmpty(),
    model = Build.MODEL.orEmpty(),
  )
}

/**
 * Formats the device summary as three lines: app version name and code, Android release and API
 * level, then manufacturer and model.
 */
fun formatDeviceSummary(
  appName: String,
  versionName: String,
  versionCode: Long,
  androidRelease: String,
  sdkInt: Int,
  manufacturer: String,
  model: String,
): String = buildString {
  append(appName).append(' ').append(versionName).append(" (").append(versionCode).append(")\n")
  append("Android ").append(androidRelease).append(" (API ").append(sdkInt).append(")\n")
  append(manufacturer).append(' ').append(model)
}

/** Draft body: blank lines for the user's message, ending with [deviceSummary]. */
fun feedbackEmailBody(deviceSummary: String): String = "\n\n" + deviceSummary

private fun start(context: Context, intent: Intent): Boolean = try {
  context.startActivity(intent)
  true
} catch (_: ActivityNotFoundException) {
  false
}
