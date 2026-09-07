package com.example

import com.example.elf.ElfHeaderExtractor
import com.example.sample.SampleSoGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElfHeaderExtractorTest {

  @Test
  fun testExtractElfHeadersAndSections() {
    val extractor = ElfHeaderExtractor()
    val bytes = SampleSoGenerator.createSampleLibrary()

    val result = extractor.extract(bytes)
    assertNotNull(result)

    // 1. Machine Architecture & Class
    assertTrue(result.header.is64Bit)
    assertEquals("ELF64", result.header.elfClass)
    assertEquals("arm64-v8a", result.header.abiName)
    assertTrue(result.header.machineArchitecture.contains("AArch64"))

    // 2. Entry Point
    assertTrue(result.header.entryPointHex.startsWith("0x"))

    // 3. Program and Section header offsets
    assertTrue(result.header.programHeaderOffset > 0)
    assertTrue(result.header.sectionHeaderOffset > 0)
    assertTrue(result.header.sectionHeaderCount > 0)

    // 4. Section Headers list
    assertTrue(result.sections.isNotEmpty())
    val sectionNames = result.sections.map { it.name }
    assertTrue(sectionNames.contains(".text"))
    assertTrue(sectionNames.contains(".rodata"))
    assertTrue(sectionNames.contains(".dynamic"))
    assertTrue(sectionNames.contains(".shstrtab"))

    // 5. GNU binutils readelf output format
    assertTrue(result.readelfHeaderOutput.contains("ELF Header:"))
    assertTrue(result.readelfHeaderOutput.contains("Magic:"))
    assertTrue(result.readelfHeaderOutput.contains("AArch64"))
    assertTrue(result.readelfHeaderOutput.contains("Entry point address:"))

    assertTrue(result.readelfSectionsOutput.contains("Section Headers:"))
    assertTrue(result.readelfSectionsOutput.contains(".text"))
  }
}
