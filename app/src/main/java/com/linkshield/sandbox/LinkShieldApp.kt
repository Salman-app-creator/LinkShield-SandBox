package com.linkshield.sandbox

import android.app.Application

/**
 * UI-frozen application shell.
 * Backend engines are intentionally not initialized from Application.onCreate().
 * They will be wired in a later integration phase.
 */
class LinkShieldApp : Application()
