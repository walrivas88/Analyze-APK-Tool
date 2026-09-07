package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.apk.ApkPackageHelper
import com.example.apk.ApkPatcher
import com.example.apk.BundledSoEntry
import com.example.apk.InstalledAppItem
import com.example.apk.PatchExecutionResult
import com.example.apk.PatchRule
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CyberCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class PatchLabSection(val title: String) {
  STUDIO("Patch Studio & Inyección"),
  FRIDA_SCRIPT("Script Frida Live"),
  RESULTS("Resultado & Exportar")
}

@Composable
fun ApkBinaryPatchLabDialog(
  initialApkUri: Uri? = null,
  initialApkName: String = "",
  initialInstalledApp: InstalledAppItem? = null,
  initialSoEntry: BundledSoEntry? = null,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(12.dp),
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp
    ) {
      ApkBinaryPatchLabContent(
        initialApkUri = initialApkUri,
        initialApkName = initialApkName,
        initialInstalledApp = initialInstalledApp,
        initialSoEntry = initialSoEntry,
        onDismiss = onDismiss
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkBinaryPatchLabContent(
  initialApkUri: Uri? = null,
  initialApkName: String = "",
  initialInstalledApp: InstalledAppItem? = null,
  initialSoEntry: BundledSoEntry? = null,
  onDismiss: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var currentApkUri by remember { mutableStateOf(initialApkUri) }
  var currentApkName by remember { mutableStateOf(initialApkName.ifEmpty { initialInstalledApp?.appName ?: "Target APK" }) }
  var currentInstalledApp by remember { mutableStateOf(initialInstalledApp) }

  var librariesInApk by remember { mutableStateOf<List<BundledSoEntry>>(emptyList()) }
  var selectedLibrary by remember { mutableStateOf<BundledSoEntry?>(initialSoEntry) }
  var isLoadingLibs by remember { mutableStateOf(false) }

  val patchRules = remember {
    mutableStateListOf(
      PatchRule(
        offsetHex = "0x1A40",
        hexBytes = "1F 20 03 D5",
        label = "NOP Anti-Root Check",
        description = "Neutraliza comprobación condicional inicial en ARM64"
      ),
      PatchRule(
        offsetHex = "0x2B80",
        hexBytes = "20 00 80 52 C0 03 5F D6",
        label = "Forzar isPro() / Licencia True",
        description = "MOV W0, #1; RET - Retorna true a la capa Java"
      )
    )
  }

  var selectedSection by remember { mutableStateOf(PatchLabSection.STUDIO) }

  // New Rule inputs
  var newRuleOffset by remember { mutableStateOf("0x1000") }
  var newRuleLabel by remember { mutableStateOf("") }
  var newRulePreset by remember { mutableStateOf("NOP") }
  var newRuleCustomHex by remember { mutableStateOf("1F 20 03 D5") }

  // Search & Replace string inputs
  var stringSearchInput by remember { mutableStateOf("") }
  var stringReplaceInput by remember { mutableStateOf("") }

  // Repackaging options
  var stripSignatures by remember { mutableStateOf(true) }
  var isPatching by remember { mutableStateOf(false) }
  var patchResult by remember { mutableStateOf<PatchExecutionResult?>(null) }

  // File Picker to load a different APK
  val apkPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    uri?.let {
      currentApkUri = it
      currentInstalledApp = null
      currentApkName = it.lastPathSegment?.substringAfterLast("/") ?: "selected.apk"
    }
  }

  // Load libraries whenever currentApkUri or currentInstalledApp changes
  LaunchedEffect(currentApkUri, currentInstalledApp) {
    isLoadingLibs = true
    withContext(Dispatchers.IO) {
      val libs = when {
        currentInstalledApp != null -> ApkPackageHelper.getSoFilesInInstalledApp(currentInstalledApp!!)
        currentApkUri != null -> ApkPackageHelper.listSoFilesFromUri(context, currentApkUri!!)
        else -> emptyList()
      }
      withContext(Dispatchers.Main) {
        librariesInApk = libs
        if (selectedLibrary == null || libs.none { it.path == selectedLibrary?.path }) {
          selectedLibrary = libs.firstOrNull()
        }
        isLoadingLibs = false
      }
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Top Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .background(AccentRose.copy(alpha = 0.2f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.Default.AutoFixHigh,
            contentDescription = null,
            tint = AccentRose,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "APK Binary Patch Lab",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Text(
            text = "Inyección y Parcheo Nativo en APK Target",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (onDismiss != null) {
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("close_patch_lab")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
        }
      }
    }

        // Section Tabs
        TabRow(
          selectedTabIndex = selectedSection.ordinal,
          containerColor = MaterialTheme.colorScheme.surface
        ) {
          PatchLabSection.entries.forEach { section ->
            Tab(
              selected = selectedSection == section,
              onClick = { selectedSection = section },
              text = {
                Text(
                  text = section.title,
                  fontSize = 12.sp,
                  fontWeight = if (selectedSection == section) FontWeight.Bold else FontWeight.Normal,
                  color = if (selectedSection == section) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            )
          }
        }

        // Content Area
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          when (selectedSection) {
            PatchLabSection.STUDIO -> {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                // 1. Target APK & Library Card
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Target APK:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = currentApkName,
                          fontSize = 13.sp,
                          fontWeight = FontWeight.Bold,
                          color = CyberCyan
                        )
                      }

                      OutlinedButton(
                        onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                        modifier = Modifier.testTag("button_change_apk")
                      ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cambiar APK", fontSize = 11.sp)
                      }
                    }

                    if (currentInstalledApp != null) {
                      Text(
                        text = "Paquete: ${currentInstalledApp?.packageName} • Versión: ${currentInstalledApp?.versionName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }

                    // Native Library Selector
                    Text("Biblioteca nativa (.so) dentro del APK a parchear:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    if (isLoadingLibs) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentMint, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscando bibliotecas .so dentro del APK...", fontSize = 11.sp)
                      }
                    } else if (librariesInApk.isEmpty()) {
                      Text(
                        "No se detectaron archivos .so en este paquete.",
                        fontSize = 11.sp,
                        color = AccentRose
                      )
                    } else {
                      var showLibDropdown by remember { mutableStateOf(false) }
                      Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                          color = Color(0xFF161B22),
                          shape = RoundedCornerShape(8.dp),
                          modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLibDropdown = true }
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                          Row(
                            modifier = Modifier
                              .fillMaxWidth()
                              .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Column {
                              Text(
                                text = selectedLibrary?.path ?: "Selecciona una biblioteca",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentMint,
                                fontFamily = FontFamily.Monospace
                              )
                              Text(
                                text = "ABI: ${selectedLibrary?.abi ?: "N/A"} • Tamaño: ${selectedLibrary?.sizeFormatted ?: "0 KB"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                          }
                        }

                        DropdownMenu(
                          expanded = showLibDropdown,
                          onDismissRequest = { showLibDropdown = false }
                        ) {
                          librariesInApk.forEach { lib ->
                            DropdownMenuItem(
                              text = {
                                Column {
                                  Text(lib.path, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                  Text("ABI: ${lib.abi} • ${lib.sizeFormatted}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                              },
                              onClick = {
                                selectedLibrary = lib
                                showLibDropdown = false
                              }
                            )
                          }
                        }
                      }
                    }
                  }
                }

                // 2. Active Patch Rules List
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = "Reglas de Parcheo Activas (${patchRules.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = "${patchRules.count { it.enabled }} activadas",
                        fontSize = 11.sp,
                        color = AccentMint
                      )
                    }

                    if (patchRules.isEmpty()) {
                      Text(
                        "No hay reglas agregadas. Usa el formulario de abajo para agregar parches.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    } else {
                      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        patchRules.forEachIndexed { index, rule ->
                          Surface(
                            color = Color(0xFF13171F),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                              .fillMaxWidth()
                              .border(1.dp, if (rule.enabled) AccentAmber.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                          ) {
                            Row(
                              modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                              horizontalArrangement = Arrangement.SpaceBetween,
                              verticalAlignment = Alignment.CenterVertically
                            ) {
                              Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                  Text(
                                    text = rule.label.ifEmpty { "Regla #${index + 1}" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (rule.enabled) Color.White else Color.Gray
                                  )
                                  Spacer(modifier = Modifier.width(8.dp))
                                  Text(
                                    text = rule.offsetHex,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = CyberCyan,
                                    fontWeight = FontWeight.Bold
                                  )
                                }
                                Text(
                                  text = "Bytes: ${rule.hexBytes}",
                                  fontFamily = FontFamily.Monospace,
                                  fontSize = 11.sp,
                                  color = AccentMint
                                )
                                if (rule.description.isNotEmpty()) {
                                  Text(
                                    text = rule.description,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                  )
                                }
                              }

                              Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                  checked = rule.enabled,
                                  onCheckedChange = { isChecked ->
                                    patchRules[index] = rule.copy(enabled = isChecked)
                                  },
                                  colors = SwitchDefaults.colors(checkedThumbColor = AccentAmber)
                                )
                                IconButton(
                                  onClick = { patchRules.removeAt(index) },
                                  modifier = Modifier.size(32.dp)
                                ) {
                                  Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AccentRose, modifier = Modifier.size(18.dp))
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }

                // 3. Add New Patch Rule Form
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Agregar Nueva Instrucción de Parche", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    // Presets chips
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      val presets = listOf(
                        "NOP" to "1F 20 03 D5",
                        "RET" to "C0 03 5F D6",
                        "Forzar True (1)" to "20 00 80 52 C0 03 5F D6",
                        "Forzar False (0)" to "00 00 80 52 C0 03 5F D6",
                        "Custom Hex" to ""
                      )

                      presets.forEach { (name, hex) ->
                        FilterChip(
                          selected = newRulePreset == name,
                          onClick = {
                            newRulePreset = name
                            if (hex.isNotEmpty()) newRuleCustomHex = hex
                            if (newRuleLabel.isEmpty()) newRuleLabel = name
                          },
                          label = { Text(name, fontSize = 11.sp) },
                          colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentAmber,
                            selectedLabelColor = Color.Black
                          )
                        )
                      }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      OutlinedTextField(
                        value = newRuleOffset,
                        onValueChange = { newRuleOffset = it },
                        label = { Text("Offset Hex (0x...)") },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = CyberCyan),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                      )

                      OutlinedTextField(
                        value = newRuleLabel,
                        onValueChange = { newRuleLabel = it },
                        label = { Text("Descripción / Etiqueta") },
                        modifier = Modifier.weight(1.5f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentMint)
                      )
                    }

                    OutlinedTextField(
                      value = newRuleCustomHex,
                      onValueChange = { newRuleCustomHex = it },
                      label = { Text("Bytes Hexadecimales (separados por espacio)") },
                      modifier = Modifier.fillMaxWidth(),
                      textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AccentMint),
                      colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentMint)
                    )

                    Button(
                      onClick = {
                        val clean = newRuleOffset.trim()
                        if (clean.isNotEmpty() && newRuleCustomHex.isNotEmpty()) {
                          patchRules.add(
                            PatchRule(
                              offsetHex = if (clean.startsWith("0x", ignoreCase = true)) clean else "0x$clean",
                              hexBytes = newRuleCustomHex.trim(),
                              label = newRuleLabel.ifEmpty { "Parche en $clean" },
                              description = "Preset: $newRulePreset"
                            )
                          )
                          newRuleLabel = ""
                          Toast.makeText(context, "Regla de parche agregada al laboratorio", Toast.LENGTH_SHORT).show()
                        }
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.align(Alignment.End)
                    ) {
                      Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Agregar Regla", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }

                // 4. Repackaging & In-Place Injection Card
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Aplicar en Target APK (Repackaging)", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Checkbox(
                        checked = stripSignatures,
                        onCheckedChange = { stripSignatures = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentAmber)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Column {
                        Text("Remover firmas anteriores (META-INF/*.RSA, *.SF)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Permite que la APK parcheada sea instalada o re-firmada sin conflicto de integridad.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }

                    Button(
                      onClick = {
                        val targetLib = selectedLibrary
                        if (targetLib == null) {
                          Toast.makeText(context, "Selecciona una biblioteca .so destino primero", Toast.LENGTH_SHORT).show()
                          return@Button
                        }
                        if (patchRules.none { it.enabled }) {
                          Toast.makeText(context, "Activa al menos una regla de parche", Toast.LENGTH_SHORT).show()
                          return@Button
                        }

                        isPatching = true
                        scope.launch {
                          val apkSourceFile = currentInstalledApp?.sourceDir?.let { File(it) }
                          val pkg = currentInstalledApp?.packageName ?: "target.app"

                          val result = ApkPatcher.patchApk(
                            context = context,
                            apkSourceUri = currentApkUri,
                            apkSourceFile = if (apkSourceFile?.exists() == true) apkSourceFile else null,
                            targetSoEntryPath = targetLib.path,
                            rules = patchRules.toList(),
                            packageName = pkg,
                            stripSignatures = stripSignatures
                          )

                          patchResult = result
                          isPatching = false

                          if (result.success) {
                            selectedSection = PatchLabSection.RESULTS
                            Toast.makeText(context, "¡APK Target parcheada con éxito!", Toast.LENGTH_LONG).show()
                          } else {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                          }
                        }
                      },
                      enabled = !isPatching && selectedLibrary != null && patchRules.any { it.enabled },
                      colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("button_apply_patch_apk")
                    ) {
                      if (isPatching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Inyectando bytes y reempaquetando APK...", color = Color.White, fontWeight = FontWeight.Bold)
                      } else {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          "APLICAR PARCHE A LA APK EN TARGET",
                          color = Color.White,
                          fontWeight = FontWeight.Bold,
                          fontSize = 12.sp
                        )
                      }
                    }
                  }
                }
              }
            }

            PatchLabSection.FRIDA_SCRIPT -> {
              val targetLibName = selectedLibrary?.name ?: "libtarget.so"
              val pkg = currentInstalledApp?.packageName ?: "target.package.name"
              val fridaScript = remember(selectedLibrary, patchRules.toList(), pkg) {
                ApkPatcher.generateFridaApkSpawnPatchScript(
                  packageName = pkg,
                  soFileName = targetLibName,
                  rules = patchRules.filter { it.enabled }
                )
              }

              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Script Frida Hot-Patch en Vivo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                      "Si prefieres no modificar el archivo APK físico, este script aplica exactamente las mismas reglas de parcheo en la memoria RAM del proceso usando Frida (bypasseando cualquier verificación de firma o checksum del APK).",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Button(
                        onClick = {
                          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                          cm.setPrimaryClip(ClipData.newPlainText("Frida Patch Script", fridaScript))
                          Toast.makeText(context, "Script Frida copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                      ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Script", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      OutlinedButton(
                        onClick = {
                          val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, fridaScript)
                            putExtra(Intent.EXTRA_TITLE, "frida_patch_${targetLibName}.js")
                          }
                          context.startActivity(Intent.createChooser(sendIntent, "Compartir Script Frida"))
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                      ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir", fontSize = 11.sp)
                      }
                    }
                  }
                }

                // Code block
                Surface(
                  color = Color(0xFF0D1117),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                  Text(
                    text = fridaScript,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = AccentMint,
                    modifier = Modifier.padding(12.dp)
                  )
                }
              }
            }

            PatchLabSection.RESULTS -> {
              val result = patchResult
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                if (result == null) {
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.Center
                    ) {
                      Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                      Spacer(modifier = Modifier.height(12.dp))
                      Text(
                        "Aún no has ejecutado el parche.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                      Spacer(modifier = Modifier.height(6.dp))
                      Text(
                        "Regresa a la pestaña 'Patch Studio & Inyección' y presiona el botón 'Aplicar Parche a la APK'.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                      )
                    }
                  }
                } else if (!result.success) {
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text("Error al aplicar parche", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                      Text(result.message, fontSize = 12.sp, color = Color.White)
                    }
                  }
                } else {
                  // Success Card
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AccentMint.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentMint.copy(alpha = 0.4f))
                  ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentMint, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          text = "¡APK Target Parcheada y Reempaquetada!",
                          fontSize = 14.sp,
                          fontWeight = FontWeight.Bold,
                          color = AccentMint
                        )
                      }

                      Text(
                        text = result.message,
                        fontSize = 12.sp,
                        color = Color.White
                      )

                      Spacer(modifier = Modifier.height(4.dp))

                      // Metadata stats
                      Column(
                        modifier = Modifier
                          .fillMaxWidth()
                          .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                          .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                      ) {
                        Text(
                          "Biblioteca inyectada: ${result.patchedLibraryPath}",
                          fontFamily = FontFamily.Monospace,
                          fontSize = 11.sp,
                          color = CyberCyan
                        )
                        Text(
                          "Reglas de parche aplicadas: ${result.appliedRulesCount}",
                          fontSize = 11.sp,
                          color = Color.White
                        )
                        val origMb = "%.2f MB".format(result.originalApkSize / (1024.0 * 1024.0))
                        val patchedMb = "%.2f MB".format(result.patchedApkSize / (1024.0 * 1024.0))
                        Text(
                          "Tamaño APK Original: $origMb → Parcheada: $patchedMb",
                          fontSize = 11.sp,
                          color = AccentAmber
                        )
                        result.outputFile?.let { f ->
                          Text(
                            "Archivo generado: ${f.name}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                        }
                      }

                      Spacer(modifier = Modifier.height(6.dp))

                      // Action Buttons
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        // Install Button
                        Button(
                          onClick = {
                            result.outputFile?.let { file ->
                              try {
                                val uri = FileProvider.getUriForFile(
                                  context,
                                  "${context.packageName}.fileprovider",
                                  file
                                )
                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                  setDataAndType(uri, "application/vnd.android.package-archive")
                                  flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(installIntent)
                              } catch (e: Exception) {
                                Toast.makeText(context, "Error al invocar instalador: ${e.message}", Toast.LENGTH_LONG).show()
                              }
                            }
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                          shape = RoundedCornerShape(8.dp),
                          modifier = Modifier.weight(1f)
                        ) {
                          Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Instalar APK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share / Export Button
                        Button(
                          onClick = {
                            result.outputFile?.let { file ->
                              try {
                                val uri = FileProvider.getUriForFile(
                                  context,
                                  "${context.packageName}.fileprovider",
                                  file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                  type = "application/vnd.android.package-archive"
                                  putExtra(Intent.EXTRA_STREAM, uri)
                                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Descargar / Compartir APK Parcheada"))
                              } catch (e: Exception) {
                                Toast.makeText(context, "Error al compartir archivo: ${e.message}", Toast.LENGTH_LONG).show()
                              }
                            }
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                          shape = RoundedCornerShape(8.dp),
                          modifier = Modifier.weight(1f)
                        ) {
                          Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Descargar / Compartir", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
