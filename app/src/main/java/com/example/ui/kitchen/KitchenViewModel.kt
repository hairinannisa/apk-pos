package com.example.ui.kitchen

import android.app.Application
import android.media.RingtoneManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Branch
import com.example.data.model.TableOrder
import com.example.data.model.User
import com.example.data.repository.UsahakiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var previousOrderCount = -1
    private var queueJob: Job? = null

    fun setUser(user: User) {
        if (_currentUser.value?.id != user.id) {
            _currentUser.value = user
            _operatingBranchId.value = user.assignedBranchId
            observeKitchenQueue(user, user.assignedBranchId)

            val unlockedRoles = listOf("owner", "admin", "manager")
            if (user.role.lowercase() in unlockedRoles) {
                viewModelScope.launch {
                    _availableBranches.value = repository.getActiveBranches(user.businessId)
                }
            }
        }
    }

    /** Ganti cabang yang antriannya sedang dipantau (Branch Switcher, role tidak terkunci saja). */
    fun setOperatingBranch(branchId: String?) {
        val user = _currentUser.value ?: return
        _operatingBranchId.value = branchId
        previousOrderCount = -1 // reset supaya ganti cabang tidak dianggap "order baru masuk" & bunyi notifikasi
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
                if (previousOrderCount != -1 && list.size > previousOrderCount) {
                    playNotificationSound()
                }
                previousOrderCount = list.size
                _orders.value = list
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
}
