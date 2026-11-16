package com.example.buffettpicks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TickerKey = ActionParameters.Key<String>("ticker")

class PicksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = PicksStore.load(context)
        val fetchedAt = PicksStore.fetchedAt(context)
        provideContent {
            GlanceTheme { WidgetBody(data, fetchedAt) }
        }
    }
}

@Composable
private fun WidgetBody(data: PicksData?, fetchedAt: Long) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (data?.sample == true) "Top 10 (SAMPLE DATA)" else "Today's Top 10",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "Refresh",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.primary),
                modifier = GlanceModifier
                    .padding(4.dp)
                    .clickable(actionRunCallback<RefreshAction>())
            )
        }

        if (data == null || data.picks.isEmpty()) {
            Text(
                text = "Open the app to load today's picks.",
                style = TextStyle(color = GlanceTheme.colors.onSurface)
            )
        } else {
            LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                items(data.picks) { p ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable(
                                actionStartActivity<MainActivity>(
                                    actionParametersOf(TickerKey to p.ticker)
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${p.rank}",
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            modifier = GlanceModifier.width(22.dp)
                        )
                        Text(
                            text = p.ticker,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onSurface
                            ),
                            modifier = GlanceModifier.width(60.dp)
                        )
                        Text(
                            text = p.name,
                            maxLines = 1,
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Text(
                            text = "MoS ${pct(p.marginOfSafetyPct)}",
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.primary)
                        )
                    }
                }
            }
            if (fetchedAt > 0L) {
                val stamp = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(fetchedAt))
                Text(
                    text = "Updated $stamp",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PicksRepository.refresh(context)
    }
}

class PicksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PicksWidget()
}
