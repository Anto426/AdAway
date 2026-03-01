package org.adaway.ui.prefs

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.ACTION_SECURITY_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.view.ContextThemeWrapper
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.net.InetAddresses
import org.adaway.AdAwayApplication
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.backup.BackupExporter
import org.adaway.model.backup.BackupImporter
import org.adaway.model.root.MountType.READ_ONLY
import org.adaway.model.root.MountType.READ_WRITE
import org.adaway.model.root.ShellUtils.isWritable
import org.adaway.model.root.ShellUtils.remountPartition
import org.adaway.model.source.SourceUpdateService
import org.adaway.model.update.ApkUpdateService
import org.adaway.model.update.UpdateStore
import org.adaway.ui.dialog.MissingAppDialog
import org.adaway.ui.prefs.exclusion.PrefsVpnExcludedAppsActivity
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
import org.adaway.util.log.SentryLog
import org.adaway.vpn.VpnServiceControls
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import timber.log.Timber

internal enum class PrefsDestination(@param:StringRes @field:StringRes val titleRes: Int) {
    MAIN(R.string.pref_main_title),
    UPDATE(R.string.pref_update_title),
    ROOT(R.string.pref_root_title),
    VPN(R.string.pref_vpn_title),
    BACKUP_RESTORE(R.string.pref_backup_restore_title)
}

@Composable
internal fun PrefsContent(
    destination: PrefsDestination,
    onNavigate: (PrefsDestination) -> Unit,
    onRequestRecreate: () -> Unit
) {
    when (destination) {
        PrefsDestination.MAIN -> PrefsMainRoute(onNavigate = onNavigate, onRequestRecreate = onRequestRecreate)
        PrefsDestination.UPDATE -> PrefsUpdateRoute()
        PrefsDestination.ROOT -> PrefsRootRoute()
        PrefsDestination.VPN -> PrefsVpnRoute()
        PrefsDestination.BACKUP_RESTORE -> PrefsBackupRestoreRoute()
    }
}

@Composable
private fun PrefsMainRoute(
    onNavigate: (PrefsDestination) -> Unit,
    onRequestRecreate: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var darkThemeMode by remember { mutableStateOf(context.getString(R.string.pref_dark_theme_mode_def)) }
    var enableIpv6 by remember { mutableStateOf(false) }
    var enableTelemetry by remember { mutableStateOf(false) }
    var enableDebug by remember { mutableStateOf(false) }
    var telemetrySupported by remember { mutableStateOf(true) }
    var adBlockMethod by remember { mutableStateOf(AdBlockMethod.UNDEFINED) }

    fun reloadState() {
        darkThemeMode = prefs.getString(
            context.getString(R.string.pref_dark_theme_mode_key),
            context.getString(R.string.pref_dark_theme_mode_def)
        ) ?: context.getString(R.string.pref_dark_theme_mode_def)
        enableIpv6 = PreferenceHelper.getEnableIpv6(context)
        enableTelemetry = PreferenceHelper.getTelemetryEnabled(context)
        enableDebug = PreferenceHelper.getDebugEnabled(context)
        adBlockMethod = PreferenceHelper.getAdBlockMethod(context)
        telemetrySupported = !SentryLog.isStub()
    }

    LaunchedEffect(context) {
        reloadState()
    }
    ObserveOnResume(onResume = ::reloadState)

    PrefsMainScreen(
        darkThemeMode = darkThemeMode,
        enableIpv6 = enableIpv6,
        enableTelemetry = enableTelemetry,
        enableDebug = enableDebug,
        telemetrySupported = telemetrySupported,
        rootConfigEnabled = adBlockMethod == AdBlockMethod.ROOT,
        vpnConfigEnabled = adBlockMethod == AdBlockMethod.VPN,
        onThemeSelected = { mode ->
            darkThemeMode = mode
            prefs.edit()
                .putString(context.getString(R.string.pref_dark_theme_mode_key), mode)
                .apply()
            onRequestRecreate()
        },
        onOpenUpdate = { onNavigate(PrefsDestination.UPDATE) },
        onOpenRootConfig = { onNavigate(PrefsDestination.ROOT) },
        onOpenVpnConfig = { onNavigate(PrefsDestination.VPN) },
        onEnableIpv6Changed = { enabled ->
            enableIpv6 = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_enable_ipv6_key), enabled)
                .apply()
        },
        onOpenBackupRestore = { onNavigate(PrefsDestination.BACKUP_RESTORE) },
        onEnableTelemetryChanged = { enabled ->
            if (!telemetrySupported) {
                return@PrefsMainScreen
            }
            enableTelemetry = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_enable_telemetry_key), enabled)
                .apply()
            SentryLog.setEnabled(context.applicationContext as Application, enabled)
        },
        onEnableDebugChanged = { enabled ->
            enableDebug = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_enable_debug_key), enabled)
                .apply()
        }
    )
}

@Composable
private fun PrefsUpdateRoute() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var notificationsDisabled by remember { mutableStateOf(false) }
    var checkAppStartup by remember { mutableStateOf(false) }
    var checkAppDaily by remember { mutableStateOf(false) }
    var includeBetaReleases by remember { mutableStateOf(false) }
    var includeBetaEnabled by remember { mutableStateOf(true) }
    var checkHostsStartup by remember { mutableStateOf(false) }
    var checkHostsDaily by remember { mutableStateOf(false) }
    var automaticUpdateDaily by remember { mutableStateOf(false) }
    var updateOnlyOnWifi by remember { mutableStateOf(false) }

    fun reloadState() {
        notificationsDisabled = Build.VERSION.SDK_INT >= TIRAMISU &&
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

    LaunchedEffect(context) {
        reloadState()
    }
    ObserveOnResume(onResume = ::reloadState)

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
        onOpenNotifications = {
            val settingsIntent = Intent(ACTION_APP_NOTIFICATION_SETTINGS)
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(settingsIntent)
        },
        onCheckAppStartupChanged = { enabled ->
            checkAppStartup = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_update_check_app_startup_key), enabled)
                .apply()
        },
        onCheckAppDailyChanged = { enabled ->
            checkAppDaily = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_update_check_app_daily_key), enabled)
                .apply()
            if (enabled) {
                ApkUpdateService.enable(context)
            } else {
                ApkUpdateService.disable(context)
            }
        },
        onIncludeBetaChanged = { enabled ->
            includeBetaReleases = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_update_include_beta_releases_key), enabled)
                .apply()
        },
        onCheckHostsStartupChanged = { enabled ->
            checkHostsStartup = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_update_check_key), enabled)
                .apply()
        },
        onCheckHostsDailyChanged = { enabled ->
            checkHostsDaily = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_update_check_hosts_daily_key), enabled)
                .apply()
            if (enabled) {
                SourceUpdateService.enable(context, updateOnlyOnWifi)
            } else {
                SourceUpdateService.disable(context)
            }
        },
        onAutomaticUpdateDailyChanged = { enabled ->
            automaticUpdateDaily = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_automatic_update_daily_key), enabled)
                .apply()
        },
        onUpdateOnlyWifiChanged = { enabled ->
            updateOnlyOnWifi = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_update_only_on_wifi_key), enabled)
                .apply()
            SourceUpdateService.enable(context, enabled)
        }
    )
}

@Composable
private fun PrefsRootRoute() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var neverReboot by remember { mutableStateOf(false) }
    var redirectionIpv4 by remember { mutableStateOf("") }
    var redirectionIpv6 by remember { mutableStateOf("") }
    var ipv6Enabled by remember { mutableStateOf(false) }
    var webServerEnabled by remember { mutableStateOf(false) }
    var webServerIcon by remember { mutableStateOf(false) }
    var webServerStateSummaryRes by remember { mutableStateOf(R.string.pref_webserver_state_checking) }

    val openHostsFileLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        try {
            val hostFile = File(ANDROID_SYSTEM_ETC_HOSTS).canonicalFile
            remountPartition(hostFile, READ_ONLY)
        } catch (exception: IOException) {
            Timber.e(exception, "Failed to get hosts canonical file.")
        }
    }
    val certificateLauncher = rememberLauncherForActivityResult(
        CreateDocument(CERTIFICATE_MIME_TYPE)
    ) { uri ->
        prepareWebServerCertificate(context, uri)
    }

    fun reloadState() {
        neverReboot = prefs.getBoolean(
            context.getString(R.string.pref_never_reboot_key),
            context.resources.getBoolean(R.bool.pref_never_reboot_def)
        )
        redirectionIpv4 = prefs.getString(
            context.getString(R.string.pref_redirection_ipv4_key),
            context.getString(R.string.pref_redirection_ipv4_def)
        ).orEmpty()
        redirectionIpv6 = prefs.getString(
            context.getString(R.string.pref_redirection_ipv6_key),
            context.getString(R.string.pref_redirection_ipv6_def)
        ).orEmpty()
        ipv6Enabled = PreferenceHelper.getEnableIpv6(context)
        webServerEnabled = PreferenceHelper.getWebServerEnabled(context)
        webServerIcon = PreferenceHelper.getWebServerIcon(context)
    }

    fun updateWebServerState() {
        webServerStateSummaryRes = R.string.pref_webserver_state_checking
        val executors = AppExecutors.getInstance()
        executors.networkIO().execute {
            try {
                Thread.sleep(500)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val summaryResId = getWebServerState()
            executors.mainThread().execute {
                webServerStateSummaryRes = summaryResId
            }
        }
    }

    LaunchedEffect(context) {
        reloadState()
        updateWebServerState()
    }
    ObserveOnResume {
        reloadState()
        updateWebServerState()
    }

    PrefsRootScreen(
        neverReboot = neverReboot,
        redirectionIpv4 = redirectionIpv4,
        redirectionIpv6 = redirectionIpv6,
        ipv6Enabled = ipv6Enabled,
        webServerEnabled = webServerEnabled,
        webServerIcon = webServerIcon,
        webServerStateSummaryRes = webServerStateSummaryRes,
        onOpenHostsFile = {
            try {
                val hostFile = File(ANDROID_SYSTEM_ETC_HOSTS).canonicalFile
                val remount = !isWritable(hostFile) && remountPartition(hostFile, READ_WRITE)
                val intent = Intent()
                    .setAction(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse("file://${hostFile.absolutePath}"), "text/plain")
                if (remount) {
                    openHostsFileLauncher.launch(intent)
                } else {
                    context.startActivity(intent)
                }
            } catch (exception: IOException) {
                Timber.e(exception, "Failed to get hosts canonical file.")
            } catch (_: ActivityNotFoundException) {
                MissingAppDialog.showTextEditorMissingDialog(context)
            }
        },
        onNeverRebootChanged = { enabled ->
            neverReboot = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_never_reboot_key), enabled)
                .apply()
        },
        onEditIpv4 = { value ->
            showRedirectionDialog(
                context = context,
                prefs = prefs,
                addressType = Inet4Address::class.java,
                initialValue = value,
                onSaved = { redirection -> redirectionIpv4 = redirection }
            )
        },
        onEditIpv6 = { value ->
            showRedirectionDialog(
                context = context,
                prefs = prefs,
                addressType = Inet6Address::class.java,
                initialValue = value,
                onSaved = { redirection -> redirectionIpv6 = redirection }
            )
        },
        onWebServerEnabledChanged = { enabled ->
            if (enabled) {
                startWebServer(context)
                webServerEnabled = isWebServerRunning()
            } else {
                stopWebServer()
                webServerEnabled = isWebServerRunning()
            }
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_webserver_enabled_key), webServerEnabled)
                .apply()
            updateWebServerState()
        },
        onWebServerTest = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL)))
        },
        onInstallCertificate = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                installCertificate(context)
            } else {
                certificateLauncher.launch("adaway-webserver-certificate.crt")
            }
        },
        onWebServerIconChanged = { enabled ->
            webServerIcon = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_webserver_icon_key), enabled)
                .apply()
            if (isWebServerRunning()) {
                stopWebServer()
                startWebServer(context)
                updateWebServerState()
            }
        }
    )
}

@Composable
private fun PrefsVpnRoute() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var serviceOnBoot by remember { mutableStateOf(false) }
    var watchdogEnabled by remember { mutableStateOf(false) }
    var excludedSystemApps by remember { mutableStateOf("") }

    fun reloadState() {
        serviceOnBoot = prefs.getBoolean(
            context.getString(R.string.pref_vpn_service_on_boot_key),
            context.resources.getBoolean(R.bool.pref_vpn_service_on_boot_def)
        )
        watchdogEnabled = prefs.getBoolean(
            context.getString(R.string.pref_vpn_watchdog_enabled_key),
            context.resources.getBoolean(R.bool.pref_vpn_watchdog_enabled_def)
        )
        excludedSystemApps = PreferenceHelper.getVpnExcludedSystemApps(context)
    }

    val startActivityLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        if (VpnServiceControls.isRunning(context)) {
            VpnServiceControls.stop(context)
            VpnServiceControls.start(context)
        }
    }

    LaunchedEffect(context) {
        reloadState()
    }
    ObserveOnResume(onResume = ::reloadState)

    PrefsVpnScreen(
        serviceOnBoot = serviceOnBoot,
        watchdogEnabled = watchdogEnabled,
        excludedSystemApps = excludedSystemApps,
        onServiceOnBootChanged = { enabled ->
            serviceOnBoot = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_vpn_service_on_boot_key), enabled)
                .apply()
        },
        onWatchdogChanged = { enabled ->
            watchdogEnabled = enabled
            prefs.edit()
                .putBoolean(context.getString(R.string.pref_vpn_watchdog_enabled_key), enabled)
                .apply()
        },
        onExcludedSystemAppsChanged = { value ->
            excludedSystemApps = value
            prefs.edit()
                .putString(context.getString(R.string.pref_vpn_excluded_system_apps_key), value)
                .apply()
            if (VpnServiceControls.isRunning(context)) {
                VpnServiceControls.stop(context)
                VpnServiceControls.start(context)
            }
        },
        onOpenExcludedUserApps = {
            val intent = Intent(context, PrefsVpnExcludedAppsActivity::class.java)
            startActivityLauncher.launch(intent)
        }
    )
}

@Composable
private fun PrefsBackupRestoreRoute() {
    val context = LocalContext.current
    val openDocumentContract = remember {
        object : OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
    }
    val importActivityLauncher = rememberLauncherForActivityResult(openDocumentContract) { backupUri ->
        if (backupUri != null) {
            BackupImporter.importFromBackup(context, backupUri)
        }
    }
    val exportActivityLauncher = rememberLauncherForActivityResult(
        CreateDocument(JSON_MIME_TYPE)
    ) { backupUri ->
        if (backupUri != null) {
            BackupExporter.exportToBackup(context, backupUri)
        }
    }

    PrefsBackupRestoreScreen(
        onBackupClick = { exportActivityLauncher.launch(BACKUP_FILE_NAME) },
        onRestoreClick = {
            val mimeTypes = when {
                Build.VERSION.SDK_INT < 28 -> arrayOf("*/*")
                Build.VERSION.SDK_INT < 29 -> arrayOf(JSON_MIME_TYPE, "application/octet-stream")
                else -> arrayOf(JSON_MIME_TYPE)
            }
            importActivityLauncher.launch(mimeTypes)
        }
    )
}

@Composable
private fun ObserveOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private fun showRedirectionDialog(
    context: Context,
    prefs: SharedPreferences,
    addressType: Class<out InetAddress>,
    initialValue: String,
    onSaved: (String) -> Unit
) {
    val editText = EditText(context).apply {
        setSingleLine(true)
        setText(initialValue)
        setSelection(text.length)
    }
    val dialogView = FrameLayout(context).apply {
        addView(
            editText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val padding = (24 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding / 2, padding, 0)
    }
    MaterialAlertDialogBuilder(context)
        .setCancelable(true)
        .setTitle(
            if (addressType == Inet4Address::class.java) {
                R.string.pref_redirection_ipv4
            } else {
                R.string.pref_redirection_ipv6
            }
        )
        .setView(dialogView)
        .setPositiveButton(R.string.button_save) { dialog, _ ->
            val redirection = editText.text.toString().trim()
            if (validateRedirection(context, addressType, redirection)) {
                if (addressType == Inet4Address::class.java) {
                    prefs.edit()
                        .putString(context.getString(R.string.pref_redirection_ipv4_key), redirection)
                        .apply()
                } else {
                    prefs.edit()
                        .putString(context.getString(R.string.pref_redirection_ipv6_key), redirection)
                        .apply()
                }
                onSaved(redirection)
                dialog.dismiss()
            }
        }
        .setNegativeButton(R.string.button_cancel) { dialog, _ -> dialog.dismiss() }
        .show()
}

private fun validateRedirection(
    context: Context,
    addressType: Class<out InetAddress>,
    redirection: String
): Boolean {
    val valid = try {
        val inetAddress = InetAddresses.forString(redirection)
        addressType.isAssignableFrom(inetAddress.javaClass)
    } catch (_: IllegalArgumentException) {
        false
    }
    if (!valid) {
        Toast.makeText(context, R.string.pref_redirection_invalid, Toast.LENGTH_SHORT).show()
    }
    return valid
}

private fun prepareWebServerCertificate(context: Context, uri: Uri?) {
    if (uri == null) {
        return
    }
    val wrapper = context as? ContextThemeWrapper ?: return
    Timber.d("Certificate URI: %s", uri)
    copyCertificate(wrapper, uri)
    MaterialAlertDialogBuilder(context)
        .setCancelable(true)
        .setTitle(R.string.pref_webserver_certificate_dialog_title)
        .setMessage(R.string.pref_webserver_certificate_dialog_content)
        .setPositiveButton(R.string.pref_webserver_certificate_dialog_action) { dialog, _ ->
            dialog.dismiss()
            context.startActivity(Intent(ACTION_SECURITY_SETTINGS))
        }
        .create()
        .show()
}

private const val CERTIFICATE_MIME_TYPE = "application/x-x509-ca-cert"
private const val JSON_MIME_TYPE = "application/json"
private const val BACKUP_FILE_NAME = "adaway-backup.json"
