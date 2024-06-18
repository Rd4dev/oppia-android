package org.oppia.android.scripts.testfile

import com.google.protobuf.TextFormat
import org.oppia.android.scripts.common.RepositoryFile
import org.oppia.android.scripts.proto.TestFileExemptions
import org.oppia.android.scripts.proto.TestFileExemptions.TestFileExemption
import java.io.File
import java.io.FileInputStream

/**
 * Script for ensuring that all production files have test files present.
 *
 * Usage:
 *   bazel run //scripts:test_file_check -- <path_to_directory_root> [regenerate]
 *
 * Arguments:
 * - path_to_directory_root: directory path to the root of the Oppia Android repository.
 * - regenerate: optional 'regenerate' string to regenerate the exemptions textproto file and print it to the command output.
 *
 * Example:
 *   bazel run //scripts:test_file_check -- $(pwd)
 *   bazel run //scripts:test_file_check -- $(pwd) regenerate
 */
fun main(vararg args: String) {
  // Path of the repo to be analyzed.
  val repoPath = "${args[0]}/"

  val regenerateFile = args.getOrNull(1) == "regenerate"
  val testFileExemptiontextProto = "scripts/assets/test_file_exemptions"

  if (regenerateFile) {
    val testFileExemptionList = loadTestFileExemptionsProto(testFileExemptiontextProto)
      .testFileExemptionList

    val newExemptions = testFileExemptionList.map { exemption ->
      TestFileExemption.newBuilder().apply {
        exemptedFilePath = exemption.exemptedFilePath
        testFileNotRequired = true // Example: setting exemption type
      }.build()
    }

    val newExemptionsProto = TestFileExemptions.newBuilder().apply {
      addAllTestFileExemption(newExemptions)
    }.build()

    println("Regenerated exemptions:")
    println()
    println(TextFormat.printer().printToString(newExemptionsProto))
    return
  }

  // A list of all the files to be exempted for this check.
  // TODO (#3436): Develop a mechanism for permanently exempting files which do not ever need tests.
  val testFileExemptionList = loadTestFileExemptionsProto(testFileExemptiontextProto)
    .testFileExemptionList
    .map { it.exemptedFilePath }

  // A list of all kotlin files in the repo to be analyzed.
  val searchFiles = RepositoryFile.collectSearchFiles(
    repoPath = repoPath,
    expectedExtension = ".kt",
    exemptionsList = testFileExemptionList
  )

  // A list of all the prod files present in the repo.
  val prodFilesList = searchFiles.filter { file -> !file.name.endsWith("Test.kt") }

  // A list of all the test files present in the repo.
  val testFilesList = searchFiles.filter { file -> file.name.endsWith("Test.kt") }

  // A list of all the prod files that do not have a corresponding test file.
  val matchedFiles = prodFilesList.filter { prodFile ->
    !testFilesList.any { testFile ->
      testFile.name == computeExpectedTestFileName(prodFile)
    }
  }

  logFailures(matchedFiles)

  if (matchedFiles.isNotEmpty()) {
    println(
      "Refer to https://github.com/oppia/oppia-android/wiki/Static-Analysis-Checks" +
        "#test-file-presence-check for more details on how to fix this.\n"
    )
  }

  if (matchedFiles.isNotEmpty()) {
    throw Exception("TEST FILE CHECK FAILED")
  } else {
    println("TEST FILE CHECK PASSED")
  }
}

/**
 * Computes the expected test file name for a prod file.
 *
 * @param prodFile the prod file for which expected test file name has to be computed
 * @return expected name of the test file
 */
private fun computeExpectedTestFileName(prodFile: File): String {
  return "${prodFile.nameWithoutExtension}Test.kt"
}

/**
 * Logs the file names of all the prod files that do not have a test file.
 *
 * @param matchedFiles list of all the files missing a test file
 */
private fun logFailures(matchedFiles: List<File>) {
  if (matchedFiles.isNotEmpty()) {
    matchedFiles.sorted().forEach { file ->
      println("File $file does not have a corresponding test file.")
    }
    println()
  }
}

/**
 * Loads the test file exemptions list to proto.
 *
 * @param testFileExemptiontextProto the location of the test file exemption textproto file
 * @return proto class from the parsed textproto file
 */
private fun loadTestFileExemptionsProto(testFileExemptiontextProto: String): TestFileExemptions {
  val protoBinaryFile = File("$testFileExemptiontextProto.pb")
  val builder = TestFileExemptions.newBuilder()

  // This cast is type-safe since proto guarantees type consistency from mergeFrom(),
  // and this method is bounded by the generic type T.
  @Suppress("UNCHECKED_CAST")
  val protoObj: TestFileExemptions =
    FileInputStream(protoBinaryFile).use {
      builder.mergeFrom(it)
    }.build()
  return protoObj
}
