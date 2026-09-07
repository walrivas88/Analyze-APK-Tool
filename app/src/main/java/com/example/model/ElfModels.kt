package com.example.model

enum class StatusLevel {
  PASS,
  WARNING,
  NEUTRAL,
  FAIL
}

data class SecurityFeatureStatus(
  val name: String,
  val status: StatusLevel,
  val description: String
)

data class SecurityAuditReport(
  val pieStatus: SecurityFeatureStatus,
  val nxStatus: SecurityFeatureStatus,
  val relroStatus: SecurityFeatureStatus,
  val canaryStatus: SecurityFeatureStatus,
  val strippedStatus: SecurityFeatureStatus,
  val overallVerdict: String
)

data class JniFunctionInfo(
  val rawSymbol: String,
  val packageName: String,
  val className: String,
  val methodName: String,
  val fullSignature: String,
  val isSpecial: Boolean = false
)

data class ElfSection(
  val index: Int,
  val name: String,
  val type: String,
  val flags: String,
  val addressHex: String,
  val offsetHex: String,
  val sizeFormatted: String,
  val sizeBytes: Long
)

data class ElfSymbol(
  val name: String,
  val demangled: String?,
  val type: String,
  val binding: String,
  val isExported: Boolean,
  val isImported: Boolean,
  val isJni: Boolean,
  val valueHex: String,
  val sizeBytes: Long
)

data class StringInfo(
  val value: String,
  val category: String
)

data class ElfInfo(
  val fileName: String,
  val fileSize: Long,
  val fileSizeFormatted: String,
  val md5: String,
  val sha1: String,
  val sha256: String,
  val is64Bit: Boolean,
  val endianness: String,
  val osAbi: String,
  val fileType: String,
  val machine: String,
  val abiName: String,
  val entryPointHex: String,
  val headerSize: Int,
  val programHeaderCount: Int,
  val sectionHeaderCount: Int,
  val soname: String?,
  val dependencies: List<String>,
  val sections: List<ElfSection>,
  val symbols: List<ElfSymbol>,
  val jniFunctions: List<JniFunctionInfo>,
  val securityReport: SecurityAuditReport,
  val extractedStrings: List<StringInfo>,
  val compatibilitySummary: String
)
