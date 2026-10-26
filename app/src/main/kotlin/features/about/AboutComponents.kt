// Copyright 2026, AsteriskBOX contributors
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ProjectInfo
import app.R
import ui.components.AsteriskListRow
import ui.components.AsteriskSection
import ui.theme.AsteriskShapeTokens

private const val DeveloperTelegramUri = "https://t.me/SELOOM1"
private const val TelegramChannelUri = "https://t.me/FREEVPSIRAQ"
private const val AboutIconForegroundScale = 1.0f

@Composable
internal fun AboutIdentityHeader(
    modifier: Modifier = Modifier,
) {
    val identity = buildAboutIdentityState(
        projectName = ProjectInfo.PROJECT_NAME,
        versionName = ProjectInfo.VERSION_NAME,
        versionCode = ProjectInfo.VERSION_CODE,
        androidLibBoxLiteVersion = ProjectInfo.ANDROID_LIB_BOX_LITE_VERSION,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AboutAppIcon(Modifier.size(96.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = identity.projectName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = identity.versionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "تطوير: @SELOOM1 • قناة التلجرام: @FREEVPSIRAQ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun AboutAppIcon(
    modifier: Modifier = Modifier,
) {
    val iconStyle = aboutIconStyle()
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            shape = AsteriskShapeTokens.PageCard,
            color = iconStyle.containerColor,
        ) {
            Image(
                painter = painterResource(iconStyle.foregroundResId),
                contentDescription = ProjectInfo.PROJECT_NAME,
                contentScale = ContentScale.Fit,
                colorFilter = iconStyle.foregroundTint?.let(ColorFilter::tint),
                modifier = Modifier.fillMaxSize().scale(AboutIconForegroundScale),
            )
        }
    }
}

@Composable
private fun aboutIconStyle(): AboutIconStyle {
    return AboutIconStyle(
        foregroundResId = R.drawable.ic_launcher_foreground,
        foregroundTint = null,
        containerColor = Color.Transparent,
    )
}

private data class AboutIconStyle(
    val foregroundResId: Int,
    val foregroundTint: Color?,
    val containerColor: Color,
)

@Composable
internal fun AboutRuntimeSection(
    modifier: Modifier = Modifier,
) {
    AsteriskSection(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.about_runtime),
    ) {
        AboutRuntimeRow(
            "AndroidLibBoxLite",
            ProjectInfo.ANDROID_LIB_BOX_LITE_VERSION,
            Icons.Rounded.Memory,
        )
        AboutRuntimeRow("hev-socks5-tunnel", ProjectInfo.HEV_SOCKS5_TUNNEL_VERSION, Icons.Rounded.VpnLock)
    }
}

@Composable
private fun AboutRuntimeRow(name: String, version: String, icon: ImageVector) {
    AsteriskListRow(
        title = name,
        summary = version,
        leadingIcon = icon,
    )
}

@Composable
internal fun AboutLinksSection(
    title: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    AsteriskSection(
        modifier = modifier.fillMaxWidth(),
        title = title,
    ) {
        AboutLinkRow(
            title = "قناة التلجرام الرسمية (@FREEVPSIRAQ)",
            icon = Icons.AutoMirrored.Rounded.Send,
            onClick = { uriHandler.openUri(TelegramChannelUri) },
        )
        AboutLinkRow(
            title = "المطور الرسمي (@SELOOM1)",
            icon = Icons.Rounded.Code,
            onClick = { uriHandler.openUri(DeveloperTelegramUri) },
        )
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AsteriskListRow(
        title = title,
        leadingIcon = icon,
        onClick = onClick,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
