/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8330998
 * @summary Verify that even if the stdout is redirected java.io.Console will
 *          use it for writing.
 * @run main RedirectedStdOut runRedirectAllTest
 * @run main/othervm RedirectedStdOut runRedirectOutOnly
 */

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class RedirectedStdOut {
    private static final String OUTPUT = "Hello!";

    public static void main(String... args) throws Throwable {
        RedirectedStdOut.class.getDeclaredMethod(args[0])
                              .invoke(new RedirectedStdOut());
    }

    //verify the case where neither stdin/out/err is attached to a terminal,
    //this test is weaker, but more reliable:
    void runRedirectAllTest() throws Exception {
        String testJDK = System.getProperty("test.jdk");
        Path javaLauncher = Path.of(testJDK, "bin", "java");
        AtomicReference<byte[]> out = new AtomicReference<>();
        AtomicReference<byte[]> err = new AtomicReference<>();
        Process launched = new ProcessBuilder(javaLauncher.toString(),
                                              "--class-path",
                                              System.getProperty("test.classes"),
                                              ConsoleTest.class.getName()
                                              )
                           .start();
        Thread outReader = Thread.ofVirtual().unstarted(() -> {
            try {
                out.set(launched.getInputStream().readAllBytes());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        outReader.start();

        Thread errReader = Thread.ofVirtual().unstarted(() -> {
            try {
                err.set(launched.getErrorStream().readAllBytes());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        errReader.start();

        int r = launched.waitFor();

        outReader.join();
        errReader.join();

        String actualOut = new String(out.get());
        String actualErr = new String(err.get());

        if (r != 0) {
            throw new AssertionError("Unexpected return value: " + r +
                                     ", actualOut: " + actualOut +
                                     ", actualErr: " + actualErr);
        }

        String expectedOut = OUTPUT;

        if (!Objects.equals(expectedOut, actualOut)) {
            throw new AssertionError("Unexpected stdout content. " +
                                     "Expected: '" + expectedOut + "'" +
                                     ", got: '" + actualOut + "'");
        }

        String expectedErr = "";

        if (!Objects.equals(expectedErr, actualErr)) {
            throw new AssertionError("Unexpected stderr content. " +
                                     "Expected: '" + expectedErr + "'" +
                                     ", got: '" + actualErr + "'");
        }
    }

    //verify the case where stdin is attached to a terminal,
    //this test allocates pty, and it might be skipped, if the appropriate
    //native functions cannot be found
    //it also leaves the VM in a broken state (with a pty attached), and so
    //should run in a separate VM instance
    void runRedirectOutOnly() throws Throwable {
        Path stdout = Path.of(".", "stdout.txt").toAbsolutePath();

        Files.deleteIfExists(stdout);

        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();
        MemorySegment parent = Arena.global().allocate(ValueLayout.ADDRESS);
        MemorySegment child = Arena.global().allocate(ValueLayout.ADDRESS);
        Optional<MemorySegment> openptyAddress = stdlib.find("openpty");

        if (openptyAddress.isEmpty()) {
            System.out.println("Cannot lookup openpty.");
            //does not have forkpty, ignore
            return ;
        }

        Optional<MemorySegment> loginttyAddress = stdlib.find("login_tty");

        if (loginttyAddress.isEmpty()) {
            System.out.println("Cannot lookup login_tty.");
            //does not have forkpty, ignore
            return ;
        }

        FunctionDescriptor openttyDescriptor =
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                      ValueLayout.ADDRESS,
                                      ValueLayout.ADDRESS,
                                      ValueLayout.ADDRESS,
                                      ValueLayout.ADDRESS,
                                      ValueLayout.ADDRESS);
        MethodHandle forkpty = linker.downcallHandle(openptyAddress.get(),
                                                     openttyDescriptor);
        int res = (int) forkpty.invoke(parent,
                                       child,
                                       MemorySegment.NULL,
                                       MemorySegment.NULL,
                                       MemorySegment.NULL);

        if (res != 0) {
            throw new AssertionError();
        }

        //set the current VM's in/out to the terminal:
        FunctionDescriptor loginttyDescriptor =
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                      ValueLayout.JAVA_INT);
        MethodHandle logintty = linker.downcallHandle(loginttyAddress.get(),
                                                      loginttyDescriptor);
        logintty.invoke(child.get(ValueLayout.JAVA_INT, 0));

        String testJDK = System.getProperty("test.jdk");
        Path javaLauncher = Path.of(testJDK, "bin", "java");

        ProcessBuilder builder = new ProcessBuilder(javaLauncher.toString(),
                                                    "RedirectedStdOut$ConsoleTest");

        builder.inheritIO();
        builder.redirectOutput(stdout.toFile());

        Process launched = builder.start();

        launched.waitFor();

        String expectedOut = OUTPUT;
        String actualOut = Files.readString(stdout);

        if (!Objects.equals(expectedOut, actualOut)) {
            throw new AssertionError("Unexpected stdout content. " +
                                     "Expected: '" + expectedOut + "'" +
                                     ", got: '" + actualOut + "'");
        }
    }

    public static class ConsoleTest {
        public static void main(String... args) {
            System.console().printf(OUTPUT);
            System.exit(0);
        }
    }
}
