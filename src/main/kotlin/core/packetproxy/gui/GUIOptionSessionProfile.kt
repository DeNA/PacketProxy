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
package packetproxy.gui

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.util.function.Supplier
import javax.swing.JFrame
import packetproxy.common.I18nString
import packetproxy.model.PropertyChangeEventType.SESSION_PROFILES
import packetproxy.model.SessionProfile
import packetproxy.model.SessionProfiles
import packetproxy.util.Logging.errWithStackTrace

class GUIOptionSessionProfile
@JvmOverloads
@Throws(Exception::class)
constructor(owner: JFrame, private val authorizationSupplier: Supplier<String>? = null) :
  GUIOptionComponentBase<SessionProfile>(owner) {
  private val sessionProfiles: SessionProfiles = SessionProfiles.getInstance()
  private val tableList = mutableListOf<SessionProfile>()

  init {
    sessionProfiles.addPropertyChangeListener(this)

    val menu = arrayOf(I18nString.get("Name"), I18nString.get("Authorization"))
    val menuWidth = intArrayOf(150, 400)

    val tableAction =
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          val rowIndex = table.rowAtPoint(e.point)
          if (rowIndex >= 0) {
            table.setRowSelectionInterval(rowIndex, rowIndex)
          }
        }
      }

    val addAction = {
      try {
        val dlg = GUIOptionSessionProfileDialog(owner, authorizationSupplier)
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }

    val editAction = {
      try {
        val oldProfile = getSelectedTableContent()
        if (oldProfile != null) {
          val dlg = GUIOptionSessionProfileDialog(owner, authorizationSupplier)
          dlg.showDialog(oldProfile)
        }
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }

    val removeAction = {
      try {
        val selected = getSelectedTableContent()
        if (selected != null) {
          sessionProfiles.delete(selected)
        }
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }

    jcomponent =
      createComponent(
        menu,
        menuWidth,
        tableAction,
        { addAction() },
        { editAction() },
        { removeAction() },
      )
    updateImpl()
  }

  fun showManageDialog() {
    try {
      val dlg = GUIOptionSessionProfileDialog(owner, authorizationSupplier)
      dlg.showDialog()
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    if (SESSION_PROFILES.matches(evt)) {
      updateImpl()
      return
    }
    super.propertyChange(evt)
  }

  override fun addTableContent(profile: SessionProfile) {
    tableList.add(profile)
    option_model.addRow(
      arrayOf<Any?>(profile.name, SessionProfile.formatAuthorizationPreview(profile.authorization))
    )
  }

  override fun updateTable(profileList: List<SessionProfile>) {
    clearTableContents()
    for (profile in profileList) {
      addTableContent(profile)
    }
  }

  override fun updateImpl() {
    try {
      updateTable(sessionProfiles.queryAll())
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }

  override fun clearTableContents() {
    option_model.rowCount = 0
    tableList.clear()
  }

  override fun getSelectedTableContent(): SessionProfile? {
    val rowIndex = table.selectedRow
    if (rowIndex < 0) {
      return null
    }
    return getTableContent(rowIndex)
  }

  override fun getTableContent(rowIndex: Int): SessionProfile = tableList[rowIndex]
}
