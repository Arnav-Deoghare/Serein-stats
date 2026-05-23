package com.serein.stats.ui

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ── Colors ────────────────────────────────────────────────────
val BG         = Color(0xFF1A1612)
val SURFACE    = Color(0xFF232018)
val SURFACE2   = Color(0xFF2C2820)
val TAN        = Color(0xFFB89A6F)
val TAN_DIM    = Color(0xFF7A6548)
val TAN_FAINT  = Color(0xFF3A3025)
val TEXT       = Color(0xFFEDE4D6)
val TEXT_MUTED = Color(0xFF6B5F50)
val DIVIDER    = Color(0xFF252118)
val RED_SOFT   = Color(0xFFB87070)
val GREEN_SOFT = Color(0xFF7A9B76)

// ── System packages to always filter out ─────────────────────
val SYSTEM_BLACKLIST = setOf(
    "com.zen.launcher",
    "com.android.launcher",
    "com.sec.android.app.launcher",          // Samsung One UI home
    "com.samsung.android.app.cocktailbarservice",
    "com.android.systemui",
    "com.samsung.android.systemui",
    "com.google.android.gms",
    "com.google.android.gsf",
    "com.android.settings",
    "com.samsung.android.settings",
    "android",
    "com.android.phone",
    "com.samsung.android.incallui",
    "com.android.inputmethod.latin",
    "com.samsung.android.honeyboard",
    "com.serein.stats",
    "com.android.vending",
    "com.google.android.packageinstaller",
    "com.android.packageinstaller",
    "com.samsung.android.app.aodservice",
    "com.samsung.android.bixby.agent",
    "com.samsung.android.app.galaxyfinder",
    "com.samsung.android.app.smartcapture",
    "com.samsung.android.app.routines",
)

fun shouldFilter(pkg: String): Boolean =
    pkg in SYSTEM_BLACKLIST ||
    pkg.startsWith("com.android.") ||
    pkg.startsWith("com.samsung.android.server") ||
    pkg.startsWith("com.google.android.server") ||
    pkg.startsWith("android.process.")

// ── Categories ────────────────────────────────────────────────
val CATEGORIES = linkedMapOf(
    "Social"       to listOf("com.whatsapp","com.instagram.android","com.twitter.android",
                             "com.facebook.katana","com.snapchat.android","com.telegram.messenger",
                             "org.telegram.messenger","com.discord","com.linkedin.android",
                             "com.pinterest","com.reddit.frontpage","com.tumblr"),
    "Video"        to listOf("com.google.android.youtube","com.netflix.mediaclient",
                             "com.amazon.avod.thirdpartyclient","com.disney.disneyplus",
                             "in.startv.hotstar","com.jio.cinema"),
    "Music"        to listOf("com.spotify.music","com.google.android.music",
                             "com.soundcloud.android","com.apple.android.music",
                             "com.gaana","com.jio.media.jiomusic"),
    "Reading"      to listOf("com.amazon.kindle","com.google.android.apps.books",
                             "com.medium.reader","com.getpocket.queryandroid",
                             "com.goodreads.android","com.instapaper.android"),
    "Productivity" to listOf("com.google.android.gm","com.microsoft.teams","com.slack",
                             "com.notion.id","com.todoist","com.google.android.calendar",
                             "com.google.android.keep","com.microsoft.office.word",
                             "com.microsoft.office.excel","com.google.android.apps.docs"),
    "Browser"      to listOf("com.android.chrome","org.mozilla.firefox","com.brave.browser",
                             "com.opera.browser","com.microsoft.emmx"),
    "Maps"         to listOf("com.google.android.apps.maps","com.waze","com.here.app.maps"),
    "Games"        to listOf("com.supercell.clashofclans","com.kiloo.subwaysurf",
                             "com.king.candycrushsaga"),
    "Finance"      to listOf("com.phonepe.app","net.one97.paytm","com.google.android.apps.nbu.paisa.user",
                             "com.snapwork.hdfc","com.csam.icici.bank.imobile"),
    "Health"       to listOf("com.google.android.apps.fitness","com.samsung.android.shealth",
                             "com.headspace.android","com.calm.android"),
)

fun getCategory(pkg: String): String {
    CATEGORIES.forEach { (cat, pkgs) -> if (pkg in pkgs) return cat }
    return "Other"
}

// ── Models ────────────────────────────────────────────────────
data class AppUsage(
    val packageName: String,
    val label: String,
    val todayMinutes: Long,
    val weekMinutes: Long,
    val monthMinutes: Long,
    val lifetimeMinutes: Long,
    val category: String,
    val unlockCount: Int,
    val sessionCount: Int,
    val avgSessionMinutes: Long,
    val hourlyMinutes: IntArray,
    val longestSessionMinutes: Long,
    val firstSeen: Long,
)

data class DaySummary(val label: String, val shortLabel: String, val minutes: Long, val unlocks: Int)

// ── Usage fetcher ─────────────────────────────────────────────
object UsageHelper {

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), ctx.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun openPermissionSettings(ctx: Context) =
        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    private fun startOfDay(offsetDays: Int = 0): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            if (offsetDays != 0) add(Calendar.DAY_OF_YEAR, offsetDays)
        }
        return c.timeInMillis
    }

    suspend fun getUsageData(ctx: Context): List<AppUsage> = withContext(Dispatchers.IO) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm  = ctx.packageManager
        val now = System.currentTimeMillis()

        val todayStart  = startOfDay()
        val weekStart   = startOfDay(-6)
        val monthStart  = startOfDay(-29)

        // Fetch stat buckets
        val todayStats   = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, todayStart, now)
            ?.filter { !shouldFilter(it.packageName) }
            ?.associateBy { it.packageName } ?: emptyMap()

        val weekStats    = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, weekStart, now)
            ?.filter { !shouldFilter(it.packageName) }
            ?.associateBy { it.packageName } ?: emptyMap()

        val monthStats   = usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, monthStart, now)
            ?.filter { !shouldFilter(it.packageName) }
            ?.associateBy { it.packageName } ?: emptyMap()

        // Lifetime — query max range Android allows (~4 years back)
        val lifetimeStart = startOfDay(-1460)
        val lifetimeStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, lifetimeStart, now)
            ?.filter { !shouldFilter(it.packageName) }
            ?.associateBy { it.packageName } ?: emptyMap()

        // Event-level data for today
        val hourly        = mutableMapOf<String, IntArray>()
        val unlocks       = mutableMapOf<String, Int>()
        val sessions      = mutableMapOf<String, MutableList<Long>>()
        val sessionStarts = mutableMapOf<String, Long>()

        val events = usm.queryEvents(todayStart, now)
        val ev     = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            if (shouldFilter(pkg)) continue

            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    sessionStarts[pkg] = ev.timeStamp
                    unlocks[pkg]       = (unlocks[pkg] ?: 0) + 1
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = sessionStarts.remove(pkg) ?: continue
                    val dur   = ev.timeStamp - start
                    if (dur < 2000) continue // skip blips

                    sessions.getOrPut(pkg) { mutableListOf() }.add(dur)

                    val buckets = hourly.getOrPut(pkg) { IntArray(24) }
                    var t = start
                    while (t < ev.timeStamp) {
                        val hour = Calendar.getInstance().also { c -> c.timeInMillis = t }
                            .get(Calendar.HOUR_OF_DAY)
                        val nextHour = Calendar.getInstance().also { c ->
                            c.timeInMillis = t
                            c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
                            c.add(Calendar.HOUR_OF_DAY, 1)
                        }.timeInMillis
                        val segEnd = minOf(nextHour, ev.timeStamp)
                        buckets[hour] += ((segEnd - t) / 60_000).toInt()
                        t = segEnd
                    }
                }
            }
        }

        todayStats.values
            .filter { it.totalTimeInForeground > 30_000 }
            .mapNotNull { stat ->
                val pkg = stat.packageName
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) { pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() } }

                val appSessions    = sessions[pkg] ?: emptyList()
                val sessionCount   = appSessions.size.coerceAtLeast(unlocks[pkg] ?: 1)
                val todayMin       = stat.totalTimeInForeground / 60_000
                val avgSession     = if (appSessions.isNotEmpty())
                    appSessions.average().toLong() / 60_000 else 0L
                val longestSession = (appSessions.maxOrNull() ?: 0L) / 60_000

                AppUsage(
                    packageName          = pkg,
                    label                = label,
                    todayMinutes         = todayMin,
                    weekMinutes          = (weekStats[pkg]?.totalTimeInForeground ?: 0L) / 60_000,
                    monthMinutes         = (monthStats[pkg]?.totalTimeInForeground ?: 0L) / 60_000,
                    lifetimeMinutes      = (lifetimeStats[pkg]?.totalTimeInForeground ?: 0L) / 60_000,
                    category             = getCategory(pkg),
                    unlockCount          = unlocks[pkg] ?: 0,
                    sessionCount         = sessionCount,
                    avgSessionMinutes    = avgSession,
                    hourlyMinutes        = hourly[pkg] ?: IntArray(24),
                    longestSessionMinutes= longestSession,
                    firstSeen            = lifetimeStats[pkg]?.firstTimeStamp ?: stat.firstTimeStamp,
                )
            }
            .filter { it.todayMinutes > 0 }
            .sortedByDescending { it.todayMinutes }
    }

    suspend fun getDailySummaries(ctx: Context, days: Int = 30): List<DaySummary> =
        withContext(Dispatchers.IO) {
            val usm    = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now    = System.currentTimeMillis()
            val result = mutableListOf<DaySummary>()
            val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val dayNum = SimpleDateFormat("d", Locale.getDefault())

            for (i in (days - 1) downTo 0) {
                val s   = startOfDay(-i)
                val e   = s + 86_400_000L
                val sts = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, s, minOf(e, now))
                val totalMin = sts?.filter { !shouldFilter(it.packageName) }
                    ?.sumOf { it.totalTimeInForeground } ?: 0L

                result.add(DaySummary(
                    label      = dayFmt.format(Date(s)),
                    shortLabel = dayNum.format(Date(s)),
                    minutes    = totalMin / 60_000,
                    unlocks    = 0
                ))
            }
            result
        }
}

// ── Formatters ────────────────────────────────────────────────
fun fmtMin(m: Long): String = when {
    m <= 0 -> "—"
    m < 1  -> "< 1m"
    m < 60 -> "${m}m"
    else   -> "${m / 60}h ${m % 60}m"
}

fun fmtDate(ts: Long): String =
    SimpleDateFormat("d MMM yy", Locale.getDefault()).format(Date(ts))

// ── Activity ──────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SereinApp() }
    }
}

@Composable
fun SereinApp() {
    val ctx = LocalContext.current
    var hasPerm by remember { mutableStateOf(UsageHelper.hasPermission(ctx)) }
    if (!hasPerm) PermissionScreen { UsageHelper.openPermissionSettings(ctx); hasPerm = UsageHelper.hasPermission(ctx) }
    else StatsRoot()
}

// ── Permission ────────────────────────────────────────────────
@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(BG).padding(48.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("serein", color = TAN, fontSize = 12.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(16.dp))
        Text("Usage access\nneeded.", color = TEXT, fontSize = 32.sp,
            fontWeight = FontWeight.Thin, lineHeight = 40.sp)
        Spacer(Modifier.height(16.dp))
        Text("Reads usage data locally.\nNothing leaves your phone.",
            color = TEXT_MUTED, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).background(TAN_FAINT)
                .clickable { onGrant() }.padding(horizontal = 28.dp, vertical = 14.dp)
        ) { Text("Grant access →", color = TAN, fontSize = 15.sp) }
        Spacer(Modifier.height(10.dp))
        Text("Settings → Usage Access → Serein → back",
            color = TEXT_MUTED, fontSize = 11.sp)
    }
}

// ── Root ──────────────────────────────────────────────────────
@Composable
fun StatsRoot() {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var apps by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var days30 by remember { mutableStateOf<List<DaySummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps   = UsageHelper.getUsageData(ctx)
        days30 = UsageHelper.getDailySummaries(ctx, 30)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(BG)) {
        // Tab bar
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            listOf("Today", "Trends", "Heat", "Apps", "All").forEachIndexed { i, lbl ->
                val active = tab == i
                Column(
                    Modifier.weight(1f).clickable { tab = i }.padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(lbl,
                        color = if (active) TAN else TEXT_MUTED,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Light,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(if (active) 18.dp else 0.dp).height(1.5.dp).background(TAN))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DIVIDER))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Crunching data...", color = TEXT_MUTED, fontSize = 13.sp)
            }
        } else {
            when (tab) {
                0 -> TodayScreen(apps, days30)
                1 -> TrendsScreen(days30, apps)
                2 -> HeatmapScreen(apps)
                3 -> CategoriesScreen(apps)
                4 -> AllAppsScreen(apps)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TODAY
// ─────────────────────────────────────────────────────────────
@Composable
fun TodayScreen(apps: List<AppUsage>, days30: List<DaySummary>) {
    val todayFmt   = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date())
    val totalMin   = apps.sumOf { it.todayMinutes }
    val totalUnlocks = apps.sumOf { it.unlockCount }
    val totalSessions = apps.sumOf { it.sessionCount }
    val maxMin     = apps.maxOfOrNull { it.todayMinutes } ?: 1L

    // 7-day avg for comparison
    val avg7 = if (days30.size >= 7)
        days30.takeLast(7).dropLast(1).map { it.minutes }.average().toLong() else 0L
    val delta = totalMin - avg7
    val deltaSign = if (delta >= 0) "+" else ""
    val deltaColor = if (delta > 15) RED_SOFT else if (delta < -15) GREEN_SOFT else TEXT_MUTED

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Header
        item {
            Text(todayFmt, color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(fmtMin(totalMin), color = TEXT, fontSize = 38.sp, fontWeight = FontWeight.Thin)
                if (avg7 > 0) {
                    Text("$deltaSign${fmtMin(kotlin.math.abs(delta))} vs 7d avg",
                        color = deltaColor, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // Stat pills
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("$totalUnlocks opens")
                StatPill("$totalSessions sessions")
                StatPill("${apps.size} apps")
                if (totalSessions > 0) StatPill("~${fmtMin(totalMin / totalSessions.coerceAtLeast(1))}/session")
            }
            Spacer(Modifier.height(24.dp))

            // Mini 7-day sparkline
            if (days30.isNotEmpty()) {
                val last7 = days30.takeLast(7)
                val maxD  = last7.maxOfOrNull { it.minutes } ?: 1L
                Row(
                    Modifier.fillMaxWidth().height(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    last7.forEachIndexed { i, d ->
                        val isLast = i == last7.lastIndex
                        val f = (d.minutes.toFloat() / maxD).coerceIn(0.05f, 1f)
                        Box(
                            Modifier.weight(1f).fillMaxHeight(f)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(if (isLast) TAN else TAN.copy(alpha = 0.25f))
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("7-day trend", color = TEXT_MUTED, fontSize = 10.sp)
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
        }

        // App rows
        items(apps) { app ->
            val frac = (app.todayMinutes.toFloat() / maxMin).coerceIn(0.02f, 1f)
            val anim by animateFloatAsState(frac, tween(600, easing = EaseOutCubic))
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(app.category, color = TAN_DIM, fontSize = 10.sp)
                            Text("·", color = TEXT_MUTED, fontSize = 10.sp)
                            Text("${app.unlockCount}× opened", color = TEXT_MUTED, fontSize = 10.sp)
                            if (app.avgSessionMinutes > 0) {
                                Text("·", color = TEXT_MUTED, fontSize = 10.sp)
                                Text("~${fmtMin(app.avgSessionMinutes)}/session", color = TEXT_MUTED, fontSize = 10.sp)
                            }
                        }
                    }
                    Text(fmtMin(app.todayMinutes), color = TAN, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(1.5.dp)
                        .clip(RoundedCornerShape(1.dp)).background(TAN_FAINT)
                ) {
                    Box(Modifier.fillMaxWidth(anim).height(1.5.dp)
                        .clip(RoundedCornerShape(1.dp)).background(TAN.copy(alpha = 0.65f)))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TRENDS
// ─────────────────────────────────────────────────────────────
@Composable
fun TrendsScreen(days: List<DaySummary>, apps: List<AppUsage>) {
    val maxDay    = days.maxOfOrNull { it.minutes } ?: 1L
    val totalWeek = days.takeLast(7).sumOf { it.minutes }
    val totalMonth= days.sumOf { it.minutes }
    val avg7      = if (days.size >= 7) days.takeLast(7).map { it.minutes }.average().toLong() else 0L
    val avg30     = days.map { it.minutes }.average().toLong()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp)
    ) {
        item {
            Text("30-day trends", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))

            // Summary grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("This week", fmtMin(totalWeek), Modifier.weight(1f))
                StatCard("This month", fmtMin(totalMonth), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("7d avg / day", fmtMin(avg7), Modifier.weight(1f))
                StatCard("30d avg / day", fmtMin(avg30), Modifier.weight(1f))
            }
            Spacer(Modifier.height(28.dp))

            // 30-day bar chart
            Text("Daily screen time", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))

            val todayIdx = days.lastIndex
            Row(
                Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { i, d ->
                    val f = (d.minutes.toFloat() / maxDay).coerceIn(0.02f, 1f)
                    val anim by animateFloatAsState(f, tween(400 + i * 10, easing = EaseOutCubic))
                    Box(
                        Modifier.weight(1f).fillMaxHeight(anim)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(
                                when {
                                    i == todayIdx -> TAN
                                    d.minutes > avg30 * 1.3 -> RED_SOFT.copy(alpha = 0.5f)
                                    else -> TAN.copy(alpha = 0.25f)
                                }
                            )
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(days.firstOrNull()?.shortLabel ?: "", color = TEXT_MUTED, fontSize = 9.sp)
                Text("today", color = TAN, fontSize = 9.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(TAN, "Today")
                LegendDot(RED_SOFT.copy(alpha = 0.5f), "> 30d avg")
                LegendDot(TAN.copy(alpha = 0.25f), "Normal")
            }

            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(20.dp))

            // Best / worst days
            val best  = days.minByOrNull { it.minutes }
            val worst = days.maxByOrNull { it.minutes }
            Text("Extremes", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Best day", if (best != null) "${best.label} · ${fmtMin(best.minutes)}" else "—", Modifier.weight(1f), GREEN_SOFT)
                StatCard("Worst day", if (worst != null) "${worst.label} · ${fmtMin(worst.minutes)}" else "—", Modifier.weight(1f), RED_SOFT)
            }

            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(20.dp))

            // Lifetime section
            Text("Lifetime", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
        }

        // Lifetime per app
        items(apps.filter { it.lifetimeMinutes > 0 }.sortedByDescending { it.lifetimeMinutes }) { app ->
            val lifetimeHours = app.lifetimeMinutes / 60
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text(app.label, color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Light)
                    Text("since ${fmtDate(app.firstSeen)}", color = TEXT_MUTED, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtMin(app.lifetimeMinutes), color = TAN, fontSize = 13.sp)
                    Text("$lifetimeHours hrs total", color = TEXT_MUTED, fontSize = 10.sp)
                }
            }
            Divider()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// HEATMAP
// ─────────────────────────────────────────────────────────────
@Composable
fun HeatmapScreen(apps: List<AppUsage>) {
    val merged = IntArray(24)
    apps.forEach { a -> a.hourlyMinutes.forEachIndexed { h, m -> merged[h] += m } }
    val maxHour  = merged.maxOrNull()?.takeIf { it > 0 } ?: 1
    val peakHour = merged.indices.maxByOrNull { merged[it] } ?: 0
    val quietHour= merged.indices.filter { merged[it] > 0 }.minByOrNull { merged[it] }
    val totalDay = merged.sum()

    val timeFmt = SimpleDateFormat("ha", Locale.getDefault())
    fun hourLabel(h: Int) = timeFmt.format(
        Calendar.getInstance().also { it.set(Calendar.HOUR_OF_DAY, h) }.time
    ).lowercase()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp)
    ) {
        item {
            Text("Hourly heatmap", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text("Today", color = TEXT, fontSize = 28.sp, fontWeight = FontWeight.Thin)
            Spacer(Modifier.height(24.dp))

            // 24-cell heatmap
            val segments = listOf(
                "Night"     to 0..5,
                "Morning"   to 6..11,
                "Afternoon" to 12..17,
                "Evening"   to 18..23
            )
            segments.forEach { (label, range) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = TEXT_MUTED, fontSize = 10.sp, modifier = Modifier.width(64.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        range.forEach { h ->
                            val f = merged[h].toFloat() / maxHour
                            val anim by animateFloatAsState(f, tween(400 + h * 20, easing = EaseOutCubic))
                            Box(
                                Modifier.weight(1f).aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TAN.copy(alpha = 0.06f + anim * 0.88f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (merged[h] > 0)
                                    Text("${merged[h]}", color = if (f > 0.55f) BG else TAN.copy(alpha = 0.9f), fontSize = 7.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Hour labels
            Row(Modifier.fillMaxWidth().padding(start = 64.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 6, 12, 18, 23).forEach { h ->
                    Text(hourLabel(h), color = TEXT_MUTED, fontSize = 9.sp)
                }
            }

            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(20.dp))

            // Insights
            Text("Insights", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Peak hour", hourLabel(peakHour), Modifier.weight(1f))
                StatCard("Peak usage", "${merged[peakHour]}m", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Quietest", if (quietHour != null) hourLabel(quietHour) else "—", Modifier.weight(1f))
                StatCard("Active hours", "${merged.count { it > 0 }}", Modifier.weight(1f))
            }

            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(20.dp))

            // Per-app hourly bars (top 5)
            Text("By app · today", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))
        }

        items(apps.take(5)) { app ->
            val appMax = app.hourlyMinutes.maxOrNull()?.takeIf { it > 0 } ?: 1
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(app.label, color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Light)
                    Text(fmtMin(app.todayMinutes), color = TAN, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    app.hourlyMinutes.forEach { m ->
                        val f = (m.toFloat() / appMax).coerceIn(0f, 1f)
                        val anim by animateFloatAsState(f, tween(500, easing = EaseOutCubic))
                        Box(
                            Modifier.weight(1f).fillMaxHeight(anim.coerceAtLeast(0.05f))
                                .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                                .background(TAN.copy(alpha = if (m > 0) 0.6f else 0.08f))
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// CATEGORIES
// ─────────────────────────────────────────────────────────────
@Composable
fun CategoriesScreen(apps: List<AppUsage>) {
    val grouped = apps.groupBy { it.category }
        .mapValues { (_, list) ->
            Triple(
                list.sumOf { it.todayMinutes },
                list.sumOf { it.weekMinutes },
                list
            )
        }
        .entries.sortedByDescending { it.value.first }

    val totalToday = grouped.sumOf { it.value.first }.coerceAtLeast(1L)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp)
    ) {
        item {
            Text("By category", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text("Today", color = TEXT, fontSize = 28.sp, fontWeight = FontWeight.Thin)
            Spacer(Modifier.height(20.dp))

            // Segmented bar
            Row(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))) {
                grouped.forEachIndexed { i, e ->
                    val f = e.value.first.toFloat() / totalToday
                    Box(Modifier.weight(f.coerceAtLeast(0.01f)).fillMaxHeight()
                        .background(TAN.copy(alpha = 1f - i * 0.1f)))
                }
            }
            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(8.dp))
        }

        items(grouped) { (cat, triple) ->
            val (todayMin, weekMin, catApps) = triple
            val pct = (todayMin.toFloat() / totalToday * 100).toInt()
            val anim by animateFloatAsState(
                todayMin.toFloat() / totalToday, tween(600, easing = EaseOutCubic))

            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(cat, color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.Light)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmtMin(todayMin), color = TAN, fontSize = 13.sp)
                            Text("today", color = TEXT_MUTED, fontSize = 9.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmtMin(weekMin), color = TAN_DIM, fontSize = 13.sp)
                            Text("week", color = TEXT_MUTED, fontSize = 9.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$pct%", color = TEXT_MUTED, fontSize = 13.sp)
                            Text("share", color = TEXT_MUTED, fontSize = 9.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(TAN_FAINT)) {
                    Box(Modifier.fillMaxWidth(anim).height(2.dp)
                        .clip(RoundedCornerShape(1.dp)).background(TAN.copy(alpha = 0.6f)))
                }
                Spacer(Modifier.height(8.dp))
                Text(catApps.joinToString(" · ") { it.label },
                    color = TEXT_MUTED, fontSize = 10.sp, lineHeight = 15.sp)
            }
            Divider()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ALL APPS — deep stats per app
// ─────────────────────────────────────────────────────────────
@Composable
fun AllAppsScreen(apps: List<AppUsage>) {
    var sortBy by remember { mutableStateOf(0) } // 0=today 1=week 2=month 3=lifetime 4=unlocks

    val sorted = remember(sortBy, apps) {
        when (sortBy) {
            0 -> apps.sortedByDescending { it.todayMinutes }
            1 -> apps.sortedByDescending { it.weekMinutes }
            2 -> apps.sortedByDescending { it.monthMinutes }
            3 -> apps.sortedByDescending { it.lifetimeMinutes }
            4 -> apps.sortedByDescending { it.unlockCount }
            else -> apps
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp)
    ) {
        item {
            Text("All apps", color = TEXT_MUTED, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))

            // Sort tabs
            ScrollableRow {
                listOf("Today","Week","Month","Lifetime","Opens").forEachIndexed { i, lbl ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sortBy == i) TAN_FAINT else Color.Transparent)
                            .border(1.dp, if (sortBy == i) TAN_DIM else DIVIDER, RoundedCornerShape(20.dp))
                            .clickable { sortBy = i }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(lbl, color = if (sortBy == i) TAN else TEXT_MUTED, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Divider()
            Spacer(Modifier.height(8.dp))
        }

        items(sorted) { app ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Light)
                        Text(app.category, color = TAN_DIM, fontSize = 10.sp)
                    }
                    // Primary sort value highlighted
                    Text(
                        when (sortBy) {
                            0 -> fmtMin(app.todayMinutes)
                            1 -> fmtMin(app.weekMinutes)
                            2 -> fmtMin(app.monthMinutes)
                            3 -> fmtMin(app.lifetimeMinutes)
                            4 -> "${app.unlockCount}×"
                            else -> ""
                        },
                        color = TAN, fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Dense stat row
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    MiniStat("Today",    fmtMin(app.todayMinutes),    Modifier.weight(1f))
                    MiniStat("Week",     fmtMin(app.weekMinutes),     Modifier.weight(1f))
                    MiniStat("Month",    fmtMin(app.monthMinutes),    Modifier.weight(1f))
                    MiniStat("Lifetime", fmtMin(app.lifetimeMinutes), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    MiniStat("Opens",    "${app.unlockCount}",                   Modifier.weight(1f))
                    MiniStat("Sessions", "${app.sessionCount}",                   Modifier.weight(1f))
                    MiniStat("Avg",      fmtMin(app.avgSessionMinutes),           Modifier.weight(1f))
                    MiniStat("Longest",  fmtMin(app.longestSessionMinutes),       Modifier.weight(1f))
                }
                if (app.firstSeen > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("First used ${fmtDate(app.firstSeen)}", color = TEXT_MUTED, fontSize = 9.sp)
                }
            }
            Divider()
        }
    }
}

// ── Shared components ─────────────────────────────────────────
@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(DIVIDER))

@Composable
fun StatPill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(TAN_FAINT)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, color = TAN_DIM, fontSize = 11.sp) }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = TAN) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(SURFACE)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, color = TEXT_MUTED, fontSize = 10.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Text(label, color = TEXT_MUTED, fontSize = 9.sp)
        Text(value, color = TEXT, fontSize = 12.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, color = TEXT_MUTED, fontSize = 10.sp)
    }
}

@Composable
fun ScrollableRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        content = content
    )
}