package com.safesignal.protocol

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor HTTP client wrapper for dispatching emergency alerts and polling acknowledgements.
 * Accepts an injected HttpClient or engine for seamless mocking in tests.
 */
class AlertApi(
    val client: HttpClient,
    val baseUrl: String = "http://localhost:8080"
) : AutoCloseable {

    suspend fun postAlert(alert: AlertMessage): Result<AlertRecord> = runCatching {
        client.post("$baseUrl/api/alerts") {
            contentType(ContentType.Application.Json)
            setBody(alert)
        }.body()
    }

    suspend fun getAlert(alertId: String): Result<AlertRecord> = runCatching {
        client.get("$baseUrl/api/alerts/$alertId").body()
    }

    suspend fun getAlerts(): Result<List<AlertRecord>> = runCatching {
        client.get("$baseUrl/api/alerts").body()
    }

    suspend fun acknowledgeAlert(alertId: String, request: AckRequest): Result<AlertRecord> = runCatching {
        client.post("$baseUrl/api/alerts/$alertId/ack") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun checkHealth(): Result<String> = runCatching {
        client.get("$baseUrl/health").body()
    }

    override fun close() {
        client.close()
    }

    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun create(
            engine: HttpClientEngine,
            baseUrl: String = "http://localhost:8080",
            json: Json = defaultJson
        ): AlertApi {
            val httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(json)
                }
            }
            return AlertApi(httpClient, baseUrl)
        }
    }
}
