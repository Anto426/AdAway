package org.adaway.ui.log

import org.adaway.db.entity.ListType

/**
 * Interface for log view actions.
 */
interface LogViewCallback {
    fun addListItem(hostName: String, type: ListType)
    fun removeListItem(hostName: String)
    fun openHostInBrowser(hostName: String)
    fun copyHostToClipboard(hostName: String)
}
