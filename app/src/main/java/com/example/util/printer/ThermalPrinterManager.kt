package com.example.util.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PrinterDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean = true
)

data class ReceiptItemData(
    val name: String,
    val variant: String? = null,
    val qty: Int,
    val price: Double
) {
    val totalPrice: Double get() = price * qty
}

data class ReceiptData(
    val businessName: String,
    val branchName: String? = null,
    val orderId: String,
    val orderTypeLabel: String? = null,
    val customerName: String? = null,
    val cashierName: String,
    val dateTime: String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date()),
    val items: List<ReceiptItemData>,
    val totalAmount: Double,
    val paymentMethod: String,
    val cashPaid: Double? = null,
    val change: Double? = null,
    val notes: String? = null
)

class ThermalPrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "ThermalPrinterManager"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private const val PREFS_NAME = "usahaki_printer_prefs"
        private const val KEY_PRINTER_NAME = "saved_printer_name"
        private const val KEY_PRINTER_ADDRESS = "saved_printer_address"
        private const val KEY_PAPER_WIDTH = "saved_paper_width" // 58 or 80
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> get() = _isScanning

    private val _discoveredDevices = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<PrinterDevice>> get() = _discoveredDevices

    val isBluetoothSupported: Boolean get() = bluetoothAdapter != null
    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    private var receiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let { dev ->
                        val devName = dev.name ?: "Printer Tanpa Nama"
                        val devAddress = dev.address ?: return@let
                        val isBonded = dev.bondState == BluetoothDevice.BOND_BONDED

                        val currentList = _discoveredDevices.value
                        if (currentList.none { it.address == devAddress }) {
                            _discoveredDevices.value = currentList + PrinterDevice(
                                name = devName,
                                address = devAddress,
                                isBonded = isBonded
                            )
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<PrinterDevice> {
        if (!isBluetoothEnabled) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.map {
                PrinterDevice(
                    name = it.name ?: "Unknown Printer",
                    address = it.address,
                    isBonded = true
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        if (!isBluetoothEnabled) return false
        try {
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.registerReceiver(bluetoothReceiver, filter)
                receiverRegistered = true
            }

            _discoveredDevices.value = emptyList()
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
            val started = bluetoothAdapter?.startDiscovery() ?: false
            _isScanning.value = started
            return started
        } catch (e: Exception) {
            Log.e(TAG, "Error starting bluetooth discovery", e)
            _isScanning.value = false
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
            _isScanning.value = false
            if (receiverRegistered) {
                context.unregisterReceiver(bluetoothReceiver)
                receiverRegistered = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
    }

    fun getSavedPrinter(): PrinterDevice? {
        val address = prefs.getString(KEY_PRINTER_ADDRESS, null) ?: return null
        val name = prefs.getString(KEY_PRINTER_NAME, "Thermal Printer") ?: "Thermal Printer"
        return PrinterDevice(name = name, address = address, isBonded = true)
    }

    fun savePrinter(device: PrinterDevice) {
        prefs.edit()
            .putString(KEY_PRINTER_NAME, device.name)
            .putString(KEY_PRINTER_ADDRESS, device.address)
            .apply()
    }

    fun clearSavedPrinter() {
        prefs.edit()
            .remove(KEY_PRINTER_NAME)
            .remove(KEY_PRINTER_ADDRESS)
            .apply()
    }

    fun getPaperWidth(): Int {
        return prefs.getInt(KEY_PAPER_WIDTH, 58) // 58 mm or 80 mm
    }

    fun savePaperWidth(width: Int) {
        prefs.edit().putInt(KEY_PAPER_WIDTH, width).apply()
    }

    /**
     * Prints receipt using ESC/POS over Bluetooth SPP connection
     */
    @SuppressLint("MissingPermission")
    suspend fun printReceipt(device: PrinterDevice, receipt: ReceiptData): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (!isBluetoothEnabled) {
                return@withContext Result.failure(Exception("Bluetooth tidak aktif."))
            }

            var socket: BluetoothSocket? = null
            try {
                val bluetoothDevice: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                    ?: return@withContext Result.failure(Exception("Perangkat Bluetooth tidak ditemukan."))

                if (bluetoothAdapter?.isDiscovering == true) {
                    bluetoothAdapter?.cancelDiscovery()
                }

                socket = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                val outputStream = socket.outputStream
                val paperWidthMm = getPaperWidth()
                val charsPerLine = if (paperWidthMm == 80) 48 else 32

                val escPosBytes = generateEscPosBytes(receipt, charsPerLine)
                outputStream.write(escPosBytes)
                outputStream.flush()

                // Safe delay before closing socket to ensure all bytes sent
                kotlinx.coroutines.delay(500)
                Result.success(Unit)
            } catch (e: IOException) {
                Log.e(TAG, "Error printing receipt via Bluetooth", e)
                Result.failure(Exception("Gagal terhubung ke printer '${device.name}'. Pastikan printer dalam keadaan menyala dan berada di dekat HP/Tablet."))
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error printing receipt", e)
                Result.failure(Exception("Gagal mencetak: ${e.localizedMessage ?: "Terjadi kesalahan"}"))
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Test print function to verify printer connection and alignment
     */
    suspend fun printTestReceipt(device: PrinterDevice): Result<Unit> {
        val testData = ReceiptData(
            businessName = "USAHAKI POS DEMO",
            branchName = "Cabang Utama",
            orderId = "TEST-001",
            orderTypeLabel = "Tes Koneksi",
            customerName = "Pengujian Printer",
            cashierName = "Kasir Test",
            items = listOf(
                ReceiptItemData("Kopi Susu Gula Aren", "Medium", 2, 18000.0),
                ReceiptItemData("Roti Bakar Cokelat", null, 1, 15000.0)
            ),
            totalAmount = 51000.0,
            paymentMethod = "CASH",
            cashPaid = 100000.0,
            change = 49000.0
        )
        return printReceipt(device, testData)
    }

    /**
     * Generates ESC/POS bytes for thermal printers
     */
    private fun generateEscPosBytes(receipt: ReceiptData, maxChars: Int): ByteArray {
        val bytes = mutableListOf<Byte>()

        fun writeBytes(arr: ByteArray) {
            for (b in arr) bytes.add(b)
        }

        fun writeString(str: String) {
            writeBytes(str.toByteArray(Charsets.ISO_8859_1))
        }

        // --- ESC/POS Commands ---
        val ESC: Byte = 0x1B
        val GS: Byte = 0x1D

        // Initialize Printer (ESC @)
        writeBytes(byteArrayOf(ESC, 0x40))

        // Center Align (ESC a 1)
        writeBytes(byteArrayOf(ESC, 0x61, 0x01))

        // Double Height + Double Width for Store Name (GS ! 0x11)
        writeBytes(byteArrayOf(GS, 0x21, 0x11))
        writeString("${receipt.businessName}\n")

        // Reset text size to normal (GS ! 0x00)
        writeBytes(byteArrayOf(GS, 0x21, 0x00))

        if (!receipt.branchName.isNull_or_empty()) {
            writeString("${receipt.branchName}\n")
        }

        val sepDouble = "=".repeat(maxChars) + "\n"
        val sepSingle = "-".repeat(maxChars) + "\n"

        writeString(sepDouble)

        // Left Align (ESC a 0)
        writeBytes(byteArrayOf(ESC, 0x61, 0x00))

        writeString(formatTwoColumns("No. Struk:", receipt.orderId, maxChars) + "\n")
        writeString(formatTwoColumns("Tanggal:", receipt.dateTime, maxChars) + "\n")
        writeString(formatTwoColumns("Kasir:", receipt.cashierName, maxChars) + "\n")

        if (!receipt.customerName.isNullOrEmpty() && receipt.customerName != "Pelanggan POS") {
            writeString(formatTwoColumns("Pelanggan:", receipt.customerName, maxChars) + "\n")
        }
        if (!receipt.orderTypeLabel.isNullOrEmpty()) {
            writeString(formatTwoColumns("Jenis:", receipt.orderTypeLabel, maxChars) + "\n")
        }

        writeString(sepSingle)

        // Items List
        for (item in receipt.items) {
            val itemName = if (!item.variant.isNullOrEmpty()) {
                "${item.name} (${item.variant})"
            } else {
                item.name
            }

            // If name is too long, wrap it
            writeString("$itemName\n")

            val qtyStr = "${item.qty} x ${formatRupiahSimple(item.price)}"
            val priceStr = formatRupiahSimple(item.totalPrice)
            writeString(formatTwoColumns("  $qtyStr", priceStr, maxChars) + "\n")
        }

        writeString(sepSingle)

        // Total Section (Bold ESC E 1)
        writeBytes(byteArrayOf(ESC, 0x45, 0x01))
        writeString(formatTwoColumns("TOTAL:", formatRupiahSimple(receipt.totalAmount), maxChars) + "\n")
        writeBytes(byteArrayOf(ESC, 0x45, 0x00))

        writeString(formatTwoColumns("Metode Bayar:", receipt.paymentMethod.uppercase(), maxChars) + "\n")

        if (receipt.cashPaid != null && receipt.cashPaid > 0) {
            writeString(formatTwoColumns("Tunai:", formatRupiahSimple(receipt.cashPaid), maxChars) + "\n")
        }
        if (receipt.change != null && receipt.change >= 0) {
            writeString(formatTwoColumns("Kembali:", formatRupiahSimple(receipt.change), maxChars) + "\n")
        }

        writeString(sepDouble)

        // Center Align for Footer
        writeBytes(byteArrayOf(ESC, 0x61, 0x01))
        writeString("Terima Kasih Atas Kunjungan Anda!\n")
        writeString("Powered by Usahaki POS\n")

        // Feed paper 4 lines (ESC d 4)
        writeBytes(byteArrayOf(ESC, 0x64, 0x04))

        return bytes.toByteArray()
    }

    private fun formatTwoColumns(left: String, right: String, totalWidth: Int): String {
        val availableLeft = totalWidth - right.length - 1
        val truncatedLeft = if (left.length > availableLeft && availableLeft > 0) {
            left.take(availableLeft)
        } else {
            left
        }
        val spacesCount = (totalWidth - truncatedLeft.length - right.length).coerceAtLeast(1)
        val spaces = " ".repeat(spacesCount)
        return "$truncatedLeft$spaces$right"
    }

    private fun formatRupiahSimple(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount).replace("Rp", "Rp ").replace(",00", "")
    }

    private fun String?.isNull_or_empty(): Boolean = this.isNullOrEmpty()
}

fun com.example.data.model.Order.toReceiptData(
    businessName: String,
    branchName: String? = null,
    cashierName: String
): ReceiptData {
    return ReceiptData(
        businessName = businessName,
        branchName = branchName,
        orderId = id.ifBlank { "POS-${System.currentTimeMillis() % 100000}" },
        orderTypeLabel = when (orderType) {
            "dine_in" -> "Dine In (${tableName ?: "Meja"})"
            "takeaway" -> "Bungkus"
            "no_table" -> "Tanpa Meja"
            else -> tableName
        },
        customerName = customerId ?: "Pelanggan POS",
        cashierName = cashierName,
        dateTime = createdAt.ifBlank {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date())
        },
        items = items.map {
            ReceiptItemData(
                name = it.name,
                variant = it.variant,
                qty = it.qty,
                price = it.price
            )
        },
        totalAmount = totalAmount,
        paymentMethod = paymentMethod
    )
}

fun com.example.data.model.TableOrder.toReceiptData(
    businessName: String,
    branchName: String? = null,
    cashierName: String
): ReceiptData {
    return ReceiptData(
        businessName = businessName,
        branchName = branchName,
        orderId = orderCode ?: id.take(12),
        orderTypeLabel = when (orderType) {
            "dine_in" -> "Dine In (${tableName.ifBlank { "Meja" }})"
            "takeaway" -> "Bungkus"
            else -> tableName.ifBlank { "Pesanan" }
        },
        customerName = customerName.ifBlank { "Pelanggan" },
        cashierName = cashierName,
        dateTime = createdAt.ifBlank {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date())
        },
        items = items.map {
            ReceiptItemData(
                name = it.name,
                variant = it.variant,
                qty = it.qty,
                price = it.price
            )
        },
        totalAmount = totalAmount,
        paymentMethod = paymentMethod ?: "cash"
    )
}

