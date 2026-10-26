package com.carbonsphere.uhppote

import android.app.Application
import android.content.Context

class UhppoteApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** The app graph from any context. */
val Context.graph: AppGraph get() = (applicationContext as UhppoteApp).graph
