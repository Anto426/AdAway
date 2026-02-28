package org.adaway.ui.prefs

import org.adaway.ui.compose.safeClickable

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import org.adaway.R
import org.adaway.model.backup.BackupExporter
import org.adaway.model.backup.BackupImporter
import org.adaway.ui.compose.ExpressiveBackground
import org.adaway.ui.compose.ExpressiveSection

/**
 * Compose version of backup and restore preferences.
 */
class PrefsBackupRestoreFragment : Fragment() {
    private lateinit var importActivityLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var exportActivityLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerForImportActivity()
        registerForExportActivity()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        setTitle()
    }

    override fun onResume() {
        super.onResume()
        setTitle()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return ComposeView(requireContext()).apply {
            setContent {
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
        }
    }

    private fun setTitle() {
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.pref_backup_restore_title)
    }

    private fun registerForImportActivity() {
        importActivityLauncher = registerForActivityResult(object : OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return super.createIntent(context, input)
                    .addCategory(Intent.CATEGORY_OPENABLE)
            }
        }) { backupUri ->
            if (backupUri != null) {
                BackupImporter.importFromBackup(requireContext(), backupUri)
            }
        }
    }

    private fun registerForExportActivity() {
        exportActivityLauncher = registerForActivityResult(
            CreateDocument(JSON_MIME_TYPE)
        ) { backupUri ->
            if (backupUri != null) {
                BackupExporter.exportToBackup(requireContext(), backupUri)
            }
        }
    }

    companion object {
        private const val JSON_MIME_TYPE = "application/json"
        private const val BACKUP_FILE_NAME = "adaway-backup.json"
    }
}

@Composable
private fun PrefsBackupRestoreScreen(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    ExpressiveBackground {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ExpressiveSection {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    BackupPreferenceRow(
                        iconRes = R.drawable.ic_save_24dp,
                        titleRes = R.string.pref_backup,
                        summaryRes = R.string.pref_backup_summary,
                        onClick = onBackupClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BackupPreferenceRow(
                        iconRes = R.drawable.ic_settings_backup_restore_24dp,
                        titleRes = R.string.pref_restore,
                        summaryRes = R.string.pref_restore_summary,
                        onClick = onRestoreClick
                    )
                }
            }
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun BackupPreferenceRow(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .safeClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(summaryRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}



