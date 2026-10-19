package dev.bettermint.plex.api

/**
 * Identity sent on every request via X-Plex-* headers. plex.tv and PMS reject or
 * misroute requests with missing headers ("unable to find client profile"), so every
 * field must be non-blank.
 */
data class PlexDeviceInfo(
    val clientIdentifier: String,
    val product: String = PlexProduct.NAME,
    val version: String = PlexProduct.VERSION,
    val platform: String = PlexProduct.PLATFORM,
    val platformVersion: String,
    val device: String,
    val deviceName: String,
) {
    init {
        require(clientIdentifier.isNotBlank()) { "clientIdentifier must not be blank" }
        require(platformVersion.isNotBlank()) { "platformVersion must not be blank" }
        require(device.isNotBlank()) { "device must not be blank" }
        require(deviceName.isNotBlank()) { "deviceName must not be blank" }
    }
}
