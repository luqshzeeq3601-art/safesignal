package com.safesignal.bot

import com.safesignal.protocol.AckRequest
import com.safesignal.protocol.AlertApi
import com.safesignal.protocol.RecordStatus
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * JVM CLI runner for fake worker bot.
 */
fun main(args: Array<String>): Unit {
    runBlocking {
        var serverUrl = "http://localhost:8080"
    var count = 5
    var workerId = "W-BOT-1"
    var simulateRetries = 1
    var autoAck = false
    var kpiCsv: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--server" -> {
                serverUrl = args.getOrNull(i + 1) ?: serverUrl
                i += 2
            }
            "--count" -> {
                count = args.getOrNull(i + 1)?.toIntOrNull() ?: count
                i += 2
            }
            "--worker-id" -> {
                workerId = args.getOrNull(i + 1) ?: workerId
                i += 2
            }
            "--simulate-retries" -> {
                simulateRetries = args.getOrNull(i + 1)?.toIntOrNull() ?: simulateRetries
                i += 2
            }
            "--auto-ack" -> {
                autoAck = args.getOrNull(i + 1)?.toBooleanStrictOrNull() ?: true
                i += 2
            }
            "--kpi-csv" -> {
                kpiCsv = args.getOrNull(i + 1)
                i += 2
            }
            else -> i++
        }
    }

    println("=== SafeSignal Worker Bot ===")
    println("Server URL: $serverUrl")
    println("Alert count: $count")
    println("Worker ID: $workerId")
    println("Simulated retries: $simulateRetries")
    println("Auto-acknowledgement: $autoAck")

    val api = AlertApi.create(CIO.create(), baseUrl = serverUrl)
    val dispatcherScope = CoroutineScope(Dispatchers.Default)
    var ackJob: Job? = null

    if (autoAck) {
        ackJob = dispatcherScope.launch {
            println("[Dispatcher] Background auto-acknowledgement loop started...")
            while (isActive) {
                val alertsRes = api.getAlerts()
                if (alertsRes.isSuccess) {
                    val alerts = alertsRes.getOrThrow()
                    for (alert in alerts) {
                        if (alert.status == RecordStatus.RECEIVED) {
                            println("[Dispatcher] Acknowledging alert ${alert.alert.alertId}...")
                            api.acknowledgeAlert(alert.alert.alertId, AckRequest("AUTO_DISPATCHER"))
                        }
                    }
                }
                delay(200)
            }
        }
    }

    val bot = WorkerBot(api = api, workerId = workerId)
    val report = bot.runShift(alertCount = count, simulateInitialFailures = simulateRetries)

    ackJob?.cancel()
    api.close()

    kpiCsv?.let { path ->
        try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            file.printWriter().use { pw ->
                pw.println("epochMs,elapsedMs,alertId,event,detail")
                for (evt in report.kpiEvents) {
                    pw.println("${evt.epochMs},${evt.elapsedMs},${evt.alertId},${evt.event},${evt.detail}")
                }
            }
            println("KPI telemetry written to: $path (${report.kpiEvents.size} events)")
        } catch (e: Exception) {
            System.err.println("Failed to write KPI CSV to $path: ${e.message}")
        }
    }

    println("\n=== Final Bot Summary ===")
    println(report.summaryString())

    val success = report.deliveredCount == count &&
            report.acknowledgedCount == count &&
            report.retriesCount > 0

        if (success) {
            println("SUCCESS: ${report.summaryString()}")
            exitProcess(0)
        } else {
            System.err.println("FAILURE: Criteria not met (${report.summaryString()})")
            exitProcess(1)
        }
    }
}

