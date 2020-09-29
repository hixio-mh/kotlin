/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.util.allJavaFiles
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/** Tests that the outputs of a build are deterministic. */
class DeterministicBuildIT : BaseGradleIT() {

    @Test
    fun `test KaptGenerateStubsTask - KT-40882`() = with(
        Project("simple", directoryPrefix = "kapt2")
    ) {
        setupWorkingDir()
        projectDir
            .resolve("src/main/java/BlockUserItemHydrator.kt")
            .writeText(
                """
                class BlockUserItemHydrator : RichFeedbackActionItemHydrator {
                    
                    fun getPrompt(data: TwitterUser): String = "Original text"
                }
                """.trimIndent()
            )
        projectDir
            .resolve("src/main/java/RichFeedbackActionItemHydrator.kt")
            .writeText(
                """
                interface RichFeedbackActionItemHydrator {
                
                    fun hydrate(prompt: FeedbackAction.PromptInfo): FeedbackAction.PromptInfo? = null
                
                    fun hydrate(action: FeedbackAction): FeedbackAction? = null
                }
                """.trimIndent()
            )
        projectDir
            .resolve("src/main/java/FeedbackAction.kt")
            .writeText(
                """
                class FeedbackAction {
                    class PromptInfo
                }
                """.trimIndent()
            )

        val buildAndSnapshotStubFiles: () -> Map<File, String> = {
            lateinit var stubFiles: Map<File, String>
            build(":kaptGenerateStubsKotlin") {
                assertSuccessful()
                assertTasksExecuted(":kaptGenerateStubsKotlin")
                stubFiles = fileInWorkingDir("build/tmp/kapt3/stubs").allJavaFiles().map {
                    it to it.readText()
                }.toMap()
            }
            stubFiles
        }

        // Run the first build
        val stubFilesAfterFirstBuild = buildAndSnapshotStubFiles()

        // Make a change
        projectDir.resolve("src/main/java/BlockUserItemHydrator.kt").also {
            it.writeText(
                """
                class BlockUserItemHydrator : RichFeedbackActionItemHydrator {
                    
                    fun getPrompt(data: TwitterUser): String = "Updated text to trigger change"
                }
                """.trimIndent()
            )
        }

        // Run the second build
        val stubFilesAfterSecondBuild = buildAndSnapshotStubFiles()

        // Check that the build outputs are deterministic
        assertEquals(stubFilesAfterFirstBuild.size, stubFilesAfterSecondBuild.size)
        for (file in stubFilesAfterFirstBuild.keys) {
            val fileContentsAfterFirstBuild = stubFilesAfterFirstBuild[file]
            val fileContentsAfterSecondBuild = stubFilesAfterSecondBuild[file]
            assertEquals(fileContentsAfterFirstBuild, fileContentsAfterSecondBuild)
        }
    }
}