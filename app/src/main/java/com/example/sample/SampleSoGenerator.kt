package com.example.sample

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SampleSoGenerator {

  fun createSampleLibrary(): ByteArray {
    val stream = ByteArrayOutputStream()

    // Strings for .dynstr
    val dynStrStream = ByteArrayOutputStream()
    dynStrStream.write(0) // Null byte at index 0

    fun addDynStr(str: String): Int {
      val offset = dynStrStream.size()
      dynStrStream.write(str.toByteArray(Charsets.UTF_8))
      dynStrStream.write(0)
      return offset
    }

    val offLibc = addDynStr("libc.so")
    val offLibLog = addDynStr("liblog.so")
    val offLibM = addDynStr("libm.so")
    val offSoName = addDynStr("libnative-lib.so")
    val offJniOnLoad = addDynStr("JNI_OnLoad")
    val offJniInit = addDynStr("Java_com_example_app_NativeEngine_init")
    val offJniProcess = addDynStr("Java_com_example_app_NativeEngine_processFrame")
    val offJniRelease = addDynStr("Java_com_example_app_NativeEngine_release")
    val offStackCanary = addDynStr("__stack_chk_fail")
    val dynStrBytes = dynStrStream.toByteArray()

    // Strings for .rodata
    val rodataStream = ByteArrayOutputStream()
    rodataStream.write(0)
    fun addRoStr(str: String) {
      rodataStream.write(str.toByteArray(Charsets.UTF_8))
      rodataStream.write(0)
    }
    addRoStr("https://api.example.com/v1/telemetry")
    addRoStr("https://android.googleapis.com/health")
    addRoStr("/system/bin/app_process64")
    addRoStr("/data/data/com.example.app/cache/engine.bin")
    addRoStr("com/example/app/NativeEngine")
    addRoStr("Android Native Engine Core v2.4.1 (arm64-v8a)")
    addRoStr("Security: Stack Canary + Full RELRO + NX Enabled")
    val rodataBytes = rodataStream.toByteArray()

    // Code for .text (dummy aarch64 instructions: ret)
    val textBytes = byteArrayOf(
      0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte(), // ret
      0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte(), // ret
      0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte(), // ret
      0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte()  // ret
    )

    // Symbol table (.dynsym)
    // Elf64_Sym: st_name(4), st_info(1), st_other(1), st_shndx(2), st_value(8), st_size(8) = 24 bytes each
    val dynSymStream = ByteArrayOutputStream()
    fun addSymbol(nameOffset: Int, bind: Int, type: Int, shndx: Int, value: Long, size: Long) {
      val bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
      bb.putInt(nameOffset)
      bb.put(((bind shl 4) or (type and 0xF)).toByte())
      bb.put(0.toByte()) // st_other (default visibility)
      bb.putShort(shndx.toShort())
      bb.putLong(value)
      bb.putLong(size)
      dynSymStream.write(bb.array())
    }

    // 0: UNDEF
    addSymbol(0, 0, 0, 0, 0L, 0L)
    // 1: imported __stack_chk_fail
    addSymbol(offStackCanary, 1, 2, 0, 0L, 0L)
    // 2: exported JNI_OnLoad (shndx = 6 [.text])
    addSymbol(offJniOnLoad, 1, 2, 6, 0x1000L, 32L)
    // 3: exported Java_com_example_app_NativeEngine_init
    addSymbol(offJniInit, 1, 2, 6, 0x1020L, 64L)
    // 4: exported Java_com_example_app_NativeEngine_processFrame
    addSymbol(offJniProcess, 1, 2, 6, 0x1060L, 128L)
    // 5: exported Java_com_example_app_NativeEngine_release
    addSymbol(offJniRelease, 1, 2, 6, 0x10E0L, 48L)

    val dynSymBytes = dynSymStream.toByteArray()

    // Dynamic section (.dynamic)
    // Elf64_Dyn: d_tag(8), d_val/d_ptr(8) = 16 bytes each
    val dynStream = ByteArrayOutputStream()
    fun addDyn(tag: Long, value: Long) {
      val bb = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
      bb.putLong(tag)
      bb.putLong(value)
      dynStream.write(bb.array())
    }

    addDyn(1L, offLibc.toLong())      // DT_NEEDED: libc.so
    addDyn(1L, offLibLog.toLong())   // DT_NEEDED: liblog.so
    addDyn(1L, offLibM.toLong())     // DT_NEEDED: libm.so
    addDyn(14L, offSoName.toLong())  // DT_SONAME: libnative-lib.so
    addDyn(24L, 1L)                  // DT_BIND_NOW
    addDyn(30L, 0x8L)                // DT_FLAGS: DF_BIND_NOW
    addDyn(0L, 0L)                   // DT_NULL
    val dynamicBytes = dynStream.toByteArray()

    // Section names for .shstrtab
    val shStrStream = ByteArrayOutputStream()
    shStrStream.write(0)
    fun addShStr(str: String): Int {
      val offset = shStrStream.size()
      shStrStream.write(str.toByteArray(Charsets.UTF_8))
      shStrStream.write(0)
      return offset
    }

    val offShStr = addShStr(".shstrtab")
    val offShDynStr = addShStr(".dynstr")
    val offShDynSym = addShStr(".dynsym")
    val offShDynamic = addShStr(".dynamic")
    val offShRodata = addShStr(".rodata")
    val offShText = addShStr(".text")
    val shStrBytes = shStrStream.toByteArray()

    // Compute layout offsets
    val elfHeaderSize = 64
    val phentsize = 56
    val phnum = 4
    val programHeadersTotal = phentsize * phnum // 224

    val offsetText = elfHeaderSize + programHeadersTotal
    val offsetRodata = offsetText + textBytes.size
    val offsetDynStr = offsetRodata + rodataBytes.size
    val offsetDynSym = offsetDynStr + dynStrBytes.size
    val offsetDynamic = offsetDynSym + dynSymBytes.size
    val offsetShStr = offsetDynamic + dynamicBytes.size

    val totalDataSize = offsetShStr + shStrBytes.size

    val shentsize = 64
    val shnum = 7 // NULL, .shstrtab, .dynstr, .dynsym, .dynamic, .rodata, .text
    val offsetShTable = totalDataSize

    // 1. ELF Header
    val eh = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
    // Magic: \x7fELF
    eh.put(0x7F.toByte())
    eh.put('E'.code.toByte())
    eh.put('L'.code.toByte())
    eh.put('F'.code.toByte())
    eh.put(2.toByte()) // 64-bit
    eh.put(1.toByte()) // Little-endian
    eh.put(1.toByte()) // EV_CURRENT
    eh.put(0.toByte()) // System V / Linux
    eh.put(0.toByte()) // ABI version
    eh.position(16)
    eh.putShort(3.toShort()) // ET_DYN (Shared library)
    eh.putShort(183.toShort()) // EM_AARCH64 (arm64-v8a)
    eh.putInt(1) // e_version
    eh.putLong(0x1000L) // e_entry
    eh.putLong(elfHeaderSize.toLong()) // e_phoff (64)
    eh.putLong(offsetShTable.toLong()) // e_shoff
    eh.putInt(0) // e_flags
    eh.putShort(64.toShort()) // e_ehsize
    eh.putShort(phentsize.toShort()) // e_phentsize (56)
    eh.putShort(phnum.toShort()) // e_phnum (4)
    eh.putShort(shentsize.toShort()) // e_shentsize (64)
    eh.putShort(shnum.toShort()) // e_shnum (7)
    eh.putShort(1.toShort()) // e_shstrndx (index 1 is .shstrtab)
    stream.write(eh.array())

    // 2. Program Headers (4 entries * 56 bytes)
    fun writePhdr(type: Int, flags: Int, offset: Long, vaddr: Long, filesz: Long, memsz: Long, align: Long) {
      val pb = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
      pb.putInt(type)
      pb.putInt(flags)
      pb.putLong(offset)
      pb.putLong(vaddr)
      pb.putLong(vaddr) // paddr
      pb.putLong(filesz)
      pb.putLong(memsz)
      pb.putLong(align)
      stream.write(pb.array())
    }

    // Phdr 0: PT_LOAD
    writePhdr(1, 5, 0L, 0x1000L, totalDataSize.toLong(), totalDataSize.toLong(), 0x1000L)
    // Phdr 1: PT_DYNAMIC
    writePhdr(2, 6, offsetDynamic.toLong(), 0x2000L, dynamicBytes.size.toLong(), dynamicBytes.size.toLong(), 8L)
    // Phdr 2: PT_GNU_STACK (flags = 6: PF_R | PF_W => NX No-Execute Stack!)
    writePhdr(0x6474e551, 6, 0L, 0L, 0L, 0L, 16L)
    // Phdr 3: PT_GNU_RELRO (flags = 4: PF_R => RELRO enabled!)
    writePhdr(0x6474e552, 4, offsetDynamic.toLong(), 0x2000L, dynamicBytes.size.toLong(), dynamicBytes.size.toLong(), 1L)

    // 3. Section Data payloads
    stream.write(textBytes)
    stream.write(rodataBytes)
    stream.write(dynStrBytes)
    stream.write(dynSymBytes)
    stream.write(dynamicBytes)
    stream.write(shStrBytes)

    // 4. Section Headers (7 entries * 64 bytes)
    fun writeShdr(name: Int, type: Int, flags: Long, addr: Long, offset: Long, size: Long, link: Int, info: Int, align: Long, entsize: Long) {
      val sb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
      sb.putInt(name)
      sb.putInt(type)
      sb.putLong(flags)
      sb.putLong(addr)
      sb.putLong(offset)
      sb.putLong(size)
      sb.putInt(link)
      sb.putInt(info)
      sb.putLong(align)
      sb.putLong(entsize)
      stream.write(sb.array())
    }

    // 0: NULL
    writeShdr(0, 0, 0L, 0L, 0L, 0L, 0, 0, 0L, 0L)
    // 1: .shstrtab (type 3 SHT_STRTAB)
    writeShdr(offShStr, 3, 0L, 0L, offsetShStr.toLong(), shStrBytes.size.toLong(), 0, 0, 1L, 0L)
    // 2: .dynstr (type 3 SHT_STRTAB)
    writeShdr(offShDynStr, 3, 2L, 0x1500L, offsetDynStr.toLong(), dynStrBytes.size.toLong(), 0, 0, 1L, 0L)
    // 3: .dynsym (type 11 SHT_DYNSYM, link to .dynstr [2])
    writeShdr(offShDynSym, 11, 2L, 0x1600L, offsetDynSym.toLong(), dynSymBytes.size.toLong(), 2, 1, 8L, 24L)
    // 4: .dynamic (type 6 SHT_DYNAMIC, link to .dynstr [2])
    writeShdr(offShDynamic, 6, 3L, 0x2000L, offsetDynamic.toLong(), dynamicBytes.size.toLong(), 2, 0, 8L, 16L)
    // 5: .rodata (type 1 SHT_PROGBITS, flags 2 ALLOC)
    writeShdr(offShRodata, 1, 2L, 0x1200L, offsetRodata.toLong(), rodataBytes.size.toLong(), 0, 0, 4L, 0L)
    // 6: .text (type 1 SHT_PROGBITS, flags 6 ALLOC | EXEC)
    writeShdr(offShText, 1, 6L, 0x1000L, offsetText.toLong(), textBytes.size.toLong(), 0, 0, 16L, 0L)

    return stream.toByteArray()
  }
}
