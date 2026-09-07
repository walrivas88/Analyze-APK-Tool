package com.example.elf

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Binutils-style ELF header and section headers extractor.
 * Implements the core logic found in GNU binutils (readelf) and modern elf-parser libraries
 * to parse and extract machine architecture, entry point address, ELF header fields,
 * and section header tables directly from Android .so native binaries.
 */
class ElfHeaderExtractor {

  data class ElfHeaderInfo(
    val magic: ByteArray,
    val magicHex: String,
    val is64Bit: Boolean,
    val elfClass: String,
    val dataEncoding: String,
    val isLittleEndian: Boolean,
    val version: Int,
    val osAbi: String,
    val osAbiCode: Int,
    val abiVersion: Int,
    val typeCode: Int,
    val fileType: String,
    val machineCode: Int,
    val machineArchitecture: String,
    val abiName: String,
    val entryPoint: Long,
    val entryPointHex: String,
    val programHeaderOffset: Long,
    val sectionHeaderOffset: Long,
    val flags: Long,
    val headerSize: Int,
    val programHeaderEntrySize: Int,
    val programHeaderCount: Int,
    val sectionHeaderEntrySize: Int,
    val sectionHeaderCount: Int,
    val sectionHeaderStringTableIndex: Int
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as ElfHeaderInfo
      return magic.contentEquals(other.magic) && entryPoint == other.entryPoint
    }

    override fun hashCode(): Int {
      return magic.contentHashCode() * 31 + entryPoint.hashCode()
    }
  }

  data class SectionHeaderInfo(
    val index: Int,
    val nameOffset: Int,
    val name: String,
    val typeCode: Int,
    val type: String,
    val address: Long,
    val addressHex: String,
    val offset: Long,
    val offsetHex: String,
    val size: Long,
    val sizeFormatted: String,
    val entrySize: Long,
    val flags: Long,
    val flagsLetters: String,
    val link: Int,
    val info: Int,
    val alignment: Long
  )

  data class ExtractionResult(
    val header: ElfHeaderInfo,
    val sections: List<SectionHeaderInfo>,
    val readelfHeaderOutput: String,
    val readelfSectionsOutput: String
  )

  fun extract(inputStream: InputStream): ExtractionResult {
    val bytes = inputStream.readBytes()
    return extract(bytes)
  }

  fun extract(bytes: ByteArray): ExtractionResult {
    if (bytes.size < 52) {
      throw IllegalArgumentException("Input byte array (${bytes.size} bytes) is too small to contain an ELF header.")
    }

    // 1. Verify Magic Number (\x7fELF)
    val magic = bytes.copyOfRange(0, 16)
    if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() || bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()) {
      val preview = bytes.take(4).joinToString(" ") { "%02X".format(it) }
      throw IllegalArgumentException("Invalid ELF magic: [$preview]. Expected 7F 45 4C 46 (0x7f, 'E', 'L', 'F').")
    }

    // 2. Identification details
    val eiClass = bytes[4].toInt() and 0xFF
    val is64Bit = when (eiClass) {
      1 -> false
      2 -> true
      else -> throw IllegalArgumentException("Unknown ELF class code: $eiClass (expected 1 for 32-bit or 2 for 64-bit)")
    }
    val elfClassStr = if (is64Bit) "ELF64" else "ELF32"

    val eiData = bytes[5].toInt() and 0xFF
    val isLittleEndian = eiData != 2
    val dataEncodingStr = if (isLittleEndian) "2's complement, little endian" else "2's complement, big endian"
    val byteOrder = if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

    val version = bytes[6].toInt() and 0xFF
    val osAbiCode = bytes[7].toInt() and 0xFF
    val osAbiStr = getOsAbiName(osAbiCode)
    val abiVersion = bytes[8].toInt() and 0xFF

    val buffer = ByteBuffer.wrap(bytes).order(byteOrder)

    // 3. Header fields
    val typeCode: Int
    val machineCode: Int
    val entryPoint: Long
    val phOffset: Long
    val shOffset: Long
    val flags: Long
    val ehSize: Int
    val phEntSize: Int
    val phNum: Int
    val shEntSize: Int
    val shNum: Int
    val shStrNdx: Int

    buffer.position(16)
    if (!is64Bit) {
      typeCode = buffer.short.toInt() and 0xFFFF
      machineCode = buffer.short.toInt() and 0xFFFF
      buffer.int // e_version (32-bit)
      entryPoint = buffer.int.toLong() and 0xFFFFFFFFL
      phOffset = buffer.int.toLong() and 0xFFFFFFFFL
      shOffset = buffer.int.toLong() and 0xFFFFFFFFL
      flags = buffer.int.toLong() and 0xFFFFFFFFL
      ehSize = buffer.short.toInt() and 0xFFFF
      phEntSize = buffer.short.toInt() and 0xFFFF
      phNum = buffer.short.toInt() and 0xFFFF
      shEntSize = buffer.short.toInt() and 0xFFFF
      shNum = buffer.short.toInt() and 0xFFFF
      shStrNdx = buffer.short.toInt() and 0xFFFF
    } else {
      typeCode = buffer.short.toInt() and 0xFFFF
      machineCode = buffer.short.toInt() and 0xFFFF
      buffer.int // e_version (32-bit)
      entryPoint = buffer.long
      phOffset = buffer.long
      shOffset = buffer.long
      flags = buffer.int.toLong() and 0xFFFFFFFFL
      ehSize = buffer.short.toInt() and 0xFFFF
      phEntSize = buffer.short.toInt() and 0xFFFF
      phNum = buffer.short.toInt() and 0xFFFF
      shEntSize = buffer.short.toInt() and 0xFFFF
      shNum = buffer.short.toInt() and 0xFFFF
      shStrNdx = buffer.short.toInt() and 0xFFFF
    }

    val (machineArch, abiName) = getMachineArchAndAbi(machineCode)
    val fileTypeStr = getFileTypeString(typeCode)
    val magicHex = magic.joinToString(" ") { "%02x".format(it) }

    val headerInfo = ElfHeaderInfo(
      magic = magic,
      magicHex = magicHex,
      is64Bit = is64Bit,
      elfClass = elfClassStr,
      dataEncoding = dataEncodingStr,
      isLittleEndian = isLittleEndian,
      version = version,
      osAbi = osAbiStr,
      osAbiCode = osAbiCode,
      abiVersion = abiVersion,
      typeCode = typeCode,
      fileType = fileTypeStr,
      machineCode = machineCode,
      machineArchitecture = machineArch,
      abiName = abiName,
      entryPoint = entryPoint,
      entryPointHex = "0x" + entryPoint.toString(16).uppercase(Locale.ROOT),
      programHeaderOffset = phOffset,
      sectionHeaderOffset = shOffset,
      flags = flags,
      headerSize = ehSize,
      programHeaderEntrySize = phEntSize,
      programHeaderCount = phNum,
      sectionHeaderEntrySize = shEntSize,
      sectionHeaderCount = shNum,
      sectionHeaderStringTableIndex = shStrNdx
    )

    // 4. Parse Section Headers (binutils readelf -S logic)
    class RawShdr(
      val index: Int,
      val nameOffset: Int,
      val typeCode: Int,
      val flags: Long,
      val addr: Long,
      val offset: Long,
      val size: Long,
      val link: Int,
      val info: Int,
      val align: Long,
      val entSize: Long
    )

    val rawSections = mutableListOf<RawShdr>()
    if (shOffset > 0 && shNum > 0 && shOffset < bytes.size) {
      for (i in 0 until shNum) {
        val pos = (shOffset + (i * shEntSize)).toInt()
        val expectedSize = if (is64Bit) 64 else 40
        if (pos + expectedSize > bytes.size) break

        buffer.position(pos)
        if (!is64Bit) {
          val shName = buffer.int
          val shType = buffer.int
          val shFlags = buffer.int.toLong() and 0xFFFFFFFFL
          val shAddr = buffer.int.toLong() and 0xFFFFFFFFL
          val shOff = buffer.int.toLong() and 0xFFFFFFFFL
          val shSize = buffer.int.toLong() and 0xFFFFFFFFL
          val shLink = buffer.int
          val shInfo = buffer.int
          val shAlign = buffer.int.toLong() and 0xFFFFFFFFL
          val shEnt = buffer.int.toLong() and 0xFFFFFFFFL
          rawSections.add(RawShdr(i, shName, shType, shFlags, shAddr, shOff, shSize, shLink, shInfo, shAlign, shEnt))
        } else {
          val shName = buffer.int
          val shType = buffer.int
          val shFlags = buffer.long
          val shAddr = buffer.long
          val shOff = buffer.long
          val shSize = buffer.long
          val shLink = buffer.int
          val shInfo = buffer.int
          val shAlign = buffer.long
          val shEnt = buffer.long
          rawSections.add(RawShdr(i, shName, shType, shFlags, shAddr, shOff, shSize, shLink, shInfo, shAlign, shEnt))
        }
      }
    }

    // Resolve .shstrtab section string table
    var shstrtabBytes: ByteArray? = null
    if (shStrNdx in rawSections.indices) {
      val strSec = rawSections[shStrNdx]
      if (strSec.offset + strSec.size <= bytes.size && strSec.offset >= 0 && strSec.size > 0) {
        shstrtabBytes = bytes.copyOfRange(strSec.offset.toInt(), (strSec.offset + strSec.size).toInt())
      }
    }

    val parsedSections = rawSections.map { raw ->
      val name = if (shstrtabBytes != null) getStringFromBytes(shstrtabBytes, raw.nameOffset) else "section_${raw.index}"
      SectionHeaderInfo(
        index = raw.index,
        nameOffset = raw.nameOffset,
        name = name,
        typeCode = raw.typeCode,
        type = getSectionTypeName(raw.typeCode),
        address = raw.addr,
        addressHex = "%016x".format(raw.addr),
        offset = raw.offset,
        offsetHex = "%08x".format(raw.offset),
        size = raw.size,
        sizeFormatted = formatByteSize(raw.size),
        entrySize = raw.entSize,
        flags = raw.flags,
        flagsLetters = getSectionFlagsLetters(raw.flags),
        link = raw.link,
        info = raw.info,
        alignment = raw.align
      )
    }

    val readelfHeader = formatReadelfHeader(headerInfo)
    val readelfSections = formatReadelfSections(headerInfo, parsedSections)

    return ExtractionResult(
      header = headerInfo,
      sections = parsedSections,
      readelfHeaderOutput = readelfHeader,
      readelfSectionsOutput = readelfSections
    )
  }

  fun formatReadelfHeader(h: ElfHeaderInfo): String {
    return buildString {
      appendLine("ELF Header:")
      appendLine("  Magic:   ${h.magicHex}")
      appendLine("  Class:                             ${h.elfClass}")
      appendLine("  Data:                              ${h.dataEncoding}")
      appendLine("  Version:                           ${h.version} (current)")
      appendLine("  OS/ABI:                            ${h.osAbi}")
      appendLine("  ABI Version:                       ${h.abiVersion}")
      appendLine("  Type:                              ${h.fileType}")
      appendLine("  Machine:                           ${h.machineArchitecture}")
      appendLine("  Version:                           0x1")
      appendLine("  Entry point address:               ${h.entryPointHex}")
      appendLine("  Start of program headers:          ${h.programHeaderOffset} (bytes into file)")
      appendLine("  Start of section headers:          ${h.sectionHeaderOffset} (bytes into file)")
      appendLine("  Flags:                             0x${h.flags.toString(16)}")
      appendLine("  Size of this header:               ${h.headerSize} (bytes)")
      appendLine("  Size of program headers:           ${h.programHeaderEntrySize} (bytes)")
      appendLine("  Number of program headers:         ${h.programHeaderCount}")
      appendLine("  Size of section headers:           ${h.sectionHeaderEntrySize} (bytes)")
      appendLine("  Number of section headers:         ${h.sectionHeaderCount}")
      appendLine("  Section header string table index: ${h.sectionHeaderStringTableIndex}")
    }
  }

  fun formatReadelfSections(h: ElfHeaderInfo, sections: List<SectionHeaderInfo>): String {
    return buildString {
      appendLine("There are ${sections.size} section headers, starting at offset 0x${h.sectionHeaderOffset.toString(16)}:")
      appendLine()
      appendLine("Section Headers:")
      appendLine("  [Nr] Name              Type             Address           Offset")
      appendLine("       Size              EntSize          Flags  Link  Info  Align")
      for (s in sections) {
        val namePad = s.name.padEnd(17).take(17)
        val typePad = s.type.padEnd(16).take(16)
        appendLine("  [%2d] %s %s %s  %s".format(s.index, namePad, typePad, s.addressHex, s.offsetHex))
        appendLine("       %016x  %016x %-6s %4d  %4d  %5d".format(s.size, s.entrySize, s.flagsLetters, s.link, s.info, s.alignment))
      }
      appendLine()
      appendLine("Key to Flags:")
      appendLine("  W (write), A (alloc), X (execute), M (merge), S (strings)")
    }
  }

  private fun getOsAbiName(code: Int): String {
    return when (code) {
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
      else -> "System V / Android ($code)"
    }
  }

  private fun getFileTypeString(type: Int): String {
    return when (type) {
      1 -> "REL (Relocatable file)"
      2 -> "EXEC (Executable file)"
      3 -> "DYN (Shared object file / Position-Independent Executable)"
      4 -> "CORE (Core file)"
      else -> "TYPE ($type)"
    }
  }

  private fun getMachineArchAndAbi(machine: Int): Pair<String, String> {
    return when (machine) {
      183 -> Pair("AArch64 (ARM 64-bit Architecture)", "arm64-v8a")
      40 -> Pair("ARM (32-bit Architecture v7/EABI)", "armeabi-v7a")
      62 -> Pair("Advanced Micro Devices X86-64", "x86_64")
      3 -> Pair("Intel 80386 (x86 32-bit)", "x86")
      243 -> Pair("RISC-V (64-bit Architecture)", "riscv64")
      8 -> Pair("MIPS R3000 (32-bit)", "mips")
      else -> Pair("Machine Architecture code $machine", "unknown-abi")
    }
  }

  private fun getSectionTypeName(type: Int): String {
    return when (type) {
      0 -> "NULL"
      1 -> "PROGBITS"
      2 -> "SYMTAB"
      3 -> "STRTAB"
      4 -> "RELA"
      5 -> "HASH"
      6 -> "DYNAMIC"
      7 -> "NOTE"
      8 -> "NOBITS"
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

  private fun getSectionFlagsLetters(flags: Long): String {
    val sb = StringBuilder()
    if ((flags and 0x1L) != 0L) sb.append("W") // SHF_WRITE
    if ((flags and 0x2L) != 0L) sb.append("A") // SHF_ALLOC
    if ((flags and 0x4L) != 0L) sb.append("X") // SHF_EXECINSTR
    if ((flags and 0x10L) != 0L) sb.append("M") // SHF_MERGE
    if ((flags and 0x20L) != 0L) sb.append("S") // SHF_STRINGS
    return if (sb.isEmpty()) "" else sb.toString()
  }

  private fun getStringFromBytes(bytes: ByteArray, offset: Int): String {
    if (offset < 0 || offset >= bytes.size) return ""
    var end = offset
    while (end < bytes.size && bytes[end] != 0.toByte()) {
      end++
    }
    return try {
      String(bytes, offset, end - offset, Charsets.UTF_8)
    } catch (_: Exception) {
      ""
    }
  }

  private fun formatByteSize(size: Long): String {
    return when {
      size < 1024 -> "$size B"
      size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
      else -> "%.2f MB".format(size / (1024.0 * 1024.0))
    }
  }
}
