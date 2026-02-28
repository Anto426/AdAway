package org.adaway.ui.support

import org.adaway.ui.compose.safeClickable

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.compose.AdAwayExpressiveTheme
import org.adaway.ui.compose.ExpressiveAppContainer
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.ExpressiveSection

/**
 * This class is an activity for users to show their support to the project.
 */
class SupportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExpressiveAppContainer {
                SupportContent(
                    onSupportClick = { openLink(SUPPORT_LINK) },
                    onSponsorshipClick = { openLink(SPONSORSHIP_LINK) }
                )
            }
        }
    }

    private fun openLink(uri: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    companion object {
        @JvmField
        val SUPPORT_LINK: Uri = Uri.parse("https://paypal.me/BruceBUJON")

        @JvmField
        val SPONSORSHIP_LINK: Uri = Uri.parse("https://github.com/sponsors/PerfectSlayer")
    }
}

@Composable
private fun SupportContent(onSupportClick: () -> Unit, onSponsorshipClick: () -> Unit) {
    val heartTransition = rememberInfiniteTransition(label = "heart")
    val heartScale by heartTransition.animateFloat(
        initialValue = 1F,
        targetValue = 1.25F,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    ExpressivePage {
        Icon(
            painter = painterResource(R.drawable.baseline_favorite_24),
            contentDescription = stringResource(R.string.welcome_support_logo),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 16.dp)
                .size(120.dp)
                .scale(heartScale)
                .safeClickable(onClick = onSupportClick)
        )

        Text(
            text = stringResource(R.string.welcome_support_header),
            style = MaterialTheme.typography.displaySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp)
        )

        Text(
            text = stringResource(R.string.welcome_support_summary),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        SupportActionCard(
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

        SupportActionCard(
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

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SupportActionCard(
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

@Preview(showBackground = true)
@Composable
private fun SupportPreview() {
    AdAwayExpressiveTheme {
        SupportContent(onSupportClick = {}, onSponsorshipClick = {})
    }
}



