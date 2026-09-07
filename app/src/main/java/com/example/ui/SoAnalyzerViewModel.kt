package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apk.ApkPackageHelper
import com.example.apk.BundledSoEntry
import com.example.apk.InstalledAppItem
import com.example.elf.ElfHeaderExtractor
import com.example.elf.ElfParser
import com.example.model.ElfInfo
import com.example.sample.SampleSoGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

sealed interface AnalyzerUiState {
  data object Idle : AnalyzerUiState
  data class Loading(val message: String) : AnalyzerUiState
  data class Success(
    val report: ElfInfo,
    val binutilsResult: ElfHeaderExtractor.ExtractionResult
  ) : AnalyzerUiState
  data class Error(val message: String, val details: String? = null) : AnalyzerUiState
}

enum class AnalyzerTab(val title: String) {
  OVERVIEW("Overview"),
  FRIDA_GHIDRA("Frida & Ghidra"),
  ELF_HEADERS("ELF Headers (readelf)"),
  DEPENDENCIES_JNI("Dependencies & JNI"),
  SYMBOLS("Symbols"),
  SECTIONS("Sections"),
  STRINGS("Strings"),
  APK_PATCH_LAB("APK Patch Lab"),
  LINUX_DESKTOP("Linux Desktop & CLI"),
  APK_INSTALL("Install APK & Linux")
}

enum class SymbolFilter(val label: String) {
  ALL("All"),
  JNI_ONLY("JNI Only"),
  EXPORTED("Exported"),
  IMPORTED("Imported")
}

class SoAnalyzerViewModel : ViewModel() {

  private val _uiState = MutableStateFlow<AnalyzerUiState>(AnalyzerUiState.Idle)
  val uiState: StateFlow<AnalyzerUiState> = _uiState.asStateFlow()

  private val _selectedTab = MutableStateFlow(AnalyzerTab.OVERVIEW)
  val selectedTab: StateFlow<AnalyzerTab> = _selectedTab.asStateFlow()

  private var _currentFileBytes: ByteArray? = null
  fun getCurrentFileBytes(): ByteArray? = _currentFileBytes

  // APK & Installed App selection state
  private val _pendingApkUri = MutableStateFlow<Uri?>(null)
  val pendingApkUri: StateFlow<Uri?> = _pendingApkUri.asStateFlow()

  private val _pendingApkName = MutableStateFlow("")
  val pendingApkName: StateFlow<String> = _pendingApkName.asStateFlow()

  private val _pendingApkLibraries = MutableStateFlow<List<BundledSoEntry>?>(null)
  val pendingApkLibraries: StateFlow<List<BundledSoEntry>?> = _pendingApkLibraries.asStateFlow()

  private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
  val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

  private val _isLoadingApps = MutableStateFlow(false)
  val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

  private val _selectedInstalledApp = MutableStateFlow<InstalledAppItem?>(null)
  val selectedInstalledApp: StateFlow<InstalledAppItem?> = _selectedInstalledApp.asStateFlow()

  private val _selectedSoEntry = MutableStateFlow<BundledSoEntry?>(null)
  val selectedSoEntry: StateFlow<BundledSoEntry?> = _selectedSoEntry.asStateFlow()

  init {
    loadSample()
  }

  private val _symbolSearchQuery = MutableStateFlow("")
  val symbolSearchQuery: StateFlow<String> = _symbolSearchQuery.asStateFlow()

  private val _symbolFilter = MutableStateFlow(SymbolFilter.ALL)
  val symbolFilter: StateFlow<SymbolFilter> = _symbolFilter.asStateFlow()

  private val _stringSearchQuery = MutableStateFlow("")
  val stringSearchQuery: StateFlow<String> = _stringSearchQuery.asStateFlow()

  private val _stringCategoryFilter = MutableStateFlow("All")
  val stringCategoryFilter: StateFlow<String> = _stringCategoryFilter.asStateFlow()

  fun setTab(tab: AnalyzerTab) {
    _selectedTab.value = tab
  }

  fun setSymbolSearch(query: String) {
    _symbolSearchQuery.value = query
  }

  fun setSymbolFilter(filter: SymbolFilter) {
    _symbolFilter.value = filter
  }

  fun setStringSearch(query: String) {
    _stringSearchQuery.value = query
  }

  fun setStringCategoryFilter(cat: String) {
    _stringCategoryFilter.value = cat
  }

  fun dismissApkLibraryPicker() {
    _pendingApkLibraries.value = null
  }

  fun reset() {
    _uiState.value = AnalyzerUiState.Idle
    _selectedTab.value = AnalyzerTab.OVERVIEW
    _symbolSearchQuery.value = ""
    _stringSearchQuery.value = ""
    _pendingApkLibraries.value = null
    _selectedInstalledApp.value = null
    _selectedSoEntry.value = null
  }

  fun loadSample() {
    viewModelScope.launch {
      _uiState.value = AnalyzerUiState.Loading("Generating and analyzing sample ELF64 library...")
      try {
        val bytes = withContext(Dispatchers.Default) {
          SampleSoGenerator.createSampleLibrary()
        }
        _currentFileBytes = bytes
        val report = withContext(Dispatchers.Default) {
          ElfParser.parse(bytes, "libnative-lib.so")
        }
        val binutilsResult = withContext(Dispatchers.Default) {
          ElfHeaderExtractor().extract(bytes)
        }
        _uiState.value = AnalyzerUiState.Success(report, binutilsResult)
        _selectedTab.value = AnalyzerTab.OVERVIEW
      } catch (e: Exception) {
        _uiState.value = AnalyzerUiState.Error("Failed to parse sample library: ${e.message}", e.stackTraceToString())
      }
    }
  }

  fun loadInstalledApps(context: Context) {
    viewModelScope.launch {
      _isLoadingApps.value = true
      try {
        val apps = ApkPackageHelper.getInstalledApps(context)
        _installedApps.value = apps
      } catch (e: Exception) {
        // Fallback
      } finally {
        _isLoadingApps.value = false
      }
    }
  }

  fun selectAppToInspect(app: InstalledAppItem) {
    viewModelScope.launch {
      _selectedInstalledApp.value = app
      _uiState.value = AnalyzerUiState.Loading("Scanning native .so libraries inside ${app.appName}...")
      try {
        val libs = ApkPackageHelper.getSoFilesInInstalledApp(app)
        if (libs.isEmpty()) {
          _uiState.value = AnalyzerUiState.Error(
            message = "No native .so libraries found in '${app.appName}'.",
            details = "Package: ${app.packageName}\nThis application is a 100% Java/Kotlin bytecode (DEX) app and does not bundle native C/C++ libraries."
          )
        } else {
          _pendingApkName.value = app.appName
          _pendingApkLibraries.value = libs
          // If we had a previous report, keep it or return to Idle so sheet can pop up
          if (_uiState.value is AnalyzerUiState.Loading) {
            _uiState.value = AnalyzerUiState.Idle
          }
        }
      } catch (e: Exception) {
        _uiState.value = AnalyzerUiState.Error("Failed to read libraries for ${app.appName}: ${e.message}")
      }
    }
  }

  fun selectSoFromApk(context: Context, entry: BundledSoEntry) {
    val uri = _pendingApkUri.value
    val installedApp = _selectedInstalledApp.value

    viewModelScope.launch {
      _uiState.value = AnalyzerUiState.Loading("Extracting ${entry.name} (${entry.abi})...")
      try {
        val bytes = withContext(Dispatchers.IO) {
          if (uri != null) {
            ApkPackageHelper.extractSoFromUri(context, uri, entry.path)
          } else if (installedApp != null) {
            ApkPackageHelper.readSoBytes(installedApp, entry)
          } else {
            throw IllegalStateException("No active APK source available.")
          }
        }

        _currentFileBytes = bytes
        _selectedSoEntry.value = entry
        _uiState.value = AnalyzerUiState.Loading("Parsing ELF headers & JNI symbols for ${entry.name}...")
        val report = withContext(Dispatchers.Default) {
          ElfParser.parse(bytes, entry.name)
        }
        val binutilsResult = withContext(Dispatchers.Default) {
          ElfHeaderExtractor().extract(bytes)
        }

        _pendingApkLibraries.value = null
        _uiState.value = AnalyzerUiState.Success(report, binutilsResult)
        _selectedTab.value = AnalyzerTab.OVERVIEW
      } catch (e: Exception) {
        _uiState.value = AnalyzerUiState.Error("Failed to extract and parse ${entry.name}: ${e.message}", e.stackTraceToString())
      }
    }
  }

  fun analyzeUri(context: Context, uri: Uri) {
    viewModelScope.launch {
      _uiState.value = AnalyzerUiState.Loading("Reading file...")
      try {
        var name = "unknown.so"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex != -1 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex) ?: "library.so"
          }
        }

        val inputStream = context.contentResolver.openInputStream(uri)
          ?: throw IllegalArgumentException("Unable to open input stream for the selected file.")

        val buffer = ByteArrayOutputStream()
        val data = ByteArray(16384)
        var nRead: Int
        var totalBytes = 0L
        val maxAllowedBytes = 90 * 1024 * 1024L // 90 MB limit

        withContext(Dispatchers.IO) {
          inputStream.use { stream ->
            while (stream.read(data, 0, data.size).also { nRead = it } != -1) {
              totalBytes += nRead
              if (totalBytes > maxAllowedBytes) {
                throw IllegalArgumentException("File exceeds the maximum analysis size limit of 90 MB.")
              }
              buffer.write(data, 0, nRead)
            }
          }
        }

        val bytes = buffer.toByteArray()

        // Check if this is an APK or ZIP archive
        if (ApkPackageHelper.isApkOrZip(bytes, name)) {
          _uiState.value = AnalyzerUiState.Loading("Detecting native .so libraries inside $name...")
          val libs = ApkPackageHelper.listSoFilesFromUri(context, uri)
          if (libs.isEmpty()) {
            throw IllegalArgumentException("The selected APK '$name' contains no native .so libraries (100% Java/Kotlin app).")
          }
          _pendingApkUri.value = uri
          _selectedInstalledApp.value = null
          _pendingApkName.value = name
          _pendingApkLibraries.value = libs
          // If only 1 library, auto-select it immediately, otherwise show picker
          if (libs.size == 1) {
            selectSoFromApk(context, libs.first())
          } else {
            _uiState.value = AnalyzerUiState.Idle
          }
          return@launch
        }

        // Standard ELF .so binary
        _currentFileBytes = bytes
        _uiState.value = AnalyzerUiState.Loading("Parsing ELF headers, dynamic symbols & sections...")
        val report = withContext(Dispatchers.Default) {
          ElfParser.parse(bytes, name)
        }
        val binutilsResult = withContext(Dispatchers.Default) {
          ElfHeaderExtractor().extract(bytes)
        }
        _uiState.value = AnalyzerUiState.Success(report, binutilsResult)
        _selectedTab.value = AnalyzerTab.OVERVIEW
      } catch (e: Exception) {
        _uiState.value = AnalyzerUiState.Error(
          message = e.message ?: "An unexpected error occurred while parsing the file.",
          details = e.stackTraceToString()
        )
      }
    }
  }

  fun getShareableReportText(report: ElfInfo): String {
    return buildString {
      appendLine("=========================================")
      appendLine("  SO ANALYZER - NATIVE BINARY REPORT")
      appendLine("=========================================")
      appendLine("File Name: ${report.fileName}")
      appendLine("File Size: ${report.fileSizeFormatted}")
      appendLine("Architecture: ${report.machine}")
      appendLine("ABI Name: ${report.abiName}")
      appendLine("Class: ${if (report.is64Bit) "64-bit ELF (ELF64)" else "32-bit ELF (ELF32)"}")
      appendLine("Endianness: ${report.endianness}")
      appendLine("Type: ${report.fileType}")
      appendLine("Entry Point: ${report.entryPointHex}")
      appendLine("SONAME: ${report.soname ?: "Not specified"}")
      appendLine()
      appendLine("--- CHECKSUMS ---")
      appendLine("MD5:    ${report.md5}")
      appendLine("SHA-1:  ${report.sha1}")
      appendLine("SHA-256: ${report.sha256}")
      appendLine()
      appendLine("--- SECURITY AUDIT ---")
      appendLine("Verdict: ${report.securityReport.overallVerdict}")
      appendLine("• PIE: ${report.securityReport.pieStatus.name} [${report.securityReport.pieStatus.status}]")
      appendLine("• NX Stack: ${report.securityReport.nxStatus.name} [${report.securityReport.nxStatus.status}]")
      appendLine("• RELRO: ${report.securityReport.relroStatus.name} [${report.securityReport.relroStatus.status}]")
      appendLine("• Stack Canary: ${report.securityReport.canaryStatus.name} [${report.securityReport.canaryStatus.status}]")
      appendLine("• Symbol Stripping: ${report.securityReport.strippedStatus.name} [${report.securityReport.strippedStatus.status}]")
      appendLine()
      appendLine("--- LINKED DEPENDENCIES (DT_NEEDED) ---")
      if (report.dependencies.isEmpty()) {
        appendLine("None detected.")
      } else {
        report.dependencies.forEach { appendLine("• $it") }
      }
      appendLine()
      appendLine("--- JNI EXPORTED FUNCTIONS (${report.jniFunctions.size}) ---")
      if (report.jniFunctions.isEmpty()) {
        appendLine("No JNI functions found.")
      } else {
        report.jniFunctions.forEach { jni ->
          appendLine("• ${jni.fullSignature} [${jni.rawSymbol}]")
        }
      }
      appendLine()
      appendLine("--- COMPATIBILITY ---")
      appendLine(report.compatibilitySummary)
      appendLine("=========================================")
    }
  }
}

