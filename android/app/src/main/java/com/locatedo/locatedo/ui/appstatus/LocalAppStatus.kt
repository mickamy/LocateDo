package com.locatedo.locatedo.ui.appstatus

import androidx.compose.runtime.compositionLocalOf
import com.locatedo.locatedo.core.appstatus.AppStatusState

// The current app status, provided by MainActivity; screens read what maintenance or an old build takes away.
val LocalAppStatus = compositionLocalOf { AppStatusState() }
