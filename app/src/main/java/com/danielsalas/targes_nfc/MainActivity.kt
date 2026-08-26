package com.danielsalas.targes_nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielsalas.targes_nfc.data.AppDatabase
import com.danielsalas.targes_nfc.data.CardEntity
import com.danielsalas.targes_nfc.nfc.NfcScannerManager
import com.danielsalas.targes_nfc.nfc.ScannedCardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.danielsalas.targes_nfc.nfc.HceLogManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.text.font.FontFamily

import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WarningAmber

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    
    var scannedCardState = mutableStateOf<ScannedCardData?>(null)
    var writeModeUid = mutableStateOf<String?>(null) // UID que queremos escribir
    var writeStatus = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1E88E5),
                    secondary = Color(0xFF0D47A1),
                    background = Color(0xFFF5F5F5)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen(this)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                adapter.enableForegroundDispatch(this, pendingIntent, null, null)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            tag?.let {
                if (writeModeUid.value != null) {
                    val result = NfcScannerManager.writeUidToTag(it, writeModeUid.value!!)
                    if (result.isSuccess) {
                        writeStatus.value = "✅ ÉXITO: UID grabado."
                        writeModeUid.value = null // Desactivar modo escritura
                    } else {
                        writeStatus.value = "❌ ERROR: ${result.exceptionOrNull()?.message}"
                    }
                } else {
                    val parsed = NfcScannerManager.parseTag(it)
                    scannedCardState.value = parsed
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(activity: MainActivity) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val cardDao = database.cardDao()

    var cards by remember { mutableStateOf<List<CardEntity>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedCard by remember { mutableStateOf<CardEntity?>(null) }
    var showWriteDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var cardNameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        cardDao.getAllCards().collectLatest { list ->
            cards = list
        }
    }

    val scannedCard = activity.scannedCardState.value
    val currentWriteUid = activity.writeModeUid.value
    val writeStatus = activity.writeStatus.value

    if (showWriteDialog) {
        AlertDialog(
            onDismissRequest = { 
                showWriteDialog = false
                activity.writeModeUid.value = null
                activity.writeStatus.value = null
            },
            title = { Text("Clonar en Pegatina NFC") },
            text = {
                Column {
                    Icon(
                        Icons.Default.WarningAmber, 
                        contentDescription = null, 
                        tint = Color(0xFFFFA000), 
                        modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Esta función grabará el UID de la tarjeta en una pegatina NFC compatible (CUID/Magic Gen2).", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (writeStatus == null) {
                        Text("ACERCA LA PEGATINA AHORA...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(writeStatus, fontWeight = FontWeight.Bold, color = if (writeStatus.contains("ÉXITO")) Color(0xFF2E7D32) else Color.Red)
                    }
                }
            },
            confirmButton = {
                if (writeStatus != null) {
                    Button(onClick = { 
                        showWriteDialog = false
                        activity.writeStatus.value = null
                    }) {
                        Text("Aceptar")
                    }
                }
            },
            dismissButton = {
                if (writeStatus == null) {
                    TextButton(onClick = { 
                        showWriteDialog = false 
                        activity.writeModeUid.value = null
                    }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Targes NFC - Contenedores", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = { showLogs = !showLogs }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Ver Logs", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        activity.scannedCardState.value = null
                        cardNameInput = ""
                        showScanDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir Tarjeta", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showLogs) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.Black).padding(8.dp)) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Consola HCE (Tiempo real)", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Limpiar", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.clickable { HceLogManager.clear() })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (HceLogManager.logs.isEmpty()) {
                            Text("Esperando comunicación del lector...", color = Color.DarkGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        HceLogManager.logs.forEach { log ->
                            Text(log, color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                HorizontalDivider(color = Color.DarkGray)
            }
            
            if (cards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No hay tarjetas guardadas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pulsa el botón '+' para acercar tu tarjeta municipal y registrarla.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(cards) { card ->
                        CardItemView(
                            card = card,
                            onClick = {
                                selectedCard = card
                            },
                            onDelete = {
                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                    cardDao.deleteCard(card)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        if (showScanDialog) {
            AlertDialog(
                onDismissRequest = { showScanDialog = false },
                title = { Text("Registrar Nueva Tarjeta NFC") },
                text = {
                    Column {
                        if (scannedCard == null) {
                            Text("Por favor, acerca tu tarjeta NFC del ayuntamiento a la parte trasera del teléfono móvil...", color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            OutlinedTextField(
                                value = cardNameInput,
                                onValueChange = { cardNameInput = it },
                                label = { Text("Nombre de la tarjeta (ej. Contenedor Orgánica)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("UID: ${scannedCard.uid}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tipo: ${scannedCard.cardType}", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val isCompatible = scannedCard.compatibilityStatus == "COMPATIBLE"
                            val cardBg = if (isCompatible) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            val textColor = if (isCompatible) Color(0xFF2E7D32) else Color(0xFFC62828)

                            Box(modifier = Modifier.fillMaxWidth().background(cardBg).padding(8.dp)) {
                                Column {
                                    Text(scannedCard.compatibilitySummary, fontWeight = FontWeight.Bold, color = textColor, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(scannedCard.compatibilityDetails, fontSize = 12.sp, color = Color.DarkGray)
                                }
                            }

                            if (!scannedCard.contentDump.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Vista previa de memoria:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(Color(0xFFF0F0F0))
                                        .padding(4.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(scannedCard.contentDump, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (scannedCard != null) {
                        Button(
                            onClick = {
                                if (cardNameInput.isNotBlank()) {
                                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                        cardDao.insertCard(
                                            CardEntity(
                                                name = cardNameInput,
                                                uid = scannedCard.uid,
                                                cardType = scannedCard.cardType,
                                                technologies = scannedCard.technologies,
                                                compatibilityStatus = scannedCard.compatibilityStatus,
                                                compatibilitySummary = scannedCard.compatibilitySummary,
                                                compatibilityDetails = scannedCard.compatibilityDetails,
                                                contentDump = scannedCard.contentDump
                                            )
                                        )
                                        withContext(Dispatchers.Main) {
                                            showScanDialog = false
                                            activity.scannedCardState.value = null
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Guardar Tarjeta")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showScanDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }

    selectedCard?.let { card ->
        AlertDialog(
            onDismissRequest = { selectedCard = null },
            title = { Text(card.name) },
            text = {
                Column {
                    Button(
                        onClick = {
                            activity.writeModeUid.value = card.uid
                            activity.writeStatus.value = null
                            showWriteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clonar UID en Pegatina", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("UID: ${card.uid}", fontWeight = FontWeight.Bold)
                    Text("Tecnología: ${card.cardType} (${card.technologies})")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Estado Android: ${card.compatibilityStatus}", color = if (card.compatibilityStatus == "COMPATIBLE") Color(0xFF2E7D32) else Color(0xFFC62828), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(card.compatibilityDetails, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!card.contentDump.isNullOrBlank()) {
                        Text("Contenido de Memoria:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .background(Color(0xFFEEEEEE))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = card.contentDump,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Para emular esta tarjeta y abrir el contenedor, mantén la pantalla encendida y acerca el móvil al lector NFC.", fontSize = 13.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = { selectedCard = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
fun CardItemView(card: CardEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val isCompatible = card.compatibilityStatus == "COMPATIBLE"
    val badgeColor = if (isCompatible) Color(0xFF4CAF50) else Color(0xFFE53935)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("UID: ${card.uid}", fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(2.dp))
                Text(card.cardType, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(badgeColor, shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isCompatible) "Emulable" else "Restringida",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Gray)
                }
            }
        }
    }
}
