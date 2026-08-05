package com.example.ui.pos

import android.content.res.Configuration
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.cashier.CashierScreen
import com.example.ui.cashier.CashierViewModel
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.EmbeddedBarcodeScannerCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.components.PrinterSettingsDialog
import com.example.util.printer.ThermalPrinterManager
import com.example.util.printer.toReceiptData
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.DiningTable
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.User
import com.example.ui.components.BranchBadge
import com.example.ui.components.RoleBadge
import com.example.ui.components.StockBadge
import com.example.ui.components.formatRupiah
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenAccentDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.MinimalBackground
import com.example.ui.theme.MinimalBorder
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    user: User,
    posViewModel: PosViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    posViewModel.setUser(user)

    val products by posViewModel.filteredProducts.collectAsState()
    val categories by posViewModel.categories.collectAsState()
    val searchQuery by posViewModel.searchQuery.collectAsState()
    val selectedCategory by posViewModel.selectedCategoryId.collectAsState()
    val cartItems by posViewModel.cartItems.collectAsState()
    val totalAmount by posViewModel.totalAmount.collectAsState()
    val variantProduct by posViewModel.variantSelectionProduct.collectAsState()
    val completedOrder by posViewModel.lastCompletedOrder.collectAsState()
    val checkoutState by posViewModel.checkoutState.collectAsState()
    val saveQueueState by posViewModel.saveQueueState.collectAsState()
    val pastOrders by posViewModel.pastOrders.collectAsState()
    val business by posViewModel.business.collectAsState()
    val isFnbBusiness = business?.transactionMode?.lowercase() == "fnb"

    // Jumlah tiap produk yang sudah masuk keranjang (dijumlah semua varian) —
    // dipakai utk badge angka kecil di pojok kartu produk.
    val cartQtyByProduct = remember(cartItems) {
        cartItems.groupBy { it.product.id }.mapValues { entry -> entry.value.sumOf { it.qty } }
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Mode landscape (miring) di HP MAUPUN tablet — dideteksi dari orientasi
    // layar saat ini, bukan dari ukuran layar, supaya konsisten di kedua
    // jenis perangkat sesuai permintaan.
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showCartSheet by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showCashierQueue by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showPrinterSettings by remember { mutableStateOf(false) }

    // Layar "Bayar Meja" (daftar tagihan belum/sudah dibayar dari antrian
    // dapur) ditampilkan sebagai pengganti layar Kasir biasa, bukan dialog
    // — supaya kasir bisa leluasa menggulir daftar & membuka detail bayar.
    if (showCashierQueue) {
        val cashierViewModel: CashierViewModel = viewModel()
        CashierScreen(
            user = user,
            cashierViewModel = cashierViewModel,
            onBack = { showCashierQueue = false }
        )
        return
    }

    LaunchedEffect(saveQueueState) {
        when (val state = saveQueueState) {
            is SaveQueueState.Success -> {
                Toast.makeText(context, "Pesanan disimpan ke antrian dapur!", Toast.LENGTH_SHORT).show()
                showSaveQueueDialog = false
                posViewModel.resetSaveQueueState()
            }
            is SaveQueueState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }

    val cartSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Usahaki.id",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isLandscape) 15.sp else 18.sp,
                            color = GreenPrimary
                        )
                        RoleBadge(role = user.role)
                        BranchBadge(assignedBranchId = user.assignedBranchId)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPrinterSettings = true },
                        modifier = Modifier.size(if (isLandscape) 36.dp else 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Pengaturan Printer Thermal",
                            tint = MinimalTextSecondary,
                            modifier = Modifier.size(if (isLandscape) 18.dp else 22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showCashierQueue = true },
                        modifier = Modifier.size(if (isLandscape) 36.dp else 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Bayar Pesanan Meja",
                            tint = MinimalTextSecondary,
                            modifier = Modifier.size(if (isLandscape) 18.dp else 22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier.size(if (isLandscape) 36.dp else 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Riwayat Transaksi",
                            tint = MinimalTextSecondary,
                            modifier = Modifier.size(if (isLandscape) 18.dp else 22.dp)
                        )
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.size(if (isLandscape) 36.dp else 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Keluar",
                            tint = MinimalTextSecondary,
                            modifier = Modifier.size(if (isLandscape) 18.dp else 22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MinimalBackground
                ),
                modifier = Modifier.border(0.5.dp, MinimalBorder, RoundedCornerShape(0.dp))
            )
        },
        bottomBar = {
            if (!isLandscape && cartItems.isNotEmpty()) {
                Surface(
                    color = MinimalBackground,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(GreenPrimary)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingBasket,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${cartItems.sumOf { it.qty }} Items",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = totalAmount.formatRupiah(),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Button(
                                onClick = { showCartSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GreenAccent,
                                    contentColor = GreenAccentDark
                                ),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(
                                    text = "Bayar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isLandscape) {
            // Mode miring (landscape) — baik di HP maupun tablet: produk di
            // kiri, keranjang belanja SELALU terlihat sebagai panel tetap di
            // kanan (bukan bottom sheet yang harus dibuka manual seperti di
            // mode potret).
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MinimalBackground)
            ) {
            val handleBarcodeScanned: (String) -> Unit = { code ->
                val match = posViewModel.lookupByBarcode(code)
                if (match != null) {
                    val (product, variant) = match
                    posViewModel.addToCart(product, variant)
                    val label = variant?.let { "${product.name} (${it.name})" } ?: product.name
                    Toast.makeText(context, "$label ditambahkan ke keranjang", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Barcode '$code' tidak ditemukan di katalog.", Toast.LENGTH_SHORT).show()
                }
            }

            ProductBrowseArea(
                modifier = Modifier.weight(1.4f),
                searchQuery = searchQuery,
                categories = categories,
                selectedCategory = selectedCategory,
                products = products,
                cartQtyByProduct = cartQtyByProduct,
                assignedBranchId = user.assignedBranchId,
                showBarcodeScanner = showBarcodeScanner,
                onSearchChange = { posViewModel.setSearchQuery(it) },
                onSelectCategory = { posViewModel.selectCategory(it) },
                onScanClick = { showBarcodeScanner = !showBarcodeScanner },
                onDismissScanner = { showBarcodeScanner = false },
                onBarcodeScanned = handleBarcodeScanned,
                onAddToCart = { product -> posViewModel.addToCart(product) }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White)
                    .border(1.dp, MinimalBorder, RoundedCornerShape(0.dp))
            ) {
                CartPanelContent(
                    cartItems = cartItems,
                    totalAmount = totalAmount,
                    isFnbBusiness = isFnbBusiness,
                    onIncrement = { item -> posViewModel.updateCartQty(item, 1) },
                    onDecrement = { item -> posViewModel.updateCartQty(item, -1) },
                    onClearCart = { posViewModel.clearCart() },
                    onSave = { showSaveQueueDialog = true },
                    onCheckout = { showCheckoutDialog = true },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    } else {
        val handleBarcodeScanned: (String) -> Unit = { code ->
            val match = posViewModel.lookupByBarcode(code)
            if (match != null) {
                val (product, variant) = match
                posViewModel.addToCart(product, variant)
                val label = variant?.let { "${product.name} (${it.name})" } ?: product.name
                Toast.makeText(context, "$label ditambahkan ke keranjang", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Barcode '$code' tidak ditemukan di katalog.", Toast.LENGTH_SHORT).show()
            }
        }

        ProductBrowseArea(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MinimalBackground),
            searchQuery = searchQuery,
            categories = categories,
            selectedCategory = selectedCategory,
            products = products,
            cartQtyByProduct = cartQtyByProduct,
            assignedBranchId = user.assignedBranchId,
            showBarcodeScanner = showBarcodeScanner,
            onSearchChange = { posViewModel.setSearchQuery(it) },
            onSelectCategory = { posViewModel.selectCategory(it) },
            onScanClick = { showBarcodeScanner = !showBarcodeScanner },
            onDismissScanner = { showBarcodeScanner = false },
            onBarcodeScanned = handleBarcodeScanned,
            onAddToCart = { product -> posViewModel.addToCart(product) }
        )
    }
}

    // Dialog "Simpan ke Antrian Dapur" — sama seperti SaveToKitchenModal di
    // website: pilih jenis pesanan (Meja/Tanpa Meja/Bungkus) lalu simpan
    // TANPA memproses pembayaran. Hanya relevan utk bisnis F&B.
    if (showSaveQueueDialog) {
        val tables by posViewModel.tables.collectAsState()
        SaveToKitchenDialog(
            tables = tables,
            isSaving = saveQueueState is SaveQueueState.Saving,
            onDismiss = { showSaveQueueDialog = false },
            onConfirm = { orderType, selectedTable, customerName ->
                posViewModel.saveToKitchen(orderType, selectedTable, customerName)
            }
        )
    }

    // Modal Bottom Sheet: Cart Details
    if (showCartSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            sheetState = cartSheetState,
            containerColor = MinimalBackground
        ) {
            CartPanelContent(
                cartItems = cartItems,
                totalAmount = totalAmount,
                isFnbBusiness = isFnbBusiness,
                onIncrement = { item -> posViewModel.updateCartQty(item, 1) },
                onDecrement = { item -> posViewModel.updateCartQty(item, -1) },
                onClearCart = { posViewModel.clearCart() },
                onSave = {
                    showCartSheet = false
                    showSaveQueueDialog = true
                },
                onCheckout = {
                    showCartSheet = false
                    showCheckoutDialog = true
                },
                modifier = Modifier.padding(20.dp)
            )
        }
    }

    // Modal Dialog: Variant Picker
    if (variantProduct != null) {
        val prod = variantProduct!!
        Dialog(onDismissRequest = { posViewModel.closeVariantPicker() }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .border(1.dp, MinimalBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Pilih Varian: ${prod.name}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(220.dp)
                    ) {
                        items(prod.variants) { variant ->
                            val vStock = variant.getEffectiveStock(user.assignedBranchId)
                            val isAvailable = vStock > 0
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isAvailable) {
                                        posViewModel.addToCart(prod, variant)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAvailable) Color(0xFFF0F4E9) else MinimalBackground
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = variant.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Stok: $vStock",
                                            fontSize = 12.sp,
                                            color = if (isAvailable) GreenPrimary else Color(0xFFDC2626)
                                        )
                                    }

                                    Text(
                                        text = variant.price.formatRupiah(),
                                        fontWeight = FontWeight.Bold,
                                        color = GreenPrimary,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { posViewModel.closeVariantPicker() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Batal")
                    }
                }
            }
        }
    }

    // Modal Dialog: Checkout Payment
    if (showCheckoutDialog) {
        val business by posViewModel.business.collectAsState()
        val tables by posViewModel.tables.collectAsState()
        val proofUploadState by posViewModel.proofUploadState.collectAsState()
        CheckoutDialogClean(
            totalAmount = totalAmount,
            itemCount = cartItems.sumOf { it.qty },
            checkoutState = checkoutState,
            isFnbBusiness = business?.transactionMode?.lowercase() == "fnb",
            tables = tables,
            proofUploadState = proofUploadState,
            onPickProofImage = { uri -> posViewModel.uploadPaymentProof(uri) },
            onClearProofImage = { posViewModel.clearPaymentProof() },
            onDismiss = { showCheckoutDialog = false },
            onConfirmCheckout = { paymentMethod, orderType, selectedTable, customerName, proofUrl ->
                posViewModel.processCheckout(
                    paymentMethod = paymentMethod,
                    orderType = orderType,
                    selectedTable = selectedTable,
                    customerName = customerName,
                    paymentProofUrl = proofUrl
                )
                showCheckoutDialog = false
            }
        )
    }

    // Modal Dialog: Receipt Struk
    if (completedOrder != null) {
        ReceiptDialogClean(
            order = completedOrder!!,
            userName = user.name,
            onDismiss = { posViewModel.dismissReceiptDialog() }
        )
    }

    // Modal Dialog: Past Order History
    if (showHistoryDialog) {
        PastOrdersDialogClean(
            orders = pastOrders,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@Composable
fun ProductBrowseArea(
    modifier: Modifier = Modifier,
    searchQuery: String,
    categories: List<Category>,
    selectedCategory: String?,
    products: List<Product>,
    cartQtyByProduct: Map<String, Int>,
    assignedBranchId: String?,
    showBarcodeScanner: Boolean = false,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onScanClick: () -> Unit,
    onDismissScanner: () -> Unit = {},
    onBarcodeScanned: (String) -> Unit = {},
    onAddToCart: (Product) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = modifier) {
        // Search Input + Tombol Pindai Barcode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isLandscape) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Cari produk...", color = MinimalTextSecondary, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = MinimalBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(if (isLandscape) 36.dp else 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (showBarcodeScanner) GreenAccentDark else GreenPrimary)
                    .clickable { onScanClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Pindai Barcode",
                    tint = Color.White,
                    modifier = Modifier.size(if (isLandscape) 18.dp else 20.dp)
                )
            }
        }

        // Card Pemindai Barcode Embedded (saat aktif, tidak menutupi keranjang / katalog)
        if (showBarcodeScanner) {
            EmbeddedBarcodeScannerCard(
                onDismiss = onDismissScanner,
                onBarcodeScanned = onBarcodeScanned
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Category Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = if (isLandscape) 4.dp else 8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("Semua", fontSize = 11.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = MinimalBorder,
                        labelColor = MinimalTextSecondary
                    )
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.id,
                    onClick = { onSelectCategory(cat.id) },
                    label = { Text(cat.name, fontSize = 11.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = MinimalBorder,
                        labelColor = MinimalTextSecondary
                    )
                )
            }
        }

        // Products Grid - minSize 100.dp agar muat 3 produk per baris di HP & landscape
        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PointOfSale,
                        contentDescription = null,
                        tint = MinimalBorder,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tidak ada produk ditemukan",
                        color = MinimalTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(products) { product ->
                    val effectiveStock = product.getEffectiveStock(assignedBranchId)
                    ProductCardClean(
                        product = product,
                        effectiveStock = effectiveStock,
                        qtyInCart = cartQtyByProduct[product.id] ?: 0,
                        onAddToCart = { onAddToCart(product) }
                    )
                }
            }
        }
    }
}

/**
 * Isi keranjang belanja (daftar item + total + tombol Simpan/Bayar) — dipakai
 * bersama di bottom sheet (mode potret) MAUPUN panel sisi kanan tetap (mode
 * landscape/miring), supaya perilakunya konsisten di kedua tata letak.
 */
@Composable
fun CartPanelContent(
    cartItems: List<CartItem>,
    totalAmount: Double,
    isFnbBusiness: Boolean,
    onIncrement: (CartItem) -> Unit,
    onDecrement: (CartItem) -> Unit,
    onClearCart: () -> Unit,
    onSave: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Keranjang Pesanan",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MinimalTextPrimary
            )
            IconButton(onClick = onClearCart) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Kosongkan Keranjang",
                    tint = Color(0xFFDC2626)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (cartItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Keranjang masih kosong", fontSize = 13.sp, color = MinimalTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cartItems) { item ->
                    CartItemRowClean(
                        cartItem = item,
                        onIncrement = { onIncrement(item) },
                        onDecrement = { onDecrement(item) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Total Bayar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                totalAmount.formatRupiah(),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = GreenPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tombol dibuat sejajar (Row) dan ukurannya menyesuaikan isi teksnya
        // sendiri (tidak dipaksa satu baris memanjang) — teks dibungkus rapi
        // maksimal 2 baris supaya tombol tetap ringkas & responsif di layar
        // sempit maupun lebar.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isFnbBusiness) {
                OutlinedButton(
                    onClick = onSave,
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Simpan ke Antrian",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onCheckout,
                enabled = cartItems.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    contentColor = GreenAccentDark
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Bayar Sekarang",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Dialog "Simpan ke Antrian Dapur" — sama seperti SaveToKitchenModal di
 * website: pilih kategori pesanan lalu simpan tanpa memproses pembayaran
 * sama sekali (pembayaran menyusul lewat layar "Bayar Meja").
 */
@Composable
fun SaveToKitchenDialog(
    tables: List<DiningTable>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (orderType: String, selectedTable: DiningTable?, customerName: String) -> Unit
) {
    var orderCategory by remember { mutableStateOf("table") }
    var selectedTable by remember { mutableStateOf<DiningTable?>(null) }
    var customerName by remember { mutableStateOf("Pelanggan POS") }

    val isValid = orderCategory != "table" || selectedTable != null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinimalBorder, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Simpan ke Antrian Dapur",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinimalTextPrimary
                )
                Text(
                    "Pesanan masuk antrian dapur dulu, pembayaran diproses belakangan lewat menu \"Bayar Meja\".",
                    fontSize = 12.sp,
                    color = MinimalTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Jenis Pesanan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("table" to "Meja", "no_table" to "Tanpa Meja", "takeaway" to "Bungkus").forEach { (key, label) ->
                        val isSelected = orderCategory == key
                        Card(
                            modifier = Modifier.weight(1f).clickable {
                                orderCategory = key
                                if (key != "table") selectedTable = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) GreenPrimary else Color(0xFFF1F5F9))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MinimalTextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                if (orderCategory == "table") {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (tables.isEmpty()) {
                        Text("Belum ada data meja untuk cabang ini.", fontSize = 11.sp, color = Color(0xFFDC2626))
                    } else {
                        Text("Pilih Meja", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalTextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(tables) { table ->
                                    val isSelected = selectedTable?.id == table.id
                                    val isOccupied = table.status == "occupied"
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable { selectedTable = table },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = when {
                                                isSelected -> GreenPrimary
                                                isOccupied -> Color(0xFFFFE4E4)
                                                else -> Color(0xFFF1F5F9)
                                            }
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                table.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else MinimalTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                if (isOccupied) "Terisi" else "Kosong",
                                                fontSize = 9.sp,
                                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MinimalTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text(if (orderCategory == "takeaway") "Nama Pemesan (Bungkus)" else "Nama Dipanggil") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = MinimalBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            onConfirm(
                                orderCategory,
                                if (orderCategory == "table") selectedTable else null,
                                customerName.ifBlank { "Pelanggan POS" }
                            )
                        },
                        enabled = isValid && !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = GreenAccentDark)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = GreenAccentDark, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Simpan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCardClean(
    product: Product,
    effectiveStock: Int,
    qtyInCart: Int = 0,
    onAddToCart: () -> Unit
) {
    val isOut = effectiveStock <= 0

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MinimalBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = !isOut) { onAddToCart() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(6.dp)
            ) {
                // Foto produk
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF0F4E9)),
                    contentAlignment = Alignment.Center
                ) {
                    val imageUrl = product.primaryImageUrl
                    if (!imageUrl.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = imageUrl,
                            contentDescription = product.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = GreenPrimary.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = product.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = MinimalTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = product.price.formatRupiah(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = GreenPrimary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stok: $effectiveStock",
                        fontSize = 9.sp,
                        color = MinimalTextSecondary
                    )

                    if (product.variants.isNotEmpty()) {
                        Text(
                            text = "${product.variants.size} Var",
                            fontSize = 8.sp,
                            color = GreenPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onAddToCart,
                    enabled = !isOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenAccent,
                        contentColor = GreenAccentDark,
                        disabledContainerColor = MinimalBorder
                    ),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    Text(
                        text = if (isOut) "Habis" else "Tambah",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Badge jumlah produk di keranjang
        if (qtyInCart > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDC2626))
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (qtyInCart > 99) "99+" else "$qtyInCart",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CartItemRowClean(
    cartItem: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MinimalBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cartItem.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MinimalTextPrimary
                )
                Text(
                    text = "${cartItem.qty} x ${cartItem.unitPrice.formatRupiah()}",
                    fontSize = 12.sp,
                    color = MinimalTextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MinimalBorder)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = "${cartItem.qty}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    fontSize = 13.sp
                )

                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashedDivider(
    color: Color = Color(0xFFCBD5E1),
    thickness: Dp = 1.dp,
    dashWidth: Dp = 4.dp,
    gapWidth: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(thickness)) {
        val pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashWidth.toPx(), gapWidth.toPx()), 0f
        )
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = thickness.toPx(),
            pathEffect = pathEffect
        )
    }
}

val SerratedReceiptShape = GenericShape { size, _ ->
    val teeth = 26
    val toothWidth = size.width / teeth
    val toothHeight = 8f

    moveTo(0f, toothHeight)
    for (i in 0 until teeth) {
        val x1 = i * toothWidth + toothWidth / 2f
        val x2 = (i + 1) * toothWidth
        lineTo(x1, 0f)
        lineTo(x2, toothHeight)
    }

    lineTo(size.width, size.height - toothHeight)

    for (i in teeth downTo 1) {
        val x1 = (i - 0.5f) * toothWidth
        val x2 = (i - 1) * toothWidth
        lineTo(x1, size.height)
        lineTo(x2, size.height - toothHeight)
    }

    lineTo(0f, toothHeight)
    close()
}

@Composable
fun CheckoutDialogClean(
    totalAmount: Double,
    itemCount: Int = 1,
    checkoutState: CheckoutState,
    isFnbBusiness: Boolean,
    tables: List<com.example.data.model.DiningTable>,
    proofUploadState: ProofUploadState,
    onPickProofImage: (android.net.Uri) -> Unit,
    onClearProofImage: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmCheckout: (
        paymentMethod: String,
        orderType: String?,
        selectedTable: com.example.data.model.DiningTable?,
        customerName: String,
        proofUrl: String?
    ) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("cash") }
    var cashInput by remember { mutableStateOf("") }

    var orderCategory by remember { mutableStateOf("table") }
    var selectedTable by remember { mutableStateOf<com.example.data.model.DiningTable?>(null) }
    var customerName by remember { mutableStateOf("Pelanggan POS") }

    val cashReceived = cashInput.toDoubleOrNull() ?: 0.0
    val change = cashReceived - totalAmount

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) onPickProofImage(uri)
    }

    val needsProof = selectedMethod != "cash"
    val proofUrl = (proofUploadState as? ProofUploadState.Success)?.url
    val isTableStepValid = !isFnbBusiness || orderCategory != "table" || selectedTable != null
    val isProofStepValid = !needsProof || proofUrl != null
    val isCashValid = selectedMethod != "cash" || cashReceived >= totalAmount

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp > 600

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(if (isWideScreen) 0.88f else 0.95f)
                .fillMaxHeight(0.92f)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        CheckoutLeftContent(
                            selectedMethod = selectedMethod,
                            onSelectMethod = { selectedMethod = it; if (it == "cash") onClearProofImage() },
                            cashInput = cashInput,
                            onCashInputChange = { cashInput = it },
                            totalAmount = totalAmount,
                            isFnbBusiness = isFnbBusiness,
                            orderCategory = orderCategory,
                            onCategoryChange = { orderCategory = it },
                            tables = tables,
                            selectedTable = selectedTable,
                            onSelectTable = { selectedTable = it },
                            customerName = customerName,
                            onCustomerNameChange = { customerName = it },
                            proofUploadState = proofUploadState,
                            onPickProofImage = { imagePickerLauncher.launch("image/*") },
                            onClearProofImage = onClearProofImage
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0xFFE2E8F0))
                    )

                    CheckoutRightPanel(
                        itemCount = itemCount,
                        totalAmount = totalAmount,
                        selectedMethod = selectedMethod,
                        cashReceived = cashReceived,
                        change = change,
                        checkoutState = checkoutState,
                        isValid = checkoutState !is CheckoutState.Processing && isCashValid && isTableStepValid && isProofStepValid,
                        onDismiss = onDismiss,
                        onConfirm = {
                            val resolvedOrderType = if (isFnbBusiness) {
                                when (orderCategory) {
                                    "table" -> "dine_in"
                                    "takeaway" -> "takeaway"
                                    else -> "no_table"
                                }
                            } else null
                            onConfirmCheckout(
                                selectedMethod,
                                resolvedOrderType,
                                if (isFnbBusiness && orderCategory == "table") selectedTable else null,
                                customerName.ifBlank { "Pelanggan POS" },
                                proofUrl
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    CheckoutLeftContent(
                        selectedMethod = selectedMethod,
                        onSelectMethod = { selectedMethod = it; if (it == "cash") onClearProofImage() },
                        cashInput = cashInput,
                        onCashInputChange = { cashInput = it },
                        totalAmount = totalAmount,
                        isFnbBusiness = isFnbBusiness,
                        orderCategory = orderCategory,
                        onCategoryChange = { orderCategory = it },
                        tables = tables,
                        selectedTable = selectedTable,
                        onSelectTable = { selectedTable = it },
                        customerName = customerName,
                        onCustomerNameChange = { customerName = it },
                        proofUploadState = proofUploadState,
                        onPickProofImage = { imagePickerLauncher.launch("image/*") },
                        onClearProofImage = onClearProofImage
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    DashedDivider()
                    Spacer(modifier = Modifier.height(20.dp))

                    CheckoutRightPanel(
                        itemCount = itemCount,
                        totalAmount = totalAmount,
                        selectedMethod = selectedMethod,
                        cashReceived = cashReceived,
                        change = change,
                        checkoutState = checkoutState,
                        isValid = checkoutState !is CheckoutState.Processing && isCashValid && isTableStepValid && isProofStepValid,
                        onDismiss = onDismiss,
                        onConfirm = {
                            val resolvedOrderType = if (isFnbBusiness) {
                                when (orderCategory) {
                                    "table" -> "dine_in"
                                    "takeaway" -> "takeaway"
                                    else -> "no_table"
                                }
                            } else null
                            onConfirmCheckout(
                                selectedMethod,
                                resolvedOrderType,
                                if (isFnbBusiness && orderCategory == "table") selectedTable else null,
                                customerName.ifBlank { "Pelanggan POS" },
                                proofUrl
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckoutLeftContent(
    selectedMethod: String,
    onSelectMethod: (String) -> Unit,
    cashInput: String,
    onCashInputChange: (String) -> Unit,
    totalAmount: Double,
    isFnbBusiness: Boolean,
    orderCategory: String,
    onCategoryChange: (String) -> Unit,
    tables: List<com.example.data.model.DiningTable>,
    selectedTable: com.example.data.model.DiningTable?,
    onSelectTable: (com.example.data.model.DiningTable?) -> Unit,
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    proofUploadState: ProofUploadState,
    onPickProofImage: () -> Unit,
    onClearProofImage: () -> Unit
) {
    Text(
        text = "Pilih Metode Pembayaran",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E293B)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val methods = listOf(
            Triple("cash", "TUNAI (CASH)", Icons.Default.AttachMoney),
            Triple("qris", "QRIS DIGITAL", Icons.Default.QrCodeScanner),
            Triple("transfer", "BANK TRANSFER", Icons.Default.AccountBalance)
        )

        methods.forEach { (key, label, icon) ->
            val isSelected = selectedMethod == key
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMethod(key) },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isSelected) Color(0xFFC8A76B) else Color(0xFFE2E8F0)
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFFFAF2E6) else Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) Color(0xFF8C6218) else Color(0xFF64748B),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF8C6218) else Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (selectedMethod == "cash") {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "JUMLAH UANG DITERIMA",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = cashInput,
            onValueChange = onCashInputChange,
            leadingIcon = {
                Text(
                    text = "Rp",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(start = 12.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF8FAFC),
                unfocusedContainerColor = Color(0xFFF8FAFC),
                focusedBorderColor = Color(0xFFC8A76B),
                unfocusedBorderColor = Color(0xFFE2E8F0)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "UANG PAS / REKOMENDASI CEPAT:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onCashInputChange(totalAmount.toInt().toString()) },
                label = { Text("Uang Pas", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp),
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF1F5F9))
            )

            val quickOptions = listOf(50000.0, 100000.0, 200000.0, 500000.0)
            val filtered = quickOptions.filter { it >= totalAmount }.take(3)
            val optionsToShow = if (filtered.isEmpty()) listOf(totalAmount) else filtered

            optionsToShow.forEach { amt ->
                AssistChip(
                    onClick = { onCashInputChange(amt.toInt().toString()) },
                    label = { Text(amt.formatRupiah(), fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF1F5F9))
                )
            }
        }
    } else {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "BUKTI PEMBAYARAN (${selectedMethod.uppercase()})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (proofUploadState) {
            is ProofUploadState.Success -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEFFBEE))
                        .border(1.dp, Color(0xFFBBE8B4), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    coil.compose.AsyncImage(
                        model = proofUploadState.url,
                        contentDescription = "Bukti pembayaran",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bukti tersimpan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E8E3E))
                        Text("Akan diarsipkan bersama transaksi ini.", fontSize = 10.sp, color = MinimalTextSecondary)
                    }
                    IconButton(onClick = onClearProofImage) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus & unggah ulang", tint = MinimalTextSecondary)
                    }
                }
            }
            is ProofUploadState.Uploading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mengunggah bukti...", fontSize = 12.sp, color = MinimalTextSecondary)
                }
            }
            else -> {
                OutlinedButton(
                    onClick = onPickProofImage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = GreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unggah Foto Bukti Bayar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                }
                if (proofUploadState is ProofUploadState.Error) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(proofUploadState.message, fontSize = 11.sp, color = Color(0xFFDC2626))
                }
            }
        }
    }

    if (isFnbBusiness) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("JENIS PESANAN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("table" to "Meja", "no_table" to "Tanpa Meja", "takeaway" to "Bungkus").forEach { (key, label) ->
                val isSelected = orderCategory == key
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onCategoryChange(key)
                            if (key != "table") onSelectTable(null)
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) GreenPrimary else Color(0xFFF1F5F9)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MinimalTextSecondary
                        )
                    }
                }
            }
        }

        if (orderCategory == "table") {
            Spacer(modifier = Modifier.height(10.dp))
            if (tables.isEmpty()) {
                Text(
                    "Belum ada data meja. Tambahkan meja lewat admin terlebih dahulu.",
                    fontSize = 11.sp,
                    color = Color(0xFFDC2626)
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tables) { table ->
                            val isSelected = selectedTable?.id == table.id
                            val isOccupied = table.status == "occupied"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTable(table) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isSelected -> GreenPrimary
                                        isOccupied -> Color(0xFFFFE4E4)
                                        else -> Color(0xFFF1F5F9)
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        table.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MinimalTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutRightPanel(
    itemCount: Int,
    totalAmount: Double,
    selectedMethod: String,
    cashReceived: Double,
    change: Double,
    checkoutState: CheckoutState,
    isValid: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHECKOUT POS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color(0xFF64748B))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Jumlah Barang:", fontSize = 14.sp, color = Color(0xFF475569))
                Text("$itemCount Pcs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            }

            Spacer(modifier = Modifier.height(12.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tagihan:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Text(
                    totalAmount.formatRupiah(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFB45309)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("KEMBALIAN:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                Text(
                    text = if (selectedMethod == "cash") {
                        if (change >= 0) change.formatRupiah() else "Kurang ${(totalAmount - cashReceived).formatRupiah()}"
                    } else "Rp 0",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D)
                )
            }

            if (checkoutState is CheckoutState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(checkoutState.message, fontSize = 12.sp, color = Color(0xFFDC2626))
            }
        }

        Column(modifier = Modifier.padding(top = 24.dp)) {
            Button(
                onClick = onConfirm,
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F5128),
                    contentColor = Color.White
                )
            ) {
                if (checkoutState is CheckoutState.Processing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Memproses...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Selesai & Cetak Struk", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569))
            ) {
                Text("Batal", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ReceiptDialogClean(
    order: Order,
    userName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printerManager = remember { ThermalPrinterManager(context) }
    val clipboardManager = LocalClipboardManager.current

    var isPrinting by remember { mutableStateOf(false) }
    var showPrinterSettings by remember { mutableStateOf(false) }

    if (showPrinterSettings) {
        PrinterSettingsDialog(onDismiss = { showPrinterSettings = false })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = SerratedReceiptShape,
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 28.dp, bottom = 28.dp, start = 24.dp, end = 24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "KOPI PAGI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E293B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SaaS POS Multi-tenant",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Makassar, Indonesia",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        DashedDivider(color = Color(0xFFCBD5E1))
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ReceiptMetaRow("No. Order:", order.id.ifBlank { "UBHDOMB3JU..." }.take(16))
                            ReceiptMetaRow(
                                "Tanggal:",
                                if (order.createdAt.isNotBlank()) order.createdAt
                                else SimpleDateFormat("d/M/yyyy, HH.mm.ss", Locale("id", "ID")).format(Date())
                            )
                            ReceiptMetaRow("Kasir:", userName.ifBlank { "dark anime" })
                            ReceiptMetaRow("Sumber:", "POS")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        DashedDivider(color = Color(0xFFCBD5E1))
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            order.items.forEach { item ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.name + if (!item.variant.isNullOrBlank()) " (${item.variant})" else "",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF1E293B),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = (item.price * item.qty).formatRupiah(),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                    Text(
                                        text = "${item.qty} x ${item.price.formatRupiah()}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        DashedDivider(color = Color(0xFFCBD5E1))
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReceiptSummaryRow("Subtotal:", order.totalAmount.formatRupiah())
                            ReceiptSummaryRow("Pajak (0%):", "Rp 0")

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL :",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = order.totalAmount.formatRupiah(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1E293B)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            ReceiptSummaryRow("Metode:", order.paymentMethod.uppercase())

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Status:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    "LUNAS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        DashedDivider(color = Color(0xFFCBD5E1))
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "*** TERIMA KASIH ***",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sudah berbelanja di outlet kami",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Sistem kasir digital terintegrasi cloud",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kembali", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val textToCopy = generateReceiptText(order, userName)
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            Toast.makeText(context, "Teks struk berhasil disalin!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5EADA),
                            contentColor = Color(0xFF8C6218)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salin Teks", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val saved = printerManager.getSavedPrinter()
                            if (saved == null) {
                                Toast.makeText(context, "Silakan atur printer Bluetooth terlebih dahulu.", Toast.LENGTH_SHORT).show()
                                showPrinterSettings = true
                            } else {
                                isPrinting = true
                                scope.launch {
                                    val receiptData = order.toReceiptData(businessName = "KOPI PAGI", cashierName = userName)
                                    val result = printerManager.printReceipt(saved, receiptData)
                                    isPrinting = false
                                    result.fold(
                                        onSuccess = { Toast.makeText(context, "Struk berhasil dicetak!", Toast.LENGTH_SHORT).show() },
                                        onFailure = { err -> Toast.makeText(context, err.message ?: "Gagal cetak", Toast.LENGTH_LONG).show() }
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB45309),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cetak", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val saved = printerManager.getSavedPrinter()
                        if (saved == null) {
                            Toast.makeText(context, "Silakan atur printer Bluetooth terlebih dahulu.", Toast.LENGTH_SHORT).show()
                            showPrinterSettings = true
                        } else {
                            isPrinting = true
                            scope.launch {
                                val receiptData = order.toReceiptData(businessName = "KOPI PAGI", cashierName = userName)
                                val result = printerManager.printReceipt(saved, receiptData)
                                isPrinting = false
                                result.fold(
                                    onSuccess = { Toast.makeText(context, "Struk berhasil dicetak!", Toast.LENGTH_SHORT).show() },
                                    onFailure = { err -> Toast.makeText(context, err.message ?: "Gagal cetak", Toast.LENGTH_LONG).show() }
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF334155))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cetak ke Thermal Printer (Bluetooth/USB)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showPrinterSettings = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Atur Printer Thermal", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ReceiptMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
    }
}

@Composable
private fun ReceiptSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
    }
}

private fun generateReceiptText(order: Order, userName: String): String {
    return buildString {
        appendLine("============== KOPI PAGI ==============")
        appendLine("SaaS POS Multi-tenant")
        appendLine("Makassar, Indonesia")
        appendLine("------------------------------------------")
        appendLine("No. Order : ${order.id.take(16)}")
        appendLine("Tanggal   : ${if (order.createdAt.isNotBlank()) order.createdAt else SimpleDateFormat("d/M/yyyy, HH.mm.ss", Locale("id", "ID")).format(Date())}")
        appendLine("Kasir     : $userName")
        appendLine("Sumber    : POS")
        appendLine("------------------------------------------")
        order.items.forEach { item ->
            appendLine("${item.name} ${item.variant?.let { "($it)" } ?: ""}")
            appendLine("  ${item.qty} x ${item.price.formatRupiah()} = ${(item.qty * item.price).formatRupiah()}")
        }
        appendLine("------------------------------------------")
        appendLine("Subtotal  : ${order.totalAmount.formatRupiah()}")
        appendLine("Pajak (0%): Rp 0")
        appendLine("TOTAL     : ${order.totalAmount.formatRupiah()}")
        appendLine("Metode    : ${order.paymentMethod.uppercase()}")
        appendLine("Status    : LUNAS")
        appendLine("------------------------------------------")
        appendLine("*** TERIMA KASIH ***")
        appendLine("Sudah berbelanja di outlet kami")
    }
}

@Composable
fun PastOrdersDialogClean(
    orders: List<Order>,
    userName: String = "Kasir",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printerManager = remember { ThermalPrinterManager(context) }

    var showPrinterSettings by remember { mutableStateOf(false) }
    var printingOrderId by remember { mutableStateOf<String?>(null) }

    if (showPrinterSettings) {
        PrinterSettingsDialog(onDismiss = { showPrinterSettings = false })
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .border(1.dp, MinimalBorder, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Riwayat Transaksi",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showPrinterSettings = true }) {
                            Icon(Icons.Default.Print, contentDescription = "Pengaturan Printer", tint = GreenPrimary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (orders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Belum ada transaksi recorded", color = MinimalTextSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(orders) { order ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MinimalBorder, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MinimalBackground)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "ID: ${order.id.take(12)}...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = GreenPrimary
                                        )
                                        Text(
                                            text = order.paymentMethod.uppercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MinimalTextSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "${order.items.size} item • Total: ${order.totalAmount.formatRupiah()}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
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
                                                            businessName = "USAHAKI POS",
                                                            cashierName = userName
                                                        )
                                                        val res = printerManager.printReceipt(saved, receiptData)
                                                        printingOrderId = null
                                                        res.fold(
                                                            onSuccess = {
                                                                Toast.makeText(context, "Struk berhasil dicetak!", Toast.LENGTH_SHORT).show()
                                                            },
                                                            onFailure = { err ->
                                                                Toast.makeText(context, err.message ?: "Gagal cetak", Toast.LENGTH_LONG).show()
                                                            }
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
        }
    }
}
