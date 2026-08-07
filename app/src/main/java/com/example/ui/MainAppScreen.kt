package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.auth.AuthUiState
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.kitchen.KitchenScreen
import com.example.ui.kitchen.KitchenViewModel
import com.example.ui.pos.PosScreen
import com.example.ui.pos.PosViewModel
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenAccentDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.MinimalBackground
import com.example.ui.theme.MinimalBorder
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary
import kotlinx.coroutines.delay

@Composable
fun MainAppScreen(
    authViewModel: AuthViewModel = viewModel(),
    posViewModel: PosViewModel = viewModel(),
    kitchenViewModel: KitchenViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.uiState.collectAsState()

    var isSplashActive by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1200) // Minimum display time for splash screen branding
        isSplashActive = false
    }

    var selectedModule by remember { mutableStateOf("pos") } // "pos" or "kitchen"

    if (isSplashActive) {
        SplashScreen()
    } else {
        when (authState) {
            is AuthUiState.Loading -> {
                SplashScreen()
            }

            is AuthUiState.Success -> {
            val user = (authState as AuthUiState.Success).user
            val userRole = user.role.lowercase()

            val canToggleModule = userRole in listOf("owner", "admin", "manager", "manager_cabang")
            // Kasir SEKARANG juga boleh melihat (& berinteraksi dengan) tab
            // Dapur — supaya bisa mengecek langsung status pesanan yang
            // sedang diproses koki, bukan cuma menebak. Sebelumnya tab
            // navigasi Kasir/Dapur disembunyikan TOTAL untuk role kasir
            // (dipaksa selalu di POS), jadi mereka tidak punya cara sama
            // sekali melihat antrian dapur dari HP. Branch Switcher TETAP
            // TIDAK diberikan ke kasir (masih pakai canToggleModule terpisah
            // di atas) — kasir tetap terkunci ke 1 cabang tempat dia bekerja.
            val canViewKitchenTab = canToggleModule || userRole == "kasir"
            val isSupportedRole = userRole in listOf("owner", "admin", "manager", "manager_cabang", "kasir", "kitchen")

            if (!isSupportedRole) {
                // App ini SENGAJA hanya untuk role Kasir & Dapur (+ owner/admin/
                // manager untuk toggle keduanya). Role lain (mis. "karyawan")
                // TIDAK diberi akses diam-diam ke POS — daripada membingungkan
                // ("kok saya masuk POS padahal cuma staf biasa"), tampilkan
                // pesan jelas & arahkan logout.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MinimalBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Akun ini (peran: ${user.role}) tidak didukung di aplikasi Kasir & Dapur.",
                            color = MinimalTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { authViewModel.logout() }) {
                            Text("Keluar & masuk dengan akun lain", color = GreenPrimary)
                        }
                    }
                }
                return
            }

            val activeModule = when {
                userRole == "kitchen" -> "kitchen"
                else -> selectedModule
            }

            // Sumber kebenaran cabang operasional = PosViewModel (kedua
            // ViewModel di-set bersamaan tiap kali diganti, lihat
            // handleBranchChange di bawah) — supaya pindah tab Kasir<->Dapur
            // tidak mereset pilihan cabang.
            val availableBranches by posViewModel.availableBranches.collectAsState()
            val operatingBranchId by posViewModel.operatingBranchId.collectAsState()
            val currentBranchLabel = if (operatingBranchId == null) {
                "Pusat"
            } else {
                availableBranches.find { it.id == operatingBranchId }?.name ?: "Cabang"
            }

            fun handleBranchChange(branchId: String?) {
                posViewModel.setOperatingBranch(branchId)
                kitchenViewModel.setOperatingBranch(branchId)
            }

            Scaffold(
                topBar = {
                    // Branch Switcher HANYA untuk role tidak terkunci
                    // (owner/admin/manager) yang punya cabang tambahan aktif.
                    // Kasir/Dapur/Manager Cabang TIDAK dapat switcher ini
                    // sama sekali — mereka memang seharusnya terkunci ke 1
                    // cabang (assignedBranchId), tidak boleh pindah-pindah.
                    if (canToggleModule && availableBranches.isNotEmpty()) {
                        BranchSwitcherBar(
                            currentLabel = currentBranchLabel,
                            branches = availableBranches,
                            onSelect = { handleBranchChange(it) }
                        )
                    }
                },
                bottomBar = {
                    if (canViewKitchenTab) {
                        NavigationBar(
                            containerColor = MinimalBackground,
                            tonalElevation = 0.dp,
                            modifier = Modifier.border(0.5.dp, MinimalBorder, RoundedCornerShape(0.dp))
                        ) {
                            NavigationBarItem(
                                selected = activeModule == "pos",
                                onClick = { selectedModule = "pos" },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.PointOfSale,
                                        contentDescription = "Kasir (POS)"
                                    )
                                },
                                label = { Text("Kasir", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GreenPrimary,
                                    selectedTextColor = GreenPrimary,
                                    indicatorColor = Color(0xFFD7E8CD),
                                    unselectedIconColor = MinimalTextSecondary,
                                    unselectedTextColor = MinimalTextSecondary
                                )
                            )

                            NavigationBarItem(
                                selected = activeModule == "kitchen",
                                onClick = { selectedModule = "kitchen" },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Restaurant,
                                        contentDescription = "Dapur (Kitchen)"
                                    )
                                },
                                label = { Text("Dapur", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFFEA580C),
                                    selectedTextColor = Color(0xFFEA580C),
                                    indicatorColor = Color(0xFFFFEDD5),
                                    unselectedIconColor = MinimalTextSecondary,
                                    unselectedTextColor = MinimalTextSecondary
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    Crossfade(targetState = activeModule, label = "ModuleSwitch") { module ->
                        when (module) {
                            "kitchen" -> {
                                KitchenScreen(
                                    user = user,
                                    kitchenViewModel = kitchenViewModel,
                                    onLogout = { authViewModel.logout() }
                                )
                            }
                            else -> {
                                PosScreen(
                                    user = user,
                                    posViewModel = posViewModel,
                                    onLogout = { authViewModel.logout() }
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> {
            LoginScreen(viewModel = authViewModel)
        }
    }
}
}

/**
 * Dropdown pemilih cabang operasional — hanya dirender untuk role tidak
 * terkunci (owner/admin/manager) yang punya cabang tambahan. Analog dari
 * Branch Switcher di website. Pilihan "Pusat" selalu ada di paling atas.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BranchSwitcherBar(
    currentLabel: String,
    branches: List<com.example.data.model.Branch>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickableNoRipple { expanded = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MinimalTextPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Ganti cabang",
                        tint = MinimalTextSecondary
                    )
                }

                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Pusat") },
                        onClick = {
                            expanded = false
                            onSelect(null)
                        }
                    )
                    branches.forEach { branch ->
                        DropdownMenuItem(
                            text = { Text(branch.name) },
                            onClick = {
                                expanded = false
                                onSelect(branch.id)
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MinimalBackground)
    )
}

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var animateStart by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (animateStart) 1.05f else 0.85f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "SplashScale"
    )

    LaunchedEffect(Unit) {
        animateStart = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MinimalBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .scale(scale)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .border(1.5.dp, GreenPrimary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.usahaki_pos_icon_1785741909906),
                    contentDescription = "Usahaki Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Usahaki.id",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GreenPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Sistem Kasir & Dapur Digital",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MinimalTextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            CircularProgressIndicator(
                color = GreenPrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(26.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Memuat Sesi...",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MinimalTextSecondary
            )
        }

        Text(
            text = "v1.0.0 • Usahaki POS",
            fontSize = 11.sp,
            color = MinimalTextSecondary.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

/** Modifier.clickable tanpa efek ripple/indication — dipakai supaya label cabang terasa seperti dropdown native, bukan tombol besar. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)

