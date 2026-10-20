/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arturo254.opentune.R
import com.arturo254.opentune.together.TogetherContact
import com.arturo254.opentune.together.TogetherDiscoveredSession
import com.arturo254.opentune.together.TogetherGuestCard
import com.arturo254.opentune.together.TogetherGuestCardLink
import com.arturo254.opentune.together.TogetherInvite
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders [content] as a black-on-white QR bitmap off the main thread. */
@Composable
internal fun rememberQrBitmap(content: String, sizePx: Int = 720): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, content, sizePx) {
        value =
            withContext(Dispatchers.Default) {
                runCatching {
                    val matrix =
                        QRCodeWriter().encode(
                            content,
                            BarcodeFormat.QR_CODE,
                            sizePx,
                            sizePx,
                            mapOf(
                                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                                EncodeHintType.MARGIN to 1,
                            ),
                        )
                    val pixels = IntArray(matrix.width * matrix.height)
                    for (y in 0 until matrix.height) {
                        val row = y * matrix.width
                        for (x in 0 until matrix.width) {
                            pixels[row + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                        }
                    }
                    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
                        .asImageBitmap()
                }.getOrNull()
            }
    }
    return bitmap
}

@Composable
internal fun GuestQrDialog(
    card: TogetherGuestCard?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(painterResource(R.drawable.qr_code), contentDescription = null) },
        title = { Text(stringResource(R.string.together_my_qr_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // QR always on white so it scans in dark theme too.
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val qr = card?.let { rememberQrBitmap(TogetherGuestCardLink.encode(it)) }
                    if (qr != null) {
                        Image(bitmap = qr, contentDescription = stringResource(R.string.together_my_qr_title))
                    } else {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp)
                    }
                }
                if (card != null) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    stringResource(R.string.together_my_qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.dismiss))
            }
        },
    )
}

@Composable
internal fun IncomingInviteDialog(
    invite: TogetherInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(painterResource(R.drawable.multi_user), contentDescription = null) },
        title = { Text(stringResource(R.string.together_invite_title)) },
        text = {
            Text(
                stringResource(R.string.together_invite_message, invite.hostName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(onClick = onAccept, shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.join), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline, shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.together_invite_decline))
            }
        },
    )
}

/** Big tonal action used for both "Show my QR" (guest) and "Add guest with QR" (host). */
@Composable
internal fun QrActionButton(
    iconRes: Int,
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun TogetherContactsCard(
    contacts: List<TogetherContact>,
    liveSessions: List<TogetherDiscoveredSession>,
    joinEnabled: Boolean,
    onJoin: (TogetherDiscoveredSession) -> Unit,
    onRemove: (TogetherContact) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRemoveId by rememberSaveable { mutableStateOf<String?>(null) }
    val confirmRemove = contacts.firstOrNull { it.clientId == confirmRemoveId }

    if (confirmRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoveId = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.together_contact_remove)) },
            text = {
                Text(
                    stringResource(R.string.together_contact_remove_confirm, confirmRemove.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveId = null
                        onRemove(confirmRemove)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveId = null }, shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.together_contacts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.together_contacts_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Live sessions first, then the rest alphabetically.
            val liveById = liveSessions.associateBy { it.hostClientId }
            val sorted = contacts.sortedWith(compareBy({ it.clientId !in liveById }, { it.name.lowercase() }))

            sorted.forEach { contact ->
                val live = liveById[contact.clientId]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (live != null) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(if (live != null) R.drawable.wifi else R.drawable.person),
                                contentDescription = null,
                                tint = if (live != null) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            contact.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                if (live != null) R.string.together_contact_live else R.string.together_contact_offline,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (live != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (live != null) {
                        Button(
                            onClick = { onJoin(live) },
                            enabled = joinEnabled,
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(stringResource(R.string.join), fontWeight = FontWeight.SemiBold) }
                    }
                    IconButton(onClick = { confirmRemoveId = contact.clientId }) {
                        Icon(
                            painterResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.together_contact_remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
