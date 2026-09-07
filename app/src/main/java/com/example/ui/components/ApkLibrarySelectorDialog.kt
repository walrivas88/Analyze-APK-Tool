package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.apk.BundledSoEntry
import com.example.apk.InstalledAppItem
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import com.example.ui.theme.CyberCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkLibraryPickerSheet(
  apkName: String,
  libraries: List<BundledSoEntry>,
  onSelectLibrary: (BundledSoEntry) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.FolderZip, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Librerías Nativas en el APK",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "$apkName (${libraries.size} archivos .so detectados)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Cerrar")
        }
      }

      Text(
        text = "Selecciona qué biblioteca .so deseas desensamblar y auditar:",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(380.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(libraries) { entry ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("apk_so_item_${entry.name}")
              .clickable { onSelectLibrary(entry) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(10.dp)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .background(if (entry.isPrimaryAbi) AccentMint.copy(alpha = 0.15f) else Color(0xFF1E2632), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.Memory,
                  contentDescription = null,
                  tint = if (entry.isPrimaryAbi) AccentMint else CyberCyan,
                  modifier = Modifier.size(18.dp)
                )
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = entry.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                  )
                  if (entry.isPrimaryAbi) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                      color = AccentMint.copy(alpha = 0.2f),
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        text = "ARM64",
                        color = AccentMint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${entry.path} • ${entry.sizeFormatted}",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Surface(
                color = CyberCyan,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "Analizar",
                  color = Color.Black,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsBrowserSheet(
  apps: List<InstalledAppItem>,
  isLoading: Boolean,
  onSelectApp: (InstalledAppItem) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var searchQuery by remember { mutableStateOf("") }
  var filterOnlyWithLibs by remember { mutableStateOf(false) }

  val filteredApps = remember(apps, searchQuery, filterOnlyWithLibs) {
    apps.filter { app ->
      val matchesQuery = searchQuery.isBlank() ||
        app.appName.contains(searchQuery, ignoreCase = true) ||
        app.packageName.contains(searchQuery, ignoreCase = true)
      val matchesLibFilter = !filterOnlyWithLibs || app.nativeLibDir.isNotEmpty()
      matchesQuery && matchesLibFilter
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .background(AccentMint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Android, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Aplicaciones Instaladas en el Teléfono",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${apps.size} aplicaciones disponibles para inspeccionar",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Cerrar")
        }
      }

      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Buscar aplicación o paquete...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("search_installed_apps"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = CyberCyan,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        singleLine = true
      )

      if (isLoading) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = CyberCyan)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Escaneando aplicaciones instaladas...", fontSize = 12.sp)
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(440.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredApps) { app ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("app_item_${app.packageName}")
                .clickable { onSelectApp(app) },
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(10.dp)
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                // App Icon
                val bitmap = remember(app.packageName) {
                  try {
                    app.icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
                  } catch (_: Exception) {
                    null
                  }
                }
                if (bitmap != null) {
                  Image(
                    bitmap = bitmap,
                    contentDescription = app.appName,
                    modifier = Modifier.size(36.dp)
                  )
                } else {
                  Icon(Icons.Default.Android, contentDescription = null, tint = AccentMint, modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = app.appName,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp
                    )
                    if (app.isSystemApp) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Surface(
                        color = Color(0xFF2A2D34),
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Text(
                          text = "Sistema",
                          fontSize = 9.sp,
                          color = Color(0xFFADB5BD),
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                      }
                    }
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = app.packageName,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  if (app.nativeLibDir.isNotEmpty()) {
                    Text(
                      text = "Librerías: ${app.nativeLibDir}",
                      fontSize = 9.sp,
                      color = AccentMint
                    )
                  }
                }

                Surface(
                  color = AccentMint.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(6.dp),
                  modifier = Modifier.border(1.dp, AccentMint.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                ) {
                  Text(
                    text = "Explorar .so",
                    color = AccentMint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
    }
  }
}
