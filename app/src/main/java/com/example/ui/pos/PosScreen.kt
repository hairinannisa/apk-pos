package com.example.ui.pos

import android.content.res.Configuration
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Usahaki.id",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = GreenPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            RoleBadge(role = user.role)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        BranchBadge(assignedBranchId = user.assignedBranchId)
                    }
                },
                actions = {
                    IconButton(onClick = { showCashierQueue = true }) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Bayar Pesanan Meja",
                            tint = MinimalTextSecondary
                        )
                    }
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Riwayat Transaksi",
                            tint = MinimalTextSecondary
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Keluar",
                            tint = MinimalTextSecondary
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
    Column(modifier = modifier) {
        // Search Input + Tombol Pindai Barcode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Cari produk...", color = MinimalTextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GreenPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = MinimalBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (showBarcodeScanner) GreenAccentDark else GreenPrimary)
                    .clickable { onScanClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Pindai Barcode",
                    tint = Color.White
                )
            }
        }

        // Card Pemindai Barcode Embedded (saat aktif, tidak menutupi keranjang / katalog)
        if (showBarcodeScanner) {
            EmbeddedBarcodeScannerCard(
                onDismiss = onDismissScanner,
                onBarcodeScanned = onBarcodeScanned
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Category Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("Semua") },
                    shape = RoundedCornerShape(20.dp),
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
                    label = { Text(cat.name) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = MinimalBorder,
                        labelColor = MinimalTextSecondary
                    )
                )
            }
        }

        // Products Grid
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
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tidak ada produk ditemukan",
                        color = MinimalTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // GridCells.Adaptive membuat jumlah kolom menyesuaikan lebar layar
            // secara dinamis (HP kecil tetap 2 kolom, HP besar/tablet otomatis
            // jadi 3-5 kolom) — sebelumnya Fixed(2) membuat setiap kartu produk
            // melebar penuh & terlihat terlalu besar di layar lebar.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                .border(1.dp, MinimalBorder, RoundedCornerShape(16.dp))
                .clickable(enabled = !isOut) { onAddToCart() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Foto produk (kalau ada). Fallback ke ikon kalau produk belum
            // punya gambar yang diunggah di website.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(10.dp))
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
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MinimalTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = product.price.formatRupiah(),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
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
                    fontSize = 10.sp,
                    color = MinimalTextSecondary
                )

                if (product.variants.isNotEmpty()) {
                    Text(
                        text = "${product.variants.size} Varian",
                        fontSize = 9.sp,
                        color = GreenPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onAddToCart,
                enabled = !isOut,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    contentColor = GreenAccentDark,
                    disabledContainerColor = MinimalBorder
                ),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(
                    text = if (isOut) "Habis" else "Tambah",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        }

        // Badge angka jumlah produk ini yang sudah ada di keranjang — muncul
        // begitu produk diklik/ditambahkan, mengambang di pojok kanan-atas
        // kartu, mirip badge notifikasi.
        if (qtyInCart > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDC2626))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (qtyInCart > 99) "99+" else "$qtyInCart",
                    color = Color.White,
                    fontSize = 10.sp,
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
fun CheckoutDialogClean(
    totalAmount: Double,
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

    // Kategori pemesanan, sama seperti "Simpan Ke Antrian Dapur" di website:
    // "table" (meja), "no_table" (tanpa meja/panggil nama), "takeaway" (bungkus).
    // Hanya relevan & ditampilkan utk bisnis F&B.
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .border(1.dp, MinimalBorder, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Pembayaran Kasir",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF0F4E9))
                            .border(1.dp, MinimalBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Tagihan", fontSize = 12.sp, color = MinimalTextSecondary)
                            Text(
                                totalAmount.formatRupiah(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GreenPrimary
                            )
                        }
                    }

                    // --- Kategori Pemesanan: Meja / Tanpa Meja / Bungkus ---
                    // Hanya ditampilkan utk bisnis F&B, sama seperti
                    // SaveToKitchenModal di website (transactionMode === 'fnb').
                    if (isFnbBusiness) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text("Jenis Pesanan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "table" to "Meja",
                                "no_table" to "Tanpa Meja",
                                "takeaway" to "Bungkus"
                            ).forEach { (key, label) ->
                                val isSelected = orderCategory == key
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            orderCategory = key
                                            if (key != "table") selectedTable = null
                                        },
                                    shape = RoundedCornerShape(12.dp),
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
                                            color = if (isSelected) Color.White else MinimalTextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        if (orderCategory == "table") {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (tables.isEmpty()) {
                                Text(
                                    "Belum ada data meja untuk cabang ini. Tambahkan meja lewat website terlebih dahulu.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFDC2626)
                                )
                            } else {
                                Text("Pilih Meja", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalTextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                // Tinggi tetap (bukan Unspecified) — LazyVerticalGrid butuh
                                // batas tinggi pasti karena sudah berada di dalam Column yang
                                // scrollable; kalau tak dibatasi, akan crash "infinite height".
                                // Meja yang jumlahnya sedikit tetap bisa discroll, tidak masalah.
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
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedTable = table },
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
                            Spacer(modifier = Modifier.height(10.dp))
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
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text("Metode Pembayaran", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("cash" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer").forEach { (key, label) ->
                            val isSelected = selectedMethod == key
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedMethod = key
                                        if (key == "cash") onClearProofImage()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) GreenPrimary else Color(0xFFF1F5F9)
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MinimalTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (selectedMethod == "cash") {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = cashInput,
                            onValueChange = { cashInput = it },
                            label = { Text("Uang Diterima (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = MinimalBorder
                            )
                        )

                        if (cashReceived > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Kembalian:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    if (change >= 0) change.formatRupiah() else "Kurang ${(-change).formatRupiah()}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (change >= 0) GreenPrimary else Color(0xFFDC2626)
                                )
                            }
                        }
                    } else {
                        // --- Upload Bukti Pembayaran (Transfer / QRIS) ---
                        // Sebelumnya tidak ada sama sekali di APK, padahal
                        // wajib di website (PaymentProofCapture).
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Bukti Pembayaran (${selectedMethod.uppercase()})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextSecondary
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
                                    IconButton(onClick = { onClearProofImage() }) {
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
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp),
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

                    if (checkoutState is CheckoutState.Error) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(checkoutState.message, fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                    }
                }

                // Tombol proses tetap terlihat di bawah (di luar area scroll)
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Button(
                        onClick = {
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
                        enabled = checkoutState !is CheckoutState.Processing &&
                            isCashValid && isTableStepValid && isProofStepValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenAccent,
                            contentColor = GreenAccentDark
                        )
                    ) {
                        if (checkoutState is CheckoutState.Processing) {
                            CircularProgressIndicator(color = GreenAccentDark, modifier = Modifier.size(20.dp))
                        } else {
                            Text("PROSES TRANSAKSI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinimalBorder, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD7E8CD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pembayaran Selesai!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinimalTextPrimary
                )

                Text(
                    text = "ID: ${order.id}",
                    fontSize = 11.sp,
                    color = MinimalTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MinimalBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MinimalBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "STRUK TRANSAKSI",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.qty}x ${item.name} ${if (item.variant != null) "(${item.variant})" else ""}",
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = (item.price * item.qty).formatRupiah(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MinimalBorder)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                order.totalAmount.formatRupiah(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = GreenPrimary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Metode", fontSize = 12.sp, color = MinimalTextSecondary)
                            Text(
                                order.paymentMethod.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kasir", fontSize = 12.sp, color = MinimalTextSecondary)
                            Text(userName, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("SELESAI", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PastOrdersDialogClean(
    orders: List<Order>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
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
                                            text = "ID: ${order.id.take(10)}...",
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
