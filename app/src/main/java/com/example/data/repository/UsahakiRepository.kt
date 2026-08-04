package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.Business
import com.example.data.model.Branch
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.Product
import com.example.data.model.TableOrder
import com.example.data.model.Transaction
import com.example.data.model.User
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        operatingBranchId: String? = user.assignedBranchId
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
}
