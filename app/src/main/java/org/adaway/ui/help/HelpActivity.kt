package org.adaway.ui.help

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.helper.ThemeHelper
import org.adaway.ui.compose.ExpressiveAppContainer
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Help screen with HTML tabs.
 */
class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)
        supportActionBar?.hide()

        setContent {
            ExpressiveAppContainer {
                HelpScreen(onNavigateBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }
}

private data class HelpTab(
    @param:StringRes @field:StringRes val titleRes: Int,
    @param:RawRes @field:RawRes val rawRes: Int
)

private val helpTabs = listOf(
    HelpTab(R.string.help_tab_faq, R.raw.help_faq),
    HelpTab(R.string.help_tab_problems, R.raw.help_problems),
    HelpTab(R.string.help_tab_s_on_s_off, R.raw.help_s_on_s_off)
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HelpScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val tabContents = remember {
        helpTabs.map { tab ->
            Html.fromHtml(readRawResource(context, tab.rawRes), Html.FROM_HTML_MODE_LEGACY)
        }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.menu_help),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                helpTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = context.getString(tab.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            HelpHtmlView(
                html = tabContents[selectedTab],
                scrollState = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun HelpHtmlView(
    html: Spanned,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    val annotatedText = remember(html, linkColor, contentColor) {
        spannedToAnnotatedString(html, linkColor, contentColor)
    }

    Box(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        ClickableText(
            text = annotatedText,
            style = MaterialTheme.typography.bodyLarge,
            onClick = { offset ->
                annotatedText
                    .getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { annotation -> uriHandler.openUri(annotation.item) }
            }
        )
    }
}

private fun spannedToAnnotatedString(
    spanned: Spanned,
    linkColor: Color,
    defaultColor: Color
): AnnotatedString {
    val text = spanned.toString()
    val builder = AnnotatedString.Builder(text)
    builder.addStyle(SpanStyle(color = defaultColor), 0, text.length)

    spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
        val start = spanned.getSpanStart(span).coerceAtLeast(0)
        val end = spanned.getSpanEnd(span).coerceAtMost(text.length)
        if (start >= end) return@forEach

        when (span) {
            is StyleSpan -> {
                when (span.style) {
                    Typeface.BOLD -> {
                        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    }
                    Typeface.ITALIC -> {
                        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    }
                    Typeface.BOLD_ITALIC -> {
                        builder.addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            start,
                            end
                        )
                    }
                }
            }

            is UnderlineSpan -> {
                builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    start,
                    end
                )
            }

            is ForegroundColorSpan -> {
                builder.addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
            }

            is URLSpan -> {
                builder.addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    start,
                    end
                )
                builder.addStringAnnotation("URL", span.url, start, end)
            }
        }
    }

    return builder.toAnnotatedString()
}

private fun readRawResource(context: android.content.Context, @RawRes resourceId: Int): String {
    context.resources.openRawResource(resourceId).use { inputStream: InputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val content = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                content.append(line)
                line = reader.readLine()
            }
            return content.toString()
        }
    }
}
