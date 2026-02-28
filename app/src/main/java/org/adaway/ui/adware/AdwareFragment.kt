package org.adaway.ui.adware

import org.adaway.ui.compose.safeClickable

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.adaway.R
import org.adaway.ui.compose.AdAwayExpressiveTheme
import org.adaway.ui.compose.ExpressiveSection

/**
 * This class is a [Fragment] to scan and uninstall adware.
 */
class AdwareFragment : Fragment() {
    private lateinit var adwareViewModel: AdwareViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        adwareViewModel = ViewModelProvider(requireActivity())[AdwareViewModel::class.java]
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AdAwayExpressiveTheme {
                    AdwareScreen(
                        adwareLiveData = adwareViewModel.adware,
                        onUninstall = ::uninstallAdware
                    )
                }
            }
        }
    }

    private fun uninstallAdware(adwareInstall: AdwareInstall) {
        val packageName = adwareInstall[AdwareInstall.PACKAGE_NAME_KEY] ?: return
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}

@Composable
private fun AdwareScreen(
    adwareLiveData: AdwareLiveData,
    onUninstall: (AdwareInstall) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val adwareState = remember { mutableStateOf<List<AdwareInstall>?>(null) }

    DisposableEffect(adwareLiveData, lifecycleOwner) {
        val observer = Observer<List<AdwareInstall>> { data ->
            adwareState.value = data
        }
        adwareLiveData.observe(lifecycleOwner, observer)
        onDispose {
            adwareLiveData.removeObserver(observer)
        }
    }

    ExpressiveSection(
        modifier = Modifier.padding(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.adware_header),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                text = stringResource(R.string.adware_description),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            val data = adwareState.value
            if (data == null) {
                Text(
                    text = stringResource(R.string.adware_scanning),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (data.isEmpty()) {
                Text(
                    text = stringResource(R.string.adware_empty),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                    modifier = Modifier.height(300.dp) // Limit height if needed, or let it expand
                ) {
                    items(data, key = { install ->
                        install[AdwareInstall.PACKAGE_NAME_KEY] ?: install.hashCode()
                    }) { install ->
                        AdwareInstallItem(install = install, onClick = { onUninstall(install) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdwareInstallItem(
    install: AdwareInstall,
    onClick: () -> Unit
) {
    val appName = install[AdwareInstall.APPLICATION_NAME_KEY].orEmpty()
    val packageName = install[AdwareInstall.PACKAGE_NAME_KEY].orEmpty()
    
    ExpressiveSection(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .safeClickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}



