package org.adaway.ui.prefs

import org.adaway.ui.compose.safeClickable

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.ui.compose.AdAwayExpressiveTheme
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.util.Constants.PREFS_NAME
import org.adaway.util.log.SentryLog

@Composable
internal fun PrefsMainScreen(
    darkThemeMode: String,
    enableIpv6: Boolean,
    enableTelemetry: Boolean,
    enableDebug: Boolean,
    telemetrySupported: Boolean,
    rootConfigEnabled: Boolean,
    vpnConfigEnabled: Boolean,
    onThemeSelected: (String) -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenRootConfig: () -> Unit,
    onOpenVpnConfig: () -> Unit,
    onEnableIpv6Changed: (Boolean) -> Unit,
    onOpenBackupRestore: () -> Unit,
    onEnableTelemetryChanged: (Boolean) -> Unit,
    onEnableDebugChanged: (Boolean) -> Unit
) {
    val themeLabels = stringArrayResource(R.array.pref_dark_theme_modes)
    val themeValues = stringArrayResource(R.array.pref_dark_theme_mode_entry_values)
    val selectedThemeLabel = remember(darkThemeMode, themeLabels, themeValues) {
        val index = themeValues.indexOf(darkThemeMode)
        if (index in themeLabels.indices) {
            themeLabels[index]
        } else {
            themeLabels.lastOrNull().orEmpty()
        }
    }
    var showThemeDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(text = stringResource(R.string.pref_dark_theme)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    themeLabels.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .safeClickable {
                                    onThemeSelected(themeValues[index])
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(text = stringResource(R.string.button_cancel))
                }
            }
        )
    }

    ExpressivePage {
        PreferenceCategoryHeader(titleRes = R.string.pref_general_category)
        ExpressiveSection {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                PreferenceRow(
                    iconRes = R.drawable.ic_brightness_medium_24dp,
                    titleRes = R.string.pref_dark_theme,
                    summary = selectedThemeLabel,
                    onClick = { showThemeDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceRow(
                    iconRes = R.drawable.ic_sync_24dp,
                    titleRes = R.string.pref_update_configuration,
                    onClick = onOpenUpdate
                )
            }
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_ad_block_category)
        ExpressiveSection {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                PreferenceRow(
                    iconRes = R.drawable.ic_superuser_24dp,
                    titleRes = R.string.pref_root_ad_blocker_configuration,
                    enabled = rootConfigEnabled,
                    onClick = onOpenRootConfig
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceRow(
                    iconRes = R.drawable.ic_vpn_key_24dp,
                    titleRes = R.string.pref_vpn_ad_blocker_configuration,
                    enabled = vpnConfigEnabled,
                    onClick = onOpenVpnConfig
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_ipv6_24dp,
                    titleRes = R.string.pref_enable_ipv6,
                    checked = enableIpv6,
                    onCheckedChange = onEnableIpv6Changed
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceRow(
                    iconRes = R.drawable.ic_sd_storage_24dp,
                    titleRes = R.string.pref_backup_restore,
                    onClick = onOpenBackupRestore
                )
            }
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_debug_category)
        ExpressiveSection {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                PreferenceToggleRow(
                    iconRes = R.drawable.outline_cloud_upload_24,
                    titleRes = R.string.pref_enable_telemetry,
                    summary = stringResource(
                        if (telemetrySupported) {
                            R.string.pref_enable_telemetry_summary
                        } else {
                            R.string.pref_enable_telemetry_disabled_summary
                        }
                    ),
                    checked = enableTelemetry,
                    enabled = telemetrySupported,
                    onCheckedChange = onEnableTelemetryChanged
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_bug_report_24dp,
                    titleRes = R.string.pref_enable_debug,
                    summary = stringResource(R.string.pref_enable_debug_summary),
                    checked = enableDebug,
                    onCheckedChange = onEnableDebugChanged
                )
            }
        }
        Spacer(modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun PreferenceCategoryHeader(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun PreferenceRow(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .safeClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .safeClickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}



