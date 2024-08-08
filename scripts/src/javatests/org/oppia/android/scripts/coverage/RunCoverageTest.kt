package org.oppia.android.scripts.coverage

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.oppia.android.scripts.common.CommandExecutorImpl
import org.oppia.android.scripts.common.ScriptBackgroundCoroutineDispatcher
import org.oppia.android.scripts.proto.TestFileExemptions
import org.oppia.android.scripts.proto.TestFileExemptions.TestFileExemption
import org.oppia.android.scripts.testing.TestBazelWorkspace
import org.oppia.android.testing.assertThrows
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.TimeUnit

/** Tests for [RunCoverage]. */
class RunCoverageTest {
  @field:[Rule JvmField] val tempFolder = TemporaryFolder()

  private val outContent: ByteArrayOutputStream = ByteArrayOutputStream()
  private val originalOut: PrintStream = System.out

  private val scriptBgDispatcher by lazy { ScriptBackgroundCoroutineDispatcher() }
  private val longCommandExecutor by lazy { initializeCommandExecutorWithLongProcessWaitTime() }

  private lateinit var testBazelWorkspace: TestBazelWorkspace
  private lateinit var coverageDir: String
  private lateinit var markdownOutputPath: String
  private lateinit var htmlOutputPath: String

  private lateinit var sourceContent: String
  private lateinit var testContent: String
  private lateinit var testExemptions: String

  @Before
  fun setUp() {
    coverageDir = "/coverage_reports"
    markdownOutputPath = "${tempFolder.root}/coverage_reports/report.md"
    htmlOutputPath = "${tempFolder.root}/coverage_reports/report.html"
    testExemptions = createTestFileExemptionTextProto()
    testBazelWorkspace = TestBazelWorkspace(tempFolder)

    sourceContent =
      """
      package com.example
      
      class AddNums {
        companion object {
          fun sumNumbers(a: Int, b: Int): Any {
            return if (a == 0 && b == 0) {
                "Both numbers are zero"
            } else {
                a + b
            }
          }
        }
      }
      """.trimIndent()

    testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsTest {
        @Test
        fun testSumNumbers() {
          assertEquals(AddNums.sumNumbers(0, 1), 1)
          assertEquals(AddNums.sumNumbers(3, 4), 7)         
          assertEquals(AddNums.sumNumbers(0, 0), "Both numbers are zero")
        }
      }
      """.trimIndent()
  }

  @After
  fun tearDown() {
    System.setOut(originalOut)
    scriptBgDispatcher.close()
  }

  @Test
  fun testRunCoverage_invalidFile_throwsException() {
    testBazelWorkspace.initEmptyWorkspace()
    val exception = assertThrows<IllegalStateException>() {
      main(tempFolder.root.absolutePath, "file.kt")
    }

    assertThat(exception).hasMessageThat().contains("File doesn't exist")
  }

  @Test
  fun testRunCoverage_missingTestFileNotExempted_throwsException() {
    testBazelWorkspace.initEmptyWorkspace()
    val exception = assertThrows<IllegalStateException>() {
      val sampleFile = File(tempFolder.root.absolutePath, "file.kt")
      sampleFile.createNewFile()
      main(tempFolder.root.absolutePath, "file.kt")
    }

    assertThat(exception).hasMessageThat().contains("No appropriate test file found")
  }

  @Test
  fun testRunCoverage_invalidFormat_throwsException() {
    testBazelWorkspace.initEmptyWorkspace()
    val exception = assertThrows<IllegalArgumentException>() {
      main(tempFolder.root.absolutePath, "file.kt", "format=PDF")
    }

    assertThat(exception).hasMessageThat().contains("Unsupported report format")
  }

  @Test
  fun testRunCoverage_ignoreCaseMarkdownArgument_returnsCoverageData() {
    val filePath = "coverage/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    main(
      "${tempFolder.root}",
      filePath,
      "format=Markdown",
      "processTimeout=10"
    )

    val outputFilePath = "${tempFolder.root}" +
      "$coverageDir/${filePath.removeSuffix(".kt")}/coverage.md"

    assertThat(File(outputFilePath).exists()).isTrue()
  }

  @Test
  fun testRunCoverage_ignoreCaseHtmlArgument_returnsCoverageData() {
    val filePath = "coverage/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    main(
      "${tempFolder.root}",
      filePath,
      "format=Html",
      "processTimeout=10"
    )

    val outputFilePath = "${tempFolder.root}" +
      "$coverageDir/${filePath.removeSuffix(".kt")}/coverage.html"

    assertThat(File(outputFilePath).exists()).isTrue()
  }

  @Test
  fun testRunCoverage_reorderedArguments_returnsCoverageData() {
    val filePath = "coverage/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    main(
      "${tempFolder.root}",
      filePath,
      "processTimeout=10",
      "format=MARKDOWN"
    )

    val outputFilePath = "${tempFolder.root}" +
      "$coverageDir/${filePath.removeSuffix(".kt")}/coverage.md"

    assertThat(File(outputFilePath).exists()).isTrue()
  }

  @Test
  fun testRunCoverage_testFileExempted_noCoverage() {
    System.setOut(PrintStream(outContent))
    val exemptedFilePath = "TestExempted.kt"

    RunCoverage(
      "${tempFolder.root}",
      exemptedFilePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    assertThat(outContent.toString().trim()).isEqualTo(
      "This file is exempted from having a test file; skipping coverage check."
    )
  }

  @Test
  fun testRunCoverage_sourceFileIncompatibleWithCodeCoverage_exemptedFromCoverageAnalysis() {
    System.setOut(PrintStream(outContent))
    val incompatibleFilePath = "SourceIncompatibleWithCoverage.kt"

    RunCoverage(
      "${tempFolder.root}",
      incompatibleFilePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    // The assertion statemnt is subject to updation of the the log statement.
    assertThat(outContent.toString().trim()).isEqualTo(
      "This file is exempted from having a test file; skipping coverage check."
    )
  }

// The following code is how we can test the same scenario in the upcoming prs with
// introduction to specific Failure, `Exempted` and Details type and
// enabling list of files analysis
// To make sure the incompatible source code is not considered for the coverage run,
// we can check for the exemption of source file in the final markdown report
/*  @Test
  fun testRunCoverage_incompatibleSourceFileCoverageRun_generatesMarkdown() {
    val filePathList =
      listOf(
        "coverage/main/java/com/example/AddNums.kt",
        "SourceIncompatibleWithCoverage.kt"
      )

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    val exception = assertThrows<IllegalStateException>() {
      RunCoverage(
        "${tempFolder.root}",
        filePathList,
        ReportFormat.MARKDOWN,
        markdownOutputPath,
        longCommandExecutor,
        scriptBgDispatcher,
        testExemptions
      ).execute()
    }

    assertThat(exception).hasMessageThat()
      .contains("Coverage Analysis$BOLD$RED FAILED$RESET")

    val failureMessage =
      "Source File Incompatible To Perform Code Coverage Analysis"

    val expectedResult = buildString {
      append("## Coverage Report\n\n")
      append("### Results\n")
      ...(other reports)
      append("### Files Exempted from Coverage\n")
      append(
        "- [${exemptedFilePathList.get(0).substringAfterLast("/")}]" +
          "($oppiaDevelopGitHubLink/${exemptedFilePathList.get(0)})"
      )
    }
    assertThat(readFinalMdReport()).isEqualTo(expectedMarkdown)
  }*/

  @Test
  fun testRunCoverage_sampleTestsDefaultFormat_returnsCoverageData() {
    val filePath = "coverage/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    main(
      "${tempFolder.root}",
      filePath,
    )

    val outputReportText = File(
      "${tempFolder.root}" +
        "$coverageDir/${filePath.removeSuffix(".kt")}/coverage.md"
    ).readText()

    val expectedResult = getExpectedMarkdownText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sampleTestsMarkdownFormat_returnsCoverageData() {
    val filePath = "coverage/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(markdownOutputPath).readText()
    val expectedResult = getExpectedMarkdownText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_scriptTestsMarkdownFormat_returnsCoverageData() {
    val filePath = "scripts/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "scripts/java/com/example",
      testSubpackage = "scripts/javatests/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(markdownOutputPath).readText()
    val expectedResult = getExpectedMarkdownText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_appTestsMarkdownFormat_returnsCoverageData() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(markdownOutputPath).readText()
    val expectedResult = getExpectedMarkdownText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_localTestsMarkdownFormat_returnsCoverageData() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    val testContentLocal =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(AddNums.sumNumbers(0, 1), 1)
              assertEquals(AddNums.sumNumbers(3, 4), 7)         
              assertEquals(AddNums.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsLocalTest",
      sourceContent = sourceContent,
      testContent = testContentLocal,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(markdownOutputPath).readText()
    val expectedResult = getExpectedMarkdownText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sharedTestsMarkdownFormat_returnsCoverageData() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/sharedTest/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(markdownOutputPath).readText()
    val expectedResult = getExpectedMarkdownText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sharedAndLocalTestsMarkdownFormat_returnsCoverageData() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()

    val testContentShared =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(AddNums.sumNumbers(0, 1), 1)       
          }
      }
      """.trimIndent()

    val testContentLocal =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(AddNums.sumNumbers(0, 1), 1)
              assertEquals(AddNums.sumNumbers(3, 4), 7)         
          }
      }
      """.trimIndent()

    testBazelWorkspace.addMultiLevelSourceAndTestFileWithContent(
      filename = "AddNums",
      sourceContent = sourceContent,
      testContentShared = testContentShared,
      testContentLocal = testContentLocal,
      subpackage = "app"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(markdownOutputPath).readText()
    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** $filePath
        - **Coverage percentage:** 50.00% covered
        - **Line coverage:** 2 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_withMultipleTestsForFile_analysingSameFile() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()

    val testContent1 =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(AddNums.sumNumbers(0, 1), 1)       
          }
      }
      """.trimIndent()

    val testContent2 =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(AddNums.sumNumbers(0, 1), 1)
              assertEquals(AddNums.sumNumbers(3, 4), 7)         
          }
      }
      """.trimIndent()

    testBazelWorkspace.addMultiLevelSourceAndTestFileWithContent(
      filename = "AddNums",
      sourceContent = sourceContent,
      testContentShared = testContent1,
      testContentLocal = testContent2,
      subpackage = "app"
    )

    // Both the test files will correspond to one single source file
    // therefore no error would be thrown while aggregating the coverage reports.
    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.MARKDOWN,
      markdownOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(markdownOutputPath).readText()
    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** $filePath
        - **Coverage percentage:** 50.00% covered
        - **Line coverage:** 2 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sampleTestsHtmlFormat_returnsCoverageData() {
    val filePath = "coverage/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.HTML,
      htmlOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(htmlOutputPath).readText()
    val expectedResult = getExpectedHtmlText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_scriptTestsHtmlFormat_returnsCoverageData() {
    val filePath = "scripts/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "scripts/java/com/example",
      testSubpackage = "scripts/javatests/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.HTML,
      htmlOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(htmlOutputPath).readText()
    val expectedResult = getExpectedHtmlText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_appTestsHtmlFormat_returnsCoverageData() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.HTML,
      htmlOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(htmlOutputPath).readText()
    val expectedResult = getExpectedHtmlText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_localTestsHtmlFormat_returnsCoverageData() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    val testContentLocal =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(AddNums.sumNumbers(0, 1), 1)
              assertEquals(AddNums.sumNumbers(3, 4), 7)         
              assertEquals(AddNums.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsLocalTest",
      sourceContent = sourceContent,
      testContent = testContentLocal,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.HTML,
      htmlOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(htmlOutputPath).readText()
    val expectedResult = getExpectedHtmlText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sharedTestsHtmlFormat_returnsCoverageData() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()
    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "AddNums",
      testFilename = "AddNumsTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/sharedTest/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      filePath,
      ReportFormat.HTML,
      htmlOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(htmlOutputPath).readText()
    val expectedResult = getExpectedHtmlText(filePath)

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sharedAndLocalTestsHtmlFormat_returnsCoverageData() {
    val filePath = "app/main/java/com/example/AddNums.kt"

    testBazelWorkspace.initEmptyWorkspace()

    val testContentShared =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(AddNums.sumNumbers(0, 1), 1)       
          }
      }
      """.trimIndent()

    val testContentLocal =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class AddNumsLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(AddNums.sumNumbers(0, 1), 1)
              assertEquals(AddNums.sumNumbers(3, 4), 7)         
          }
      }
      """.trimIndent()

    testBazelWorkspace.addMultiLevelSourceAndTestFileWithContent(
      filename = "AddNums",
      sourceContent = sourceContent,
      testContentShared = testContentShared,
      testContentLocal = testContentLocal,
      subpackage = "app"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/AddNums.kt",
      ReportFormat.HTML,
      htmlOutputPath,
      longCommandExecutor,
      scriptBgDispatcher,
      testExemptions
    ).execute()

    val outputReportText = File(htmlOutputPath).readText()
    val expectedResult =
      """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Coverage Report</title>
      <style>
        body {
            font-family: Arial, sans-serif;
            font-size: 12px;
            line-height: 1.6;
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 8px;
            margin-left: 20px;
            text-align: left;
            white-space: pre-wrap;
            border-bottom: 1px solid #e3e3e3;
        }
        .line-number-col {
            width: 4%;
        }
        .line-number-row {
            border-right: 1px solid #ababab
        }
        .source-code-col {
            width: 96%;
        }
        .covered-line, .not-covered-line, .uncovered-line {
            /*white-space: pre-wrap;*/
        }
        .covered-line {
            background-color: #c8e6c9; /* Light green */
        }
        .not-covered-line {
            background-color: #ffcdd2; /* Light red */
        }
        .uncovered-line {
            background-color: #f7f7f7; /* light gray */
        }
        .coverage-summary {
          margin-bottom: 20px;
        }
        h2 {
          text-align: center;
        }
        ul {
          list-style-type: none;
          padding: 0;
          text-align: center;
        }
        .summary-box {
          border: 1px solid #ccc;
          border-radius: 8px;
          padding: 10px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .summary-left {
          text-align: left;
        }
        .summary-right {
          text-align: right;
        }
        .legend {
          display: flex;
          align-items: center;
        }
        .legend-item {
          width: 20px;
          height: 10px;
          margin-right: 5px;
          border-radius: 2px;
          display: inline-block;
        }
        .legend .covered {
          background-color: #c8e6c9; /* Light green */
        }
        .legend .not-covered {
          margin-left: 4px;
          background-color: #ffcdd2; /* Light red */
        }
        @media screen and (max-width: 768px) {
          body {
              padding: 10px;
          }
          table {
              width: auto;
          }
        }
      </style>
    </head>
    <body>
      <h2>Coverage Report</h2>
      <div class="summary-box">
        <div class="summary-left">
          <strong>Covered File:</strong> $filePath <br>
          <div class="legend">
            <div class="legend-item covered"></div>
            <span>Covered</span>
            <div class="legend-item not-covered"></div>
            <span>Uncovered</span>
          </div>
        </div>
        <div class="summary-right">
          <div><strong>Coverage percentage:</strong> 50.00%</div>
          <div><strong>Line coverage:</strong> 2 / 4 covered</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th class="line-number-col">Line No</th>
            <th class="source-code-col">Source Code</th>
          </tr>
        </thead>
        <tbody><tr>
        <td class="line-number-row">   1</td>
        <td class="uncovered-line">package com.example</td>
    </tr><tr>
        <td class="line-number-row">   2</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   3</td>
        <td class="not-covered-line">class AddNums {</td>
    </tr><tr>
        <td class="line-number-row">   4</td>
        <td class="uncovered-line">  companion object {</td>
    </tr><tr>
        <td class="line-number-row">   5</td>
        <td class="uncovered-line">    fun sumNumbers(a: Int, b: Int): Any {</td>
    </tr><tr>
        <td class="line-number-row">   6</td>
        <td class="covered-line">      return if (a == 0 && b == 0) {</td>
    </tr><tr>
        <td class="line-number-row">   7</td>
        <td class="not-covered-line">          "Both numbers are zero"</td>
    </tr><tr>
        <td class="line-number-row">   8</td>
        <td class="uncovered-line">      } else {</td>
    </tr><tr>
        <td class="line-number-row">   9</td>
        <td class="covered-line">          a + b</td>
    </tr><tr>
        <td class="line-number-row">  10</td>
        <td class="uncovered-line">      }</td>
    </tr><tr>
        <td class="line-number-row">  11</td>
        <td class="uncovered-line">    }</td>
    </tr><tr>
        <td class="line-number-row">  12</td>
        <td class="uncovered-line">  }</td>
    </tr><tr>
        <td class="line-number-row">  13</td>
        <td class="uncovered-line">}</td>
    </tr>    </tbody>
      </table>
    </body>
    </html>
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  private fun getExpectedMarkdownText(filePath: String): String {
    val markdownText =
      """
        ## Coverage Report
        
        - **Covered File:** $filePath
        - **Coverage percentage:** 75.00% covered
        - **Line coverage:** 3 / 4 lines covered
      """.trimIndent()

    return markdownText
  }

  private fun getExpectedHtmlText(filePath: String): String {
    val htmlText =
      """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Coverage Report</title>
      <style>
        body {
            font-family: Arial, sans-serif;
            font-size: 12px;
            line-height: 1.6;
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 8px;
            margin-left: 20px;
            text-align: left;
            white-space: pre-wrap;
            border-bottom: 1px solid #e3e3e3;
        }
        .line-number-col {
            width: 4%;
        }
        .line-number-row {
            border-right: 1px solid #ababab
        }
        .source-code-col {
            width: 96%;
        }
        .covered-line, .not-covered-line, .uncovered-line {
            /*white-space: pre-wrap;*/
        }
        .covered-line {
            background-color: #c8e6c9; /* Light green */
        }
        .not-covered-line {
            background-color: #ffcdd2; /* Light red */
        }
        .uncovered-line {
            background-color: #f7f7f7; /* light gray */
        }
        .coverage-summary {
          margin-bottom: 20px;
        }
        h2 {
          text-align: center;
        }
        ul {
          list-style-type: none;
          padding: 0;
          text-align: center;
        }
        .summary-box {
          border: 1px solid #ccc;
          border-radius: 8px;
          padding: 10px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .summary-left {
          text-align: left;
        }
        .summary-right {
          text-align: right;
        }
        .legend {
          display: flex;
          align-items: center;
        }
        .legend-item {
          width: 20px;
          height: 10px;
          margin-right: 5px;
          border-radius: 2px;
          display: inline-block;
        }
        .legend .covered {
          background-color: #c8e6c9; /* Light green */
        }
        .legend .not-covered {
          margin-left: 4px;
          background-color: #ffcdd2; /* Light red */
        }
        @media screen and (max-width: 768px) {
          body {
              padding: 10px;
          }
          table {
              width: auto;
          }
        }
      </style>
    </head>
    <body>
      <h2>Coverage Report</h2>
      <div class="summary-box">
        <div class="summary-left">
          <strong>Covered File:</strong> $filePath <br>
          <div class="legend">
            <div class="legend-item covered"></div>
            <span>Covered</span>
            <div class="legend-item not-covered"></div>
            <span>Uncovered</span>
          </div>
        </div>
        <div class="summary-right">
          <div><strong>Coverage percentage:</strong> 75.00%</div>
          <div><strong>Line coverage:</strong> 3 / 4 covered</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th class="line-number-col">Line No</th>
            <th class="source-code-col">Source Code</th>
          </tr>
        </thead>
        <tbody><tr>
        <td class="line-number-row">   1</td>
        <td class="uncovered-line">package com.example</td>
    </tr><tr>
        <td class="line-number-row">   2</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   3</td>
        <td class="not-covered-line">class AddNums {</td>
    </tr><tr>
        <td class="line-number-row">   4</td>
        <td class="uncovered-line">  companion object {</td>
    </tr><tr>
        <td class="line-number-row">   5</td>
        <td class="uncovered-line">    fun sumNumbers(a: Int, b: Int): Any {</td>
    </tr><tr>
        <td class="line-number-row">   6</td>
        <td class="covered-line">      return if (a == 0 && b == 0) {</td>
    </tr><tr>
        <td class="line-number-row">   7</td>
        <td class="covered-line">          "Both numbers are zero"</td>
    </tr><tr>
        <td class="line-number-row">   8</td>
        <td class="uncovered-line">      } else {</td>
    </tr><tr>
        <td class="line-number-row">   9</td>
        <td class="covered-line">          a + b</td>
    </tr><tr>
        <td class="line-number-row">  10</td>
        <td class="uncovered-line">      }</td>
    </tr><tr>
        <td class="line-number-row">  11</td>
        <td class="uncovered-line">    }</td>
    </tr><tr>
        <td class="line-number-row">  12</td>
        <td class="uncovered-line">  }</td>
    </tr><tr>
        <td class="line-number-row">  13</td>
        <td class="uncovered-line">}</td>
    </tr>    </tbody>
      </table>
    </body>
    </html>
      """.trimIndent()

    return htmlText
  }

  private fun initializeCommandExecutorWithLongProcessWaitTime(): CommandExecutorImpl {
    return CommandExecutorImpl(
      scriptBgDispatcher, processTimeout = 5, processTimeoutUnit = TimeUnit.MINUTES
    )
  }

  private fun createTestFileExemptionTextProto(): String {
    val testFileExemptions = TestFileExemptions.newBuilder()
      .addTestFileExemption(
        TestFileExemption.newBuilder()
          .setExemptedFilePath("TestExempted.kt")
          .setTestFileNotRequired(true)
      )
      .addTestFileExemption(
        TestFileExemption.newBuilder()
          .setExemptedFilePath("SourceIncompatibleWithCoverage.kt")
          .setSourceFileIsIncompatibleWithCodeCoverage(true)
      )
      .build()

    val testExemptionPb = "test_exemption.pb"
    val coverageTestExemptiontextProto = tempFolder.newFile(testExemptionPb)
    coverageTestExemptiontextProto.outputStream().use {
      (testFileExemptions.writeTo(it))
    }
    return "${tempFolder.root}/${testExemptionPb.removeSuffix(".pb")}"
  }
}
