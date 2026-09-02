package it.stefazzi.pokerolesheets.data

import android.content.Intent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import it.stefazzi.pokerolesheets.BuildConfig
import kotlinx.serialization.json.Json

object SupabaseProvider {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        check(isConfigured) { "Configurazione Supabase mancante" }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth) {
                scheme = AUTH_SCHEME
                host = AUTH_HOST
                flowType = FlowType.PKCE
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
            install(Postgrest) {
                requireValidSession = true
            }
            install(Storage)
        }
    }

    fun repository(): CharacterRepository = CharacterRepository(client, json)

    fun handleDeepLink(intent: Intent) {
        if (isConfigured) client.handleDeeplinks(intent)
    }

    const val AUTH_SCHEME = "pokerolesheets"
    const val AUTH_HOST = "login-callback"
    const val AUTH_REDIRECT_URL = "$AUTH_SCHEME://$AUTH_HOST"
}
