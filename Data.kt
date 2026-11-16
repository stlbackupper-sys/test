package com.example.buffettpicks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Pick(
    val rank: Int,
    val ticker: String,
    val name: String,
    val price: Double,
    val priceDate: String,
    val ivLow: Double,
    val ivBase: Double,
    val ivHigh: Double,
    val marginOfSafetyPct: Double,
    val growthLowPct: Double,
    val growthBasePct: Double,
    val growthHighPct: Double,
    val scorecard: String,
    val thesis: String,
    val risks: String,
    val dropIf: String,
    val sources: List<String>,
)

data class PicksData(
    val generatedAt: String,
    val asOf: String,
    val disclaimer: String,
    val coverageNotes: String,
    val sample: Boolean,
    val picks: List<Pick>,
)

object PicksParser {
    fun parse(json: String): PicksData {
        val root = JSONObject(json)
        val arr = root.getJSONArray("picks")
        val picks = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Pick(
                rank = o.optInt("rank", i + 1),
                ticker = o.getString("ticker"),
                name = o.optString("name", ""),
                price = o.optDouble("price", Double.NaN),
                priceDate = o.optString("price_date", ""),
                ivLow = o.optDouble("intrinsic_low", Double.NaN),
                ivBase = o.optDouble("intrinsic_base", Double.NaN),
                ivHigh = o.optDouble("intrinsic_high", Double.NaN),
                marginOfSafetyPct = o.optDouble("margin_of_safety_pct", Double.NaN),
                growthLowPct = o.optDouble("growth_low_pct", Double.NaN),
                growthBasePct = o.optDouble("growth_base_pct", Double.NaN),
                growthHighPct = o.optDouble("growth_high_pct", Double.NaN),
                scorecard = o.optString("scorecard", ""),
                thesis = o.optString("thesis", ""),
                risks = o.optString("risks", ""),
                dropIf = o.optString("drop_if", ""),
                sources = o.optJSONArray("sources").toStringList(),
            )
        }.sortedBy { it.rank }
        return PicksData(
            generatedAt = root.optString("generated_at", ""),
            asOf = root.optString("as_of", ""),
            disclaimer = root.optString(
                "disclaimer",
                "Educational screening output, not personalized financial advice."
            ),
            coverageNotes = root.optString("coverage_notes", ""),
            sample = root.optBoolean("sample", false),
            picks = picks,
        )
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }
}

/** Keeps the last downloaded picks.json so the widget and app work offline. */
object PicksStore {
    private const val PREFS = "picks_store"
    private const val KEY_JSON = "json"
    private const val KEY_FETCHED = "fetched_at"

    fun save(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, json)
            .putLong(KEY_FETCHED, System.currentTimeMillis())
            .apply()
    }

    /** Last downloaded picks, or the bundled sample data if nothing has been downloaded yet. */
    fun load(ctx: Context): PicksData? {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            ?: runCatching {
                ctx.assets.open("sample_picks.json").bufferedReader().use { it.readText() }
            }.getOrNull()
            ?: return null
        return runCatching { PicksParser.parse(json) }.getOrNull()
    }

    fun fetchedAt(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_FETCHED, 0L)
}

fun money(v: Double): String = if (v.isNaN()) "n/a" else String.format("$%.2f", v)
fun pct(v: Double): String = if (v.isNaN()) "n/a" else String.format("%.0f%%", v)
