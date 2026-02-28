package org.adaway.ui.prefs

import org.adaway.ui.compose.safeClickable

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import org.adaway.AdAwayApplication
import org.adaway.R
import org.adaway.model.source.SourceUpdateService
import org.adaway.model.update.ApkUpdateService
import org.adaway.model.update.UpdateStore
import org.adaway.ui.compose.AdAwayExpressiveTheme
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.util.Constants.PREFS_NAME

/**
 * Compose version of update preferences.
 */
class PrefsUpdateFragment : Fragment() {
    private lateinit var prefs: android.content.SharedPreferences

    private var notificationsDisabled by mutableStateOf(false)
    private var checkAppStartup by mutableStateOf(false)
    private var checkAppDaily by mutableStateOf(false)
    private var includeBetaReleases by mutableStateOf(false)
    private var includeBetaEnabled by mutableStateOf(true)
    private var checkHostsStartup by mutableStateOf(false)
    private var checkHostsDaily by mutableStateOf(false)
    private var automaticUpdateDaily by mutableStateOf(false)
    private var updateOnlyOnWifi by mutableStateOf(false)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reloadState(context)
        setTitle()
    }

    override fun onResume() {
        super.onResume()
        reloadState(requireContext())
        setTitle()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return ComposeView(requireContext()).apply {
            setContent {
                AdAwayExpressiveTheme {
                    PrefsUpdateScreen(
                        notificationsDisabled = notificationsDisabled,
                        checkAppStartup = checkAppStartup,
                        checkAppDaily = checkAppDaily,
                        includeBetaReleases = includeBetaReleases,
                        includeBetaEnabled = includeBetaEnabled,
                        checkHostsStartup = checkHostsStartup,
                        checkHostsDaily = checkHostsDaily,
                        automaticUpdateDaily = automaticUpdateDaily,
                        updateOnlyOnWifi = updateOnlyOnWifi,
                        onOpenNotifications = ::openNotificationPreferences,
                        onCheckAppStartupChanged = { enabled ->
                            checkAppStartup = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_update_check_app_startup_key), enabled)
                                .apply()
                        },
                        onCheckAppDailyChanged = { enabled ->
                            checkAppDaily = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_update_check_app_daily_key), enabled)
                                .apply()
                            if (enabled) ApkUpdateService.enable(requireContext()) else ApkUpdateService.disable(requireContext())
                        },
                        onIncludeBetaChanged = { enabled ->
                            includeBetaReleases = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_update_include_beta_releases_key), enabled)
                                .apply()
                        },
                        onCheckHostsStartupChanged = { enabled ->
                            checkHostsStartup = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_update_check_key), enabled)
                                .apply()
                        },
                        onCheckHostsDailyChanged = { enabled ->
                            checkHostsDaily = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_update_check_hosts_daily_key), enabled)
                                .apply()
                            if (enabled) {
                                SourceUpdateService.enable(requireContext(), updateOnlyOnWifi)
                            } else {
                                SourceUpdateService.disable(requireContext())
                            }
                        },
                        onAutomaticUpdateDailyChanged = { enabled ->
                            automaticUpdateDaily = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_automatic_update_daily_key), enabled)
                                .apply()
                        },
                        onUpdateOnlyWifiChanged = { enabled ->
                            updateOnlyOnWifi = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_update_only_on_wifi_key), enabled)
                                .apply()
                            SourceUpdateService.enable(requireContext(), enabled)
                        }
                    )
                }
            }
        }
    }

    private fun setTitle() {
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.pref_update_title)
    }

    private fun openNotificationPreferences() {
        val context = requireContext()
        val settingsIntent = Intent(ACTION_APP_NOTIFICATION_SETTINGS)
            .addFlags(FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_APP_PACKAGE, context.packageName)
        context.startActivity(settingsIntent)
    }

    private fun reloadState(context: Context) {
        notificationsDisabled = SDK_INT >= TIRAMISU &&
            context.checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED

        checkAppStartup = prefs.getBoolean(
            context.getString(R.string.pref_update_check_app_startup_key),
            context.resources.getBoolean(R.bool.pref_update_check_app_startup_def)
        )
        checkAppDaily = prefs.getBoolean(
            context.getString(R.string.pref_update_check_app_daily_key),
            context.resources.getBoolean(R.bool.pref_update_check_app_daily_def)
        )
        includeBetaReleases = prefs.getBoolean(
            context.getString(R.string.pref_update_include_beta_releases_key),
            context.resources.getBoolean(R.bool.pref_update_include_beta_releases_def)
        )
        checkHostsStartup = prefs.getBoolean(
            context.getString(R.string.pref_update_check_key),
            context.resources.getBoolean(R.bool.pref_update_check_def)
        )
        checkHostsDaily = prefs.getBoolean(
            context.getString(R.string.pref_update_check_hosts_daily_key),
            context.resources.getBoolean(R.bool.pref_update_check_hosts_daily_def)
        )
        automaticUpdateDaily = prefs.getBoolean(
            context.getString(R.string.pref_automatic_update_daily_key),
            context.resources.getBoolean(R.bool.pref_automatic_update_daily_def)
        )
        updateOnlyOnWifi = prefs.getBoolean(
            context.getString(R.string.pref_update_only_on_wifi_key),
            context.resources.getBoolean(R.bool.pref_update_only_on_wifi_def)
        )

        val application = context.applicationContext as AdAwayApplication
        includeBetaEnabled = application.updateModel.store == UpdateStore.ADAWAY
    }
}

@Composable
private fun PrefsUpdateScreen(
    notificationsDisabled: Boolean,
    checkAppStartup: Boolean,
    checkAppDaily: Boolean,
    includeBetaReleases: Boolean,
    includeBetaEnabled: Boolean,
    checkHostsStartup: Boolean,
    checkHostsDaily: Boolean,
    automaticUpdateDaily: Boolean,
    updateOnlyOnWifi: Boolean,
    onOpenNotifications: () -> Unit,
    onCheckAppStartupChanged: (Boolean) -> Unit,
    onCheckAppDailyChanged: (Boolean) -> Unit,
    onIncludeBetaChanged: (Boolean) -> Unit,
    onCheckHostsStartupChanged: (Boolean) -> Unit,
    onCheckHostsDailyChanged: (Boolean) -> Unit,
    onAutomaticUpdateDailyChanged: (Boolean) -> Unit,
    onUpdateOnlyWifiChanged: (Boolean) -> Unit
) {
    ExpressivePage {
        if (notificationsDisabled) {
            ExpressiveSection(containerColor = MaterialTheme.colorScheme.errorContainer) {
                PreferenceRow(
                    iconRes = R.drawable.notifications_off_24,
                    titleRes = R.string.pref_update_enable_notifications,
                    summary = stringResource(R.string.pref_update_enable_notifications_summary),
                    onClick = onOpenNotifications,
                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                    titleColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_update_app_category)
        ExpressiveSection {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_sync_24dp,
                    titleRes = R.string.pref_update_check_app_startup,
                    checked = checkAppStartup,
                    onCheckedChange = onCheckAppStartupChanged
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_sync_24dp,
                    titleRes = R.string.pref_update_check_app_daily,
                    checked = checkAppDaily,
                    onCheckedChange = onCheckAppDailyChanged
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_outline_rule_24,
                    titleRes = R.string.pref_update_include_beta_releases,
                    checked = includeBetaReleases,
                    enabled = includeBetaEnabled,
                    onCheckedChange = onIncludeBetaChanged
                )
            }
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_update_hosts_category)
        ExpressiveSection {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_sync_24dp,
                    titleRes = R.string.pref_update_check,
                    checked = checkHostsStartup,
                    onCheckedChange = onCheckHostsStartupChanged
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_sync_24dp,
                    titleRes = R.string.pref_update_check_hosts_daily,
                    checked = checkHostsDaily,
                    onCheckedChange = onCheckHostsDailyChanged
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_playlist_add_24dp,
                    titleRes = R.string.pref_update_sync_on_update,
                    checked = automaticUpdateDaily,
                    enabled = checkHostsDaily,
                    onCheckedChange = onAutomaticUpdateDailyChanged
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                PreferenceToggleRow(
                    iconRes = R.drawable.ic_vpn_key_24dp,
                    titleRes = R.string.pref_update_sync_unmetered_only,
                    checked = updateOnlyOnWifi,
                    enabled = checkHostsDaily,
                    onCheckedChange = onUpdateOnlyWifiChanged
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
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
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
                .background(iconTint.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = titleColor.copy(alpha = 0.7f),
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



