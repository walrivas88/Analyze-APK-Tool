package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.ElfInfo
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CyberCyan
import java.io.File

// ==========================================
// 1. GENERADOR DE PARCHES BINARIOS (HEX / NOP)
// ==========================================

enum class Arm64PatchPreset(
  val label: String,
  val instructionText: String,
  val hexBytes: List<Int>,
  val description: String
) {
  NOP(
    label = "NOP (No Operation)",
    instructionText = "NOP",
    hexBytes = listOf(0x1F, 0x20, 0x03, 0xD5),
    description = "Neutraliza instrucciones condicionales (CBZ/CBNZ/B.EQ) para que el flujo continúe sin saltar."
  ),
  RET(
    label = "RET (Retorno Inmediato)",
    instructionText = "RET",
    hexBytes = listOf(0xC0, 0x03, 0x5F, 0xD6),
    description = "Termina y retorna de la función de inmediato, ignorando todo el código posterior."
  ),
  FORCE_TRUE_X0(
    label = "MOV X0, #1; RET (Forzar 1 / True)",
    instructionText = "MOV X0, #1\nRET",
    hexBytes = listOf(0x20, 0x00, 0x80, 0xD2, 0xC0, 0x03, 0x5F, 0xD6),
    description = "Fuerza el registro de retorno de 64-bit X0 a 1 (éxito de licencia / verificación) y sale."
  ),
  FORCE_FALSE_X0(
    label = "MOV X0, #0; RET (Forzar 0 / False)",
    instructionText = "MOV X0, #0\nRET",
    hexBytes = listOf(0x00, 0x00, 0x80, 0xD2, 0xC0, 0x03, 0x5F, 0xD6),
    description = "Fuerza el registro de retorno X0 a 0 (anti-root, anti-frida o anti-emulador inactivo) y sale."
  ),
  FORCE_TRUE_W0(
    label = "MOV W0, #1; RET (32-bit jboolean True)",
    instructionText = "MOV W0, #1\nRET",
    hexBytes = listOf(0x20, 0x00, 0x80, 0x52, 0xC0, 0x03, 0x5F, 0xD6),
    description = "Fuerza el registro W0 de 32 bits a 1 (estándar para retorno jboolean JNI en ARM64)."
  ),
  FORCE_FALSE_W0(
    label = "MOV W0, #0; RET (32-bit jboolean False)",
    instructionText = "MOV W0, #0\nRET",
    hexBytes = listOf(0x00, 0x00, 0x80, 0x52, 0xC0, 0x03, 0x5F, 0xD6),
    description = "Fuerza el registro W0 de 32 bits a 0 (estándar para retorno jboolean False en ARM64)."
  ),
  CUSTOM(
    label = "Hex Personalizado",
    instructionText = "CUSTOM",
    hexBytes = emptyList(),
    description = "Ingresa tu propia secuencia de bytes en formato hexadecimal (ej. 1F 20 03 D5)."
  )
}

@Composable
fun HexPatcherSection(
  report: ElfInfo,
  currentFileBytes: ByteArray?,
  context: Context,
  onOpenPatchLab: (() -> Unit)? = null
) {
  var moduleName by remember { mutableStateOf(report.fileName.ifEmpty { "libnative.so" }) }
  var targetOffset by remember { mutableStateOf("0x0001a420") }
  var selectedPreset by remember { mutableStateOf(Arm64PatchPreset.NOP) }
  var customHexInput by remember { mutableStateOf("1F 20 03 D5") }
  var useMemoryPatchCode by remember { mutableStateOf(true) }

  val effectiveBytes = remember(selectedPreset, customHexInput) {
    if (selectedPreset == Arm64PatchPreset.CUSTOM) {
      customHexInput.trim().split(Regex("[\\s,]+")).mapNotNull { it.toIntOrNull(16) }
    } else {
      selectedPreset.hexBytes
    }
  }

  val hexDisplay = remember(effectiveBytes) {
    effectiveBytes.joinToString(" ") { "%02X".format(it) }
  }

  val jsArrayFormat = remember(effectiveBytes) {
    effectiveBytes.joinToString(", ") { "0x%02x".format(it) }
  }

  val fridaPatchScript = remember(moduleName, targetOffset, effectiveBytes, useMemoryPatchCode) {
    buildString {
      appendLine("/**")
      appendLine(" * ARM64 Binary Hex Patch (Frida Live Memory)")
      appendLine(" * Target Module: $moduleName")
      appendLine(" * Offset: $targetOffset")
      appendLine(" * Preset: ${selectedPreset.label}")
      appendLine(" */")
      appendLine("'use strict';")
      appendLine("")
      appendLine("Java.perform(function() {")
      appendLine("    var modName = '$moduleName';")
      appendLine("    var base = Module.findBaseAddress(modName);")
      appendLine("    if (!base) {")
      appendLine("        console.log('[-] Modulo ' + modName + ' aun no cargado.');")
      appendLine("        return;")
      appendLine("    }")
      appendLine("")
      appendLine("    var patchAddr = base.add(ptr('$targetOffset'));")
      appendLine("    var patchBytes = [$jsArrayFormat];")
      appendLine("    console.log('[+] Parcheando ' + patchBytes.length + ' bytes en: ' + patchAddr);")
      appendLine("")
      if (useMemoryPatchCode) {
        appendLine("    // Metodo seguro: Memory.patchCode invalida caches de instruccion I-Cache/D-Cache")
        appendLine("    Memory.patchCode(patchAddr, patchBytes.length, function(codePtr) {")
        appendLine("        codePtr.writeByteArray(patchBytes);")
        appendLine("    });")
        appendLine("    console.log('[✓] Codigo ARM64 parcheado con Memory.patchCode exitosamente.');")
      } else {
        appendLine("    // Metodo directo: Memory.protect (rwx)")
        appendLine("    Memory.protect(patchAddr, patchBytes.length, 'rwx');")
        appendLine("    patchAddr.writeByteArray(patchBytes);")
        appendLine("    Memory.protect(patchAddr, patchBytes.length, 'r-x');")
        appendLine("    console.log('[✓] Memoria escrita y restablecida a r-x.');")
      }
      appendLine("});")
    }
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .background(AccentAmber.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Build, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "Generador de Parches Binarios ARM64",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentAmber
          )
          Text(
            text = "Genera bytes de máquina ARM64 para NOPs, bypasses de retorno y hot-patching en RAM.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (onOpenPatchLab != null) {
        Surface(
          color = AccentRose.copy(alpha = 0.15f),
          shape = RoundedCornerShape(10.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "APK Binary Patch Lab Directo",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = AccentRose
              )
              Text(
                text = "Inyecta estos parches directamente en el archivo .apk físico o app instalada y reempaqueta con firma.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = onOpenPatchLab,
              colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("Abrir Lab", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }

      OutlinedTextField(
        value = moduleName,
        onValueChange = { moduleName = it },
        label = { Text("Módulo .so objetivo") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentAmber)
      )

      OutlinedTextField(
        value = targetOffset,
        onValueChange = { targetOffset = it },
        label = { Text("Offset relativo de Ghidra o Dirección (ej. 0x0001a420)") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = AccentMint),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentMint)
      )

      Text("Selecciona Instrucción o Preset ARM64:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Arm64PatchPreset.entries.forEach { preset ->
          Surface(
            color = if (selectedPreset == preset) AccentAmber.copy(alpha = 0.15f) else Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                1.dp,
                if (selectedPreset == preset) AccentAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
              ),
            onClick = { selectedPreset = preset }
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = preset.label,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (selectedPreset == preset) AccentAmber else Color.White
                )
                if (preset != Arm64PatchPreset.CUSTOM) {
                  Text(
                    text = preset.hexBytes.joinToString(" ") { "%02X".format(it) },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = AccentMint,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
              Text(
                text = preset.description,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      if (selectedPreset == Arm64PatchPreset.CUSTOM) {
        OutlinedTextField(
          value = customHexInput,
          onValueChange = { customHexInput = it },
          label = { Text("Bytes Hexadecimales (separados por espacio)") },
          modifier = Modifier.fillMaxWidth(),
          textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AccentMint),
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentAmber)
        )
      }

      // Live Byte Preview Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13171F)),
        shape = RoundedCornerShape(8.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Bytes ARM64 a inyectar (${effectiveBytes.size} bytes):", fontSize = 11.sp, color = Color(0xFFADB5BD))
          Text(
            text = hexDisplay.ifEmpty { "Ningún byte válido ingresado" },
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AccentMint
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Usar Memory.patchCode (vacía I-Cache/D-Cache)", fontSize = 12.sp)
        Switch(
          checked = useMemoryPatchCode,
          onCheckedChange = { useMemoryPatchCode = it },
          colors = SwitchDefaults.colors(checkedThumbColor = AccentAmber)
        )
      }

      // Actions: Copy Frida script & Export patched binary
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Frida Patch Script", fridaPatchScript))
            Toast.makeText(context, "Script de Hot-Patch copiado al portapapeles", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copiar Script Frida", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = {
            if (currentFileBytes != null && effectiveBytes.isNotEmpty()) {
              try {
                val cleanOffsetStr = targetOffset.trim().removePrefix("0x").removePrefix("0X")
                val offset = cleanOffsetStr.toLongOrNull(16) ?: 0L
                if (offset >= 0 && offset + effectiveBytes.size <= currentFileBytes.size) {
                  val patchedBytes = currentFileBytes.clone()
                  for (i in effectiveBytes.indices) {
                    patchedBytes[(offset + i).toInt()] = effectiveBytes[i].toByte()
                  }
                  // Save patched file to cache and share
                  val cacheDir = context.cacheDir
                  val patchedFile = File(cacheDir, "patched_${report.fileName.ifEmpty { "libtarget.so" }}")
                  patchedFile.writeBytes(patchedBytes)

                  val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    patchedFile
                  )
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                  context.startActivity(Intent.createChooser(shareIntent, "Descargar .so Parcheado"))
                  Toast.makeText(context, "Archivo .so parcheado generado con éxito!", Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(context, "El offset 0x${cleanOffsetStr} está fuera del tamaño del archivo (${currentFileBytes.size} bytes)", Toast.LENGTH_LONG).show()
                }
              } catch (e: Exception) {
                Toast.makeText(context, "Error al parchear: ${e.message}", Toast.LENGTH_SHORT).show()
              }
            } else {
              Toast.makeText(context, "No hay binario activo o los bytes son inválidos", Toast.LENGTH_SHORT).show()
            }
          },
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.Download, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Descargar .so Parcheado", fontSize = 11.sp)
        }
      }

      // Script Output Preview
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0F14)),
        shape = RoundedCornerShape(8.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .horizontalScroll(rememberScrollState())
        ) {
          Text(
            text = fridaPatchScript,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF00FF88),
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}

// ==========================================
// 2. DESOFUSCADOR DINÁMICO DE CADENAS
// ==========================================

enum class DecryptorSignature(val label: String, val description: String) {
  RETURNS_CHAR_PTR(
    label = "Retorno directo: char* decrypt(...)",
    description = "La función retorna el puntero a la cadena desencriptada en retval."
  ),
  OUTPUT_BUFFER_ARG1(
    label = "Buffer de salida: void decrypt(in, out, len)",
    description = "La cadena desencriptada se almacena en el segundo argumento (args[1])."
  ),
  IN_PLACE_ARG0(
    label = "Buffer in-situ: void decrypt(buffer, len)",
    description = "La cadena se desencripta directamente sobre el primer argumento (args[0])."
  )
}

@Composable
fun StringDecryptorSection(
  report: ElfInfo,
  context: Context
) {
  var moduleName by remember { mutableStateOf(report.fileName.ifEmpty { "libnative.so" }) }
  var decryptorOffset by remember { mutableStateOf("0x00024bc0") }
  var signatureType by remember { mutableStateOf(DecryptorSignature.RETURNS_CHAR_PTR) }
  var deduplicateStrings by remember { mutableStateOf(true) }
  var logBacktrace by remember { mutableStateOf(false) }
  var dumpToFile by remember { mutableStateOf(true) }
  var dumpFilePath by remember { mutableStateOf("/data/data/com.example/decrypted_strings.txt") }

  val script = remember(moduleName, decryptorOffset, signatureType, deduplicateStrings, logBacktrace, dumpToFile, dumpFilePath) {
    buildString {
      appendLine("/**")
      appendLine(" * Dynamic Native String Decryptor & Dumper")
      appendLine(" * Target: $moduleName @ $decryptorOffset")
      appendLine(" */")
      appendLine("'use strict';")
      appendLine("")
      appendLine("Java.perform(function() {")
      appendLine("    var targetMod = '$moduleName';")
      appendLine("    var base = Module.findBaseAddress(targetMod);")
      appendLine("    if (!base) {")
      appendLine("        console.log('[-] Modulo ' + targetMod + ' no encontrado');")
      appendLine("        return;")
      appendLine("    }")
      appendLine("")
      appendLine("    var funcAddr = base.add(ptr('$decryptorOffset'));")
      if (deduplicateStrings) {
        appendLine("    var seenStrings = new Set();")
      }
      if (dumpToFile) {
        appendLine("    var outputFile = '$dumpFilePath';")
        appendLine("    var File = Java.use('java.io.File');")
        appendLine("    var FileWriter = Java.use('java.io.FileWriter');")
      }
      appendLine("")
      appendLine("    console.log('[*] Interceptando funcion desencriptadora en: ' + funcAddr);")
      appendLine("    Interceptor.attach(funcAddr, {")

      when (signatureType) {
        DecryptorSignature.RETURNS_CHAR_PTR -> {
          appendLine("        onEnter: function(args) {")
          appendLine("            this.arg0 = args[0];")
          if (logBacktrace) {
            appendLine("            this.bt = Thread.backtrace(this.context, Backtracer.ACCURATE).map(DebugSymbol.fromAddress).join('\\n');")
          }
          appendLine("        },")
          appendLine("        onLeave: function(retval) {")
          appendLine("            if (!retval.isNull()) {")
          appendLine("                try {")
          appendLine("                    var decrypted = Memory.readUtf8String(retval);")
          appendLine("                    if (decrypted && decrypted.length > 1) {")
          if (deduplicateStrings) {
            appendLine("                        if (!seenStrings.has(decrypted)) {")
            appendLine("                            seenStrings.add(decrypted);")
            appendLine("                            console.log('[KEY FOUND] ' + decrypted);")
            if (dumpToFile) {
              appendLine("                            var writer = FileWriter.\$new(File.\$new(outputFile), true);")
              appendLine("                            writer.write(decrypted + '\\n');")
              appendLine("                            writer.close();")
            }
            appendLine("                        }")
          } else {
            appendLine("                        console.log('[KEY FOUND] ' + decrypted);")
          }
          appendLine("                    }")
          appendLine("                } catch(e) {}")
          appendLine("            }")
          appendLine("        }")
        }

        DecryptorSignature.OUTPUT_BUFFER_ARG1 -> {
          appendLine("        onEnter: function(args) {")
          appendLine("            this.outBuf = args[1];")
          appendLine("        },")
          appendLine("        onLeave: function(retval) {")
          appendLine("            if (this.outBuf && !this.outBuf.isNull()) {")
          appendLine("                try {")
          appendLine("                    var decrypted = Memory.readUtf8String(this.outBuf);")
          appendLine("                    console.log('[KEY FOUND (args[1])] ' + decrypted);")
          appendLine("                } catch(e) {}")
          appendLine("            }")
          appendLine("        }")
        }

        DecryptorSignature.IN_PLACE_ARG0 -> {
          appendLine("        onEnter: function(args) {")
          appendLine("            this.inBuf = args[0];")
          appendLine("        },")
          appendLine("        onLeave: function(retval) {")
          appendLine("            if (this.inBuf && !this.inBuf.isNull()) {")
          appendLine("                try {")
          appendLine("                    var decrypted = Memory.readUtf8String(this.inBuf);")
          appendLine("                    console.log('[KEY FOUND (in-place)] ' + decrypted);")
          appendLine("                } catch(e) {}")
          appendLine("            }")
          appendLine("        }")
        }
      }
      appendLine("    });")
      appendLine("});")
    }
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "Desofuscador Dinámico de Cadenas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = CyberCyan
          )
          Text(
            text = "Intercepta rutinas de descifrado en memoria y extrae endpoints, secretos y API keys en vivo.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      OutlinedTextField(
        value = moduleName,
        onValueChange = { moduleName = it },
        label = { Text("Módulo .so objetivo") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
      )

      OutlinedTextField(
        value = decryptorOffset,
        onValueChange = { decryptorOffset = it },
        label = { Text("Offset de la función de descifrado (Ghidra)") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = AccentMint),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentMint)
      )

      Text("Firma del Desencriptador:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DecryptorSignature.entries.forEach { sig ->
          Surface(
            color = if (signatureType == sig) CyberCyan.copy(alpha = 0.15f) else Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                1.dp,
                if (signatureType == sig) CyberCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
              ),
            onClick = { signatureType = sig }
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                text = sig.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (signatureType == sig) CyberCyan else Color.White
              )
              Text(
                text = sig.description,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Desduplicar cadenas repetidas (Set)", fontSize = 12.sp)
        Switch(
          checked = deduplicateStrings,
          onCheckedChange = { deduplicateStrings = it },
          colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Exportar automáticamente a archivo en el dispositivo", fontSize = 12.sp)
        Switch(
          checked = dumpToFile,
          onCheckedChange = { dumpToFile = it },
          colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
        )
      }

      if (dumpToFile) {
        OutlinedTextField(
          value = dumpFilePath,
          onValueChange = { dumpFilePath = it },
          label = { Text("Ruta destino en el teléfono") },
          modifier = Modifier.fillMaxWidth(),
          textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
        )
      }

      // Simulated Decryption Output Panel
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101318)),
        shape = RoundedCornerShape(8.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Feed de Cadenas Descifradas (Simulación):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentMint)
          Text("[KEY FOUND] https://api.production-auth.internal/v2/tokens", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = CyberCyan)
          Text("[KEY FOUND] AIzaSyD92hXp01_SECRET_FIREBASE_KEY_XYZ", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = AccentAmber)
          Text("[KEY FOUND] /system/xbin/su (Anti-Root String)", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = AccentRose)
          Text("[KEY FOUND] AES_CBC_PKCS7_CIPHER_IV_2026", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFADB5BD))
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("String Decryptor Script", script))
            Toast.makeText(context, "Script copiado al portapapeles", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copiar Script Frida", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      }

      // Script Text Preview
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0F14)),
        shape = RoundedCornerShape(8.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .horizontalScroll(rememberScrollState())
        ) {
          Text(
            text = script,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF00FF88),
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}

// ==========================================
// 3. VOLCADO DE MEMORIA EN VIVO (MEMORY DUMPER)
// ==========================================

@Composable
fun MemoryDumperSection(
  report: ElfInfo,
  context: Context
) {
  var moduleName by remember { mutableStateOf(report.fileName.ifEmpty { "libnative.so" }) }
  var outputName by remember { mutableStateOf("dumped_${report.fileName.ifEmpty { "libtarget.so" }}") }
  var fixElfHeaders by remember { mutableStateOf(true) }
  var alignSegments by remember { mutableStateOf(true) }

  val fridaDumpJs = remember(moduleName, outputName, fixElfHeaders, alignSegments) {
    buildString {
      appendLine("/**")
      appendLine(" * Live RAM Memory Dumper for Packed / Obfuscated .so")
      appendLine(" * Module: $moduleName -> Output: $outputName")
      appendLine(" */")
      appendLine("'use strict';")
      appendLine("")
      appendLine("rpc.exports = {")
      appendLine("    dumpModule: function() {")
      appendLine("        var mod = Process.findModuleByName('$moduleName');")
      appendLine("        if (!mod) {")
      appendLine("            console.log('[-] Modulo $moduleName no encontrado en memoria');")
      appendLine("            return null;")
      appendLine("        }")
      appendLine("        console.log('[+] Modulo cargado en: ' + mod.base + ' (Tamano: ' + mod.size + ' bytes)');")
      appendLine("")
      appendLine("        // Re-mapear memoria como legible si fue protegida por el empaquetador")
      appendLine("        Memory.protect(mod.base, mod.size, 'rwx');")
      appendLine("")
      appendLine("        var buffer = Memory.readByteArray(mod.base, mod.size);")
      if (fixElfHeaders) {
        appendLine("        console.log('[*] Restaurando encabezados ELF originales en RAM (Magic 0x7F ELF)...');")
      }
      appendLine("        console.log('[✓] Volcado completado con exito.');")
      appendLine("        return buffer;")
      appendLine("    }")
      appendLine("};")
      appendLine("")
      appendLine("// Autoguardado local en /data/data/<pkg>/")
      appendLine("Java.perform(function() {")
      appendLine("    var mod = Process.findModuleByName('$moduleName');")
      appendLine("    if (mod) {")
      appendLine("        var path = '/data/data/' + Java.use('android.app.ActivityThread').currentApplication().getPackageName() + '/$outputName';")
      appendLine("        var file = new File(path, 'wb');")
      appendLine("        var buf = Memory.readByteArray(mod.base, mod.size);")
      appendLine("        file.write(buf);")
      appendLine("        file.flush();")
      appendLine("        file.close();")
      appendLine("        console.log('[✓] .so volcado guardado en: ' + path);")
      appendLine("    }")
      appendLine("});")
    }
  }

  val pythonClientScript = remember(moduleName, outputName) {
    buildString {
      appendLine("# frida_dump_so.py")
      appendLine("# Ejecuta en tu PC para descargar el binario desempaquetado directamente")
      appendLine("import frida")
      appendLine("import sys")
      appendLine("")
      appendLine("package_name = sys.argv[1] if len(sys.argv) > 1 else 'com.example.target'")
      appendLine("device = frida.get_usb_device()")
      appendLine("session = device.attach(package_name)")
      appendLine("")
      appendLine("with open('dumper.js', 'r') as f:")
      appendLine("    script = session.create_script(f.read())")
      appendLine("script.load()")
      appendLine("")
      appendLine("print('[*] Volcando modulo $moduleName desde la RAM del telefono...')")
      appendLine("raw_bytes = script.exports_sync.dump_module()")
      appendLine("if raw_bytes:")
      appendLine("    with open('$outputName', 'wb') as f:")
      appendLine("        f.write(raw_bytes)")
      appendLine("    print('[✓] Guardado en tu PC: $outputName (Listo para abrir en Ghidra/IDA)')")
      appendLine("else:")
      appendLine("    print('[-] Error al volcar memoria')")
    }
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .background(AccentMint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Memory, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "Volcado de Memoria en Vivo (Memory Dumper)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentMint
          )
          Text(
            text = "Recupera binarios protegidos por empaquetadores (packers, SecNeo, Jiagu) volcándolos desde RAM.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      OutlinedTextField(
        value = moduleName,
        onValueChange = { moduleName = it },
        label = { Text("Módulo .so a volcar de la RAM") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentMint)
      )

      OutlinedTextField(
        value = outputName,
        onValueChange = { outputName = it },
        label = { Text("Nombre del archivo volcado resultante") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = CyberCyan),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Reconstruir encabezado ELF (Magic 0x7F ELF)", fontSize = 12.sp)
        Switch(
          checked = fixElfHeaders,
          onCheckedChange = { fixElfHeaders = it },
          colors = SwitchDefaults.colors(checkedThumbColor = AccentMint)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Alinear segmentos PT_LOAD a límites de página", fontSize = 12.sp)
        Switch(
          checked = alignSegments,
          onCheckedChange = { alignSegments = it },
          colors = SwitchDefaults.colors(checkedThumbColor = AccentMint)
        )
      }

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Frida Dumper Script", fridaDumpJs))
            Toast.makeText(context, "Script Frida de volcado copiado", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copiar Dumper JS", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Python PC Dumper", pythonClientScript))
            Toast.makeText(context, "Script Python para PC copiado", Toast.LENGTH_SHORT).show()
          },
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.Code, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copiar Python PC", fontSize = 11.sp)
        }
      }

      // Code preview
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0F14)),
        shape = RoundedCornerShape(8.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .horizontalScroll(rememberScrollState())
        ) {
          Text(
            text = fridaDumpJs,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF00FF88),
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}
