package com.example.monlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

class MainActivity : ComponentActivity() {
    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { Launcher(homeSignal.intValue) }
        }
    }

    override fun onResume() {
        super.onResume()
        homeSignal.intValue++
    }
}

fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

fun initial(a: AppItem): Char =
    norm(a.label).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

suspend fun loadApps(ctx: Context): List<AppItem> = withContext(Dispatchers.Default) {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.loadIcon(pm).toBitmap(192, 192).asImageBitmap()
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { norm(it.label).lowercase() }
}

fun dockApps(ctx: Context, apps: List<AppItem>): List<AppItem> {
    val pm = ctx.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    )
    return intents
        .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
        .distinct()
        .mapNotNull { p -> apps.find { it.pkg == p } }
}

fun openApp(ctx: Context, app: AppItem) {
    ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.let(ctx::startActivity)
}

private val labelStyle = TextStyle(
    color = Color.White, fontSize = 20.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
)

@Composable
fun IosIcon(app: AppItem, size: Dp) {
    val shape = RoundedCornerShape(percent = 23)
    Image(
        app.icon, null,
        Modifier.size(size)
            .shadow(8.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .border(0.6.dp, Color.White.copy(alpha = 0.28f), shape)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppItem,
    textSize: TextUnit,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.93f else 1f, label = "press")
    Row(
        modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = press
                scaleY = press
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosIcon(app, iconSize)
        Spacer(Modifier.width(16.dp))
        Text(app.label, style = labelStyle.copy(fontSize = textSize))
    }
}

@Composable
fun Rail(onLetter: (Char) -> Unit, onHome: () -> Unit) {
    val letters = remember { listOf('★', '#') + ('A'..'Z').toList() }
    val haptic = LocalHapticFeedback.current
    var h by remember { mutableIntStateOf(1) }
    var active by remember { mutableIntStateOf(-1) }

    fun pick(y: Float) {
        val i = (y / h * letters.size).toInt().coerceIn(0, letters.size - 1)
        if (i != active) {
            active = i
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val c = letters[i]
            if (c == '★') onHome() else onLetter(c)
        }
    }

    Column(
        Modifier.fillMaxHeight().width(40.dp)
            .onSizeChanged { h = it.height }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onVerticalDrag = { change, _ -> pick(change.position.y) }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pick(it.y)
                    tryAwaitRelease()
                    active = -1
                })
            },
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEachIndexed { i, c ->
            val dist = if (active < 0) 9 else abs(i - active)
            val target = when (dist) {
                0 -> 2.0f
                1 -> 1.5f
                2 -> 1.2f
                else -> 1f
            }
            val s by animateFloatAsState(target, label = "letter")
            Text(
                c.toString(),
                color = Color.White.copy(alpha = if (dist < 3) 1f else 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = s
                    scaleY = s
                    translationX = -(s - 1f) * 40f
                }
            )
        }
    }
}

@Composable
fun Launcher(homeSignal: Int) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var favs by remember {
        mutableStateOf(prefs.getString("favs", null)?.split(",")?.filter { it.isNotEmpty() })
    }
    var showAll by remember { mutableStateOf(false) }
    var letter by remember { mutableStateOf<Char?>(null) }
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val clockFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val allA by animateFloatAsState(if (showAll) 1f else 0f, label = "all")

    fun goHome() { showAll = false; letter = null }
    fun saveFavs(l: List<String>) {
        favs = l
        prefs.edit().putString("favs", l.joinToString(",")).apply()
    }
    fun toggle(app: AppItem) {
        val cur = favs ?: emptyList()
        if (app.pkg in cur) {
            saveFavs(cur - app.pkg)
            Toast.makeText(ctx, "Retiré des favoris", Toast.LENGTH_SHORT).show()
        } else {
            saveFavs(cur + app.pkg)
            Toast.makeText(ctx, "Ajouté aux favoris", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) { apps = loadApps(ctx) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            battery = (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(15_000)
        }
    }
    LaunchedEffect(apps) {
        if (favs == null && apps.isNotEmpty()) saveFavs(dockApps(ctx, apps).map { it.pkg })
    }
    LaunchedEffect(homeSignal) { goHome() }
    LaunchedEffect(letter) {
        letter?.let { c ->
            val i = apps.indexOfFirst { initial(it) >= c }
            listState.scrollToItem(if (i < 0) maxOf(apps.lastIndex, 0) else i)
        }
    }
    BackHandler(enabled = showAll) { goHome() }

    val favApps = (favs ?: emptyList()).mapNotNull { p -> apps.find { it.pkg == p } }

    Row(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .statusBarsPadding().navigationBarsPadding()
            .padding(start = 28.dp, end = 4.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (allA < 1f) {
                Column(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - allA
                        val s = 1f - 0.06f * allA
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        clockFmt.format(now),
                        style = labelStyle.copy(fontSize = 64.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        dateFmt.format(now).replaceFirstChar { it.uppercase() } + "  ·  " + battery + "%",
                        style = labelStyle.copy(fontSize = 15.sp)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        if (favApps.isEmpty()) {
                            Text(
                                "Glisse sur les lettres à droite, puis appuie longuement sur une appli pour l'ajouter ici.",
                                style = labelStyle.copy(fontSize = 14.sp)
                            )
                        }
                        favApps.forEach { app ->
                            AppRow(app, 22.sp, 56.dp, Modifier, { openApp(ctx, app) }, { toggle(app) })
                        }
                    }
                }
            }
            if (allA > 0f) {
                LazyColumn(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = allA
                        val s = 0.94f + 0.06f * allA
                        scaleX = s
                        scaleY = s
                    },
                    state = listState,
                    contentPadding = PaddingValues(vertical = 100.dp)
                ) {
                    items(apps, key = { it.pkg }) { app ->
                        val fx = Modifier.graphicsLayer {
                            val info = listState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.key == app.pkg }
                            if (item != null) {
                                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val itemCenter = item.offset + item.size / 2f
                                val d = (abs(itemCenter - center) / center.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                val d2 = d * d
                                val s = 1f - 0.22f * d2
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.65f * d2
                                translationX = -60f * d2
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                        }
                        AppRow(app, 20.sp, 52.dp, fx, { openApp(ctx, app); goHome() }, { toggle(app) })
                    }
                }
            }
        }
        Rail(onLetter = { c -> showAll = true; letter = c }, onHome = { goHome() })
    }
}
