package com.example.apk

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class PatchRule(
  val id: String = UUID.randomUUID().toString(),
  val offsetHex: String,
  val hexBytes: String,
  val label: String,
  val description: String = "",
  val enabled: Boolean = true
) {
  val cleanOffset: Long
    get() = offsetHex.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: -1L

  val parsedBytes: ByteArray
    get() {
      val clean = hexBytes.replace(" ", "").replace(",", "").replace("0x", "", ignoreCase = true)
      if (clean.length % 2 != 0 || clean.isEmpty()) return byteArrayOf()
      return try {
        ByteArray(clean.length / 2) { i ->
          clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
      } catch (_: Exception) {
        byteArrayOf()
      }
    }
}

data class PatchExecutionResult(
  val success: Boolean,
  val message: String,
  val outputFile: File? = null,
  val patchedLibraryPath: String = "",
  val appliedRulesCount: Int = 0,
  val originalApkSize: Long = 0L,
  val patchedApkSize: Long = 0L,
  val fridaScript: String = ""
)

object ApkPatcher {

  // Preset ARM64 Quick Patches
  val PRESET_NOP_ARM64 = PatchRule(
    offsetHex = "0x0",
    hexBytes = "1F 20 03 D5",
    label = "ARM64 NOP (No Operation)",
    description = "0x1F 0x20 0x03 0xD5 - Neutraliza saltos condicionales o comprobaciones de seguridad."
  )

  val PRESET_RET_ARM64 = PatchRule(
    offsetHex = "0x0",
    hexBytes = "C0 03 5F D6",
    label = "ARM64 RET (Retorno Inmediato)",
    description = "0xC0 0x03 0x5F 0xD6 - Retorna inmediatamente al llamador omitiendo el cuerpo de la función."
  )

  val PRESET_FORCE_TRUE_ARM64 = PatchRule(
    offsetHex = "0x0",
    hexBytes = "20 00 80 52 C0 03 5F D6",
    label = "ARM64 MOV W0, #1; RET (Forzar True)",
    description = "0x20 0x00 0x80 0x52 + 0xC0 0x03 0x5F 0xD6 - Retorna jboolean 1 (éxito/true) en JNI."
  )

  val PRESET_FORCE_FALSE_ARM64 = PatchRule(
    offsetHex = "0x0",
    hexBytes = "00 00 80 52 C0 03 5F D6",
    label = "ARM64 MOV W0, #0; RET (Forzar False)",
    description = "0x00 0x00 0x80 0x52 + 0xC0 0x03 0x5F 0xD6 - Retorna jboolean 0 (falso) para anti-root o anti-debug."
  )

  /**
   * Applies patch rules to a specific native library inside an APK.
   * Strips outdated signature files so package managers or test signers can install it.
   */
  suspend fun patchApk(
    context: Context,
    apkSourceUri: Uri?,
    apkSourceFile: File?,
    targetSoEntryPath: String,
    rules: List<PatchRule>,
    packageName: String = "target.app",
    stripSignatures: Boolean = true
  ): PatchExecutionResult = withContext(Dispatchers.IO) {
    val enabledRules = rules.filter { it.enabled && it.cleanOffset >= 0 && it.parsedBytes.isNotEmpty() }
    if (enabledRules.isEmpty()) {
      return@withContext PatchExecutionResult(
        success = false,
        message = "No hay reglas de parche válidas y habilitadas para aplicar."
      )
    }

    val tempDir = File(context.cacheDir, "patch_lab")
    if (!tempDir.exists()) tempDir.mkdirs()

    val targetLibName = targetSoEntryPath.substringAfterLast("/")
    val outputApkName = "patched_${System.currentTimeMillis()}_$targetLibName.apk"
    val outputApkFile = File(tempDir, outputApkName)

    var originalSize = 0L
    var foundTargetLib = false
    var appliedCount = 0

    try {
      val inputStream: InputStream = when {
        apkSourceFile != null && apkSourceFile.exists() -> {
          originalSize = apkSourceFile.length()
          FileInputStream(apkSourceFile)
        }
        apkSourceUri != null -> {
          context.contentResolver.openInputStream(apkSourceUri)
            ?: throw IllegalArgumentException("No se pudo abrir el stream de la APK seleccionada.")
        }
        else -> throw IllegalArgumentException("No se proporcionó un archivo o URI de APK de origen.")
      }

      inputStream.use { sourceStream ->
        ZipInputStream(sourceStream).use { zis ->
          ZipOutputStream(FileOutputStream(outputApkFile)).use { zos ->
            // Use maximum compression for repackaging
            zos.setLevel(6)

            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
              val entryName = entry.name

              // 1. Signature stripping (META-INF/*.RSA, *.SF, *.MF)
              if (stripSignatures && isSignatureEntry(entryName)) {
                zis.closeEntry()
                entry = zis.nextEntry
                continue
              }

              if (entryName == targetSoEntryPath) {
                foundTargetLib = true
                val originalBytes = zis.readBytes()
                val patchedBytes = originalBytes.clone()

                for (rule in enabledRules) {
                  val offset = rule.cleanOffset
                  val bytesToInject = rule.parsedBytes
                  if (offset in 0 until patchedBytes.size) {
                    val injectLen = minOf(bytesToInject.size, (patchedBytes.size - offset).toInt())
                    for (i in 0 until injectLen) {
                      patchedBytes[(offset + i).toInt()] = bytesToInject[i]
                    }
                    appliedCount++
                  }
                }

                val newEntry = ZipEntry(entryName).apply {
                  method = ZipEntry.DEFLATED
                  time = System.currentTimeMillis()
                }
                zos.putNextEntry(newEntry)
                zos.write(patchedBytes)
                zos.closeEntry()
              } else {
                // Copy entry as is
                val newEntry = ZipEntry(entryName).apply {
                  method = ZipEntry.DEFLATED
                  time = entry.time
                }
                zos.putNextEntry(newEntry)
                zis.copyTo(zos)
                zos.closeEntry()
              }

              zis.closeEntry()
              entry = zis.nextEntry
            }
          }
        }
      }

      if (!foundTargetLib) {
        outputApkFile.delete()
        return@withContext PatchExecutionResult(
          success = false,
          message = "No se encontró la biblioteca '$targetSoEntryPath' dentro del APK."
        )
      }

      val patchedSize = outputApkFile.length()
      val fridaScript = generateFridaApkSpawnPatchScript(packageName, targetLibName, enabledRules)

      PatchExecutionResult(
        success = true,
        message = "¡APK parcheada exitosamente! Se inyectaron $appliedCount parches en $targetLibName.",
        outputFile = outputApkFile,
        patchedLibraryPath = targetSoEntryPath,
        appliedRulesCount = appliedCount,
        originalApkSize = if (originalSize > 0) originalSize else patchedSize,
        patchedApkSize = patchedSize,
        fridaScript = fridaScript
      )
    } catch (e: Exception) {
      if (outputApkFile.exists()) outputApkFile.delete()
      PatchExecutionResult(
        success = false,
        message = "Error durante el repackaging de la APK: ${e.message}"
      )
    }
  }

  private fun isSignatureEntry(name: String): Boolean {
    if (!name.startsWith("META-INF/")) return false
    val upper = name.uppercase()
    return upper.endsWith(".RSA") ||
      upper.endsWith(".DSA") ||
      upper.endsWith(".EC") ||
      upper.endsWith(".SF") ||
      upper.endsWith(".MF")
  }

  /**
   * Search an ASCII string within a byte array and return its offset.
   */
  fun findStringOffset(bytes: ByteArray, search: String): Long {
    if (search.isEmpty() || bytes.isEmpty()) return -1L
    val target = search.toByteArray(Charsets.UTF_8)
    outer@ for (i in 0..(bytes.size - target.size)) {
      for (j in target.indices) {
        if (bytes[i + j] != target[j]) continue@outer
      }
      return i.toLong()
    }
    return -1L
  }

  /**
   * Generates a complete Frida dynamic hot-patching script targeting this APK and library.
   */
  fun generateFridaApkSpawnPatchScript(
    packageName: String,
    soFileName: String,
    rules: List<PatchRule>
  ): String {
    val cleanPkg = packageName.ifEmpty { "com.example.targetapp" }
    val cleanSo = soFileName.ifEmpty { "libtarget.so" }

    val patchLines = StringBuilder()
    rules.forEachIndexed { idx, rule ->
      val hexFormatted = rule.parsedBytes.joinToString(", ") { "0x%02X".format(it) }
      patchLines.append("""
      // Regla #${idx + 1}: ${rule.label}
      // Offset: 0x${rule.cleanOffset.toString(16).uppercase()} (${rule.description})
      var patchOffset_${idx + 1} = ptr("0x${rule.cleanOffset.toString(16)}");
      var patchBytes_${idx + 1} = [$hexFormatted];
      var targetAddr_${idx + 1} = baseAddr.add(patchOffset_${idx + 1});
      
      Memory.patchCode(targetAddr_${idx + 1}, patchBytes_${idx + 1}.length, function(code) {
        code.writeByteArray(patchBytes_${idx + 1});
      });
      console.log("[+] Regla #${idx + 1} aplicada en " + targetAddr_${idx + 1} + " (${rule.label})");
""".trimIndent())
      patchLines.append("\n\n")
    }

    return """
/**
 * ==============================================================================
 * SO Analyzer - Binary Patch Lab (Runtime In-Memory Hook)
 * Target Package : $cleanPkg
 * Target Library : $cleanSo
 * Rules Applied  : ${rules.size}
 * ==============================================================================
 * Modo de Ejecución (Spawn / Attach con Frida CLI):
 *   frida -U -f $cleanPkg -l patch_lab.js --no-pause
 *   frida -U -n $cleanPkg -l patch_lab.js
 * ==============================================================================
 */

console.log("[*] [PatchLab] Inicializando Binary Hot-Patcher para $cleanPkg...");

function applyBinaryPatches() {
  var module = Process.findModuleByName("$cleanSo");
  if (!module) {
    console.log("[-] Modulo $cleanSo aun no cargado en memoria. Esperando dlopen()...");
    return false;
  }

  var baseAddr = module.base;
  console.log("[+] [PatchLab] $cleanSo encontrado en " + baseAddr + " (Tamano: " + module.size + " bytes)");

  try {
${patchLines.toString().prependIndent("    ")}
    console.log("[+] [PatchLab] ¡Todos los parches binarios se aplicaron correctamente en la memoria RAM!");
    return true;
  } catch (err) {
    console.error("[-] [PatchLab] Error al aplicar parche en memoria: " + err);
    return false;
  }
}

// 1. Intentar parchear inmediatamente si la biblioteca ya esta en memoria
if (!applyBinaryPatches()) {
  // 2. Interceptar dlopen / android_dlopen_ext para parchear tan pronto se cargue
  var dlopenPointers = ["android_dlopen_ext", "dlopen"];
  dlopenPointers.forEach(function(fnName) {
    var fnPtr = Module.findExportByName(null, fnName);
    if (fnPtr) {
      Interceptor.attach(fnPtr, {
        onEnter: function(args) {
          this.path = args[0].readUtf8String();
        },
        onLeave: function(retval) {
          if (this.path && this.path.indexOf("$cleanSo") !== -1) {
            console.log("[+] [PatchLab] Detectada carga de " + this.path + " via " + fnName);
            setTimeout(applyBinaryPatches, 50);
          }
        }
      });
    }
  });
}
""".trimIndent()
  }
}
