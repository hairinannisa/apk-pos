package com.example.ui.kitchen

import android.app.Application
import android.media.RingtoneManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Branch
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.TableOrder
import com.example.data.model.TableOrderItem
import com.example.data.model.User
import com.example.data.repository.UsahakiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KitchenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsahakiRepository(application)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Cabang tempat antrian dapur ini ditampilkan — sama seperti di
    // PosViewModel, terkunci untuk role kasir/kitchen/manager_cabang, bisa
    // diganti lewat Branch Switcher untuk owner/admin/manager.
    private val _operatingBranchId = MutableStateFlow<String?>(null)
    val operatingBranchId: StateFlow<String?> = _operatingBranchId.asStateFlow()

    private val _availableBranches = MutableStateFlow<List<Branch>>(emptyList())
    val availableBranches: StateFlow<List<Branch>> = _availableBranches.asStateFlow()

    private val _orders = MutableStateFlow<List<TableOrder>>(emptyList())
    val orders: StateFlow<List<TableOrder>> = _orders.asStateFlow()

    // Riwayat pesanan yang dibatalkan (sama seperti tab "Dibatalkan" di
    // QueueTab website), supaya dapur/kasir bisa cek kembali kalau perlu.
    private val _cancelledOrders = MutableStateFlow<List<TableOrder>>(emptyList())
    val cancelledOrders: StateFlow<List<TableOrder>> = _cancelledOrders.asStateFlow()

    // Katalog produk & kategori — dipakai utk dialog "+ Tambah Pesanan" pada
    // nota yang sudah berjalan (mis. meja minta tambah 1 porsi lagi).
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // Event sekali-jalan (bukan state) yang dipancarkan tiap kali ada pesanan
    // baru masuk ke antrian — dipakai UI utk menampilkan banner + memicu
    // notifikasi sistem Android, terpisah dari bunyi ringtone yang sudah ada.
    private val _newOrderEvent = MutableSharedFlow<TableOrder>(extraBufferCapacity = 4)
    val newOrderEvent: SharedFlow<TableOrder> = _newOrderEvent.asSharedFlow()

    private var previousOrderIds = setOf<String>()
    private var isFirstLoad = true
    private var queueJob: Job? = null
    private var cancelledJob: Job? = null

    fun setUser(user: User) {
        if (_currentUser.value?.id != user.id) {
            _currentUser.value = user
            _operatingBranchId.value = user.assignedBranchId
            observeKitchenQueue(user, user.assignedBranchId)
            observeCancelledOrders(user.businessId)
            loadCatalog(user.businessId)

            val unlockedRoles = listOf("owner", "admin", "manager")
            if (user.role.lowercase() in unlockedRoles) {
                viewModelScope.launch {
                    _availableBranches.value = repository.getActiveBranches(user.businessId)
                }
            }
        }
    }

    private fun loadCatalog(businessId: String) {
        viewModelScope.launch {
            repository.observeProducts(businessId).collect { _products.value = it }
        }
        viewModelScope.launch {
            repository.observeCategories(businessId).collect { _categories.value = it }
        }
    }

    /** Ganti cabang yang antriannya sedang dipantau (Branch Switcher, role tidak terkunci saja). */
    fun setOperatingBranch(branchId: String?) {
        val user = _currentUser.value ?: return
        _operatingBranchId.value = branchId
        isFirstLoad = true // reset supaya ganti cabang tidak dianggap "order baru masuk" & bunyi notifikasi
        previousOrderIds = emptySet()
        observeKitchenQueue(user, branchId)
    }

    private fun observeKitchenQueue(user: User, branchId: String?) {
        // Hentikan listener cabang sebelumnya dulu supaya tidak menumpuk
        // listener Firestore tiap kali cabang diganti.
        queueJob?.cancel()
        queueJob = viewModelScope.launch {
            _isLoading.value = true
            repository.observeKitchenQueue(user, branchId).collect { list ->
                _isLoading.value = false
                val currentIds = list.map { it.id }.toSet()
                if (!isFirstLoad) {
                    val newIds = currentIds - previousOrderIds
                    if (newIds.isNotEmpty()) {
                        playNotificationSound()
                        list.firstOrNull { it.id in newIds }?.let { newOrder ->
                            _newOrderEvent.tryEmit(newOrder)
                        }
                    }
                }
                isFirstLoad = false
                previousOrderIds = currentIds
                _orders.value = list
            }
        }
    }

    private fun observeCancelledOrders(businessId: String) {
        cancelledJob?.cancel()
        cancelledJob = viewModelScope.launch {
            repository.observeCancelledTableOrders(businessId).collect {
                _cancelledOrders.value = it
            }
        }
    }

    private fun playNotificationSound() {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(getApplication(), notificationUri)
            ringtone.play()
        } catch (e: Exception) {
            // Ignore if notification audio is silenced
        }
    }

    fun toggleItemStatus(order: TableOrder, itemIndex: Int) {
        viewModelScope.launch {
            val result = repository.markKitchenItemDone(order, itemIndex)
            result.onSuccess { allDone ->
                if (allDone) {
                    val label = order.tableName.ifBlank { order.customerName.ifBlank { "#${order.id.takeLast(4)}" } }
                    Toast.makeText(getApplication(), "Pesanan $label Selesai Dimasak!", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                Toast.makeText(getApplication(), "Gagal update item: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Batalkan 1 pesanan (mis. pelanggan berubah pikiran / salah input meja). */
    fun cancelOrder(order: TableOrder, reason: String?) {
        viewModelScope.launch {
            val result = repository.cancelTableOrder(order.id, reason)
            result.onSuccess {
                Toast.makeText(getApplication(), "Pesanan dibatalkan.", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(getApplication(), "Gagal membatalkan: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Aktifkan kembali pesanan yang tadi dibatalkan (salah batal). */
    fun reactivateOrder(order: TableOrder) {
        viewModelScope.launch {
            val result = repository.reactivateTableOrder(order.id)
            result.onFailure { err ->
                Toast.makeText(getApplication(), "Gagal mengaktifkan ulang: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Tambah item baru ke pesanan yang sedang berjalan (nota sama, dikirim ulang ke dapur). */
    fun addItemsToOrder(order: TableOrder, newItems: List<TableOrderItem>) {
        if (newItems.isEmpty()) return
        viewModelScope.launch {
            val result = repository.addItemsToTableOrder(order, newItems)
            result.onSuccess {
                _actionError.value = null
                Toast.makeText(getApplication(), "Item baru ditambahkan ke pesanan.", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                _actionError.value = err.message ?: "Gagal menambah item."
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }
}
