package com.example.ui.cashier

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Business
import com.example.data.model.ReceiptSettings
import com.example.data.model.TableOrder
import com.example.data.model.User
import com.example.data.repository.UsahakiRepository
import com.example.ui.pos.ProofUploadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 1 tagihan = 1 meja (atau 1 pesanan tanpa-meja/bungkus, yang selalu jadi
 * tagihan sendiri-sendiri). Meniru persis logika pengelompokan `bills` di
 * CashierTab.tsx pada website.
 */
data class TableBill(
    val billKey: String,
    val tableId: String,
    val displayName: String,
    val customerNames: List<String>,
    val orders: List<TableOrder>,
    val total: Double,
    val allCompleted: Boolean,
    val isSeparateBill: Boolean
)

sealed interface PaymentState {
    object Idle : PaymentState
    object Processing : PaymentState
    object Success : PaymentState
    data class Error(val message: String) : PaymentState
}

class CashierViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsahakiRepository(application)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Nama bisnis asli + kustomisasi struk (Pengaturan > Struk website) —
    // SEBELUMNYA struk yang dicetak dari halaman Kasir mobile ini selalu
    // pakai nama bisnis hardcode "USAHAKI POS", tidak pernah baca data
    // sungguhan dari Firestore sama sekali.
    private val _business = MutableStateFlow<Business?>(null)
    val business: StateFlow<Business?> = _business.asStateFlow()

    private val _receiptSettings = MutableStateFlow<ReceiptSettings?>(null)
    val receiptSettings: StateFlow<ReceiptSettings?> = _receiptSettings.asStateFlow()

    private val _unpaidOrders = MutableStateFlow<List<TableOrder>>(emptyList())
    private val _paidOrders = MutableStateFlow<List<TableOrder>>(emptyList())
    val paidOrders: StateFlow<List<TableOrder>> = _paidOrders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _proofUploadState = MutableStateFlow<ProofUploadState>(ProofUploadState.Idle)
    val proofUploadState: StateFlow<ProofUploadState> = _proofUploadState.asStateFlow()

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    // Tagihan per meja/nota — dihitung ulang otomatis tiap kali daftar
    // pesanan belum-bayar berubah (real-time, sama seperti website).
    val bills: StateFlow<List<TableBill>> = _unpaidOrders
        .map { groupIntoBills(it) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    fun setUser(user: User) {
        if (_currentUser.value?.id != user.id) {
            _currentUser.value = user
            viewModelScope.launch {
                _isLoading.value = true
                repository.observeUnpaidTableOrders(user.businessId).collect { list ->
                    _isLoading.value = false
                    _unpaidOrders.value = list
                }
            }
            viewModelScope.launch {
                repository.observePaidTableOrders(user.businessId).collect { list ->
                    _paidOrders.value = list
                }
            }
            viewModelScope.launch {
                _business.value = repository.fetchBusiness(user.businessId)
            }
            viewModelScope.launch {
                _receiptSettings.value = repository.fetchReceiptSettings(user.businessId)
            }
        }
    }

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

    fun resetPaymentState() {
        _paymentState.value = PaymentState.Idle
    }

    fun payBill(bill: TableBill, paymentMethod: String, paymentProofUrl: String?) {
        val user = _currentUser.value ?: return
        if (paymentMethod != "cash" && paymentProofUrl.isNullOrEmpty()) {
            _paymentState.value = PaymentState.Error("Unggah bukti pembayaran terlebih dahulu untuk metode ${paymentMethod.uppercase()}.")
            return
        }
        _paymentState.value = PaymentState.Processing
        viewModelScope.launch {
            val result = repository.markTablePaid(
                businessId = user.businessId,
                tableId = bill.tableId,
                ordersToPay = bill.orders,
                operatingBranchId = user.assignedBranchId,
                paymentMethod = paymentMethod,
                cashierName = user.name.ifBlank { user.email },
                paymentProofUrl = paymentProofUrl
            )
            result.onSuccess {
                _paymentState.value = PaymentState.Success
                _proofUploadState.value = ProofUploadState.Idle
            }.onFailure { err ->
                _paymentState.value = PaymentState.Error(err.localizedMessage ?: "Gagal memproses pembayaran.")
            }
        }
    }
}

private fun groupIntoBills(unpaidOrders: List<TableOrder>): List<TableBill> {
    val map = LinkedHashMap<String, TableBill>()
    unpaidOrders.forEach { order ->
        // Pesanan tanpa meja (Tanpa Meja/panggil nama) & Bungkus selalu jadi
        // tagihan terpisah per pesanan — tidak digabung hanya karena berbagi
        // tableId yang sama ('no_table'/'unassigned'), sama seperti website.
        val isSeparateBill = order.tableId == "no_table" ||
            order.tableId == "unassigned" ||
            order.orderType == "takeaway" ||
            order.isCallByName == true
        val key = if (isSeparateBill) order.id else order.tableId

        val existing = map[key]
        if (existing != null) {
            val names = if (order.customerName in existing.customerNames) {
                existing.customerNames
            } else {
                existing.customerNames + order.customerName
            }
            map[key] = existing.copy(
                customerNames = names,
                orders = existing.orders + order,
                total = existing.total + order.totalAmount,
                allCompleted = existing.allCompleted && order.status == "completed"
            )
        } else {
            val displayName = if (isSeparateBill) {
                "Tanpa Meja (${order.orderCode ?: "#${order.queueNumber}"})"
            } else {
                order.tableName.ifBlank { "Meja" }
            }
            map[key] = TableBill(
                billKey = key,
                tableId = key,
                displayName = displayName,
                customerNames = listOf(order.customerName),
                orders = listOf(order),
                total = order.totalAmount,
                allCompleted = order.status == "completed",
                isSeparateBill = isSeparateBill
            )
        }
    }
    return map.values.sortedBy { it.displayName }
}
