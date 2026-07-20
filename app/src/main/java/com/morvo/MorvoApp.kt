// Morvo Application class
package com.morvo

import android.app.Application
import com.morvo.injector.ProcessManager
import com.morvo.network.LicenseAPI

class MorvoApp : Application() {
    
    lateinit var processManager: ProcessManager
    private lateinit var _licenseAPI: LicenseAPI
    val licenseAPI: LicenseAPI get() = _licenseAPI
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        processManager = ProcessManager(this)
        _licenseAPI = LicenseAPI(this)
    }
    
    companion object {
        lateinit var instance: MorvoApp
            private set
    }
}