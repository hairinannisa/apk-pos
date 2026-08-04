package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenAccentDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.GreenSecondaryContainer
import com.example.ui.theme.MinimalBorder
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary
import java.text.NumberFormat
import java.util.Locale

fun Double.formatRupiah(): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    formatter.maximumFractionDigits = 0
    return formatter.format(this).replace("Rp", "Rp ")
}

fun Long.formatRupiah(): String = this.toDouble().formatRupiah()

@Composable
fun BranchBadge(
    assignedBranchId: String?,
    modifier: Modifier = Modifier
) {
    val isBranch = !assignedBranchId.isNullOrEmpty()
    val text = if (isBranch) "Cabang ID: ${assignedBranchId!!.take(8)}" else "Pusat (Pusat Bisnis)"
    val bgColor = Color(0xFFD7E8CD)
    val textColor = Color(0xFF042100)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RoleBadge(
    role: String,
    modifier: Modifier = Modifier
) {
    val label = when (role.lowercase()) {
        "kasir" -> "Kasir (POS)"
        "kitchen" -> "Dapur (Kitchen)"
        "owner" -> "Pemilik"
        "admin" -> "Admin"
        else -> role.uppercase()
    }

    val (bgColor, textColor) = when (role.lowercase()) {
        "kasir" -> GreenPrimary to Color.White
        "kitchen" -> Color(0xFFEA580C) to Color.White
        else -> Color(0xFF42493F) to Color.White
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StockBadge(
    stockCount: Int,
    modifier: Modifier = Modifier
) {
    val isOut = stockCount <= 0
    val isLow = stockCount in 1..5
    val (bgColor, textColor) = when {
        isOut -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
        isLow -> Color(0xFFFEF3C7) to Color(0xFFD97706)
        else -> Color(0xFFF0F4E9) to Color(0xFF386B20)
    }

    val label = if (isOut) "Stok Habis" else "Stok: $stockCount"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, MinimalBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
