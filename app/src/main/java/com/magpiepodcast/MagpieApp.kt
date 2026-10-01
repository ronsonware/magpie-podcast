package com.magpiepodcast

import android.app.Application
import com.magpiepodcast.data.ProgressRepository
import com.magpiepodcast.data.TokenStore

class MagpieApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var progressRepository: ProgressRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenStore = TokenStore(this)
        progressRepository = ProgressRepository(this)
    }

    companion object {
        lateinit var instance: MagpieApp
            private set
    }
}
