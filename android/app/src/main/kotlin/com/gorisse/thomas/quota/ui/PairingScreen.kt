package com.gorisse.thomas.quota.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.gorisse.thomas.quota.data.DEFAULT_HOST
import com.gorisse.thomas.quota.data.ProviderId
import com.gorisse.thomas.quota.data.Pairing
import com.gorisse.thomas.quota.data.parsePairingUrl
import com.gorisse.thomas.quota.ui.theme.Dimens

/**
 * Pairing: paste the URL the collector printed, or scan its QR code.
 *
 * The scan uses Play Services' code scanner rather than CameraX + ML Kit, because it runs the
 * camera in the Play Services process: no `CAMERA` permission in the manifest, nothing to ask
 * the user, and no preview surface to get wrong.
 *
 * "Try with sample data" ships in every build type, release included: without it the app is
 * a dead end for anyone who has not run the collector — a store reviewer first of all. It
 * loads [DemoData] locally and never touches the backend.
 */
@Composable
fun PairingScreen(
    onPaired: (Pairing) -> Unit,
    onDemoData: () -> Unit,
    modifier: Modifier = Modifier,
    pairingError: String? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var url by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun submit(candidate: String) {
        val pairing = parsePairingUrl(candidate)
        if (pairing == null) {
            errorText = "Paste the complete pairing link from your Mac, including #t= and the code after it."
        } else {
            errorText = null
            onPaired(pairing)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.Grid * 3),
        verticalArrangement = Arrangement.spacedBy(Dimens.Grid * 2),
    ) {
        QuotaWordmark()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderMark(ProviderId.CLAUDE)
            ProviderMark(ProviderId.CODEX)
            ProviderMark(ProviderId.GEMINI)
        }
        Text(
            text = "A little clarity.\nMore room to create.",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Connect Claude, Codex and Gemini to see what’s left, when it resets, across your AI tools.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        pairingError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Card(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(Dimens.CardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Dimens.Grid * 2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Grid * 2),
            ) {
                Text("Connect your Mac", style = MaterialTheme.typography.titleLarge)
                Text("1. Open the Quota collector on your Mac.\n2. Copy its pairing link or scan its QR code.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { uriHandler.openUri("https://quota.sceneview.dev/setup.html") }) {
                    Text("Set up Quota on your Mac ↗")
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorText = null
                    },
                    label = { Text("Pairing URL") },
                    placeholder = { Text("$DEFAULT_HOST/#t=…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) submit(url) }),
                    isError = errorText != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                errorText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Grid * 1.5f),
                ) {
                    OutlinedButton(
                        onClick = {
                            val options = GmsBarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .enableAutoZoom()
                                .build()
                            GmsBarcodeScanning.getClient(context, options).startScan()
                                .addOnSuccessListener { barcode ->
                                    val value = barcode.rawValue.orEmpty()
                                    url = value
                                    submit(value)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(
                                        context,
                                        "Could not start the scanner — paste the link instead",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Text("Scan QR", modifier = Modifier.padding(start = Dimens.Grid))
                    }
                    Button(
                        onClick = { submit(url) },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Connect")
                    }
                }
            }
        }

        Text("Your AI account credentials stay on your Mac. Only usage and reset times are synced.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(
                onClick = onDemoData,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Try with sample data")
            }
            Text(
                text = "Explore the app and widgets before connecting. No account needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
