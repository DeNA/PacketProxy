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
package packetproxy.encode

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import packetproxy.EncoderManager

/**
 * Characterization tests for EncoderManager, pinning the discovery and instantiation behaviour that
 * the CLI relies on.
 */
class EncoderManagerCharacterizationTest {

  @Test
  fun instanceIsObtainableHeadless() {
    val manager = EncoderManager.getInstance()
    assertThat(manager).isNotNull()
  }

  @Test
  fun encoderNameListContainsKnownBuiltins() {
    val names = EncoderManager.getInstance().getEncoderNameList().toList()
    assertThat(names).contains("Sample", "Sample UpperCase")
  }

  @Test
  fun createInstanceReturnsNonNullForKnownName() {
    val encoder = EncoderManager.getInstance().createInstance("Sample", null)
    assertThat(encoder).isNotNull()
    assertThat(encoder).isInstanceOf(Encoder::class.java)
  }

  @Test
  fun createInstanceReturnsNullForUnknownName() {
    val encoder =
      EncoderManager.getInstance().createInstance("NoSuchEncoderXYZ_${'$'}does_not_exist", null)
    assertThat(encoder).isNull()
  }

  @Test
  fun encoderNameListIsSorted() {
    val names = EncoderManager.getInstance().getEncoderNameList().toList()
    assertThat(names).isSorted()
  }

  @Test
  fun createInstanceSampleHasCorrectName() {
    val encoder = EncoderManager.getInstance().createInstance("Sample", null)!!
    assertThat(encoder.name).isEqualTo("Sample")
  }

  @Test
  fun createInstanceUpperCaseHasCorrectName() {
    val encoder = EncoderManager.getInstance().createInstance("Sample UpperCase", null)!!
    assertThat(encoder.name).isEqualTo("Sample UpperCase")
  }
}
