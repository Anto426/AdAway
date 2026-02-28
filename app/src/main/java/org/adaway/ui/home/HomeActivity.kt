package org.adaway.ui.home

import org.adaway.ui.compose.safeClickable

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.adaway.R
import org.adaway.helper.NotificationHelper
import org.adaway.helper.PreferenceHelper
import org.adaway.helper.ThemeHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.error.HostError
import org.adaway.ui.compose.ExpressiveAppContainer
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.help.HelpActivity
import org.adaway.ui.hosts.HostsSourcesActivity
import org.adaway.ui.lists.ListsActivity
import org.adaway.ui.log.LogActivity
import org.adaway.ui.prefs.PrefsActivity
import org.adaway.ui.support.SupportActivity
import org.adaway.ui.update.UpdateActivity
import org.adaway.ui.welcome.WelcomeActivity
import timber.log.Timber

/**
 * This class is the application main activity.
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var prepareVpnLauncher: ActivityResultLauncher<Intent>

    private var screenState by mutableStateOf(HomeScreenState())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)
        NotificationHelper.clearUpdateNotifications(this)
        Timber.i("Starting main activity")

        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        screenState = screenState.copy(versionName = homeViewModel.versionName)

        bindBackPress()
        bindViewModel()
        prepareVpnLauncher = registerForActivityResult(StartActivityForResult()) {}

        setContent {
            ExpressiveAppContainer {
                HomeScreen(
                    state = screenState,
                    onToggleAdBlocking = { homeViewModel.toggleAdBlocking() },
                    onOpenDrawer = { setDrawerVisible(true) },
                    onCloseDrawer = { setDrawerVisible(false) },
                    onOpenUpdate = ::startUpdateActivity,
                    onOpenBlockedList = { startHostListActivity(ListsActivity.BLOCKED_HOSTS_TAB) },
                    onOpenAllowedList = { startHostListActivity(ListsActivity.ALLOWED_HOSTS_TAB) },
                    onOpenRedirectedList = { startHostListActivity(ListsActivity.REDIRECTED_HOSTS_TAB) },
                    onOpenSources = ::startHostsSourcesActivity,
                    onCheckSources = { homeViewModel.update() },
                    onSyncSources = { homeViewModel.sync() },
                    onOpenLog = ::startDnsLogActivity,
                    onOpenHelp = ::startHelpActivity,
                    onOpenSupport = ::startSupportActivity,
                    onOpenPreferences = {
                        setDrawerVisible(false)
                        startPrefsActivity()
                    },
                    onOpenProjectPage = {
                        setDrawerVisible(false)
                        showProjectPage()
                    }
                )
            }
        }

        if (savedInstanceState == null) {
            checkUpdateAtStartup()
        }
    }

    override fun onResume() {
        super.onResume()
        checkFirstStep()
    }

    private fun bindBackPress() {
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                setDrawerVisible(false)
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun bindViewModel() {
        homeViewModel.isAdBlocked().observe(this) { adBlocked ->
            screenState = screenState.copy(adBlocked = adBlocked == true)
        }
        homeViewModel.appManifest.observe(this) { manifest ->
            screenState = screenState.copy(updateAvailable = manifest?.updateAvailable == true)
        }
        homeViewModel.blockedHostCount.observe(this) { count ->
            screenState = screenState.copy(blockedHostCount = count ?: 0)
        }
        homeViewModel.allowedHostCount.observe(this) { count ->
            screenState = screenState.copy(allowedHostCount = count ?: 0)
        }
        homeViewModel.redirectHostCount.observe(this) { count ->
            screenState = screenState.copy(redirectHostCount = count ?: 0)
        }
        homeViewModel.upToDateSourceCount.observe(this) { count ->
            screenState = screenState.copy(upToDateSourceCount = count ?: 0)
        }
        homeViewModel.outdatedSourceCount.observe(this) { count ->
            screenState = screenState.copy(outdatedSourceCount = count ?: 0)
        }
        homeViewModel.pending.observe(this) { pending ->
            screenState = screenState.copy(pending = pending == true)
        }
        homeViewModel.state.observe(this) { state ->
            screenState = screenState.copy(stateText = state.orEmpty())
        }
        homeViewModel.error.observe(this, ::notifyError)
    }

    private fun setDrawerVisible(visible: Boolean) {
        screenState = screenState.copy(drawerVisible = visible)
        onBackPressedCallback.isEnabled = visible
    }

    private fun checkFirstStep() {
        val adBlockMethod = PreferenceHelper.getAdBlockMethod(this)
        if (adBlockMethod == AdBlockMethod.UNDEFINED) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        if (adBlockMethod == AdBlockMethod.VPN) {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                prepareVpnLauncher.launch(prepareIntent)
            }
        }
    }

    private fun checkUpdateAtStartup() {
        if (PreferenceHelper.getUpdateCheckAppStartup(this)) {
            homeViewModel.checkForAppUpdate()
        }
        if (PreferenceHelper.getUpdateCheck(this)) {
            homeViewModel.update()
        }
    }

    private fun startHostListActivity(tab: Int) {
        val intent = Intent(this, ListsActivity::class.java)
        intent.putExtra(ListsActivity.TAB, tab)
        startActivity(intent)
    }

    private fun startHostsSourcesActivity() {
        startActivity(Intent(this, HostsSourcesActivity::class.java))
    }

    private fun startHelpActivity() {
        startActivity(Intent(this, HelpActivity::class.java))
    }

    private fun startSupportActivity() {
        startActivity(Intent(this, SupportActivity::class.java))
    }

    private fun startPrefsActivity() {
        startActivity(Intent(this, PrefsActivity::class.java))
    }

    private fun startDnsLogActivity() {
        startActivity(Intent(this, LogActivity::class.java))
    }

    private fun startUpdateActivity() {
        startActivity(Intent(this, UpdateActivity::class.java))
    }

    private fun showProjectPage() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_LINK)))
    }

    private fun notifyError(error: HostError?) {
        if (error == null) {
            return
        }
        val message = getString(error.detailsKey) + "\n\n" + getString(R.string.error_dialog_help)
        MaterialAlertDialogBuilder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(error.messageKey)
            .setMessage(message)
            .setPositiveButton(R.string.button_close) { dialog, _ -> dialog.dismiss() }
            .setNegativeButton(R.string.button_help) { dialog, _ ->
                dialog.dismiss()
                startHelpActivity()
            }
            .create()
            .show()
    }

    companion object {
        private const val PROJECT_LINK = "https://github.com/AdAway/AdAway"
    }
}

private data class HomeScreenState(
    val versionName: String = "",
    val updateAvailable: Boolean = false,
    val blockedHostCount: Int = 0,
    val allowedHostCount: Int = 0,
    val redirectHostCount: Int = 0,
    val upToDateSourceCount: Int = 0,
    val outdatedSourceCount: Int = 0,
    val pending: Boolean = false,
    val stateText: String = "",
    val adBlocked: Boolean = false,
    val drawerVisible: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: HomeScreenState,
    onToggleAdBlocking: () -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenBlockedList: () -> Unit,
    onOpenAllowedList: () -> Unit,
    onOpenRedirectedList: () -> Unit,
    onOpenSources: () -> Unit,
    onCheckSources: () -> Unit,
    onSyncSources: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenProjectPage: () -> Unit
) {
    ExpressiveScaffold(
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                actions = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu_24dp),
                            contentDescription = stringResource(R.string.open_drawer_button_description)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onCheckSources) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync_24dp),
                            contentDescription = stringResource(R.string.update_button)
                        )
                    }
                    IconButton(onClick = onOpenLog) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playlist_add_24dp),
                            contentDescription = stringResource(R.string.show_log_button)
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = onToggleAdBlocking,
                        containerColor = if (state.adBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (state.adBlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (state.adBlocked) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pause_24dp),
                                contentDescription = stringResource(R.string.adblock_pause_button_description)
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.logo),
                                contentDescription = stringResource(R.string.app_logo),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(
                state = state,
                onOpenUpdate = onOpenUpdate
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeMetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.baseline_block_24,
                    iconTint = colorResource(R.color.blocked),
                    count = state.blockedHostCount,
                    labelRes = R.string.blocked_hosts_label,
                    onClick = onOpenBlockedList
                )
                HomeMetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.baseline_check_24,
                    iconTint = colorResource(R.color.allowed),
                    count = state.allowedHostCount,
                    labelRes = R.string.allowed_hosts_label,
                    onClick = onOpenAllowedList
                )
                HomeMetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.baseline_compare_arrows_24,
                    iconTint = colorResource(R.color.redirected),
                    count = state.redirectHostCount,
                    labelRes = R.string.redirect_hosts_label,
                    onClick = onOpenRedirectedList
                )
            }

            SourceStatusSection(
                state = state,
                onOpenSources = onOpenSources,
                onCheckSources = onCheckSources,
                onSyncSources = onSyncSources
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeQuickActionCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.ic_outline_rule_24,
                    labelRes = R.string.log_label,
                    onClick = onOpenLog
                )
                HomeQuickActionCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.ic_help_24dp,
                    labelRes = R.string.help_label,
                    onClick = onOpenHelp
                )
                HomeQuickActionCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.baseline_favorite_24,
                    labelRes = R.string.support_label,
                    onClick = onOpenSupport
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (state.drawerVisible) {
            ModalBottomSheet(
                onDismissRequest = onCloseDrawer,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(bottom = 48.dp, top = 8.dp)) {
                    HomeDrawerItem(
                        label = stringResource(R.string.hosts_title),
                        iconRes = R.drawable.ic_collections_bookmark_24dp,
                        onClick = { 
                            onCloseDrawer()
                            onOpenSources() 
                        }
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.shortcut_your_lists),
                        iconRes = R.drawable.ic_list_red,
                        onClick = { 
                            onCloseDrawer()
                            onOpenBlockedList() 
                        }
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.log_label),
                        iconRes = R.drawable.ic_outline_rule_24,
                        onClick = { 
                            onCloseDrawer()
                            onOpenLog() 
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
                    
                    HomeDrawerItem(
                        label = stringResource(R.string.preferences_drawer_item),
                        iconRes = R.drawable.ic_settings_24dp,
                        onClick = onOpenPreferences
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.github_project_drawer_item),
                        iconRes = R.drawable.ic_github_24dp,
                        onClick = onOpenProjectPage
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.help_label),
                        iconRes = R.drawable.ic_help_24dp,
                        onClick = {
                            onCloseDrawer()
                            onOpenHelp()
                        }
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.support_label),
                        iconRes = R.drawable.baseline_favorite_24,
                        onClick = {
                            onCloseDrawer()
                            onOpenSupport()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDrawerItem(
    label: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            ) 
        },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.safeClickable(onClick = onClick)
    )
}

@Composable
private fun HomeHeader(
    state: HomeScreenState,
    onOpenUpdate: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = if (state.adBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(500),
        label = "statusColor"
    )
    val onStatusColor by animateColorAsState(
        targetValue = if (state.adBlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(500),
        label = "onStatusColor"
    )

    ExpressiveSection(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.adBlocked) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = onStatusColor
                        )
                        Text(
                            text = if (state.adBlocked) stringResource(R.string.status_enabled_subtitle) else stringResource(R.string.status_disabled_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onStatusColor.copy(alpha = 0.85f)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(onStatusColor.copy(alpha = 0.15f))
                            .safeClickable(onClick = onOpenUpdate)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (state.updateAvailable) stringResource(R.string.update_available) else state.versionName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = onStatusColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = stringResource(R.string.app_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMetricCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    iconTint: Color,
    count: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit
) {
    ExpressiveSection(
        modifier = modifier.safeClickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                },
                label = "countTransition"
            ) { targetCount ->
                Text(
                    text = targetCount.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SourceStatusSection(
    state: HomeScreenState,
    onOpenSources: () -> Unit,
    onCheckSources: () -> Unit,
    onSyncSources: () -> Unit
) {
    ExpressiveSection(
        modifier = Modifier
            .padding(top = 16.dp)
            .safeClickable(onClick = onOpenSources),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_collections_bookmark_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.up_to_date_source_label,
                            state.upToDateSourceCount,
                            state.upToDateSourceCount
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.outdated_source_label,
                            state.outdatedSourceCount,
                            state.outdatedSourceCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.outdatedSourceCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCheckSources,
                        modifier = Modifier.then(if (state.pending) Modifier.size(0.dp) else Modifier)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync_24dp),
                            contentDescription = stringResource(R.string.check_hosts_update_description),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onSyncSources,
                        modifier = Modifier.then(if (state.pending) Modifier.size(0.dp) else Modifier)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_get_app_24dp),
                            contentDescription = stringResource(R.string.update_hosts_description),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            AnimatedContent(
                targetState = state.pending,
                label = "progressTransition"
            ) { isPending ->
                if (isPending) {
                    Column(modifier = Modifier.padding(top = 20.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            strokeCap = StrokeCap.Round,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        if (state.stateText.isNotEmpty()) {
                            Text(
                                text = state.stateText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeQuickActionCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit
) {
    ExpressiveSection(
        modifier = modifier.safeClickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}



