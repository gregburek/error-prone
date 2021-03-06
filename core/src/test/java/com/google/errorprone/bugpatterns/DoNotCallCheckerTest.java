/*
 * Copyright 2017 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns;

import com.google.common.io.ByteStreams;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.annotations.DoNotCall;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link DoNotCallChecker}Test */
@RunWith(JUnit4.class)
public class DoNotCallCheckerTest {

  private final CompilationTestHelper testHelper =
      CompilationTestHelper.newInstance(DoNotCallChecker.class, getClass());

  @Test
  public void positive() {
    testHelper
        .addSourceLines(
            "Test.java",
            "import com.google.errorprone.annotations.DoNotCall;",
            "class Test {",
            "  @DoNotCall(\"satisfying explanation\") final void f() {}",
            "  @DoNotCall final void g() {}",
            "  void m() {",
            "    // BUG: Diagnostic contains:",
            "    // This method should not be called: satisfying explanation",
            "    f();",
            "    // BUG: Diagnostic contains:",
            "    // This method should not be called, see its documentation for details",
            "    g();",
            "    // BUG: Diagnostic contains:",
            "    Runnable r = this::g;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void concreteFinal() {
    testHelper
        .addSourceLines(
            "Test.java",
            "import com.google.errorprone.annotations.DoNotCall;",
            "public class Test {",
            "  // BUG: Diagnostic contains: should be final",
            "  @DoNotCall public void f() {}",
            "  @DoNotCall public final void g() {}",
            "}")
        .doTest();
  }

  @Test
  public void requiredOverride() {
    testHelper
        .addSourceLines(
            "A.java",
            "import com.google.errorprone.annotations.DoNotCall;",
            "public interface A {",
            "  @DoNotCall public void f();",
            "}")
        .addSourceLines(
            "B.java",
            "public class B implements A {",
            "  // BUG: Diagnostic contains: overrides f in A which is annotated",
            "  @Override public void f() {}",
            "}")
        .doTest();
  }

  @Test
  public void annotatedOverride() {
    testHelper
        .addSourceLines(
            "A.java",
            "import com.google.errorprone.annotations.DoNotCall;",
            "public interface A {",
            "  @DoNotCall public void f();",
            "}")
        .addSourceLines(
            "B.java",
            "import com.google.errorprone.annotations.DoNotCall;",
            "public class B implements A {",
            "  @DoNotCall @Override public final void f() {}",
            "}")
        .doTest();
  }

  // The interface tries to make Object#toString @DoNotCall, and because
  // the declaration in B is implicit it doesn't get checked.
  // In practice, making default Object methods @DoNotCall isn't super
  // useful - typically users interface with the interface directly
  // (e.g. Hasher) or there's an override that has unwanted behaviour (Localizable).
  // TODO(cushon): check class declarations for super-interfaces that do this?
  @Test
  public void interfaceRedeclaresObjectMethod() {
    testHelper
        .addSourceLines(
            "I.java",
            "import com.google.errorprone.annotations.DoNotCall;",
            "public interface I {",
            "  @DoNotCall public String toString();",
            "}")
        .addSourceLines(
            "B.java", //
            "public class B implements I {",
            "}")
        .addSourceLines(
            "Test.java", //
            "public class Test {",
            "  void f(B b) {",
            "    b.toString();",
            "    I i = b;",
            "    // BUG: Diagnostic contains:",
            "    i.toString();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void finalClass() {
    testHelper
        .addSourceLines(
            "Test.java",
            "import com.google.errorprone.annotations.DoNotCall;",
            "public final class Test {",
            "  @DoNotCall public void f() {}",
            "}")
        .doTest();
  }

  @Test
  public void privateMethod() {
    testHelper
        .addSourceLines(
            "Test.java",
            "import com.google.errorprone.annotations.DoNotCall;",
            "public final class Test {",
            "  // BUG: Diagnostic contains: private method",
            "  @DoNotCall private void f() {}",
            "}")
        .doTest();
  }

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  /** Test class containing a method annotated with @DNC. */
  public static class DNCTest {
    @DoNotCall
    public static final void f() {}
  }

  static void addClassToJar(JarOutputStream jos, Class<?> clazz) throws IOException {
    String entryPath = clazz.getName().replace('.', '/') + ".class";
    try (InputStream is = clazz.getClassLoader().getResourceAsStream(entryPath)) {
      jos.putNextEntry(new JarEntry(entryPath));
      ByteStreams.copy(is, jos);
    }
  }

  @Test
  public void noDNConClasspath() throws Exception {
    File libJar = tempFolder.newFile("lib.jar");
    try (FileOutputStream fis = new FileOutputStream(libJar);
        JarOutputStream jos = new JarOutputStream(fis)) {
      addClassToJar(jos, DNCTest.class);
      addClassToJar(jos, DoNotCallCheckerTest.class);
    }
    testHelper
        .addSourceLines(
            "Test.java",
            "class Test {",
            "  void m() {",
            "    // BUG: Diagnostic contains: This method should not be called",
            "    com.google.errorprone.bugpatterns.DoNotCallCheckerTest.DNCTest.f();",
            "  }",
            "}")
        .setArgs(Arrays.asList("-cp", libJar.toString()))
        .doTest();
  }
}
