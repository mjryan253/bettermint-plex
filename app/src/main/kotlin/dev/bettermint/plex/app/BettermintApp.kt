package dev.bettermint.plex.app

import android.app.Application

class BettermintApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
