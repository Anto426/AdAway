package org.adaway.ui.prefs

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.adaway.helper.ThemeHelper
import org.adaway.ui.compose.ExpressiveAppContainer
import org.adaway.ui.compose.ExpressiveScaffold

/**
 * This activity is the preferences activity.
 */
class PrefsActivity : AppCompatActivity() {
    private var destination by mutableStateOf(PrefsDestination.MAIN)
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)
        destination = savedInstanceState
            ?.getString(DESTINATION_KEY)
            ?.let { route -> PrefsDestination.entries.firstOrNull { it.name == route } }
            ?: PrefsDestination.MAIN

        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigateUpInPrefs()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        supportActionBar?.hide()

        setContent {
            ExpressiveAppContainer {
                PrefsActivityScreen(
                    destination = destination,
                    onNavigateBack = {
                        if (!navigateUpInPrefs()) {
                            finish()
                        }
                    },
                    onNavigate = ::navigateTo,
                    onRequestRecreate = ::recreate
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(DESTINATION_KEY, destination.name)
        super.onSaveInstanceState(outState)
    }

    private fun navigateTo(destination: PrefsDestination) {
        this.destination = destination
    }

    private fun navigateUpInPrefs(): Boolean {
        return if (destination != PrefsDestination.MAIN) {
            navigateTo(PrefsDestination.MAIN)
            true
        } else {
            false
        }
    }

    companion object {
        private const val DESTINATION_KEY = "prefs_destination_key"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PrefsActivityScreen(
    destination: PrefsDestination,
    onNavigateBack: () -> Unit,
    onNavigate: (PrefsDestination) -> Unit,
    onRequestRecreate: () -> Unit
) {
    ExpressiveScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(destination.titleRes),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrefsContent(
                destination = destination,
                onNavigate = onNavigate,
                onRequestRecreate = onRequestRecreate
            )
        }
    }
}
