package com.touchmaster.pro.engine

data class MacroStep(val command: String, val args: List<String>, val line: Int)
class MacroParseException(message: String): Exception(message)
object MacroParser {
    fun parse(source: String): List<MacroStep> = source.lines().mapIndexedNotNull { index, raw ->
        val line = raw.substringBefore('#').trim(); if (line.isEmpty()) return@mapIndexedNotNull null
        val tokens = line.split(Regex("\\s+")); val cmd = tokens.first().uppercase()
        val valid = setOf("CLICK","MULTI_CLICK","PRESS","MULTI_PRESS","SWIPE","DELAY","LOOP","LOOP_FOREVER","END_LOOP","STOP_MACRO","VIBRATE","COMMENT","SET")
        if (cmd !in valid) throw MacroParseException("Satır ${index + 1}: bilinmeyen komut $cmd")
        MacroStep(cmd, tokens.drop(1), index + 1)
    }
}
