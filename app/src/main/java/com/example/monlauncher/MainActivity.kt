package com.example.monlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Launcher() } }
    }
}

suspend fun loadApps(ctx: Context): List<AppItem> = withContext(Dispatchers.Default) {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.loadIcon(pm).toBitmap(160, 160).asImageBitmap()
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

// Dock : téléphone, messages, navigateur, appareil photo (apps par défaut du téléphone)
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

fun launch(ctx: Context, app: AppItem) {
    ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.let(ctx::startActivity)
}

private val labelStyle = TextStyle(
    color = Color.White, fontSize = 11.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)
)

@Composable
fun AppCell(app: AppItem, showLabel: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(app.icon, app.label, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(app.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Launcher() {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var searching by remember { mutableStateOf(false) }
    val date = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()) }

    LaunchedEffect(Unit) { apps = loadApps(ctx) }
    val dock = remember(apps) { dockApps(ctx, apps) }
    val pages = remember(apps, dock) { apps.filter { a -> dock.none { it.pkg == a.pkg } }.chunked(24) }
    val pager = rememberPagerState(pageCount = { maxOf(pages.size, 1) })

    BackHandler(enabled = searching) { searching = false }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
            Text(
                date.replaceFirstChar { it.uppercase() },
                style = labelStyle.copy(fontSize = 15.sp),
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp)
            )
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                val page = pages.getOrElse(i) { emptyList() }
                Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    page.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AppCell(app) { launch(ctx, app) }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            // Points de pagination
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pager.pageCount) { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(7.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.4f))
                    )
                }
            }
            // Bouton de recherche
            Box(
                Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable { searching = true }
                    .padding(horizontal = 28.dp, vertical = 8.dp)
            ) { Text("Rechercher", style = labelStyle.copy(fontSize = 13.sp)) }
            Spacer(Modifier.height(12.dp))
            // Dock
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = 0.22f)).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) { dock.forEach { AppCell(it, showLabel = false) { launch(ctx, it) } } }
            Spacer(Modifier.height(8.dp))
        }
        if (searching) SearchOverlay(apps, onClose = { searching = false })
    }
}

@Composable
fun SearchOverlay(apps: List<AppItem>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = if (query.isBlank()) emptyList() else apps.filter { it.label.contains(query, true) }

    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f))
            .statusBarsPadding().imePadding().padding(16.dp)
    ) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = { Text("Rechercher") }, singleLine = true,
            shape = RoundedCornerShape(50)
        )
        LazyColumn(Modifier.padding(top = 12.dp)) {
            items(results, key = { it.pkg }) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable { launch(ctx, app); onClose() }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(app.icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}
