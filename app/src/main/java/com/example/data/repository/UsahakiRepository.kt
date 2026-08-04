package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.Business
import com.example.data.model.Branch
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.DiningTable
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.Product
import com.example.data.model.TableOrder
import com.example.data.model.TableOrderItem
import com.example.data.model.Transaction
import com.example.data.model.User
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

class UsahakiRepository(context: Context) {

    companion object {
        private const val TAG = "UsahakiRepository"
        private const val NAMED_DATABASE_ID = "ai-studio-b778b7d5-6121-4ebb-b57f-d9f58c40eac9"
    }

    private val firebaseApp: FirebaseApp by lazy {
        val apps = FirebaseApp.getApps(context)
        if (apps.isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyCNskqfZiu-bjTMvCTFZCVWcaYaQ1p_Frc")
                .setApplicationId("1:937792400060:web:245a2dc97d4d1a3f773eb9")
                .setProjectId("gen-lang-client-0526063046")
                .setGcmSenderId("937792400060")
                .setStorageBucket("gen-lang-client-0526063046.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(context, options)
        } else {
            FirebaseApp.getInstance()
        }
    }

    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance(firebaseApp)
    }

    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(firebaseApp, NAMED_DATABASE_ID)
    }

    val storage: FirebaseStorage by lazy {
        FirebaseStorage.getInstance(firebaseApp)
    }

    // Helper to format ISO 8601 timestamp
    private fun currentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    // -------------------------------------------------------------
    // Auth & User Profile
    // -------------------------------------------------------------

    suspend fun fetchUserProfile(userId: String): User? {
        return try {
            val doc = db.collection("users").document(userId).get().await()
            if (doc.exists()) {
                val data = doc.data ?: return null
                User(
                    id = doc.id,
                    name = data["name"] as? String ?: "",
                    email = data["email"] as? String ?: "",
                    businessId = data["businessId"] as? String ?: "",
                    role = data["role"] as? String ?: "",
                    status = data["status"] as? String ?: "active",
                    assignedBranchId = data["assignedBranchId"] as? String
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile", e)
            null
        }
    }

    suspend fun fetchBusiness(businessId: String): Business? {
        if (businessId.isEmpty()) return null
        return try {
            val doc = db.collection("businesses").document(businessId).get().await()
            if (doc.exists()) {
                doc.toObject(Business::class.java)?.copy(id = doc.id)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching business", e)
            null
        }
    }

    // -------------------------------------------------------------
    // Products & Categories Live Stream
    // -------------------------------------------------------------

    fun observeProducts(businessId: String): Flow<List<Product>> = callbackFlow {
        if (businessId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("products")
            .whereEqualTo("businessId", businessId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing products", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val products = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Product::class.java)?.copy(id = doc.id)
                    }.filter { it.isActive }
                    trySend(products)
                }
            }

        awaitClose { listener.remove() }
    }

    fun observeCategories(businessId: String): Flow<List<Category>> = callbackFlow {
        if (businessId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("categories")
            .whereEqualTo("businessId", businessId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing categories", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val categories = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Category::class.java)?.copy(id = doc.id)
                    }
                    trySend(categories)
                }
            }

        awaitClose { listener.remove() }
    }

    // -------------------------------------------------------------
    // Dining Tables (Meja) — sama seperti koleksi "tables" di website
    // -------------------------------------------------------------

    /**
     * Ambil daftar meja aktif milik bisnis, opsional difilter per cabang
     * (sama seperti dineInService.getTables di website). Dipakai untuk
     * pilihan "Meja" saat checkout dine-in di POS mobile.
     */
    suspend fun getTables(businessId: String, branchId: String?): List<DiningTable> {
        if (businessId.isEmpty()) return emptyList()
        return try {
            var query: Query = db.collection("tables").whereEqualTo("businessId", businessId)
            if (!branchId.isNullOrEmpty()) {
                query = query.whereEqualTo("branchId", branchId)
            }
            query.get().await().documents.mapNotNull { doc ->
                doc.toObject(DiningTable::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tables", e)
            emptyList()
        }
    }

    // -------------------------------------------------------------
    // Upload Bukti Pembayaran (Transfer / QRIS)
    // -------------------------------------------------------------

    /**
     * Unggah foto bukti bayar ke Firebase Storage, path sama persis dengan
     * konvensi website (`users/{uid}/uploads/payment-proofs/...`, lihat
     * storage.rules) supaya lolos Storage Security Rules yang mensyaratkan
     * request.auth.uid == segmen {userId} di path. Mengembalikan download URL
     * yang disimpan ke field `paymentProofUrl` pada Order/TableOrder.
     */
    suspend fun uploadPaymentProof(fileUri: Uri): Result<String> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("Sesi login tidak ditemukan. Silakan login ulang."))
            val fileName = "${System.currentTimeMillis()}-bukti-bayar.jpg"
            val ref = storage.reference.child("users/$uid/uploads/payment-proofs/$fileName")
            ref.putFile(fileUri).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading payment proof", e)
            Result.failure(e)
        }
    }

    /**
     * Simpan pesanan ke antrian dapur TANPA memproses pembayaran — sama
     * seperti tombol "Simpan" di SaveToKitchenModal pada website. Stok TIDAK
     * dikurangi di sini (baru dikurangi saat benar-benar dibayar), dan tidak
     * ada dokumen "orders"/"transactions" yang dibuat sampai kasir memproses
     * pembayarannya lewat markTablePaid().
     */
    suspend fun saveOrderToKitchenQueue(
        user: User,
        cartItems: List<CartItem>,
        operatingBranchId: String?,
        orderType: String,
        selectedTable: DiningTable?,
        customerName: String
    ): Result<TableOrder> {
        return try {
            val tableOrderRef = db.collection("tableorders").document()
            val nowStr = currentIsoTimestamp()
            val totalAmount = cartItems.sumOf { it.totalPrice }
            val queueNumber = ((System.currentTimeMillis() % 86400000L) / 10000L % 900L + 100L).toInt()
            val orderCode = "MQ-${Random.nextInt(1000, 10000)}"

            val tableItems = cartItems.map { cart ->
                TableOrderItem(
                    productId = cart.product.id,
                    name = cart.product.name,
                    price = cart.unitPrice,
                    qty = cart.qty,
                    variant = cart.selectedVariant?.name,
                    status = "pending"
                )
            }

            val newTableOrder = TableOrder(
                id = tableOrderRef.id,
                businessId = user.businessId,
                branchId = selectedTable?.branchId ?: operatingBranchId,
                tableId = selectedTable?.id ?: "unassigned",
                tableName = selectedTable?.name ?: customerName,
                customerName = customerName,
                items = tableItems,
                totalAmount = totalAmount,
                queueNumber = queueNumber,
                orderCode = orderCode,
                orderType = orderType,
                isCallByName = orderType == "no_table",
                status = "pending",
                paymentStatus = "unpaid",
                createdAt = nowStr
            )

            val batch = db.batch()
            batch.set(tableOrderRef, newTableOrder)
            if (selectedTable != null) {
                val tableRef = db.collection("tables").document(selectedTable.id)
                batch.update(tableRef, "status", "occupied")
            }
            batch.commit().await()

            Result.success(newTableOrder)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving order to kitchen queue", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // POS Checkout with Batch Write (Section 7 & 9)
    // -------------------------------------------------------------

    /**
     * Ambil daftar cabang AKTIF milik bisnis — dipakai Branch Switcher mobile
     * untuk role yang TIDAK terkunci ke 1 cabang (owner/admin/manager).
     */
    suspend fun getActiveBranches(businessId: String): List<Branch> {
        return try {
            db.collection("branches")
                .whereEqualTo("businessId", businessId)
                .whereEqualTo("isActive", true)
                .get()
                .await()
                .documents.mapNotNull { it.toObject(Branch::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching branches", e)
            emptyList()
        }
    }

    suspend fun processCheckout(
        user: User,
        cartItems: List<CartItem>,
        paymentMethod: String,
        totalAmount: Double,
        // Cabang TEMPAT transaksi ini terjadi. Untuk role terkunci
        // (kasir/kitchen/manager_cabang) ini SELALU sama dengan
        // user.assignedBranchId (parameter default). Untuk role TIDAK
        // terkunci (owner/admin/manager) ini datang dari pilihan Branch
        // Switcher mobile — null berarti "Pusat", BUKAN "semua cabang"
        // (transaksi harus tercatat di 1 lokasi spesifik).
        operatingBranchId: String? = user.assignedBranchId,
        // Jenis pesanan F&B, sama seperti "Simpan Ke Antrian Dapur" di
        // website: "dine_in" (meja), "no_table" (tanpa meja/panggil nama),
        // "takeaway" (bungkus). Null untuk bisnis retail biasa.
        orderType: String? = null,
        selectedTable: DiningTable? = null,
        customerName: String = "Pelanggan POS",
        // Wajib diisi (URL Storage) kalau paymentMethod = "transfer" / "qris",
        // sama seperti wajib upload PaymentProofCapture di website.
        paymentProofUrl: String? = null
    ): Result<Order> {
        return try {
            val orderRef = db.collection("orders").document()
            val transactionRef = db.collection("transactions").document()
            val nowStr = currentIsoTimestamp()

            val orderItems = cartItems.map { cart ->
                OrderItem(
                    productId = cart.product.id,
                    name = cart.product.name,
                    qty = cart.qty,
                    price = cart.unitPrice,
                    variant = cart.selectedVariant?.name
                )
            }

            val newOrder = Order(
                id = orderRef.id,
                businessId = user.businessId,
                branchId = operatingBranchId,
                source = "pos",
                items = orderItems,
                totalAmount = totalAmount,
                status = "completed",
                paymentStatus = "paid",
                paymentMethod = paymentMethod,
                paymentProofUrl = paymentProofUrl,
                orderType = orderType,
                tableId = selectedTable?.id,
                tableName = selectedTable?.name,
                createdAt = nowStr
            )

            val newTransaction = Transaction(
                id = transactionRef.id,
                businessId = user.businessId,
                branchId = operatingBranchId,
                orderId = orderRef.id,
                type = "income",
                category = "penjualan",
                amount = totalAmount,
                method = paymentMethod,
                createdBy = user.id,
                createdAt = nowStr
            )

            val batch = db.batch()
            batch.set(orderRef, newOrder)
            batch.set(transactionRef, newTransaction)

            // Stock reduction logic per branch as mandated in Section 7
            for (cart in cartItems) {
                val prodRef = db.collection("products").document(cart.product.id)

                if (!operatingBranchId.isNullOrEmpty()) {
                    // Update stockByBranch.<branchId> using dot path
                    val currentBranchStock = cart.product.getEffectiveStock(operatingBranchId, cart.selectedVariant)
                    val newStock = maxOf(0, currentBranchStock - cart.qty)
                    batch.update(prodRef, "stockByBranch.$operatingBranchId", newStock)
                } else {
                    // Central stock update
                    val currentStock = cart.product.stock
                    val newStock = maxOf(0, currentStock - cart.qty)
                    batch.update(prodRef, "stock", newStock)
                }
            }

            // Untuk pesanan F&B (meja/tanpa meja/bungkus), kirim juga ke
            // antrian dapur ("tableorders") — sama seperti "Simpan Ke
            // Antrian Dapur" di website — supaya layar Dapur & Antrian
            // Publik tetap melihat pesanan ini, dan meja yang dipilih
            // otomatis ditandai "occupied".
            var tableOrderRef: com.google.firebase.firestore.DocumentReference? = null
            if (orderType != null) {
                tableOrderRef = db.collection("tableorders").document()
                val queueNumber = ((System.currentTimeMillis() % 86400000L) / 10000L % 900L + 100L).toInt()
                val orderCode = "MQ-${Random.nextInt(1000, 10000)}"
                val tableItems = cartItems.map { cart ->
                    com.example.data.model.TableOrderItem(
                        productId = cart.product.id,
                        name = cart.product.name,
                        price = cart.unitPrice,
                        qty = cart.qty,
                        variant = cart.selectedVariant?.name,
                        status = "done" // Sudah dibayar & selesai langsung di kasir mobile.
                    )
                }
                val newTableOrder = TableOrder(
                    id = tableOrderRef.id,
                    businessId = user.businessId,
                    branchId = selectedTable?.branchId ?: operatingBranchId,
                    tableId = selectedTable?.id ?: "unassigned",
                    tableName = selectedTable?.name ?: customerName,
                    customerName = customerName,
                    items = tableItems,
                    totalAmount = totalAmount,
                    queueNumber = queueNumber,
                    orderCode = orderCode,
                    orderType = orderType,
                    paymentMethod = paymentMethod,
                    paymentProofUrl = paymentProofUrl,
                    status = "completed",
                    paymentStatus = "paid",
                    createdAt = nowStr
                )
                batch.set(tableOrderRef, newTableOrder)

                if (selectedTable != null) {
                    val tableRef = db.collection("tables").document(selectedTable.id)
                    batch.update(tableRef, "status", "occupied")
                }
            }

            batch.commit().await()
            Result.success(newOrder)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing checkout batch", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // Order History Stream
    // -------------------------------------------------------------

    fun observePastOrders(user: User, operatingBranchId: String? = user.assignedBranchId): Flow<List<Order>> = callbackFlow {
        if (user.businessId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var query: Query = db.collection("orders")
            .whereEqualTo("businessId", user.businessId)

        if (!operatingBranchId.isNullOrEmpty()) {
            query = query.whereEqualTo("branchId", operatingBranchId)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error observing past orders", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val orders = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.copy(id = doc.id)
                }.sortedByDescending { it.createdAt }
                trySend(orders)
            }
        }

        awaitClose { listener.remove() }
    }

    // -------------------------------------------------------------
    // Kitchen Real-time Stream & Controls (Section 8)
    // -------------------------------------------------------------

    fun observeKitchenQueue(user: User, operatingBranchId: String? = user.assignedBranchId): Flow<List<TableOrder>> = callbackFlow {
        if (user.businessId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Website sekarang SUDAH mendukung branchId di `tables`/`tableorders`
        // (sebelumnya belum — lihat riwayat perbaikan). operatingBranchId
        // null berarti "Semua Cabang" (agregat, sama seperti Branch Switcher
        // di website saat "Semua Cabang" dipilih) — TIDAK difilter branchId
        // sama sekali, supaya owner/admin/manager yang belum pilih cabang
        // spesifik tetap melihat SEMUA pesanan, bukan nol.
        var query: Query = db.collection("tableorders")
            .whereEqualTo("businessId", user.businessId)
            .whereIn("status", listOf("pending", "preparing"))
        if (!operatingBranchId.isNullOrEmpty()) {
            query = query.whereEqualTo("branchId", operatingBranchId)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error observing kitchen queue", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val orders = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(TableOrder::class.java)?.copy(id = doc.id)
                }.sortedBy { it.createdAt }
                trySend(orders)
            }
        }

        awaitClose { listener.remove() }
    }

    suspend fun markKitchenItemDone(order: TableOrder, itemIndex: Int): Result<Boolean> {
        return try {
            val updatedItems = order.items.toMutableList()
            if (itemIndex in updatedItems.indices) {
                val currentItem = updatedItems[itemIndex]
                val newStatus = if (currentItem.status == "done") "pending" else "done"
                updatedItems[itemIndex] = currentItem.copy(status = newStatus)
            }

            // Check if all non-self-service items (or all items) are done
            val allDone = updatedItems.all { it.status == "done" || it.isSelfService == true }
            val newOrderStatus = if (allDone) "completed" else "preparing"

            val updateData = mapOf(
                "items" to updatedItems,
                "status" to newOrderStatus
            )

            db.collection("tableorders").document(order.id)
                .update(updateData)
                .await()

            Result.success(allDone)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating kitchen item status", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // Batalkan / Tambah Item Pesanan Dapur (sama seperti QueueTab &
    // CashierTab di website — dineInService.cancelTableOrder &
    // addItemsToExistingOrder)
    // -------------------------------------------------------------

    suspend fun cancelTableOrder(orderId: String, reason: String? = null): Result<Unit> {
        return try {
            db.collection("tableorders").document(orderId).update(
                mapOf(
                    "status" to "cancelled",
                    "cancelReason" to (reason ?: "Dibatalkan oleh dapur/kasir"),
                    "completedAt" to currentIsoTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling table order", e)
            Result.failure(e)
        }
    }

    suspend fun reactivateTableOrder(orderId: String): Result<Unit> {
        return try {
            db.collection("tableorders").document(orderId).update(
                mapOf(
                    "status" to "pending",
                    "cancelReason" to null,
                    "completedAt" to null
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error reactivating table order", e)
            Result.failure(e)
        }
    }

    /**
     * Tambah item baru ke pesanan yang sudah ada di antrian dapur (mis. meja
     * minta tambah 1 porsi lagi). Meniru persis logika
     * dineInService.addItemsToExistingOrder di website: total & status pesanan
     * dihitung ulang, dan pesanan otomatis kembali ke antrian dapur
     * ("pending"/"preparing") kalau ada item baru yang belum selesai — kecuali
     * kalau pesanan sudah lunas dibayar, itemnya tidak boleh diubah lagi.
     */
    suspend fun addItemsToTableOrder(order: TableOrder, newItems: List<TableOrderItem>): Result<Unit> {
        if (newItems.isEmpty()) return Result.failure(IllegalArgumentException("Minimal 1 item harus ditambahkan."))
        if (order.paymentStatus == "paid") {
            return Result.failure(IllegalStateException("Pesanan sudah dibayar lunas, tidak bisa menambah item lagi."))
        }
        if (order.status == "cancelled") {
            return Result.failure(IllegalStateException("Pesanan sudah dibatalkan, tidak bisa menambah item."))
        }
        return try {
            val formattedNewItems = newItems.map { item ->
                item.copy(status = if (item.isSelfService == true) "done" else "pending")
            }
            val allItems = order.items + formattedNewItems
            val newTotal = allItems.sumOf { it.price * it.qty }
            val allDone = allItems.all { it.status == "done" }
            val anyDone = allItems.any { it.status == "done" }
            val newStatus = if (allDone) "completed" else if (anyDone) "preparing" else "pending"

            db.collection("tableorders").document(order.id).update(
                mapOf(
                    "items" to allItems,
                    "totalAmount" to newTotal,
                    "status" to newStatus,
                    "completedAt" to if (newStatus == "completed") currentIsoTimestamp() else null
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding items to table order", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // Kasir — Daftar Tagihan Meja (Belum Dibayar / Sudah Dibayar), sama
    // seperti CashierTab di website (dineInService.subscribeToUnpaidOrders /
    // subscribeToPaidOrders). Query business-wide (tanpa filter cabang) sama
    // persis seperti website, supaya kasir tetap bisa memproses pembayaran
    // dari cabang manapun kalau memang tidak dipisah per cabang di sana.
    // -------------------------------------------------------------

    fun observeUnpaidTableOrders(businessId: String): Flow<List<TableOrder>> = callbackFlow {
        if (businessId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("tableorders")
            .whereEqualTo("businessId", businessId)
            .whereEqualTo("paymentStatus", "unpaid")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing unpaid table orders", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val orders = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(TableOrder::class.java)?.copy(id = doc.id)
                    }.filter { it.status != "cancelled" } // Pesanan dibatalkan tidak perlu ditagih.
                    trySend(orders)
                }
            }
        awaitClose { listener.remove() }
    }

    /** Riwayat pesanan yang dibatalkan — sama seperti tab "Dibatalkan" di QueueTab website. */
    fun observeCancelledTableOrders(businessId: String): Flow<List<TableOrder>> = callbackFlow {
        if (businessId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("tableorders")
            .whereEqualTo("businessId", businessId)
            .whereEqualTo("status", "cancelled")
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing cancelled table orders", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val orders = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(TableOrder::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.createdAt }
                    trySend(orders)
                }
            }
        awaitClose { listener.remove() }
    }

    fun observePaidTableOrders(businessId: String): Flow<List<TableOrder>> = callbackFlow {        if (businessId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("tableorders")
            .whereEqualTo("businessId", businessId)
            .whereEqualTo("paymentStatus", "paid")
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing paid table orders", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val orders = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(TableOrder::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.createdAt }
                    trySend(orders)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Proses pembayaran 1 tagihan (1 meja, bisa berisi beberapa TableOrder
     * kalau digabung) — meniru persis dineInService.markTablePaid: tandai
     * semua tableorder terkait lunas, catat ke "transactions", cerminkan ke
     * "orders" (supaya laporan penjualan POS tetap lengkap), lalu bebaskan
     * mejanya.
     */
    suspend fun markTablePaid(
        businessId: String,
        tableId: String,
        ordersToPay: List<TableOrder>,
        operatingBranchId: String?,
        paymentMethod: String,
        cashierName: String,
        paymentProofUrl: String?
    ): Result<Unit> {
        return try {
            val batch = db.batch()
            val nowStr = currentIsoTimestamp()
            val normalizedMethod = if (paymentMethod == "qris") "transfer" else paymentMethod

            ordersToPay.forEach { order ->
                val orderRef = db.collection("tableorders").document(order.id)
                val updateMap = mutableMapOf<String, Any?>(
                    "paymentStatus" to "paid",
                    "paymentMethod" to normalizedMethod
                )
                if (!paymentProofUrl.isNullOrEmpty()) updateMap["paymentProofUrl"] = paymentProofUrl
                batch.update(orderRef, updateMap)

                val txRef = db.collection("transactions").document()
                val newTransaction = Transaction(
                    id = txRef.id,
                    businessId = businessId,
                    branchId = order.branchId ?: operatingBranchId,
                    orderId = order.id,
                    type = "income",
                    category = "penjualan",
                    amount = order.totalAmount,
                    method = normalizedMethod,
                    createdBy = cashierName,
                    createdAt = nowStr
                )
                batch.set(txRef, newTransaction)

                val posOrderRef = db.collection("orders").document(order.id)
                val posOrder = Order(
                    id = order.id,
                    businessId = businessId,
                    branchId = order.branchId ?: operatingBranchId,
                    source = "pos",
                    items = order.items.map { item ->
                        OrderItem(
                            productId = item.productId,
                            name = item.name,
                            qty = item.qty,
                            price = item.price,
                            variant = item.variant
                        )
                    },
                    totalAmount = order.totalAmount,
                    status = "completed",
                    paymentStatus = "paid",
                    paymentMethod = normalizedMethod,
                    paymentProofUrl = paymentProofUrl,
                    orderType = order.orderType,
                    tableId = order.tableId,
                    tableName = order.tableName,
                    createdAt = nowStr
                )
                batch.set(posOrderRef, posOrder)
            }

            if (tableId.isNotEmpty() && tableId != "no_table" && !tableId.startsWith("MQ-") && tableId != "unassigned") {
                val tableRef = db.collection("tables").document(tableId)
                batch.update(tableRef, "status", "available")
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking table paid", e)
            Result.failure(e)
        }
    }
}

