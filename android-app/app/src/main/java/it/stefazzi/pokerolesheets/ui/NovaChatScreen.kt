package it.stefazzi.pokerolesheets.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.stefazzi.pokerolesheets.R

@Composable
fun NovaChatScreen(
    configured: Boolean,
    messages: List<NovaChatMessage>,
    loading: Boolean,
    onSend: (String) -> Unit,
    onFeedback: (String, Boolean) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val expression = novaExpression(messages, loading)
    val portrait = when (expression) {
        NovaExpression.HAPPY -> R.drawable.nova_happy
        NovaExpression.NEUTRAL -> R.drawable.nova_neutral
        NovaExpression.SAD -> R.drawable.nova_sad
        NovaExpression.SKEPTICAL -> R.drawable.nova_skeptical
        NovaExpression.THINKING -> R.drawable.nova_thinking
        NovaExpression.ANGRY -> R.drawable.nova_angry
        NovaExpression.CONFUSED -> R.drawable.nova_confused
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(portrait),
            contentDescription = "Nova: ${expression.name.lowercase()}",
            modifier = Modifier.fillMaxWidth().weight(0.45f),
            contentScale = ContentScale.Fit,
        )
        Text("Nova", style = MaterialTheme.typography.headlineSmall)
        if (!configured) {
            Text("Configura POKEROLE_API_BASE_URL in local.properties per usare Nova.")
            return
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (message.fromUser) "Tu" else "Nova", style = MaterialTheme.typography.labelMedium)
                        Text(message.text)
                        if (message.citations.isNotEmpty()) {
                            Text(message.citations.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                        }
                        message.requestId?.let { requestId ->
                            if (message.feedbackSent) {
                                Text("Grazie per il feedback", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Row {
                                    TextButton(onClick = { onFeedback(requestId, true) }) { Text("Utile") }
                                    TextButton(onClick = { onFeedback(requestId, false) }) { Text("Non utile") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Chiedi a Nova") },
                modifier = Modifier.weight(1f),
                enabled = !loading,
            )
            Button(
                enabled = input.isNotBlank() && !loading,
                onClick = {
                    onSend(input)
                    input = ""
                },
            ) {
                if (loading) CircularProgressIndicator() else Text("Invia")
            }
        }
    }
}
