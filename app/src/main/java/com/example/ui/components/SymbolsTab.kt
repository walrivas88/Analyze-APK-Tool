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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ElfInfo
import com.example.model.ElfSymbol
import com.example.ui.SymbolFilter
import com.example.ui.theme.AccentMint
import com.example.ui.theme.CyberCyan

@Composable
fun SymbolsTab(
  report: ElfInfo,
  searchQuery: String,
  filter: SymbolFilter,
  onSearchChange: (String) -> Unit,
  onFilterChange: (SymbolFilter) -> Unit
) {
  val context = LocalContext.current

  val filteredSymbols = remember(report.symbols, searchQuery, filter) {
    report.symbols.filter { sym ->
      val matchesFilter = when (filter) {
        SymbolFilter.ALL -> true
        SymbolFilter.JNI_ONLY -> sym.isJni
        SymbolFilter.EXPORTED -> sym.isExported
        SymbolFilter.IMPORTED -> sym.isImported
      }
      val matchesSearch = if (searchQuery.isBlank()) {
        true
      } else {
        sym.name.contains(searchQuery, ignoreCase = true) ||
          (sym.demangled?.contains(searchQuery, ignoreCase = true) == true)
      }
      matchesFilter && matchesSearch
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Search Box
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchChange,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("symbol_search_field"),
      placeholder = { Text("Search symbol names...") },
      leadingIcon = {
        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchChange("") }) {
            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(12.dp)
    )

    // Filter Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      SymbolFilter.entries.forEach { item ->
        val count = when (item) {
          SymbolFilter.ALL -> report.symbols.size
          SymbolFilter.JNI_ONLY -> report.symbols.count { it.isJni }
          SymbolFilter.EXPORTED -> report.symbols.count { it.isExported }
          SymbolFilter.IMPORTED -> report.symbols.count { it.isImported }
        }
        FilterChip(
          selected = filter == item,
          onClick = { onFilterChange(item) },
          label = { Text("${item.label} ($count)") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
            selectedLabelColor = CyberCyan
          )
        )
      }
    }

    Text(
      text = "Showing ${filteredSymbols.size} of ${report.symbols.size} symbols",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    )

    if (filteredSymbols.isEmpty()) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "No matching symbols found",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Try adjusting your search query or filter selection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(filteredSymbols) { sym ->
          SymbolItemCard(sym = sym, context = context)
        }
      }
    }
  }
}

@Composable
fun SymbolItemCard(sym: ElfSymbol, context: Context) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = sym.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (sym.isJni) AccentMint else MaterialTheme.colorScheme.onSurface
          )
          if (sym.demangled != null && sym.demangled != sym.name) {
            Text(
              text = sym.demangled,
              style = MaterialTheme.typography.bodySmall,
              color = AccentMint.copy(alpha = 0.85f),
              fontFamily = FontFamily.Monospace
            )
          }
        }

        IconButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Symbol", sym.name)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied symbol name", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy Symbol",
            tint = CyberCyan,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          color = if (sym.isExported) AccentMint.copy(alpha = 0.15f) else CyberCyan.copy(alpha = 0.15f),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = if (sym.isExported) "EXPORTED" else "IMPORTED",
            color = if (sym.isExported) AccentMint else CyberCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }

        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "${sym.binding} / ${sym.type}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
          text = "Val: ${sym.valueHex}",
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
      }
    }
  }
}
