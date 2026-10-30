package com.offline.bydvoice.byd

/** Actions that are Android-level rather than vehicle API calls. Implemented by the service layer. */
interface Platform {
    fun broadcastCameraView(keyEvent: Int): Boolean
    fun screen(on: Boolean): Boolean
    fun splitScreen(): Boolean
    fun brightness(night: Boolean): Boolean
    fun theme(dark: Boolean): Boolean
    fun launch(packageName: String): Boolean
    fun runLater(delayMs: Long, block: () -> Unit)
}
