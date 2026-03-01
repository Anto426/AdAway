package org.adaway.ui.lists

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import org.adaway.R
import org.adaway.db.entity.HostListItem
import org.adaway.db.entity.HostsSource
import org.adaway.db.entity.ListType
import org.adaway.helper.ThemeHelper
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar
import org.adaway.ui.compose.ExpressiveAppContainer
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.safeCombinedClickable
import org.adaway.util.Clipboard
import org.adaway.util.RegexUtils

/**
 * This activity displays and manages host lists.
 */
class ListsActivity : AppCompatActivity() {
    private lateinit var listsViewModel: ListsViewModel
    private var currentTab by androidx.compose.runtime.mutableIntStateOf(BLOCKED_HOSTS_TAB)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        supportActionBar?.hide()

        listsViewModel = ViewModelProvider(this)[ListsViewModel::class.java]
        currentTab = intent.getIntExtra(TAB, BLOCKED_HOSTS_TAB).coerceIn(
            BLOCKED_HOSTS_TAB,
            REDIRECTED_HOSTS_TAB
        )

        val blockedFlow = listsViewModel.blockedListItems.toFlow(this)
        val allowedFlow = listsViewModel.allowedListItems.toFlow(this)
        val redirectedFlow = listsViewModel.redirectedListItems.toFlow(this)

        setContent {
            ExpressiveAppContainer {
                ListsScreen(
                    initialTab = currentTab,
                    blockedItemsFlow = blockedFlow,
                    allowedItemsFlow = allowedFlow,
                    redirectedItemsFlow = redirectedFlow,
                    onNavigateBack = {
                        listsViewModel.clearSearch()
                        onBackPressedDispatcher.onBackPressed()
                    },
                    onToggleSources = listsViewModel::toggleSources,
                    onSearchQueryChanged = { query ->
                        if (query.isNullOrBlank()) {
                            listsViewModel.clearSearch()
                        } else {
                            listsViewModel.search(query)
                        }
                    },
                    onTabChanged = { currentTab = it },
                    onToggleItemEnabled = listsViewModel::toggleItemEnabled,
                    onAddItem = listsViewModel::addListItem,
                    onUpdateItem = listsViewModel::updateListItem,
                    onDeleteItem = listsViewModel::removeListItem
                )
            }
        }

        val applySnackbar = ApplyConfigurationSnackbar(window.decorView, false, false)
        listsViewModel.modelChanged.observe(this, applySnackbar.createObserver())
    }

    companion object {
        const val TAB: String = "org.adaway.lists.tab"
        const val BLOCKED_HOSTS_TAB: Int = 0
        const val ALLOWED_HOSTS_TAB: Int = 1
        const val REDIRECTED_HOSTS_TAB: Int = 2
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ListsScreen(
    initialTab: Int,
    blockedItemsFlow: Flow<PagingData<HostListItem>>,
    allowedItemsFlow: Flow<PagingData<HostListItem>>,
    redirectedItemsFlow: Flow<PagingData<HostListItem>>,
    onNavigateBack: () -> Unit,
    onToggleSources: () -> Unit,
    onSearchQueryChanged: (String?) -> Unit,
    onTabChanged: (Int) -> Unit,
    onToggleItemEnabled: (HostListItem) -> Unit,
    onAddItem: (ListType, String, String?) -> Unit,
    onUpdateItem: (HostListItem, String, String?) -> Unit,
    onDeleteItem: (HostListItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, ListsTab.entries.lastIndex),
        pageCount = { ListsTab.entries.size }
    )

    val blockedItems = blockedItemsFlow.collectAsLazyPagingItems()
    val allowedItems = allowedItemsFlow.collectAsLazyPagingItems()
    val redirectedItems = redirectedItemsFlow.collectAsLazyPagingItems()

    var dialogState by remember { mutableStateOf<ListDialogState?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pagerState.currentPage) {
        onTabChanged(pagerState.currentPage)
    }

    BackHandler(enabled = searchVisible) {
        searchVisible = false
        searchQuery = ""
        onSearchQueryChanged(null)
    }

    ExpressiveScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.lists_title),
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
                    IconButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) {
                                searchQuery = ""
                                onSearchQueryChanged(null)
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_search_24),
                            contentDescription = stringResource(R.string.lists_menu_filter)
                        )
                    }
                    IconButton(onClick = onToggleSources) {
                        Icon(
                            painter = painterResource(R.drawable.ic_collections_bookmark_24dp),
                            contentDescription = stringResource(R.string.lists_menu_toggle_sources)
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
        },
        bottomBar = {
            ListsBottomNavigation(
                selectedTab = pagerState.currentPage,
                onTabSelected = { tab ->
                    scope.launch {
                        pagerState.animateScrollToPage(tab)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val tab = ListsTab.fromPosition(pagerState.currentPage)
                    dialogState = ListDialogState.forAdd(tab)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_black_24px),
                    contentDescription = stringResource(R.string.lists_add)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (searchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { value ->
                        searchQuery = value
                        onSearchQueryChanged(value.ifBlank { null })
                    },
                    label = { Text(stringResource(R.string.lists_menu_filter_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (ListsTab.fromPosition(page)) {
                    ListsTab.BLOCKED -> {
                        HostsListPage(
                            pagingItems = blockedItems,
                            showRedirection = false,
                            onToggleItemEnabled = onToggleItemEnabled,
                            onEditItem = { item -> dialogState = ListDialogState.forEdit(ListsTab.BLOCKED, item) },
                            onDeleteItem = onDeleteItem,
                            onCopyHost = { host -> Clipboard.copyHostToClipboard(context, host) }
                        )
                    }

                    ListsTab.ALLOWED -> {
                        HostsListPage(
                            pagingItems = allowedItems,
                            showRedirection = false,
                            onToggleItemEnabled = onToggleItemEnabled,
                            onEditItem = { item -> dialogState = ListDialogState.forEdit(ListsTab.ALLOWED, item) },
                            onDeleteItem = onDeleteItem,
                            onCopyHost = { host -> Clipboard.copyHostToClipboard(context, host) }
                        )
                    }

                    ListsTab.REDIRECTED -> {
                        HostsListPage(
                            pagingItems = redirectedItems,
                            showRedirection = true,
                            onToggleItemEnabled = onToggleItemEnabled,
                            onEditItem = { item -> dialogState = ListDialogState.forEdit(ListsTab.REDIRECTED, item) },
                            onDeleteItem = onDeleteItem,
                            onCopyHost = { host -> Clipboard.copyHostToClipboard(context, host) }
                        )
                    }
                }
            }
        }
    }

    dialogState?.let { state ->
        HostListDialog(
            state = state,
            onDismiss = { dialogState = null },
            onConfirm = { host, redirection ->
                val normalizedHost = host.trim()
                val normalizedRedirection = redirection?.trim()?.ifEmpty { null }
                val itemToEdit = state.itemToEdit
                if (itemToEdit == null) {
                    onAddItem(state.listType, normalizedHost, normalizedRedirection)
                } else {
                    onUpdateItem(itemToEdit, normalizedHost, normalizedRedirection)
                }
                dialogState = null
            }
        )
    }
}

@Composable
private fun ListsBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        ListsTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab.position,
                onClick = { onTabSelected(tab.position) },
                icon = { Icon(painterResource(tab.iconRes), null) },
                label = { Text(stringResource(tab.labelRes)) }
            )
        }
    }
}

@Composable
private fun HostsListPage(
    pagingItems: LazyPagingItems<HostListItem>,
    showRedirection: Boolean,
    onToggleItemEnabled: (HostListItem) -> Unit,
    onEditItem: (HostListItem) -> Unit,
    onDeleteItem: (HostListItem) -> Unit,
    onCopyHost: (String) -> Unit
) {
    val refreshState = pagingItems.loadState.refresh
    when {
        refreshState is LoadState.Loading && pagingItems.itemCount == 0 -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        refreshState is LoadState.NotLoading && pagingItems.itemCount == 0 -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.lists_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    count = pagingItems.itemCount,
                    key = { index ->
                        val item = pagingItems[index]
                        if (item == null) {
                            index
                        } else {
                            "${item.sourceId}:${item.host}:${item.type.value}"
                        }
                    }
                ) { index ->
                    val item = pagingItems[index] ?: return@items
                    HostListRow(
                        item = item,
                        showRedirection = showRedirection,
                        onToggleItemEnabled = onToggleItemEnabled,
                        onEditItem = onEditItem,
                        onDeleteItem = onDeleteItem,
                        onCopyHost = onCopyHost
                    )
                }

                if (pagingItems.loadState.append is LoadState.Loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostListRow(
    item: HostListItem,
    showRedirection: Boolean,
    onToggleItemEnabled: (HostListItem) -> Unit,
    onEditItem: (HostListItem) -> Unit,
    onDeleteItem: (HostListItem) -> Unit,
    onCopyHost: (String) -> Unit
) {
    val editable = item.sourceId == HostsSource.USER_SOURCE_ID
    var menuExpanded by remember(item.id) { mutableStateOf(false) }

    Box {
        ExpressiveSection(
            modifier = Modifier
                .fillMaxWidth()
                .safeCombinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (editable) {
                            menuExpanded = true
                        } else {
                            onCopyHost(item.host)
                        }
                    }
                ),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = item.isEnabled,
                    onCheckedChange = { onToggleItemEnabled(item) },
                    enabled = editable
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.host,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (editable) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (showRedirection) {
                        Text(
                            text = item.redirection.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.checkbox_list_context_edit)) },
                onClick = {
                    menuExpanded = false
                    onEditItem(item)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.checkbox_list_context_delete)) },
                onClick = {
                    menuExpanded = false
                    onDeleteItem(item)
                }
            )
        }
    }
}

@Composable
private fun HostListDialog(
    state: ListDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var host by remember(state) { mutableStateOf(state.initialHost) }
    var redirection by remember(state) { mutableStateOf(state.initialRedirection.orEmpty()) }

    val hostValid = isHostValid(state.listType, host)
    val redirectionValid = !state.requiresRedirection || RegexUtils.isValidIP(redirection)
    val inputValid = hostValid && redirectionValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(state.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    singleLine = true,
                    isError = host.isNotBlank() && !hostValid,
                    label = { Text(stringResource(R.string.list_dialog_hostname)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.showWildcardHint) {
                    Text(
                        text = stringResource(R.string.list_dialog_wildcard),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.requiresRedirection) {
                    OutlinedTextField(
                        value = redirection,
                        onValueChange = { redirection = it },
                        singleLine = true,
                        isError = redirection.isNotBlank() && !redirectionValid,
                        label = { Text(stringResource(R.string.list_dialog_ip)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(host, if (state.requiresRedirection) redirection else null) },
                enabled = inputValid
            ) {
                Text(stringResource(state.confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

private fun isHostValid(type: ListType, host: String): Boolean {
    return when (type) {
        ListType.BLOCKED -> RegexUtils.isValidHostname(host)
        ListType.ALLOWED -> RegexUtils.isValidWildcardHostname(host)
        ListType.REDIRECTED -> RegexUtils.isValidHostname(host)
    }
}

private enum class ListsTab(
    val position: Int,
    val listType: ListType,
    val labelRes: Int,
    val iconRes: Int
) {
    BLOCKED(
        ListsActivity.BLOCKED_HOSTS_TAB,
        ListType.BLOCKED,
        R.string.lists_tab_blocked,
        R.drawable.baseline_block_24
    ),
    ALLOWED(
        ListsActivity.ALLOWED_HOSTS_TAB,
        ListType.ALLOWED,
        R.string.lists_tab_allowed,
        R.drawable.baseline_check_24
    ),
    REDIRECTED(
        ListsActivity.REDIRECTED_HOSTS_TAB,
        ListType.REDIRECTED,
        R.string.lists_tab_redirected,
        R.drawable.baseline_compare_arrows_24
    );

    companion object {
        fun fromPosition(position: Int): ListsTab {
            return entries.firstOrNull { it.position == position } ?: BLOCKED
        }
    }
}

private data class ListDialogState(
    val listType: ListType,
    val titleRes: Int,
    val confirmRes: Int,
    val initialHost: String,
    val initialRedirection: String?,
    val requiresRedirection: Boolean,
    val showWildcardHint: Boolean,
    val itemToEdit: HostListItem?
) {
    companion object {
        fun forAdd(tab: ListsTab): ListDialogState {
            return when (tab) {
                ListsTab.BLOCKED -> ListDialogState(
                    listType = ListType.BLOCKED,
                    titleRes = R.string.list_add_dialog_black,
                    confirmRes = R.string.button_add,
                    initialHost = "",
                    initialRedirection = null,
                    requiresRedirection = false,
                    showWildcardHint = false,
                    itemToEdit = null
                )

                ListsTab.ALLOWED -> ListDialogState(
                    listType = ListType.ALLOWED,
                    titleRes = R.string.list_add_dialog_white,
                    confirmRes = R.string.button_add,
                    initialHost = "",
                    initialRedirection = null,
                    requiresRedirection = false,
                    showWildcardHint = true,
                    itemToEdit = null
                )

                ListsTab.REDIRECTED -> ListDialogState(
                    listType = ListType.REDIRECTED,
                    titleRes = R.string.list_add_dialog_redirect,
                    confirmRes = R.string.button_add,
                    initialHost = "",
                    initialRedirection = "0.0.0.0",
                    requiresRedirection = true,
                    showWildcardHint = false,
                    itemToEdit = null
                )
            }
        }

        fun forEdit(tab: ListsTab, item: HostListItem): ListDialogState {
            return when (tab) {
                ListsTab.BLOCKED -> ListDialogState(
                    listType = ListType.BLOCKED,
                    titleRes = R.string.list_edit_dialog_black,
                    confirmRes = R.string.button_save,
                    initialHost = item.host,
                    initialRedirection = null,
                    requiresRedirection = false,
                    showWildcardHint = false,
                    itemToEdit = item
                )

                ListsTab.ALLOWED -> ListDialogState(
                    listType = ListType.ALLOWED,
                    titleRes = R.string.list_edit_dialog_white,
                    confirmRes = R.string.button_save,
                    initialHost = item.host,
                    initialRedirection = null,
                    requiresRedirection = false,
                    showWildcardHint = true,
                    itemToEdit = item
                )

                ListsTab.REDIRECTED -> ListDialogState(
                    listType = ListType.REDIRECTED,
                    titleRes = R.string.list_edit_dialog_redirect,
                    confirmRes = R.string.button_save,
                    initialHost = item.host,
                    initialRedirection = item.redirection,
                    requiresRedirection = true,
                    showWildcardHint = false,
                    itemToEdit = item
                )
            }
        }
    }
}

private fun <T> LiveData<T>.toFlow(owner: LifecycleOwner): Flow<T> {
    return callbackFlow {
        val observer = Observer<T> { value ->
            trySend(value)
        }
        observe(owner, observer)
        awaitClose {
            removeObserver(observer)
        }
    }.conflate()
}
