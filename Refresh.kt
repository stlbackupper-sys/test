package com.example.buffettpicks

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object PicksRepository {
    /** Downloads picks.json, validates it, stores it, and redraws the widget. Returns true on success. */
    suspend fun refresh(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(BuildConfig.PICKS_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val parsed = PicksParser.parse(body)
            if (parsed.picks.isEmpty()) return@withContext false
            PicksStore.save(ctx, body)
            PicksWidget().updateAll(ctx)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Runs a refresh at 8:30 AM New York time on weekdays, i.e. one hour before the NYSE opens at 9:30 AM.
 * Uses a chain of one-time jobs (each schedules the next) because periodic work can't target a clock time.
 * Android may delay the job a few minutes in battery-saver/Doze; it will not run early.
 * Market holidays are not skipped: the job just downloads the same file again.
 */
object RefreshScheduler {
    private const val WORK_NAME = "daily_picks_refresh"
    private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    private val RUN_TIME: LocalTime = LocalTime.of(8, 30)

    fun millisUntilNextRun(now: ZonedDateTime = ZonedDateTime.now(NEW_YORK)): Long {
        var next = now.with(RUN_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis()
    }

    private fun enqueue(ctx: Context, delayMs: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<PicksWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /** Called on app start: schedules the next run only if none is pending. */
    fun ensureScheduled(ctx: Context) =
        enqueue(ctx, millisUntilNextRun(), ExistingWorkPolicy.KEEP)

    /** Called from the worker itself. After a failed download, retry in an hour instead of waiting a day. */
    fun scheduleNext(ctx: Context, afterFailure: Boolean) {
        val daily = millisUntilNextRun()
        val delay = if (afterFailure) minOf(daily, TimeUnit.HOURS.toMillis(1)) else daily
        enqueue(ctx, delay, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }
}

class PicksWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ok = PicksRepository.refresh(applicationContext)
        RefreshScheduler.scheduleNext(applicationContext, afterFailure = !ok)
        return Result.success()
    }
}
