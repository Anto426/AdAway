package org.adaway.ui.prefs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.Settings.ACTION_SECURITY_SETTINGS
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.net.InetAddresses
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.model.root.MountType.READ_ONLY
import org.adaway.model.root.MountType.READ_WRITE
import org.adaway.model.root.ShellUtils.isWritable
import org.adaway.model.root.ShellUtils.remountPartition
import org.adaway.ui.dialog.MissingAppDialog
import org.adaway.util.AppExecutors
import org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS
import org.adaway.util.Constants.PREFS_NAME
import org.adaway.util.WebServerUtils.TEST_URL
import org.adaway.util.WebServerUtils.copyCertificate
import org.adaway.util.WebServerUtils.getWebServerState
import org.adaway.util.WebServerUtils.installCertificate
import org.adaway.util.WebServerUtils.isWebServerRunning
import org.adaway.util.WebServerUtils.startWebServer
import org.adaway.util.WebServerUtils.stopWebServer
import org.adaway.ui.compose.AdAwayExpressiveTheme
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.safeClickable
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import timber.log.Timber

@Composable
internal fun PrefsRootScreen(
    neverReboot: Boolean,
    redirectionIpv4: String,
    redirectionIpv6: String,
    ipv6Enabled: Boolean,
    webServerEnabled: Boolean,
    webServerIcon: Boolean,
    @StringRes webServerStateSummaryRes: Int,
    onOpenHostsFile: () -> Unit,
    onNeverRebootChanged: (Boolean) -> Unit,
    onEditIpv4: (String) -> Unit,
    onEditIpv6: (String) -> Unit,
    onWebServerEnabledChanged: (Boolean) -> Unit,
    onWebServerTest: () -> Unit,
    onInstallCertificate: () -> Unit,
    onWebServerIconChanged: (Boolean) -> Unit
) {
    ExpressivePage {
        RootPrefsCategoryHeader(titleRes = R.string.pref_hosts_installation)
        ExpressiveSection {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                RootPreferenceRow(
                    iconRes = R.drawable.ic_collections_bookmark_24dp,
                    titleRes = R.string.pref_root_open_hosts,
                    onClick = onOpenHostsFile
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                RootPreferenceToggleRow(
                    iconRes = R.drawable.ic_settings_24dp,
                    titleRes = R.string.pref_never_reboot,
                    checked = neverReboot,
                    onCheckedChange = onNeverRebootChanged
                )
            }
        }

        RootPrefsCategoryHeader(titleRes = R.string.pref_hosts_redirection)
        ExpressiveSection {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                RootPreferenceRow(
                    iconRes = R.drawable.ic_outline_rule_24,
                    titleRes = R.string.pref_redirection_ipv4,
                    summary = redirectionIpv4,
                    onClick = { onEditIpv4(redirectionIpv4) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                RootPreferenceRow(
                    iconRes = R.drawable.ic_outline_rule_24,
                    titleRes = R.string.pref_redirection_ipv6,
                    summary = redirectionIpv6,
                    enabled = ipv6Enabled,
                    onClick = { onEditIpv6(redirectionIpv6) }
                )
            }
        }

        RootPrefsCategoryHeader(titleRes = R.string.pref_webserver)
        ExpressiveSection {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.pref_webserver_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                RootPreferenceToggleRow(
                    iconRes = R.drawable.ic_sync_24dp,
                    titleRes = R.string.pref_webserver_enabled,
                    checked = webServerEnabled,
                    onCheckedChange = onWebServerEnabledChanged
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                RootPreferenceRow(
                    iconRes = R.drawable.ic_help_24dp,
                    titleRes = R.string.pref_webserver_test,
                    summary = stringResource(webServerStateSummaryRes),
                    enabled = webServerEnabled,
                    onClick = onWebServerTest
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                RootPreferenceRow(
                    iconRes = R.drawable.ic_get_app_24dp,
                    titleRes = R.string.pref_webserver_certificate,
                    enabled = webServerEnabled,
                    onClick = onInstallCertificate
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                RootPreferenceToggleRow(
                    iconRes = R.drawable.logo,
                    titleRes = R.string.pref_webserver_icon,
                    checked = webServerIcon,
                    enabled = webServerEnabled,
                    onCheckedChange = onWebServerIconChanged
                )
            }
        }
        Spacer(modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun RootPrefsCategoryHeader(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun RootPreferenceRow(
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
            if (!summary.isNullOrBlank()) {
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
private fun RootPreferenceToggleRow(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
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
            if (iconRes == R.drawable.logo) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
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
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
