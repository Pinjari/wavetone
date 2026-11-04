package com.wavetone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavetone.app.domain.CallSecuritySession
import com.wavetone.app.domain.PairedContact
import com.wavetone.app.domain.VerificationStatus

@Composable
fun MainDashboardScreen(
    controller: WavetoneController,
    onOpenPairing: () -> Unit,
    onOpenMockUpi: () -> Unit
) {
    val status by CallSecuritySession.status.collectAsState()
    val session by CallSecuritySession.session.collectAsState()
    val sessionActive by CallSecuritySession.sessionActive.collectAsState()
    val latency by CallSecuritySession.lastLatencyMs.collectAsState()
    val detail by CallSecuritySession.lastDetail.collectAsState()
    val strongBox by controller.strongBox.collectAsState()
    val statusLine by controller.statusLine.collectAsState()
    val bars by controller.spectrogram.bars.collectAsState()
    val listening by controller.spectrogram.listening.collectAsState()
    val frameHex by controller.spectrogram.lastFrameHex.collectAsState()
    val bitstream by controller.spectrogram.bitstreamLog.collectAsState()
    val contacts by controller.vault.contacts.collectAsState()
    val shieldOn by CallSecuritySession.shieldConnected.collectAsState()

    var selectedPhone by remember { mutableStateOf<String?>(null) }
    val selected: PairedContact? = contacts.firstOrNull { it.phoneNumber == selectedPhone } ?: contacts.firstOrNull()

    val statusColor = when (status) {
        VerificationStatus.VERIFIED_HARDWARE, VerificationStatus.VERIFIED_PAIRED_DEVICE -> NeonGreen
        VerificationStatus.FAILED_SPOOF_DETECTED, VerificationStatus.UNPAIRED_CALLER_LOCKDOWN -> AlertRed
        VerificationStatus.VERIFYING_HANDSHAKE -> NeonCyan
        VerificationStatus.IDLE -> TextSecondary
    }
    val statusGlyph = when (status) {
        VerificationStatus.VERIFIED_HARDWARE, VerificationStatus.VERIFIED_PAIRED_DEVICE -> "✓"
        VerificationStatus.FAILED_SPOOF_DETECTED, VerificationStatus.UNPAIRED_CALLER_LOCKDOWN -> "✕"
        VerificationStatus.VERIFYING_HANDSHAKE -> "…"
        VerificationStatus.IDLE -> "○"
    }
    val statusLabel = when (status) {
        VerificationStatus.IDLE -> "Idle — no active call"
        VerificationStatus.VERIFYING_HANDSHAKE -> "Verifying handshake…"
        VerificationStatus.VERIFIED_HARDWARE -> "Hardware Verified" + if (latency >= 0) " · ${latency} ms" else ""
        VerificationStatus.VERIFIED_PAIRED_DEVICE -> "Paired Device Verified" + if (latency >= 0) " · ${latency} ms" else ""
        VerificationStatus.FAILED_SPOOF_DETECTED -> "Spoofed Call — No Valid Signature"
        VerificationStatus.UNPAIRED_CALLER_LOCKDOWN -> "Unpaired Caller — Lockdown Armed"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(PanelRaised, DeepSpace)))
                    .border(1.dp, PanelBorder, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) { Text("≋", color = NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("WAVETONE", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("Hardware + acoustic call attestation", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
            }
            ShieldBadge(shieldOn)
        }

        // Hero status card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(statusColor.copy(alpha = 0.14f), PanelDark)))
                .border(1.5.dp, statusColor.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(statusColor.copy(alpha = 0.16f))
                            .border(1.5.dp, statusColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(statusGlyph, color = statusColor, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(statusLabel, color = statusColor, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                        if (sessionActive) {
                            Text(
                                "Caller: ${session.callerName} · ${session.callerPhone}${if (session.isSimulated) " · simulated" else ""}",
                                color = TextPrimary, fontSize = 13.sp
                            )
                        }
                    }
                }
                if (detail.isNotEmpty()) {
                    Text(detail, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 17.sp)
                }
                if (CallSecuritySession.isLockdownStatus && sessionActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AlertRed.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "Scam-Breaker is armed: payment apps are shielded until this call ends. Open Mock UPI to see it.",
                            color = AlertRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Identity card
        SectionCard(title = "Device Identity") {
            InfoRow("Security chip", controller.signer.hardwareLabel())
            RowDivider()
            InfoRow("Enclave", if (strongBox) "StrongBox active" else "TEE fallback")
            RowDivider()
            InfoRow("Key fingerprint", controller.publicKeyFingerprint(), mono = true)
            RowDivider()
            InfoRow("My number", controller.vault.myPhone(), mono = true)
            RowDivider()
            InfoRow("Paired contacts", contacts.size.toString())
            RowDivider()
            InfoRow("Scam-Breaker shield", if (shieldOn) "Accessibility service ON" else "OFF")
            if (!shieldOn) {
                Text(
                    "Enable Wavetone in Settings → Accessibility → Installed apps so payment lockdown can block UPI apps during spoofed calls.",
                    color = WarningAmber, fontSize = 12.sp, lineHeight = 16.sp
                )
                OutlinedButton(
                    onClick = { com.wavetone.app.domain.openAccessibilitySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open Accessibility Settings") }
            }
            if (statusLine.isNotEmpty()) {
                Text(statusLine, color = WarningAmber, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        // Acoustic monitor card
        SectionCard(title = "Acoustic Monitor") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (listening) NeonGreen else TextSecondary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (listening) "LISTENING" else "MIC IDLE",
                    color = if (listening) NeonGreen else TextSecondary,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFamily
                )
                Spacer(Modifier.weight(1f))
                Text("1600 Hz = 0 · 2400 Hz = 1", color = TextSecondary, fontSize = 11.sp, fontFamily = MonoFamily)
            }
            SpectrogramCanvas(bars)
            Text(
                "Frame  ${frameHex.ifEmpty { "— — — —" }}",
                color = TextPrimary, fontSize = 12.sp, fontFamily = MonoFamily
            )
            Text(
                bitstream.ifEmpty { "Press Listen & Verify, then Emit on a peer or Simulate Peer Burst." },
                color = TextSecondary, fontSize = 11.sp, fontFamily = MonoFamily, lineHeight = 15.sp
            )
        }

        // Demo console card
        SectionCard(title = "Stage Demo Console") {
            if (contacts.isEmpty()) {
                Text(
                    "Pair a contact to unlock verification. For a one-device stage demo, create the demo peer:",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { controller.createDemoPeer() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Create Demo Peer Contact") }
            } else {
                if (contacts.size > 1) {
                    Text("Peer", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        contacts.forEach { contact ->
                            val isSel = selected?.phoneNumber == contact.phoneNumber
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSel) NeonCyan.copy(alpha = 0.18f) else PanelRaised)
                                    .border(1.dp, if (isSel) NeonCyan else PanelBorder, RoundedCornerShape(50))
                                    .clickable { selectedPhone = contact.phoneNumber }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(contact.displayName, color = if (isSel) NeonCyan else TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (selected != null) {
                    Text(
                        "Peer: ${selected.displayName} · ${selected.phoneNumber}",
                        color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = { controller.runOobHandshake(selected) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color(0xFF00301F))
                    ) { Text("Run OOB Hardware Handshake (ECDSA)", fontWeight = FontWeight.SemiBold) }
                    if (!controller.isLoopbackCapable(selected)) {
                        Text(
                            "Loopback OOB signs as this device, so it demos against the Demo Peer. Real peers answer via relay; verify them acoustically below.",
                            color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { controller.emitAcousticBurst(selected) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Emit Acoustic Burst") }
                        OutlinedButton(
                            onClick = { controller.simulatePeerBurst(selected) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Simulate Peer Burst") }
                    }
                    Button(
                        onClick = { controller.toggleListening() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (listening) AlertRed else NeonCyan,
                            contentColor = if (listening) Color.White else Color(0xFF00222B)
                        )
                    ) { Text(if (listening) "Stop Listening" else "Listen & Verify Acoustic Burst", fontWeight = FontWeight.SemiBold) }
                }

                HorizontalDivider(color = PanelBorder)
                Text("THREAT SIMULATION", color = AlertRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Button(
                    onClick = { controller.simulateDeepfakeAttack(selected) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White)
                ) { Text("Simulate Deepfake Attack Call", fontWeight = FontWeight.SemiBold) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { controller.simulateUnpairedCaller() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Simulate Unpaired Caller") }
                    OutlinedButton(
                        onClick = onOpenMockUpi,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Launch Mock UPI Payment") }
                }
            }
            HorizontalDivider(color = PanelBorder)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onOpenPairing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Pairing / Vault") }
                OutlinedButton(
                    onClick = { controller.endSession() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("End Session / Reset") }
            }
            Text(
                "Demo: 1) OOB handshake → hardware green. 2) Listen + peer burst → paired-device green. " +
                    "3) Deepfake attack → red. 4) Mock UPI → Accessibility shield blocks Pay. Enable Wavetone under Settings → Accessibility first.",
                color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ShieldBadge(shieldOn: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (shieldOn) NeonGreen.copy(alpha = 0.12f) else PanelRaised)
            .border(1.dp, if (shieldOn) NeonGreen.copy(alpha = 0.6f) else PanelBorder, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (shieldOn) NeonGreen else WarningAmber)
        )
        Text(
            if (shieldOn) "Shield ON" else "Shield OFF",
            color = if (shieldOn) NeonGreen else WarningAmber,
            fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = PanelBorder.copy(alpha = 0.55f))
}

@Composable
private fun Card(borderColor: Color = PanelBorder, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanelDark)
            .border(1.2.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Text(title.uppercase(), color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value, color = TextPrimary, fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) MonoFamily else null,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SpectrogramCanvas(bars: FloatArray) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF020610))
    ) {
        val barCount = 16
        val slot = size.width / barCount
        val barWidth = slot * 0.6f
        for (i in 0 until barCount) {
            val fraction = bars.getOrElse(i) { 0f }.coerceIn(0.02f, 1f)
            val barHeight = size.height * fraction
            val freqHz = 1200f + i * 120f
            val isMarkOrSpace = freqHz in 1560f..1640f || freqHz in 2360f..2440f
            val color = when {
                isMarkOrSpace && fraction > 0.45f -> NeonGreen
                isMarkOrSpace -> NeonCyan
                else -> Color(0xFF24507E)
            }
            drawRect(
                color = color,
                topLeft = Offset(x = i * slot + (slot - barWidth) / 2f, y = size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}
