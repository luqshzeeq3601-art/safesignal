package com.safesignal.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import java.io.File

/**
 * SafeSignal server application module and Netty server entry point.
 */
fun Application.module(
    store: AlertStore = AlertStore(
        jsonlFile = File("build/server/alerts.jsonl"),
        kpiCsvFile = File("build/server/server_events.csv")
    )
) {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
        })
    }
    configureRoutes(store)
}

fun main(args: Array<String>) {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"

    println("Starting SafeSignal Dispatcher Server on $host:$port...")
    embeddedServer(Netty, port = port, host = host) {
        module()
    }.start(wait = true)
}
