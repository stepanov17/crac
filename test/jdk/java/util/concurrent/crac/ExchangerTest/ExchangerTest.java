// Copyright 2019-2021 Azul Systems, Inc.  All Rights Reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it under
// the terms of the GNU General Public License version 2 only, as published by
// the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
// A PARTICULAR PURPOSE.  See the GNU General Public License version 2 for more
// details (a copy is included in the LICENSE file that accompanied this code).
//
// You should have received a copy of the GNU General Public License version 2
// along with this work; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Azul Systems, 385 Moffett Park Drive, Suite 115, Sunnyvale,
// CA 94089 USA or visit www.azul.com if you need additional information or
// have any questions.

/*
 * @test ExchangerTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the checkpoint/restore does not break Exchanger behaviour
 * @run main ExchangerTest
 */

import jdk.crac.Core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Exchanger;
import static java.util.concurrent.CompletableFuture.runAsync;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class ExchangerTest {

    private static final String
            MSGA = "AAAAA AAAAA AAAAA AAAAA AAAAA AAAAA AAAAA AAAAA AAAAA AAAAA",
            MSGB = "BBBBBBBBBB   bbbbbbbbbb   BBBBBBBBBB";

    private static void runTest() {

        Exchanger<String> exchanger = new Exchanger<>();

        Runnable taskA = () -> {

            try {

                String message = exchanger.exchange(MSGA);

                if (!message.equals(MSGB)) {
                    throw new RuntimeException("invalid B message");
                }
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        };

        Runnable taskB = () -> {

            try {

                Thread.sleep(5000);

                String message = exchanger.exchange(MSGB);
                if (!message.equals(MSGA)) {
                    throw new RuntimeException("invalid B message");
                }
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        };

        Runnable cr = () -> {

            try {

                Thread.sleep(2000);
                jdk.crac.Core.checkpointRestore();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // C/R between exchange A and exchange B
        CompletableFuture.allOf(
                runAsync(taskA), runAsync(taskB), runAsync(cr)).join();
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            runTest();

        } else {

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCCheckpointTo=cr", "ExchangerTest", "runTest");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldContain("CR: Checkpoint");
            out.shouldHaveExitValue(137);

            pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCRestoreFrom=cr", "ExchangerTest", "runTest");
            out = new OutputAnalyzer(pb.start());
            out.shouldHaveExitValue(0);
        }
    }
}
