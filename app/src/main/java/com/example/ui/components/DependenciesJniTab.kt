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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Extension
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
import com.example.model.ElfInfo
import com.example.model.JniFunctionInfo
import com.example.ui.theme.AccentMint
import com.example.ui.theme.CyberCyan

@Composable
fun DependenciesJniTab(report: ElfInfo) {
  val context = LocalContext.current

  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. DT_NEEDED Shared Dependencies
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("deps_card"),
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
                imageVector = Icons.Default.Extension,
                contentDescription = "Dependencies",
                tint = CyberCyan,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Required Shared Libraries (DT_NEEDED)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }
            Surface(
              color = CyberCyan.copy(alpha = 0.15f),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = "${report.dependencies.size} linked",
                color = CyberCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          if (report.dependencies.isEmpty()) {
            Text(
              text = "No dynamic dependencies specified in DT_NEEDED table.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          } else {
            report.dependencies.forEach { dep ->
              DependencyItem(depName = dep)
            }
          }
        }
      }
    }

    // 2. JNI Exported Functions Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Code,
            contentDescription = "JNI Functions",
            tint = AccentMint,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "JNI Exported Functions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Surface(
          color = AccentMint.copy(alpha = 0.15f),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = "${report.jniFunctions.size} found",
            color = AccentMint,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    }

    if (report.jniFunctions.isEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(12.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "No standard JNI exported symbols (starting with 'Java_' or 'JNI_OnLoad') were detected.",
              style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Note: If this native library is invoked from Java/Kotlin, it might use dynamic JNI registration via (*env)->RegisterNatives() in a custom init function, or it might be a pure C/C++ helper library.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      }
    } else {
      items(report.jniFunctions) { jni ->
        JniFunctionCard(jni = jni, context = context)
      }
    }
  }
}

@Composable
fun DependencyItem(depName: String) {
  val description = when (depName) {
    "libc.so" -> "Bionic standard C library (memory, threads, syscalls)"
    "libm.so" -> "Standard Math library (sin, cos, pow, float math)"
    "liblog.so" -> "Android Logcat logging service (__android_log_print)"
    "libdl.so" -> "Dynamic link loader interface (dlopen, dlsym)"
    "libandroid.so" -> "Android NDK native activity & asset manager"
    "libjnigraphics.so" -> "Android bitmap pixel access buffer library"
    "libz.so" -> "zlib compression and decompression library"
    "libOpenSLES.so", "libaaudio.so" -> "Android low-latency native audio engine"
    "libEGL.so", "libGLESv2.so", "libGLESv3.so" -> "OpenGL ES graphics pipeline"
    "libvulkan.so" -> "Vulkan high-performance 3D graphics API"
    else -> "External dynamic shared library dependency"
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = depName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          color = CyberCyan
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
      }
    }
  }
}

@Composable
fun JniFunctionCard(jni: JniFunctionInfo, context: Context) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = jni.fullSignature,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = AccentMint,
            fontFamily = FontFamily.Monospace
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = jni.rawSymbol,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val fridaHook = """// Frida Hook for ${jni.rawSymbol}
Interceptor.attach(Module.findExportByName(null, "${jni.rawSymbol}"), {
    onEnter: function(args) {
        console.log("[+] Invocado JNI: ${jni.methodName}()");
    },
    onLeave: function(retval) {
        console.log("[-] Retorno: " + retval);
    }
});"""
              clipboard.setPrimaryClip(ClipData.newPlainText("Frida Hook", fridaHook))
              Toast.makeText(context, "Script Frida copiado!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Terminal,
              contentDescription = "Copy Frida Hook",
              tint = AccentMint,
              modifier = Modifier.size(18.dp)
            )
          }

          IconButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val codeSnippet = if (jni.isSpecial) {
                jni.rawSymbol
              } else {
                "// Kotlin native declaration\nexternal fun ${jni.methodName}(): Any"
              }
              val clip = ClipData.newPlainText("JNI Symbol", codeSnippet)
              clipboard.setPrimaryClip(clip)
              Toast.makeText(context, "Copied declaration to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy JNI Symbol",
              tint = CyberCyan,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      if (!jni.isSpecial) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(
            text = "Class: ${jni.className}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "Method: ${jni.methodName}()",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }
  }
}
