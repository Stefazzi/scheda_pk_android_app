package it.stefazzi.pokerolesheets.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NovaChatClientTest {
    @Test
    fun `sends Supabase access token and decodes synthesis`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("Bearer player-token", request.headers[HttpHeaders.Authorization])
            assertEquals("https://nova.example/api/v1/chat", request.url.toString())
            respond(
                content = """{"synthesis":{"answer":"Tre successi [S1].","citation_ids":["S1"]}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(SupabaseProvider.json) }
        }
        val client = NovaChatClient("https://nova.example", { "player-token" }, http)

        assertEquals(NovaReply("Tre successi [S1].", listOf("S1")), client.ask("Conta"))
        client.close()
    }

    @Test
    fun `fails before network without authenticated session`() = runBlocking {
        val client = NovaChatClient(
            "https://nova.example",
            accessToken = { null },
            http = HttpClient(MockEngine { error("network must not be called") }),
        )

        val error = try {
            client.ask("Paralisi?")
            fail("NovaChatException expected")
            error("unreachable")
        } catch (error: NovaChatException) {
            error
        }
        assertEquals("Sessione scaduta: accedi nuovamente", error.message)
        client.close()
    }
}
