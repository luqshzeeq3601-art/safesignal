package com.safesignal.kpi

import java.io.File

/**
 * Main CLI entry point for SafeSignal KPI calculation.
 * Executable via `.\gradlew.bat :kpi:run --args="..."`.
 */
fun main(args: Array<String>) {
    var appEventsPath: String? = null
    var serverEventsPath: String? = null
    var batteryPath: String? = null
    var outputDirPath: String = "build/kpi"
    var labFalsePositives = 0
    var labTotalTraces = 9

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--app-events", "--kpi-events" -> {
                if (i + 1 < args.size) appEventsPath = args[++i]
            }
            "--server-events" -> {
                if (i + 1 < args.size) serverEventsPath = args[++i]
            }
            "--battery" -> {
                if (i + 1 < args.size) batteryPath = args[++i]
            }
            "--output-dir" -> {
                if (i + 1 < args.size) outputDirPath = args[++i]
            }
            "--lab-fp" -> {
                if (i + 1 < args.size) labFalsePositives = args[++i].toIntOrNull() ?: 0
            }
            "--lab-total" -> {
                if (i + 1 < args.size) labTotalTraces = args[++i].toIntOrNull() ?: 9
            }
        }
        i++
    }

    println("=== SafeSignal KPI Evaluation Engine ===")
    println("Output Directory: $outputDirPath")

    val appEvents = appEventsPath?.let { File(it) }?.takeIf { it.exists() }?.let {
        println("Reading App Events from: ${it.absolutePath}")
        KpiParser.parseAppEvents(it)
    } ?: emptyList()

    val serverEvents = serverEventsPath?.let { File(it) }?.takeIf { it.exists() }?.let {
        println("Reading Server Events from: ${it.absolutePath}")
        KpiParser.parseServerEvents(it)
    } ?: emptyList()

    val batteryRecord = batteryPath?.let { File(it) }?.takeIf { it.exists() }?.let {
        println("Reading Battery Log from: ${it.absolutePath}")
        KpiParser.parseBatteryCsv(it)
    }

    val summary = KpiCalculator.calculate(
        appEvents = appEvents,
        serverEvents = serverEvents,
        batteryRecord = batteryRecord,
        labFalsePositives = labFalsePositives,
        labTotalTraces = labTotalTraces
    )

    val outputDir = File(outputDirPath)
    outputDir.mkdirs()

    val markdownFile = File(outputDir, "summary.md")
    val markdownContent = KpiCalculator.formatMarkdown(summary)
    markdownFile.writeText(markdownContent)
    println("Generated Markdown Report: ${markdownFile.absolutePath}")

    val csvFile = File(outputDir, "summary.csv")
    val csvContent = KpiCalculator.formatCsv(summary)
    csvFile.writeText(csvContent)
    println("Generated CSV Report: ${csvFile.absolutePath}")

    println("\n" + markdownContent)
}
