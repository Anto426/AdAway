package org.adaway.ui.prefs

import org.adaway.ui.compose.safeClickable

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.ui.compose.ExpressiveBackground
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.prefs.exclusion.PrefsVpnExcludedAppsActivity
import org.adaway.util.Constants.PREFS_NAME
import org.adaway.vpn.VpnServiceControls

@Composable
internal fun PrefsVpnScreen(
    serviceOnBoot: Boolean,
    watchdogEnabled: Boolean,
    excludedSystemApps: String,
    onServiceOnBootChanged: (Boolean) -> Unit,
    onWatchdogChanged: (Boolean) -> Unit,
    onExcludedSystemAppsChanged: (String) -> Unit,
    onOpenExcludedUserApps: () -> Unit
) {
    val excludedEntries = stringArrayResource(R.array.pref_vpn_excluded_system_apps_entries)
    val excludedValues = stringArrayResource(R.array.pref_vpn_excluded_system_apps_values)
    val selectedExcludedLabel = remember(excludedSystemApps, excludedEntries, excludedValues) {
        val index = excludedValues.indexOf(excludedSystemApps)
        if (index in excludedEntries.indices) {
            excludedEntries[index]
        } else {
            excludedEntries.firstOrNull().orEmpty()
        }
    }
    var showExcludedDialog by remember { mutableStateOf(false) }

    if (showExcludedDialog) {
        AlertDialog(
            onDismissRequest = { showExcludedDialog = false },
            title = { Text(text = stringResource(R.string.pref_vpn_exclude_system_apps)) },
            text = {
                Column {
                    excludedEntries.forEachIndexed { index, entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .safeClickable {
                                    onExcludedSystemAppsChanged(excludedValues[index])
                                    showExcludedDialog = false
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExcludedDialog = false }) {
                    Text(text = stringResource(R.string.button_cancel))
                }
            }
        )
    }

    ExpressiveBackground {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            PrefsCategoryTitle(titleRes = R.string.pref_general_category)
            ExpressiveSection {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    PrefsToggleRow(
                        iconRes = R.drawable.ic_vpn_key_24dp,
                        titleRes = R.string.pref_vpn_service_on_boot,
                        checked = serviceOnBoot,
                        onCheckedChange = onServiceOnBootChanged
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PrefsToggleRow(
                        iconRes = R.drawable.ic_sync_24dp,
                        titleRes = R.string.pref_vpn_service_monitor,
                        summary = stringResource(R.string.pref_vpn_service_monitor_description),
                        checked = watchdogEnabled,
                        onCheckedChange = onWatchdogChanged
                    )
                }
            }

            PrefsCategoryTitle(titleRes = R.string.pref_vpn_excluded_apps)
            ExpressiveSection {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.pref_vpn_excluded_apps_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PrefsActionRow(
                        iconRes = R.drawable.ic_settings_24dp,
                        titleRes = R.string.pref_vpn_exclude_system_apps,
                        summary = selectedExcludedLabel,
                        onClick = { showExcludedDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PrefsActionRow(
                        iconRes = R.drawable.ic_menu_24dp,
                        titleRes = R.string.pref_vpn_exclude_user_apps,
                        onClick = onOpenExcludedUserApps
                    )
                }
            }
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PrefsCategoryTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun PrefsActionRow(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    summary: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .safeClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PrefsToggleRow(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .safeClickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}



