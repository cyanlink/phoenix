/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.services

import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.msopentech.thali.android.installer.AndroidTorInstaller
import com.msopentech.thali.android.toronionproxy.AndroidOnionProxyManager
import com.msopentech.thali.android.toronionproxy.AndroidTorConfig
import com.msopentech.thali.toronionproxy.OnionProxyManager
import com.msopentech.thali.toronionproxy.TorConfig
import com.msopentech.thali.toronionproxy.android.R
import fr.acinq.phoenix.BuildConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream


/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 *
 *
 * TODO: Customize class - update intent actions, extra parameters and static helper methods.
 */
class TorBackgroundService : IntentService("TorBackgroundService") {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun onHandleIntent(intent: Intent?) {
    intent?.let {
      if (ACTION_START_TOR == it.action) {
        try {
          startTor()
        } catch (e: Exception) {
          log.error("failed to start TOR service: ", e)
        }
      }
    }
  }

  private fun startTor() {
    val outputDir: File = cacheDir
    val fileStorageLocation: String = outputDir.absolutePath
    val onionProxyManager: OnionProxyManager = getOnionProxyManager(fileStorageLocation)
    val totalSecondsPerTorStartup = 4 * 60
    val totalTriesPerTorStartup = 5
    onionProxyManager.setup()
    onionProxyManager.torInstaller.updateTorConfigCustom("ControlPort auto" +
      "\nControlPortWriteToFile " + onionProxyManager.context.config.controlPortFile +
      "\nCookieAuthFile " + onionProxyManager.context.config.cookieAuthFile +
      "\nCookieAuthentication 1" +
      "\nSocksPort 10462")
    if (!onionProxyManager.startWithRepeat(totalSecondsPerTorStartup, totalTriesPerTorStartup, true)) {
      log.error("could not start Tor!")
    } else {
      log.info("successfully started TOR service")
    }
  }

  private fun getOnionProxyManager(workingSubDirectoryName: String): OnionProxyManager {
    val installDir = File(workingSubDirectoryName)
    val torConfig: TorConfig = AndroidTorConfig.createConfig(installDir, installDir, this)
    return AndroidOnionProxyManager(this, torConfig, TestTorInstaller(this, installDir),
      null, null, null)
  }

  companion object {
    private const val ACTION_START_TOR = "${BuildConfig.APPLICATION_ID}.START_TOR"

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    fun startTorService(context: Context) {
      val intent = Intent(context, TorBackgroundService::class.java)
      intent.action = ACTION_START_TOR
      context.startService(intent)
    }
  }
}

internal class TestTorInstaller(context: Context?, configDir: File?) : AndroidTorInstaller(context, configDir) {
  override fun openBridgesStream(): InputStream {
    return context.resources.openRawResource(R.raw.bridges)
  }
}
