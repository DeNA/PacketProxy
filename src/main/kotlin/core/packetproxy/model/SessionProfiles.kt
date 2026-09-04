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
package packetproxy.model

import com.j256.ormlite.dao.Dao
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import packetproxy.model.Database.DatabaseMessage
import packetproxy.model.PropertyChangeEventType.DATABASE_MESSAGE
import packetproxy.model.PropertyChangeEventType.SESSION_PROFILES
import packetproxy.util.Logging.errWithStackTrace

class SessionProfiles private constructor() : PropertyChangeListener {
  private val changes = PropertyChangeSupport(this)
  private var database: Database = Database.getInstance()
  private var dao: Dao<SessionProfile, Int> = database.createTable(SessionProfile::class.java, this)

  fun create(profile: SessionProfile) {
    dao.create(profile)
    firePropertyChange()
  }

  fun delete(profile: SessionProfile) {
    dao.delete(profile)
    firePropertyChange()
  }

  fun update(profile: SessionProfile) {
    dao.update(profile)
    firePropertyChange()
  }

  fun query(id: Int): SessionProfile? = dao.queryForId(id)

  fun queryByName(name: String): SessionProfile? =
    dao.queryBuilder().where().eq("name", name).queryForFirst()

  fun queryAll(): List<SessionProfile> = dao.queryBuilder().orderBy("name", true).query()

  fun addPropertyChangeListener(listener: PropertyChangeListener) {
    changes.addPropertyChangeListener(listener)
  }

  fun removePropertyChangeListener(listener: PropertyChangeListener) {
    changes.removePropertyChangeListener(listener)
  }

  private fun firePropertyChange() {
    changes.firePropertyChange(SESSION_PROFILES.toString(), null, null)
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    if (!DATABASE_MESSAGE.matches(evt)) {
      return
    }

    val message = evt.newValue as DatabaseMessage
    try {
      when (message) {
        DatabaseMessage.PAUSE -> {}
        DatabaseMessage.RESUME -> {}
        DatabaseMessage.DISCONNECT_NOW -> {}
        DatabaseMessage.RECONNECT -> {
          database = Database.getInstance()
          dao = database.createTable(SessionProfile::class.java, this)
          firePropertyChange()
        }
        DatabaseMessage.RECREATE -> {
          database = Database.getInstance()
          dao = database.createTable(SessionProfile::class.java, this)
        }
      }
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }

  companion object {
    private var instance: SessionProfiles? = null

    @JvmStatic
    @Throws(Exception::class)
    fun getInstance(): SessionProfiles {
      if (instance == null) {
        instance = SessionProfiles()
      }
      return instance!!
    }
  }
}
