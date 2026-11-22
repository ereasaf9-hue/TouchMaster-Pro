package com.touchmaster.pro.macro

/** A source-located command in a parsed macro. */
sealed class MacroStep(open val line: Int) {
    data class Click(val x: Int, val y: Int, val durationMs: Long?, override val line: Int) : MacroStep(line)
    data class Press(val x: Int, val y: Int, val durationMs: Long, override val line: Int) : MacroStep(line)
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durationMs: Long, override val line: Int) : MacroStep(line)
    data class Delay(val durationMs: Long, override val line: Int) : MacroStep(line)
    data class LoopStart(val count: Long?, override val line: Int) : MacroStep(line) // null means forever
    data class LoopEnd(override val line: Int) : MacroStep(line)
    data class Stop(override val line: Int) : MacroStep(line)
    data class Comment(val text: String, override val line: Int) : MacroStep(line)
}

data class MacroError(val line: Int, val message: String) {
    override fun toString(): String = "Satır $line: $message"
}

data class MacroParseResult(val steps: List<MacroStep>, val errors: List<MacroError>) {
    val isSuccess: Boolean get() = errors.isEmpty()
}

data class MacroLimits(
    val maxX: Int,
    val maxY: Int,
    val maxLoopDepth: Int = 8,
    val maxLoopCount: Long = 1_000_000,
    val maxDurationMs: Long = 24L * 60 * 60 * 1000
) {
    init {
        require(maxX >= 0 && maxY >= 0) { "Coordinate maxima must be non-negative" }
        require(maxLoopDepth >= 1 && maxLoopCount >= 1 && maxDurationMs >= 1)
    }
}

/** Parses and validates the complete script; no partial script is executable on error. */
class MacroParser(private val limits: MacroLimits) {
    fun parse(source: String): MacroParseResult {
        val steps = mutableListOf<MacroStep>()
        val errors = mutableListOf<MacroError>()
        val loopLines = ArrayDeque<Int>()
        source.split('\n').forEachIndexed { index, raw ->
            val lineNo = index + 1
            val s = raw.trim().removeSuffix("\r").trim()
            if (s.isEmpty()) return@forEachIndexed
            if (s.startsWith('#')) {
                steps += MacroStep.Comment(s.drop(1).trim(), lineNo)
                return@forEachIndexed
            }
            val op = s.substringBefore(' ').uppercase()
            val tail = s.substringAfter(' ', "").trim()
            if (op == "COMMENT") {
                steps += MacroStep.Comment(tail, lineNo)
                return@forEachIndexed
            }
            val words = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
            fun usage(msg: String) { errors += MacroError(lineNo, msg) }
            fun nums(expected: Int): List<Long>? {
                if (words.size != expected + 1) { usage("$op komutu için hatalı argüman sayısı."); return null }
                val parsed = words.drop(1).map { it.toLongOrNull() }
                if (parsed.any { it == null }) { usage("$op komutunda sayısal olmayan veya aralık dışı değer var."); return null }
                return parsed.filterNotNull()
            }
            fun duration(value: Long): Boolean {
                if (value < 1 || value > limits.maxDurationMs) { usage("Süre 1 ile ${limits.maxDurationMs} ms arasında olmalı."); return false }
                return true
            }
            when (op) {
                "CLICK" -> {
                    if (words.size !in 3..4) { usage("CLICK kullanımı: CLICK x y [duration]"); return@forEachIndexed }
                    val a = words.drop(1).map { it.toLongOrNull() }
                    if (a.any { it == null }) { usage("CLICK koordinatları ve süresi tam sayı olmalı."); return@forEachIndexed }
                    val v = a.filterNotNull(); val x = v[0]; val y = v[1]; val d = v.getOrNull(2)
                    var ok = true
                    if (x < 0 || x > limits.maxX) { usage("X koordinatı 0..${limits.maxX} aralığında olmalı."); ok = false }
                    if (y < 0 || y > limits.maxY) { usage("Y koordinatı 0..${limits.maxY} aralığında olmalı."); ok = false }
                    if (d != null && !duration(d)) ok = false
                    if (ok) steps += MacroStep.Click(x.toInt(), y.toInt(), d, lineNo)
                }
                "PRESS" -> nums(3)?.let { v ->
                    var ok = true
                    if (v[0] < 0 || v[0] > limits.maxX) { usage("X koordinatı 0..${limits.maxX} aralığında olmalı."); ok = false }
                    if (v[1] < 0 || v[1] > limits.maxY) { usage("Y koordinatı 0..${limits.maxY} aralığında olmalı."); ok = false }
                    if (!duration(v[2])) ok = false
                    if (ok) steps += MacroStep.Press(v[0].toInt(), v[1].toInt(), v[2], lineNo)
                }
                "SWIPE" -> nums(5)?.let { v ->
                    var ok = true
                    listOf(v[0] to "X1", v[1] to "Y1", v[2] to "X2", v[3] to "Y2").forEach { (n, axis) ->
                        val max = if (axis.startsWith("X")) limits.maxX else limits.maxY
                        if (n < 0 || n > max) { usage("$axis koordinatı 0..$max aralığında olmalı."); ok = false }
                    }
                    if (!duration(v[4])) ok = false
                    if (ok) steps += MacroStep.Swipe(v[0].toInt(), v[1].toInt(), v[2].toInt(), v[3].toInt(), v[4], lineNo)
                }
                "DELAY" -> nums(1)?.let { if (duration(it[0])) steps += MacroStep.Delay(it[0], lineNo) }
                "LOOP" -> nums(1)?.let { v ->
                    if (v[0] < 1 || v[0] > limits.maxLoopCount) usage("Döngü sayısı 1..${limits.maxLoopCount} aralığında olmalı.")
                    else if (loopLines.size >= limits.maxLoopDepth) usage("Döngü iç içe geçme sınırı (${limits.maxLoopDepth}) aşıldı.")
                    else { loopLines.addLast(lineNo); steps += MacroStep.LoopStart(v[0], lineNo) }
                }
                "LOOP_FOREVER" -> {
                    if (words.size != 1) usage("LOOP_FOREVER argüman kabul etmez.")
                    else if (loopLines.size >= limits.maxLoopDepth) usage("Döngü iç içe geçme sınırı (${limits.maxLoopDepth}) aşıldı.")
                    else { loopLines.addLast(lineNo); steps += MacroStep.LoopStart(null, lineNo) }
                }
                "END_LOOP" -> {
                    if (words.size != 1) usage("END_LOOP argüman kabul etmez.")
                    else if (loopLines.isEmpty()) usage("Eşleşen LOOP veya LOOP_FOREVER yok.")
                    else { loopLines.removeLast(); steps += MacroStep.LoopEnd(lineNo) }
                }
                "STOP_MACRO" -> if (words.size != 1) usage("STOP_MACRO argüman kabul etmez.") else steps += MacroStep.Stop(lineNo)
                else -> usage("Bilinmeyen komut: $op")
            }
        }
        loopLines.forEach { errors += MacroError(it, "Bu döngü için END_LOOP eksik.") }
        return MacroParseResult(if (errors.isEmpty()) steps.toList() else emptyList(), errors.toList())
    }
}

/** Target-independent hooks; implementations can dispatch gestures and provide cancellable suspension. */
interface MacroRuntime {
    suspend fun perform(step: MacroStep)
    suspend fun delay(milliseconds: Long)
    /** Must suspend while paused and throw/cancel promptly when cancellation is requested. */
    suspend fun checkpoint()
}

/** Executes validated parser output; pause/resume and cancellation are delegated to runtime.checkpoint(). */
class MacroExecutor(private val runtime: MacroRuntime) {
    suspend fun execute(steps: List<MacroStep>) {
        val pairs = mutableMapOf<Int, Int>()
        val stack = ArrayDeque<Int>()
        steps.forEachIndexed { i, step ->
            when (step) {
                is MacroStep.LoopStart -> stack.addLast(i)
                is MacroStep.LoopEnd -> if (stack.isNotEmpty()) { val start = stack.removeLast(); pairs[start] = i; pairs[i] = start }
                else -> Unit
            }
        }
        require(stack.isEmpty()) { "Macro must be parsed and validated before execution" }
        suspend fun runRange(from: Int, until: Int) {
            var i = from
            while (i < until) {
                runtime.checkpoint()
                when (val s = steps[i]) {
                    is MacroStep.LoopStart -> {
                        val end = pairs[i] ?: error("Unmatched loop at line ${s.line}")
                        var iteration = 0L
                        while (s.count == null || iteration < s.count) {
                            runtime.checkpoint()
                            runRange(i + 1, end)
                            iteration++
                        }
                        i = end + 1
                    }
                    is MacroStep.LoopEnd -> error("Unexpected END_LOOP at line ${s.line}")
                    is MacroStep.Delay -> { runtime.delay(s.durationMs); i++ }
                    is MacroStep.Stop -> return
                    else -> { runtime.perform(s); i++ }
                }
            }
        }
        runRange(0, steps.size)
    }
}
