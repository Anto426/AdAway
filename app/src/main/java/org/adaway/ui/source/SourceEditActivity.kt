package org.adaway.ui.source

import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.db.AppDatabase
import org.adaway.db.dao.HostsSourceDao
import org.adaway.db.entity.HostsSource
import org.adaway.db.entity.SourceType
import org.adaway.helper.ThemeHelper
import org.adaway.ui.compose.ExpressiveAppContainer
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.util.AppExecutors
import java.util.concurrent.Executor

/**
 * This activity create, edit and delete a hosts source.
 */
class SourceEditActivity : AppCompatActivity() {
    private lateinit var hostsSourceDao: HostsSourceDao
    private lateinit var startActivityLauncher: ActivityResultLauncher<Intent>
    private var editing by mutableStateOf(false)
    private var edited: HostsSource? = null
    private var screenState by mutableStateOf(SourceEditScreenState())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        hostsSourceDao = AppDatabase.getInstance(this).hostsSourceDao()
        registerForStartActivity()

        screenState = screenState.copy(
            urlLocation = getString(R.string.source_edit_url_location_default)
        )

        checkInitialValueFromIntent()

        supportActionBar?.hide()
        setContent {
            ExpressiveAppContainer {
                SourceEditScreen(
                    state = screenState,
                    editing = editing,
                    onNavigateBack = ::finish,
                    onSave = ::saveSource,
                    onDelete = ::deleteEditedSource,
                    onLabelChanged = { value ->
                        screenState = screenState.copy(label = value, labelError = null)
                    },
                    onFormatSelected = ::setAllowFormat,
                    onTypeSelected = ::setType,
                    onUrlChanged = { value ->
                        screenState = screenState.copy(urlLocation = value, locationError = null)
                    },
                    onFileLocationClick = ::openDocument,
                    onRedirectedChanged = { checked ->
                        screenState = screenState.copy(redirectedHosts = checked)
                    }
                )
            }
        }
    }

    private fun registerForStartActivity() {
        startActivityLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data
            val uri: Uri? = data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // No persisted grant from provider.
                }
                screenState = screenState.copy(
                    fileLocation = uri.toString(),
                    locationError = null
                )
            }
        }
    }

    private fun checkInitialValueFromIntent() {
        val sourceId = intent.getIntExtra(SOURCE_ID, -1)
        editing = sourceId != -1
        if (editing) {
            DISK_IO_EXECUTOR.execute {
                hostsSourceDao.getById(sourceId).ifPresent { source ->
                    edited = source
                    MAIN_THREAD_EXECUTOR.execute {
                        applyInitialValues(source)
                    }
                }
            }
        } else {
            editing = false
        }
    }

    private fun applyInitialValues(source: HostsSource) {
        val isAllow = source.isAllowEnabled
        val type = when (source.type) {
            SourceType.FILE -> SourceInputType.FILE
            else -> SourceInputType.URL
        }
        screenState = screenState.copy(
            label = source.label,
            allowFormat = isAllow,
            type = type,
            urlLocation = if (type == SourceInputType.URL) {
                source.url
            } else {
                getString(R.string.source_edit_url_location_default)
            },
            fileLocation = if (type == SourceInputType.FILE) source.url else "",
            redirectedHosts = source.isRedirectEnabled,
            labelError = null,
            locationError = null
        )
    }

    private fun setAllowFormat(allowFormat: Boolean) {
        screenState = screenState.copy(
            allowFormat = allowFormat,
            redirectedHosts = if (allowFormat) false else screenState.redirectedHosts
        )
    }

    private fun setType(type: SourceInputType) {
        if (screenState.type == type) {
            return
        }
        screenState = screenState.copy(
            type = type,
            locationError = null,
            urlLocation = if (type == SourceInputType.URL && screenState.urlLocation.isBlank()) {
                getString(R.string.source_edit_url_location_default)
            } else {
                screenState.urlLocation
            }
        )
        if (type == SourceInputType.FILE) {
            openDocument()
        }
    }

    private fun validate(): HostsSource? {
        var state = screenState.copy(labelError = null, locationError = null)
        val label = state.label.trim()
        if (label.isEmpty()) {
            state = state.copy(labelError = R.string.source_edit_label_required)
        }

        val url = if (state.type == SourceInputType.URL) {
            val value = state.urlLocation.trim()
            if (value.isEmpty()) {
                state = state.copy(locationError = R.string.source_edit_url_location_required)
            } else if (!HostsSource.isValidUrl(value)) {
                state = state.copy(locationError = R.string.source_edit_location_invalid)
            }
            value
        } else {
            val value = state.fileLocation.trim()
            if (!HostsSource.isValidUrl(value)) {
                state = state.copy(locationError = R.string.source_edit_location_invalid)
            }
            value
        }

        screenState = state
        if (state.labelError != null || state.locationError != null) {
            return null
        }

        return HostsSource().apply {
            this.label = label
            this.url = url
            setAllowEnabled(state.allowFormat)
            setRedirectEnabled(!state.allowFormat && state.redirectedHosts)
        }
    }

    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = ANY_MIME_TYPE
            addFlags(FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityLauncher.launch(intent)
    }

    private fun saveSource() {
        val source = validate() ?: return
        DISK_IO_EXECUTOR.execute {
            if (editing) {
                edited?.let { hostsSourceDao.delete(it) }
            }
            hostsSourceDao.insert(source)
            MAIN_THREAD_EXECUTOR.execute { finish() }
        }
    }

    private fun deleteEditedSource() {
        val source = edited ?: return
        DISK_IO_EXECUTOR.execute {
            hostsSourceDao.delete(source)
            MAIN_THREAD_EXECUTOR.execute { finish() }
        }
    }

    companion object {
        @JvmField
        val SOURCE_ID: String = "sourceId"

        private const val ANY_MIME_TYPE = "*/*"
        private val DISK_IO_EXECUTOR: Executor = AppExecutors.getInstance().diskIO()
        private val MAIN_THREAD_EXECUTOR: Executor = AppExecutors.getInstance().mainThread()
    }
}

private enum class SourceInputType {
    URL,
    FILE
}

private data class SourceEditScreenState(
    val label: String = "",
    @param:StringRes @field:StringRes val labelError: Int? = null,
    val allowFormat: Boolean = false,
    val type: SourceInputType = SourceInputType.URL,
    val urlLocation: String = "",
    val fileLocation: String = "",
    @param:StringRes @field:StringRes val locationError: Int? = null,
    val redirectedHosts: Boolean = false
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SourceEditScreen(
    state: SourceEditScreenState,
    editing: Boolean,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onFormatSelected: (Boolean) -> Unit,
    onTypeSelected: (SourceInputType) -> Unit,
    onUrlChanged: (String) -> Unit,
    onFileLocationClick: () -> Unit,
    onRedirectedChanged: (Boolean) -> Unit
) {
    ExpressiveScaffold(
        topBar = {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(
                        if (editing) R.string.source_edit_title else R.string.source_edit_add_title
                    ),
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
            actions = {
                if (editing) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.outline_delete_24),
                            contentDescription = stringResource(R.string.checkbox_list_context_delete)
                        )
                    }
                }
                IconButton(onClick = onSave) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_check_24),
                        contentDescription = stringResource(R.string.checkbox_list_context_apply)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {

        ExpressiveSection {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.source_edit_label),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.label,
                    onValueChange = onLabelChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.source_edit_label)) },
                    singleLine = true,
                    isError = state.labelError != null,
                    supportingText = {
                        state.labelError?.let { error ->
                            Text(text = stringResource(error))
                        }
                    },
                    shape = MaterialTheme.shapes.medium
                )
            }
        }

        ExpressiveSection {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.source_edit_format),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SourceToggleButton(
                        text = stringResource(R.string.source_edit_format_block_list),
                        selected = !state.allowFormat,
                        modifier = Modifier.weight(1f),
                        onClick = { onFormatSelected(false) }
                    )
                    SourceToggleButton(
                        text = stringResource(R.string.source_edit_format_allow_list),
                        selected = state.allowFormat,
                        modifier = Modifier.weight(1f),
                        onClick = { onFormatSelected(true) }
                    )
                }
            }
        }

        ExpressiveSection {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.source_edit_type),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SourceToggleButton(
                        text = stringResource(R.string.source_edit_url),
                        selected = state.type == SourceInputType.URL,
                        modifier = Modifier.weight(1f),
                        onClick = { onTypeSelected(SourceInputType.URL) }
                    )
                    SourceToggleButton(
                        text = stringResource(R.string.source_edit_file),
                        selected = state.type == SourceInputType.FILE,
                        modifier = Modifier.weight(1f),
                        onClick = { onTypeSelected(SourceInputType.FILE) }
                    )
                }

                if (state.type == SourceInputType.URL) {
                    OutlinedTextField(
                        value = state.urlLocation,
                        onValueChange = onUrlChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        label = { Text(text = stringResource(R.string.source_edit_url_location)) },
                        singleLine = true,
                        isError = state.locationError != null,
                        supportingText = {
                            state.locationError?.let { error ->
                                Text(text = stringResource(error))
                            }
                        },
                        shape = MaterialTheme.shapes.medium
                    )
                } else {
                    val fileLocation = state.fileLocation.ifEmpty {
                        stringResource(R.string.source_edit_file_hint)
                    }
                    OutlinedButton(
                        onClick = onFileLocationClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = fileLocation,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    state.locationError?.let { error ->
                        Text(
                            text = stringResource(error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp, start = 16.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = !state.allowFormat) {
            ExpressiveSection {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.redirectedHosts,
                            onCheckedChange = onRedirectedChanged
                        )
                        Text(
                            text = stringResource(R.string.source_edit_redirected_hosts),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = stringResource(R.string.source_edit_redirected_hosts_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SourceToggleButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}
