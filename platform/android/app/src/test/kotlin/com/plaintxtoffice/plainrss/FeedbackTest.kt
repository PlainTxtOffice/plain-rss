package com.plaintxtoffice.plainrss

import com.plaintxtoffice.plainrss.ui.FEEDBACK_ADDRESS
import com.plaintxtoffice.plainrss.ui.feedbackEmailBody
import com.plaintxtoffice.plainrss.ui.formatDeviceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
  private val summary = formatDeviceSummary(
    appName = "Plain RSS",
    versionName = "1.0.0",
    versionCode = 1L,
    androidRelease = "16",
    sdkInt = 36,
    manufacturer = "Google",
    model = "Pixel 9",
  )

  @Test
  fun formatDeviceSummary_listsAppAndroidAndDevice() {
    assertEquals("Plain RSS 1.0.0 (1)\nAndroid 16 (API 36)\nGoogle Pixel 9", summary)
  }

  @Test
  fun feedbackEmailBody_leavesRoomToWriteAndEndsWithDeviceSummary() {
    val body = feedbackEmailBody(summary)

    assertTrue(body.startsWith("\n\n"))
    assertTrue(body.endsWith(summary))
    assertEquals(summary, body.trim())
  }

  @Test
  fun feedbackAddress_isSupportMailbox() {
    assertEquals("feedback@plaintxtoffice.com", FEEDBACK_ADDRESS)
  }
}
