package com.wavetone.app.services

import android.telecom.Call
import android.telecom.CallScreeningService
import com.wavetone.app.AndroidContext
import com.wavetone.app.domain.CallSecuritySession
import com.wavetone.app.domain.ContactVault
import com.wavetone.app.domain.AndroidKeyValueStore
import com.wavetone.app.domain.VerificationStatus
import com.wavetone.app.domain.normalizePhone

/**
 * Fast, synchronous call screening: extracts the number, checks the paired
 * vault and seeds [CallSecuritySession]. Heavy handshake work runs in the UI
 * layer — screening must respond within the telecom timeout.
 * Requires the user to grant the Call Screening role to Wavetone.
 */
class CallMonitorService : CallScreeningService() {

    override fun onCreate() {
        super.onCreate()
        AndroidContext.init(applicationContext)
        CallSecuritySession.attachStore(AndroidKeyValueStore())
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // Telecom enforces a short screening timeout: whatever happens in
        // session bookkeeping, the call must always get a response.
        try {
            screen(callDetails)
        } catch (e: Exception) {
            // Fall through and allow the call unscreened rather than stall it.
        } finally {
            respondToCall(
                callDetails,
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            )
        }
    }

    private fun screen(callDetails: Call.Details) {
        AndroidContext.init(applicationContext)
        CallSecuritySession.attachStore(AndroidKeyValueStore())
        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val number = normalizePhone(rawNumber)
        val vault = ContactVault(AndroidKeyValueStore())
        val contact = if (number.isNotEmpty()) vault.find(number) else null

        if (contact != null) {
            CallSecuritySession.beginSession(
                callerPhone = contact.phoneNumber,
                callerName = contact.displayName,
                simulated = false,
                status = VerificationStatus.VERIFYING_HANDSHAKE
            )
            CallSecuritySession.setStatus(
                VerificationStatus.VERIFYING_HANDSHAKE,
                "Incoming call from paired contact ${contact.displayName} — run OOB/acoustic verification"
            )
        } else {
            CallSecuritySession.beginSession(
                callerPhone = number.ifEmpty { "unknown" },
                callerName = "Unpaired Caller",
                simulated = false,
                status = VerificationStatus.UNPAIRED_CALLER_LOCKDOWN
            )
            CallSecuritySession.setStatus(
                VerificationStatus.UNPAIRED_CALLER_LOCKDOWN,
                "Caller $number is not paired — UPI lockdown armed during this call"
            )
        }
    }
}
