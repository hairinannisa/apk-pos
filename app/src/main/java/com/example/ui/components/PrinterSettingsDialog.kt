package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenAccentDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.MinimalBackground
import com.example.ui.theme.MinimalBorder
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary
import com.example.util.printer.PrinterDevice
import com.example.util.printer.ThermalPrinterManager
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun PrinterSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val printerManager = remember { ThermalPrinterManager(context) }

    var savedPrinter by remember { mutableStateOf(printerManager.getSavedPrinter()) }
    var paperWidth by remember { mutableIntStateOf(printerManager.getPaperWidth()) }

    var pairedDevices by remember { mutableStateOf(emptyList<PrinterDevice>()) }
    val isScanning by printerManager.isScanning.collectAsState()
    val discoveredDevices by printerManager.discoveredDevices.collectAsState()

    var isTestingPrint by remember { mutableStateOf(false) }

    // Required Bluetooth permissions launcher for Android 12+ / Android 11-
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun hasPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            pairedDevices = printerManager.getPairedDevices()
            printerManager.startDiscovery()
        } else {
            Toast.makeText(context, "Izin Bluetooth diperlukan untuk mencari printer.", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshDevices() {
        if (hasPermissions()) {
            pairedDevices = printerManager.getPairedDevices()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    fun startScanning() {
        if (hasPermissions()) {
            pairedDevices = printerManager.getPairedDevices()
            val started = printerManager.startDiscovery()
            if (!started && !printerManager.isBluetoothEnabled) {
                Toast.makeText(context, "Bluetooth tidak aktif. Aktifkan Bluetooth terlebih dahulu.", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    DisposableEffect(Unit) {
        onDispose {
            printerManager.stopDiscovery()
        }
    }

    Dialog(
        onDismissRequest = {
            printerManager.stopDiscovery()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.dp, MinimalBorder, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GreenPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pengaturan Printer Thermal",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextPrimary
                            )
                            Text(
                                text = "Cetak struk via Bluetooth POS",
                                fontSize = 11.sp,
                                color = MinimalTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = {
                        printerManager.stopDiscovery()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = MinimalTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Check Bluetooth Status
                if (!printerManager.isBluetoothSupported) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Perangkat ini tidak mendukung Bluetooth.", fontSize = 12.sp, color = Color(0xFFDC2626))
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                } else if (!printerManager.isBluetoothEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFFD97706))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Bluetooth Tidak Aktif", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                    Text("Aktifkan Bluetooth untuk mendeteksi printer.", fontSize = 10.sp, color = Color(0xFFB45309))
                                }
                            }
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                            ) {
                                Text("Aktifkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                LazyColumn(
                    modifier = Modifier.height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Active Selected Printer Card
                    item {
                        Text("PRINTER AKTIF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinimalTextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))

                        if (savedPrinter != null) {
                            val activeDev = savedPrinter!!
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF0284C7),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = activeDev.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MinimalTextPrimary
                                                )
                                                Text(
                                                    text = "MAC: ${activeDev.address}",
                                                    fontSize = 11.sp,
                                                    color = MinimalTextSecondary
                                                )
                                            }
                                        }

                                        IconButton(onClick = {
                                            printerManager.clearSavedPrinter()
                                            savedPrinter = null
                                            Toast.makeText(context, "Printer dihapus.", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus Printer", tint = Color(0xFFDC2626))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            if (isTestingPrint) return@Button
                                            isTestingPrint = true
                                            scope.launch {
                                                val res = printerManager.printTestReceipt(activeDev)
                                                isTestingPrint = false
                                                res.fold(
                                                    onSuccess = {
                                                        Toast.makeText(context, "Struk uji coba berhasil dicetak!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onFailure = { err ->
                                                        Toast.makeText(context, err.message ?: "Gagal cetak test", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        },
                                        enabled = !isTestingPrint,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GreenAccent,
                                            contentColor = GreenAccentDark
                                        )
                                    ) {
                                        if (isTestingPrint) {
                                            CircularProgressIndicator(color = GreenAccentDark, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Mencetak Test Struk...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Tes Cetak Struk (Test Print)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MinimalBackground),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MinimalBorder, RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MinimalTextSecondary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Belum ada printer Bluetooth terpilih. Silakan pilih dari daftar perangkat di bawah.",
                                        fontSize = 11.sp,
                                        color = MinimalTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Paper Width Setting
                    item {
                        Text("UKURAN KERTAS PRINTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinimalTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(
                                58 to "58 mm (Standard Mobile POS)",
                                80 to "80 mm (Printer Struk Besar)"
                            ).forEach { (w, label) ->
                                val isSel = paperWidth == w
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            paperWidth = w
                                            printerManager.savePaperWidth(w)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSel) GreenPrimary.copy(alpha = 0.12f) else Color.White
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSel) GreenPrimary else MinimalBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSel,
                                            onClick = {
                                                paperWidth = w
                                                printerManager.savePaperWidth(w)
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = GreenPrimary)
                                        )
                                        Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }

                    // Paired Bluetooth Devices Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PRINTER TERPASANG (PAIRED)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinimalTextSecondary)
                            IconButton(
                                onClick = { refreshDevices() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Paired", tint = GreenPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (pairedDevices.isEmpty()) {
                        item {
                            Text(
                                text = "Tidak ada perangkat Bluetooth terpasang di HP ini.",
                                fontSize = 11.sp,
                                color = MinimalTextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        items(pairedDevices) { device ->
                            val isCurrent = savedPrinter?.address == device.address
                            PrinterDeviceRow(
                                device = device,
                                isSelected = isCurrent,
                                onSelect = {
                                    printerManager.savePrinter(device)
                                    savedPrinter = device
                                    Toast.makeText(context, "Printer '${device.name}' terpilih.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // Scan Nearby Bluetooth Printers Section
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PINDAI PRINTER SEKITAR (DISCOVERY)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinimalTextSecondary)

                            if (isScanning) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Memindai...", fontSize = 11.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { startScanning() },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(14.dp), tint = GreenPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cari Printer", fontSize = 11.sp, color = GreenPrimary)
                                }
                            }
                        }
                    }

                    if (discoveredDevices.isEmpty() && !isScanning) {
                        item {
                            Text(
                                text = "Klik 'Cari Printer' untuk memindai printer Bluetooth terdekat yang belum terpasang.",
                                fontSize = 11.sp,
                                color = MinimalTextSecondary
                            )
                        }
                    } else {
                        items(discoveredDevices) { device ->
                            val isCurrent = savedPrinter?.address == device.address
                            PrinterDeviceRow(
                                device = device,
                                isSelected = isCurrent,
                                onSelect = {
                                    printerManager.savePrinter(device)
                                    savedPrinter = device
                                    Toast.makeText(context, "Printer '${device.name}' terpilih.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // Helpful Tips
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MinimalBackground),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mendukung semua printer thermal Bluetooth ESC/POS (58mm/80mm) seperti Blueprint, Panda, Enibit, Kassen, Bellav, GOOJPRT, Mini POS, dll.",
                                    fontSize = 10.sp,
                                    color = MinimalTextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        printerManager.stopDiscovery()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) {
                    Text("SIMPAN & SELESAI", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrinterDeviceRow(
    device: PrinterDevice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF0F4E9) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) GreenPrimary else MinimalBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = if (isSelected) GreenPrimary else MinimalTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = device.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextPrimary
                    )
                    Text(
                        text = device.address,
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GreenPrimary)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Dipilih",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Pilih", fontSize = 11.sp, color = GreenPrimary)
                }
            }
        }
    }
}
