package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SpeedTestPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    COMPLETED,
    ERROR
}

data class SpeedTestProgress(
    val phase: SpeedTestPhase,
    val progressFraction: Float = 0f,
    val currentMbps: Double = 0.0,
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val serverHost: String = "",
    val errorMessage: String? = null
)

class SpeedTestEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    fun runSpeedTest(
        downloadUrl: String,
        uploadUrl: String
    ): Flow<SpeedTestProgress> = flow {
        val cleanDownload = downloadUrl.trim()
        val cleanUpload = uploadUrl.trim()
        if (cleanDownload.isBlank() || !cleanDownload.startsWith("https://", ignoreCase = true)) {
            emit(
                SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    errorMessage = "A valid HTTPS download test endpoint URL must be configured in Settings."
                )
            )
            return@flow
        }

        val host = try {
            URI(cleanDownload).host ?: cleanDownload
        } catch (_: Exception) {
            cleanDownload
        }

        emit(SpeedTestProgress(phase = SpeedTestPhase.PING, progressFraction = 0.05f, serverHost = host))

        // Phase 1: Latency & Jitter (5 HTTPS HEAD/tiny GET probes)
        val pingSamples = mutableListOf<Double>()
        val probeUrl = cleanDownload.substringBefore("?")
        for (i in 0 until 5) {
            currentCoroutineContext().ensureActive()
            val req = Request.Builder()
                .url(probeUrl)
                .head()
                .header("Cache-Control", "no-store")
                .build()
            val startNs = System.nanoTime()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code in 200..499) {
                        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                        pingSamples.add(elapsedMs)
                    }
                }
            } catch (e: Exception) {
                if (i == 0 && pingSamples.isEmpty()) {
                    emit(
                        SpeedTestProgress(
                            phase = SpeedTestPhase.ERROR,
                            serverHost = host,
                            errorMessage = "Unable to reach test server ($host): ${e.localizedMessage ?: "Network unreachable"}"
                        )
                    )
                    return@flow
                }
            }
            emit(
                SpeedTestProgress(
                    phase = SpeedTestPhase.PING,
                    progressFraction = 0.05f + (i + 1) * 0.03f,
                    pingMs = pingSamples.averageOrZero(),
                    jitterMs = calculateJitter(pingSamples),
                    serverHost = host
                )
            )
        }

        if (pingSamples.isEmpty()) {
            emit(
                SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    serverHost = host,
                    errorMessage = "Speed test server did not respond. Check your internet connection or endpoint configuration."
                )
            )
            return@flow
        }

        val finalPingMs = pingSamples.sorted()[pingSamples.size / 2]
        val finalJitterMs = calculateJitter(pingSamples)

        // Phase 2: Download speed measurement
        var finalDownloadMbps = 0.0
        try {
            val dlReq = Request.Builder()
                .url(cleanDownload)
                .get()
                .header("Cache-Control", "no-store")
                .build()

            val dlStartNs = System.nanoTime()
            client.newCall(dlReq).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(
                        SpeedTestProgress(
                            phase = SpeedTestPhase.ERROR,
                            pingMs = finalPingMs,
                            jitterMs = finalJitterMs,
                            serverHost = host,
                            errorMessage = "Download test endpoint returned HTTP ${response.code}"
                        )
                    )
                    return@flow
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                val expectedBytes = body.contentLength().takeIf { it > 0 } ?: 8_000_000L
                val stream = body.byteStream()
                val buffer = ByteArray(16 * 1024)
                var totalBytes = 0L
                var lastEmitNs = dlStartNs
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    val nowNs = System.nanoTime()
                    val elapsedSec = (nowNs - dlStartNs) / 1_000_000_000.0
                    if (elapsedSec > 0.05 && (nowNs - lastEmitNs) > 120_000_000L) {
                        val mbps = (totalBytes * 8.0) / (elapsedSec * 1_000_000.0)
                        val dlProg = (totalBytes.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 1f)
                        emit(
                            SpeedTestProgress(
                                phase = SpeedTestPhase.DOWNLOAD,
                                progressFraction = 0.20f + dlProg * 0.40f,
                                currentMbps = mbps,
                                pingMs = finalPingMs,
                                jitterMs = finalJitterMs,
                                downloadMbps = mbps,
                                serverHost = host
                            )
                        )
                        lastEmitNs = nowNs
                    }
                    // Cap single test run at ~10s to remain responsive on slow links
                    if (elapsedSec > 10.0) break
                }
                val totalSec = ((System.nanoTime() - dlStartNs) / 1_000_000_000.0).coerceAtLeast(0.001)
                finalDownloadMbps = (totalBytes * 8.0) / (totalSec * 1_000_000.0)
            }
        } catch (e: Exception) {
            emit(
                SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    pingMs = finalPingMs,
                    jitterMs = finalJitterMs,
                    serverHost = host,
                    errorMessage = "Download test failed: ${e.localizedMessage ?: "Connection interrupted"}"
                )
            )
            return@flow
        }

        // Phase 3: Upload speed measurement (only if upload endpoint configured)
        var finalUploadMbps = 0.0
        if (cleanUpload.startsWith("https://", ignoreCase = true)) {
            try {
                val uploadTotalBytes = 2_000_000L
                var uploadedBytes = 0L
                val ulStartNs = System.nanoTime()
                var lastEmitNs = ulStartNs

                val requestBody = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = uploadTotalBytes
                    override fun writeTo(sink: BufferedSink) {
                        val chunk = ByteArray(16 * 1024)
                        var remaining = uploadTotalBytes
                        while (remaining > 0) {
                            val toWrite = minOf(chunk.size.toLong(), remaining).toInt()
                            sink.write(chunk, 0, toWrite)
                            remaining -= toWrite
                            uploadedBytes += toWrite
                        }
                        sink.flush()
                    }
                }

                emit(
                    SpeedTestProgress(
                        phase = SpeedTestPhase.UPLOAD,
                        progressFraction = 0.65f,
                        pingMs = finalPingMs,
                        jitterMs = finalJitterMs,
                        downloadMbps = finalDownloadMbps,
                        serverHost = host
                    )
                )

                val ulReq = Request.Builder()
                    .url(cleanUpload)
                    .post(requestBody)
                    .header("Cache-Control", "no-store")
                    .build()

                client.newCall(ulReq).execute().use { response ->
                    val totalSec = ((System.nanoTime() - ulStartNs) / 1_000_000_000.0).coerceAtLeast(0.001)
                    if (response.isSuccessful) {
                        finalUploadMbps = (uploadedBytes * 8.0) / (totalSec * 1_000_000.0)
                    }
                }
            } catch (_: Exception) {
                // If upload endpoint fails while download succeeded, report 0 upload rather than failing whole test
                finalUploadMbps = 0.0
            }
        }

        emit(
            SpeedTestProgress(
                phase = SpeedTestPhase.COMPLETED,
                progressFraction = 1f,
                currentMbps = finalDownloadMbps,
                pingMs = (finalPingMs * 10.0).roundToInt() / 10.0,
                jitterMs = (finalJitterMs * 10.0).roundToInt() / 10.0,
                downloadMbps = (finalDownloadMbps * 100.0).roundToInt() / 100.0,
                uploadMbps = (finalUploadMbps * 100.0).roundToInt() / 100.0,
                serverHost = host
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else average()

    private fun calculateJitter(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        var sumDiff = 0.0
        for (i in 1 until samples.size) {
            sumDiff += abs(samples[i] - samples[i - 1])
        }
        return sumDiff / (samples.size - 1)
    }
}
