package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import com.example.ui.theme.CyberCyan
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

private const val DEFAULT_DOWNLOAD_URL = "https://temp.sh/JwoDp/app-debug.apk"
private const val LINUX_APP_URL = "https://temp.sh/WgttK/so-analyzer-linux.tar.gz"
private const val SOURCE_CODE_URL = "https://temp.sh/jVyCZ/so-analyzer-source.zip"
private const val USER_EMAIL = "Walrivas88@gmail.com"

@Composable
fun ApkInstallGuideTab() {
  val context = LocalContext.current
  val scrollState = rememberScrollState()
  val scope = rememberCoroutineScope()

  var downloadUrl by remember { mutableStateOf(DEFAULT_DOWNLOAD_URL) }
  var isUploadingFresh by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(scrollState)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. IMPORTANT EXPLANATION: Why the download didn't go to physical phone
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("emulator_explainer_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = AccentAmber,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Downloading to Your Actual Phone",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentAmber
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "The phone on your screen is a virtual Android emulator running in Google Cloud. When you click download inside this virtual phone, the file stays inside the cloud emulator.\n\n" +
            "To get the APK directly onto your REAL physical phone, scan the QR code below with your real phone's camera, or send the direct link to your phone:",
          style = MaterialTheme.typography.bodySmall,
          lineHeight = 20.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
        )
      }
    }

    // 2. SCAN QR CODE DIRECTLY ON SCREEN (FASTEST WAY TO REAL PHONE)
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("scan_qr_download_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = CyberCyan,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "Scan with Your Real Phone",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Point your actual phone camera at this QR code",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // High contrast QR code container for instant scanning off computer monitor
        Surface(
          modifier = Modifier
            .size(220.dp)
            .testTag("qr_code_surface"),
          shape = RoundedCornerShape(12.dp),
          color = Color.White
        ) {
          Box(
            modifier = Modifier.padding(12.dp),
            contentAlignment = Alignment.Center
          ) {
            val qrApiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" +
              URLEncoder.encode(downloadUrl, "UTF-8")

            SubcomposeAsyncImage(
              model = qrApiUrl,
              contentDescription = "Scan QR to download APK to real phone",
              loading = {
                Box(contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(36.dp))
                }
              },
              modifier = Modifier.size(196.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "1. Open Camera or Google Lens on your physical phone\n2. Point it at the QR code above\n3. Tap the link to download SO-Analyzer.apk instantly",
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Live URL display
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = downloadUrl,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = CyberCyan,
            modifier = Modifier.padding(10.dp),
            textAlign = TextAlign.Center
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions row: Copy Link & Open in Browser
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("APK Link", downloadUrl))
              Toast.makeText(context, "Link copied! Paste into WhatsApp or Email for your phone", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier
              .weight(1f)
              .testTag("copy_download_link_button"),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = {
              try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                context.startActivity(intent)
              } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier
              .weight(1f)
              .testTag("open_download_link_button"),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 3. SEND DIRECTLY TO YOUR PHYSICAL PHONE VIA EMAIL
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("email_apk_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Email,
            contentDescription = null,
            tint = AccentMint,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "Send Download to Your Email",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Pre-addressed to $USER_EMAIL",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "Tap the button below to compose an email with the APK direct download link. Open the email on your phone to install in seconds.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
          onClick = { sendApkByEmail(context, downloadUrl) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("email_apk_button"),
          colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
          shape = RoundedCornerShape(10.dp)
        ) {
          Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = Color.Black)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Email Download Link to $USER_EMAIL", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Android Share Sheet for Drive / Messaging
        OutlinedButton(
          onClick = { exportAndShareApk(context) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("export_apk_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Send via Android Share (Google Drive / WhatsApp)")
        }
      }
    }

    // 4. LINUX DESKTOP APP & SOURCE CODE DOWNLOADS
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("linux_desktop_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            tint = CyberCyan,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "Linux Desktop App & Source Code",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = CyberCyan
            )
            Text(
              text = "Run on Linux desktop, edit source, or install",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "You can download the native Linux Desktop application installer and the full editable source code project directly:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Linux Desktop App Installer Button
        Button(
          onClick = {
            try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LINUX_APP_URL))
              context.startActivity(intent)
            } catch (e: Exception) {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Linux App URL", LINUX_APP_URL))
              Toast.makeText(context, "Linux download URL copied!", Toast.LENGTH_SHORT).show()
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("download_linux_app_button"),
          colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
          shape = RoundedCornerShape(10.dp)
        ) {
          Icon(imageVector = Icons.Default.Download, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Download Linux Desktop App (.tar.gz)", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Full Source Code ZIP Button
        OutlinedButton(
          onClick = {
            try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_CODE_URL))
              context.startActivity(intent)
            } catch (e: Exception) {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Source Code URL", SOURCE_CODE_URL))
              Toast.makeText(context, "Source code URL copied!", Toast.LENGTH_SHORT).show()
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("download_source_code_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Download Complete Source Code (.zip)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "Quick Install on Linux:",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Bold,
              color = AccentMint
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "1. Extract archive: tar -xzvf so-analyzer-linux.tar.gz\n" +
                "2. Run directly: ./run.sh\n" +
                "3. Or install to apps menu: ./install.sh\n" +
                "4. Edit Python GUI: nano so_analyzer_gui.py\n" +
                "5. Edit Android Kotlin project: Open folder in Android Studio on Linux",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
              lineHeight = 18.sp
            )
          }
        }
      }
    }

    // 5. GENERATE FRESH LINK ON DEMAND
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("fresh_upload_card"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Re-Upload & Generate Fresh Link",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = CyberCyan
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Need a new link? Tap below to package and re-upload the latest APK build directly to generate an updated QR code.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Button(
          onClick = {
            if (!isUploadingFresh) {
              isUploadingFresh = true
              scope.launch {
                val newUrl = uploadApkToTempSh(context)
                if (newUrl != null) {
                  downloadUrl = newUrl
                  Toast.makeText(context, "Fresh link & QR code ready!", Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(context, "Upload failed. Please check network connection.", Toast.LENGTH_LONG).show()
                }
                isUploadingFresh = false
              }
            }
          },
          enabled = !isUploadingFresh,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("upload_fresh_apk_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          if (isUploadingFresh) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Packaging & Uploading APK...")
          } else {
            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Re-Upload & Update QR Code", fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 5. STEP BY STEP INSTALLATION GUIDE
    StepCard(
      stepNumber = "1",
      title = "Get the APK File on Your Real Phone",
      description = "Scan the QR code above with your physical phone's camera, or in the Google AI Studio menu (••• at top right outside the virtual phone), tap 'Export' -> 'Download APK'.",
      icon = Icons.Default.Download
    )

    StepCard(
      stepNumber = "2",
      title = "Open Downloads on Your Phone",
      description = "Once downloaded, tap the notification or open your phone's 'Files' or 'Downloads' app and tap 'SO-Analyzer.apk'.",
      icon = Icons.AutoMirrored.Filled.SendToMobile
    )

    StepCard(
      stepNumber = "3",
      title = "Allow 'Install Unknown Apps' Permission",
      description = "When Android asks: 'For your security, your phone is not allowed to install unknown apps from this source', tap 'Settings' and switch ON 'Allow from this source'.",
      icon = Icons.Default.Security
    )

    StepCard(
      stepNumber = "4",
      title = "Tap Install & Launch SO Analyzer",
      description = "Return to the installer dialog and tap 'Install'. When finished, tap 'Open' to launch SO Analyzer directly on your physical Android phone!",
      icon = Icons.Default.Android
    )

    // Specifications card
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(12.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Compatibility & Specifications",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = AccentMint
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "• Universal Android support: Android 7.0 (API 24) through Android 15/16\n• Built-in pure Kotlin ELF Header & Binutils Parser\n• Completely offline parsing with zero native compilation bottlenecks\n• Compatible with arm64-v8a, armeabi-v7a, x86_64, and x86 devices",
          style = MaterialTheme.typography.bodySmall,
          lineHeight = 20.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
        )
      }
    }
  }
}

private fun sendApkByEmail(context: Context, downloadUrl: String) {
  try {
    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
      data = Uri.parse("mailto:")
      putExtra(Intent.EXTRA_EMAIL, arrayOf(USER_EMAIL))
      putExtra(Intent.EXTRA_SUBJECT, "SO Analyzer APK Direct Download Link")
      putExtra(
        Intent.EXTRA_TEXT,
        "Here is the direct download link for the SO Analyzer APK to install on your Android phone:\n\n" +
          "$downloadUrl\n\n" +
          "Instructions:\n" +
          "1. Open this email on your Android phone and tap the download link.\n" +
          "2. When the APK finishes downloading, tap it to install.\n" +
          "3. If prompted, allow 'Install unknown apps' in Settings."
      )
    }
    context.startActivity(Intent.createChooser(emailIntent, "Send Download Link via Email"))
  } catch (_: Exception) {
    // Fallback: Copy link and show toast
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("APK Link", downloadUrl))
    Toast.makeText(context, "Direct download link copied to clipboard!", Toast.LENGTH_LONG).show()
  }
}

private suspend fun uploadApkToTempSh(context: Context): String? {
  return withContext(Dispatchers.IO) {
    try {
      val srcDir = context.applicationInfo.sourceDir
      val apkFile = File(srcDir)
      if (!apkFile.exists()) return@withContext null

      val client = OkHttpClient()
      val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
          "file",
          "so-analyzer.apk",
          apkFile.asRequestBody("application/vnd.android.package-archive".toMediaType())
        )
        .build()

      val request = Request.Builder()
        .url("https://temp.sh/upload")
        .post(requestBody)
        .build()

      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          response.body?.string()?.trim()
        } else {
          null
        }
      }
    } catch (_: Exception) {
      null
    }
  }
}

private fun exportAndShareApk(context: Context) {
  try {
    val srcDir = context.applicationInfo.sourceDir
    val srcFile = File(srcDir)
    if (!srcFile.exists()) {
      Toast.makeText(context, "APK file not found on device storage.", Toast.LENGTH_LONG).show()
      return
    }

    val exportDir = File(context.cacheDir, "apk_exports")
    if (!exportDir.exists()) exportDir.mkdirs()

    val destApk = File(exportDir, "SO-Analyzer.apk")
    srcFile.copyTo(destApk, overwrite = true)

    val uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      destApk
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "application/vnd.android.package-archive"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "SO Analyzer APK")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share / Save SO Analyzer APK"))
  } catch (e: Exception) {
    Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
  }
}

@Composable
fun StepCard(
  stepNumber: String,
  title: String,
  description: String,
  icon: ImageVector
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(14.dp)
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.Top
    ) {
      Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = CyberCyan.copy(alpha = 0.15f)
      ) {
        Row(
          modifier = Modifier.size(36.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stepNumber,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            fontSize = 16.sp
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyberCyan,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
          lineHeight = 18.sp
        )
      }
    }
  }
}

