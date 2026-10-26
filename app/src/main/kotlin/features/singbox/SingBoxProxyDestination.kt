// Copyright 2026, AsteriskBOX contributors
// SPDX-License-Identifier: GPL-3.0

package features.singbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import features.outbound.OutboundListPage

/**
 * SingBoxProxyDestination:
 * Shows the full OutboundListPage (Default & custom groups, server selection, add/import)
 * allowing users to freely select their server at any time before and after connection.
 */
@Composable
internal fun SingBoxProxyDestination(padding: PaddingValues) {
    OutboundListPage(padding = padding, embeddedInProxyTab = true)
}
