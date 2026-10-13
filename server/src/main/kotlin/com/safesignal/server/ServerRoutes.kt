package com.safesignal.server

import com.safesignal.protocol.AckRequest
import com.safesignal.protocol.AlertMessage
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Registers all REST endpoints and static console route for SafeSignal server.
 */
fun Application.configureRoutes(store: AlertStore) {
    val consoleHtml by lazy {
        val stream = object {}.javaClass.classLoader.getResourceAsStream("console/index.html")
            ?: error("console/index.html not found on classpath")
        stream.bufferedReader().use { it.readText() }
    }

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("OK", ContentType.Text.Plain)
        }

        // Dispatcher console Web UI
        get("/") {
            call.respondText(consoleHtml, ContentType.Text.Html)
        }

        route("/api/alerts") {
            // Ingest emergency alert
            post {
                try {
                    val alert = call.receive<AlertMessage>()
                    if (alert.alertId.isBlank() || alert.workerId.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "alertId and workerId are required"))
                        return@post
                    }

                    val result = store.put(alert)
                    when (result) {
                        is AlertStore.PutResult.Created -> {
                            call.respond(HttpStatusCode.Created, result.record)
                        }
                        is AlertStore.PutResult.Duplicate -> {
                            call.respond(HttpStatusCode.OK, result.record)
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid payload")))
                }
            }

            // List all alerts newest first (polled by dispatcher console)
            get {
                call.respond(HttpStatusCode.OK, store.getAll())
            }

            // Get single alert by ID (polled by worker app for ack)
            get("/{id}") {
                val id = call.parameters["id"]
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing alert id"))
                    return@get
                }
                val record = store.get(id)
                if (record != null) {
                    call.respond(HttpStatusCode.OK, record)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Alert not found"))
                }
            }

            // Acknowledge alert by dispatcher
            post("/{id}/ack") {
                val id = call.parameters["id"]
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing alert id"))
                    return@post
                }
                try {
                    val request = call.receive<AckRequest>()
                    val record = store.acknowledge(id, request.dispatcher)
                    if (record != null) {
                        call.respond(HttpStatusCode.OK, record)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Alert not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid ack payload")))
                }
            }
        }
    }
}
