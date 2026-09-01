/*
 * Copyright 2026 DeNA Co., Ltd.
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
package packetproxy

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.net.BindException
import packetproxy.model.ListenPort
import packetproxy.model.ListenPorts
import packetproxy.model.PropertyChangeEventType.LISTEN_PORTS
import packetproxy.model.PropertyChangeEventType.SERVERS
import packetproxy.model.Servers
import packetproxy.util.Logging.err
import packetproxy.util.Logging.errWithStackTrace
import packetproxy.util.Logging.log

class ListenPortManager private constructor() : PropertyChangeListener {
  companion object {
    @Volatile private var instance: ListenPortManager? = null

    @JvmStatic
    @Throws(Exception::class)
    fun getInstance(): ListenPortManager =
      instance ?: synchronized(this) { instance ?: ListenPortManager().also { instance = it } }
  }

  private val listenPorts: ListenPorts = ListenPorts.getInstance()
  private val listenMap: MutableMap<String, Listen> = HashMap()

  init {
    listenPorts.addPropertyChangeListener(this)
    Servers.getInstance().addPropertyChangeListener(this)
    listenPorts.refresh()
  }

  @Throws(Exception::class)
  fun rebootIfHTTPProxyRunning() {
    val list = listenPorts.queryEnabledHttpProxis()
    for (lp in list) {
      val listen = listenMap[lp.protoPort] ?: continue
      listen.close()
      listenMap[lp.protoPort] = Listen(lp)
    }
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    if (LISTEN_PORTS.matches(evt)) {
      try {
        synchronized(listenMap) {
          stopIfRunning()
          startIfStateChanged()
        }
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    } else if (SERVERS.matches(evt)) {
      try {
        synchronized(listenMap) { restartAffectedForwarders() }
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
  }

  @Throws(Exception::class)
  private fun stopIfRunning() {
    val enabledPorts = listenPorts.queryEnabled().map { it.protoPort }.toSet()
    val toRemove = listenMap.keys.filter { it !in enabledPorts }
    for (p in toRemove) {
      listenMap[p]?.close()
      listenMap.remove(p)
    }
  }

  @Throws(Exception::class)
  private fun startIfStateChanged() {
    val list = listenPorts.queryEnabled()
    for (listenPort in list) {
      startListen(listenPort)
    }
  }

  @Throws(Exception::class)
  private fun restartAffectedForwarders() {
    val list = listenPorts.queryEnabled()
    for (listenPort in list) {
      if (!listenPort.type.isForwarder) continue
      val listen = listenMap[listenPort.protoPort] ?: continue
      log("## restarting forwarder due to server change: %s", listenPort.protoPort)
      listen.close()
      listenMap[listenPort.protoPort] = Listen(listenPort)
    }
  }

  @Throws(Exception::class)
  private fun startListen(listenPort: ListenPort) {
    try {
      val listen = listenMap[listenPort.protoPort]
      if (listen != null) {
        if (listen.listenInfo != listenPort) {
          listen.close()
          listenMap[listenPort.protoPort] = Listen(listenPort)
          log("## restart: %s", listenPort.protoPort)
        }
      } else {
        log("## start: %s", listenPort.protoPort)
        listenMap[listenPort.protoPort] = Listen(listenPort)
      }
    } catch (e: BindException) {
      err("cannot listen port. (permission issue or already listened)")
      listenPort.setDisabled()
      listenPorts.update(listenPort)
    }
  }
}
