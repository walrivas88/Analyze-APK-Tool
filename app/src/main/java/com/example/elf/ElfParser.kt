package com.example.elf

import com.example.model.ElfInfo
import com.example.model.ElfSection
import com.example.model.ElfSymbol
import com.example.model.JniFunctionInfo
import com.example.model.SecurityAuditReport
import com.example.model.SecurityFeatureStatus
import com.example.model.StatusLevel
import com.example.model.StringInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale

object ElfParser {

  private val ELF_MAGIC = byteArrayOf(0x7F.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())

  fun parse(bytes: ByteArray, fileName: String = "library.so"): ElfInfo {
    if (bytes.size < 52) {
      throw IllegalArgumentException("File size (${bytes.size} bytes) is too small to be a valid ELF binary.")
    }

    // Verify ELF Magic
    for (i in 0..3) {
      if (bytes[i] != ELF_MAGIC[i]) {
        val headerPreview = bytes.take(8).joinToString(" ") { "%02X".format(it) }
        throw IllegalArgumentException("Invalid ELF Magic [$headerPreview]. Expected: 7F 45 4C 46 (.ELF). This file is not a valid ELF / .so binary.")
      }
    }

    val eiClass = bytes[4].toInt() and 0xFF
    val is64Bit = when (eiClass) {
      1 -> false
      2 -> true
      else -> throw IllegalArgumentException("Unsupported ELF class: $eiClass (Must be 1 for 32-bit or 2 for 64-bit)")
    }

    val eiData = bytes[5].toInt() and 0xFF
    val byteOrder = when (eiData) {
      1 -> ByteOrder.LITTLE_ENDIAN
      2 -> ByteOrder.BIG_ENDIAN
      else -> ByteOrder.LITTLE_ENDIAN
    }

    val osAbiCode = bytes[7].toInt() and 0xFF
    val osAbi = when (osAbiCode) {
      0 -> "UNIX - System V"
      1 -> "HP-UX"
      2 -> "NetBSD"
      3 -> "Linux"
      6 -> "Solaris"
      7 -> "AIX"
      8 -> "IRIX"
      9 -> "FreeBSD"
      12 -> "OpenBSD"
      97 -> "ARM"
      255 -> "Standalone (embedded)"
      else -> "System V / Android ($osAbiCode)"
    }

    val buffer = ByteBuffer.wrap(bytes).order(byteOrder)

    val eType: Int
    val eMachine: Int
    val eVersion: Long
    val eEntry: Long
    val ePhOff: Long
    val eShOff: Long
    val eFlags: Long
    val eEhSize: Int
    val ePhEntSize: Int
    val ePhNum: Int
    val eShEntSize: Int
    val eShNum: Int
    val eShStrNdx: Int

    if (!is64Bit) {
      buffer.position(16)
      eType = buffer.short.toInt() and 0xFFFF
      eMachine = buffer.short.toInt() and 0xFFFF
      eVersion = buffer.int.toLong() and 0xFFFFFFFFL
      eEntry = buffer.int.toLong() and 0xFFFFFFFFL
      ePhOff = buffer.int.toLong() and 0xFFFFFFFFL
      eShOff = buffer.int.toLong() and 0xFFFFFFFFL
      eFlags = buffer.int.toLong() and 0xFFFFFFFFL
      eEhSize = buffer.short.toInt() and 0xFFFF
      ePhEntSize = buffer.short.toInt() and 0xFFFF
      ePhNum = buffer.short.toInt() and 0xFFFF
      eShEntSize = buffer.short.toInt() and 0xFFFF
      eShNum = buffer.short.toInt() and 0xFFFF
      eShStrNdx = buffer.short.toInt() and 0xFFFF
    } else {
      buffer.position(16)
      eType = buffer.short.toInt() and 0xFFFF
      eMachine = buffer.short.toInt() and 0xFFFF
      eVersion = buffer.int.toLong() and 0xFFFFFFFFL
      eEntry = buffer.long
      ePhOff = buffer.long
      eShOff = buffer.long
      eFlags = buffer.int.toLong() and 0xFFFFFFFFL
      eEhSize = buffer.short.toInt() and 0xFFFF
      ePhEntSize = buffer.short.toInt() and 0xFFFF
      ePhNum = buffer.short.toInt() and 0xFFFF
      eShEntSize = buffer.short.toInt() and 0xFFFF
      eShNum = buffer.short.toInt() and 0xFFFF
      eShStrNdx = buffer.short.toInt() and 0xFFFF
    }

    val (machineName, abiName) = getMachineAndAbi(eMachine, is64Bit)
    val fileTypeStr = getElfTypeString(eType)

    // Parse Program Headers
    var hasGnuStack = false
    var gnuStackExecutable = false
    var hasGnuRelro = false

    if (ePhOff > 0 && ePhNum > 0 && ePhOff < bytes.size) {
      for (i in 0 until ePhNum) {
        val phPos = (ePhOff + (i * ePhEntSize)).toInt()
        if (phPos + (if (is64Bit) 56 else 32) > bytes.size) break
        buffer.position(phPos)
        val pType = buffer.int
        if (!is64Bit) {
          buffer.int // p_offset
          buffer.int // p_vaddr
          buffer.int // p_paddr
          buffer.int // p_filesz
          buffer.int // p_memsz
          val pFlags = buffer.int
          if (pType == 0x6474e551) { // PT_GNU_STACK
            hasGnuStack = true
            gnuStackExecutable = (pFlags and 0x1) != 0 // PF_X
          } else if (pType == 0x6474e552) { // PT_GNU_RELRO
            hasGnuRelro = true
          }
        } else {
          val pFlags = buffer.int
          buffer.long // p_offset
          buffer.long // p_vaddr
          buffer.long // p_paddr
          buffer.long // p_filesz
          buffer.long // p_memsz
          if (pType == 0x6474e551) { // PT_GNU_STACK
            hasGnuStack = true
            gnuStackExecutable = (pFlags and 0x1) != 0 // PF_X
          } else if (pType == 0x6474e552) { // PT_GNU_RELRO
            hasGnuRelro = true
          }
        }
      }
    }

    // Parse Section Headers
    class RawSection(
      val index: Int,
      val nameOffset: Int,
      val type: Int,
      val flags: Long,
      val addr: Long,
      val offset: Long,
      val size: Long,
      val link: Int,
      val info: Int,
      val addralign: Long,
      val entsize: Long
    )

    val rawSections = mutableListOf<RawSection>()
    if (eShOff > 0 && eShNum > 0 && eShOff < bytes.size) {
      for (i in 0 until eShNum) {
        val shPos = (eShOff + (i * eShEntSize)).toInt()
        if (shPos + (if (is64Bit) 64 else 40) > bytes.size) break
        buffer.position(shPos)
        if (!is64Bit) {
          val shName = buffer.int
          val shType = buffer.int
          val shFlags = buffer.int.toLong() and 0xFFFFFFFFL
          val shAddr = buffer.int.toLong() and 0xFFFFFFFFL
          val shOffset = buffer.int.toLong() and 0xFFFFFFFFL
          val shSize = buffer.int.toLong() and 0xFFFFFFFFL
          val shLink = buffer.int
          val shInfo = buffer.int
          val shAlign = buffer.int.toLong() and 0xFFFFFFFFL
          val shEntSize = buffer.int.toLong() and 0xFFFFFFFFL
          rawSections.add(RawSection(i, shName, shType, shFlags, shAddr, shOffset, shSize, shLink, shInfo, shAlign, shEntSize))
        } else {
          val shName = buffer.int
          val shType = buffer.int
          val shFlags = buffer.long
          val shAddr = buffer.long
          val shOffset = buffer.long
          val shSize = buffer.long
          val shLink = buffer.int
          val shInfo = buffer.int
          val shAlign = buffer.long
          val shEntSize = buffer.long
          rawSections.add(RawSection(i, shName, shType, shFlags, shAddr, shOffset, shSize, shLink, shInfo, shAlign, shEntSize))
        }
      }
    }

    // Resolve Section String Table (.shstrtab)
    var shStrTabBytes: ByteArray? = null
    if (eShStrNdx in rawSections.indices) {
      val shStrSec = rawSections[eShStrNdx]
      if (shStrSec.offset + shStrSec.size <= bytes.size && shStrSec.offset >= 0 && shStrSec.size > 0) {
        shStrTabBytes = bytes.copyOfRange(shStrSec.offset.toInt(), (shStrSec.offset + shStrSec.size).toInt())
      }
    }

    val parsedSections = mutableListOf<ElfSection>()
    var dynSymSection: RawSection? = null
    var symTabSection: RawSection? = null
    var dynamicSection: RawSection? = null
    var rodataSection: RawSection? = null

    for (raw in rawSections) {
      val name = if (shStrTabBytes != null) getStringFromTable(shStrTabBytes, raw.nameOffset) else "sec_${raw.index}"
      if (name == ".dynsym" || raw.type == 11) dynSymSection = raw
      if (name == ".symtab" || raw.type == 2) symTabSection = raw
      if (name == ".dynamic" || raw.type == 6) dynamicSection = raw
      if (name == ".rodata") rodataSection = raw

      parsedSections.add(
        ElfSection(
          index = raw.index,
          name = name,
          type = getSectionTypeString(raw.type),
          flags = getSectionFlagsString(raw.flags),
          addressHex = "0x" + raw.addr.toString(16).uppercase(Locale.ROOT),
          offsetHex = "0x" + raw.offset.toString(16).uppercase(Locale.ROOT),
          sizeFormatted = formatBytes(raw.size),
          sizeBytes = raw.size
        )
      )
    }

    // Parse Dynamic Section for DT_NEEDED, SONAME, and BIND_NOW
    val dependencies = mutableListOf<String>()
    var soName: String? = null
    var hasBindNow = false

    // Locate .dynstr table for symbol and dependency string lookups
    var dynStrBytes: ByteArray? = null
    if (dynSymSection != null && dynSymSection.link in rawSections.indices) {
      val dynStrSec = rawSections[dynSymSection.link]
      if (dynStrSec.offset + dynStrSec.size <= bytes.size && dynStrSec.offset >= 0 && dynStrSec.size > 0) {
        dynStrBytes = bytes.copyOfRange(dynStrSec.offset.toInt(), (dynStrSec.offset + dynStrSec.size).toInt())
      }
    }

    // Fallback search for .dynstr if not directly linked
    if (dynStrBytes == null) {
      for (raw in rawSections) {
        val name = if (shStrTabBytes != null) getStringFromTable(shStrTabBytes, raw.nameOffset) else ""
        if (name == ".dynstr" && raw.offset + raw.size <= bytes.size && raw.size > 0) {
          dynStrBytes = bytes.copyOfRange(raw.offset.toInt(), (raw.offset + raw.size).toInt())
          break
        }
      }
    }

    if (dynamicSection != null && dynamicSection.offset + dynamicSection.size <= bytes.size && dynamicSection.offset >= 0) {
      val dynOffset = dynamicSection.offset.toInt()
      val dynSize = dynamicSection.size.toInt()
      buffer.position(dynOffset)
      val entrySize = if (is64Bit) 16 else 8
      val numEntries = dynSize / entrySize

      for (i in 0 until numEntries) {
        val tag = if (is64Bit) buffer.long else buffer.int.toLong() and 0xFFFFFFFFL
        val value = if (is64Bit) buffer.long else buffer.int.toLong() and 0xFFFFFFFFL
        if (tag == 0L) break // DT_NULL
        if (tag == 1L) { // DT_NEEDED
          if (dynStrBytes != null) {
            val depName = getStringFromTable(dynStrBytes, value.toInt())
            if (depName.isNotBlank() && !dependencies.contains(depName)) {
              dependencies.add(depName)
            }
          }
        } else if (tag == 14L) { // DT_SONAME
          if (dynStrBytes != null) {
            soName = getStringFromTable(dynStrBytes, value.toInt())
          }
        } else if (tag == 24L) { // DT_BIND_NOW
          hasBindNow = true
        } else if (tag == 30L) { // DT_FLAGS (0x8 = DF_BIND_NOW)
          if ((value and 0x8L) != 0L) hasBindNow = true
        } else if (tag == 0x6ffffffbL) { // DT_FLAGS_1 (0x1 = DF_1_NOW)
          if ((value and 0x1L) != 0L) hasBindNow = true
        }
      }
    }

    // Parse Dynamic Symbols
    val parsedSymbols = mutableListOf<ElfSymbol>()
    var hasStackCanary = false

    if (dynSymSection != null && dynStrBytes != null && dynSymSection.offset + dynSymSection.size <= bytes.size) {
      val symOffset = dynSymSection.offset.toInt()
      val symSize = dynSymSection.size.toInt()
      val entrySize = if (dynSymSection.entsize > 0) dynSymSection.entsize.toInt() else (if (is64Bit) 24 else 16)
      val numSymbols = symSize / entrySize

      buffer.position(symOffset)
      for (i in 0 until numSymbols) {
        val symPos = symOffset + (i * entrySize)
        if (symPos + entrySize > bytes.size) break
        buffer.position(symPos)

        val stName: Int
        val stInfo: Int
        val stOther: Int
        val stShNdx: Int
        val stValue: Long
        val stSize: Long

        if (!is64Bit) {
          stName = buffer.int
          stValue = buffer.int.toLong() and 0xFFFFFFFFL
          stSize = buffer.int.toLong() and 0xFFFFFFFFL
          stInfo = buffer.get().toInt() and 0xFF
          stOther = buffer.get().toInt() and 0xFF
          stShNdx = buffer.short.toInt() and 0xFFFF
        } else {
          stName = buffer.int
          stInfo = buffer.get().toInt() and 0xFF
          stOther = buffer.get().toInt() and 0xFF
          stShNdx = buffer.short.toInt() and 0xFFFF
          stValue = buffer.long
          stSize = buffer.long
        }

        val symbolName = getStringFromTable(dynStrBytes, stName)
        if (symbolName.isNotBlank()) {
          val bindCode = stInfo shr 4
          val typeCode = stInfo and 0xF
          val isExported = stShNdx != 0 && (bindCode == 1 || bindCode == 2)
          val isImported = stShNdx == 0
          val isJni = symbolName.startsWith("Java_") || symbolName == "JNI_OnLoad" || symbolName == "JNI_OnUnload"

          if (symbolName.contains("__stack_chk_fail") || symbolName.contains("__stack_chk_guard")) {
            hasStackCanary = true
          }

          val demangled = if (isJni) demangleJni(symbolName) else null

          parsedSymbols.add(
            ElfSymbol(
              name = symbolName,
              demangled = demangled,
              type = getSymbolTypeString(typeCode),
              binding = getSymbolBindingString(bindCode),
              isExported = isExported,
              isImported = isImported,
              isJni = isJni,
              valueHex = "0x" + stValue.toString(16).uppercase(Locale.ROOT),
              sizeBytes = stSize
            )
          )
        }
      }
    }

    // Parse JNI Functions specifically
    val jniFunctions = parsedSymbols.filter { it.isJni }.map { sym ->
      parseJniDetails(sym.name)
    }

    // Extract Strings
    val extractedStrings = extractStrings(bytes, rodataSection?.offset?.toInt(), rodataSection?.size?.toInt())

    // Hashes
    val md5 = computeHash(bytes, "MD5")
    val sha1 = computeHash(bytes, "SHA-1")
    val sha256 = computeHash(bytes, "SHA-256")

    // Security Audit
    val isPie = eType == 3 // ET_DYN is position-independent
    val pieStatus = SecurityFeatureStatus(
      name = "PIE / PIC (Position Independent Code)",
      status = if (isPie) StatusLevel.PASS else StatusLevel.FAIL,
      description = if (isPie) "Enabled (ET_DYN shared library, compatible with ASLR memory randomization)" else "Disabled (Fixed base address executable, vulnerable to predictable memory layout)"
    )

    val nxStatus = SecurityFeatureStatus(
      name = "NX / DEP (No-Execute Stack)",
      status = if (hasGnuStack && !gnuStackExecutable) StatusLevel.PASS else if (!hasGnuStack) StatusLevel.WARNING else StatusLevel.FAIL,
      description = if (hasGnuStack && !gnuStackExecutable) "Protected (PT_GNU_STACK is non-executable; prevents shellcode injection in stack memory)" else if (!hasGnuStack) "Unspecified (Missing PT_GNU_STACK segment)" else "Disabled (Executable stack allowed - security hazard)"
    )

    val relroStatus = SecurityFeatureStatus(
      name = "RELRO (Relocation Read-Only)",
      status = if (hasGnuRelro && hasBindNow) StatusLevel.PASS else if (hasGnuRelro) StatusLevel.NEUTRAL else StatusLevel.WARNING,
      description = if (hasGnuRelro && hasBindNow) "Full RELRO (GOT table is completely marked read-only before execution starts; maximum hardening)" else if (hasGnuRelro) "Partial RELRO (Data sections are protected, but PLT/GOT relocations can still be lazily resolved)" else "No RELRO (Vulnerable to Global Offset Table overwrite exploits)"
    )

    val canaryStatus = SecurityFeatureStatus(
      name = "Stack Canary (-fstack-protector)",
      status = if (hasStackCanary) StatusLevel.PASS else StatusLevel.WARNING,
      description = if (hasStackCanary) "Detected (__stack_chk_fail guard present; defends against buffer overflow exploits)" else "Not detected in dynamic imports (Binary may lack stack smashing protection or functions use inline checks)"
    )

    val isStripped = symTabSection == null
    val strippedStatus = SecurityFeatureStatus(
      name = "Symbol Stripping (APK Optimization)",
      status = if (isStripped) StatusLevel.PASS else StatusLevel.NEUTRAL,
      description = if (isStripped) "Stripped (.symtab debug section removed, normal for production release APKs)" else "Unstripped (.symtab present, debug symbols retained; increases file size)"
    )

    val overallScore = when {
      pieStatus.status == StatusLevel.PASS && nxStatus.status == StatusLevel.PASS && relroStatus.status == StatusLevel.PASS -> "Hardened & Production-Ready"
      pieStatus.status == StatusLevel.PASS && nxStatus.status == StatusLevel.PASS -> "Standard Security (Compatible with modern Android)"
      else -> "Review Recommended"
    }

    val securityAudit = SecurityAuditReport(
      pieStatus = pieStatus,
      nxStatus = nxStatus,
      relroStatus = relroStatus,
      canaryStatus = canaryStatus,
      strippedStatus = strippedStatus,
      overallVerdict = overallScore
    )

    val compatibilitySummary = buildCompatibilitySummary(abiName, is64Bit, dependencies, jniFunctions.size)

    return ElfInfo(
      fileName = fileName,
      fileSize = bytes.size.toLong(),
      fileSizeFormatted = formatBytes(bytes.size.toLong()),
      md5 = md5,
      sha1 = sha1,
      sha256 = sha256,
      is64Bit = is64Bit,
      endianness = if (byteOrder == ByteOrder.LITTLE_ENDIAN) "Little-endian (2's complement)" else "Big-endian",
      osAbi = osAbi,
      fileType = fileTypeStr,
      machine = machineName,
      abiName = abiName,
      entryPointHex = "0x" + eEntry.toString(16).uppercase(Locale.ROOT),
      headerSize = eEhSize,
      programHeaderCount = ePhNum,
      sectionHeaderCount = eShNum,
      soname = soName,
      dependencies = dependencies,
      sections = parsedSections,
      symbols = parsedSymbols,
      jniFunctions = jniFunctions,
      securityReport = securityAudit,
      extractedStrings = extractedStrings,
      compatibilitySummary = compatibilitySummary
    )
  }

  private fun getMachineAndAbi(machine: Int, is64Bit: Boolean): Pair<String, String> {
    return when (machine) {
      183 -> Pair("AArch64 / ARM 64-bit", "arm64-v8a")
      40 -> Pair("ARM 32-bit (v7 / EABI)", "armeabi-v7a")
      62 -> Pair("AMD x86-64 / Intel 64-bit", "x86_64")
      3 -> Pair("Intel 80386 / x86 32-bit", "x86")
      243 -> Pair("RISC-V 64-bit", "riscv64")
      8 -> Pair("MIPS 32-bit", "mips")
      else -> Pair("Machine code $machine", if (is64Bit) "generic-64" else "generic-32")
    }
  }

  private fun getElfTypeString(type: Int): String {
    return when (type) {
      1 -> "Relocatable file (ET_REL)"
      2 -> "Executable file (ET_EXEC)"
      3 -> "Shared object / Dynamic library (ET_DYN)"
      4 -> "Core dump file (ET_CORE)"
      else -> "Type $type"
    }
  }

  private fun getSectionTypeString(type: Int): String {
    return when (type) {
      0 -> "NULL"
      1 -> "PROGBITS"
      2 -> "SYMTAB"
      3 -> "STRTAB"
      4 -> "RELA"
      5 -> "HASH"
      6 -> "DYNAMIC"
      7 -> "NOTE"
      8 -> "NOBITS (BSS)"
      9 -> "REL"
      11 -> "DYNSYM"
      14 -> "INIT_ARRAY"
      15 -> "FINI_ARRAY"
      0x6ffffff6 -> "GNU_HASH"
      0x6ffffffe -> "VERNEED"
      0x6fffffff -> "VERSYM"
      else -> "TYPE_0x" + type.toString(16).uppercase(Locale.ROOT)
    }
  }

  private fun getSectionFlagsString(flags: Long): String {
    val list = mutableListOf<String>()
    if ((flags and 0x1L) != 0L) list.add("WRITE")
    if ((flags and 0x2L) != 0L) list.add("ALLOC")
    if ((flags and 0x4L) != 0L) list.add("EXEC")
    if ((flags and 0x10L) != 0L) list.add("MERGE")
    if ((flags and 0x20L) != 0L) list.add("STRINGS")
    return if (list.isEmpty()) "-" else list.joinToString(" | ")
  }

  private fun getSymbolTypeString(type: Int): String {
    return when (type) {
      0 -> "NOTYPE"
      1 -> "OBJECT"
      2 -> "FUNC"
      3 -> "SECTION"
      4 -> "FILE"
      5 -> "COMMON"
      6 -> "TLS"
      else -> "TYPE_$type"
    }
  }

  private fun getSymbolBindingString(binding: Int): String {
    return when (binding) {
      0 -> "LOCAL"
      1 -> "GLOBAL"
      2 -> "WEAK"
      else -> "BIND_$binding"
    }
  }

  private fun getStringFromTable(table: ByteArray, offset: Int): String {
    if (offset < 0 || offset >= table.size) return ""
    var end = offset
    while (end < table.size && table[end] != 0.toByte()) {
      end++
    }
    return try {
      String(table, offset, end - offset, Charsets.UTF_8)
    } catch (_: Exception) {
      ""
    }
  }

  private fun demangleJni(raw: String): String {
    if (raw == "JNI_OnLoad" || raw == "JNI_OnUnload") return raw
    if (!raw.startsWith("Java_")) return raw

    // Example: Java_com_example_app_NativeLib_stringFromJNI
    val clean = raw.substring(5)
      .replace("_1", "_")
      .replace("_0", "$")

    val parts = clean.split("_")
    return if (parts.size >= 2) {
      val method = parts.last()
      val clazz = parts[parts.size - 2]
      val pkg = parts.dropLast(2).joinToString(".")
      if (pkg.isNotEmpty()) "$pkg.$clazz.$method()" else "$clazz.$method()"
    } else {
      raw
    }
  }

  private fun parseJniDetails(raw: String): JniFunctionInfo {
    if (raw == "JNI_OnLoad") {
      return JniFunctionInfo(
        rawSymbol = raw,
        packageName = "(Native Runtime)",
        className = "JNI Lifecycle",
        methodName = "JNI_OnLoad",
        fullSignature = "jint JNI_OnLoad(JavaVM *vm, void *reserved)",
        isSpecial = true
      )
    }
    if (raw == "JNI_OnUnload") {
      return JniFunctionInfo(
        rawSymbol = raw,
        packageName = "(Native Runtime)",
        className = "JNI Lifecycle",
        methodName = "JNI_OnUnload",
        fullSignature = "void JNI_OnUnload(JavaVM *vm, void *reserved)",
        isSpecial = true
      )
    }

    val clean = raw.substring(5)
      .replace("_1", "_")
      .replace("_0", "$")
    val parts = clean.split("_")
    if (parts.size >= 2) {
      val methodName = parts.last()
      val className = parts[parts.size - 2]
      val pkgName = parts.dropLast(2).joinToString(".")
      val full = if (pkgName.isNotEmpty()) "$pkgName.$className.$methodName(...)" else "$className.$methodName(...)"
      return JniFunctionInfo(
        rawSymbol = raw,
        packageName = pkgName.ifEmpty { "(default package)" },
        className = className,
        methodName = methodName,
        fullSignature = full,
        isSpecial = false
      )
    }

    return JniFunctionInfo(
      rawSymbol = raw,
      packageName = "-",
      className = "-",
      methodName = raw,
      fullSignature = raw,
      isSpecial = false
    )
  }

  private fun extractStrings(bytes: ByteArray, secOffset: Int?, secSize: Int?): List<StringInfo> {
    val results = mutableListOf<StringInfo>()
    val searchBytes: ByteArray
    if (secOffset != null && secSize != null && secOffset >= 0 && secOffset + secSize <= bytes.size && secSize > 0) {
      searchBytes = bytes.copyOfRange(secOffset, secOffset + secSize)
    } else {
      searchBytes = bytes
    }

    val sb = StringBuilder()
    var count = 0
    val maxStrings = 200

    for (b in searchBytes) {
      val c = b.toInt().toChar()
      if (c in ' '..'~' || c == '\t') {
        sb.append(c)
      } else {
        if (sb.length >= 4) {
          val s = sb.toString().trim()
          if (s.length >= 4 && s.length <= 150) {
            val category = when {
              s.startsWith("http://") || s.startsWith("https://") -> "URL"
              s.startsWith("/") && (s.contains("system") || s.contains("data") || s.contains("dev") || s.contains("proc")) -> "System Path"
              s.contains("android/") || s.contains("java/") || s.contains("dalvik/") -> "Android/JVM Class"
              s.contains(".so") -> "Shared Library"
              else -> "General String"
            }
            results.add(StringInfo(s, category))
            count++
            if (count >= maxStrings) break
          }
        }
        sb.setLength(0)
      }
    }
    return results
  }

  private fun computeHash(bytes: ByteArray, algorithm: String): String {
    return try {
      val digest = MessageDigest.getInstance(algorithm)
      val hashBytes = digest.digest(bytes)
      hashBytes.joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
      "Unavailable"
    }
  }

  fun formatBytes(bytes: Long): String {
    return when {
      bytes < 1024 -> "$bytes B"
      bytes < 1024 * 1024 -> "%.1f KB (%d B)".format(bytes / 1024.0, bytes)
      else -> "%.2f MB (%d B)".format(bytes / (1024.0 * 1024.0), bytes)
    }
  }

  private fun buildCompatibilitySummary(abi: String, is64Bit: Boolean, deps: List<String>, jniCount: Int): String {
    val sb = StringBuilder()
    when (abi) {
      "arm64-v8a" -> sb.append("Targeted for ARM 64-bit (arm64-v8a). Supported by virtually all modern Android devices (Android 5.0+ / API 21+). Required for Google Play 64-bit compliance.")
      "armeabi-v7a" -> sb.append("Targeted for legacy ARM 32-bit (armeabi-v7a). Supported on older hardware or 32-bit mode; modern devices running 64-bit only Android 14+ won't load 32-bit binaries.")
      "x86_64" -> sb.append("Targeted for x86 64-bit architectures. Commonly used in Android Studio Virtual Devices (AVD / Emulator) and ChromeOS x86_64 devices.")
      "x86" -> sb.append("Targeted for legacy x86 32-bit architectures. Used in older emulator configurations.")
      "riscv64" -> sb.append("Targeted for emerging RISC-V 64-bit Android architecture preview.")
      else -> sb.append("Targeted for generic architecture: $abi (${if (is64Bit) "64-bit" else "32-bit"}).")
    }

    if (deps.isNotEmpty()) {
      sb.append("\n\nLinked System Dependencies: ").append(deps.joinToString(", "))
    }
    if (jniCount > 0) {
      sb.append("\n\nDetected $jniCount JNI exported symbol(s) ready to be invoked via native Kotlin/Java external methods.")
    } else {
      sb.append("\n\nNo JNI exported methods detected. This library might be an internal C/C++ engine, statically bound, or registered dynamically using RegisterNatives().")
    }
    return sb.toString()
  }
}
