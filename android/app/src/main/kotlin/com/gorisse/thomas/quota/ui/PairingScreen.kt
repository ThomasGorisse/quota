package com.gorisse.thomas.quota.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.gorisse.thomas.quota.BuildConfig
import com.gorisse.thomas.quota.data.DEFAULT_HOST
import com.gorisse.thomas.quota.data.Pairing
import com.gorisse.thomas.quota.data.parsePairingUrl
import com.gorisse.thomas.quota.ui.theme.Dimens
import com.gorisse.thomas.quota.ui.theme.QuotaColors

/**
 * Pairing: paste the URL the collector printed, or scan its QR code.
 *
 * The scan uses Play Services' code scanner rather than CameraX + ML Kit, because it runs the
 * camera in the Play Services process: no `CAMERA` permission in the manifest, nothing to ask
 * the user, and no preview surface to get wrong.
 */
@Composable
fun PairingScreen(
    onPaired: (Pairing) -> Unit,
    onDemoData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var demoEnabled by remember { mutableStateOf(false) }

    fun submit(candidate: String) {
        val pairing = parsePairingUrl(candidate)
        if (pairing == null) {
            errorText = "That is not a pairing URL. It looks like $DEFAULT_HOST/#t=…"
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
        Text(
            text = "Pair this phone",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Run the collector on your Mac. It prints a pairing link — scan its QR code " +
                "or paste it here. Your Claude and ChatGPT credentials stay on the Mac.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            shape = RoundedCornerShape(Dimens.CardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Dimens.Grid * 2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Grid * 2),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorText = null
                    },
                    label = { Text("Pairing URL") },
                    placeholder = { Text("$DEFAULT_HOST/#t=…") },
                    singleLine = true,
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
                        color = QuotaColors.Critical,
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
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Text("Scan QR", modifier = Modifier.padding(start = Dimens.Grid))
                    }
                    Button(
                        onClick = { submit(url) },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Pair")
                    }
                }
            }
        }

        if (BuildConfig.DEMO_DATA_AVAILABLE) {
            Card(
                shape = RoundedCornerShape(Dimens.CardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Dimens.Grid * 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Demo data",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Debug builds only. Fills the app and the widget with a " +
                                "canned status so the UI can be checked without a backend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = demoEnabled,
                        onCheckedChange = { checked ->
                            demoEnabled = checked
                            if (checked) onDemoData()
                        },
                    )
                }
            }
        }
    }
}
