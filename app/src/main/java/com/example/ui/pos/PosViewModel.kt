package com.example.ui.pos

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Branch
import com.example.data.model.CartItem
import com.example.data.model.Category
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

            val unlockedRoles = listOf("owner", "admin", "manager")
            if (user.role.lowercase() in unlockedRoles) {
                viewModelScope.launch {
                    _availableBranches.value = repository.getActiveBranches(user.businessId)
                }
            }
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

    fun processCheckout(paymentMethod: String) {
        val user = _currentUser.value ?: return
        val currentCart = _cartItems.value
        if (currentCart.isEmpty()) return

        val total = currentCart.sumOf { it.totalPrice }
        _checkoutState.value = CheckoutState.Processing

        viewModelScope.launch {
            val result = repository.processCheckout(
                user = user,
                cartItems = currentCart,
                paymentMethod = paymentMethod,
                totalAmount = total,
                operatingBranchId = _operatingBranchId.value
            )

            result.onSuccess { order ->
                _checkoutState.value = CheckoutState.Success(order)
                _lastCompletedOrder.value = order
                _cartItems.value = emptyList()
            }.onFailure { err ->
                _checkoutState.value = CheckoutState.Error(err.localizedMessage ?: "Checkout gagal.")
            }
        }
    }

    fun dismissReceiptDialog() {
        _lastCompletedOrder.value = null
        _checkoutState.value = CheckoutState.Idle
    }
}
