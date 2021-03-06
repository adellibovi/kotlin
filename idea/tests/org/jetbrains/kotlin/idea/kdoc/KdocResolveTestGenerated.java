/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.kdoc;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("idea/testData/kdoc/resolve")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class KdocResolveTestGenerated extends AbstractReferenceResolveTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    public void testAllFilesPresentInResolve() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("idea/testData/kdoc/resolve"), Pattern.compile("^(.+)\\.kt$"), null, true);
    }

    @TestMetadata("AmbiguousReference.kt")
    public void testAmbiguousReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/AmbiguousReference.kt");
    }

    @TestMetadata("AmbiguousReferenceTypeParameter.kt")
    public void testAmbiguousReferenceTypeParameter() throws Exception {
        runTest("idea/testData/kdoc/resolve/AmbiguousReferenceTypeParameter.kt");
    }

    @TestMetadata("CheckExtensionReceiver.kt")
    public void testCheckExtensionReceiver() throws Exception {
        runTest("idea/testData/kdoc/resolve/CheckExtensionReceiver.kt");
    }

    @TestMetadata("ClassSelfReference.kt")
    public void testClassSelfReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/ClassSelfReference.kt");
    }

    @TestMetadata("CodeReference.kt")
    public void testCodeReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/CodeReference.kt");
    }

    @TestMetadata("CompanionObjectMember.kt")
    public void testCompanionObjectMember() throws Exception {
        runTest("idea/testData/kdoc/resolve/CompanionObjectMember.kt");
    }

    @TestMetadata("ConstructorParamReference.kt")
    public void testConstructorParamReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/ConstructorParamReference.kt");
    }

    @TestMetadata("ExtensionFromImports.kt")
    public void testExtensionFromImports() throws Exception {
        runTest("idea/testData/kdoc/resolve/ExtensionFromImports.kt");
    }

    @TestMetadata("ExtensionFun.kt")
    public void testExtensionFun() throws Exception {
        runTest("idea/testData/kdoc/resolve/ExtensionFun.kt");
    }

    @TestMetadata("ExtensionNonQualified.kt")
    public void testExtensionNonQualified() throws Exception {
        runTest("idea/testData/kdoc/resolve/ExtensionNonQualified.kt");
    }

    @TestMetadata("ExtensionVal.kt")
    public void testExtensionVal() throws Exception {
        runTest("idea/testData/kdoc/resolve/ExtensionVal.kt");
    }

    @TestMetadata("ImportAliasClass.kt")
    public void testImportAliasClass() throws Exception {
        runTest("idea/testData/kdoc/resolve/ImportAliasClass.kt");
    }

    @TestMetadata("ImportedClassReference.kt")
    public void testImportedClassReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/ImportedClassReference.kt");
    }

    @TestMetadata("OnlyMembersFromClass.kt")
    public void testOnlyMembersFromClass() throws Exception {
        runTest("idea/testData/kdoc/resolve/OnlyMembersFromClass.kt");
    }

    @TestMetadata("Overloads.kt")
    public void testOverloads() throws Exception {
        runTest("idea/testData/kdoc/resolve/Overloads.kt");
    }

    @TestMetadata("ParamReference.kt")
    public void testParamReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/ParamReference.kt");
    }

    @TestMetadata("PropertyTypeParamReference.kt")
    public void testPropertyTypeParamReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/PropertyTypeParamReference.kt");
    }

    @TestMetadata("QualifiedCodeReference.kt")
    public void testQualifiedCodeReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/QualifiedCodeReference.kt");
    }

    @TestMetadata("QualifiedNameFunctionReference.kt")
    public void testQualifiedNameFunctionReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/QualifiedNameFunctionReference.kt");
    }

    @TestMetadata("QualifiedNameReference.kt")
    public void testQualifiedNameReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/QualifiedNameReference.kt");
    }

    @TestMetadata("ReceiverReference.kt")
    public void testReceiverReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/ReceiverReference.kt");
    }

    @TestMetadata("SeeReference.kt")
    public void testSeeReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/SeeReference.kt");
    }

    @TestMetadata("StaticMember.kt")
    public void testStaticMember() throws Exception {
        runTest("idea/testData/kdoc/resolve/StaticMember.kt");
    }

    @TestMetadata("TypeParamReference.kt")
    public void testTypeParamReference() throws Exception {
        runTest("idea/testData/kdoc/resolve/TypeParamReference.kt");
    }
}
