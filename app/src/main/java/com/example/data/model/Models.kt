package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val businessId: String = "",
    val role: String = "", // "kasir" | "kitchen" | "owner" | "admin" | "manager_cabang"
    val status: String = "active",
    val assignedBranchId: String? = null
)

@IgnoreExtraProperties
data class Business(
    val id: String = "",
    val name: String = "",
    val subdomain: String = "",
    val plan: String = "starter",
    val transactionMode: String? = "retail", // "retail" or "fnb"
    val logoUrl: String? = null
)

@IgnoreExtraProperties
data class Branch(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val code: String = "",
    val isActive: Boolean = true
)

@IgnoreExtraProperties
data class ProductVariant(
    val name: String = "",
    val price: Double = 0.0,
    val stock: Int = 0,
    val stockByBranch: Map<String, Int>? = null,
    val sku: String? = null
) {
    fun getEffectiveStock(branchId: String?): Int {
        if (branchId.isNullOrEmpty()) return stock
        val branchMap = stockByBranch
        if (branchMap == null) return stock
        return branchMap[branchId] ?: 0
    }
}

@IgnoreExtraProperties
data class Product(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val categoryId: String = "",
    val price: Double = 0.0,
    val sku: String? = null,
    val variants: List<ProductVariant> = emptyList(),
    // Sama seperti field `images` di website (src/types/product.types.ts) —
    // list URL Firebase Storage. Kita pakai gambar pertama sebagai thumbnail
    // di grid POS. Sebelumnya field ini tidak ada di model Android sama
    // sekali sehingga Firestore tidak pernah mengisinya dan gambar produk
    // tidak pernah muncul di APK walau sudah diunggah lewat website.
    val images: List<String> = emptyList(),
    val stock: Int = 0,
    val stockByBranch: Map<String, Int>? = null,
    val isActive: Boolean = true
) {
    /** URL gambar utama produk (gambar pertama), atau null kalau belum ada gambar diunggah. */
    val primaryImageUrl: String?
        get() = images.firstOrNull()

    fun getEffectiveStock(branchId: String?, selectedVariant: ProductVariant? = null): Int {
        if (selectedVariant != null) {
            return selectedVariant.getEffectiveStock(branchId)
        }
        if (branchId.isNullOrEmpty()) return stock
        val branchMap = stockByBranch
        if (branchMap == null) return stock
        return branchMap[branchId] ?: 0
    }
}

@IgnoreExtraProperties
data class Category(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val parentId: String? = null
)

@IgnoreExtraProperties
data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val qty: Int = 1,
    val price: Double = 0.0,
    val variant: String? = null
)

@IgnoreExtraProperties
data class Order(
    val id: String = "",
    val businessId: String = "",
    val branchId: String? = null,
    val customerId: String? = null,
    val source: String = "pos",
    val items: List<OrderItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val status: String = "completed",
    val paymentStatus: String = "paid",
    val paymentMethod: String = "cash", // "cash" | "transfer" | "qris"
    val paymentProofUrl: String? = null,
    // Jenis pesanan sama seperti TableOrderType di website: "dine_in" (meja),
    // "no_table" (tanpa meja / panggil nama), "takeaway" (bungkus). Null =
    // pesanan retail biasa (bukan bisnis F&B / tidak relevan).
    val orderType: String? = null,
    val tableId: String? = null,
    val tableName: String? = null,
    val createdAt: String = ""
)

@IgnoreExtraProperties
data class Transaction(
    val id: String = "",
    val businessId: String = "",
    val branchId: String? = null,
    val orderId: String? = null,
    val type: String = "income",
    val category: String = "penjualan",
    val amount: Double = 0.0,
    val method: String = "cash",
    val createdBy: String = "",
    val createdAt: String = ""
)

@IgnoreExtraProperties
data class DiningTable(
    val id: String = "",
    val businessId: String = "",
    val branchId: String? = null,
    val name: String = "", // Contoh: "Meja 1", "VIP 2"
    val code: String = "",
    val capacity: Int? = null,
    val status: String = "available" // "available" | "occupied"
)

@IgnoreExtraProperties
data class TableOrderItem(
    val productId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val qty: Int = 1,
    val variant: String? = null,
    val notes: String? = null,
    val status: String = "pending", // "pending" | "done"
    val isSelfService: Boolean? = false
)

@IgnoreExtraProperties
data class TableOrder(
    val id: String = "",
    val businessId: String = "",
    val branchId: String? = null, // Sekarang REAL — DiningTable & TableOrder di website sudah per-cabang.
    val tableId: String = "",
    val tableName: String = "",
    val customerName: String = "",
    val items: List<TableOrderItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val queueNumber: Int = 0,
    val orderCode: String? = null,
    val orderType: String? = "dine_in", // "dine_in" | "no_table" | "online" | "takeaway"
    val paxCount: Int? = null,
    val isCallByName: Boolean? = false,
    val paymentMethod: String? = "cash", // "cash" | "transfer" | "qris"
    val paymentProofUrl: String? = null,
    val customerPhone: String? = null,
    val cancelReason: String? = null,
    val status: String = "pending", // "pending" | "preparing" | "completed" | "cancelled"
    val paymentStatus: String = "unpaid",
    val createdAt: String = ""
)

data class CartItem(
    val product: Product,
    val selectedVariant: ProductVariant? = null,
    var qty: Int = 1,
    var notes: String? = null
) {
    val unitPrice: Double
        get() = selectedVariant?.price ?: product.price

    val totalPrice: Double
        get() = unitPrice * qty

    val displayName: String
        get() = if (selectedVariant != null) "${product.name} (${selectedVariant.name})" else product.name
}
