package com.example.ui.kitchen

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.TableOrder
import com.example.data.model.TableOrderItem
import com.example.data.model.User
import com.example.ui.components.BranchBadge
import com.example.ui.components.RoleBadge
import com.example.ui.components.formatRupiah
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenAccentDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.MinimalBackground
import com.example.ui.theme.MinimalBorder
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary
import kotlinx.coroutines.delay

private const val KITCHEN_NOTIF_CHANNEL_ID = "kitchen_new_order"

private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(KITCHEN_NOTIF_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                KITCHEN_NOTIF_CHANNEL_ID,
                "Pesanan Dapur Baru",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat ada pesanan baru masuk ke antrian dapur."
            }
            manager.createNotificationChannel(channel)
        }
    }
}

private fun showNewOrderSystemNotification(context: Context, order: TableOrder) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return // Izin belum diberikan — cukup andalkan bunyi ringtone & banner dalam-aplikasi.
    }
    val label = order.tableName.ifBlank { order.customerName.ifBlank { "Pesanan Baru" } }
    val notification = NotificationCompat.Builder(context, KITCHEN_NOTIF_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("🍽️ Pesanan Baru Masuk!")
        .setContentText("$label — ${order.items.size} item, ${order.totalAmount.formatRupiah()}")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(order.id.hashCode(), notification)
    } catch (e: SecurityException) {
        // Izin dicabut di tengah jalan — abaikan saja, tidak fatal.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(
    user: User,
    kitchenViewModel: KitchenViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    kitchenViewModel.setUser(user)

    val orders by kitchenViewModel.orders.collectAsState()
    val cancelledOrders by kitchenViewModel.cancelledOrders.collectAsState()
    val products by kitchenViewModel.products.collectAsState()
    val categories by kitchenViewModel.categories.collectAsState()
    val isLoading by kitchenViewModel.isLoading.collectAsState()
    val actionError by kitchenViewModel.actionError.collectAsState()

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("active") }
    var cancelDialogOrder by remember { mutableStateOf<TableOrder?>(null) }
    var addItemDialogOrder by remember { mutableStateOf<TableOrder?>(null) }
    var bannerOrder by remember { mutableStateOf<TableOrder?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Hasil izin tidak perlu ditangani khusus — kalau ditolak, notifikasi sistem cuma tidak muncul. */ }

    LaunchedEffect(Unit) {
        ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Setiap ada pesanan baru: tampilkan banner di dalam app + notifikasi
    // sistem Android (ringtone bunyi otomatis dari KitchenViewModel).
    LaunchedEffect(Unit) {
        kitchenViewModel.newOrderEvent.collect { order ->
            bannerOrder = order
            showNewOrderSystemNotification(context, order)
            delay(4500)
            if (bannerOrder?.id == order.id) bannerOrder = null
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Antrian Dapur (Kitchen)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFFEA580C)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                RoleBadge(role = user.role)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            BranchBadge(assignedBranchId = user.assignedBranchId)
                        }
                    },
                    actions = {
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

                // Tab "Antrian Aktif" / "Dibatalkan" — sama seperti QueueTab di website.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MinimalBackground)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitchenTabChip(
                        label = "Antrian Aktif (${orders.size})",
                        isSelected = activeTab == "active",
                        onClick = { activeTab = "active" }
                    )
                    KitchenTabChip(
                        label = "Dibatalkan (${cancelledOrders.size})",
                        isSelected = activeTab == "cancelled",
                        onClick = { activeTab = "cancelled" }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MinimalBackground)
        ) {
            if (activeTab == "active") {
                if (isLoading && orders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFEA580C))
                    }
                } else if (orders.isEmpty()) {
                    EmptyKitchenState()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(orders, key = { it.id }) { tableOrder ->
                            KitchenOrderCardClean(
                                order = tableOrder,
                                onToggleItem = { itemIndex ->
                                    kitchenViewModel.toggleItemStatus(tableOrder, itemIndex)
                                },
                                onCancelOrder = { cancelDialogOrder = tableOrder },
                                onAddItems = { addItemDialogOrder = tableOrder }
                            )
                        }
                    }
                }
            } else {
                if (cancelledOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Belum ada pesanan yang dibatalkan.",
                            color = MinimalTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cancelledOrders, key = { it.id }) { order ->
                            CancelledOrderCard(
                                order = order,
                                onReactivate = { kitchenViewModel.reactivateOrder(order) }
                            )
                        }
                    }
                }
            }

            // Banner pesanan baru — muncul di atas layar selama beberapa detik.
            AnimatedVisibility(
                visible = bannerOrder != null,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                bannerOrder?.let { order ->
                    NewOrderBanner(
                        order = order,
                        onDismiss = { bannerOrder = null }
                    )
                }
            }
        }
    }

    cancelDialogOrder?.let { order ->
        CancelOrderDialog(
            order = order,
            onConfirm = { reason ->
                kitchenViewModel.cancelOrder(order, reason)
                cancelDialogOrder = null
            },
            onDismiss = { cancelDialogOrder = null }
        )
    }

    addItemDialogOrder?.let { order ->
        AddItemsDialog(
            order = order,
            products = products,
            categories = categories,
            errorMessage = actionError,
            onClearError = { kitchenViewModel.clearActionError() },
            onConfirm = { newItems ->
                kitchenViewModel.addItemsToOrder(order, newItems)
                addItemDialogOrder = null
            },
            onDismiss = {
                kitchenViewModel.clearActionError()
                addItemDialogOrder = null
            }
        )
    }
}

@Composable
private fun KitchenTabChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFEA580C) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFFEA580C) else MinimalBorder)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else MinimalTextSecondary
        )
    }
}

@Composable
private fun EmptyKitchenState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = null,
                tint = MinimalBorder,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tidak Ada Antrian Pesanan",
                color = MinimalTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Pesanan baru dari meja akan muncul di sini secara real-time.",
                color = MinimalTextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun NewOrderBanner(order: TableOrder, onDismiss: () -> Unit) {
    val label = order.tableName.ifBlank { order.customerName.ifBlank { "Pesanan Baru" } }
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable { onDismiss() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16A34A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Pesanan Baru Masuk!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("$label • ${order.items.size} item", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun KitchenOrderCardClean(
    order: TableOrder,
    onToggleItem: (Int) -> Unit,
    onCancelOrder: () -> Unit = {},
    onAddItems: () -> Unit = {}
) {
    val completedCount = order.items.count { it.status == "done" }
    val totalCount = order.items.size
    val isAllDone = totalCount > 0 && completedCount == totalCount

    val cardHeaderColor = when {
        isAllDone -> GreenPrimary
        order.status == "preparing" -> Color(0xFF0284C7)
        else -> Color(0xFFEA580C)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MinimalBorder, RoundedCornerShape(20.dp))
    ) {
        Column {
            // Header Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardHeaderColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            // Pakai tableName asli (mis. "Meja 1") kalau ada;
                            // fallback customerName utk pesanan tanpa meja
                            // (call-by-name/takeaway), baru fallback ID kalau
                            // dua-duanya kosong.
                            text = order.tableName.ifBlank {
                                order.customerName.ifBlank { "Pesanan #${order.id.takeLast(4).uppercase()}" }
                            }.uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (order.queueNumber > 0) "No. ${order.queueNumber}" else "ID: ${order.id.take(6)}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$completedCount/$totalCount Selesai",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Items Checklist
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                order.items.forEachIndexed { index, item ->
                    KitchenItemRowClean(
                        item = item,
                        onToggle = { onToggleItem(index) }
                    )
                }
            }

            // Aksi: Tambah Pesanan & Batalkan — sama seperti tombol di
            // QueueTab website (tambah item ke nota yang sama / batalkan nota).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onAddItems,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah Pesanan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                }
                OutlinedButton(
                    onClick = onCancelOrder,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Batalkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun KitchenItemRowClean(
    item: TableOrderItem,
    onToggle: () -> Unit
) {
    val isDone = item.status == "done"

    val cardBg by animateColorAsState(
        targetValue = if (isDone) Color(0xFFF0F4E9) else MinimalBackground
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MinimalBorder, RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isDone) GreenPrimary else MinimalTextSecondary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${item.qty}x ",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = if (isDone) MinimalTextSecondary else GreenPrimary
                        )
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isDone) MinimalTextSecondary else MinimalTextPrimary,
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }

                    if (!item.variant.isNullOrBlank()) {
                        Text(
                            text = "Varian: ${item.variant}",
                            fontSize = 12.sp,
                            color = GreenPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!item.notes.isNullOrBlank()) {
                        Text(
                            text = "Catatan: ${item.notes}",
                            fontSize = 12.sp,
                            color = Color(0xFFEA580C),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDone) GreenAccent else MinimalBorder)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isDone) "SELESAI" else "TANDAI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) GreenAccentDark else MinimalTextPrimary
                )
            }
        }
    }
}

@Composable
private fun CancelledOrderCard(order: TableOrder, onReactivate: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFFFD1D1), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.tableName.ifBlank { order.customerName },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MinimalTextPrimary
                )
                OutlinedButton(
                    onClick = onReactivate,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aktifkan Lagi", fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Alasan: ${order.cancelReason ?: "Tidak ada keterangan"}",
                fontSize = 12.sp,
                color = Color(0xFFDC2626)
            )
            Text(
                text = order.items.joinToString(", ") { "${it.qty}x ${it.name}" },
                fontSize = 11.sp,
                color = MinimalTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CancelOrderDialog(
    order: TableOrder,
    onConfirm: (reason: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Batalkan Pesanan?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MinimalTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${order.tableName.ifBlank { order.customerName }} — pesanan ini akan ditandai batal dan tidak perlu dibayar.",
                    fontSize = 12.sp,
                    color = MinimalTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal")
                    }
                    Button(
                        onClick = { onConfirm(reason.ifBlank { null }) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Ya, Batalkan")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddItemsDialog(
    order: TableOrder,
    products: List<Product>,
    categories: List<Category>,
    errorMessage: String?,
    onClearError: () -> Unit,
    onConfirm: (List<TableOrderItem>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // productId -> qty yang dipilih untuk ditambahkan.
    val selectedQty = remember { mutableStateOf(mapOf<String, Int>()) }

    val filteredProducts = products.filter {
        it.isActive && (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true))
    }

    val totalToAdd = selectedQty.value.entries.sumOf { (productId, qty) ->
        (products.firstOrNull { it.id == productId }?.price ?: 0.0) * qty
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tambah Pesanan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            order.tableName.ifBlank { order.customerName },
                            fontSize = 12.sp,
                            color = MinimalTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Cari produk...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage, fontSize = 12.sp, color = Color(0xFFDC2626))
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val qty = selectedQty.value[product.id] ?: 0
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF6)),
                            modifier = Modifier.fillMaxWidth().border(1.dp, MinimalBorder, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(product.price.formatRupiah(), fontSize = 12.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (qty > 0) {
                                                selectedQty.value = selectedQty.value.toMutableMap().apply { put(product.id, qty - 1) }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Kurangi", modifier = Modifier.size(16.dp))
                                    }
                                    Text("$qty", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    IconButton(
                                        onClick = {
                                            selectedQty.value = selectedQty.value.toMutableMap().apply { put(product.id, qty + 1) }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tambahan:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(totalToAdd.formatRupiah(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onClearError()
                        val newItems = selectedQty.value.entries
                            .filter { it.value > 0 }
                            .mapNotNull { (productId, qty) ->
                                products.firstOrNull { it.id == productId }?.let { product ->
                                    TableOrderItem(
                                        productId = product.id,
                                        name = product.name,
                                        price = product.price,
                                        qty = qty,
                                        status = "pending"
                                    )
                                }
                            }
                        if (newItems.isNotEmpty()) onConfirm(newItems)
                    },
                    enabled = selectedQty.value.values.any { it > 0 },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = GreenAccentDark)
                ) {
                    Text("Tambahkan ke Pesanan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
