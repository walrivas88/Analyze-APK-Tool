package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.model.SecurityFeatureStatus
import com.example.model.StatusLevel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CyberCyan

@Composable
fun OverviewTab(
  report: ElfInfo,
  onOpenPatchLab: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(scrollState)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Binary Patch Lab Quick Launch Card
    if (onOpenPatchLab != null) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("card_patch_lab_banner"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.3f))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = AccentRose, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("APK Binary Patch Lab", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentRose)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              "Inyecta parches ARM64 (NOP, RET, licencias, anti-root) y reempaqueta la APK en target.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Button(
            onClick = { onOpenPatchLab() },
            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text("Abrir Lab", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
    // 1. Architecture & Machine Info
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("overview_arch_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = "Architecture",
            tint = CyberCyan,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Architecture & Binary Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SuggestionChip(
            onClick = {},
            label = { Text(report.abiName, fontWeight = FontWeight.Bold) },
            colors = SuggestionChipDefaults.suggestionChipColors(
              containerColor = CyberCyan.copy(alpha = 0.15f),
              labelColor = CyberCyan
            )
          )
          SuggestionChip(
            onClick = {},
            label = { Text(if (report.is64Bit) "64-bit ELF" else "32-bit ELF") }
          )
          SuggestionChip(
            onClick = {},
            label = { Text(report.endianness.substringBefore(" ")) }
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        InfoRow(label = "Target Architecture", value = report.machine)
        InfoRow(label = "File Type", value = report.fileType)
        InfoRow(label = "OS / ABI", value = report.osAbi)
        InfoRow(label = "Entry Point Address", value = report.entryPointHex, isMono = true)
        InfoRow(label = "Program Headers", value = "${report.programHeaderCount} entries")
        InfoRow(label = "Section Headers", value = "${report.sectionHeaderCount} entries")
        if (report.soname != null) {
          InfoRow(label = "Internal SONAME", value = report.soname)
        }
      }
    }

    // 2. Security Hardening Audit
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("overview_security_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = "Security Audit",
              tint = AccentMint,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Security Audit",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          Surface(
            color = AccentMint.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(
              text = report.securityReport.overallVerdict,
              color = AccentMint,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SecurityFeatureRow(report.securityReport.pieStatus)
        SecurityFeatureRow(report.securityReport.nxStatus)
        SecurityFeatureRow(report.securityReport.relroStatus)
        SecurityFeatureRow(report.securityReport.canaryStatus)
        SecurityFeatureRow(report.securityReport.strippedStatus)
      }
    }

    // 3. Hashes & Checksums
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("overview_hashes_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "File Checksums & Hashes",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        HashItem(title = "SHA-256", hash = report.sha256, context = context)
        HashItem(title = "SHA-1", hash = report.sha1, context = context)
        HashItem(title = "MD5", hash = report.md5, context = context)
      }
    }

    // 4. Compatibility Summary Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("overview_compatibility_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Compatibility",
            tint = CyberCyan,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Device Compatibility Verdict",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = report.compatibilitySummary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun InfoRow(label: String, value: String, isMono: Boolean = false) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
    )
  }
}

@Composable
fun SecurityFeatureRow(feature: SecurityFeatureStatus) {
  val (icon, tint) = when (feature.status) {
    StatusLevel.PASS -> Pair(Icons.Default.CheckCircle, AccentMint)
    StatusLevel.WARNING -> Pair(Icons.Default.Warning, AccentAmber)
    StatusLevel.FAIL -> Pair(Icons.Default.Warning, AccentRose)
    StatusLevel.NEUTRAL -> Pair(Icons.Default.Info, CyberCyan)
  }

  Column(modifier = Modifier.padding(vertical = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = feature.status.name,
        tint = tint,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = feature.name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = tint
      )
    }
    Text(
      text = feature.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
      modifier = Modifier.padding(start = 26.dp, top = 2.dp)
    )
  }
}

@Composable
fun HashItem(title: String, hash: String, context: Context) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
      )
      Text(
        text = hash,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = 1
      )
    }
    IconButton(
      onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(title, hash)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$title copied to clipboard", Toast.LENGTH_SHORT).show()
      },
      modifier = Modifier.size(36.dp)
    ) {
      Icon(
        imageVector = Icons.Default.ContentCopy,
        contentDescription = "Copy $title",
        tint = CyberCyan,
        modifier = Modifier.size(16.dp)
      )
    }
  }
}
