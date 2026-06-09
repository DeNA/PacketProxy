/*
 * Copyright 2025 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy.cli

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import packetproxy.cli.app.Codec
import packetproxy.cli.app.Codec.Direction

/**
 * Tests for Codec (client-mode encode/decode pipeline). Verifies that the CLI path produces the
 * same results as calling encoders directly — which is the key CLI compatibility guarantee.
 */
class CodecTest {

  @TempDir lateinit var tempDir: File

  private lateinit var originalOut: PrintStream
  private lateinit var originalErr: PrintStream
  private lateinit var capturedOut: ByteArrayOutputStream
  private lateinit var capturedErr: ByteArrayOutputStream

  @BeforeEach
  fun setUp() {
    originalOut = System.out
    originalErr = System.err
    capturedOut = ByteArrayOutputStream()
    capturedErr = ByteArrayOutputStream()
    System.setOut(PrintStream(capturedOut, true, Charsets.UTF_8))
    System.setErr(PrintStream(capturedErr, true, Charsets.UTF_8))
  }

  @AfterEach
  fun tearDown() {
    System.setOut(originalOut)
    System.setErr(originalErr)
  }

  // ─── Sample (identity) via file I/O ───────────────────────────────────────

  @Test
  fun sampleEncodeRequestIsIdentity() {
    val input = tempDir.resolve("in.bin").also { it.writeBytes("hello".toByteArray()) }
    val output = tempDir.resolve("out.bin")

    val exitCode =
      Codec.run(
        encoderName = "Sample",
        direction = Direction.ENCODE_REQUEST,
        text = false,
        inFile = input,
        outFile = output,
      )

    assertThat(exitCode).isEqualTo(0)
    assertThat(output.readBytes()).isEqualTo("hello".toByteArray())
  }

  @Test
  fun sampleDecodeRequestIsIdentity() {
    val input = tempDir.resolve("in.bin").also { it.writeBytes("hello".toByteArray()) }
    val output = tempDir.resolve("out.bin")

    val exitCode =
      Codec.run(
        encoderName = "Sample",
        direction = Direction.DECODE_REQUEST,
        text = false,
        inFile = input,
        outFile = output,
      )

    assertThat(exitCode).isEqualTo(0)
    assertThat(output.readBytes()).isEqualTo("hello".toByteArray())
  }

  @Test
  fun sampleHandlesBinaryData() {
    val binaryData = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())
    val input = tempDir.resolve("in.bin").also { it.writeBytes(binaryData) }
    val output = tempDir.resolve("out.bin")

    val exitCode =
      Codec.run(
        encoderName = "Sample",
        direction = Direction.ENCODE_REQUEST,
        text = false,
        inFile = input,
        outFile = output,
      )

    assertThat(exitCode).isEqualTo(0)
    assertThat(output.readBytes()).isEqualTo(binaryData)
  }

  // ─── SampleUpperCase ──────────────────────────────────────────────────────

  @Test
  fun upperCaseEncodeClientRequestLowercases() {
    val input = tempDir.resolve("in.txt").also { it.writeBytes("HELLO".toByteArray()) }
    val output = tempDir.resolve("out.txt")

    val exitCode =
      Codec.run(
        encoderName = "Sample UpperCase",
        direction = Direction.ENCODE_REQUEST,
        text = false,
        inFile = input,
        outFile = output,
      )

    assertThat(exitCode).isEqualTo(0)
    assertThat(String(output.readBytes())).isEqualTo("hello")
  }

  @Test
  fun upperCaseDecodeClientRequestUppercases() {
    val input = tempDir.resolve("in.txt").also { it.writeBytes("hello".toByteArray()) }
    val output = tempDir.resolve("out.txt")

    val exitCode =
      Codec.run(
        encoderName = "Sample UpperCase",
        direction = Direction.DECODE_REQUEST,
        text = false,
        inFile = input,
        outFile = output,
      )

    assertThat(exitCode).isEqualTo(0)
    assertThat(String(output.readBytes())).isEqualTo("HELLO")
  }

  @Test
  fun upperCaseEncodeServerResponseLowercases() {
    val input = tempDir.resolve("in.txt").also { it.writeBytes("HELLO".toByteArray()) }
    val output = tempDir.resolve("out.txt")

    val exitCode =
      Codec.run(
        encoderName = "Sample UpperCase",
        direction = Direction.ENCODE_RESPONSE,
        text = false,
        inFile = input,
        outFile = output,
      )

    assertThat(exitCode).isEqualTo(0)
    assertThat(String(output.readBytes())).isEqualTo("hello")
  }

  @Test
  fun upperCaseDecodeServerResponseUppercases() {
    val input = tempDir.resolve("in.txt").also { it.writeBytes("hello".toByteArray()) }
    val output = tempDir.resolve("out.txt")

    val exitCode =
      Codec.run(
        encoderName = "Sample UpperCase",
        direction = Direction.DECODE_RESPONSE,
        text = false,
        inFile = input,
        outFile = output,
      )

    assertThat(exitCode).isEqualTo(0)
    assertThat(String(output.readBytes())).isEqualTo("HELLO")
  }

  // ─── Text mode ────────────────────────────────────────────────────────────

  @Test
  fun textModeAddsTrailingNewlineIfMissing() {
    val input = tempDir.resolve("in.txt").also { it.writeBytes("hello".toByteArray()) }

    val exitCode =
      Codec.run(
        encoderName = "Sample",
        direction = Direction.DECODE_REQUEST,
        text = true,
        inFile = input,
        outFile = null,
      )

    assertThat(exitCode).isEqualTo(0)
    val stdout = capturedOut.toString(Charsets.UTF_8)
    assertThat(stdout).endsWith("\n")
    assertThat(stdout.trimEnd('\n')).isEqualTo("hello")
  }

  @Test
  fun textModeDoesNotDuplicateTrailingNewline() {
    val input =
      tempDir.resolve("in.txt").also { it.writeBytes("hello\n".toByteArray(Charsets.UTF_8)) }

    val exitCode =
      Codec.run(
        encoderName = "Sample",
        direction = Direction.DECODE_REQUEST,
        text = true,
        inFile = input,
        outFile = null,
      )

    assertThat(exitCode).isEqualTo(0)
    assertThat(capturedOut.toString(Charsets.UTF_8)).isEqualTo("hello\n")
  }

  // ─── Error handling ───────────────────────────────────────────────────────

  @Test
  fun unknownEncoderReturnsNonZeroAndPrintsToStderr() {
    val input = tempDir.resolve("in.bin").also { it.writeBytes("x".toByteArray()) }

    val exitCode =
      Codec.run(
        encoderName = "NoSuchEncoderXYZ_does_not_exist",
        direction = Direction.ENCODE_REQUEST,
        text = false,
        inFile = input,
        outFile = null,
      )

    assertThat(exitCode).isNotEqualTo(0)
    val stderr = capturedErr.toString(Charsets.UTF_8)
    assertThat(stderr).contains("Unknown encoder")
    assertThat(stderr).contains("Available encoders")
  }

  @Test
  fun errorMessageListsAvailableEncoders() {
    val input = tempDir.resolve("in.bin").also { it.writeBytes("x".toByteArray()) }

    Codec.run(
      encoderName = "NoSuchEncoderXYZ",
      direction = Direction.ENCODE_REQUEST,
      text = false,
      inFile = input,
      outFile = null,
    )

    val stderr = capturedErr.toString(Charsets.UTF_8)
    assertThat(stderr).contains("Sample")
  }
}
