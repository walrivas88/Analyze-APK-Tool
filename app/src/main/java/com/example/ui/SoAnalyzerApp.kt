package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.elf.ElfHeaderExtractor
import com.example.model.ElfInfo
import com.example.apk.BundledSoEntry
import com.example.apk.InstalledAppItem
import com.example.ui.components.ApkBinaryPatchLabContent
import com.example.ui.components.ApkBinaryPatchLabDialog
import com.example.ui.components.ApkInstallGuideTab
import com.example.ui.components.ApkLibraryPickerSheet
import com.example.ui.components.DependenciesJniTab
import com.example.ui.components.ElfHeadersTab
import com.example.ui.components.FridaGhidraTab
import com.example.ui.components.InstalledAppsBrowserSheet
import com.example.ui.components.LinuxDesktopSimulatorTab
import com.example.ui.components.OverviewTab
import com.example.ui.components.SectionsTab
import com.example.ui.components.StringsTab
import com.example.ui.components.SymbolsTab
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CyberCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoAnalyzerApp(
  viewModel: SoAnalyzerViewModel = viewModel()
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val selectedTab by viewModel.selectedTab.collectAsState()
  val symbolSearchQuery by viewModel.symbolSearchQuery.collectAsState()
  val symbolFilter by viewModel.symbolFilter.collectAsState()
  val stringSearchQuery by viewModel.stringSearchQuery.collectAsState()
  val stringCatFilter by viewModel.stringCategoryFilter.collectAsState()

  val pendingApkLibraries by viewModel.pendingApkLibraries.collectAsState()
  val pendingApkName by viewModel.pendingApkName.collectAsState()
  val installedApps by viewModel.installedApps.collectAsState()
  val isLoadingApps by viewModel.isLoadingApps.collectAsState()
  val selectedInstalledApp by viewModel.selectedInstalledApp.collectAsState()
  val selectedSoEntry by viewModel.selectedSoEntry.collectAsState()
  val pendingApkUri by viewModel.pendingApkUri.collectAsState()
  var showInstalledAppsSheet by remember { mutableStateOf(false) }
  var showApkPatchLabDialog by remember { mutableStateOf(false) }
  val currentFileBytes = viewModel.getCurrentFileBytes()

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    uri?.let { viewModel.analyzeUri(context, it) }
  }

  // APK Library Picker Bottom Sheet (shown when an APK with multiple .so files is opened)
  if (pendingApkLibraries != null) {
    ApkLibraryPickerSheet(
      apkName = pendingApkName,
      libraries = pendingApkLibraries ?: emptyList(),
      onSelectLibrary = { entry ->
        viewModel.selectSoFromApk(context, entry)
      },
      onDismiss = { viewModel.dismissApkLibraryPicker() }
    )
  }

  // Installed Apps on Phone Browser Bottom Sheet
  if (showInstalledAppsSheet) {
    InstalledAppsBrowserSheet(
      apps = installedApps,
      isLoading = isLoadingApps,
      onSelectApp = { app ->
        showInstalledAppsSheet = false
        viewModel.selectAppToInspect(app)
      },
      onDismiss = { showInstalledAppsSheet = false }
    )
  }

  // APK Binary Patch Lab Dialog
  if (showApkPatchLabDialog) {
    ApkBinaryPatchLabDialog(
      initialApkUri = pendingApkUri,
      initialApkName = pendingApkName,
      initialInstalledApp = selectedInstalledApp,
      initialSoEntry = selectedSoEntry,
      onDismiss = { showApkPatchLabDialog = false }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Memory,
              contentDescription = "SO Analyzer",
              tint = CyberCyan,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "SO Analyzer",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
          }
        },
        actions = {
          // Action to open APK Binary Patch Lab
          IconButton(
            onClick = { showApkPatchLabDialog = true },
            modifier = Modifier.testTag("action_open_patch_lab")
          ) {
            Icon(
              imageVector = Icons.Default.AutoFixHigh,
              contentDescription = "APK Binary Patch Lab",
              tint = AccentRose
            )
          }

          // Action to browse apps installed on the phone
          IconButton(
            onClick = {
              showInstalledAppsSheet = true
              viewModel.loadInstalledApps(context)
            },
            modifier = Modifier.testTag("action_browse_installed_apps")
          ) {
            Icon(
              imageVector = Icons.Default.Android,
              contentDescription = "Installed Apps on Phone",
              tint = AccentMint
            )
          }

          if (uiState is AnalyzerUiState.Success) {
            IconButton(
              onClick = {
                val report = (uiState as AnalyzerUiState.Success).report
                val reportText = viewModel.getShareableReportText(report)
                val sendIntent = Intent().apply {
                  action = Intent.ACTION_SEND
                  putExtra(Intent.EXTRA_TEXT, reportText)
                  type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Share SO Analysis Report")
                context.startActivity(shareIntent)
              },
              modifier = Modifier.testTag("action_share_report")
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share Report",
                tint = CyberCyan
              )
            }

            IconButton(
              onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
              modifier = Modifier.testTag("action_upload_new")
            ) {
              Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = "Upload Another File",
                tint = CyberCyan
              )
            }

            IconButton(
              onClick = { viewModel.reset() },
              modifier = Modifier.testTag("action_reset")
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset"
              )
            }
          }

          IconButton(
            onClick = { viewModel.setTab(AnalyzerTab.APK_INSTALL) },
            modifier = Modifier.testTag("action_install_apk_guide")
          ) {
            Icon(
              imageVector = Icons.Default.InstallMobile,
              contentDescription = "Install APK Guide",
              tint = AccentMint
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      when (val state = uiState) {
        is AnalyzerUiState.Idle -> {
          if (selectedTab == AnalyzerTab.APK_INSTALL) {
            ApkInstallGuideTab()
          } else if (selectedTab == AnalyzerTab.APK_PATCH_LAB) {
            ApkBinaryPatchLabContent(
              initialApkUri = pendingApkUri,
              initialApkName = pendingApkName,
              initialInstalledApp = selectedInstalledApp,
              initialSoEntry = selectedSoEntry
            )
          } else {
            IdleScreen(
              onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
              onOpenInstalledApps = {
                showInstalledAppsSheet = true
                viewModel.loadInstalledApps(context)
              },
              onLoadSample = { viewModel.loadSample() },
              onOpenInstallGuide = { viewModel.setTab(AnalyzerTab.APK_INSTALL) },
              onOpenPatchLab = { showApkPatchLabDialog = true },
              onOpenLinuxDesktop = {
                viewModel.loadSample()
                viewModel.setTab(AnalyzerTab.LINUX_DESKTOP)
              }
            )
          }
        }

        is AnalyzerUiState.Loading -> {
          LoadingScreen(message = state.message)
        }

        is AnalyzerUiState.Error -> {
          ErrorScreen(
            message = state.message,
            details = state.details,
            onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
            onLoadSample = { viewModel.loadSample() }
          )
        }

        is AnalyzerUiState.Success -> {
          SuccessScreen(
            report = state.report,
            binutilsResult = state.binutilsResult,
            currentFileBytes = currentFileBytes,
            selectedTab = selectedTab,
            pendingApkUri = pendingApkUri,
            pendingApkName = pendingApkName,
            selectedInstalledApp = selectedInstalledApp,
            selectedSoEntry = selectedSoEntry,
            onOpenPatchLab = { showApkPatchLabDialog = true },
            symbolSearchQuery = symbolSearchQuery,
            symbolFilter = symbolFilter,
            stringSearchQuery = stringSearchQuery,
            stringCatFilter = stringCatFilter,
            onTabSelected = { viewModel.setTab(it) },
            onSymbolSearchChange = { viewModel.setSymbolSearch(it) },
            onSymbolFilterChange = { viewModel.setSymbolFilter(it) },
            onStringSearchChange = { viewModel.setStringSearch(it) },
            onStringCategoryChange = { viewModel.setStringCategoryFilter(it) }
          )
        }
      }
    }
  }
}

@Composable
fun IdleScreen(
  onPickFile: () -> Unit,
  onOpenInstalledApps: () -> Unit,
  onLoadSample: () -> Unit,
  onOpenInstallGuide: () -> Unit,
  onOpenPatchLab: () -> Unit,
  onOpenLinuxDesktop: () -> Unit
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Hero Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("idle_hero_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(20.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = CyberCyan.copy(alpha = 0.15f),
          modifier = Modifier.size(64.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Memory,
              contentDescription = "ELF Binary",
              tint = CyberCyan,
              modifier = Modifier.size(36.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Native Android .so Analyzer",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Upload any Android shared library (.so ELF file) or APK package to inspect target architecture, ABI compatibility, JNI exported functions, dynamic dependencies, and security hardening.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
          lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = onPickFile,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("upload_so_button"),
          colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Select / Upload .so or .apk File", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
          onClick = onOpenInstalledApps,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("select_installed_app_button"),
          colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(imageVector = Icons.Default.Android, contentDescription = null, tint = Color.Black)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Select App Installed on Phone", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
          onClick = onOpenPatchLab,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("open_patch_lab_idle_button"),
          colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White)
          Spacer(modifier = Modifier.width(8.dp))
          Text("APK Binary Patch Lab (ARM64 Injektor)", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
          onClick = onLoadSample,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("load_sample_so_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = AccentMint)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Load Demo Library (libnative-lib.so)", color = AccentMint, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
          onClick = onOpenLinuxDesktop,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("open_linux_desktop_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(imageVector = Icons.Default.Computer, contentDescription = null, tint = CyberCyan)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Launch Linux Desktop & CLI Simulator", color = CyberCyan, fontWeight = FontWeight.Bold)
        }
      }
    }

    // What we analyze features card
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "What This Tool Inspects",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureRow(
          icon = Icons.Default.Memory,
          title = "Architecture & ABI Detection",
          subtitle = "Identifies 32-bit vs 64-bit ELF, arm64-v8a, armeabi-v7a, x86_64, endianness, and entry points."
        )
        FeatureRow(
          icon = Icons.Default.Code,
          title = "JNI Method Decoder",
          subtitle = "Detects Java_... exports and translates them into readable Kotlin/Java signatures."
        )
        FeatureRow(
          icon = Icons.Default.Extension,
          title = "Dynamic Dependencies (DT_NEEDED)",
          subtitle = "Discovers required shared libraries like libc.so, libm.so, liblog.so, libandroid.so."
        )
        FeatureRow(
          icon = Icons.Default.Security,
          title = "Security & Hardening Audit",
          subtitle = "Evaluates PIE (Position Independent Executable), NX (No-Execute Stack), RELRO, and Stack Canaries."
        )
      }
    }

    // Install on phone banner
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("idle_install_apk_banner"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.InstallMobile,
          contentDescription = null,
          tint = AccentMint,
          modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Install APK on your Phone",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Export this app directly from AI Studio to run on your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
          )
        }
        OutlinedButton(
          onClick = onOpenInstallGuide,
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("Guide", fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.Top
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = CyberCyan,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        lineHeight = 16.sp
      )
    }
  }
}

@Composable
fun LoadingScreen(message: String) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(24.dp)
    ) {
      CircularProgressIndicator(
        color = CyberCyan,
        modifier = Modifier.size(48.dp)
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun ErrorScreen(
  message: String,
  details: String?,
  onPickFile: () -> Unit,
  onLoadSample: () -> Unit
) {
  var showDetails by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(20.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.ErrorOutline,
          contentDescription = "Error",
          tint = AccentRose,
          modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Analysis Failed",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (details != null) {
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = { showDetails = !showDetails },
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(imageVector = Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (showDetails) "Hide Technical Logs" else "View Technical Logs", fontSize = 12.sp)
          }

          AnimatedVisibility(visible = showDetails) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(8.dp),
                maxLines = 10
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = onPickFile,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
        ) {
          Text("Select Another File")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
          onClick = onLoadSample,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Load Demo Library")
        }
      }
    }
  }
}

@Composable
fun SuccessScreen(
  report: ElfInfo,
  binutilsResult: ElfHeaderExtractor.ExtractionResult,
  currentFileBytes: ByteArray? = null,
  selectedTab: AnalyzerTab,
  pendingApkUri: Uri? = null,
  pendingApkName: String = "",
  selectedInstalledApp: InstalledAppItem? = null,
  selectedSoEntry: BundledSoEntry? = null,
  onOpenPatchLab: () -> Unit = {},
  symbolSearchQuery: String,
  symbolFilter: SymbolFilter,
  stringSearchQuery: String,
  stringCatFilter: String,
  onTabSelected: (AnalyzerTab) -> Unit,
  onSymbolSearchChange: (String) -> Unit,
  onSymbolFilterChange: (SymbolFilter) -> Unit,
  onStringSearchChange: (String) -> Unit,
  onStringCategoryChange: (String) -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    // Top Summary Banner
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = report.fileName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "${report.fileSizeFormatted} • ${if (report.is64Bit) "ELF64" else "ELF32"}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
              color = CyberCyan.copy(alpha = 0.15f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = report.abiName,
                color = CyberCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
            Surface(
              color = AccentMint.copy(alpha = 0.15f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = AccentMint,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = report.securityReport.overallVerdict.take(12),
                  color = AccentMint,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
              }
            }
          }
        }
      }
    }

    // Scrollable Tab Row
    ScrollableTabRow(
      selectedTabIndex = selectedTab.ordinal,
      edgePadding = 12.dp,
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = CyberCyan
    ) {
      AnalyzerTab.entries.forEach { tab ->
        val badge = when (tab) {
          AnalyzerTab.DEPENDENCIES_JNI -> "${report.jniFunctions.size}"
          AnalyzerTab.SYMBOLS -> "${report.symbols.size}"
          AnalyzerTab.SECTIONS -> "${report.sections.size}"
          AnalyzerTab.STRINGS -> "${report.extractedStrings.size}"
          else -> null
        }
        Tab(
          selected = selectedTab == tab,
          onClick = { onTabSelected(tab) },
          text = {
            Text(
              text = if (badge != null) "${tab.title} ($badge)" else tab.title,
              fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
            )
          }
        )
      }
    }

    // Tab Body
    Box(modifier = Modifier.weight(1f)) {
      when (selectedTab) {
        AnalyzerTab.OVERVIEW -> OverviewTab(report = report, onOpenPatchLab = onOpenPatchLab)
        AnalyzerTab.FRIDA_GHIDRA -> FridaGhidraTab(report = report, currentFileBytes = currentFileBytes, onOpenPatchLab = onOpenPatchLab)
        AnalyzerTab.ELF_HEADERS -> ElfHeadersTab(result = binutilsResult)
        AnalyzerTab.DEPENDENCIES_JNI -> DependenciesJniTab(report = report)
        AnalyzerTab.SYMBOLS -> SymbolsTab(
          report = report,
          searchQuery = symbolSearchQuery,
          filter = symbolFilter,
          onSearchChange = onSymbolSearchChange,
          onFilterChange = onSymbolFilterChange
        )
        AnalyzerTab.SECTIONS -> SectionsTab(report = report)
        AnalyzerTab.STRINGS -> StringsTab(
          report = report,
          searchQuery = stringSearchQuery,
          categoryFilter = stringCatFilter,
          onSearchChange = onStringSearchChange,
          onCategoryChange = onStringCategoryChange
        )
        AnalyzerTab.APK_PATCH_LAB -> ApkBinaryPatchLabContent(
          initialApkUri = pendingApkUri,
          initialApkName = pendingApkName,
          initialInstalledApp = selectedInstalledApp,
          initialSoEntry = selectedSoEntry
        )
        AnalyzerTab.LINUX_DESKTOP -> LinuxDesktopSimulatorTab(report = report)
        AnalyzerTab.APK_INSTALL -> ApkInstallGuideTab()
      }
    }
  }
}
