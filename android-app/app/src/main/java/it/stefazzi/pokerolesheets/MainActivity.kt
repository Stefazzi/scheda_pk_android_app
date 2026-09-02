package it.stefazzi.pokerolesheets

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.stefazzi.pokerolesheets.data.SupabaseProvider
import it.stefazzi.pokerolesheets.ui.PokeroleApp
import it.stefazzi.pokerolesheets.ui.theme.PokeroleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseProvider.handleDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            PokeroleTheme {
                PokeroleApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SupabaseProvider.handleDeepLink(intent)
    }
}
