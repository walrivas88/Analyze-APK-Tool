package com.example

import com.example.elf.ElfParser
import com.example.model.StatusLevel
import com.example.sample.SampleSoGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ElfParserTest {

  @Test
  fun testSampleElfParsing() {
    val bytes = SampleSoGenerator.createSampleLibrary()
    assertTrue(bytes.size > 100)

    val info = ElfParser.parse(bytes, "libtest.so")
    assertEquals("libtest.so", info.fileName)
    assertTrue(info.is64Bit)
    assertEquals("arm64-v8a", info.abiName)
    assertTrue(info.machine.contains("AArch64"))
    assertEquals("libnative-lib.so", info.soname)

    // Dependencies
    assertTrue(info.dependencies.contains("libc.so"))
    assertTrue(info.dependencies.contains("liblog.so"))
    assertTrue(info.dependencies.contains("libm.so"))

    // JNI functions
    assertTrue(info.jniFunctions.any { it.rawSymbol == "JNI_OnLoad" })
    assertTrue(info.jniFunctions.any { it.rawSymbol == "Java_com_example_app_NativeEngine_init" })
    assertTrue(info.jniFunctions.any { it.methodName == "processFrame" })

    // Security Audit
    assertEquals(StatusLevel.PASS, info.securityReport.pieStatus.status)
    assertEquals(StatusLevel.PASS, info.securityReport.nxStatus.status)
    assertEquals(StatusLevel.PASS, info.securityReport.relroStatus.status)
    assertEquals(StatusLevel.PASS, info.securityReport.canaryStatus.status)

    // Hashes
    assertEquals(32, info.md5.length)
    assertEquals(40, info.sha1.length)
    assertEquals(64, info.sha256.length)

    // Sections
    assertTrue(info.sections.any { it.name == ".text" })
    assertTrue(info.sections.any { it.name == ".rodata" })
    assertTrue(info.sections.any { it.name == ".dynamic" })

    // Extracted strings
    assertTrue(info.extractedStrings.any { it.value.contains("api.example.com") })
  }

  @Test
  fun testInvalidElfThrowsException() {
    val invalidBytes = "This is not an ELF binary file at all.".toByteArray()
    val exception = assertThrows(IllegalArgumentException::class.java) {
      ElfParser.parse(invalidBytes, "invalid.so")
    }
    assertNotNull(exception.message)
    assertTrue(exception.message!!.contains("ELF Magic") || exception.message!!.contains("too small"))
  }
}
