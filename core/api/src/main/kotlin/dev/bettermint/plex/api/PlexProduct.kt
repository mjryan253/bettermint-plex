package dev.bettermint.plex.api

/**
 * Identity this client presents to plex.tv and Plex Media Servers via X-Plex-* headers.
 * The product name must not match an existing Plex product.
 */
object PlexProduct {
    const val NAME = "Bettermint"
    const val VERSION = "0.1.0"
    const val PLATFORM = "Android"
}
