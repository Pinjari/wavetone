package com.wavetone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PairingScreen(controller: WavetoneController, onBack: () -> Unit) {
    val contacts by controller.vault.contacts.collectAsState()
    val myKey by controller.publicKeyBase64.collectAsState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var peerKey by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var myPhone by remember { mutableStateOf(controller.vault.myPhone()) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pairing & Vault", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text(
                "Share your public key (for ECDSA) and agree on ONE identical shared secret (for the acoustic HMAC) with each peer.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        PairCard(title = "My Identity") {
            OutlinedTextField(value = myPhone, onValueChange = { myPhone = it }, label = { Text("My phone (E.164 digits)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { controller.vault.setMyPhone(myPhone) }, modifier = Modifier.fillMaxWidth()) { Text("Save My Number") }
            Text("MY PUBLIC KEY — share with peer", color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                myKey.ifEmpty { "Generating…" },
                color = TextPrimary, fontSize = 10.sp, fontFamily = MonoFamily,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelRaised)
                    .border(1.dp, PanelBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            )
        }

        PairCard(title = "Add a Peer") {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Peer display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Peer phone (E.164 digits)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = peerKey, onValueChange = { peerKey = it }, label = { Text("Peer public key (Base64 X.509)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text("Shared secret (64 hex chars)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { secret = controller.generateSharedSecretHex() }, modifier = Modifier.weight(1f)) { Text("Generate Secret") }
                Button(
                    onClick = {
                        error = when {
                            phone.filter { it.isDigit() }.length < 10 -> "Enter a valid peer phone number."
                            !controller.sharedSecretValid(secret) -> "Shared secret must be 32 bytes (64 hex chars)."
                            else -> {
                                controller.addContact(name, phone, peerKey, secret)
                                name = ""; phone = ""; peerKey = ""; secret = ""
                                ""
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Pair Contact") }
            }
            if (error.isNotEmpty()) Text(error, color = AlertRed, fontSize = 12.sp)
            OutlinedButton(onClick = { controller.createDemoPeer() }, modifier = Modifier.fillMaxWidth()) { Text("Create One-Device Demo Peer") }
        }

        PairCard(title = "Paired Contacts (${contacts.size})") {
            if (contacts.isEmpty()) {
                Text("No contacts yet. Add a peer above or create the demo peer.", style = MaterialTheme.typography.bodySmall)
            }
            contacts.forEach { contact ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(contact.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(contact.phoneNumber, color = TextSecondary, fontSize = 12.sp, fontFamily = MonoFamily)
                        Text(
                            "Secret ${contact.sharedSecretHex.take(8)}… · paired ${if (contact.isHardwareBacked) "with hardware key" else "software key"}",
                            color = TextSecondary, fontSize = 11.sp, fontFamily = MonoFamily
                        )
                    }
                    OutlinedButton(onClick = { controller.vault.remove(contact.phoneNumber) }) { Text("Remove") }
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to Dashboard") }
    }
}

@Composable
private fun PairCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanelDark)
            .border(1.2.dp, PanelBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title.uppercase(), color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        content()
    }
}
