package com.wavetone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavetone.app.domain.CallSecuritySession

/**
 * Demo UPI payment screen. The visible/semantic text ("Pay ₹15,000",
 * "Enter UPI PIN") is exactly what ScamLockAccessibilityService scans for,
 * so the lockdown shield triggers without exposing a real banking app.
 */
@Composable
fun MockUpiScreen(onBack: () -> Unit) {
    val status by CallSecuritySession.status.collectAsState()
    val sessionActive by CallSecuritySession.sessionActive.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEF2F7))
            .systemBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A237E))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("MockPay UPI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Secure payments · demo build", color = Color(0xFFB3C0E8), fontSize = 12.sp)
            }
        }
        Column(
            modifier = Modifier
                .padding(20.dp)
                .shadow(2.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Send Money", color = Color(0xFF5A6B84), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Text("R. Sharma", color = Color.Black, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("98765 43210 · Mock Bank", color = Color(0xFF5A6B84), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("₹15,000", color = Color.Black, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text("Enter UPI PIN", color = Color(0xFF5A6B84), fontSize = 14.sp)
            Text("• • • •", color = Color.Black, fontSize = 26.sp, letterSpacing = 6.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "Pay ₹15,000" },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E9E50)),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Pay ₹15,000", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Cancel / Back")
            }
        }
        Text(
            "Session: $status · Active: $sessionActive. During FAILED/UNPAIRED lockdown with the Accessibility shield enabled, " +
                "a crimson overlay covers this Pay button until the call ends or a 3-second override is held.",
            color = Color(0xFF7A8799), fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}
