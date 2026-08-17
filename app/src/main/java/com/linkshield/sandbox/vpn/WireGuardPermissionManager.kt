package com.linkshield.sandbox.vpn

import android.app.Activity
import android.content.Intent
import com.wireguard.android.backend.GoBackend

class WireGuardPermissionManager(
    private val activity: Activity
) {

    companion object {
        const val REQUEST_CODE = 9102
    }

    fun prepare(): Boolean {
        val intent =
            GoBackend.VpnService.prepare(activity)

        if (intent == null) {
            return true
        }

        activity.startActivityForResult(
            intent,
            REQUEST_CODE
        )

        return false
    }

    fun handleResult(
        requestCode: Int,
        resultCode: Int
    ): Boolean {
        return requestCode == REQUEST_CODE &&
            resultCode == Activity.RESULT_OK
    }
}
