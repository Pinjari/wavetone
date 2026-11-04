package com.wavetone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.wavetone.app.domain.ContactVault
import com.wavetone.app.domain.createPlatformStore
import com.wavetone.app.ui.MainDashboardScreen
import com.wavetone.app.ui.MockUpiScreen
import com.wavetone.app.ui.PairingScreen
import com.wavetone.app.ui.WavetoneController
import com.wavetone.app.ui.WavetoneTheme

enum class WavetoneScreen { DASHBOARD, PAIRING, MOCK_UPI }

@Composable
fun App() {
    WavetoneTheme {
        val scope = rememberCoroutineScope()
        val controller = remember { WavetoneController(scope, ContactVault(createPlatformStore())) }
        var screen by remember { mutableStateOf(WavetoneScreen.DASHBOARD) }

        LaunchedEffect(Unit) { controller.initIdentity() }

        when (screen) {
            WavetoneScreen.DASHBOARD -> MainDashboardScreen(
                controller = controller,
                onOpenPairing = { screen = WavetoneScreen.PAIRING },
                onOpenMockUpi = { screen = WavetoneScreen.MOCK_UPI }
            )
            WavetoneScreen.PAIRING -> PairingScreen(
                controller = controller,
                onBack = { screen = WavetoneScreen.DASHBOARD }
            )
            WavetoneScreen.MOCK_UPI -> MockUpiScreen(
                onBack = { screen = WavetoneScreen.DASHBOARD }
            )
        }
    }
}
