package com.example.ui.kitchen

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TableOrder
import com.example.data.model.TableOrderItem
import com.example.data.model.User
import com.example.ui.components.BranchBadge
import com.example.ui.components.RoleBadge
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenAccentDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.MinimalBackground
import com.example.ui.theme.MinimalBorder
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary

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
    val isLoading by kitchenViewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MinimalBackground)
        ) {
            if (isLoading && orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFEA580C))
                }
            } else if (orders.isEmpty()) {
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
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KitchenOrderCardClean(
    order: TableOrder,
    onToggleItem: (Int) -> Unit
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
