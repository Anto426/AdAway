package org.adaway.ui.log

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.adaway.R
import org.adaway.db.entity.ListType
import org.adaway.helper.ThemeHelper
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar
import org.adaway.ui.compose.ExpressiveAppContainer
import org.adaway.ui.compose.ExpressiveBackground
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.safeCombinedClickable
import org.adaway.ui.dialog.AlertDialogValidator
import org.adaway.util.Clipboard
import org.adaway.util.RegexUtils

/**
 * Activity showing DNS request logs.
 */
class LogActivity : AppCompatActivity() {
    private lateinit var viewModel: LogViewModel
    private var applySnackbar: ApplyConfigurationSnackbar? = null
    private var snackbarBound = false
    private var refreshing by mutableStateOf(false)
    private var blockedRequestsIgnored = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        viewModel = ViewModelProvider(this)[LogViewModel::class.java]
        blockedRequestsIgnored = viewModel.areBlockedRequestsIgnored()
        viewModel.logs.observe(this) { refreshing = false }

        setContent {
            val recording by viewModel.isRecording().observeAsState(false)
            val logs by viewModel.logs.observeAsState(emptyList())
            ExpressiveAppContainer {
                LogScreen(
                    logs = logs,
                    recording = recording,
                    refreshing = refreshing,
                    blockedRequestsIgnored = blockedRequestsIgnored,
                    onNavigateBack = { onBackPressedDispatcher.onBackPressed() },
                    onSort = { viewModel.toggleSort() },
                    onClear = { viewModel.clearLogs() },
                    onRefresh = ::refreshLogs,
                    onToggleRecording = viewModel::toggleRecording,
                    onEntryAction = ::onEntryAction,
                    onOpenHost = ::openHostInBrowser,
                    onCopyHost = ::copyHostToClipboard
                )
            }
        }

        window.decorView.post { bindApplySnackbar() }
        supportActionBar?.hide()
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun bindApplySnackbar() {
        if (snackbarBound) {
            return
        }
        val rootView = findViewById<View>(android.R.id.content) ?: return
        applySnackbar = ApplyConfigurationSnackbar(rootView, false, false)
        snackbarBound = true
    }

    private fun refreshLogs() {
        refreshing = true
        viewModel.updateLogs()
    }

    private fun onEntryAction(entry: LogEntry, targetType: ListType) {
        if (entry.type == targetType) {
            viewModel.removeListItem(entry.host)
            applySnackbar?.notifyUpdateAvailable()
            return
        }
        if (targetType == ListType.REDIRECTED) {
            showRedirectDialog(entry.host)
            return
        }
        viewModel.addListItem(entry.host, targetType, null)
        applySnackbar?.notifyUpdateAvailable()
    }

    private fun showRedirectDialog(hostName: String) {
        val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
        val redirectIp = EditText(this).apply {
            setSingleLine(true)
            setText("0.0.0.0")
            setSelection(text.length)
        }
        val dialogView = FrameLayout(this).apply {
            addView(
                redirectIp,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            setPadding(horizontalPadding, horizontalPadding / 2, horizontalPadding, 0)
        }
        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setTitle(R.string.log_redirect_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.button_add) { dialog, _ ->
                dialog.dismiss()
                val ip = redirectIp.text.toString()
                if (RegexUtils.isValidIP(ip)) {
                    viewModel.addListItem(hostName, ListType.REDIRECTED, ip)
                    applySnackbar?.notifyUpdateAvailable()
                }
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ -> dialog.dismiss() }
            .create()
        alertDialog.show()
        redirectIp.addTextChangedListener(
            AlertDialogValidator(alertDialog, RegexUtils::isValidIP, false)
        )
    }

    private fun openHostInBrowser(hostName: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("http://$hostName")
        }
        startActivity(intent)
    }

    private fun copyHostToClipboard(hostName: String) {
        Clipboard.copyHostToClipboard(this, hostName)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LogScreen(
    logs: List<LogEntry>,
    recording: Boolean,
    refreshing: Boolean,
    blockedRequestsIgnored: Boolean,
    onNavigateBack: () -> Unit,
    onSort: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onToggleRecording: () -> Unit,
    onEntryAction: (LogEntry, ListType) -> Unit,
    onOpenHost: (String) -> Unit,
    onCopyHost: (String) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.shortcut_dns_requests),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material),
                            contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSort) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_sort_by_alpha_24),
                            contentDescription = stringResource(R.string.tcpdump_menu_sort)
                        )
                    }
                    IconButton(onClick = onClear) {
                        Icon(
                            painter = painterResource(R.drawable.outline_delete_24),
                            contentDescription = stringResource(R.string.tcpdump_menu_clear)
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync_24dp),
                            contentDescription = stringResource(R.string.menu_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onToggleRecording,
                containerColor = if (recording) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (recording) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    painter = painterResource(
                        if (recording) R.drawable.ic_pause_24dp else R.drawable.ic_record_24dp
                    ),
                    contentDescription = stringResource(R.string.log_toggle_recording_description),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        ExpressiveBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (refreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                if (logs.isEmpty()) {
                    val message = buildString {
                        append(stringResource(R.string.log_start_recording))
                        if (blockedRequestsIgnored) {
                            append(stringResource(R.string.log_blocked_requests_ignored))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        ExpressiveSection {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Start,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = logs,
                            key = { entry -> entry.host }
                        ) { entry ->
                            LogEntryRow(
                                entry = entry,
                                onAction = onEntryAction,
                                onOpenHost = onOpenHost,
                                onCopyHost = onCopyHost
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: LogEntry,
    onAction: (LogEntry, ListType) -> Unit,
    onOpenHost: (String) -> Unit,
    onCopyHost: (String) -> Unit
) {
    ExpressiveSection(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogActionButton(
                iconRes = R.drawable.baseline_block_24,
                active = entry.type == ListType.BLOCKED,
                onClick = { onAction(entry, ListType.BLOCKED) }
            )
            LogActionButton(
                iconRes = R.drawable.baseline_check_24,
                active = entry.type == ListType.ALLOWED,
                onClick = { onAction(entry, ListType.ALLOWED) }
            )
            LogActionButton(
                iconRes = R.drawable.baseline_compare_arrows_24,
                active = entry.type == ListType.REDIRECTED,
                onClick = { onAction(entry, ListType.REDIRECTED) }
            )
            Text(
                text = entry.host,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 6.dp)
                    .safeCombinedClickable(
                        onClick = { onOpenHost(entry.host) },
                        onLongClick = { onCopyHost(entry.host) }
                    )
            )
        }
    }
}

@Composable
private fun LogActionButton(
    iconRes: Int,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
    }
}
