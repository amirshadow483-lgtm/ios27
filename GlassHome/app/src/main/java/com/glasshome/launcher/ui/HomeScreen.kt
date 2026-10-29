package com.glasshome.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.glasshome.launcher.data.*
import com.glasshome.launcher.system.rememberBattery
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

class DragInfo(val item: HomeItem, val page: Int, val index: Int, start: Offset) {
    var pos by mutableStateOf(start)
    var travelled = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = LocalSettings.current
    val density = LocalDensity.current
    val pages = vm.layout.pages
    val pagerState = rememberPagerState { pages.size }
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    val dockRect = remember { mutableStateOf(Rect.Zero) }
    val gridOrigins = remember { mutableMapOf<Int, Offset>() }

    LaunchedEffect(vm.homeSignal) { if (vm.homeSignal > 0) pagerState.animateScrollToPage(0) }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var startY = 0f; var startX = 0f; var total = 0f
            val topZone = 90.dp.toPx(); val th = 70.dp.toPx()
            detectVerticalDragGestures(
                onDragStart = { o -> startX = o.x; startY = o.y; total = 0f },
                onDragEnd = {
                    if (drag == null && vm.overlay == Overlay.None && vm.menu == null && vm.openFolder == null) {
                        if (startY < topZone && total > th) {
                            if (startX < size.width / 2f) vm.show(Overlay.Notifs) else vm.show(Overlay.Control)
                        } else if (startY >= topZone && total < -th) vm.openLibrary(false)
                        else if (startY >= topZone && total > th) vm.openLibrary(true)
                    }
                },
                onVerticalDrag = { _, dy -> total += dy },
            )
        }
    ) {
        val gridPad = 12.dp
        val cellW = (maxWidth - gridPad * 2) / COLS
        val rowH = 96.dp
        val cellWpx = with(density) { cellW.toPx() }
        val rowHpx = with(density) { rowH.toPx() }
        val iconSize = 60.dp * s.iconScale

        fun finishDrag() {
            val d = drag ?: return
            drag = null
            if (d.travelled < with(density) { 12.dp.toPx() }) {
                (d.item as? HomeItem.App)?.let { vm.menu = Menu.ForApp(it.pkg, Where.Page, d.page) }
                (d.item as? HomeItem.Folder)?.let { vm.menu = Menu.ForFolder(it.id) }
                return
            }
            val p = d.pos
            if (d.item is HomeItem.App && dockRect.value.contains(p)) { vm.dropToDock(d.page, d.index); return }
            val g = gridOrigins[d.page] ?: return
            val items = vm.layout.pages.getOrNull(d.page) ?: return
            val rows = pageCapacity(d.page) / COLS
            val col = floor((p.x - g.x) / cellWpx).toInt().coerceIn(0, COLS - 1)
            val row = floor((p.y - g.y) / rowHpx).toInt().coerceIn(0, rows - 1)
            val t = (row * COLS + col).coerceAtMost(items.lastIndex)
            val cx = g.x + (col + 0.5f) * cellWpx
            val cy = g.y + (row + 0.5f) * rowHpx
            val near = hypot(p.x - cx, p.y - cy) < cellWpx * 0.28f
            if (t != d.index && near && d.item is HomeItem.App) vm.mergeIntoFolder(d.page, d.index, t)
            else vm.reorder(d.page, d.index, t)
        }

        HorizontalPager(pagerState, Modifier.fillMaxSize(), userScrollEnabled = drag == null) { p ->
            Box(Modifier.fillMaxSize().graphicsLayer {
                if (s.animLevel > 0) {
                    val off = abs((pagerState.currentPage - p) + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                    val k = s.animLevel / 2f
                    scaleX = 1f - 0.08f * off * k; scaleY = scaleX; alpha = 1f - 0.5f * off * k
                }
            }) {
                // empty-area long press -> home menu
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { vm.menu = Menu.Home }) })
                val items = pages.getOrNull(p) ?: emptyList()
                val rows = pageCapacity(p) / COLS
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 200.dp).padding(horizontal = gridPad)) {
                    if (p == 0) {
                        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ClockCard(Modifier.weight(1f).fillMaxHeight())
                            BatteryCard(Modifier.weight(1f).fillMaxHeight())
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(rowH * rows)
                        .onGloballyPositioned { gridOrigins[p] = it.positionInRoot() }) {
                        items.forEachIndexed { idx, item ->
                            key(item.key) {
                                val posRef = remember { floatArrayOf(0f, 0f) }
                                Box(
                                    Modifier.offset(cellW * (idx % COLS), rowH * (idx / COLS)).size(cellW, rowH)
                                        .onGloballyPositioned {
                                            val o = it.positionInRoot()
                                            posRef[0] = o.x + it.size.width / 2f; posRef[1] = o.y + it.size.height / 2f
                                        }
                                        .alpha(if (drag?.item?.key == item.key) 0f else 1f)
                                        .pointerInput(item.key, idx, p) { detectTapGestures(onTap = { vm.open(ctx, item) }) }
                                        .pointerInput(item.key, idx, p) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { drag = DragInfo(item, p, idx, Offset(posRef[0], posRef[1])) },
                                                onDrag = { c, a -> c.consume(); drag?.let { it.pos += a; it.travelled += a.getDistance() } },
                                                onDragEnd = { finishDrag() },
                                                onDragCancel = { finishDrag() },
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { ItemVisual(item, vm, iconSize) }
                            }
                        }
                    }
                }
            }
        }

        // bottom area: search pill, page dots, dock
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            GlassSurface(Modifier.height(36.dp).width(150.dp).clickable { vm.openLibrary(true) }, CircleShape) {
                Row(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), tint = textSecondary())
                    Spacer(Modifier.width(6.dp))
                    Text("Search", color = textSecondary(), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(pages.size) { i ->
                    Box(Modifier.size(7.dp).background(Color.White.copy(if (i == pagerState.currentPage) 0.95f else 0.35f), CircleShape))
                }
            }
            Spacer(Modifier.height(10.dp))
            val dockH = iconSize + 28.dp
            GlassSurface(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(dockH)
                    .onGloballyPositioned { dockRect.value = Rect(it.positionInRoot(), it.size.toSize()) },
                RoundedCornerShape(34.dp), strength = s.dockOpacity,
            ) {
                Row(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    vm.layout.dock.forEach { pkg ->
                        vm.byPkg[pkg]?.let { e ->
                            AppIconImage(e, iconSize, Modifier.pointerInput(pkg) {
                                detectTapGestures(onTap = { vm.launch(ctx, e) }, onLongPress = { vm.menu = Menu.ForApp(pkg, Where.Dock) })
                            })
                        }
                    }
                }
            }
        }

        // dragged item follows the finger
        drag?.let { d ->
            Box(Modifier.offset { IntOffset((d.pos.x - cellWpx / 2).roundToInt(), (d.pos.y - rowHpx / 2).roundToInt()) }
                .size(cellW, rowH).graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }, contentAlignment = Alignment.Center) {
                ItemVisual(d.item, vm, iconSize)
            }
        }
    }
}

@Composable
fun ItemVisual(item: HomeItem, vm: LauncherViewModel, iconSize: Dp) {
    val s = LocalSettings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (item) {
            is HomeItem.App -> {
                val e = vm.byPkg[item.pkg]
                if (e != null) {
                    AppIconImage(e, iconSize)
                    if (s.showLabels) { Spacer(Modifier.height(5.dp)); WhiteLabel(e.label) }
                }
            }
            is HomeItem.Folder -> {
                FolderIcon(item, vm.byPkg, iconSize)
                if (s.showLabels) { Spacer(Modifier.height(5.dp)); WhiteLabel(item.name) }
            }
        }
    }
}

@Composable
private fun ClockCard(modifier: Modifier) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(now))
    GlassSurface(modifier.clickable {
        com.glasshome.launcher.system.openFirst(ctx, android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
    }) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(day.uppercase(), color = Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(time, color = textPrimary(), fontSize = 46.sp, fontWeight = FontWeight.Light)
            Text(date, color = textSecondary(), fontSize = 14.sp)
        }
    }
}

@Composable
private fun BatteryCard(modifier: Modifier) {
    val b by rememberBattery()
    val level = b.first
    GlassSurface(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("BATTERY", color = Color(0xFF30D158), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.align(Alignment.CenterHorizontally).size(70.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = 7.dp.toPx()
                    drawArc(Color.White.copy(0.2f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                    drawArc(Color(0xFF30D158), -90f, 3.6f * level.coerceAtLeast(0), false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Text(if (level >= 0) "$level%" else "--", color = textPrimary(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(if (b.second) "Charging" else "On battery", color = textSecondary(), fontSize = 12.sp)
        }
    }
}
