package com.example.ui.cashier

import android.widget.Toast
import androidx.compose.material.icons.filled.Print
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.PrinterSettingsDialog
import com.example.util.printer.ThermalPrinterManager
import com.example.util.printer.toReceiptData
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.TableOrder
import com.example.data.model.User
import com.example.ui.components.formatRupiah
import com.example.ui.kitchen.AddItemsDialog
import com.example.ui.kitchen.CancelOrderDialog
import com.example.ui.kitchen.KitchenOrderCardClean
import com.example.ui.kitchen.KitchenViewModel
import com.example.ui.pos.ProofUploadState
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenAccentDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.MinimalBackground
import com.example.ui.theme.MinimalBorder
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary

/**
 * Layar "Bayar Meja" — daftar tagihan yang sedang berjalan di antrian dapur
 * (belum dibayar), yang sudah selesai dibayar, dan antrean dapur, sama seperti
 * di website. Kasir bisa langsung melihat status dapur tiap tagihan (masih
 * dimasak / sudah selesai) sebelum memproses pembayaran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(
    user: User,
    cashierViewModel: CashierViewModel,
    kitchenViewModel: KitchenViewModel = viewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    cashierViewModel.setUser(user)
    kitchenViewModel.setUser(user)

    val bills by cashierViewModel.bills.collectAsState()
    val paidOrders by cashierViewModel.paidOrders.collectAsState()
    val isLoading by cashierViewModel.isLoading.collectAsState()
    val paymentState by cashierViewModel.paymentState.collectAsState()
    val business by cashierViewModel.business.collectAsState()
    val receiptSettings by cashierViewModel.receiptSettings.collectAsState()
    val businessName = business?.name?.ifBlank { null } ?: "Toko Saya"

    val kitchenOrders by kitchenViewModel.orders.collectAsState()
    val kitchenProducts by kitchenViewModel.products.collectAsState()
    val kitchenCategories by kitchenViewModel.categories.collectAsState()
    val isKitchenLoading by kitchenViewModel.isLoading.collectAsState()
    val actionError by kitchenViewModel.actionError.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printerManager = remember { ThermalPrinterManager(context) }

    var activeTab by remember { mutableStateOf("unpaid") }
    var selectedBill by remember { mutableStateOf<TableBill?>(null) }
    // FR-DINEIN-ADDITEM: kasir menambah item ke tagihan yang sudah berjalan
    // (mis. pelanggan minta tambah self service / ada pesanan susulan yang
    // belum tercatat) walau pesanannya sudah/sedang diproses dapur.
    var showAddItemsFor by remember { mutableStateOf<TableBill?>(null) }
    var cancelDialogOrder by remember { mutableStateOf<TableOrder?>(null) }
    var addItemDialogOrder by remember { mutableStateOf<TableOrder?>(null) }
    var showPrinterSettings by remember { mutableStateOf(false) }
    var printingOrderId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(paymentState) {
        if (paymentState is PaymentState.Success) {
            selectedBill = null
            cashierViewModel.resetPaymentState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Bayar Pesanan Meja", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GreenPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showPrinterSettings = true }) {
                        Icon(Icons.Default.Print, contentDescription = "Pengaturan Printer Thermal", tint = GreenPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MinimalBackground),
                modifier = Modifier.border(0.5.dp, MinimalBorder, RoundedCornerShape(0.dp))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MinimalBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CashierTabChip(
                    label = "Belum Dibayar (${bills.size})",
                    isSelected = activeTab == "unpaid",
                    onClick = { activeTab = "unpaid" }
                )
                CashierTabChip(
                    label = "Sudah Dibayar",
                    isSelected = activeTab == "paid",
                    onClick = { activeTab = "paid" }
                )
                CashierTabChip(
                    label = "Antrean Dapur (${kitchenOrders.size})",
                    isSelected = activeTab == "kitchen",
                    onClick = { activeTab = "kitchen" }
                )
            }

            when (activeTab) {
                "unpaid" -> {
                    if (isLoading && bills.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GreenPrimary)
                        }
                    } else if (bills.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = MinimalBorder, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Belum ada tagihan berjalan", color = MinimalTextPrimary, fontWeight = FontWeight.Bold)
                                Text("Pesanan meja/bungkus yang dikirim ke dapur akan muncul di sini.", color = MinimalTextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(bills, key = { it.billKey }) { bill ->
                                BillCard(
                                    bill = bill,
                                    onClick = { selectedBill = bill },
                                    onAddItems = { showAddItemsFor = bill }
                                )
                            }
                        }
                    }
                }
                "paid" -> {
                    if (paidOrders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Belum ada riwayat pembayaran.", color = MinimalTextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(paidOrders, key = { it.id }) { order ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MinimalBorder, RoundedCornerShape(14.dp))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(order.tableName.ifBlank { order.customerName }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(order.totalAmount.formatRupiah(), fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 13.sp)
                                        }
                                        Text(
                                            "Dibayar via ${order.paymentMethod?.uppercase() ?: "CASH"}",
                                            fontSize = 11.sp,
                                            color = MinimalTextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val saved = printerManager.getSavedPrinter()
                                                    if (saved == null) {
                                                        Toast.makeText(context, "Silakan atur printer Bluetooth terlebih dahulu.", Toast.LENGTH_SHORT).show()
                                                        showPrinterSettings = true
                                                    } else {
                                                        printingOrderId = order.id
                                                        scope.launch {
                                                            val receiptData = order.toReceiptData(
                                                                businessName = businessName,
                                                                cashierName = user.name,
                                                                storeAddress = receiptSettings?.storeAddress,
                                                                headerText = receiptSettings?.headerText,
                                                                footerText = receiptSettings?.footerText
                                                            )
                                                            val res = printerManager.printReceipt(saved, receiptData)
                                                            printingOrderId = null
                                                            res.fold(
                                                                onSuccess = { Toast.makeText(context, "Struk berhasil dicetak!", Toast.LENGTH_SHORT).show() },
                                                                onFailure = { err -> Toast.makeText(context, err.message ?: "Gagal cetak", Toast.LENGTH_LONG).show() }
                                                            )
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                if (printingOrderId == order.id) {
                                                    CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Mencetak...", fontSize = 11.sp, color = GreenPrimary)
                                                } else {
                                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp), tint = GreenPrimary)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Cetak Struk", fontSize = 11.sp, color = GreenPrimary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "kitchen" -> {
                    if (isKitchenLoading && kitchenOrders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GreenPrimary)
                        }
                    } else if (kitchenOrders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, tint = MinimalBorder, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Belum ada antrian dapur", color = MinimalTextPrimary, fontWeight = FontWeight.Bold)
                                Text("Pesanan yang dikirim ke dapur akan muncul di sini secara real-time.", color = MinimalTextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(kitchenOrders, key = { it.id }) { tableOrder ->
                                KitchenOrderCardClean(
                                    order = tableOrder,
                                    onToggleItem = { itemIndex ->
                                        kitchenViewModel.toggleItemStatus(tableOrder, itemIndex)
                                    },
                                    onCancelOrder = { cancelDialogOrder = tableOrder },
                                    onAddItems = { addItemDialogOrder = tableOrder }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    cancelDialogOrder?.let { order ->
        CancelOrderDialog(
            order = order,
            onConfirm = { reason ->
                kitchenViewModel.cancelOrder(order, reason)
                cancelDialogOrder = null
            },
            onDismiss = { cancelDialogOrder = null }
        )
    }

    addItemDialogOrder?.let { order ->
        AddItemsDialog(
            order = order,
            products = kitchenProducts,
            categories = kitchenCategories,
            errorMessage = actionError,
            onClearError = { kitchenViewModel.clearActionError() },
            onConfirm = { newItems ->
                kitchenViewModel.addItemsToOrder(order, newItems)
                addItemDialogOrder = null
            },
            onDismiss = {
                kitchenViewModel.clearActionError()
                addItemDialogOrder = null
            }
        )
    }

    // Tombol "Tambah Item" dari tab "Belum Dibayar" (BillCard) — dipakai
    // kasir kalau pelanggan lapor ada self service / pesanan susulan yang
    // belum tercatat, TANPA perlu pindah dulu ke tab "Antrean Dapur". Pakai
    // dialog & ViewModel yang SAMA dengan tab Dapur (kitchenViewModel) supaya
    // 1 sumber logika (stok, recompute status) — bukan implementasi terpisah.
    showAddItemsFor?.let { bill ->
        val targetOrder = bill.orders.firstOrNull()
        if (targetOrder != null) {
            AddItemsDialog(
                order = targetOrder,
                products = kitchenProducts,
                categories = kitchenCategories,
                errorMessage = actionError,
                onClearError = { kitchenViewModel.clearActionError() },
                onConfirm = { newItems ->
                    kitchenViewModel.addItemsToOrder(targetOrder, newItems)
                    showAddItemsFor = null
                },
                onDismiss = {
                    kitchenViewModel.clearActionError()
                    showAddItemsFor = null
                }
            )
        }
    }

    selectedBill?.let { bill ->
        PayBillDialog(
            bill = bill,
            cashierViewModel = cashierViewModel,
            paymentState = paymentState,
            onDismiss = {
                cashierViewModel.clearPaymentProof()
                cashierViewModel.resetPaymentState()
                selectedBill = null
            }
        )
    }

    if (showPrinterSettings) {
        PrinterSettingsDialog(onDismiss = { showPrinterSettings = false })
    }
}

@Composable
private fun CashierTabChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) GreenPrimary else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) GreenPrimary else MinimalBorder)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else MinimalTextSecondary
        )
    }
}

@Composable
private fun BillCard(bill: TableBill, onClick: () -> Unit, onAddItems: () -> Unit) {
    val statusLabel = if (bill.allCompleted) "Siap Dibayar — Selesai Dimasak" else "Masih Diproses Dapur"
    val statusColor = if (bill.allCompleted) GreenPrimary else Color(0xFFEA580C)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(bill.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MinimalTextPrimary)
                Text(bill.total.formatRupiah(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = bill.customerNames.joinToString(", "),
                fontSize = 12.sp,
                color = MinimalTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onClick() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }

                // FR-DINEIN-ADDITEM: tetap boleh tambah item walau pesanan
                // sudah/sedang diproses dapur (mis. self service susulan
                // yang pelanggan laporkan ke kasir) — selama belum lunas.
                TextButton(onClick = onAddItems, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Tambah Item", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                }
            }
        }
    }
}

@Composable
private fun PayBillDialog(
    bill: TableBill,
    cashierViewModel: CashierViewModel,
    paymentState: PaymentState,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("cash") }
    var cashInput by remember { mutableStateOf(bill.total.toInt().toString()) }
    val proofUploadState by cashierViewModel.proofUploadState.collectAsState()

    val cashReceived = cashInput.toDoubleOrNull() ?: 0.0
    val change = cashReceived - bill.total
    val proofUrl = (proofUploadState as? ProofUploadState.Success)?.url
    val needsProof = selectedMethod != "cash"

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) cashierViewModel.uploadPaymentProof(uri) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bayar: ${bill.displayName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Rincian item dari semua nota yang tergabung dalam tagihan ini.
                    bill.orders.forEach { order ->
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.qty}x ${item.name}", fontSize = 12.sp, color = MinimalTextSecondary)
                                Text((item.price * item.qty).formatRupiah(), fontSize = 12.sp, color = MinimalTextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF0F4E9))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Tagihan", fontSize = 12.sp, color = MinimalTextSecondary)
                            Text(bill.total.formatRupiah(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Metode Pembayaran", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("cash" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer").forEach { (key, label) ->
                            val isSelected = selectedMethod == key
                            Card(
                                modifier = Modifier.weight(1f).clickable {
                                    selectedMethod = key
                                    if (key == "cash") cashierViewModel.clearPaymentProof()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) GreenPrimary else Color(0xFFF1F5F9))
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else MinimalTextSecondary)
                                }
                            }
                        }
                    }

                    if (selectedMethod == "cash") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kembalian:", fontSize = 13.sp)
                            Text(
                                if (change >= 0) change.formatRupiah() else "Kurang ${(-change).formatRupiah()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (change >= 0) GreenPrimary else Color(0xFFDC2626)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        when (val proofState = proofUploadState) {
                            is ProofUploadState.Success -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFEFFBEE))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    coil.compose.AsyncImage(
                                        model = proofState.url,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Bukti bayar tersimpan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E8E3E))
                                }
                            }
                            is ProofUploadState.Uploading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GreenPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mengunggah...", fontSize = 12.sp, color = MinimalTextSecondary)
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = GreenPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Unggah Bukti Bayar", fontSize = 12.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (paymentState is PaymentState.Error) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(paymentState.message, fontSize = 12.sp, color = Color(0xFFDC2626))
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Button(
                        onClick = {
                            cashierViewModel.payBill(bill, selectedMethod, proofUrl)
                        },
                        enabled = paymentState !is PaymentState.Processing &&
                            (selectedMethod != "cash" || cashReceived >= bill.total) &&
                            (!needsProof || proofUrl != null),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = GreenAccentDark)
                    ) {
                        if (paymentState is PaymentState.Processing) {
                            CircularProgressIndicator(color = GreenAccentDark, modifier = Modifier.size(20.dp))
                        } else {
                            Text("KONFIRMASI LUNAS", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
