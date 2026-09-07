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
import com.example.model.StringInfo
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import com.example.ui.theme.CyberCyan

@Composable
fun StringsTab(
  report: ElfInfo,
  searchQuery: String,
  categoryFilter: String,
  onSearchChange: (String) -> Unit,
  onCategoryChange: (String) -> Unit
) {
  val context = LocalContext.current

  val categories = listOf("All", "URL", "System Path", "Android/JVM Class", "Shared Library", "General String")

  val filteredStrings = remember(report.extractedStrings, searchQuery, categoryFilter) {
    report.extractedStrings.filter { str ->
      val matchesCat = categoryFilter == "All" || str.category == categoryFilter
      val matchesSearch = searchQuery.isBlank() || str.value.contains(searchQuery, ignoreCase = true)
      matchesCat && matchesSearch
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchChange,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("string_search_field"),
      placeholder = { Text("Search extracted strings...") },
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

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      categories.take(4).forEach { cat ->
        FilterChip(
          selected = categoryFilter == cat,
          onClick = { onCategoryChange(cat) },
          label = { Text(cat) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
            selectedLabelColor = CyberCyan
          )
        )
      }
    }

    Text(
      text = "Showing ${filteredStrings.size} of ${report.extractedStrings.size} extracted strings",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    )

    if (filteredStrings.isEmpty()) {
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
            text = "No matching strings found",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(filteredStrings) { item ->
          StringItemCard(item = item, context = context)
        }
      }
    }
  }
}

@Composable
fun StringItemCard(item: StringInfo, context: Context) {
  val badgeColor = when (item.category) {
    "URL" -> CyberCyan
    "System Path" -> AccentAmber
    "Android/JVM Class" -> AccentMint
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Surface(
          color = badgeColor.copy(alpha = 0.15f),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = item.category,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = item.value,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace
        )
      }

      IconButton(
        onClick = {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          val clip = ClipData.newPlainText("Extracted String", item.value)
          clipboard.setPrimaryClip(clip)
          Toast.makeText(context, "String copied to clipboard", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.size(32.dp)
      ) {
        Icon(
          imageVector = Icons.Default.ContentCopy,
          contentDescription = "Copy String",
          tint = CyberCyan,
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}
