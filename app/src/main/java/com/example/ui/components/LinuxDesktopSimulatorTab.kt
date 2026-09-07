package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

private const val LINUX_DOWNLOAD_URL = "https://temp.sh/WgttK/so-analyzer-linux.tar.gz"
private const val SOURCE_CODE_URL = "https://temp.sh/jVyCZ/so-analyzer-source.zip"

enum class LinuxViewMode {
  TERMINAL_CLI,
  DESKTOP_GUI,
  INSTALLER_GUIDE
}

@Composable
fun LinuxDesktopSimulatorTab(report: ElfInfo) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()
  var viewMode by remember { mutableStateOf(LinuxViewMode.DESKTOP_GUI) }
  var selectedCommand by remember { mutableStateOf("readelf -h") }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(scrollState)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Mode selector chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(
        selected = viewMode == LinuxViewMode.DESKTOP_GUI,
        onClick = { viewMode = LinuxViewMode.DESKTOP_GUI },
        label = { Text("Linux Desktop GUI", fontSize = 12.sp) },
        leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberCyan.copy(alpha = 0.2f))
      )
      FilterChip(
        selected = viewMode == LinuxViewMode.TERMINAL_CLI,
        onClick = { viewMode = LinuxViewMode.TERMINAL_CLI },
        label = { Text("Linux Terminal (CLI)", fontSize = 12.sp) },
        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentMint.copy(alpha = 0.2f))
      )
      FilterChip(
        selected = viewMode == LinuxViewMode.INSTALLER_GUIDE,
        onClick = { viewMode = LinuxViewMode.INSTALLER_GUIDE },
        label = { Text("Install on Linux", fontSize = 12.sp) },
        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentAmber.copy(alpha = 0.2f))
      )
    }

    when (viewMode) {
      LinuxViewMode.DESKTOP_GUI -> {
        LinuxDesktopWindow(report)
      }
      LinuxViewMode.TERMINAL_CLI -> {
        LinuxTerminalWindow(
          report = report,
          selectedCommand = selectedCommand,
          onSelectCommand = { selectedCommand = it }
        )
      }
      LinuxViewMode.INSTALLER_GUIDE -> {
        LinuxInstallationCard(context)
      }
    }

    // Quick download banner at bottom
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(14.dp)
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Download for Your Linux System",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CyberCyan
          )
          Text(
            text = "Includes so_analyzer_gui.py, install.sh launcher, and full source code.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = {
            try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LINUX_DOWNLOAD_URL))
              context.startActivity(intent)
            } catch (e: Exception) {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Linux URL", LINUX_DOWNLOAD_URL))
              Toast.makeText(context, "URL copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("Get Linux App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
fun LinuxDesktopWindow(report: ElfInfo) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("linux_desktop_window"),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2028)),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column {
      // Linux GTK / Wayland Window Title Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF14171D))
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Window buttons (close, minimize, maximize)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(modifier = Modifier.size(12.dp).background(Color(0xFFFF5F56), CircleShape))
          Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFBD2E), CircleShape))
          Box(modifier = Modifier.size(12.dp).background(Color(0xFF27C93F), CircleShape))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
          text = "SO Analyzer — Linux Desktop (GTK / Tkinter)",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = Color(0xFFADB5BD),
          fontWeight = FontWeight.SemiBold
        )
      }

      // App Header inside the Linux window
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF212732))
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "SO Analyzer",
            color = CyberCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Native Linux ELF Inspector",
            color = Color(0xFF8A99AD),
            fontSize = 11.sp
          )
        }
        Surface(
          color = CyberCyan,
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "📂 Open .so File",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      // Main content body of the desktop app
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // File badge
        Surface(
          color = Color(0xFF252D3A),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Active Target: ${report.fileName} (${report.fileSizeFormatted}, ${report.abiName})",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = AccentMint
            )
          }
        }

        // Security check status row
        Text(
          text = "Linux ELF Hardening Mitigations:",
          fontSize = 11.sp,
          color = Color(0xFFADB5BD),
          fontWeight = FontWeight.Bold
        )

        val sec = report.securityReport
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          SecurityTag("PIE/PIC", sec.pieStatus.status == com.example.model.StatusLevel.PASS)
          SecurityTag("Canary", sec.canaryStatus.status == com.example.model.StatusLevel.PASS)
          SecurityTag("NX Stack", sec.nxStatus.status == com.example.model.StatusLevel.PASS)
          SecurityTag("RELRO", sec.relroStatus.status == com.example.model.StatusLevel.PASS)
        }

        // Section breakdown table
        Text(
          text = "Sections & Segments Preview:",
          fontSize = 11.sp,
          color = Color(0xFFADB5BD),
          fontWeight = FontWeight.Bold
        )

        Surface(
          color = Color(0xFF14171D),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            report.sections.take(4).forEach { secItem ->
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(secItem.name, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = CyberCyan)
                Text(secItem.type, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFADB5BD))
                Text(secItem.sizeFormatted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.White)
              }
            }
          }
        }

        // Status bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14171D))
            .padding(horizontal = 8.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Parsed ${report.sections.size} sections, ${report.symbols.size} dynamic symbols", fontSize = 10.sp, color = Color(0xFF8A99AD))
          Text("Python 3 & Tkinter GUI Ready", fontSize = 10.sp, color = AccentMint)
        }
      }
    }
  }
}

@Composable
fun SecurityTag(label: String, passed: Boolean) {
  Surface(
    color = if (passed) AccentMint.copy(alpha = 0.2f) else AccentRose.copy(alpha = 0.2f),
    shape = RoundedCornerShape(4.dp)
  ) {
    Text(
      text = "${if (passed) "✓" else "✗"} $label",
      color = if (passed) AccentMint else AccentRose,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
  }
}

@Composable
fun LinuxTerminalWindow(
  report: ElfInfo,
  selectedCommand: String,
  onSelectCommand: (String) -> Unit
) {
  val commands = listOf("readelf -h", "readelf -S", "readelf -s", "file", "checksec")

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("linux_terminal_window"),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1115)),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column {
      // Terminal Title Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF191C22))
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(modifier = Modifier.size(11.dp).background(Color(0xFFFF5F56), CircleShape))
          Box(modifier = Modifier.size(11.dp).background(Color(0xFFFFBD2E), CircleShape))
          Box(modifier = Modifier.size(11.dp).background(Color(0xFF27C93F), CircleShape))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
          text = "bash — user@linux: ~/so-analyzer (x86_64)",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = Color(0xFF8A99AD)
        )
      }

      // Command quick buttons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF14171D))
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        commands.forEach { cmd ->
          Surface(
            color = if (selectedCommand == cmd) CyberCyan else Color(0xFF252D3A),
            shape = RoundedCornerShape(4.dp),
            onClick = { onSelectCommand(cmd) }
          ) {
            Text(
              text = "$ $cmd",
              color = if (selectedCommand == cmd) Color.Black else Color(0xFFADB5BD),
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }

      // Terminal Output Box
      val terminalOutput = remember(selectedCommand, report) {
        generateLinuxTerminalOutput(selectedCommand, report)
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp)
          .horizontalScroll(rememberScrollState())
      ) {
        Text(
          text = terminalOutput,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = Color(0xFF00FF66),
          lineHeight = 18.sp
        )
      }
    }
  }
}

private fun generateLinuxTerminalOutput(command: String, report: ElfInfo): String {
  return when (command) {
    "readelf -h" -> """user@linux:~$ readelf -h ${report.fileName}
ELF Header:
  Magic:   7f 45 4c 46 02 01 01 00 00 00 00 00 00 00 00 00
  Class:                             ${if (report.is64Bit) "ELF64" else "ELF32"}
  Data:                              ${report.endianness}
  Version:                           1 (current)
  OS/ABI:                            ${report.osAbi}
  ABI Version:                       0
  Type:                              ${report.fileType}
  Machine:                           ${report.abiName}
  Version:                           0x1
  Entry point address:               ${report.entryPointHex}
  Start of program headers:          64 (bytes into file)
  Flags:                             0x0
  Size of this header:               ${report.headerSize} (bytes)
  Number of program headers:         ${report.programHeaderCount}
  Number of section headers:         ${report.sectionHeaderCount}"""

    "readelf -S" -> {
      val sb = StringBuilder()
      sb.appendLine("user@linux:~$ readelf -S ${report.fileName}")
      sb.appendLine("There are ${report.sections.size} section headers:")
      sb.appendLine("")
      sb.appendLine("Section Headers:")
      sb.appendLine("  [Nr] Name              Type             Address           Offset")
      sb.appendLine("       Size              Flags")
      report.sections.take(8).forEachIndexed { idx, s ->
        sb.appendLine("  [${idx.toString().padStart(2)}] ${s.name.padEnd(17)} ${s.type.padEnd(16)} ${s.addressHex.padEnd(14)} ${s.offsetHex.padEnd(8)}")
        sb.appendLine("       ${s.sizeFormatted.padEnd(14)}   ${s.flags}")
      }
      sb.toString()
    }

    "readelf -s" -> {
      val sb = StringBuilder()
      sb.appendLine("user@linux:~$ readelf -s ${report.fileName}")
      sb.appendLine("Symbol table '.dynsym' contains ${report.symbols.size} entries:")
      sb.appendLine("   Num:    Value          Type    Bind   Name")
      report.symbols.take(10).forEachIndexed { idx, sym ->
        sb.appendLine("   ${idx.toString().padStart(3)}: ${sym.valueHex.padEnd(14)} ${sym.type.padEnd(7)} ${sym.binding.padEnd(6)} ${sym.name}")
      }
      sb.toString()
    }

    "file" -> """user@linux:~$ file ${report.fileName}
${report.fileName}: ELF ${if (report.is64Bit) "64-bit" else "32-bit"} LSB shared object, ${report.abiName}, version 1 (SYSV), dynamically linked, interpreter /system/bin/linker64, BuildID[sha1]=83a0f12..., stripped"""

    "checksec" -> """user@linux:~$ checksec --file=${report.fileName}
[*] '${report.fileName}'
    Arch:     ${if (report.is64Bit) "aarch64-64-little" else "arm-32-little"}
    RELRO:    ${if (report.securityReport.relroStatus.status == com.example.model.StatusLevel.PASS) "Full RELRO" else "No RELRO"}
    Stack:    ${if (report.securityReport.canaryStatus.status == com.example.model.StatusLevel.PASS) "Canary found" else "No canary found"}
    NX:       ${if (report.securityReport.nxStatus.status == com.example.model.StatusLevel.PASS) "NX enabled" else "NX disabled"}
    PIE:      ${if (report.securityReport.pieStatus.status == com.example.model.StatusLevel.PASS) "PIE enabled" else "No PIE"}
    RUNPATH:  No RUNPATH
    RPATH:    No RPATH"""

    else -> "user@linux:~$ $command\nCommand not found."
  }
}

@Composable
fun LinuxInstallationCard(context: Context) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("linux_installation_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = "How to Install on Your Linux Machine",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = AccentAmber
      )

      Text(
        text = "1. Download the archive onto your Linux computer:\n" +
          "   wget $LINUX_DOWNLOAD_URL\n\n" +
          "2. Extract the package:\n" +
          "   tar -xzvf so-analyzer-linux.tar.gz\n" +
          "   cd linux-app\n\n" +
          "3. Run immediately:\n" +
          "   ./run.sh\n\n" +
          "4. Install into your Linux application launcher:\n" +
          "   ./install.sh\n" +
          "   (SO Analyzer will appear in your GNOME/KDE Application Menu!)",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 18.sp
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Linux Command", "tar -xzvf so-analyzer-linux.tar.gz && cd linux-app && ./run.sh"))
            Toast.makeText(context, "Command copied!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy Install Command", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
