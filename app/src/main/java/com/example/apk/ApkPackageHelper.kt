package com.example.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class BundledSoEntry(
  val path: String,
  val name: String,
  val abi: String,
  val size: Long,
  val isPrimaryAbi: Boolean = false
) {
  val sizeFormatted: String
    get() {
      if (size <= 0) return "Unknown size"
      val kb = size / 1024.0
      return if (kb < 1024) "%.1f KB".format(kb) else "%.2f MB".format(kb / 1024.0)
    }
}

data class InstalledAppItem(
  val appName: String,
  val packageName: String,
  val versionName: String,
  val sourceDir: String,
  val nativeLibDir: String,
  val isSystemApp: Boolean,
  val icon: Drawable? = null
)

object ApkPackageHelper {

  private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

  fun isApkOrZip(bytes: ByteArray, fileName: String = ""): Boolean {
    if (fileName.endsWith(".apk", ignoreCase = true) ||
      fileName.endsWith(".xapk", ignoreCase = true) ||
      fileName.endsWith(".apks", ignoreCase = true) ||
      fileName.endsWith(".zip", ignoreCase = true)
    ) {
      return true
    }
    if (bytes.size >= 4) {
      return bytes[0] == ZIP_MAGIC[0] &&
        bytes[1] == ZIP_MAGIC[1] &&
        bytes[2] == ZIP_MAGIC[2] &&
        bytes[3] == ZIP_MAGIC[3]
    }
    return false
  }

  suspend fun listSoFilesFromUri(context: Context, uri: Uri): List<BundledSoEntry> = withContext(Dispatchers.IO) {
    val list = mutableListOf<BundledSoEntry>()
    val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
    inputStream.use { stream ->
      val zis = ZipInputStream(stream)
      var entry: ZipEntry? = zis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory && entry.name.endsWith(".so", ignoreCase = true)) {
          val parts = entry.name.split("/")
          val abi = if (parts.size >= 2 && parts[0] == "lib") parts[1] else "universal"
          val fileName = parts.last()
          val isArm64 = abi.equals("arm64-v8a", ignoreCase = true)
          list.add(
            BundledSoEntry(
              path = entry.name,
              name = fileName,
              abi = abi,
              size = if (entry.size >= 0) entry.size else 0L,
              isPrimaryAbi = isArm64
            )
          )
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }
    // Sort so arm64-v8a appears first, then by name
    list.sortedWith(compareByDescending<BundledSoEntry> { it.isPrimaryAbi }.thenBy { it.name })
  }

  suspend fun extractSoFromUri(context: Context, uri: Uri, entryPath: String): ByteArray = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(uri)
      ?: throw IllegalArgumentException("Could not open stream for selected APK file.")

    inputStream.use { stream ->
      val zis = ZipInputStream(stream)
      var entry: ZipEntry? = zis.nextEntry
      while (entry != null) {
        if (entry.name == entryPath) {
          val bos = ByteArrayOutputStream()
          val buffer = ByteArray(16384)
          var len: Int
          while (zis.read(buffer).also { len = it } != -1) {
            bos.write(buffer, 0, len)
          }
          return@withContext bos.toByteArray()
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }
    throw IllegalArgumentException("Library '$entryPath' not found inside the APK.")
  }

  suspend fun getInstalledApps(context: Context): List<InstalledAppItem> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val apps = mutableListOf<InstalledAppItem>()
    try {
      val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
      for (app in installedApps) {
        // Exclude our own package to keep list clean, but keep everything else
        if (app.packageName == context.packageName) continue

        val appName = try {
          pm.getApplicationLabel(app).toString()
        } catch (_: Exception) {
          app.packageName
        }

        val versionName = try {
          val pInfo = pm.getPackageInfo(app.packageName, 0)
          pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
          "1.0"
        }

        val icon = try {
          pm.getApplicationIcon(app)
        } catch (_: Exception) {
          null
        }

        val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        apps.add(
          InstalledAppItem(
            appName = appName,
            packageName = app.packageName,
            versionName = versionName,
            sourceDir = app.sourceDir ?: "",
            nativeLibDir = app.nativeLibraryDir ?: "",
            isSystemApp = isSystem,
            icon = icon
          )
        )
      }
    } catch (_: Exception) {
      // Fallback
    }
    // Sort user apps first, then alphabetically
    apps.sortedWith(compareBy<InstalledAppItem> { it.isSystemApp }.thenBy { it.appName.lowercase() })
  }

  suspend fun getSoFilesInInstalledApp(app: InstalledAppItem): List<BundledSoEntry> = withContext(Dispatchers.IO) {
    val results = mutableListOf<BundledSoEntry>()

    // 1. Check extracted nativeLibraryDir first
    if (app.nativeLibDir.isNotEmpty()) {
      val libDir = File(app.nativeLibDir)
      if (libDir.exists() && libDir.isDirectory) {
        libDir.listFiles()?.forEach { file ->
          if (file.isFile && file.name.endsWith(".so", ignoreCase = true)) {
            results.add(
              BundledSoEntry(
                path = file.absolutePath,
                name = file.name,
                abi = "native-dir",
                size = file.length(),
                isPrimaryAbi = true
              )
            )
          }
        }
      }
    }

    // 2. Also inspect the base APK file if accessible
    if (app.sourceDir.isNotEmpty()) {
      val apkFile = File(app.sourceDir)
      if (apkFile.exists() && apkFile.canRead()) {
        try {
          ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
              val entry = entries.nextElement()
              if (!entry.isDirectory && entry.name.endsWith(".so", ignoreCase = true)) {
                val parts = entry.name.split("/")
                val abi = if (parts.size >= 2 && parts[0] == "lib") parts[1] else "apk-bundled"
                val fileName = parts.last()
                val isArm64 = abi.equals("arm64-v8a", ignoreCase = true)
                // Avoid exact duplicates if already added from nativeLibraryDir
                if (results.none { it.name == fileName && it.abi == abi }) {
                  results.add(
                    BundledSoEntry(
                      path = entry.name,
                      name = fileName,
                      abi = abi,
                      size = if (entry.size >= 0) entry.size else 0L,
                      isPrimaryAbi = isArm64
                    )
                  )
                }
              }
            }
          }
        } catch (_: Exception) {
          // If ZipFile read restricted by sandbox, stream it via FileInputStream
          try {
            FileInputStream(apkFile).use { fis ->
              val zis = ZipInputStream(fis)
              var entry: ZipEntry? = zis.nextEntry
              while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".so", ignoreCase = true)) {
                  val parts = entry.name.split("/")
                  val abi = if (parts.size >= 2 && parts[0] == "lib") parts[1] else "apk-bundled"
                  val fileName = parts.last()
                  if (results.none { it.name == fileName }) {
                    results.add(
                      BundledSoEntry(
                        path = entry.name,
                        name = fileName,
                        abi = abi,
                        size = if (entry.size >= 0) entry.size else 0L,
                        isPrimaryAbi = abi == "arm64-v8a"
                      )
                    )
                  }
                }
                zis.closeEntry()
                entry = zis.nextEntry
              }
            }
          } catch (_: Exception) {
            // Ignored
          }
        }
      }
    }

    results.sortedWith(compareByDescending<BundledSoEntry> { it.isPrimaryAbi }.thenBy { it.name })
  }

  suspend fun readSoBytes(app: InstalledAppItem, entry: BundledSoEntry): ByteArray = withContext(Dispatchers.IO) {
    if (entry.path.startsWith("/")) {
      val file = File(entry.path)
      if (file.exists() && file.canRead()) {
        return@withContext file.readBytes()
      }
    }
    // Otherwise read from sourceDir APK
    val apkFile = File(app.sourceDir)
    ZipFile(apkFile).use { zip ->
      val zipEntry = zip.getEntry(entry.path)
        ?: throw IllegalArgumentException("Could not find ${entry.path} in ${apkFile.name}")
      zip.getInputStream(zipEntry).use { input ->
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(16384)
        var len: Int
        while (input.read(buffer).also { len = it } != -1) {
          bos.write(buffer, 0, len)
        }
        return@withContext bos.toByteArray()
      }
    }
  }
}
