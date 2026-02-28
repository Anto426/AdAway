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

/**
 * Compose version of root preferences.
 */
class PrefsRootFragment : Fragment() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var openHostsFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var prepareCertificateLauncher: ActivityResultLauncher<String>

    private var neverReboot by mutableStateOf(false)
    private var redirectionIpv4 by mutableStateOf("")
    private var redirectionIpv6 by mutableStateOf("")
    private var ipv6Enabled by mutableStateOf(false)
    private var webServerEnabled by mutableStateOf(false)
    private var webServerIcon by mutableStateOf(false)
    private var webServerStateSummaryRes by mutableStateOf(R.string.pref_webserver_state_checking)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerForOpenHostActivity()
        registerForPrepareCertificateActivity()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reloadState(context)
        setTitle()
    }

    override fun onResume() {
        super.onResume()
        reloadState(requireContext())
        updateWebServerState()
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
                    PrefsRootScreen(
                        neverReboot = neverReboot,
                        redirectionIpv4 = redirectionIpv4,
                        redirectionIpv6 = redirectionIpv6,
                        ipv6Enabled = ipv6Enabled,
                        webServerEnabled = webServerEnabled,
                        webServerIcon = webServerIcon,
                        webServerStateSummaryRes = webServerStateSummaryRes,
                        onOpenHostsFile = ::openHostsFile,
                        onNeverRebootChanged = { enabled ->
                            neverReboot = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_never_reboot_key), enabled)
                                .apply()
                        },
                        onEditIpv4 = { showRedirectionDialog(Inet4Address::class.java, it) },
                        onEditIpv6 = { showRedirectionDialog(Inet6Address::class.java, it) },
                        onWebServerEnabledChanged = ::onWebServerEnabledChanged,
                        onWebServerTest = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL)))
                        },
                        onInstallCertificate = {
                            if (SDK_INT < VERSION_CODES.R) {
                                installCertificate(requireContext())
                            } else {
                                prepareCertificateLauncher.launch("adaway-webserver-certificate.crt")
                            }
                        },
                        onWebServerIconChanged = { enabled ->
                            webServerIcon = enabled
                            prefs.edit()
                                .putBoolean(getString(R.string.pref_webserver_icon_key), enabled)
                                .apply()
                            if (isWebServerRunning()) {
                                stopWebServer()
                                startWebServer(requireContext())
                                updateWebServerState()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun setTitle() {
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.pref_root_title)
    }

    private fun reloadState(context: Context) {
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

    private fun registerForOpenHostActivity() {
        openHostsFileLauncher = registerForActivityResult(StartActivityForResult()) {
            try {
                val hostFile = File(ANDROID_SYSTEM_ETC_HOSTS).canonicalFile
                remountPartition(hostFile, READ_ONLY)
            } catch (e: IOException) {
                Timber.e(e, "Failed to get hosts canonical file.")
            }
        }
    }

    private fun registerForPrepareCertificateActivity() {
        prepareCertificateLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument(CERTIFICATE_MIME_TYPE)
        ) { uri ->
            prepareWebServerCertificate(uri)
        }
    }

    private fun openHostsFile() {
        try {
            val hostFile = File(ANDROID_SYSTEM_ETC_HOSTS).canonicalFile
            val remount = !isWritable(hostFile) && remountPartition(hostFile, READ_WRITE)
            val intent = Intent()
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("file://${hostFile.absolutePath}"), "text/plain")
            if (remount) {
                openHostsFileLauncher.launch(intent)
            } else {
                startActivity(intent)
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to get hosts canonical file.")
        } catch (e: ActivityNotFoundException) {
            MissingAppDialog.showTextEditorMissingDialog(context)
        }
    }

    private fun showRedirectionDialog(addressType: Class<out InetAddress>, initialValue: String) {
        val editText = EditText(requireContext()).apply {
            setSingleLine(true)
            setText(initialValue)
            setSelection(text.length)
        }
        val dialogView = FrameLayout(requireContext()).apply {
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
        MaterialAlertDialogBuilder(requireContext())
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
                if (validateRedirection(addressType, redirection)) {
                    if (addressType == Inet4Address::class.java) {
                        redirectionIpv4 = redirection
                        prefs.edit()
                            .putString(getString(R.string.pref_redirection_ipv4_key), redirection)
                            .apply()
                    } else {
                        redirectionIpv6 = redirection
                        prefs.edit()
                            .putString(getString(R.string.pref_redirection_ipv6_key), redirection)
                            .apply()
                    }
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun validateRedirection(addressType: Class<out InetAddress>, redirection: String): Boolean {
        val valid = try {
            val inetAddress = InetAddresses.forString(redirection)
            addressType.isAssignableFrom(inetAddress.javaClass)
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!valid) {
            Toast.makeText(requireContext(), R.string.pref_redirection_invalid, Toast.LENGTH_SHORT).show()
        }
        return valid
    }

    private fun onWebServerEnabledChanged(enabled: Boolean) {
        val context = requireContext()
        if (enabled) {
            startWebServer(context)
            webServerEnabled = isWebServerRunning()
        } else {
            stopWebServer()
            webServerEnabled = isWebServerRunning()
        }
        prefs.edit()
            .putBoolean(getString(R.string.pref_webserver_enabled_key), webServerEnabled)
            .apply()
        updateWebServerState()
    }

    private fun prepareWebServerCertificate(uri: Uri?) {
        if (uri == null) {
            return
        }
        Timber.d("Certificate URI: %s", uri)
        copyCertificate(requireActivity(), uri)
        MaterialAlertDialogBuilder(requireContext())
            .setCancelable(true)
            .setTitle(R.string.pref_webserver_certificate_dialog_title)
            .setMessage(R.string.pref_webserver_certificate_dialog_content)
            .setPositiveButton(R.string.pref_webserver_certificate_dialog_action) { dialog, _ ->
                dialog.dismiss()
                startActivity(Intent(ACTION_SECURITY_SETTINGS))
            }
            .create()
            .show()
    }

    private fun updateWebServerState() {
        webServerStateSummaryRes = R.string.pref_webserver_state_checking
        val executors = AppExecutors.getInstance()
        executors.networkIO().execute {
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val summaryResId = getWebServerState()
            executors.mainThread().execute {
                webServerStateSummaryRes = summaryResId
            }
        }
    }

    companion object {
        private const val CERTIFICATE_MIME_TYPE = "application/x-x509-ca-cert"
    }
}

@Composable
private fun PrefsRootScreen(
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
