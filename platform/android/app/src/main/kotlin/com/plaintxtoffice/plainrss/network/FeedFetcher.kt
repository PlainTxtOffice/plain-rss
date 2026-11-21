package com.plaintxtoffice.plainrss.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.plaintxtoffice.plainrss.domain.FeedDiscovery
import com.plaintxtoffice.plainrss.domain.ReaderUrl
import com.plaintxtoffice.plainrss.domain.RssFeedParser
import com.plaintxtoffice.plainrss.model.FeedException
import com.plaintxtoffice.plainrss.model.FeedFetchResult
import com.plaintxtoffice.plainrss.model.FetchedFeed
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownServiceException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

/**
 * Identifies the app to feed and image servers. Many hosts answer the default
 * `Dalvik/...` agent with HTTP 403. It names only the app, never the device or user.
 */
internal const val READER_USER_AGENT = "PlainRSS/1.0 (Android; +https://plaintxtoffice.com)"

private const val FEED_ACCEPT =
  "application/rss+xml, application/atom+xml, application/rdf+xml, " +
    "application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5"

/**
 * Abstraction for downloading and parsing RSS/Atom feeds.
 */
interface FeedFetcher {
  suspend fun fetch(
    url: String,
    etag: String? = null,
    lastModified: String? = null,
  ): FeedFetchResult

  suspend fun fetchNew(url: String): Pair<String, FetchedFeed>
}

/**
 * Ephemeral HTTP implementation of [FeedFetcher] that bypasses caches and cookies.
 */
class HttpFeedFetcher : FeedFetcher {
  override suspend fun fetch(
    url: String,
    etag: String?,
    lastModified: String?,
  ): FeedFetchResult =
    withContext(Dispatchers.IO) {
      val headers = buildMap {
        if (!etag.isNullOrEmpty()) put("If-None-Match", etag)
        if (!lastModified.isNullOrEmpty()) put("If-Modified-Since", lastModified)
      }
      val response = download(url, headers)
      if (response.statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
        return@withContext FeedFetchResult.NotModified
      }
      if (response.statusCode !in 200..299) {
        throw FeedException.ServerError(response.statusCode)
      }
      val parsed = RssFeedParser.parse(response.bytes)
      FeedFetchResult.Fetched(
        FetchedFeed(
          parsed = parsed,
          etag = response.etag,
          lastModified = response.lastModified,
        )
      )
    }

  override suspend fun fetchNew(url: String): Pair<String, FetchedFeed> =
    withContext(Dispatchers.IO) {
      val firstResponse = download(url, emptyMap())
      if (firstResponse.statusCode !in 200..299) {
        throw FeedException.ServerError(firstResponse.statusCode)
      }

      try {
        val parsed = RssFeedParser.parse(firstResponse.bytes)
        return@withContext url to FetchedFeed(
          parsed = parsed,
          etag = firstResponse.etag,
          lastModified = firstResponse.lastModified,
        )
      } catch (_: Exception) {
        val discovered =
          FeedDiscovery.feedUrl(firstResponse.bytes, firstResponse.finalUrl)
        if (discovered == null || discovered == url) {
          throw FeedException.NoFeedFound()
        }
        val secondResponse = download(discovered, emptyMap())
        if (secondResponse.statusCode !in 200..299) {
          throw FeedException.ServerError(secondResponse.statusCode)
        }
        val parsed = RssFeedParser.parse(secondResponse.bytes)
        discovered to FetchedFeed(
          parsed = parsed,
          etag = secondResponse.etag,
          lastModified = secondResponse.lastModified,
        )
      }
    }

  private class HttpResponse(
    val statusCode: Int,
    val bytes: ByteArray,
    val etag: String?,
    val lastModified: String?,
    val finalUrl: String,
  )

  private suspend fun download(
    urlString: String,
    headers: Map<String, String>,
  ): HttpResponse =
    withContext(Dispatchers.IO) {
      var currentUrl =
        ReaderUrl.allowed(urlString) ?: throw FeedException.InvalidResponse()
      var redirects = 0

      while (true) {
        ensureActive()
        val connection =
          try {
            (URL(currentUrl).openConnection() as? HttpURLConnection)
              ?: throw FeedException.InvalidResponse()
          } catch (e: UnknownServiceException) {
            throw FeedException.InsecureConnection()
          }
        try {
          connection.instanceFollowRedirects = false
          connection.useCaches = false
          connection.connectTimeout = TIMEOUT_MS
          connection.readTimeout = TIMEOUT_MS
          connection.setRequestProperty("Cache-Control", "no-cache, no-store")
          connection.setRequestProperty("Pragma", "no-cache")
          connection.setRequestProperty("User-Agent", READER_USER_AGENT)
          connection.setRequestProperty("Accept", FEED_ACCEPT)
          for ((k, v) in headers) {
            connection.setRequestProperty(k, v)
          }

          val statusCode =
            try {
              connection.responseCode
            } catch (e: UnknownServiceException) {
              throw FeedException.InsecureConnection()
            } catch (e: IOException) {
              if (e.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                throw FeedException.InsecureConnection()
              }
              throw e
            }

          if (statusCode in 300..399 && statusCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
            redirects++
            if (redirects > MAX_REDIRECTS) throw FeedException.InvalidResponse()
            val location = connection.getHeaderField("Location") ?: throw FeedException.InvalidResponse()
            currentUrl = ReaderUrl.allowed(location, currentUrl) ?: throw FeedException.InvalidResponse()
            continue
          }

          if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return@withContext HttpResponse(
              statusCode = statusCode,
              bytes = ByteArray(0),
              etag = connection.getHeaderField("ETag"),
              lastModified = connection.getHeaderField("Last-Modified"),
              finalUrl = currentUrl,
            )
          }

          if (statusCode !in 200..299) {
            return@withContext HttpResponse(
              statusCode = statusCode,
              bytes = ByteArray(0),
              etag = null,
              lastModified = null,
              finalUrl = currentUrl,
            )
          }

          val contentLength = connection.contentLengthLong
          if (contentLength > MAX_FEED_BYTES) {
            throw FeedException.TooLarge()
          }

          val output = ByteArrayOutputStream(
            if (contentLength in 1..MAX_FEED_BYTES) contentLength.toInt() else 16_384
          )
          connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
              ensureActive()
              val read = input.read(buffer)
              if (read < 0) break
              if (output.size() + read > MAX_FEED_BYTES) {
                throw FeedException.TooLarge()
              }
              output.write(buffer, 0, read)
            }
          }

          return@withContext HttpResponse(
            statusCode = statusCode,
            bytes = output.toByteArray(),
            etag = connection.getHeaderField("ETag"),
            lastModified = connection.getHeaderField("Last-Modified"),
            finalUrl = currentUrl,
          )
        } finally {
          connection.disconnect()
        }
      }
      @Suppress("UNREACHABLE_CODE")
      throw FeedException.InvalidResponse()
    }

  companion object {
    const val MAX_FEED_BYTES = 10_000_000L
    private const val TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5
  }
}

/**
 * Abstraction for downloading and sanitizing remote reader images in memory.
 */
interface ReaderImageFetcher {
  suspend fun fetchPng(url: String): ByteArray
}

/**
 * Downloads a remote image without cookies or disk caching, verifies its dimensions,
 * downsamples to at most 1024 px on its longest edge, and re-encodes as PNG in memory.
 */
class HttpReaderImageFetcher : ReaderImageFetcher {
  override suspend fun fetchPng(url: String): ByteArray =
    withContext(Dispatchers.IO) {
      var currentUrl = ReaderUrl.allowed(url) ?: throw IOException("Unsupported image URL")
      var redirects = 0

      var rawBytes: ByteArray?
      while (true) {
        ensureActive()
        val connection =
          (URL(currentUrl).openConnection() as? HttpURLConnection)
            ?: throw IOException("Invalid connection")
        try {
          connection.instanceFollowRedirects = false
          connection.useCaches = false
          connection.connectTimeout = 15_000
          connection.readTimeout = 20_000
          connection.setRequestProperty("Cache-Control", "no-cache, no-store")
          connection.setRequestProperty("User-Agent", READER_USER_AGENT)

          val code = connection.responseCode
          if (code in 300..399) {
            redirects++
            if (redirects > MAX_IMAGE_REDIRECTS) throw IOException("Too many redirects")
            val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
            currentUrl = ReaderUrl.allowed(location, currentUrl) ?: throw IOException("Unsafe redirect")
            continue
          }
          if (code !in 200..299) {
            throw IOException("HTTP $code")
          }
          val mimeType =
            connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: ""
          if (mimeType !in ALLOWED_IMAGE_MIME_TYPES) {
            throw IOException("Unsupported MIME type: $mimeType")
          }
          val expectedLength = connection.contentLengthLong
          if (expectedLength > MAX_IMAGE_BYTES) {
            throw IOException("Image exceeds maximum size")
          }

          val output = ByteArrayOutputStream()
          connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
              ensureActive()
              val read = input.read(buffer)
              if (read < 0) break
              if (output.size() + read >= MAX_IMAGE_BYTES) {
                throw IOException("Image exceeds maximum size")
              }
              output.write(buffer, 0, read)
            }
          }
          rawBytes = output.toByteArray()
          break
        } finally {
          connection.disconnect()
        }
      }
      val imageBytes = rawBytes ?: throw IOException("Empty image data")

      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
      val width = bounds.outWidth
      val height = bounds.outHeight
      if (
        width <= 0 ||
          height <= 0 ||
          width > MAX_DIMENSION ||
          height > MAX_DIMENSION ||
          width.toLong() * height.toLong() > MAX_PIXELS
      ) {
        throw IOException("Invalid image dimensions")
      }

      var sampleSize = 1
      while (width / sampleSize > MAX_THUMBNAIL_SIZE * 2 || height / sampleSize > MAX_THUMBNAIL_SIZE * 2) {
        sampleSize *= 2
      }

      val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      val decoded =
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
          ?: throw IOException("Could not decode bitmap")

      val scaled =
        try {
          val maxEdge = maxOf(decoded.width, decoded.height)
          if (maxEdge > MAX_THUMBNAIL_SIZE) {
            val ratio = MAX_THUMBNAIL_SIZE.toFloat() / maxEdge.toFloat()
            val targetWidth = maxOf(1, (decoded.width * ratio).toInt())
            val targetHeight = maxOf(1, (decoded.height * ratio).toInt())
            decoded.scale(targetWidth, targetHeight)
          } else {
            decoded
          }
        } finally {
          // Recycled below if scaled != decoded
        }

      try {
        val pngOut = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, pngOut)) {
          throw IOException("Failed to encode PNG")
        }
        pngOut.toByteArray()
      } finally {
        if (scaled !== decoded) {
          scaled.recycle()
        }
        decoded.recycle()
      }
    }

  companion object {
    private val ALLOWED_IMAGE_MIME_TYPES = setOf(
      "image/png",
      "image/jpeg",
      "image/gif",
      "image/webp",
    )
    private const val MAX_IMAGE_REDIRECTS = 3
    private const val MAX_IMAGE_BYTES = 5_000_000
    private const val MAX_DIMENSION = 8_000
    private const val MAX_PIXELS = 16_000_000L
    private const val MAX_THUMBNAIL_SIZE = 1024
  }
}
