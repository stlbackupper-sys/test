package com.example.buffettpicks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshScheduler.ensureScheduled(this)
        // A widget row tap passes the ticker as an intent extra named "ticker".
        val startTicker = intent.getStringExtra("ticker")
        setContent { MaterialTheme { App(startTicker) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(startTicker: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(PicksStore.load(ctx)) }
    var selected by remember { mutableStateOf(startTicker) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun doRefresh() {
        refreshing = true
        PicksRepository.refresh(ctx)
        data = PicksStore.load(ctx)
        refreshing = false
    }

    LaunchedEffect(Unit) { doRefresh() }
    BackHandler(enabled = selected != null) { selected = null }

    val current = data
    val pick = current?.picks?.firstOrNull { it.ticker == selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pick?.ticker ?: "Buffett-style Top 10") },
                navigationIcon = {
                    if (pick != null) TextButton(onClick = { selected = null }) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { scope.launch { doRefresh() } }) {
                        Text(if (refreshing) "Loading…" else "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            current == null -> Text("No data yet. Check your connection and tap Refresh.", Modifier.padding(padding).padding(16.dp))
            pick != null -> DetailScreen(pick, current, Modifier.padding(padding))
            else -> ListScreen(current, Modifier.padding(padding)) { selected = it.ticker }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(data: PicksData, modifier: Modifier, onPick: (Pick) -> Unit) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Banner(data) }
        items(data.picks, key = { it.ticker }) { p ->
            Card(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${p.rank}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.ticker, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(p.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(money(p.price), style = MaterialTheme.typography.titleSmall)
                        Text("MoS ${pct(p.marginOfSafetyPct)} · growth ${pct(p.growthBasePct)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Text(data.disclaimer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Banner(data: PicksData) {
    val colors = if (data.sample) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    else CardDefaults.cardColors()
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (data.sample) {
                Text("SAMPLE DATA", fontWeight = FontWeight.Bold)
                Text("Real picks haven't been downloaded yet. Check the PICKS_URL the app was built with.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("As of ${data.asOf} · generated ${data.generatedAt}", style = MaterialTheme.typography.bodySmall)
            }
            if (data.coverageNotes.isNotBlank()) {
                Text("Coverage: ${data.coverageNotes}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailScreen(p: Pick, data: PicksData, modifier: Modifier) {
    val uri = LocalUriHandler.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(p.name, style = MaterialTheme.typography.headlineSmall)
        Text("Rank #${p.rank} · price ${money(p.price)} (as of ${p.priceDate})")

        Section(
            "Valuation (discounted owner earnings)",
            "Intrinsic value per share: conservative ${money(p.ivLow)} · base ${money(p.ivBase)} · optimistic ${money(p.ivHigh)}\n" +
                "Margin of safety at base case: ${pct(p.marginOfSafetyPct)}"
        )
        Section(
            "Projected owner-earnings growth, 1-3 years",
            "Conservative ${pct(p.growthLowPct)} · base ${pct(p.growthBasePct)} · optimistic ${pct(p.growthHighPct)}"
        )
        Text(
            "Growth figures are analyst extrapolation, not Buffett's forecast.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic
        )
        Section("Buffett scorecard", p.scorecard)
        Section("Why it made the list", p.thesis)
        Section("Strongest argument against", p.risks)
        Section("Drop it if", p.dropIf)

        if (p.sources.isNotEmpty()) {
            Column {
                Text("Sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                p.sources.forEach { url ->
                    TextButton(onClick = { uri.openUri(url) }) { Text(url, maxLines = 1) }
                }
            }
        }
        Text(data.disclaimer, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, body: String) {
    if (body.isBlank()) return
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
