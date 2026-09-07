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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ElfInfo
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CyberCyan

enum class FridaSubTab(val label: String) {
  HOOK_BUILDER("Hook Builder"),
  HEX_PATCHER("Hex / NOP Patcher"),
  STRING_DECRYPTOR("String Decryptor"),
  MEMORY_DUMPER("Memory Dumper"),
  GHIDRA_EXPORTER("export_to_frida.py"),
  GHIDRA_IMPORTER("Import Ghidra JSON"),
  BYPASS_TEMPLATES("Bypass Templates"),
  EXECUTION_GUIDE("Frida & Root Setup")
}

data class GhidraFunctionItem(
  val name: String,
  val offset: String,
  val signature: String,
  val returnType: String,
  val isJni: Boolean,
  val xrefs: Int
)

enum class ArgFormat(val label: String) {
  NONE("Ignorar"),
  POINTER("Puntero (ptr)"),
  STRING_UTF8("String (readUtf8String)"),
  INT32("Entero (toInt32)"),
  HEXDUMP("Hexdump (64 bytes)")
}

enum class ReturnOverride(val label: String, val codeSnippet: String) {
  NONE("Sin Modificar", ""),
  FORCE_ONE("Forzar 1 (Éxito / True)", "retval.replace(1);"),
  FORCE_ZERO("Forzar 0 (Falso / Fail)", "retval.replace(0);"),
  FORCE_MINUS_ONE("Forzar -1 (Error)", "retval.replace(-1);"),
  FORCE_NULL("Forzar NULL / 0x0", "retval.replace(ptr(0));"),
  CUSTOM("Personalizado", "retval.replace(0x1);")
}

@Composable
fun FridaGhidraTab(
  report: ElfInfo,
  currentFileBytes: ByteArray? = null,
  onOpenPatchLab: (() -> Unit)? = null
) {
  val context = LocalContext.current
  var currentSubTab by remember { mutableStateOf(FridaSubTab.HOOK_BUILDER) }

  // Hook Builder State
  var moduleName by remember { mutableStateOf(report.fileName.ifEmpty { "libnative-lib.so" }) }
  var hookTargetType by remember { mutableStateOf("OFFSET") } // "OFFSET" or "EXPORT"
  var targetOffset by remember { mutableStateOf("0x4a2c0") }
  var targetSymbolName by remember {
    mutableStateOf(
      report.jniFunctions.firstOrNull()?.rawSymbol ?: report.symbols.firstOrNull { it.isExported }?.name ?: "Java_com_example_MainActivity_stringFromJNI"
    )
  }

  // Interception options
  var logOnEnter by remember { mutableStateOf(true) }
  var logBacktrace by remember { mutableStateOf(false) }
  var arg0Format by remember { mutableStateOf(ArgFormat.STRING_UTF8) }
  var arg1Format by remember { mutableStateOf(ArgFormat.INT32) }
  var arg2Format by remember { mutableStateOf(ArgFormat.NONE) }
  var logOnLeave by remember { mutableStateOf(true) }
  var returnOverride by remember { mutableStateOf(ReturnOverride.FORCE_ONE) }
  var customReturnValue by remember { mutableStateOf("1") }

  // Ghidra Imported Functions list
  var ghidraFunctions by remember {
    mutableStateOf(
      listOf(
        GhidraFunctionItem("verify_license_token", "0x0001a420", "int verify_license_token(char* token, int len)", "int", false, 4),
        GhidraFunctionItem("is_device_rooted", "0x0002b110", "bool is_device_rooted(void)", "bool", false, 8),
        GhidraFunctionItem("check_binary_integrity", "0x00035ce0", "int check_binary_integrity(char* hash)", "int", false, 2),
        GhidraFunctionItem("Java_com_app_Security_validateKey", "0x0004a2c0", "jboolean Java_com_app_Security_validateKey(JNIEnv* env, jobject thiz, jstring key)", "jboolean", true, 6)
      )
    )
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header Banner
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF131922)),
      shape = RoundedCornerShape(14.dp)
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Terminal, contentDescription = null, tint = CyberCyan)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Frida & Ghidra Reverse Engineering Bridge",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CyberCyan
          )
          Text(
            text = "Del desensamblado estático al hooking dinámico en memoria en 1 toque.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFADB5BD)
          )
        }
      }
    }

    // Sub-tab Navigation Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FridaSubTab.entries.forEach { subTab ->
        FilterChip(
          selected = currentSubTab == subTab,
          onClick = { currentSubTab = subTab },
          label = { Text(subTab.label, fontSize = 12.sp) },
          leadingIcon = {
            when (subTab) {
              FridaSubTab.HOOK_BUILDER -> Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
              FridaSubTab.HEX_PATCHER -> Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
              FridaSubTab.STRING_DECRYPTOR -> Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
              FridaSubTab.MEMORY_DUMPER -> Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(14.dp))
              FridaSubTab.GHIDRA_EXPORTER -> Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
              FridaSubTab.GHIDRA_IMPORTER -> Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
              FridaSubTab.BYPASS_TEMPLATES -> Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
              FridaSubTab.EXECUTION_GUIDE -> Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
            }
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
            selectedLabelColor = CyberCyan
          )
        )
      }
    }

    // Sub-tab Contents
    when (currentSubTab) {
      FridaSubTab.HOOK_BUILDER -> {
        HookBuilderSection(
          report = report,
          moduleName = moduleName,
          onModuleNameChange = { moduleName = it },
          hookTargetType = hookTargetType,
          onHookTargetTypeChange = { hookTargetType = it },
          targetOffset = targetOffset,
          onTargetOffsetChange = { targetOffset = it },
          targetSymbolName = targetSymbolName,
          onTargetSymbolNameChange = { targetSymbolName = it },
          logOnEnter = logOnEnter,
          onLogOnEnterChange = { logOnEnter = it },
          logBacktrace = logBacktrace,
          onLogBacktraceChange = { logBacktrace = it },
          arg0Format = arg0Format,
          onArg0FormatChange = { arg0Format = it },
          arg1Format = arg1Format,
          onArg1FormatChange = { arg1Format = it },
          arg2Format = arg2Format,
          onArg2FormatChange = { arg2Format = it },
          logOnLeave = logOnLeave,
          onLogOnLeaveChange = { logOnLeave = it },
          returnOverride = returnOverride,
          onReturnOverrideChange = { returnOverride = it },
          customReturnValue = customReturnValue,
          onCustomReturnValueChange = { customReturnValue = it },
          context = context
        )
      }

      FridaSubTab.HEX_PATCHER -> {
        HexPatcherSection(
          report = report,
          currentFileBytes = currentFileBytes,
          context = context,
          onOpenPatchLab = onOpenPatchLab
        )
      }

      FridaSubTab.STRING_DECRYPTOR -> {
        StringDecryptorSection(
          report = report,
          context = context
        )
      }

      FridaSubTab.MEMORY_DUMPER -> {
        MemoryDumperSection(
          report = report,
          context = context
        )
      }

      FridaSubTab.GHIDRA_EXPORTER -> {
        GhidraExporterSection(context)
      }

      FridaSubTab.GHIDRA_IMPORTER -> {
        GhidraImporterSection(
          functions = ghidraFunctions,
          onSelectFunction = { fn ->
            moduleName = report.fileName
            hookTargetType = "OFFSET"
            targetOffset = fn.offset
            targetSymbolName = fn.name
            currentSubTab = FridaSubTab.HOOK_BUILDER
            Toast.makeText(context, "Cargado offset ${fn.offset} (${fn.name}) en el Hook Builder", Toast.LENGTH_SHORT).show()
          },
          onImportJson = { newFunctions ->
            ghidraFunctions = newFunctions
            Toast.makeText(context, "Se importaron ${newFunctions.size} funciones de Ghidra", Toast.LENGTH_SHORT).show()
          },
          context = context
        )
      }

      FridaSubTab.BYPASS_TEMPLATES -> {
        BypassTemplatesSection(context)
      }

      FridaSubTab.EXECUTION_GUIDE -> {
        FridaExecutionGuideSection(report, context)
      }
    }
  }
}

@Composable
fun HookBuilderSection(
  report: ElfInfo,
  moduleName: String,
  onModuleNameChange: (String) -> Unit,
  hookTargetType: String,
  onHookTargetTypeChange: (String) -> Unit,
  targetOffset: String,
  onTargetOffsetChange: (String) -> Unit,
  targetSymbolName: String,
  onTargetSymbolNameChange: (String) -> Unit,
  logOnEnter: Boolean,
  onLogOnEnterChange: (Boolean) -> Unit,
  logBacktrace: Boolean,
  onLogBacktraceChange: (Boolean) -> Unit,
  arg0Format: ArgFormat,
  onArg0FormatChange: (ArgFormat) -> Unit,
  arg1Format: ArgFormat,
  onArg1FormatChange: (ArgFormat) -> Unit,
  arg2Format: ArgFormat,
  onArg2FormatChange: (ArgFormat) -> Unit,
  logOnLeave: Boolean,
  onLogOnLeaveChange: (Boolean) -> Unit,
  returnOverride: ReturnOverride,
  onReturnOverrideChange: (ReturnOverride) -> Unit,
  customReturnValue: String,
  onCustomReturnValueChange: (String) -> Unit,
  context: Context
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = "1. Objetivo de Interceptación (Target)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = CyberCyan
      )

      OutlinedTextField(
        value = moduleName,
        onValueChange = onModuleNameChange,
        label = { Text("Módulo / Archivo .so") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = CyberCyan,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
      )

      // Target selection mode: Offset vs Exported Symbol
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = hookTargetType == "OFFSET",
          onClick = { onHookTargetTypeChange("OFFSET") },
          label = { Text("Por Offset Relativo (Ghidra)") },
          colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberCyan.copy(alpha = 0.2f))
        )
        FilterChip(
          selected = hookTargetType == "EXPORT",
          onClick = { onHookTargetTypeChange("EXPORT") },
          label = { Text("Por Símbolo / JNI Export") },
          colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentMint.copy(alpha = 0.2f))
        )
      }

      if (hookTargetType == "OFFSET") {
        OutlinedTextField(
          value = targetOffset,
          onValueChange = onTargetOffsetChange,
          label = { Text("Offset de Ghidra (ej. 0x4a2c0 o 0x12bc0)") },
          modifier = Modifier.fillMaxWidth(),
          textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = AccentMint),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentMint,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          )
        )
      } else {
        OutlinedTextField(
          value = targetSymbolName,
          onValueChange = onTargetSymbolNameChange,
          label = { Text("Nombre del Símbolo Exportado o JNI") },
          modifier = Modifier.fillMaxWidth(),
          textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = CyberCyan),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberCyan,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          )
        )

        // Quick suggestions from analyzed binary
        if (report.jniFunctions.isNotEmpty() || report.symbols.isNotEmpty()) {
          Text(
            text = "Sugerencias del binario analizado:",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            report.jniFunctions.take(3).forEach { jni ->
              Surface(
                color = Color(0xFF1B2430),
                shape = RoundedCornerShape(4.dp),
                onClick = { onTargetSymbolNameChange(jni.rawSymbol) }
              ) {
                Text(
                  text = jni.methodName,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                  color = CyberCyan,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
            report.symbols.filter { it.isExported }.take(3).forEach { sym ->
              Surface(
                color = Color(0xFF1B2430),
                shape = RoundedCornerShape(4.dp),
                onClick = { onTargetSymbolNameChange(sym.name) }
              ) {
                Text(
                  text = sym.name,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                  color = AccentMint,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // 2. onEnter Parameters
      Text(
        text = "2. Parámetros de Entrada (onEnter)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = AccentMint
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Registrar entrada con console.log", fontSize = 13.sp)
        Switch(
          checked = logOnEnter,
          onCheckedChange = onLogOnEnterChange,
          colors = SwitchDefaults.colors(checkedThumbColor = AccentMint, checkedTrackColor = AccentMint.copy(alpha = 0.4f))
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Pila de Llamadas (Thread.backtrace)", fontSize = 13.sp)
        Switch(
          checked = logBacktrace,
          onCheckedChange = onLogBacktraceChange,
          colors = SwitchDefaults.colors(checkedThumbColor = AccentMint, checkedTrackColor = AccentMint.copy(alpha = 0.4f))
        )
      }

      // Format for Argument 0
      Text("Formato para args[0]:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        ArgFormat.entries.forEach { fmt ->
          FilterChip(
            selected = arg0Format == fmt,
            onClick = { onArg0FormatChange(fmt) },
            label = { Text(fmt.label, fontSize = 10.sp) }
          )
        }
      }

      // Format for Argument 1
      Text("Formato para args[1]:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        ArgFormat.entries.forEach { fmt ->
          FilterChip(
            selected = arg1Format == fmt,
            onClick = { onArg1FormatChange(fmt) },
            label = { Text(fmt.label, fontSize = 10.sp) }
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // 3. onLeave & Force Return Values
      Text(
        text = "3. Parámetros de Retorno (onLeave & Bypass)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = AccentAmber
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Registrar valor de retorno original", fontSize = 13.sp)
        Switch(
          checked = logOnLeave,
          onCheckedChange = onLogOnLeaveChange,
          colors = SwitchDefaults.colors(checkedThumbColor = AccentAmber, checkedTrackColor = AccentAmber.copy(alpha = 0.4f))
        )
      }

      Text("Modificación de Retorno (retval.replace):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ReturnOverride.entries.forEach { opt ->
          Surface(
            color = if (returnOverride == opt) AccentAmber.copy(alpha = 0.15f) else Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                1.dp,
                if (returnOverride == opt) AccentAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
              ),
            onClick = { onReturnOverrideChange(opt) }
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = opt.label,
                fontSize = 12.sp,
                color = if (returnOverride == opt) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (returnOverride == opt) FontWeight.Bold else FontWeight.Normal
              )
              if (opt.codeSnippet.isNotEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                  text = opt.codeSnippet,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 10.sp,
                  color = Color(0xFFADB5BD)
                )
              }
            }
          }
        }
      }

      if (returnOverride == ReturnOverride.CUSTOM) {
        OutlinedTextField(
          value = customReturnValue,
          onValueChange = onCustomReturnValueChange,
          label = { Text("Valor personalizado (ej. 0x1337 o ptr(0x1))") },
          modifier = Modifier.fillMaxWidth(),
          textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentAmber)
        )
      }
    }
  }

  // Generated Script Output View
  val generatedScript = remember(
    moduleName, hookTargetType, targetOffset, targetSymbolName,
    logOnEnter, logBacktrace, arg0Format, arg1Format, arg2Format,
    logOnLeave, returnOverride, customReturnValue
  ) {
    buildFridaScript(
      moduleName = moduleName,
      targetType = hookTargetType,
      targetOffset = targetOffset,
      targetSymbol = targetSymbolName,
      logOnEnter = logOnEnter,
      logBacktrace = logBacktrace,
      arg0 = arg0Format,
      arg1 = arg1Format,
      arg2 = arg2Format,
      logOnLeave = logOnLeave,
      returnOverride = returnOverride,
      customReturn = customReturnValue
    )
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("generated_frida_script_card"),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1115)),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column {
      // Header with Copy & Share
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF191C22))
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(modifier = Modifier.size(10.dp).background(Color(0xFF00FF66), CircleShape))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "frida_hook.js (Generado en tiempo real)",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFFADB5BD),
            fontWeight = FontWeight.Bold
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Frida Script", generatedScript))
              Toast.makeText(context, "¡Script de Frida copiado al portapapeles!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Script", tint = CyberCyan, modifier = Modifier.size(18.dp))
          }

          IconButton(
            onClick = {
              val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, generatedScript)
              }
              context.startActivity(Intent.createChooser(intent, "Compartir Script de Frida"))
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(Icons.Default.Share, contentDescription = "Compartir", tint = CyberCyan, modifier = Modifier.size(18.dp))
          }
        }
      }

      // Script Text Box
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp)
          .horizontalScroll(rememberScrollState())
      ) {
        Text(
          text = generatedScript,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = Color(0xFF00FF88),
          lineHeight = 17.sp
        )
      }

      // Quick CLI invocation snippet
      Surface(
        color = Color(0xFF14171D),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "$ frida -U -f <app.package> -l frida_hook.js",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color(0xFFADB5BD),
            modifier = Modifier.weight(1f)
          )
          Button(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Frida Command", "frida -U -f com.example.app -l frida_hook.js"))
              Toast.makeText(context, "Comando CLI copiado", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(28.dp)
          ) {
            Text("Copiar CLI", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

private fun buildFridaScript(
  moduleName: String,
  targetType: String,
  targetOffset: String,
  targetSymbol: String,
  logOnEnter: Boolean,
  logBacktrace: Boolean,
  arg0: ArgFormat,
  arg1: ArgFormat,
  arg2: ArgFormat,
  logOnLeave: Boolean,
  returnOverride: ReturnOverride,
  customReturn: String
): String {
  val sb = StringBuilder()
  sb.appendLine("/**")
  sb.appendLine(" * Frida Dynamic Hook Script")
  sb.appendLine(" * Target: $moduleName")
  if (targetType == "OFFSET") {
    sb.appendLine(" * Ghidra Relative Offset: $targetOffset")
  } else {
    sb.appendLine(" * Target Symbol: $targetSymbol")
  }
  sb.appendLine(" */")
  sb.appendLine("'use strict';")
  sb.appendLine("")
  sb.appendLine("Java.perform(function() {")
  sb.appendLine("    console.log('[*] SO Analyzer Frida Agent Initialized');")
  sb.appendLine("    var targetModule = '$moduleName';")
  sb.appendLine("    var baseAddress = Module.findBaseAddress(targetModule);")
  sb.appendLine("")
  sb.appendLine("    if (!baseAddress) {")
  sb.appendLine("        console.log('[!] Modulo ' + targetModule + ' aun no cargado. Esperando dlopen...');")
  sb.appendLine("        // Hook dlopen/android_dlopen_ext para esperar carga dinamica")
  sb.appendLine("        return;")
  sb.appendLine("    }")
  sb.appendLine("    console.log('[+] Modulo ' + targetModule + ' encontrado en: ' + baseAddress);")
  sb.appendLine("")

  if (targetType == "OFFSET") {
    sb.appendLine("    // Calculo de direccion absoluta: Base + Offset de Ghidra")
    sb.appendLine("    var targetFunc = baseAddress.add(ptr('$targetOffset'));")
    sb.appendLine("    console.log('[+] Enganchando offset Ghidra $targetOffset en: ' + targetFunc);")
  } else {
    sb.appendLine("    var targetFunc = Module.findExportByName(targetModule, '$targetSymbol');")
    sb.appendLine("    if (!targetFunc) {")
    sb.appendLine("        console.log('[!] Simbolo $targetSymbol no exportado, buscando por direccion...');")
    sb.appendLine("        return;")
    sb.appendLine("    }")
    sb.appendLine("    console.log('[+] Enganchando simbolo $targetSymbol en: ' + targetFunc);")
  }

  sb.appendLine("")
  sb.appendLine("    Interceptor.attach(targetFunc, {")
  sb.appendLine("        onEnter: function(args) {")
  if (logOnEnter) {
    sb.appendLine("            console.log('[>>>] Invocando funcion en ' + this.context.pc);")
  }

  // Arg 0
  when (arg0) {
    ArgFormat.NONE -> {}
    ArgFormat.POINTER -> sb.appendLine("            console.log('    args[0] (ptr): ' + args[0]);")
    ArgFormat.STRING_UTF8 -> sb.appendLine("            try { console.log('    args[0] (str): ' + Memory.readUtf8String(args[0])); } catch(e) { console.log('    args[0]: ' + args[0]); }")
    ArgFormat.INT32 -> sb.appendLine("            console.log('    args[0] (int): ' + args[0].toInt32());")
    ArgFormat.HEXDUMP -> sb.appendLine("            try { console.log('    args[0] hexdump:\\n' + hexdump(args[0], { length: 64, header: true })); } catch(e) {}")
  }

  // Arg 1
  when (arg1) {
    ArgFormat.NONE -> {}
    ArgFormat.POINTER -> sb.appendLine("            console.log('    args[1] (ptr): ' + args[1]);")
    ArgFormat.STRING_UTF8 -> sb.appendLine("            try { console.log('    args[1] (str): ' + Memory.readUtf8String(args[1])); } catch(e) { console.log('    args[1]: ' + args[1]); }")
    ArgFormat.INT32 -> sb.appendLine("            console.log('    args[1] (int): ' + args[1].toInt32());")
    ArgFormat.HEXDUMP -> sb.appendLine("            try { console.log('    args[1] hexdump:\\n' + hexdump(args[1], { length: 64, header: true })); } catch(e) {}")
  }

  // Arg 2
  when (arg2) {
    ArgFormat.NONE -> {}
    ArgFormat.POINTER -> sb.appendLine("            console.log('    args[2] (ptr): ' + args[2]);")
    ArgFormat.STRING_UTF8 -> sb.appendLine("            try { console.log('    args[2] (str): ' + Memory.readUtf8String(args[2])); } catch(e) {}")
    ArgFormat.INT32 -> sb.appendLine("            console.log('    args[2] (int): ' + args[2].toInt32());")
    ArgFormat.HEXDUMP -> sb.appendLine("            try { console.log('    args[2] hexdump:\\n' + hexdump(args[2], { length: 64, header: true })); } catch(e) {}")
  }

  if (logBacktrace) {
    sb.appendLine("            console.log('    Call Stack:\\n' + Thread.backtrace(this.context, Backtracer.ACCURATE).map(DebugSymbol.fromAddress).join('\\n'));")
  }

  sb.appendLine("        },")
  sb.appendLine("        onLeave: function(retval) {")
  if (logOnLeave) {
    sb.appendLine("            console.log('[<<<] Retorno original: ' + retval);")
  }

  when (returnOverride) {
    ReturnOverride.NONE -> {}
    ReturnOverride.FORCE_ONE -> {
      sb.appendLine("            // BYPASS ACTIVADO: Forzar retorno 1")
      sb.appendLine("            retval.replace(1);")
      sb.appendLine("            console.log('    [!] Retorno modificado por Frida a: 1 (Exito)');")
    }
    ReturnOverride.FORCE_ZERO -> {
      sb.appendLine("            // BYPASS ACTIVADO: Forzar retorno 0")
      sb.appendLine("            retval.replace(0);")
      sb.appendLine("            console.log('    [!] Retorno modificado por Frida a: 0 (Falso)');")
    }
    ReturnOverride.FORCE_MINUS_ONE -> {
      sb.appendLine("            retval.replace(-1);")
      sb.appendLine("            console.log('    [!] Retorno modificado por Frida a: -1 (Error)');")
    }
    ReturnOverride.FORCE_NULL -> {
      sb.appendLine("            retval.replace(ptr(0));")
      sb.appendLine("            console.log('    [!] Retorno modificado por Frida a: NULL');")
    }
    ReturnOverride.CUSTOM -> {
      sb.appendLine("            retval.replace($customReturn);")
      sb.appendLine("            console.log('    [!] Retorno modificado a: $customReturn');")
    }
  }

  sb.appendLine("        }")
  sb.appendLine("    });")
  sb.appendLine("});")
  return sb.toString()
}

@Composable
fun GhidraExporterSection(context: Context) {
  val scriptContent = remember {
    """# export_to_frida.py
# Ghidra Script: Export Function Offsets & Signatures to JSON for Frida Interception
# Compatible with Ghidra GUI (Script Manager) and Headless Analyzer

import json
import os
from ghidra.app.decompiler import DecompInterface
from ghidra.util.task import ConsoleTaskMonitor

def run():
    program = currentProgram
    func_manager = program.getFunctionManager()
    base_addr = program.getImageBase().getOffset()
    module_name = program.getName()

    print("[+] Exportando funciones de %s (Base: 0x%x)" % (module_name, base_addr))
    exported_functions = []
    
    for func in func_manager.getFunctions(True):
        entry_addr = func.getEntryPoint().getOffset()
        rel_offset = entry_addr - base_addr
        func_name = func.getName()
        is_jni = func_name.startswith("Java_")

        params = [{"name": p.getName(), "type": str(p.getDataType().getName())} for p in func.getParameters()]
        sig = func.getSignature().getPrototypeString()

        exported_functions.append({
            "name": func_name,
            "offset": "0x{:x}".format(rel_offset),
            "signature": sig,
            "return_type": str(func.getReturnType().getName()),
            "parameters": params,
            "is_jni": is_jni,
            "xrefs_count": len(func.getSymbol().getReferences())
        })

    out_file = os.path.expanduser("~/ghidra_frida_export.json")
    with open(out_file, "w") as f:
        json.dump({"module_name": module_name, "image_base": "0x{:x}".format(base_addr), "functions": exported_functions}, f, indent=2)

    print("[✓] Guardado en: %s (%d funciones)" % (out_file, len(exported_functions)))

run()"""
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = "Script de Exportación para Ghidra (export_to_frida.py)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = CyberCyan
      )

      Text(
        text = "Ejecuta este script dentro de Ghidra en tu PC para extraer automáticamente todos los offsets relativos, nombres de funciones, tipos de retorno y prototipos en formato JSON.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // Instructions
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131922)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Pasos de instalación en Ghidra:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentMint)
          Text("1. En Ghidra: abre el menú Window -> Script Manager.", fontSize = 11.sp, color = Color(0xFFADB5BD))
          Text("2. Haz clic en 'New Script' (icono de cruz verde), selecciona Python y ponle de nombre 'export_to_frida.py'.", fontSize = 11.sp, color = Color(0xFFADB5BD))
          Text("3. Pega el código de abajo y pulsa 'Run Script' (icono de Play).", fontSize = 11.sp, color = Color(0xFFADB5BD))
          Text("4. Se generará el archivo 'ghidra_frida_export.json' que puedes importar directamente en esta app.", fontSize = 11.sp, color = Color(0xFFADB5BD))
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("export_to_frida.py", scriptContent))
            Toast.makeText(context, "¡export_to_frida.py copiado al portapapeles!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copiar export_to_frida.py", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }

      // Code preview
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1115)),
        shape = RoundedCornerShape(8.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .horizontalScroll(rememberScrollState())
        ) {
          Text(
            text = scriptContent,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF00FF66),
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}

@Composable
fun GhidraImporterSection(
  functions: List<GhidraFunctionItem>,
  onSelectFunction: (GhidraFunctionItem) -> Unit,
  onImportJson: (List<GhidraFunctionItem>) -> Unit,
  context: Context
) {
  var pasteText by remember { mutableStateOf("") }
  var isPasting by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Funciones Analizadas por Ghidra (${functions.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = AccentMint
        )

        Button(
          onClick = { isPasting = !isPasting },
          colors = ButtonDefaults.buttonColors(containerColor = if (isPasting) AccentRose else AccentMint),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (isPasting) "Cancelar" else "Pegar JSON", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      }

      if (isPasting) {
        OutlinedTextField(
          value = pasteText,
          onValueChange = { pasteText = it },
          label = { Text("Pega el JSON exportado por Ghidra...") },
          modifier = Modifier.fillMaxWidth().height(140.dp),
          textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentMint)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              if (pasteText.isNotBlank()) {
                val parsed = parseGhidraJsonSimple(pasteText)
                if (parsed.isNotEmpty()) {
                  onImportJson(parsed)
                  isPasting = false
                  pasteText = ""
                } else {
                  Toast.makeText(context, "No se encontraron funciones en el JSON proporcionado", Toast.LENGTH_SHORT).show()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text("Procesar JSON", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = {
              // Load realistic demo Ghidra JSON
              val demo = listOf(
                GhidraFunctionItem("check_tampering", "0x00012e80", "int check_tampering(char* apk_path)", "int", false, 3),
                GhidraFunctionItem("anti_debug_ptrace", "0x00018f40", "void anti_debug_ptrace()", "void", false, 1),
                GhidraFunctionItem("Java_com_bank_App_verifyBiometrics", "0x00029b30", "jboolean Java_com_bank_App_verifyBiometrics(JNIEnv* env, jobject obj)", "jboolean", true, 5),
                GhidraFunctionItem("decrypt_payload", "0x0003c720", "char* decrypt_payload(char* key, int len)", "char*", false, 7)
              )
              onImportJson(demo)
              isPasting = false
            },
            shape = RoundedCornerShape(6.dp)
          ) {
            Text("Cargar Ejemplo de Ghidra", fontSize = 11.sp)
          }
        }
      }

      Text(
        text = "Toca cualquier función para cargarla instantáneamente en el generador de Frida:",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        functions.forEach { fn ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(8.dp),
            onClick = { onSelectFunction(fn) }
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Surface(
                    color = if (fn.isJni) CyberCyan.copy(alpha = 0.2f) else AccentMint.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(
                      text = if (fn.isJni) "JNI EXPORT" else "INTERNAL C/C++",
                      fontFamily = FontFamily.Monospace,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (fn.isJni) CyberCyan else AccentMint,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = fn.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }

                Surface(
                  color = AccentAmber.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(
                    text = fn.offset,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAmber,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              Text(
                text = fn.signature,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF8A99AD)
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Retorno: ${fn.returnType}", fontSize = 10.sp, color = AccentMint)
                Text("XREFs: ${fn.xrefs}", fontSize = 10.sp, color = Color(0xFFADB5BD))
                Text("Enganchar con Frida ➔", fontSize = 10.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}

private fun parseGhidraJsonSimple(jsonStr: String): List<GhidraFunctionItem> {
  val result = mutableListOf<GhidraFunctionItem>()
  try {
    val jsonObject = org.json.JSONObject(jsonStr)
    val arr = jsonObject.optJSONArray("functions")
    if (arr != null) {
      for (i in 0 until arr.length()) {
        val f = arr.getJSONObject(i)
        result.add(
          GhidraFunctionItem(
            name = f.optString("name", "func_$i"),
            offset = f.optString("offset", "0x0"),
            signature = f.optString("signature", "void func()"),
            returnType = f.optString("return_type", "void"),
            isJni = f.optBoolean("is_jni", false),
            xrefs = f.optInt("xrefs_count", 1)
          )
        )
      }
    }
  } catch (e: Exception) {
    // try array directly
    try {
      val arr = org.json.JSONArray(jsonStr)
      for (i in 0 until arr.length()) {
        val f = arr.getJSONObject(i)
        result.add(
          GhidraFunctionItem(
            name = f.optString("name", "func_$i"),
            offset = f.optString("offset", "0x0"),
            signature = f.optString("signature", "void func()"),
            returnType = f.optString("return_type", "void"),
            isJni = f.optBoolean("is_jni", false),
            xrefs = f.optInt("xrefs_count", 1)
          )
        )
      }
    } catch (e2: Exception) {
      // ignore
    }
  }
  return result
}

@Composable
fun BypassTemplatesSection(context: Context) {
  val templates = listOf(
    Pair(
      "1. Bypass Detección de Root (stat, access, fopen)",
      """// Universal Native Root Detection Bypass
Java.perform(function() {
    console.log('[*] Instalando Bypass Universal de Root...');
    var libc = 'libc.so';
    var bannedPaths = ['/system/bin/su', '/system/xbin/su', '/sbin/su', '/system/app/Superuser.apk', 'magisk'];

    ['fopen', 'stat', 'access'].forEach(function(fnName) {
        var p = Module.findExportByName(libc, fnName);
        if (p) {
            Interceptor.attach(p, {
                onEnter: function(args) {
                    var path = Memory.readUtf8String(args[0]);
                    if (path) {
                        for (var i = 0; i < bannedPaths.length; i++) {
                            if (path.indexOf(bannedPaths[i]) !== -1) {
                                console.log('[!] Bloqueando chequeo de root: ' + path);
                                args[0] = Memory.allocUtf8String('/system/does_not_exist');
                                break;
                            }
                        }
                    }
                }
            });
        }
    });
});"""
    ),
    Pair(
      "2. Bypass Anti-Frida & Anti-Debugging (ptrace & TracerPid)",
      """// Anti-Debugging & Anti-Frida Bypass
Java.perform(function() {
    console.log('[*] Instalando Bypass de Anti-Debugging...');
    var ptracePtr = Module.findExportByName('libc.so', 'ptrace');
    if (ptracePtr) {
        Interceptor.attach(ptracePtr, {
            onEnter: function(args) {
                var request = args[0].toInt32();
                if (request === 0) { // PTRACE_TRACEME = 0
                    console.log('[!] Interceptado ptrace(PTRACE_TRACEME), simulando exito.');
                }
            },
            onLeave: function(retval) {
                retval.replace(0); // Forzar exito
            }
        });
    }
});"""
    ),
    Pair(
      "3. Bypass SSL Pinning (OpenSSL / BoringSSL)",
      """// Native SSL Pinning Bypass (BoringSSL SSL_set_verify)
Java.perform(function() {
    console.log('[*] Desactivando verificacion de certificados SSL...');
    var sslSetVerify = Module.findExportByName(null, 'SSL_set_verify');
    if (sslSetVerify) {
        Interceptor.attach(sslSetVerify, {
            onEnter: function(args) {
                console.log('[+] SSL_set_verify llamado. Modificando modo a SSL_VERIFY_NONE (0)');
                args[1] = ptr(0); // SSL_VERIFY_NONE
            }
        });
    }
});"""
    ),
    Pair(
      "4. Rastreador Dinámico de JNI (RegisterNatives Tracer)",
      """// JNI RegisterNatives Automatic Function Discovery
Java.perform(function() {
    console.log('[*] Rastreador de JNI RegisterNatives Activo...');
    var env = Java.vm.getEnv();
    var handle = env.handle;
    var registerNativesAddr = Memory.readPointer(handle.add(215 * Process.pointerSize)); // RegisterNatives index

    Interceptor.attach(registerNativesAddr, {
        onEnter: function(args) {
            var jclass = args[1];
            var methods = args[2];
            var nMethods = args[3].toInt32();
            var className = Java.vm.tryGetEnv().getClassName(jclass);
            console.log('[+] RegisterNatives en clase: ' + className + ' (' + nMethods + ' metodos)');

            for (var i = 0; i < nMethods; i++) {
                var namePtr = Memory.readPointer(methods.add(i * 3 * Process.pointerSize));
                var sigPtr = Memory.readPointer(methods.add((i * 3 + 1) * Process.pointerSize));
                var fnPtr = Memory.readPointer(methods.add((i * 3 + 2) * Process.pointerSize));
                console.log('    -> ' + Memory.readCString(namePtr) + ' ' + Memory.readCString(sigPtr) + ' en ' + fnPtr);
            }
        }
    });
});"""
    )
  )

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    templates.forEach { (title, code) ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = CyberCyan
            )

            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(title, code))
                Toast.makeText(context, "Plantilla copiada al portapapeles", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = CyberCyan, modifier = Modifier.size(16.dp))
            }
          }

          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1115)),
            shape = RoundedCornerShape(8.dp)
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .horizontalScroll(rememberScrollState())
            ) {
              Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF00FF66),
                lineHeight = 15.sp
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun FridaExecutionGuideSection(report: ElfInfo, context: Context) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = "Guía de Ejecución de Frida en Android",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = CyberCyan
      )

      // Rooted Setup
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131922)),
        shape = RoundedCornerShape(10.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Opción A: Dispositivo con Root (Magisk / KernelSU / Emulador)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentMint)
          Text(
            text = "1. Descarga 'frida-server' coincidente con la arquitectura de tu teléfono (${report.abiName}):\n" +
              "   https://github.com/frida/frida/releases\n\n" +
              "2. Envíalo al dispositivo mediante ADB:\n" +
              "   adb push frida-server /data/local/tmp/\n" +
              "   adb shell \"chmod 755 /data/local/tmp/frida-server\"\n" +
              "   adb shell \"su -c /data/local/tmp/frida-server &\"\n\n" +
              "3. Ejecuta el script generado con Frida:\n" +
              "   frida -U -f <com.nombre.paquete> -l frida_hook.js",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFFADB5BD),
            lineHeight = 16.sp
          )

          Button(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Root Commands", "adb push frida-server /data/local/tmp/ && adb shell \"chmod 755 /data/local/tmp/frida-server && su -c /data/local/tmp/frida-server &\""))
              Toast.makeText(context, "Comandos ADB de frida-server copiados", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
            shape = RoundedCornerShape(6.dp)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copiar Comandos ADB frida-server", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      // Non-Root Setup
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131922)),
        shape = RoundedCornerShape(10.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Opción B: Dispositivo Sin Root (Frida Gadget)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
          Text(
            text = "Si tu teléfono no tiene Root, puedes inyectar 'frida-gadget.so' dentro del APK:\n\n" +
              "1. Desempaqueta el APK con apktool:\n" +
              "   apktool d app.apk\n" +
              "2. Copia 'frida-gadget.so' en la carpeta lib/${report.abiName}/\n" +
              "3. Agrega en el Smali del MainActivity: System.loadLibrary(\"frida-gadget\")\n" +
              "4. Reempaqueta y firma el APK:\n" +
              "   objection patchapk --source app.apk --architecture ${if (report.is64Bit) "arm64" else "arm"}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFFADB5BD),
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}
