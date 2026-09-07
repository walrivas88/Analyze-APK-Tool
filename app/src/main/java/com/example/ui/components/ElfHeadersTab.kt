package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elf.ElfHeaderExtractor
import com.example.ui.theme.AccentMint
import com.example.ui.theme.CyberCyan

@Composable
fun ElfHeadersTab(result: ElfHeaderExtractor.ExtractionResult) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(scrollState)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Key Header Facts
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("binutils_facts_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.DataObject,
            contentDescription = null,
            tint = CyberCyan,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "ELF Header Summary (binutils logic)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        InfoRow(label = "Machine Architecture", value = result.header.machineArchitecture)
        InfoRow(label = "ABI Identification", value = result.header.abiName)
        InfoRow(label = "Entry Point Address", value = result.header.entryPointHex, isMono = true)
        InfoRow(label = "ELF Class", value = result.header.elfClass)
        InfoRow(label = "Data / Endianness", value = result.header.dataEncoding)
        InfoRow(label = "OS / ABI", value = result.header.osAbi)
        InfoRow(label = "File Type", value = result.header.fileType)
        InfoRow(label = "Header Size", value = "${result.header.headerSize} bytes")
        InfoRow(label = "Program Headers Offset", value = "${result.header.programHeaderOffset} (count: ${result.header.programHeaderCount})")
        InfoRow(label = "Section Headers Offset", value = "${result.header.sectionHeaderOffset} (count: ${result.header.sectionHeaderCount})")
        InfoRow(label = "String Table Index (.shstrtab)", value = "[${result.header.sectionHeaderStringTableIndex}]")
      }
    }

    // 2. GNU binutils "readelf -h" console output
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("readelf_h_card"),
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
              imageVector = Icons.Default.Terminal,
              contentDescription = null,
              tint = AccentMint,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "GNU readelf -h Output",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = AccentMint
            )
          }

          IconButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val clip = ClipData.newPlainText("readelf -h", result.readelfHeaderOutput)
              clipboard.setPrimaryClip(clip)
              Toast.makeText(context, "Copied readelf -h output", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy readelf -h",
              tint = CyberCyan,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          val hScroll = rememberScrollState()
          Text(
            text = result.readelfHeaderOutput,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
              .padding(12.dp)
              .horizontalScroll(hScroll)
          )
        }
      }
    }

    // 3. GNU binutils "readelf -S" console output
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("readelf_s_card"),
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
              imageVector = Icons.Default.Terminal,
              contentDescription = null,
              tint = CyberCyan,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "GNU readelf -S Output (Section Headers)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = CyberCyan
            )
          }

          IconButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val clip = ClipData.newPlainText("readelf -S", result.readelfSectionsOutput)
              clipboard.setPrimaryClip(clip)
              Toast.makeText(context, "Copied readelf -S output", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy readelf -S",
              tint = CyberCyan,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          val hScroll = rememberScrollState()
          Text(
            text = result.readelfSectionsOutput,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
              .padding(12.dp)
              .horizontalScroll(hScroll)
          )
        }
      }
    }
  }
}
