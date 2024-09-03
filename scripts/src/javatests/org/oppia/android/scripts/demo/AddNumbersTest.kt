package org.oppia.android.scripts.demo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AddNumbersTest {
  @Test
  fun testAddNumbers_withNonZeroValues_returnsTheSum() {
    assertThat(AddNumbers(7, 4).add()).isEqualTo("Sum of the numbers: 11")
  }
}
