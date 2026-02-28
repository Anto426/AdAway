package org.adaway.ui.log

import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.adaway.R
import org.adaway.db.entity.ListType
import org.adaway.ui.compose.AdAwayExpressiveTheme
import org.adaway.ui.compose.ExpressiveSection

class LogAdapter(private val callback: LogViewCallback) :
    ListAdapter<LogEntry, LogAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LogEntry>() {
            override fun areItemsTheSame(oldEntry: LogEntry, newEntry: LogEntry): Boolean {
                return oldEntry.host == newEntry.host
            }

            override fun areContentsTheSame(oldEntry: LogEntry, newEntry: LogEntry): Boolean {
                return oldEntry == newEntry
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ComposeView(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.bind(entry, callback)
    }

    class ViewHolder(private val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        fun bind(entry: LogEntry, callback: LogViewCallback) {
            composeView.setContent {
                AdAwayExpressiveTheme {
                    LogEntryItem(entry, callback)
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry, callback: LogViewCallback) {
    ExpressiveSection(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { callback.openHostInBrowser(entry.host) }
            ) {
                Text(
                    text = entry.host,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LogActionIcon(
                    iconRes = R.drawable.baseline_block_24,
                    isActive = entry.type == ListType.BLOCKED,
                    activeColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        if (entry.type == ListType.BLOCKED) {
                            callback.removeListItem(entry.host)
                        } else {
                            callback.addListItem(entry.host, ListType.BLOCKED)
                        }
                    }
                )
                LogActionIcon(
                    iconRes = R.drawable.baseline_check_24,
                    isActive = entry.type == ListType.ALLOWED,
                    activeColor = colorResource(R.color.allowed),
                    onClick = {
                        if (entry.type == ListType.ALLOWED) {
                            callback.removeListItem(entry.host)
                        } else {
                            callback.addListItem(entry.host, ListType.ALLOWED)
                        }
                    }
                )
                LogActionIcon(
                    iconRes = R.drawable.baseline_compare_arrows_24,
                    isActive = entry.type == ListType.REDIRECTED,
                    activeColor = colorResource(R.color.redirected),
                    onClick = {
                        if (entry.type == ListType.REDIRECTED) {
                            callback.removeListItem(entry.host)
                        } else {
                            callback.addListItem(entry.host, ListType.REDIRECTED)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun colorResource(id: Int): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(androidx.core.content.ContextCompat.getColor(androidx.compose.ui.platform.LocalContext.current, id))
}

@Composable
private fun LogActionIcon(
    iconRes: Int,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (isActive) activeColor else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
    }
}
