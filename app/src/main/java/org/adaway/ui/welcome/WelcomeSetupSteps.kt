package org.adaway.ui.welcome

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.provider.Settings
import android.provider.Settings.ACTION_VPN_SETTINGS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.error.HostError
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.safeClickable
import org.adaway.ui.home.HomeViewModel
import org.adaway.ui.support.SupportActivity
import org.adaway.util.log.SentryLog

private enum class SetupMethod {
    NONE,
    ROOT,
    VPN
}

private data class MethodEntry(
    val iconRes: Int,
    val textRes: Int,
    val tint: Color
)

@Composable
internal fun WelcomeMethodStep(onCanProceedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var selectedMethod by rememberSaveable { mutableStateOf(SetupMethod.NONE) }

    val prepareVpnLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            SentryLog.recordBreadcrumb("Enable vpn ad-blocking method")
            PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.VPN)
            selectedMethod = SetupMethod.VPN
        } else {
            PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.UNDEFINED)
            selectedMethod = SetupMethod.NONE
            showAlwaysOnVpnDialogIfNeeded(context)
        }
    }

    LaunchedEffect(selectedMethod) {
        onCanProceedChange(selectedMethod != SetupMethod.NONE)
    }

    val onRootClick = {
        PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.UNDEFINED)
        selectedMethod = SetupMethod.NONE

        Shell.getShell()
        if (java.lang.Boolean.TRUE == Shell.isAppGrantedRoot()) {
            SentryLog.recordBreadcrumb("Enable root ad-blocking method")
            PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.ROOT)
            selectedMethod = SetupMethod.ROOT
        } else {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.welcome_root_missing_title)
                .setMessage(R.string.welcome_root_missile_description)
                .setPositiveButton(R.string.button_close, null)
                .create()
                .show()
        }
    }

    val onVpnClick = {
        PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.UNDEFINED)
        selectedMethod = SetupMethod.NONE

        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent == null) {
            SentryLog.recordBreadcrumb("Enable vpn ad-blocking method")
            PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.VPN)
            selectedMethod = SetupMethod.VPN
        } else {
            prepareVpnLauncher.launch(prepareIntent)
        }
    }

    ExpressivePage {
        Icon(
            painter = painterResource(R.drawable.ic_outline_rule_24),
            contentDescription = stringResource(R.string.app_name),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 16.dp)
                .size(96.dp)
        )

        Text(
            text = stringResource(R.string.welcome_method_header),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        )

        Text(
            text = stringResource(R.string.welcome_method_summary),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WelcomeMethodCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                selected = selectedMethod == SetupMethod.ROOT,
                iconRes = R.drawable.ic_superuser_24dp,
                iconDescription = R.string.welcome_root_method_logo,
                titleRes = R.string.welcome_root_method_title,
                entries = listOf(
                    MethodEntry(
                        iconRes = R.drawable.ic_add_circle_outline_24dp,
                        textRes = R.string.welcome_root_method_text1,
                        tint = MaterialTheme.colorScheme.primary
                    ),
                    MethodEntry(
                        iconRes = R.drawable.ic_add_circle_outline_24dp,
                        textRes = R.string.welcome_root_method_text2,
                        tint = MaterialTheme.colorScheme.primary
                    ),
                    MethodEntry(
                        iconRes = R.drawable.ic_remove_circle_outline_24dp,
                        textRes = R.string.welcome_root_method_text3,
                        tint = MaterialTheme.colorScheme.error
                    )
                ),
                onClick = onRootClick
            )

            WelcomeMethodCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                selected = selectedMethod == SetupMethod.VPN,
                iconRes = R.drawable.ic_vpn_key_24dp,
                iconDescription = R.string.welcome_vpn_method_logo,
                titleRes = R.string.welcome_vpn_method_title,
                entries = listOf(
                    MethodEntry(
                        iconRes = R.drawable.ic_remove_circle_outline_24dp,
                        textRes = R.string.welcome_vpn_method_text1,
                        tint = MaterialTheme.colorScheme.error
                    ),
                    MethodEntry(
                        iconRes = R.drawable.ic_remove_circle_outline_24dp,
                        textRes = R.string.welcome_vpn_method_text2,
                        tint = MaterialTheme.colorScheme.error
                    ),
                    MethodEntry(
                        iconRes = R.drawable.ic_add_circle_outline_24dp,
                        textRes = R.string.welcome_vpn_method_text3,
                        tint = MaterialTheme.colorScheme.primary
                    )
                ),
                onClick = onVpnClick
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
internal fun WelcomeSyncStep(
    onCanProceedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as WelcomeActivity
    val homeViewModel = remember(activity) {
        ViewModelProvider(activity)[HomeViewModel::class.java]
    }

    var headerTextRes by rememberSaveable { mutableIntStateOf(R.string.welcome_sync_header) }
    var showProgress by rememberSaveable { mutableStateOf(true) }
    var showSyncedIcon by rememberSaveable { mutableStateOf(false) }
    var showErrorIcon by rememberSaveable { mutableStateOf(false) }
    var showRetry by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf("") }
    var showNotificationsText by rememberSaveable { mutableStateOf(false) }
    var syncStarted by rememberSaveable { mutableStateOf(false) }
    var requestNotificationsPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { _ -> }

    val adBlocked by homeViewModel.isAdBlocked().observeAsState()
    val error by homeViewModel.error.observeAsState()

    LaunchedEffect(Unit) {
        onCanProceedChange(false)

        if (!syncStarted) {
            syncStarted = true
            homeViewModel.sync()
        }

        if (
            SDK_INT >= TIRAMISU &&
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            showNotificationsText = true
            requestNotificationsPermission = true
            delay(10_000)
            if (requestNotificationsPermission) {
                requestNotificationsPermission = false
                permissionLauncher.launch(POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(adBlocked) {
        if (adBlocked == true) {
            homeViewModel.enableAllSources()
            headerTextRes = R.string.welcome_synced_header
            showProgress = false
            showSyncedIcon = true
            showErrorIcon = false
            showRetry = false
            errorText = ""
            onCanProceedChange(true)

            if (SDK_INT >= TIRAMISU && requestNotificationsPermission) {
                requestNotificationsPermission = false
                permissionLauncher.launch(POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(error) {
        val hostError: HostError = error ?: return@LaunchedEffect
        val errorMessage = context.getString(hostError.messageKey)
        errorText = context.getString(R.string.welcome_sync_error, errorMessage)
        showProgress = false
        showSyncedIcon = false
        showErrorIcon = true
        showRetry = true
        onCanProceedChange(false)
    }

    val onRetry = {
        showErrorIcon = false
        showRetry = false
        errorText = ""
        showProgress = true
        onCanProceedChange(false)
        homeViewModel.sync()
    }

    ExpressivePage {
        Spacer(modifier = Modifier.size(32.dp))

        AnimatedContent(
            targetState = Triple(showProgress, showSyncedIcon, showErrorIcon),
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                    fadeOut(animationSpec = tween(90))
            },
            label = "statusIconTransition"
        ) { (progress, synced, showError) ->
            when {
                progress -> CircularProgressIndicator(
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                synced -> Icon(
                    painter = painterResource(R.drawable.baseline_check_24),
                    contentDescription = stringResource(R.string.welcome_sync_done_logo),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(120.dp)
                )

                showError -> Icon(
                    painter = painterResource(R.drawable.ic_cloud_off_24dp),
                    contentDescription = stringResource(R.string.welcome_sync_error_logo),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(120.dp)
                )
            }
        }

        AnimatedContent(
            targetState = headerTextRes,
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                    fadeOut(animationSpec = tween(90))
            },
            label = "headerTransition"
        ) { targetHeader ->
            Text(
                text = stringResource(targetHeader),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            )
        }

        ExpressiveSection(modifier = Modifier.padding(top = 32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.welcome_sync_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showRetry) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .safeClickable(onClick = onRetry)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync_24dp),
                            contentDescription = stringResource(R.string.welcome_sync_retry_logo),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        )
                    }
                }
            }
        }

        if (showNotificationsText) {
            Text(
                text = stringResource(R.string.welcome_sync_notifications),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
internal fun WelcomeSupportStep(onCanProceedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as? Application
    val showSponsorship = remember { SentryLog.isStub() }
    var telemetryEnabled by rememberSaveable {
        mutableStateOf(PreferenceHelper.getTelemetryEnabled(context))
    }

    LaunchedEffect(Unit) {
        onCanProceedChange(true)
    }

    val onSupportClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, SupportActivity.SUPPORT_LINK))
    }
    val onSponsorshipClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, SupportActivity.SPONSORSHIP_LINK))
    }
    val onTelemetryChanged = { enabled: Boolean ->
        telemetryEnabled = enabled
        PreferenceHelper.setTelemetryEnabled(context, enabled)
        if (application != null) {
            SentryLog.setEnabled(application, enabled)
        }
    }

    val heartTransition = rememberInfiniteTransition(label = "welcomeHeart")
    val heartScale by heartTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "welcomeHeartScale"
    )

    ExpressivePage {
        Icon(
            painter = painterResource(R.drawable.baseline_favorite_24),
            contentDescription = stringResource(R.string.welcome_support_logo),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 16.dp)
                .size(100.dp)
                .scale(heartScale)
                .safeClickable(onClick = onSupportClick)
        )

        Text(
            text = stringResource(R.string.welcome_support_header),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        )

        Text(
            text = stringResource(R.string.welcome_support_summary),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        WelcomeSupportActionCard(
            label = stringResource(R.string.welcome_support_button),
            icon = {
                Image(
                    painter = painterResource(R.drawable.paypal),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            },
            onClick = onSupportClick
        )

        if (showSponsorship) {
            WelcomeSupportActionCard(
                label = stringResource(R.string.support_sponsorship_button),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_github_32dp),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onSponsorshipClick
            )
        } else {
            ExpressiveSection(
                modifier = Modifier.padding(top = 24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text(
                        text = stringResource(R.string.welcome_support_telemetry_summary),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .safeClickable { onTelemetryChanged(!telemetryEnabled) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Checkbox(
                            checked = telemetryEnabled,
                            onCheckedChange = onTelemetryChanged
                        )
                        Text(
                            text = stringResource(R.string.welcome_support_telemetry_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun WelcomeMethodCard(
    modifier: Modifier = Modifier,
    selected: Boolean,
    iconRes: Int,
    iconDescription: Int,
    titleRes: Int,
    entries: List<MethodEntry>,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "cardColor"
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 8.dp else 2.dp,
        label = "cardElevation"
    )

    ElevatedCard(
        modifier = modifier.safeClickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = stringResource(iconDescription),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            entries.forEach { entry ->
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        painter = painterResource(entry.iconRes),
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            entry.tint
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(entry.textRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeSupportActionCard(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    ExpressiveSection(
        modifier = Modifier.safeClickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun showAlwaysOnVpnDialogIfNeeded(context: Context) {
    var alwaysOnMessage = R.string.welcome_vpn_alwayson_description
    try {
        val alwaysOn = Settings.Secure.getString(context.contentResolver, "always_on_vpn_app")
        if (alwaysOn == null) {
            return
        }
    } catch (_: SecurityException) {
        alwaysOnMessage = R.string.welcome_vpn_alwayson_blocked_description
    }

    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.welcome_vpn_alwayson_title)
        .setMessage(alwaysOnMessage)
        .setNegativeButton(R.string.button_close, null)
        .setPositiveButton(R.string.welcome_vpn_alwayson_settings_action) { dialog, _ ->
            dialog.dismiss()
            context.startActivity(Intent(ACTION_VPN_SETTINGS))
        }
        .create()
        .show()
}
