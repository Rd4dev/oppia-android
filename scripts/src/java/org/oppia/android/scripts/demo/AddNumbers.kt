package org.oppia.android.scripts.demo

class AddNumbers(val n1: Int, val n2: Int) {
  fun add(): String {
    if (n1 == 0 && n2 == 0)
      return "Both the numbers are Zero!"
    else
      return "Sum of the numbers: ${n1 + n2}"
  }
}
