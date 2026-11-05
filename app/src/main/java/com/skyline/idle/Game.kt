package com.skyline.idle

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

const val GRID = 8
const val CELLS = GRID * GRID
const val MAX_LEVEL = 10

/** Limite de progresso offline (24h). */
const val MAX_OFFLINE_SEC = 24 * 3600.0

data class BType(
    val id: Int,
    val name: String,
    val emoji: String,
    val baseCost: Double,
    val baseIncome: Double,
    val pop: Int,
    val unlock: Int,
)

val TYPES = listOf(
    BType(1, "Casa", "🏠", 15.0, 0.25, 4, 0),
    BType(2, "Loja", "🏪", 2_400.0, 1.6, 0, 1),
    BType(3, "Parque", "🌳", 20_000.0, 8.0, 0, 2),
    BType(4, "Fábrica", "🏭", 96_000.0, 45.0, 0, 2),
    BType(5, "Arranha-céu", "🏙️", 1_200_000.0, 300.0, 40, 3),
    BType(6, "Torre Tech", "🛰️", 24_000_000.0, 2_500.0, 80, 4),
    BType(7, "Mega Cúpula", "🔮", 640_000_000.0, 26_000.0, 200, 5),
)

val LEVEL_NAMES = listOf(
    "Vilarejo", "Cidade pequena", "Metrópole", "Capital", "Megacidade", "Distrito Neon",
)
val THRESHOLDS = doubleArrayOf(0.0, 3_200.0, 96_000.0, 2_400_000.0, 64_000_000.0, 2_000_000_000.0)

fun incomeOf(t: BType, level: Int): Double = t.baseIncome * level * 1.10.pow(level - 1)

data class Report(val seconds: Long, val earned: Double, val actions: Int)

class Game(context: Context) {
    private val prefs = context.getSharedPreferences("skyline", Context.MODE_PRIVATE)

    // Ordem de desbloqueio dos lotes: do centro para as bordas.
    private val order: IntArray =
        (0 until CELLS)
            .sortedWith(compareBy<Int>({ abs(it % GRID - 3.5) + abs(it / GRID - 3.5) }, { it }))
            .toIntArray()
    private val rank = IntArray(CELLS).also { r -> order.forEachIndexed { pos, cell -> r[cell] = pos } }

    val types = IntArray(CELLS)
    val levels = IntArray(CELLS)
    var coins = 15.0
    var total = 0.0
    private var lifetime = 0.0

    var legacy by mutableIntStateOf(0)
    var auto by mutableStateOf(true)
    var keepAwake by mutableStateOf(true)
    var version by mutableIntStateOf(0)
    var report by mutableStateOf<Report?>(null)

    private var lastMs = System.currentTimeMillis()
    private var ticksSinceSave = 0

    init {
        prefs.getString("types", null)?.split(",")?.forEachIndexed { i, s ->
            if (i < CELLS) types[i] = s.toIntOrNull() ?: 0
        }
        prefs.getString("levels", null)?.split(",")?.forEachIndexed { i, s ->
            if (i < CELLS) levels[i] = s.toIntOrNull() ?: 0
        }
        coins = prefs.getString("coins", null)?.toDoubleOrNull() ?: 15.0
        total = prefs.getString("total", null)?.toDoubleOrNull() ?: 0.0
        legacy = prefs.getInt("legacy", 0)
        auto = prefs.getBoolean("auto", true)
        keepAwake = prefs.getBoolean("keep", true)
        lastMs = prefs.getLong("last", System.currentTimeMillis())
    }

    fun save() {
        prefs.edit()
            .putString("types", types.joinToString(","))
            .putString("levels", levels.joinToString(","))
            .putString("coins", coins.toString())
            .putString("total", total.toString())
            .putInt("legacy", legacy)
            .putBoolean("auto", auto)
            .putBoolean("keep", keepAwake)
            .putLong("last", lastMs)
            .apply()
    }

    /* ---------- Consultas ---------- */

    fun typeOf(i: Int): BType = TYPES[types[i] - 1]

    fun cityLevel(): Int {
        var l = 0
        for (i in THRESHOLDS.indices) if (total >= THRESHOLDS[i]) l = i
        return l
    }

    private fun unlockedCount(): Int = minOf(CELLS, 10 + cityLevel() * 12)

    fun isUnlocked(i: Int): Boolean = rank[i] < unlockedCount()

    private fun countOf(id: Int): Int {
        var n = 0
        for (i in 0 until CELLS) if (types[i] == id) n++
        return n
    }

    fun buildCost(t: BType): Double = t.baseCost * 1.15.pow(countOf(t.id))

    fun upgradeCost(i: Int): Double = typeOf(i).baseCost * 2.0.pow(levels[i])

    fun multiplier(): Double {
        var park = 0.0
        for (i in 0 until CELLS) if (types[i] == 3) park += 0.03 * levels[i]
        return (1.0 + minOf(park, 1.5)) * (1.0 + 0.15 * legacy)
    }

    private fun rawIncome(): Double {
        var s = 0.0
        for (i in 0 until CELLS) if (types[i] != 0) s += incomeOf(typeOf(i), levels[i])
        return s
    }

    fun income(): Double = rawIncome() * multiplier()

    fun population(): Int {
        var s = 0
        for (i in 0 until CELLS) if (types[i] != 0) s += typeOf(i).pop * levels[i]
        return s
    }

    fun legacyGain(): Int = floor(sqrt(total / 10_000_000_000.0)).toInt()

    /* ---------- Ações do jogador ---------- */

    fun build(i: Int, t: BType): Boolean {
        if (types[i] != 0 || !isUnlocked(i) || t.unlock > cityLevel()) return false
        val c = buildCost(t)
        if (coins < c) return false
        coins -= c
        types[i] = t.id
        levels[i] = 1
        version++
        return true
    }

    fun upgrade(i: Int): Boolean {
        if (types[i] == 0 || levels[i] >= MAX_LEVEL) return false
        val c = upgradeCost(i)
        if (coins < c) return false
        coins -= c
        levels[i]++
        version++
        return true
    }

    fun prestige() {
        val gain = legacyGain()
        if (gain < 1) return
        legacy += gain
        types.fill(0)
        levels.fill(0)
        coins = 15.0
        total = 0.0
        version++
    }

    /* ---------- Prefeito automático ---------- */

    private fun firstEmpty(): Int {
        val limit = unlockedCount()
        for (pos in 0 until limit) {
            val cell = order[pos]
            if (types[cell] == 0) return cell
        }
        return -1
    }

    private fun weakest(): Int {
        var best = -1
        var bestInc = Double.MAX_VALUE
        for (i in 0 until CELLS) {
            if (types[i] == 0 || types[i] == 3) continue
            val inc = incomeOf(typeOf(i), levels[i])
            if (inc < bestInc) {
                bestInc = inc
                best = i
            }
        }
        return best
    }

    private fun isMaxed(): Boolean {
        for (i in 0 until CELLS) if (types[i] == 0 || levels[i] < MAX_LEVEL) return false
        return true
    }

    /** Escolhe sempre a melhor ação (maior retorno por moeda) e espera juntar o dinheiro. */
    private fun autoStep(maxActions: Int): Int {
        var done = 0
        while (done < maxActions) {
            val lvl = cityLevel()
            val cur = rawIncome()
            var bestRoi = 0.0
            var kind = 0
            var bi = -1
            var bt: BType? = null
            var bestCost = 0.0

            for (i in 0 until CELLS) {
                if (types[i] == 0 || levels[i] >= MAX_LEVEL) continue
                val t = typeOf(i)
                val cost = upgradeCost(i)
                var delta = incomeOf(t, levels[i] + 1) - incomeOf(t, levels[i])
                if (t.id == 3) delta += 0.03 * cur
                val roi = delta / cost
                if (roi > bestRoi) {
                    bestRoi = roi; kind = 1; bi = i; bestCost = cost
                }
            }

            val empty = firstEmpty()
            if (empty >= 0) {
                for (t in TYPES) {
                    if (t.unlock > lvl) continue
                    val cost = buildCost(t)
                    var gain = incomeOf(t, 1)
                    if (t.id == 3) gain += 0.03 * cur
                    val roi = gain / cost
                    if (roi > bestRoi) {
                        bestRoi = roi; kind = 2; bi = empty; bt = t; bestCost = cost
                    }
                }
            } else {
                val weak = weakest()
                if (weak >= 0) {
                    val wi = incomeOf(typeOf(weak), levels[weak])
                    for (t in TYPES) {
                        if (t.unlock > lvl || t.id == types[weak]) continue
                        val gain = incomeOf(t, 1) - wi
                        if (gain <= wi * 0.3) continue
                        val cost = buildCost(t)
                        val roi = gain / cost
                        if (roi > bestRoi) {
                            bestRoi = roi; kind = 3; bi = weak; bt = t; bestCost = cost
                        }
                    }
                }
            }

            if (kind == 0 || bestCost > coins) break
            coins -= bestCost
            when (kind) {
                1 -> levels[bi]++
                else -> {
                    types[bi] = bt!!.id
                    levels[bi] = 1
                }
            }
            done++
        }
        return done
    }

    /* ---------- Simulação ---------- */

    private fun advance(seconds: Double): Int {
        var actions = 0
        var left = seconds
        val step = maxOf(1.0, seconds / 3000.0)
        while (left > 0.0) {
            val dt = minOf(step, left)
            val inc = income() * dt
            coins += inc
            total += inc
            lifetime += inc
            left -= dt
            if (auto) {
                actions += autoStep(12)
                if (isMaxed() && legacyGain() >= 1) prestige()
            }
        }
        return actions
    }

    /** Chamado a cada segundo. Também recupera qualquer tempo em que o app ficou parado. */
    fun tick() {
        val now = System.currentTimeMillis()
        var dt = (now - lastMs) / 1000.0
        lastMs = now
        if (dt <= 0.0) return
        if (dt > MAX_OFFLINE_SEC) dt = MAX_OFFLINE_SEC
        if (dt > 90.0) {
            val before = lifetime
            val actions = advance(dt)
            report = Report(dt.toLong(), lifetime - before, actions)
        } else {
            advance(dt)
        }
        if (++ticksSinceSave >= 10) {
            save()
            ticksSinceSave = 0
        }
        version++
    }
}
