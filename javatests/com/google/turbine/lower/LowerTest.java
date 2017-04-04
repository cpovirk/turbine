/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.turbine.lower;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.turbine.binder.Binder;
import com.google.turbine.binder.Binder.BindingResult;
import com.google.turbine.binder.ClassPathBinder;
import com.google.turbine.binder.bound.SourceTypeBoundClass;
import com.google.turbine.binder.bytecode.BytecodeBoundClass;
import com.google.turbine.binder.env.CompoundEnv;
import com.google.turbine.binder.env.SimpleEnv;
import com.google.turbine.binder.lookup.TopLevelIndex;
import com.google.turbine.binder.sym.ClassSymbol;
import com.google.turbine.binder.sym.FieldSymbol;
import com.google.turbine.binder.sym.MethodSymbol;
import com.google.turbine.binder.sym.TyVarSymbol;
import com.google.turbine.bytecode.AsmUtils;
import com.google.turbine.bytecode.JavapUtils;
import com.google.turbine.model.TurbineConstantTypeKind;
import com.google.turbine.model.TurbineFlag;
import com.google.turbine.model.TurbineTyKind;
import com.google.turbine.parse.Parser;
import com.google.turbine.type.Type;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

@RunWith(JUnit4.class)
public class LowerTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final ImmutableList<Path> BOOTCLASSPATH =
      ImmutableList.of(Paths.get(System.getProperty("java.home")).resolve("lib/rt.jar"));

  @Test
  public void hello() throws Exception {
    CompoundEnv<ClassSymbol, BytecodeBoundClass> classpath =
        ClassPathBinder.bind(ImmutableList.of(), BOOTCLASSPATH, TopLevelIndex.builder());

    ImmutableList<Type.ClassTy> interfaceTypes =
        ImmutableList.of(
            new Type.ClassTy(
                ImmutableList.of(
                    new Type.ClassTy.SimpleClassTy(
                        new ClassSymbol("java/util/List"),
                        ImmutableList.of(
                            new Type.TyVar(
                                new TyVarSymbol(new ClassSymbol("test/Test"), "V"),
                                ImmutableList.of())),
                        ImmutableList.of()))));
    Type.ClassTy xtnds = Type.ClassTy.OBJECT;
    ImmutableMap<TyVarSymbol, SourceTypeBoundClass.TyVarInfo> tps =
        ImmutableMap.of(
            new TyVarSymbol(new ClassSymbol("test/Test"), "V"),
            new SourceTypeBoundClass.TyVarInfo(
                new Type.ClassTy(
                    ImmutableList.of(
                        new Type.ClassTy.SimpleClassTy(
                            new ClassSymbol("test/Test$Inner"),
                            ImmutableList.of(),
                            ImmutableList.of()))),
                ImmutableList.of(),
                ImmutableList.of()));
    int access = TurbineFlag.ACC_SUPER | TurbineFlag.ACC_PUBLIC;
    ImmutableList<SourceTypeBoundClass.MethodInfo> methods =
        ImmutableList.of(
            new SourceTypeBoundClass.MethodInfo(
                new MethodSymbol(new ClassSymbol("test/Test"), "f"),
                ImmutableMap.of(),
                new Type.PrimTy(TurbineConstantTypeKind.INT, ImmutableList.of()),
                ImmutableList.of(),
                ImmutableList.of(),
                TurbineFlag.ACC_STATIC | TurbineFlag.ACC_PUBLIC,
                null,
                null,
                ImmutableList.of(),
                null),
            new SourceTypeBoundClass.MethodInfo(
                new MethodSymbol(new ClassSymbol("test/Test"), "g"),
                ImmutableMap.of(
                    new TyVarSymbol(new MethodSymbol(new ClassSymbol("test/Test"), "g"), "V"),
                    new SourceTypeBoundClass.TyVarInfo(
                        null,
                        ImmutableList.of(
                            new Type.ClassTy(
                                ImmutableList.of(
                                    new Type.ClassTy.SimpleClassTy(
                                        new ClassSymbol("java/lang/Runnable"),
                                        ImmutableList.of(),
                                        ImmutableList.of())))),
                        ImmutableList.of()),
                    new TyVarSymbol(new MethodSymbol(new ClassSymbol("test/Test"), "g"), "E"),
                    new SourceTypeBoundClass.TyVarInfo(
                        new Type.ClassTy(
                            ImmutableList.of(
                                new Type.ClassTy.SimpleClassTy(
                                    new ClassSymbol("java/lang/Error"),
                                    ImmutableList.of(),
                                    ImmutableList.of()))),
                        ImmutableList.of(),
                        ImmutableList.of())),
                Type.VOID,
                ImmutableList.of(
                    new SourceTypeBoundClass.ParamInfo(
                        new Type.PrimTy(TurbineConstantTypeKind.INT, ImmutableList.of()),
                        "foo",
                        ImmutableList.of(),
                        0)),
                ImmutableList.of(
                    new Type.TyVar(
                        new TyVarSymbol(new MethodSymbol(new ClassSymbol("test/Test"), "g"), "E"),
                        ImmutableList.of())),
                TurbineFlag.ACC_PUBLIC,
                null,
                null,
                ImmutableList.of(),
                null));
    ImmutableList<SourceTypeBoundClass.FieldInfo> fields =
        ImmutableList.of(
            new SourceTypeBoundClass.FieldInfo(
                new FieldSymbol(new ClassSymbol("test/Test"), "theField"),
                Type.ClassTy.asNonParametricClassTy(new ClassSymbol("test/Test$Inner")),
                TurbineFlag.ACC_STATIC | TurbineFlag.ACC_FINAL | TurbineFlag.ACC_PUBLIC,
                ImmutableList.of(),
                null,
                null));
    ClassSymbol owner = null;
    TurbineTyKind kind = TurbineTyKind.CLASS;
    ImmutableMap<String, ClassSymbol> children = ImmutableMap.of();
    ImmutableMap<String, TyVarSymbol> tyParams =
        ImmutableMap.of("V", new TyVarSymbol(new ClassSymbol("test/Test"), "V"));

    SourceTypeBoundClass c =
        new SourceTypeBoundClass(
            interfaceTypes,
            xtnds,
            tps,
            access,
            methods,
            fields,
            owner,
            kind,
            children,
            tyParams,
            null,
            null,
            null,
            null,
            ImmutableList.of(),
            null);

    SourceTypeBoundClass i =
        new SourceTypeBoundClass(
            ImmutableList.of(),
            Type.ClassTy.OBJECT,
            ImmutableMap.of(),
            TurbineFlag.ACC_STATIC | TurbineFlag.ACC_PROTECTED,
            ImmutableList.of(),
            ImmutableList.of(),
            new ClassSymbol("test/Test"),
            TurbineTyKind.CLASS,
            ImmutableMap.of("Inner", new ClassSymbol("test/Test$Inner")),
            ImmutableMap.of(),
            null,
            null,
            null,
            null,
            ImmutableList.of(),
            null);

    SimpleEnv.Builder<ClassSymbol, SourceTypeBoundClass> b = SimpleEnv.builder();
    b.put(new ClassSymbol("test/Test"), c);
    b.put(new ClassSymbol("test/Test$Inner"), i);

    Map<String, byte[]> bytes =
        Lower.lowerAll(
                ImmutableMap.of(
                    new ClassSymbol("test/Test"), c, new ClassSymbol("test/Test$Inner"), i),
                classpath)
            .bytes();

    assertThat(AsmUtils.textify(bytes.get("test/Test")))
        .isEqualTo(
            new String(
                ByteStreams.toByteArray(
                    LowerTest.class.getResourceAsStream("testdata/golden/outer.txt")),
                UTF_8));
    assertThat(AsmUtils.textify(bytes.get("test/Test$Inner")))
        .isEqualTo(
            new String(
                ByteStreams.toByteArray(
                    LowerTest.class.getResourceAsStream("testdata/golden/inner.txt")),
                UTF_8));
  }

  @Test
  public void innerClassAttributeOrder() throws IOException {
    BindingResult bound =
        Binder.bind(
            ImmutableList.of(
                Parser.parse(
                    Joiner.on('\n')
                        .join(
                            "class Test {", //
                            "  class Inner {",
                            "    class InnerMost {}",
                            "  }",
                            "}"))),
            ImmutableList.of(),
            BOOTCLASSPATH);
    Map<String, byte[]> lowered = Lower.lowerAll(bound.units(), bound.classPathEnv()).bytes();
    List<String> attributes = new ArrayList<>();
    new org.objectweb.asm.ClassReader(lowered.get("Test$Inner$InnerMost"))
        .accept(
            new ClassVisitor(Opcodes.ASM5) {
              @Override
              public void visitInnerClass(
                  String name, String outerName, String innerName, int access) {
                attributes.add(String.format("%s %s %s", name, outerName, innerName));
              }
            },
            0);
    assertThat(attributes)
        .containsExactly("Test$Inner Test Inner", "Test$Inner$InnerMost Test$Inner InnerMost")
        .inOrder();
  }

  @Test
  public void wildArrayElement() throws Exception {
    IntegrationTestSupport.TestInput input =
        IntegrationTestSupport.TestInput.parse(
            new String(
                ByteStreams.toByteArray(
                    getClass().getResourceAsStream("testdata/canon_array.test")),
                UTF_8));

    Map<String, byte[]> actual =
        IntegrationTestSupport.runTurbine(input.sources, ImmutableList.of(), BOOTCLASSPATH);

    assertThat(JavapUtils.dump("Test", actual.get("Test"), ImmutableList.of("-s", "-private")))
        .isEqualTo(
            Joiner.on('\n')
                .join(
                    "class Test {",
                    "  A<?[]>.I i;",
                    "    descriptor: LA$I;",
                    "  Test();",
                    "    descriptor: ()V",
                    "}",
                    ""));
  }

  @Test
  public void typePath() throws Exception {
    BindingResult bound =
        Binder.bind(
            ImmutableList.of(
                Parser.parse(
                    Joiner.on('\n')
                        .join(
                            "import java.lang.annotation.ElementType;",
                            "import java.lang.annotation.Target;",
                            "import java.util.List;",
                            "@Target({ElementType.TYPE_USE}) @interface Anno {}",
                            "class Test {",
                            "  public @Anno int[][] xs;",
                            "}"))),
            ImmutableList.of(),
            BOOTCLASSPATH);
    Map<String, byte[]> lowered = Lower.lowerAll(bound.units(), bound.classPathEnv()).bytes();
    TypePath[] path = new TypePath[1];
    new ClassReader(lowered.get("Test"))
        .accept(
            new ClassVisitor(Opcodes.ASM5) {
              @Override
              public FieldVisitor visitField(
                  int access, String name, String desc, String signature, Object value) {
                return new FieldVisitor(Opcodes.ASM5) {
                  @Override
                  public AnnotationVisitor visitTypeAnnotation(
                      int typeRef, TypePath typePath, String desc, boolean visible) {
                    path[0] = typePath;
                    return null;
                  };
                };
              }
            },
            0);
    assertThat(path[0].getLength()).isEqualTo(2);
    assertThat(path[0].getStep(0)).isEqualTo(TypePath.ARRAY_ELEMENT);
    assertThat(path[0].getStepArgument(0)).isEqualTo(0);
    assertThat(path[0].getStep(1)).isEqualTo(TypePath.ARRAY_ELEMENT);
    assertThat(path[0].getStepArgument(1)).isEqualTo(0);
  }

  @Test
  public void invalidConstants() throws Exception {
    Path lib = temporaryFolder.newFile("lib.jar").toPath();
    try (OutputStream os = Files.newOutputStream(lib);
        JarOutputStream jos = new JarOutputStream(os)) {
      jos.putNextEntry(new JarEntry("Lib.class"));

      ClassWriter cw = new ClassWriter(0);
      cw.visit(52, Opcodes.ACC_SUPER, "Lib", null, "java/lang/Object", null);
      cw.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_STATIC, "ZCONST", "Z", null, Integer.MAX_VALUE);
      cw.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_STATIC, "SCONST", "S", null, Integer.MAX_VALUE);
      jos.write(cw.toByteArray());
    }

    ImmutableMap<String, String> input =
        ImmutableMap.of(
            "Test.java",
            Joiner.on('\n')
                .join(
                    "class Test {",
                    "  static final short SCONST = Lib.SCONST + 0;",
                    "  static final boolean ZCONST = Lib.ZCONST || false;",
                    "}"));

    Map<String, byte[]> actual =
        IntegrationTestSupport.runTurbine(input, ImmutableList.of(lib), BOOTCLASSPATH);

    Map<String, Object> values = new LinkedHashMap<>();
    new ClassReader(actual.get("Test"))
        .accept(
            new ClassVisitor(Opcodes.ASM5) {
              @Override
              public FieldVisitor visitField(
                  int access, String name, String desc, String signature, Object value) {
                values.put(name, value);
                return super.visitField(access, name, desc, signature, value);
              }
            },
            0);

    assertThat(values).containsEntry("SCONST", -1);
    assertThat(values).containsEntry("ZCONST", 1);
  }

  @Test
  public void deprecated() throws Exception {
    BindingResult bound =
        Binder.bind(
            ImmutableList.of(Parser.parse("@Deprecated class Test {}")),
            ImmutableList.of(),
            BOOTCLASSPATH);
    Map<String, byte[]> lowered = Lower.lowerAll(bound.units(), bound.classPathEnv()).bytes();
    int[] acc = {0};
    new ClassReader(lowered.get("Test"))
        .accept(
            new ClassVisitor(Opcodes.ASM5) {
              @Override
              public void visit(
                  int version,
                  int access,
                  String name,
                  String signature,
                  String superName,
                  String[] interfaces) {
                acc[0] = access;
              }
            },
            0);
    assertThat((acc[0] & Opcodes.ACC_DEPRECATED) == Opcodes.ACC_DEPRECATED).isTrue();
  }
}
