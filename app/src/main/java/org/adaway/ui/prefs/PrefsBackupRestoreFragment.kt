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

@Composable
internal fun PrefsBackupRestoreScreen(
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



