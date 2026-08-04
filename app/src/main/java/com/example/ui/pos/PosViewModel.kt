package com.example.ui.pos

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Branch
import com.example.data.model.Business
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.DiningTable
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.ProductVariant
import com.example.data.model.User
import com.example.data.repository.UsahakiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CheckoutState {
    object Idle : CheckoutState
    object Processing : CheckoutState
    data class Success(val order: Order) : CheckoutState
    data class Error(val message: String) : CheckoutState
}

sealed interface ProofUploadState {
    object Idle : ProofUploadState
    object Uploading : ProofUploadState
    data class Success(val url: String) : ProofUploadState
    data class Error(val message: String) : ProofUploadState
}

sealed interface SaveQueueState {
    object Idle : SaveQueueState
    object Saving : SaveQueueState
    object Success : SaveQueueState
    data class Error(val message: String) : SaveQueueState
}

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsahakiRepository(application)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Cabang TEMPAT staf ini beroperasi sekarang. Untuk role terkunci
    // (kasir/kitchen/manager_cabang) SELALU sama dengan user.assignedBranchId
    // dan tidak bisa diganti. Untuk role tidak terkunci (owner/admin/manager)
    // ini bisa diganti lewat Branch Switcher (BranchSwitcherBar di
    // MainAppScreen) — null berarti "Pusat".
    private val _operatingBranchId = MutableStateFlow<String?>(null)
    val operatingBranchId: StateFlow<String?> = _operatingBranchId.asStateFlow()

    private val _availableBranches = MutableStateFlow<List<Branch>>(emptyList())
    val availableBranches: StateFlow<List<Branch>> = _availableBranches.asStateFlow()

    // Info bisnis (dipakai utk cek transactionMode "fnb" — hanya bisnis F&B
    // yang menampilkan pilihan Meja/Tanpa Meja/Bungkus saat checkout, sama
    // seperti di website).
    private val _business = MutableStateFlow<Business?>(null)
    val business: StateFlow<Business?> = _business.asStateFlow()

    // Daftar meja aktif utk cabang yang sedang beroperasi (koleksi "tables").
    private val _tables = MutableStateFlow<List<DiningTable>>(emptyList())
    val tables: StateFlow<List<DiningTable>> = _tables.asStateFlow()

    private val _proofUploadState = MutableStateFlow<ProofUploadState>(ProofUploadState.Idle)
    val proofUploadState: StateFlow<ProofUploadState> = _proofUploadState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _rawProducts = MutableStateFlow<List<Product>>(emptyList())
    val rawProducts: StateFlow<List<Product>> = _rawProducts.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _pastOrders = MutableStateFlow<List<Order>>(emptyList())
    val pastOrders: StateFlow<List<Order>> = _pastOrders.asStateFlow()

    // Variant Selection Dialog State
    private val _variantSelectionProduct = MutableStateFlow<Product?>(null)
    val variantSelectionProduct: StateFlow<Product?> = _variantSelectionProduct.asStateFlow()

    // Receipt Dialog State
    private val _lastCompletedOrder = MutableStateFlow<Order?>(null)
    val lastCompletedOrder: StateFlow<Order?> = _lastCompletedOrder.asStateFlow()

    private val _checkoutState = MutableStateFlow<CheckoutState>(CheckoutState.Idle)
    val checkoutState: StateFlow<CheckoutState> = _checkoutState.asStateFlow()

    private val _saveQueueState = MutableStateFlow<SaveQueueState>(SaveQueueState.Idle)
    val saveQueueState: StateFlow<SaveQueueState> = _saveQueueState.asStateFlow()

    // Filtered Products
    val filteredProducts: StateFlow<List<Product>> = combine(
        _rawProducts,
        _searchQuery,
        _selectedCategoryId
    ) { products, query, catId ->
        products.filter { p ->
            val matchesQuery = query.isBlank() || p.name.contains(query, ignoreCase = true) || (p.sku?.contains(query, ignoreCase = true) == true)
            val matchesCat = catId == null || p.categoryId == catId
            matchesQuery && matchesCat
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalAmount: StateFlow<Double> = combine(_cartItems) { itemsList ->
        itemsList.firstOrNull()?.sumOf { it.totalPrice } ?: 0.0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    private var pastOrdersJob: kotlinx.coroutines.Job? = null

    fun setUser(user: User) {
        if (_currentUser.value?.id != user.id) {
            _currentUser.value = user
            _operatingBranchId.value = user.assignedBranchId
            observeData(user)
            refreshTables(user.businessId, _operatingBranchId.value)

            viewModelScope.launch {
                _business.value = repository.fetchBusiness(user.businessId)
            }

            val unlockedRoles = listOf("owner", "admin", "manager")
            if (user.role.lowercase() in unlockedRoles) {
                viewModelScope.launch {
                    _availableBranches.value = repository.getActiveBranches(user.businessId)
                }
            }
        }
    }

    private fun refreshTables(businessId: String, branchId: String?) {
        viewModelScope.launch {
            _tables.value = repository.getTables(businessId, branchId)
        }
    }

    /**
     * Ganti cabang tempat kasir ini beroperasi (dipanggil dari Branch
     * Switcher — hanya tersedia untuk role tidak terkunci). Order/transaksi/
     * pengurangan stok berikutnya akan pakai cabang baru ini.
     */
    fun setOperatingBranch(branchId: String?) {
        val user = _currentUser.value ?: return
        _operatingBranchId.value = branchId
        observePastOrdersFor(user, branchId)
        refreshTables(user.businessId, branchId)
    }

    private fun observeData(user: User) {
        viewModelScope.launch {
            repository.observeProducts(user.businessId).collect {
                _rawProducts.value = it
            }
        }
        viewModelScope.launch {
            repository.observeCategories(user.businessId).collect {
                _categories.value = it
            }
        }
        observePastOrdersFor(user, _operatingBranchId.value)
    }

    private fun observePastOrdersFor(user: User, branchId: String?) {
        // Hentikan listener riwayat order cabang sebelumnya dulu — kalau
        // tidak, tiap ganti cabang menumpuk listener Firestore baru yang
        // tidak pernah berhenti (leak, dan bisa balapan menimpa data cabang
        // yang baru dipilih dengan data cabang lama yang telat masuk).
        pastOrdersJob?.cancel()
        pastOrdersJob = viewModelScope.launch {
            repository.observePastOrders(user, branchId).collect {
                _pastOrders.value = it
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun openVariantPicker(product: Product) {
        _variantSelectionProduct.value = product
    }

    fun closeVariantPicker() {
        _variantSelectionProduct.value = null
    }

    fun addToCart(product: Product, variant: ProductVariant? = null) {
        val user = _currentUser.value ?: return
        val branchId = _operatingBranchId.value

        // If product has variants and no variant is explicitly chosen yet
        if (product.variants.isNotEmpty() && variant == null) {
            openVariantPicker(product)
            return
        }

        val effectiveStock = product.getEffectiveStock(branchId, variant)
        if (effectiveStock <= 0) {
            Toast.makeText(getApplication(), "Stok ${product.name} habis untuk cabang ini!", Toast.LENGTH_SHORT).show()
            return
        }

        val currentCart = _cartItems.value.toMutableList()
        val existingIndex = currentCart.indexOfFirst {
            it.product.id == product.id && it.selectedVariant?.name == variant?.name
        }

        if (existingIndex != -1) {
            val existingItem = currentCart[existingIndex]
            if (existingItem.qty + 1 > effectiveStock) {
                Toast.makeText(getApplication(), "Maksimal stok tersedia ($effectiveStock) telah dicapai!", Toast.LENGTH_SHORT).show()
                return
            }
            currentCart[existingIndex] = existingItem.copy(qty = existingItem.qty + 1)
        } else {
            currentCart.add(CartItem(product = product, selectedVariant = variant, qty = 1))
        }

        _cartItems.value = currentCart
        closeVariantPicker()
    }

    fun updateCartQty(cartItem: CartItem, delta: Int) {
        val user = _currentUser.value ?: return
        val branchId = _operatingBranchId.value
        val effectiveStock = cartItem.product.getEffectiveStock(branchId, cartItem.selectedVariant)

        val currentCart = _cartItems.value.toMutableList()
        val index = currentCart.indexOfFirst {
            it.product.id == cartItem.product.id && it.selectedVariant?.name == cartItem.selectedVariant?.name
        }

        if (index != -1) {
            val newQty = currentCart[index].qty + delta
            if (newQty <= 0) {
                currentCart.removeAt(index)
            } else if (newQty > effectiveStock) {
                Toast.makeText(getApplication(), "Maksimal stok tersedia ($effectiveStock)", Toast.LENGTH_SHORT).show()
            } else {
                currentCart[index] = currentCart[index].copy(qty = newQty)
            }
            _cartItems.value = currentCart
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    /** Unggah foto bukti bayar (transfer/QRIS) ke Firebase Storage sebelum checkout dikonfirmasi. */
    fun uploadPaymentProof(uri: Uri) {
        _proofUploadState.value = ProofUploadState.Uploading
        viewModelScope.launch {
            val result = repository.uploadPaymentProof(uri)
            result.onSuccess { url ->
                _proofUploadState.value = ProofUploadState.Success(url)
            }.onFailure { err ->
                _proofUploadState.value = ProofUploadState.Error(err.localizedMessage ?: "Gagal mengunggah bukti pembayaran.")
            }
        }
    }

    fun clearPaymentProof() {
        _proofUploadState.value = ProofUploadState.Idle
    }

    fun processCheckout(
        paymentMethod: String,
        orderType: String? = null,
        selectedTable: DiningTable? = null,
        customerName: String = "Pelanggan POS",
        paymentProofUrl: String? = null
    ) {
        val user = _currentUser.value ?: return
        val currentCart = _cartItems.value
        if (currentCart.isEmpty()) return

        // Bukti pembayaran wajib utk Transfer & QRIS, sama seperti di website
        // (PaymentProofCapture) — supaya tetap ada arsip transaksi.
        if (paymentMethod != "cash" && paymentProofUrl.isNullOrEmpty()) {
            _checkoutState.value = CheckoutState.Error("Unggah bukti pembayaran terlebih dahulu untuk metode ${paymentMethod.uppercase()}.")
            return
        }

        val total = currentCart.sumOf { it.totalPrice }
        _checkoutState.value = CheckoutState.Processing

        viewModelScope.launch {
            val result = repository.processCheckout(
                user = user,
                cartItems = currentCart,
                paymentMethod = paymentMethod,
                totalAmount = total,
                operatingBranchId = _operatingBranchId.value,
                orderType = orderType,
                selectedTable = selectedTable,
                customerName = customerName,
                paymentProofUrl = paymentProofUrl
            )

            result.onSuccess { order ->
                _checkoutState.value = CheckoutState.Success(order)
                _lastCompletedOrder.value = order
                _cartItems.value = emptyList()
                _proofUploadState.value = ProofUploadState.Idle
                if (selectedTable != null) {
                    refreshTables(user.businessId, _operatingBranchId.value)
                }
            }.onFailure { err ->
                _checkoutState.value = CheckoutState.Error(err.localizedMessage ?: "Checkout gagal.")
            }
        }
    }

    /**
     * Simpan pesanan ke antrian dapur tanpa membayar dulu — sama seperti
     * tombol "Simpan" (Simpan ke Antrian Dapur) di website. Kasir bisa
     * memprosesnya belakangan lewat layar "Bayar Meja".
     */
    fun saveToKitchen(
        orderType: String,
        selectedTable: DiningTable? = null,
        customerName: String = "Pelanggan POS"
    ) {
        val user = _currentUser.value ?: return
        val currentCart = _cartItems.value
        if (currentCart.isEmpty()) return

        _saveQueueState.value = SaveQueueState.Saving
        viewModelScope.launch {
            val result = repository.saveOrderToKitchenQueue(
                user = user,
                cartItems = currentCart,
                operatingBranchId = _operatingBranchId.value,
                orderType = orderType,
                selectedTable = selectedTable,
                customerName = customerName
            )
            result.onSuccess {
                _saveQueueState.value = SaveQueueState.Success
                _cartItems.value = emptyList()
                if (selectedTable != null) {
                    refreshTables(user.businessId, _operatingBranchId.value)
                }
            }.onFailure { err ->
                _saveQueueState.value = SaveQueueState.Error(err.localizedMessage ?: "Gagal menyimpan ke antrian dapur.")
            }
        }
    }

    fun resetSaveQueueState() {
        _saveQueueState.value = SaveQueueState.Idle
    }

    /**
     * Cocokkan kode barcode/QR hasil pindai dengan katalog produk — meniru
     * persis logika `productBarcodes` di BarcodeScanner.tsx pada website:
     * utamakan SKU varian, lalu SKU produk, lalu 8 karakter awal ID produk
     * sebagai fallback (produk lama yang belum diberi SKU).
     */
    fun lookupByBarcode(code: String): Pair<Product, ProductVariant?>? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null

        for (product in _rawProducts.value) {
            if (product.variants.isNotEmpty()) {
                val matchedVariant = product.variants.firstOrNull {
                    !it.sku.isNullOrBlank() && it.sku.equals(trimmed, ignoreCase = true)
                }
                if (matchedVariant != null) return product to matchedVariant
            } else {
                val fallbackCode = product.sku?.takeIf { it.isNotBlank() }
                    ?: product.id.take(8).uppercase()
                if (fallbackCode.equals(trimmed, ignoreCase = true)) return product to null
            }
        }
        return null
    }

    fun dismissReceiptDialog() {
        _lastCompletedOrder.value = null
        _checkoutState.value = CheckoutState.Idle
    }
}
