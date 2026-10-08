package it.stefazzi.pokerolesheets.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class NovaReply(val requestId: String, val answer: String, val citations: List<String>)

class NovaChatException(message: String) : RuntimeException(message)

class NovaChatClient(
    baseUrl: String,
    private val accessToken: () -> String?,
    private val http: HttpClient = defaultHttpClient(),
) {
    private val endpoint = "${baseUrl.trimEnd('/')}/api/v1/chat"
    private val feedbackEndpoint = "${baseUrl.trimEnd('/')}/api/v1/feedback"

    init {
        require(baseUrl.isNotBlank()) { "Configurazione API Nova mancante" }
    }

    suspend fun ask(message: String, context: List<String> = emptyList()): NovaReply {
        val token = accessToken()?.takeIf(String::isNotBlank)
            ?: throw NovaChatException("Sessione scaduta: accedi nuovamente")
        val response = http.post(endpoint) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            val prompt = (context.takeLast(6) + "Domanda attuale: ${message.trim()}")
                .joinToString("\n")
                .takeLast(4_000)
            setBody(ChatRequest(prompt))
        }
        if (!response.status.isSuccess()) {
            val error = runCatching { response.body<ApiErrorEnvelope>().error.message }.getOrNull()
            throw NovaChatException(error ?: "Nova non è disponibile")
        }
        val chat = response.body<ChatResponse>()
        val synthesis = chat.synthesis
            ?: throw NovaChatException("Nova non ha prodotto una risposta completa")
        return NovaReply(chat.requestId, synthesis.answer, synthesis.citationIds)
    }

    suspend fun sendFeedback(requestId: String, useful: Boolean) {
        val token = accessToken()?.takeIf(String::isNotBlank)
            ?: throw NovaChatException("Sessione scaduta: accedi nuovamente")
        val response = http.post(feedbackEndpoint) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ChatFeedbackRequest(requestId, useful))
        }
        if (!response.status.isSuccess()) throw NovaChatException("Feedback non inviato")
    }

    fun close() = http.close()

    companion object {
        private fun defaultHttpClient() = HttpClient(Android) {
            install(ContentNegotiation) { json(SupabaseProvider.json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}

@Serializable
private data class ChatRequest(val message: String)

@Serializable
private data class ChatResponse(
    @SerialName("request_id") val requestId: String,
    val synthesis: ChatSynthesis? = null,
)

@Serializable
private data class ChatFeedbackRequest(
    @SerialName("request_id") val requestId: String,
    val useful: Boolean,
)

@Serializable
private data class ChatSynthesis(
    val answer: String,
    @SerialName("citation_ids") val citationIds: List<String> = emptyList(),
)

@Serializable
private data class ApiErrorEnvelope(val error: ApiError)

@Serializable
private data class ApiError(val message: String)
