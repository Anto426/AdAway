package org.adaway.ui.prefs.exclusion

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.helper.ThemeHelper
import org.adaway.ui.compose.ExpressiveAppContainer
import org.adaway.ui.compose.ExpressiveBackground
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.safeClickable

/**
 * This activity allows selecting user applications excluded from VPN routing.
 */
class PrefsVpnExcludedAppsActivity : AppCompatActivity(), ExcludedAppController {
    private var userApplications: Array<UserApp>? = null
    private var uiVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        setContent {
            // Read to trigger recomposition when exclusion changes.
            @Suppress("UNUSED_VARIABLE")
            val version = uiVersion
            val apps = getUserApplications().toList()
            ExpressiveAppContainer {
                VpnExcludedAppsScreen(
                    applications = apps,
                    onToggleExcluded = { app, excluded ->
                        if (excluded) {
                            excludeApplications(app)
                        } else {
                            includeApplications(app)
                        }
                    }
                )
            }
        }

        val actionBar: ActionBar? = supportActionBar
        actionBar?.setDisplayShowTitleEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.vpn_excluded_app_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.select_all -> {
                excludeApplications(*getUserApplications())
                return true
            }

            R.id.deselect_all -> {
                includeApplications(*getUserApplications())
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getUserApplications(): Array<UserApp> {
        val cachedApplications = userApplications
        if (cachedApplications != null) {
            return cachedApplications
        }

        val packageManager: PackageManager = packageManager
        val self = applicationInfo
        val excludedApps = PreferenceHelper.getVpnExcludedApps(this)
        val installedApplications = packageManager.getInstalledApplications(0)

        val applications = installedApplications
            .asSequence()
            .filter { applicationInfo ->
                (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .filter { applicationInfo ->
                applicationInfo.packageName != self.packageName
            }
            .map { applicationInfo ->
                UserApp(
                    packageManager.getApplicationLabel(applicationInfo),
                    applicationInfo.packageName,
                    packageManager.getApplicationIcon(applicationInfo),
                    excludedApps.contains(applicationInfo.packageName)
                )
            }
            .sorted()
            .toList()
            .toTypedArray()

        userApplications = applications
        return applications
    }

    override fun excludeApplications(vararg applications: UserApp) {
        for (application in applications) {
            application.excluded = true
        }
        updatePreferences()
    }

    override fun includeApplications(vararg applications: UserApp) {
        for (application in applications) {
            application.excluded = false
        }
        updatePreferences()
    }

    private fun updatePreferences() {
        val excludedApplicationPackageNames = getUserApplications()
            .filter { userApp -> userApp.excluded }
            .map { userApp -> userApp.packageName.toString() }
            .toSet()
        PreferenceHelper.setVpnExcludedApps(this, excludedApplicationPackageNames)
        uiVersion++
    }
}

@Composable
private fun VpnExcludedAppsScreen(
    applications: List<UserApp>,
    onToggleExcluded: (UserApp, Boolean) -> Unit
) {
    ExpressiveBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = applications,
                key = { application -> application.packageName.toString() }
            ) { application ->
                UserAppCard(
                    application = application,
                    onToggle = { checked -> onToggleExcluded(application, checked) }
                )
            }
        }
    }
}

@Composable
private fun UserAppCard(
    application: UserApp,
    onToggle: (Boolean) -> Unit
) {
    ExpressiveSection(
        modifier = Modifier.safeClickable { onToggle(!application.excluded) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        val iconSizeDp = 40.dp
        val iconSizePx = with(LocalDensity.current) { iconSizeDp.roundToPx() }
        val iconBitmap = remember(application.icon, iconSizePx) {
            application.icon
                .toBitmap(width = iconSizePx, height = iconSizePx)
                .asImageBitmap()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(iconSizeDp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = application.name.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = application.packageName.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Switch(
                    checked = application.excluded,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}
