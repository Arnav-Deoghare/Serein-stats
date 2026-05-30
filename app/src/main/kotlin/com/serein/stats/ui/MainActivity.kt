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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════
// COLOR SCHEMES
// ═══════════════════════════════════════════════════════════════

enum class ColorScheme(val label: String, val accentName: String) {
    TAN("Dark Tan", "tan"),
    PAPER("Ink on Paper", "gold"),
    SLATE("Slate Blue", "blue"),
    FOREST("Forest", "green"),
}

data class SchemeColors(
    val bg: Color, val surface: Color, val surface2: Color,
    val border: Color, val border2: Color,
    val accent: Color, val accentDim: Color, val accentFaint: Color,
    val text: Color, val text2: Color, val text3: Color,
    val green: Color, val red: Color,
    val barNormal: Color, val barAbove: Color,
)

fun ColorScheme.colors() = when (this) {
    ColorScheme.TAN -> SchemeColors(
        bg=Color(0xFF16130F), surface=Color(0xFF1E1A14), surface2=Color(0xFF252018),
        border=Color(0xFF2E2820), border2=Color(0xFF3A3228),
        accent=Color(0xFFBE9E72), accentDim=Color(0xFF8A6E4A), accentFaint=Color(0xFF3D3020),
        text=Color(0xFFEFE6D8), text2=Color(0xFF9C8C78), text3=Color(0xFF5A5040),
        green=Color(0xFF7FA882), red=Color(0xFFB07868),
        barNormal=Color(0xFFBE9E72).copy(alpha=0.22f), barAbove=Color(0xFFB07868).copy(alpha=0.55f),
    )
    ColorScheme.PAPER -> SchemeColors(
        bg=Color(0xFFF7F4EE), surface=Color(0xFFEEEAE2), surface2=Color(0xFFE5E0D6),
        border=Color(0xFFD8D2C6), border2=Color(0xFFC8C0B0),
        accent=Color(0xFF8B6914), accentDim=Color(0xFF6B5010), accentFaint=Color(0xFFEAE0CC),
        text=Color(0xFF2A2520), text2=Color(0xFF6B5F50), text3=Color(0xFF9C8C78),
        green=Color(0xFF3D6B42), red=Color(0xFF8B3A2A),
        barNormal=Color(0xFF8B6914).copy(alpha=0.22f), barAbove=Color(0xFF8B3A2A).copy(alpha=0.45f),
    )
    ColorScheme.SLATE -> SchemeColors(
        bg=Color(0xFF0F1117), surface=Color(0xFF161B24), surface2=Color(0xFF1E2536),
        border=Color(0xFF242D3E), border2=Color(0xFF2E3A50),
        accent=Color(0xFF7E9ECC), accentDim=Color(0xFF4E6E9C), accentFaint=Color(0xFF1A2540),
        text=Color(0xFFE8ECF4), text2=Color(0xFF8090A8), text3=Color(0xFF485868),
        green=Color(0xFF6A9E82), red=Color(0xFFB07878),
        barNormal=Color(0xFF7E9ECC).copy(alpha=0.22f), barAbove=Color(0xFFB07878).copy(alpha=0.55f),
    )
    ColorScheme.FOREST -> SchemeColors(
        bg=Color(0xFF0D1610), surface=Color(0xFF121E14), surface2=Color(0xFF182818),
        border=Color(0xFF1E2E1E), border2=Color(0xFF263826),
        accent=Color(0xFF6BAA7A), accentDim=Color(0xFF437A52), accentFaint=Color(0xFF163020),
        text=Color(0xFFD8EDD8), text2=Color(0xFF7A9E7A), text3=Color(0xFF486048),
        green=Color(0xFF6BAA7A), red=Color(0xFFB07870),
        barNormal=Color(0xFF6BAA7A).copy(alpha=0.22f), barAbove=Color(0xFFB07870).copy(alpha=0.55f),
    )
}

// ═══════════════════════════════════════════════════════════════
// SYSTEM FILTER
// ═══════════════════════════════════════════════════════════════

val SYSTEM_BLACKLIST = setOf(
    "com.zen.launcher","com.android.launcher","com.sec.android.app.launcher",
    "com.samsung.android.app.cocktailbarservice","com.android.systemui",
    "com.samsung.android.systemui","com.google.android.gms","com.google.android.gsf",
    "com.android.settings","com.samsung.android.settings","android","com.android.phone",
    "com.samsung.android.incallui","com.android.inputmethod.latin",
    "com.samsung.android.honeyboard","com.serein.stats","com.android.vending",
    "com.google.android.packageinstaller","com.android.packageinstaller",
    "com.samsung.android.aodservice","com.samsung.android.bixby.agent",
    "com.samsung.android.app.galaxyfinder",
)
fun shouldFilter(pkg: String) = pkg in SYSTEM_BLACKLIST
    || pkg.startsWith("com.android.") || pkg.startsWith("com.samsung.android.server")
    || pkg.startsWith("android.process.")

// ═══════════════════════════════════════════════════════════════
// CATEGORIES
// ═══════════════════════════════════════════════════════════════

val CATEGORIES = linkedMapOf(
    "Social"       to listOf("com.whatsapp","com.instagram.android","com.twitter.android","com.facebook.katana","com.snapchat.android","com.telegram.messenger","org.telegram.messenger","com.discord","com.linkedin.android"),
    "Video"        to listOf("com.google.android.youtube","com.netflix.mediaclient","com.amazon.avod.thirdpartyclient","in.startv.hotstar"),
    "Music"        to listOf("com.spotify.music","com.google.android.music","com.soundcloud.android","com.apple.android.music"),
    "Reading"      to listOf("com.amazon.kindle","com.google.android.apps.books","com.medium.reader","com.getpocket.queryandroid"),
    "Productivity" to listOf("com.google.android.gm","com.microsoft.teams","com.slack","com.notion.id","com.todoist","com.google.android.calendar","com.google.android.keep"),
    "Browser"      to listOf("com.android.chrome","org.mozilla.firefox","com.brave.browser"),
    "Maps"         to listOf("com.google.android.apps.maps","com.waze"),
    "Finance"      to listOf("com.phonepe.app","net.one97.paytm"),
    "Health"       to listOf("com.google.android.apps.fitness","com.samsung.android.shealth","com.headspace.android","com.calm.android"),
)
fun getCategory(pkg: String): String { CATEGORIES.forEach { (c,p) -> if (pkg in p) return c }; return "Other" }

// ═══════════════════════════════════════════════════════════════
// MODELS
// ═══════════════════════════════════════════════════════════════

data class AppUsage(
    val packageName: String, val label: String,
    val todayMinutes: Long, val weekMinutes: Long,
    val monthMinutes: Long, val lifetimeMinutes: Long,
    val category: String, val unlockCount: Int,
    val sessionCount: Int, val avgSessionMinutes: Long,
    val longestSessionMinutes: Long, val hourlyMinutes: IntArray,
    val firstSeen: Long,
    // FIX 3: proper short session tracking
    val shortSessionCount: Int,
)
data class DaySummary(val label: String, val date: String, val minutes: Long)

// ═══════════════════════════════════════════════════════════════
// DATA LAYER
// ═══════════════════════════════════════════════════════════════

object UsageHelper {
    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun openPermissionSettings(ctx: Context) =
        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    private fun startOfDay(offset: Int = 0) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0)
        set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
        if (offset != 0) add(Calendar.DAY_OF_YEAR, offset)
    }.timeInMillis

    suspend fun getApps(ctx: Context): List<AppUsage> = withContext(Dispatchers.IO) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm  = ctx.packageManager
        val now = System.currentTimeMillis()

        val todayMap = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay(), now)
            ?.filter { !shouldFilter(it.packageName) }?.associateBy { it.packageName } ?: emptyMap()
        val weekMap  = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, startOfDay(-6), now)
            ?.filter { !shouldFilter(it.packageName) }?.associateBy { it.packageName } ?: emptyMap()
        val monthMap = usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, startOfDay(-29), now)
            ?.filter { !shouldFilter(it.packageName) }?.associateBy { it.packageName } ?: emptyMap()
        val lifeMap  = usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, startOfDay(-1460), now)
            ?.filter { !shouldFilter(it.packageName) }?.associateBy { it.packageName } ?: emptyMap()

        val hourly       = mutableMapOf<String, IntArray>()
        val unlocks      = mutableMapOf<String, Int>()
        val sessions     = mutableMapOf<String, MutableList<Long>>()
        val starts       = mutableMapOf<String, Long>()
        // FIX 2: track first/last foreground timestamps
        val firstUnlock  = mutableMapOf<String, Long>()
        val lastUnlock   = mutableMapOf<String, Long>()

        val events = usm.queryEvents(startOfDay(), now)
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            if (shouldFilter(pkg)) continue
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    starts[pkg] = ev.timeStamp
                    unlocks[pkg] = (unlocks[pkg] ?: 0) + 1
                    if (!firstUnlock.containsKey(pkg)) firstUnlock[pkg] = ev.timeStamp
                    lastUnlock[pkg] = ev.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val s = starts.remove(pkg) ?: continue
                    val dur = ev.timeStamp - s
                    if (dur < 2000) continue
                    sessions.getOrPut(pkg) { mutableListOf() }.add(dur)
                    val b = hourly.getOrPut(pkg) { IntArray(24) }
                    var t = s
                    while (t < ev.timeStamp) {
                        val h = Calendar.getInstance().also { c -> c.timeInMillis = t }
                            .get(Calendar.HOUR_OF_DAY)
                        val next = Calendar.getInstance().also { c ->
                            c.timeInMillis = t
                            c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
                            c.add(Calendar.HOUR_OF_DAY, 1)
                        }.timeInMillis
                        val end = minOf(next, ev.timeStamp)
                        b[h] += ((end - t) / 60_000).toInt()
                        t = end
                    }
                }
            }
        }

        todayMap.values.filter { it.totalTimeInForeground > 30_000 }.mapNotNull { stat ->
            val pkg  = stat.packageName
            val lbl  = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() } }
            val sess = sessions[pkg] ?: emptyList()
            // FIX 3: count sessions under 2 minutes (120_000 ms)
            val shortSessions = sess.count { it < 120_000 }

            AppUsage(
                packageName          = pkg,
                label                = lbl,
                todayMinutes         = stat.totalTimeInForeground / 60_000,
                weekMinutes          = (weekMap[pkg]?.totalTimeInForeground ?: 0L) / 60_000,
                monthMinutes         = (monthMap[pkg]?.totalTimeInForeground ?: 0L) / 60_000,
                lifetimeMinutes      = (lifeMap[pkg]?.totalTimeInForeground ?: 0L) / 60_000,
                category             = getCategory(pkg),
                unlockCount          = unlocks[pkg] ?: 0,
                sessionCount         = sess.size.coerceAtLeast(1),
                avgSessionMinutes    = if (sess.isNotEmpty()) sess.average().toLong() / 60_000 else 0L,
                longestSessionMinutes= (sess.maxOrNull() ?: 0L) / 60_000,
                hourlyMinutes        = hourly[pkg] ?: IntArray(24),
                firstSeen            = lifeMap[pkg]?.firstTimeStamp ?: stat.firstTimeStamp,
                shortSessionCount    = shortSessions,
            )
        }.filter { it.todayMinutes > 0 }.sortedByDescending { it.todayMinutes }
    }

    // FIX 2: separate query to get today's first + last unlock across all apps
    suspend fun getTodayUnlockBounds(ctx: Context): Pair<Long?, Long?> = withContext(Dispatchers.IO) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        var first: Long? = null
        var last: Long? = null
        val events = usm.queryEvents(startOfDay(), now)
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && !shouldFilter(ev.packageName ?: "")) {
                if (first == null) first = ev.timeStamp
                last = ev.timeStamp
            }
        }
        Pair(first, last)
    }

    suspend fun getDays(ctx: Context, n: Int = 30): List<DaySummary> = withContext(Dispatchers.IO) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val df  = SimpleDateFormat("EEE", Locale.getDefault())
        val dtf = SimpleDateFormat("d MMM", Locale.getDefault())
        (n - 1 downTo 0).map { i ->
            val s = startOfDay(-i); val e = s + 86_400_000L
            val total = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, s, minOf(e, now))
                ?.filter { !shouldFilter(it.packageName) }
                ?.sumOf { it.totalTimeInForeground } ?: 0L
            DaySummary(df.format(Date(s)), dtf.format(Date(s)), total / 60_000)
        }
    }

    // FIX 6: week-over-week comparison
    suspend fun getLastWeekTotal(ctx: Context): Long = withContext(Dispatchers.IO) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val lastWeekStart = startOfDay(-13)
        val lastWeekEnd   = startOfDay(-7)
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, lastWeekStart, lastWeekEnd)
            ?.filter { !shouldFilter(it.packageName) }
            ?.sumOf { it.totalTimeInForeground } ?.div(60_000) ?: 0L
    }
}

fun fmt(m: Long): String = when { m <= 0 -> "—"; m < 60 -> "${m}m"; else -> "${m/60}h ${m%60}m" }
fun fmtDate(ts: Long): String = SimpleDateFormat("d MMM ''yy", Locale.getDefault()).format(Date(ts))
fun fmtTime(ts: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))

// ═══════════════════════════════════════════════════════════════
// PREFERENCES
// ═══════════════════════════════════════════════════════════════

fun Context.getPrefs() = getSharedPreferences("serein_prefs", Context.MODE_PRIVATE)
fun Context.getColorScheme(): ColorScheme =
    ColorScheme.valueOf(getPrefs().getString("color_scheme", ColorScheme.TAN.name)!!)
fun Context.saveColorScheme(s: ColorScheme) =
    getPrefs().edit().putString("color_scheme", s.name).apply()

// ═══════════════════════════════════════════════════════════════
// ACTIVITY
// ═══════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SereinApp() }
    }
}

// ═══════════════════════════════════════════════════════════════
// SPLASH
// ═══════════════════════════════════════════════════════════════

@Composable
fun SplashScreen(c: SchemeColors, onDone: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    // FIX 1: use .alpha() not graphicsLayer — no unused imports
    val alphaVal by animateFloatAsState(if (visible) 1f else 0f, tween(600))
    val nudge    by animateFloatAsState(if (visible) 0f else 12f, tween(700, easing = EaseOutCubic))

    LaunchedEffect(Unit) { visible = true; delay(1800); onDone() }

    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = nudge.dp).alpha(alphaVal)
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(c.accentFaint)
                    .border(1.dp, c.accentDim.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = c.accent, fontSize = 28.sp, fontWeight = FontWeight.Thin)
            }
            Spacer(Modifier.height(18.dp))
            Text("SEREIN", color = c.accent, fontSize = 13.sp,
                letterSpacing = 5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("Screen Intelligence", color = c.text3, fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ROOT
// ═══════════════════════════════════════════════════════════════

@Composable
fun SereinApp() {
    val ctx = LocalContext.current
    var scheme     by remember { mutableStateOf(ctx.getColorScheme()) }
    var hasPerm    by remember { mutableStateOf(UsageHelper.hasPermission(ctx)) }
    var showSplash by remember { mutableStateOf(true) }
    val c = scheme.colors()

    when {
        showSplash -> SplashScreen(c) { showSplash = false }
        !hasPerm   -> PermissionScreen(c) {
            UsageHelper.openPermissionSettings(ctx)
            hasPerm = UsageHelper.hasPermission(ctx)
        }
        else       -> MainNav(c, scheme) { newScheme ->
            scheme = newScheme; ctx.saveColorScheme(newScheme)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// PERMISSION SCREEN
// ═══════════════════════════════════════════════════════════════

@Composable
fun PermissionScreen(c: SchemeColors, onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(40.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                    .background(c.accentFaint)
                    .border(1.dp, c.accentDim.copy(alpha=0.4f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center) {
                    Text("S", color=c.accent, fontSize=16.sp, fontWeight=FontWeight.Thin)
                }
                Column {
                    Text("SEREIN", color=c.accent, fontSize=11.sp,
                        letterSpacing=3.sp, fontWeight=FontWeight.Medium)
                    Text("Screen Intelligence", color=c.text3, fontSize=10.sp)
                }
            }
        }
        Column {
            Text("One permission\nto begin.", color=c.text, fontSize=36.sp,
                fontWeight=FontWeight.Thin, lineHeight=44.sp)
            Spacer(Modifier.height(12.dp))
            Text("Serein reads usage data on-device only.\nNothing is ever sent anywhere.",
                color=c.text3, fontSize=13.sp, lineHeight=20.sp)
            Spacer(Modifier.height(32.dp))
            listOf("Tap the button below","Find Serein in the list",
                   "Toggle usage access on","Come back here").forEachIndexed { i, step ->
                Row(Modifier.padding(vertical=7.dp),
                    horizontalArrangement=Arrangement.spacedBy(14.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp))
                        .background(c.accentFaint), contentAlignment=Alignment.Center) {
                        Text("${i+1}", color=c.accent, fontSize=11.sp, fontWeight=FontWeight.Medium)
                    }
                    Text(step, color=c.text2, fontSize=13.sp)
                }
            }
        }
        Column {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(c.accentFaint)
                .border(1.dp, c.accentDim.copy(alpha=0.4f), RoundedCornerShape(12.dp))
                .clickable { onGrant() }
                .padding(vertical=16.dp), contentAlignment=Alignment.Center) {
                Text("Open Usage Access Settings →", color=c.accent,
                    fontSize=14.sp, fontWeight=FontWeight.Medium)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// MAIN NAV
// ═══════════════════════════════════════════════════════════════

@Composable
fun MainNav(c: SchemeColors, scheme: ColorScheme, onSchemeChange: (ColorScheme) -> Unit) {
    val ctx = LocalContext.current
    var tab  by remember { mutableStateOf(0) }
    var apps by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var days by remember { mutableStateOf<List<DaySummary>>(emptyList()) }
    var unlockBounds by remember { mutableStateOf<Pair<Long?,Long?>>(Pair(null,null)) }
    var lastWeekTotal by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }

    // FIX 7: pull-to-refresh counter — increment to trigger reload
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        apps         = UsageHelper.getApps(ctx)
        days         = UsageHelper.getDays(ctx, 30)
        unlockBounds = UsageHelper.getTodayUnlockBounds(ctx)
        lastWeekTotal= UsageHelper.getLastWeekTotal(ctx)
        loading      = false
    }

    val navItems = listOf("Today","Trends","Hours","Apps","Theme")

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Box(Modifier.fillMaxSize().padding(bottom = 64.dp)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Reading your data…", color=c.text3, fontSize=14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("This may take a moment",
                            color=c.text3.copy(alpha=0.5f), fontSize=11.sp)
                    }
                }
            } else {
                when (tab) {
                    0 -> TodayTab(c, apps, days, unlockBounds) { refreshKey++ }
                    1 -> TrendsTab(c, scheme, days, apps, lastWeekTotal) { refreshKey++ }
                    2 -> HeatTab(c, apps) { refreshKey++ }
                    3 -> AllAppsTab(c, apps) { refreshKey++ }
                    4 -> ThemeTab(c, scheme, onSchemeChange)
                }
            }
        }

        // Bottom nav
        Column(Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(
                Modifier.fillMaxWidth().background(c.bg).padding(vertical=8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                navItems.forEachIndexed { i, label ->
                    val active = tab == i
                    Column(
                        Modifier.weight(1f).clickable { tab = i }.padding(vertical=4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (active) c.accent else Color.Transparent))
                        Spacer(Modifier.height(4.dp))
                        Text(label,
                            color = if (active) c.accent else c.text3,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 0.2.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SHARED HEADER
// ═══════════════════════════════════════════════════════════════

@Composable
fun ScreenHeader(c: SchemeColors, title: String, subtitle: String, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start=28.dp, end=28.dp, top=52.dp, bottom=0.dp)) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                    .background(c.accentFaint), contentAlignment=Alignment.Center) {
                    Text("S", color=c.accent, fontSize=10.sp, fontWeight=FontWeight.Medium)
                }
                Text("SEREIN", color=c.accent, fontSize=9.sp,
                    letterSpacing=3.sp, fontWeight=FontWeight.SemiBold)
            }
            // FIX 7: refresh button in every header
            Text("↻ refresh", color=c.text3, fontSize=10.sp,
                modifier=Modifier.clickable { onRefresh() }.padding(4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color=c.text, fontSize=26.sp, fontWeight=FontWeight.Thin)
        Text(subtitle, color=c.text3, fontSize=11.sp, letterSpacing=0.3.sp)
        Spacer(Modifier.height(16.dp))
        HLine(c)
    }
}

// ═══════════════════════════════════════════════════════════════
// TODAY TAB
// ═══════════════════════════════════════════════════════════════

@Composable
fun TodayTab(
    c: SchemeColors,
    apps: List<AppUsage>,
    days: List<DaySummary>,
    unlockBounds: Pair<Long?,Long?>,
    onRefresh: () -> Unit
) {
    val total       = apps.sumOf { it.todayMinutes }
    val unlocks     = apps.sumOf { it.unlockCount }
    val sessions    = apps.sumOf { it.sessionCount }
    val deepSess    = apps.sumOf { app -> app.hourlyMinutes.count { it >= 10 }.toLong() }
    val avg7        = if (days.size>=7) days.takeLast(7).dropLast(1).map{it.minutes}.average().toLong() else 0L
    val delta       = total - avg7
    val maxMin      = apps.maxOfOrNull { it.todayMinutes } ?: 1L
    val todayStr    = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault()).format(Date()).uppercase()
    val totalShort  = apps.sumOf { it.shortSessionCount }
    val topCategory = apps.groupBy { it.category }
        .maxByOrNull { e -> e.value.sumOf { it.todayMinutes } }?.key ?: "—"

    // Intentionality score 0–100
    val deliberateApps = setOf("Reading","Music","Productivity","Maps","Health")
    val reflexiveApps  = setOf("Social","Browser","Video")
    val deliberateMin  = apps.filter { it.category in deliberateApps }.sumOf { it.todayMinutes }
    val reflexiveMin   = apps.filter { it.category in reflexiveApps  }.sumOf { it.todayMinutes }
    val intentScore    = if (deliberateMin + reflexiveMin > 0)
        ((deliberateMin.toFloat() / (deliberateMin + reflexiveMin)) * 100).toInt() else 0

    // Longest break between unlocks (approximate from hourly gaps)
    val emptyHours = days.lastOrNull()?.let { 0 } ?: 0 // placeholder — real value comes from unlockBounds
    val longestBreakStr = if (unlockBounds.first != null && unlockBounds.second != null) {
        val gapMs = unlockBounds.second!! - unlockBounds.first!!
        val gapMin = gapMs / 60_000
        if (gapMin > 60) "${gapMin/60}h ${gapMin%60}m" else "${gapMin}m"
    } else "—"

    // 7-day data
    val last7 = days.takeLast(7)
    val maxD  = last7.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1L
    val weekTotal = last7.sumOf { it.minutes }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {

        // ── 1. HEADER ────────────────────────────────────────────
        item {
            ScreenHeader(c, "Today", todayStr.lowercase(), onRefresh)
            Spacer(Modifier.height(20.dp))
        }

        // ── 2. HERO NUMBER + DELTA ────────────────────────────────
        item {
            Column(Modifier.padding(horizontal = 28.dp)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        fmt(total),
                        color = c.text,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp
                    )
                    if (avg7 > 0 && delta != 0L) {
                        val sign = if (delta > 0) "↑" else "↓"
                        val col  = if (delta > 15) c.red else if (delta < -15) c.green else c.text3
                        Column(Modifier.padding(bottom = 20.dp)) {
                            Text("$sign ${fmt(abs(delta))}", color = col,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("vs 7-day avg", color = c.text3, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // ── 3. UNLOCKS (left, large) + INTENTIONALITY (right) ────
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Unlocks — same visual weight as hero
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "$unlocks×",
                        color = c.accent,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp
                    )
                    Column(Modifier.padding(bottom = 5.dp)) {
                        Text("phone", color = c.text3, fontSize = 10.sp)
                        Text("opens", color = c.text3, fontSize = 10.sp)
                    }
                }

                // Intentionality score — right aligned
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$intentScore",
                        color = if (intentScore >= 60) c.green else if (intentScore >= 40) c.accent else c.red,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text("intentionality", color = c.text3, fontSize = 9.sp)
                    Text(
                        if (intentScore >= 60) "↑ deliberate" else if (intentScore >= 40) "mixed" else "↓ reflexive",
                        color = c.text3,
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── 4. WEEKLY BARS ────────────────────────────────────────
        item {
            Column(Modifier.padding(horizontal = 28.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        // Top row: label + total
                        Row(
                            Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text("PAST 7 DAYS", color = c.text3,
                                fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
                            Text(fmt(weekTotal) + " total", color = c.accentDim, fontSize = 9.sp)
                        }
                        Spacer(Modifier.height(10.dp))

                        // Bars
                        Row(
                            Modifier.fillMaxWidth().height(52.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            last7.forEachIndexed { i, d ->
                                val isToday    = i == last7.lastIndex
                                val aboveAvg   = d.minutes > avg7 * 1.2
                                val frac = (d.minutes.toFloat() / maxD).coerceIn(0.04f, 1f)
                                val anim by animateFloatAsState(
                                    frac, tween(400 + i * 50, easing = EaseOutCubic))
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth().height(40.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(anim)
                                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                                .background(
                                                    when {
                                                        isToday   -> c.accent
                                                        aboveAvg  -> c.barAbove
                                                        else      -> c.barNormal
                                                    }
                                                )
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        d.label.take(1),
                                        color = if (isToday) c.accent else c.text3,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // ── 5. APP BREAKDOWN HEADER ───────────────────────────────
        item {
            Column(Modifier.padding(horizontal = 28.dp)) {
                Text("APP BREAKDOWN", color = c.text3,
                    fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("time · opens · avg session · trend",
                    color = c.text3.copy(alpha = 0.6f), fontSize = 9.sp)
                Spacer(Modifier.height(8.dp))
            }
            // Column headers
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp)
            ) {
                Text("App", color = c.text3, fontSize = 9.sp,
                    letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
                Text("Opens", color = c.text3, fontSize = 9.sp,
                    modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                Text("Time", color = c.text3, fontSize = 9.sp,
                    modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
            }
            HLine(c)
        }

        // ── 6. APP ROWS WITH CIRCULAR RINGS ──────────────────────
        items(apps) { app ->
            val appFrac   = (app.todayMinutes.toFloat() / maxMin).coerceIn(0f, 1f)
            val ringAnim  by animateFloatAsState(appFrac, tween(700, easing = EaseOutCubic))
            val ringColor = when {
                appFrac > 0.8f -> c.red
                appFrac > 0.5f -> c.accent
                else           -> c.accent
            }
            // Trend: compare today vs 7-day avg for this app
            val appAvg7 = (app.weekMinutes / 7f)
            val trendUp = app.todayMinutes > appAvg7 * 1.15f
            val trendDn = app.todayMinutes < appAvg7 * 0.85f

            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular ring
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.size(36.dp)) {
                            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            val sweep = ringAnim * 300f
                            // Background track
                            drawArc(
                                color = c.border2,
                                startAngle = 120f,
                                sweepAngle = 300f,
                                useCenter = false,
                                style = stroke
                            )
                            // Progress arc
                            drawArc(
                                color = ringColor,
                                startAngle = 120f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = stroke
                            )
                        }
                        // App initial
                        Text(
                            app.label.take(1).uppercase(),
                            color = c.text2,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Light
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // App name + category + session info
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label,
                            color = c.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(app.category, color = c.accentDim, fontSize = 9.sp)
                            if (app.avgSessionMinutes > 0) {
                                Text("·", color = c.text3, fontSize = 9.sp)
                                Text("~${fmt(app.avgSessionMinutes)}/session",
                                    color = c.text3, fontSize = 9.sp)
                            }
                            when {
                                trendUp -> Text("↑", color = c.red,   fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                trendDn -> Text("↓", color = c.green, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Opens
                    Text(
                        "${app.unlockCount}",
                        color = c.text2,
                        fontSize = 12.sp,
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.End
                    )

                    // Time — colored by intensity
                    Text(
                        fmt(app.todayMinutes),
                        color = if (appFrac > 0.7f) c.red else c.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(50.dp),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(12.dp))
                HLine(c)
            }
        }

        // ── 7. BOTTOM INSIGHTS STRIP ──────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(10.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BottomInsightCell(c, "$deepSess deep", "sessions")
                    Box(Modifier.width(1.dp).height(28.dp).background(c.border))
                    BottomInsightCell(c, "$totalShort checks", "under 2 min")
                    Box(Modifier.width(1.dp).height(28.dp).background(c.border))
                    BottomInsightCell(c, longestBreakStr, "longest break")
                    Box(Modifier.width(1.dp).height(28.dp).background(c.border))
                    BottomInsightCell(c, topCategory, "top category")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// TRENDS TAB
// ═══════════════════════════════════════════════════════════════

@Composable
fun TrendsTab(
    c: SchemeColors,
    scheme: ColorScheme,
    days: List<DaySummary>,
    apps: List<AppUsage>,
    lastWeekTotal: Long,
    onRefresh: () -> Unit
){
    val totalWeek  = days.takeLast(7).sumOf { it.minutes }
    val totalMonth = days.sumOf { it.minutes }
    val avg7       = if(days.size>=7) days.takeLast(7).dropLast(1).map{it.minutes}.average().toLong() else 0L
    val avg30      = days.map { it.minutes }.average().toLong()
    val maxDay     = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1L
    val nonZero    = days.filter { it.minutes > 0 }
    val best       = nonZero.minByOrNull { it.minutes }
    val worst      = nonZero.maxByOrNull { it.minutes }
    val streakDays = days.reversed().takeWhile { it.minutes < avg30 * 1.1 }.size

    // FIX 6: week-over-week delta
    val weekDelta     = totalWeek - lastWeekTotal
    val weekDeltaSign = if (weekDelta >= 0) "↑" else "↓"
    val weekDeltaCol  = if (weekDelta > 30) c.red else if (weekDelta < -30) c.green else c.text3

    // Day-of-week pattern
    val dowTotals = Array(7) { 0L }
    val dowCounts = Array(7) { 0 }
    days.forEach { d ->
        val cal = Calendar.getInstance()
        cal.time = SimpleDateFormat("d MMM", Locale.getDefault()).parse(d.date) ?: return@forEach
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
        dowTotals[dow] += d.minutes; dowCounts[dow]++
    }
    val dowAvg    = Array(7) { i -> if (dowCounts[i]>0) dowTotals[i]/dowCounts[i] else 0L }
    val dowLabels = listOf("Su","Mo","Tu","We","Th","Fr","Sa")
    val maxDow    = dowAvg.maxOrNull()?.coerceAtLeast(1) ?: 1L

    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(bottom=32.dp)) {
        item {
            ScreenHeader(c, "Trends", "30-day patterns & history", onRefresh)
            Spacer(Modifier.height(20.dp))
        }
        item {
            Column(Modifier.padding(horizontal=28.dp)) {

                // Summary cards
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    // FIX 6: this week card shows WoW delta
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(c.surface)
                        .border(1.dp,c.border,RoundedCornerShape(10.dp))
                        .padding(horizontal=14.dp, vertical=12.dp)) {
                        Text("THIS WEEK", color=c.text3, fontSize=9.sp, letterSpacing=1.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(fmt(totalWeek), color=c.accent, fontSize=20.sp, fontWeight=FontWeight.Light)
                        if (lastWeekTotal > 0) {
                            Text("$weekDeltaSign ${fmt(abs(weekDelta))} vs last week",
                                color=weekDeltaCol, fontSize=10.sp)
                        } else {
                            Text("7-day total", color=c.text3, fontSize=9.sp)
                        }
                    }
                    SummaryCard(c,"This month",fmt(totalMonth),"30-day total",Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SummaryCard(c,"7-day avg",fmt(avg7),"per day",Modifier.weight(1f))
                    SummaryCard(c,"30-day avg",fmt(avg30),"per day",Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    ExtremeCard(c,"Best day",best?.date?:"—",
                        fmt(best?.minutes?:0),c.green,Modifier.weight(1f))
                    ExtremeCard(c,"Worst day",worst?.date?:"—",
                        fmt(worst?.minutes?:0),c.red,Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    InsightCard(c,"Under-avg streak","$streakDays days",
                        "consecutive days at/below avg",Modifier.weight(1f))
                    InsightCard(c,"Last week total", fmt(lastWeekTotal),
                        "for comparison",Modifier.weight(1f))
                }

                Spacer(Modifier.height(28.dp))

                // 30-day bar chart — FIX 9: legend uses scheme's accentName
                SectionLabel(c, "30-DAY SCREEN TIME",
                    "${scheme.accentName} = today · muted = above avg")
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.dp,c.border,RoundedCornerShape(14.dp))
                    .padding(14.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().height(96.dp),
                            horizontalArrangement=Arrangement.spacedBy(3.dp),
                            verticalAlignment=Alignment.Bottom) {
                            days.forEachIndexed { i, d ->
                                val isToday  = i == days.lastIndex
                                val aboveAvg = d.minutes > avg30 * 1.2
                                val frac = (d.minutes.toFloat()/maxDay).coerceIn(0.02f,1f)
                                val anim by animateFloatAsState(frac,
                                    tween(250+i*8, easing=EaseOutCubic))
                                Box(Modifier.weight(1f).fillMaxHeight(anim)
                                    .clip(RoundedCornerShape(topStart=2.dp,topEnd=2.dp))
                                    .background(when {
                                        isToday  -> c.accent
                                        aboveAvg -> c.barAbove
                                        else     -> c.barNormal
                                    }))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(days.firstOrNull()?.date?:"", color=c.text3, fontSize=9.sp)
                            Text("today", color=c.accent, fontSize=9.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        HLine(c)
                        Spacer(Modifier.height(10.dp))
                        // FIX 9: legend labels use accentName not hardcoded "tan"
                        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            LegendDot(c.accent, "Today (${scheme.accentName})", c)
                            LegendDot(c.barAbove, "> 20% avg", c)
                            LegendDot(c.barNormal, "Normal", c)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Day-of-week pattern
                SectionLabel(c,"DAY-OF-WEEK PATTERN","your average per weekday")
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.dp,c.border,RoundedCornerShape(14.dp))
                    .padding(14.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().height(64.dp),
                            horizontalArrangement=Arrangement.spacedBy(6.dp),
                            verticalAlignment=Alignment.Bottom) {
                            dowAvg.forEachIndexed { i, avg ->
                                val frac = (avg.toFloat()/maxDow).coerceIn(0.02f,1f)
                                val anim by animateFloatAsState(frac,
                                    tween(400+i*50, easing=EaseOutCubic))
                                Column(Modifier.weight(1f),
                                    horizontalAlignment=Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth().height(52.dp),
                                        contentAlignment=Alignment.BottomCenter) {
                                        Box(Modifier.fillMaxWidth().fillMaxHeight(anim)
                                            .clip(RoundedCornerShape(topStart=3.dp,topEnd=3.dp))
                                            .background(if(avg==dowAvg.maxOrNull()) c.accent else c.barNormal))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(dowLabels[i], color=c.text3, fontSize=9.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HLine(c)
                        Spacer(Modifier.height(8.dp))
                        val heaviestDay = dowLabels[dowAvg.indices.maxByOrNull { dowAvg[it] } ?: 0]
                        Text("Heaviest day on average: $heaviestDay",
                            color=c.text3, fontSize=10.sp)
                    }
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel(c,"LIFETIME PER APP","total time ever recorded")
                Spacer(Modifier.height(4.dp))
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=28.dp, vertical=8.dp)) {
                Text("App · since", color=c.text3, fontSize=10.sp, modifier=Modifier.weight(1f))
                Text("Total hours", color=c.text3, fontSize=10.sp,
                    modifier=Modifier.width(80.dp), textAlign=TextAlign.End)
                Text("Lifetime", color=c.text3, fontSize=10.sp,
                    modifier=Modifier.width(60.dp), textAlign=TextAlign.End)
            }
            HLine(c)
        }

        items(apps.filter{it.lifetimeMinutes>0}.sortedByDescending{it.lifetimeMinutes}) { app ->
            Column(Modifier.fillMaxWidth().padding(horizontal=28.dp)) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end=8.dp)) {
                        Text(app.label, color=c.text, fontSize=14.sp,
                            fontWeight=FontWeight.Light, maxLines=1,
                            overflow=TextOverflow.Ellipsis)
                        Text("since ${fmtDate(app.firstSeen)}", color=c.text3, fontSize=10.sp)
                    }
                    Text("${app.lifetimeMinutes/60}h", color=c.text2, fontSize=13.sp,
                        modifier=Modifier.width(80.dp), textAlign=TextAlign.End)
                    Text(fmt(app.lifetimeMinutes), color=c.accent, fontSize=13.sp,
                        fontWeight=FontWeight.Medium,
                        modifier=Modifier.width(60.dp), textAlign=TextAlign.End)
                }
                Spacer(Modifier.height(14.dp))
                HLine(c)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HEAT TAB
// ═══════════════════════════════════════════════════════════════

@Composable
fun HeatTab(c: SchemeColors, apps: List<AppUsage>, onRefresh: () -> Unit) {
    val merged    = IntArray(24)
    apps.forEach { a -> a.hourlyMinutes.forEachIndexed { h, m -> merged[h] += m } }
    val maxHour   = merged.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peakHour  = merged.indices.maxByOrNull { merged[it] } ?: 0
    val activeHrs = merged.count { it > 0 }
    val quietHour = merged.indices.filter { merged[it]>0 }.minByOrNull { merged[it] }
    val timeFmt   = SimpleDateFormat("h a", Locale.getDefault())
    fun hourStr(h: Int) = timeFmt.format(
        Calendar.getInstance().also { it.set(Calendar.HOUR_OF_DAY, h) }.time).lowercase()
    val segs = listOf(
        "12 am–6 am" to 0..5, "6 am–12 pm" to 6..11,
        "12 pm–6 pm" to 12..17, "6 pm–12 am" to 18..23)

    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(bottom=32.dp)) {
        item {
            ScreenHeader(c, "Hours", "when are you on your phone?", onRefresh)
            Spacer(Modifier.height(20.dp))
        }
        item {
            Column(Modifier.padding(horizontal=28.dp)) {
                Text("Each cell = minutes used in that hour. Darker = more time.",
                    color=c.text3, fontSize=11.sp, lineHeight=17.sp)
                Spacer(Modifier.height(16.dp))

                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.dp,c.border,RoundedCornerShape(14.dp))
                    .padding(16.dp)) {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        segs.forEach { (label, range) ->
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(label, color=c.text3, fontSize=9.sp,
                                    modifier=Modifier.width(70.dp), letterSpacing=0.2.sp)
                                Row(Modifier.weight(1f),
                                    horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                    range.forEach { h ->
                                        val f = merged[h].toFloat() / maxHour
                                        val anim by animateFloatAsState(f,
                                            tween(300+h*20, easing=EaseOutCubic))
                                        Box(Modifier.weight(1f).aspectRatio(1f)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(c.accent.copy(alpha=0.06f+anim*0.88f)),
                                            contentAlignment=Alignment.Center) {
                                            if (merged[h]>0)
                                                Text("${merged[h]}", fontSize=7.sp,
                                                    color=if(f>0.6f) c.bg else c.accent.copy(alpha=0.9f))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        HLine(c)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("12 am", color=c.text3, fontSize=9.sp)
                            Text("6 am", color=c.text3, fontSize=9.sp)
                            Text("12 pm", color=c.text3, fontSize=9.sp)
                            Text("6 pm", color=c.text3, fontSize=9.sp)
                            Text("11 pm", color=c.text3, fontSize=9.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(c.surface)
                    .border(1.dp,c.border,RoundedCornerShape(12.dp))
                    .padding(vertical=16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly) {
                        MetricCell(c,"PEAK HOUR",hourStr(peakHour),"${merged[peakHour]}m used")
                        VDiv(c)
                        MetricCell(c,"ACTIVE","$activeHrs hrs","of 24")
                        VDiv(c)
                        MetricCell(c,"QUIETEST",
                            if(quietHour!=null) hourStr(quietHour) else "—",
                            "${if(quietHour!=null) merged[quietHour] else 0}m")
                    }
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel(c,"PER-APP HOURLY","top 5 apps — bars span midnight to 11 pm")
                Spacer(Modifier.height(12.dp))
            }
        }

        items(apps.take(5)) { app ->
            val appMax = app.hourlyMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1
            Column(Modifier.fillMaxWidth().padding(horizontal=28.dp, vertical=12.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(app.label, color=c.text, fontSize=13.sp,
                        fontWeight=FontWeight.Light, modifier=Modifier.weight(1f),
                        maxLines=1, overflow=TextOverflow.Ellipsis)
                    Text(fmt(app.todayMinutes), color=c.accent, fontSize=12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(20.dp),
                    horizontalArrangement=Arrangement.spacedBy(2.dp),
                    verticalAlignment=Alignment.Bottom) {
                    app.hourlyMinutes.forEach { m ->
                        val f = (m.toFloat()/appMax).coerceIn(0f,1f)
                        val anim by animateFloatAsState(f, tween(400, easing=EaseOutCubic))
                        Box(Modifier.weight(1f).fillMaxHeight(anim.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart=1.dp,topEnd=1.dp))
                            .background(if(m>0) c.accent.copy(alpha=0.4f+f*0.55f) else c.border2))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("12 am", color=c.text3, fontSize=8.sp)
                    Text("12 pm", color=c.text3, fontSize=8.sp)
                    Text("11 pm", color=c.text3, fontSize=8.sp)
                }
                Spacer(Modifier.height(12.dp))
                HLine(c)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ALL APPS TAB
// ═══════════════════════════════════════════════════════════════

@Composable
fun AllAppsTab(c: SchemeColors, apps: List<AppUsage>, onRefresh: () -> Unit) {
    var sortIdx by remember { mutableStateOf(0) }
    val sortOptions = listOf("Today","Week","Month","Lifetime","Opens")
    val sorted = remember(sortIdx, apps) {
        when(sortIdx) {
            0 -> apps.sortedByDescending { it.todayMinutes }
            1 -> apps.sortedByDescending { it.weekMinutes }
            2 -> apps.sortedByDescending { it.monthMinutes }
            3 -> apps.sortedByDescending { it.lifetimeMinutes }
            4 -> apps.sortedByDescending { it.unlockCount }
            else -> apps
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(bottom=32.dp)) {
        item {
            ScreenHeader(c, "Apps", "complete usage breakdown", onRefresh)
            Spacer(Modifier.height(20.dp))
        }
        item {
            Column(Modifier.padding(horizontal=28.dp)) {
                Text("Every app you used today, with all available stats.",
                    color=c.text3, fontSize=11.sp)
                Spacer(Modifier.height(14.dp))
                Text("SORT BY", color=c.text3, fontSize=9.sp, letterSpacing=1.5.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    sortOptions.forEachIndexed { i, label ->
                        val active = sortIdx==i
                        Box(Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if(active) c.accentFaint else c.surface)
                            .border(1.dp,
                                if(active) c.accentDim.copy(alpha=0.5f) else c.border,
                                RoundedCornerShape(8.dp))
                            .clickable { sortIdx=i }
                            .padding(horizontal=14.dp, vertical=8.dp)) {
                            Text(label,
                                color=if(active) c.accent else c.text3,
                                fontSize=12.sp,
                                fontWeight=if(active) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                    Text("App", color=c.text3, fontSize=10.sp, modifier=Modifier.weight(1f))
                    Text("Today",
                        color=if(sortIdx==0) c.accent else c.text3, fontSize=10.sp,
                        modifier=Modifier.width(44.dp), textAlign=TextAlign.End)
                    Text("Week",
                        color=if(sortIdx==1) c.accent else c.text3, fontSize=10.sp,
                        modifier=Modifier.width(44.dp), textAlign=TextAlign.End)
                    Text("Month",
                        color=if(sortIdx==2) c.accent else c.text3, fontSize=10.sp,
                        modifier=Modifier.width(48.dp), textAlign=TextAlign.End)
                }
                HLine(c)
            }
        }

        items(sorted) { app ->
            Column(Modifier.fillMaxWidth().padding(horizontal=28.dp)) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end=6.dp)) {
                        Text(app.label, color=c.text, fontSize=14.sp,
                            fontWeight=FontWeight.Light, maxLines=1,
                            overflow=TextOverflow.Ellipsis)
                        Text(app.category, color=c.accentDim, fontSize=10.sp)
                    }
                    Text(fmt(app.todayMinutes),
                        color=if(sortIdx==0) c.accent else c.text2, fontSize=13.sp,
                        modifier=Modifier.width(44.dp), textAlign=TextAlign.End)
                    Text(fmt(app.weekMinutes),
                        color=if(sortIdx==1) c.accent else c.text2, fontSize=13.sp,
                        modifier=Modifier.width(44.dp), textAlign=TextAlign.End)
                    Text(fmt(app.monthMinutes),
                        color=if(sortIdx==2) c.accent else c.text2, fontSize=13.sp,
                        modifier=Modifier.width(48.dp), textAlign=TextAlign.End)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(c.surface)
                    .padding(horizontal=10.dp, vertical=8.dp)) {
                    DenseCell(c,"Opens","${app.unlockCount}×",Modifier.weight(1f))
                    DenseCell(c,"Sessions","${app.sessionCount}",Modifier.weight(1f))
                    DenseCell(c,"Avg",fmt(app.avgSessionMinutes),Modifier.weight(1f))
                    DenseCell(c,"Longest",fmt(app.longestSessionMinutes),Modifier.weight(1f))
                    DenseCell(c,"Lifetime",fmt(app.lifetimeMinutes),Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text("First used ${fmtDate(app.firstSeen)}", color=c.text3, fontSize=9.sp)
                Spacer(Modifier.height(14.dp))
                HLine(c)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// THEME TAB
// ═══════════════════════════════════════════════════════════════

@Composable
fun ThemeTab(c: SchemeColors, current: ColorScheme, onPick: (ColorScheme) -> Unit) {
    val schemes = listOf(
        ColorScheme.TAN    to "Dark warm brown with gold-tan accent. The default Serein look.",
        ColorScheme.PAPER  to "Warm off-white with dark ink. Great for daytime reading.",
        ColorScheme.SLATE  to "Deep cool dark with a blue accent. Clean and analytical.",
        ColorScheme.FOREST to "Very dark green with sage accent. Calm and earthy.",
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(bottom=32.dp)) {
        item {
            // Theme tab has no refresh — no data to reload
            Column(Modifier.fillMaxWidth().padding(start=28.dp, end=28.dp, top=52.dp, bottom=0.dp)) {
                Row(verticalAlignment=                    Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                        .background(c.accentFaint), contentAlignment=Alignment.Center) {
                        Text("S", color=c.accent, fontSize=10.sp, fontWeight=FontWeight.Medium)
                    }
                    Text("SEREIN", color=c.accent, fontSize=9.sp,
                        letterSpacing=3.sp, fontWeight=FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text("Theme", color=c.text, fontSize=26.sp, fontWeight=FontWeight.Thin)
                Text("choose your color scheme", color=c.text3, fontSize=11.sp)
                Spacer(Modifier.height(16.dp))
                HLine(c)
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            Column(Modifier.padding(horizontal=28.dp)) {
                Text("Applies instantly. Persists across restarts.",
                    color=c.text3, fontSize=12.sp, lineHeight=18.sp)
                Spacer(Modifier.height(24.dp))
            }
        }
        items(schemes) { (scheme, desc) ->
            val sc     = scheme.colors()
            val active = scheme == current
            Box(Modifier.fillMaxWidth().padding(horizontal=28.dp, vertical=6.dp)) {
                Box(Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(sc.bg)
                    .border(2.dp, if(active) sc.accent else sc.border, RoundedCornerShape(14.dp))
                    .clickable { onPick(scheme) }
                    .padding(16.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                                    .background(sc.accentFaint)
                                    .border(1.dp,sc.accentDim.copy(alpha=0.4f),
                                        RoundedCornerShape(9.dp)),
                                    contentAlignment=Alignment.Center) {
                                    Text("S", color=sc.accent, fontSize=15.sp,
                                        fontWeight=FontWeight.Thin)
                                }
                                Column {
                                    Text(scheme.label, color=sc.text,
                                        fontSize=14.sp, fontWeight=FontWeight.Light)
                                    Text(if(active) "Active" else "Tap to apply",
                                        color=sc.accentDim, fontSize=10.sp)
                                }
                            }
                            if (active) Box(Modifier.size(8.dp)
                                .clip(RoundedCornerShape(4.dp)).background(sc.accent))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().height(28.dp),
                            horizontalArrangement=Arrangement.spacedBy(4.dp),
                            verticalAlignment=Alignment.Bottom) {
                            listOf(0.4f,0.65f,0.5f,0.85f,0.3f,0.7f,1f).forEach { h ->
                                Box(Modifier.weight(1f).fillMaxHeight(h)
                                    .clip(RoundedCornerShape(topStart=2.dp,topEnd=2.dp))
                                    .background(if(h==1f) sc.accent else sc.barNormal))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(desc, color=sc.text3, fontSize=11.sp, lineHeight=16.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
fun BottomInsightCell(c: SchemeColors, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = c.text2, fontSize = 11.sp, fontWeight = FontWeight.Light)
        Text(label, color = c.text3, fontSize = 8.sp)
    }
}

@Composable
fun HLine(c: SchemeColors) =
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

@Composable
fun VDiv(c: SchemeColors) =
    Box(Modifier.width(1.dp).height(36.dp).background(c.border))

@Composable
fun SectionLabel(c: SchemeColors, title: String, sub: String) {
    Text(title, color=c.text3, fontSize=10.sp,
        letterSpacing=1.5.sp, fontWeight=FontWeight.Medium)
    if (sub.isNotEmpty()) {
        Spacer(Modifier.height(2.dp))
        Text(sub, color=c.text3.copy(alpha=0.7f), fontSize=10.sp)
    }
}

@Composable
fun MetricCell(c: SchemeColors, label: String, value: String, sub: String) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text(label, color=c.text3, fontSize=8.sp, letterSpacing=1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color=c.text, fontSize=18.sp, fontWeight=FontWeight.Light)
        Text(sub, color=c.text3, fontSize=9.sp)
    }
}

@Composable
fun DenseCell(c: SchemeColors, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally) {
        Text(value, color=c.text2, fontSize=12.sp, fontWeight=FontWeight.Light)
        Text(label, color=c.text3, fontSize=9.sp)
    }
}

@Composable
fun InsightCard(c: SchemeColors, label: String, value: String, sub: String,
                modifier: Modifier = Modifier) {
    Column(modifier
        .clip(RoundedCornerShape(10.dp))
        .background(c.surface)
        .border(1.dp, c.border, RoundedCornerShape(10.dp))
        .padding(horizontal=14.dp, vertical=12.dp)) {
        Text(label.uppercase(), color=c.text3, fontSize=9.sp, letterSpacing=1.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color=c.accent, fontSize=20.sp, fontWeight=FontWeight.Light)
        Text(sub, color=c.text3, fontSize=9.sp)
    }
}

@Composable
fun SummaryCard(c: SchemeColors, label: String, value: String, sub: String,
                modifier: Modifier = Modifier) {
    Column(modifier
        .clip(RoundedCornerShape(10.dp))
        .background(c.surface)
        .border(1.dp, c.border, RoundedCornerShape(10.dp))
        .padding(horizontal=14.dp, vertical=12.dp)) {
        Text(label.uppercase(), color=c.text3, fontSize=9.sp, letterSpacing=1.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color=c.accent, fontSize=20.sp, fontWeight=FontWeight.Light)
        Text(sub, color=c.text3, fontSize=9.sp)
    }
}

@Composable
fun ExtremeCard(c: SchemeColors, label: String, date: String, value: String,
                col: Color, modifier: Modifier = Modifier) {
    Column(modifier
        .clip(RoundedCornerShape(10.dp))
        .background(c.surface)
        .border(1.dp, c.border, RoundedCornerShape(10.dp))
        .padding(horizontal=14.dp, vertical=12.dp)) {
        Text(label.uppercase(), color=c.text3, fontSize=9.sp, letterSpacing=1.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color=col, fontSize=20.sp, fontWeight=FontWeight.Light)
        Text(date, color=c.text3, fontSize=9.sp)
    }
}

@Composable
fun LegendDot(color: Color, label: String, c: SchemeColors) {
    Row(verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color=c.text3, fontSize=10.sp)
    }
}