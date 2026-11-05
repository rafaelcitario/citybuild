package com.skyline.idle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        game = Game(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GameScreen(game)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        game.save()
    }
}

/* ======================= Utilidades ======================= */

private val Violet = Color(0xFF8B5CF6)
private val Pink = Color(0xFFEC4899)
private val Cyan = Color(0xFF22D3EE)

private fun Modifier.glass(radius: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(Color(0xFF0B1030).copy(alpha = 0.72f), Color(0xFF1B1446).copy(alpha = 0.62f)),
            ),
        )
        .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
}

fun fmt(v: Double): String {
    if (v < 1000.0) return if (v < 10.0) "%.1f".format(v) else "%.0f".format(v)
    val units = listOf("K", "M", "B", "T", "Qa", "Qi")
    var x = v
    var i = -1
    while (x >= 1000.0 && i < units.size - 1) {
        x /= 1000.0
        i++
    }
    return "%.2f%s".format(x, units[i])
}

private fun fmtDur(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}

private fun hourFraction(): Float {
    val t = LocalTime.now()
    return t.hour + t.minute / 60f
}

private fun nightFactor(h: Float): Float {
    val d = (sin((h - 6f) / 24f * 2f * PI.toFloat()) + 1f) / 2f
    return ((0.5f - d) * 2.5f + 0.5f).coerceIn(0f, 1f)
}

/* ======================= Tela principal ======================= */

@Composable
private fun GameScreen(game: Game) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val view = LocalView.current

    LaunchedEffect(Unit) {
        while (true) {
            game.tick()
            delay(1000)
        }
    }
    DisposableEffect(game.keepAwake) {
        view.keepScreenOn = game.keepAwake
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize()) {
        CityCanvas(game, selected) { selected = if (selected == it) -1 else it }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            Hud(game)
            Spacer(Modifier.weight(1f))
            BottomPanel(game, selected) { selected = -1 }
        }
        game.report?.let { ReportDialog(it) { game.report = null } }
    }
}

@Composable
private fun Hud(game: Game) {
    @Suppress("UNUSED_VARIABLE") val ver = game.version
    val lvl = game.cityLevel()
    val next = if (lvl < THRESHOLDS.size - 1) THRESHOLDS[lvl + 1] else null
    val prog = if (next == null) 1f
    else ((game.total - THRESHOLDS[lvl]) / (next - THRESHOLDS[lvl])).toFloat().coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxWidth()
            .glass(24.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "🪙 ${fmt(game.coins)}",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "+${fmt(game.income())}/s",
                    color = Color(0xFF86EFAC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "👥 ${fmt(game.population().toDouble())}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (game.legacy > 0) {
                    Text(
                        "✨ Legado ${game.legacy}",
                        color = Color(0xFFF0ABFC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${LEVEL_NAMES[lvl]} · Nv ${lvl + 1}",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(prog.coerceAtLeast(0.001f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Cyan, Violet, Pink))),
            )
        }
    }
}

@Composable
private fun BottomPanel(game: Game, selected: Int, onClose: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = game.version
    Column(
        Modifier
            .fillMaxWidth()
            .glass(24.dp)
            .padding(14.dp),
    ) {
        if (selected >= 0) PlotPanel(game, selected, onClose) else Controls(game)
    }
}

@Composable
private fun Toggle(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(if (on) Violet.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$text ${if (on) "ON" else "OFF"}",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GradientButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Violet, Pink)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Controls(game: Game) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Toggle("🤖 Prefeito", game.auto, Modifier.weight(1f)) { game.auto = !game.auto }
        Toggle("🌙 Modo 24h", game.keepAwake, Modifier.weight(1f)) { game.keepAwake = !game.keepAwake }
    }
    val gain = game.legacyGain()
    if (gain >= 1) {
        Spacer(Modifier.height(8.dp))
        GradientButton("♻️ Renascer  (+$gain ✨)") { game.prestige() }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Toque num lote · arraste e use pinça para mover e dar zoom",
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlotPanel(game: Game, idx: Int, onClose: () -> Unit) {
    val lvl = game.cityLevel()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Lote ${idx % GRID + 1}×${idx / GRID + 1}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "✕",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .padding(horizontal = 10.dp),
        )
    }
    Spacer(Modifier.height(6.dp))
    when {
        !game.isUnlocked(idx) -> Text(
            "🔒 Lote bloqueado. Evolua a cidade para liberar.",
            color = Color.White,
            fontSize = 15.sp,
        )

        game.types[idx] == 0 -> Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TYPES.forEach { t ->
                val locked = t.unlock > lvl
                val cost = game.buildCost(t)
                val can = !locked && game.coins >= cost
                Column(
                    Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = if (can) 0.14f else 0.05f))
                        .border(
                            1.dp,
                            if (can) Cyan.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(18.dp),
                        )
                        .clickable(enabled = can) { game.build(idx, t) }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.emoji, fontSize = 28.sp)
                    Text(
                        t.name,
                        color = Color.White.copy(alpha = if (locked) 0.4f else 1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (locked) "🔒 Nv ${t.unlock + 1}" else "🪙 ${fmt(cost)}",
                        color = if (can) Color(0xFF86EFAC) else Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        else -> {
            val t = game.typeOf(idx)
            val l = game.levels[idx]
            Text(
                "${t.emoji} ${t.name} · nv $l/$MAX_LEVEL",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "+${fmt(incomeOf(t, l) * game.multiplier())}/s",
                color = Color(0xFF86EFAC),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (l < MAX_LEVEL) {
                GradientButton("Melhorar · 🪙 ${fmt(game.upgradeCost(idx))}") { game.upgrade(idx) }
            } else {
                Text("⭐ Nível máximo", color = Color(0xFFFDE68A), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReportDialog(r: Report, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .glass(28.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Bem-vindo de volta! 🌆", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "Você ficou fora por ${fmtDur(r.seconds)}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "🪙 +${fmt(r.earned)}",
                color = Color(0xFF86EFAC),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "O prefeito fez ${r.actions} melhorias sozinho",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            GradientButton("Coletar", onClick = onDismiss)
        }
    }
}

/* ======================= Renderização isométrica ======================= */

private class Geo(w: Float, h: Float) {
    val tw = w / GRID * 0.98f
    val th = tw * 0.5f
    val cx = w / 2f
    val oy = h * 0.52f - (GRID - 1) * th / 2f
    fun x(c: Int, r: Int) = cx + (c - r) * tw / 2f
    fun y(c: Int, r: Int) = oy + (c + r) * th / 2f
}

private fun hitTest(p: Offset, box: IntSize, zoom: Float, offset: Offset): Int {
    if (box.width == 0) return -1
    val g = Geo(box.width.toFloat(), box.height.toFloat())
    val w = (p - offset) / zoom
    val u = (w.x - g.cx) / (g.tw / 2f)
    val v = (w.y - g.oy) / (g.th / 2f)
    val c = Math.round((u + v) / 2f)
    val r = Math.round((v - u) / 2f)
    return if (c in 0 until GRID && r in 0 until GRID) r * GRID + c else -1
}

@Composable
private fun CityCanvas(game: Game, selected: Int, onTap: (Int) -> Unit) {
    val anim by rememberInfiniteTransition(label = "t").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val path = remember { Path() }
    val stars = remember {
        val r = Random(7)
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.55f, r.nextFloat()) }
    }
    @Suppress("UNUSED_VARIABLE") val ver = game.version
    val hourF = remember(ver) { hourFraction() }
    val night = remember(ver) { nightFactor(hourF) }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val ns = (zoom * gestureZoom).coerceIn(0.6f, 3.2f)
                    val z = ns / zoom
                    offset = (offset - centroid) * z + centroid + pan
                    zoom = ns
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val idx = hitTest(p, boxSize, zoom, offset)
                    if (idx >= 0) onTap(idx)
                }
            },
    ) {
        @Suppress("UNUSED_VARIABLE") val dv = game.version
        val g = Geo(size.width, size.height)
        drawSky(night, stars, hourF)
        withTransform({
            translate(offset.x, offset.y)
            scale(zoom, zoom, Offset.Zero)
        }) {
            drawCity(game, g, selected, anim, night, path)
        }
    }
}

private fun Color.dim(f: Float) = Color(red * f, green * f, blue * f, alpha)
private fun Color.nt(n: Float) = lerp(this, Color(0xFF0A0E2A), n * 0.6f)

private fun DrawScope.drawSky(night: Float, stars: List<Triple<Float, Float, Float>>, hourF: Float) {
    val topDay = Color(0xFF3B82F6)
    val botDay = Color(0xFFBAE6FD)
    val topNight = Color(0xFF050816)
    val botNight = Color(0xFF2E1F5E)
    drawRect(Brush.verticalGradient(listOf(lerp(topDay, topNight, night), lerp(botDay, botNight, night))))
    if (night > 0.2f) {
        for ((sx, sy, ss) in stars) {
            drawCircle(
                Color.White.copy(alpha = night * (0.3f + 0.7f * ss)),
                radius = 1.2f + 2f * ss,
                center = Offset(sx * size.width, sy * size.height),
            )
        }
    }
    val cyc = ((hourF - 6f + 24f) % 24f) / 12f
    val isDay = cyc < 1f
    val f = if (isDay) cyc else cyc - 1f
    val bx = size.width * (0.1f + 0.8f * f)
    val by = size.height * 0.2f - sin(f * PI.toFloat()) * size.height * 0.1f
    val c = Offset(bx, by)
    if (isDay) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFF3B0).copy(alpha = 0.8f), Color.Transparent), center = c, radius = 150f),
            radius = 150f, center = c,
        )
        drawCircle(Color(0xFFFFE066), radius = 38f, center = c)
    } else {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFBFDBFE).copy(alpha = 0.45f), Color.Transparent), center = c, radius = 120f),
            radius = 120f, center = c,
        )
        drawCircle(Color(0xFFE2E8F0), radius = 30f, center = c)
        drawCircle(Color(0xFFCBD5E1), radius = 7f, center = Offset(bx - 9f, by - 6f))
        drawCircle(Color(0xFFCBD5E1), radius = 5f, center = Offset(bx + 10f, by + 8f))
    }
}

private fun DrawScope.box(
    p: Path, x: Float, y: Float, a: Float, b: Float, h: Float, base: Color, n: Float,
) {
    p.rewind(); p.moveTo(x - a, y); p.lineTo(x, y + b); p.lineTo(x, y + b - h); p.lineTo(x - a, y - h); p.close()
    drawPath(p, base.dim(0.80f).nt(n))
    p.rewind(); p.moveTo(x + a, y); p.lineTo(x, y + b); p.lineTo(x, y + b - h); p.lineTo(x + a, y - h); p.close()
    drawPath(p, base.dim(0.60f).nt(n))
    p.rewind(); p.moveTo(x, y - b - h); p.lineTo(x + a, y - h); p.lineTo(x, y + b - h); p.lineTo(x - a, y - h); p.close()
    drawPath(p, base.nt(n))
}

private fun DrawScope.diamond(
    p: Path, x: Float, y: Float, a: Float, b: Float, color: Color, style: DrawStyle = Fill,
) {
    p.rewind(); p.moveTo(x, y - b); p.lineTo(x + a, y); p.lineTo(x, y + b); p.lineTo(x - a, y); p.close()
    drawPath(p, color, style = style)
}

private fun DrawScope.windows(
    x: Float, y: Float, a: Float, b: Float, h: Float, floors: Int, n: Float, glass: Color, lit: Color,
) {
    val col = lerp(glass, lit, n)
    val sw = minOf(h / floors * 0.38f, a * 0.14f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(a * 0.15f, a * 0.13f))
    for (k in 0 until floors) {
        val hk = h * (k + 0.6f) / floors
        drawLine(
            col.copy(alpha = 0.85f),
            Offset(x - a * 0.88f, y + b * 0.12f - hk), Offset(x - a * 0.12f, y + b * 0.88f - hk),
            strokeWidth = sw, pathEffect = dash,
        )
        drawLine(
            col.copy(alpha = 0.6f),
            Offset(x + a * 0.12f, y + b * 0.88f - hk), Offset(x + a * 0.88f, y + b * 0.12f - hk),
            strokeWidth = sw, pathEffect = dash,
        )
    }
}

private val HOUSES = listOf(Color(0xFFFDE7C0), Color(0xFFBFD9FF), Color(0xFFFBCFE8), Color(0xFFC7F2D0))
private val ROOFS = listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFF8B5CF6), Color(0xFF14B8A6))
private val SHOPS = listOf(Color(0xFFFBBF24), Color(0xFFF472B6), Color(0xFF34D399))
private val PARK_OFFS = listOf(
    -0.16f to 0.0f, 0.16f to 0.02f, 0.0f to -0.14f, 0.0f to 0.12f, -0.12f to 0.1f, 0.1f to -0.08f,
)
private val PARK_SETS = (2..6).map { cnt -> PARK_OFFS.take(cnt).sortedBy { it.second } }

/** Desenha um prédio e devolve o Y do ponto mais alto (para o marcador de nível máximo). */
private fun DrawScope.building(
    p: Path, type: Int, level: Int, idx: Int, x: Float, y: Float, tw: Float, th: Float, t: Float, n: Float,
): Float {
    val lit = Color(0xFFFFD866)
    when (type) {
        1 -> {
            val a = tw * 0.30f
            val b = th * 0.30f
            val h = tw * (0.15f + 0.02f * level)
            val wall = HOUSES[idx % HOUSES.size]
            val roof = ROOFS[(idx / 3) % ROOFS.size]
            box(p, x, y, a, b, h, wall, n)
            val apex = y - h - tw * 0.16f
            p.rewind(); p.moveTo(x - a, y - h); p.lineTo(x, y + b - h); p.lineTo(x, apex); p.close()
            drawPath(p, roof.dim(0.85f).nt(n))
            p.rewind(); p.moveTo(x + a, y - h); p.lineTo(x, y + b - h); p.lineTo(x, apex); p.close()
            drawPath(p, roof.dim(0.62f).nt(n))
            if (n > 0.3f) {
                drawCircle(lit.copy(alpha = n), tw * 0.02f, Offset(x - a * 0.5f, y + b * 0.5f - h * 0.5f))
            }
            return apex
        }

        2 -> {
            val a = tw * 0.36f
            val b = th * 0.36f
            val h = tw * (0.18f + 0.03f * level)
            val c = SHOPS[idx % SHOPS.size]
            box(p, x, y, a, b, h, Color(0xFFE2E8F0), n)
            box(p, x, y - h, a * 0.9f, b * 0.9f, tw * 0.045f, c, n)
            windows(x, y, a, b, h, 1 + level / 4, n, Color(0xFF7DD3FC), lit)
            return y - h - tw * 0.06f
        }

        3 -> {
            box(p, x, y, tw * 0.46f, th * 0.46f, tw * 0.02f, Color(0xFF4ADE80), n)
            val count = minOf(2 + level / 2, 6)
            for ((dx, dy) in PARK_SETS[count - 2]) {
                val px = x + dx * tw
                val py = y + dy * th
                drawLine(Color(0xFF7C4A21).nt(n), Offset(px, py), Offset(px, py - tw * 0.06f), strokeWidth = tw * 0.02f)
                drawCircle(Color(0xFF16A34A).nt(n), tw * 0.07f, Offset(px, py - tw * 0.09f))
                drawCircle(Color(0xFF4ADE80).nt(n), tw * 0.04f, Offset(px - tw * 0.015f, py - tw * 0.105f))
            }
            return y - tw * 0.2f
        }

        4 -> {
            val a = tw * 0.42f
            val b = th * 0.42f
            val h = tw * (0.16f + 0.018f * level)
            box(p, x, y, a, b, h, Color(0xFF94A3B8), n)
            box(p, x - a * 0.35f, y - h + b * 0.1f, a * 0.45f, b * 0.45f, tw * 0.05f, Color(0xFF64748B), n)
            windows(x, y, a, b, h, 1, n, Color(0xFFFDE68A), lit)
            val cxm = x + a * 0.25f
            val cym = y - h - b * 0.25f
            val chH = tw * 0.26f
            box(p, cxm, cym, tw * 0.055f, th * 0.055f, chH, Color(0xFF64748B), n)
            val topY = cym - chH
            for (s in 0 until 3) {
                val ph = (t * 10f + s / 3f) % 1f
                drawCircle(
                    Color(0xFFCBD5E1).copy(alpha = (1f - ph) * 0.55f),
                    radius = tw * (0.035f + 0.06f * ph),
                    center = Offset(cxm + ph * tw * 0.12f, topY - ph * tw * 0.5f),
                )
            }
            return topY - tw * 0.2f
        }

        5 -> {
            val a = tw * 0.30f
            val b = th * 0.30f
            val h = tw * (0.55f + 0.11f * level)
            val glass = Color(0xFF60A5FA)
            box(p, x, y, a, b, h, glass, n)
            windows(x, y, a, b, h, 3 + level, n, Color(0xFFDBEAFE), lit)
            var top = y - h
            if (level >= 6) {
                box(p, x, y - h, a * 0.62f, b * 0.62f, tw * 0.2f, Color(0xFF93C5FD), n)
                top -= tw * 0.2f
            }
            drawLine(Color(0xFF94A3B8).nt(n), Offset(x, top), Offset(x, top - tw * 0.22f), strokeWidth = tw * 0.012f)
            val blink = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 10f + idx)
            drawCircle(Color(0xFFEF4444).copy(alpha = blink), tw * 0.025f, Offset(x, top - tw * 0.22f))
            return top - tw * 0.24f
        }

        6 -> {
            val a = tw * 0.26f
            val b = th * 0.26f
            val h = tw * (0.85f + 0.13f * level)
            box(p, x, y, a, b, h, Color(0xFF1E293B), n)
            windows(x, y, a, b, h, 4 + level, 0f, Cyan, Cyan)
            drawLine(Cyan.copy(alpha = 0.85f), Offset(x, y + b), Offset(x, y + b - h), strokeWidth = tw * 0.014f)
            val pulse = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 10f + idx)
            val r = tw * (0.16f + 0.05f * pulse)
            val c = Offset(x, y - h - tw * 0.03f)
            drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.7f), Color.Transparent), center = c, radius = r), r, c)
            drawCircle(Color.White, tw * 0.02f, c)
            return c.y - tw * 0.05f
        }

        else -> {
            val a = tw * 0.42f
            val b = th * 0.42f
            box(p, x, y, a, b, tw * 0.07f, Color(0xFF4C1D95), n)
            val r = tw * (0.28f + 0.012f * level)
            val c = Offset(x, y - tw * 0.07f - r * 0.55f)
            val pulse = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 10f + idx)
            val halo = r * (1.7f + 0.2f * pulse)
            drawCircle(
                Brush.radialGradient(listOf(Pink.copy(alpha = 0.45f), Color.Transparent), center = c, radius = halo),
                halo, c,
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFF0ABFC), Color(0xFF8B5CF6), Color(0xFF312E81)),
                    center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.6f,
                ),
                r, c,
            )
            return c.y - r
        }
    }
}

private fun DrawScope.drawCity(
    game: Game, g: Geo, selected: Int, t: Float, night: Float, path: Path,
) {
    val tw = g.tw
    val th = g.th

    // Base (placa) da cidade
    val pcy = g.oy + (GRID - 1) * th / 2f
    val slab = tw * 0.22f
    box(
        path, g.cx, pcy + slab, GRID * tw / 2f + tw * 0.12f, GRID * th / 2f + th * 0.12f, slab,
        Color(0xFF1E293B), night,
    )

    // Chão
    for (r in 0 until GRID) {
        for (c in 0 until GRID) {
            val idx = r * GRID + c
            val color = when {
                !game.isUnlocked(idx) -> Color(0xFF0F172A)
                (r + c) % 2 == 0 -> Color(0xFF2B3A5E)
                else -> Color(0xFF33456F)
            }
            diamond(path, g.x(c, r), g.y(c, r), tw / 2f, th / 2f, color.nt(night))
        }
    }

    // Prédios, de trás para frente
    for (s in 0..2 * (GRID - 1)) {
        for (c in maxOf(0, s - (GRID - 1))..minOf(s, GRID - 1)) {
            val r = s - c
            val idx = r * GRID + c
            val type = game.types[idx]
            if (type == 0) continue
            val level = game.levels[idx]
            val x = g.x(c, r)
            val y = g.y(c, r)
            val top = building(path, type, level, idx, x, y, tw, th, t, night)
            if (level >= MAX_LEVEL) {
                val bob = sin(t * 2f * PI.toFloat() * 10f + idx) * tw * 0.015f
                val sy = top - tw * 0.1f + bob
                val star = tw * 0.04f
                path.rewind()
                path.moveTo(x, sy - star); path.lineTo(x + star, sy); path.lineTo(x, sy + star); path.lineTo(x - star, sy); path.close()
                drawPath(path, Color(0xFFFDE047))
            }
        }
    }

    // Seleção
    if (selected in 0 until CELLS) {
        val c = selected % GRID
        val r = selected / GRID
        val pulse = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 20f)
        diamond(
            path, g.x(c, r), g.y(c, r), tw / 2f, th / 2f,
            Color.White.copy(alpha = 0.55f + 0.45f * pulse), Stroke(width = tw * 0.025f),
        )
    }
}
