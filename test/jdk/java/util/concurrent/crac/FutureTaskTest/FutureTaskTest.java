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
 * @test FutureTaskTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the checkpoint/restore does not break FutureTask behaviour
 * @run main FutureTaskTest
 */

import jdk.crac.Core;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CancellationException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class FutureTaskTest {

    private static void runTest(boolean cancel) throws Exception {

        Callable task = () -> {

            int s = 0;

            for (int i = 0; i < 10; ++i) {

                Thread.sleep(100);
                s += i;
            }

            return s;
        };

        FutureTask<Integer> future = new FutureTask<>(task);
        new Thread(future).start();

        Thread.sleep(500);

        jdk.crac.Core.checkpointRestore();

        if (cancel) {

            // check if can cancel the task after the restore correctly
            future.cancel(true);

            if (!future.isCancelled()) {
                throw new RuntimeException("isCancelled() did not return true");
            }

            if (!future.isDone()) {
                throw new RuntimeException("isDone() did not return true");
            }

            // check if get() throws CancellationException
            boolean thrown = false;
            try {
                future.get();
            } catch (CancellationException ce) {
                thrown = true;
            }
            if (!thrown) {
                throw new RuntimeException("no CancellationException was thrown");
            }

        } else { // check the result correctness

            int s = future.get();
            if (s != 45) {
                throw new RuntimeException("invalid result: " + s);
            }

            if (!future.isDone()) {
                throw new RuntimeException("isDone() did not return true");
            }

            if (future.isCancelled()) {
                throw new RuntimeException("isCancelled() returned true");
            }
        }
    }

    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            boolean cancel = Boolean.parseBoolean(args[0]);
            runTest(cancel);

        } else {

            String cancel[] = {"false", "true"};

            for (String c: cancel) {

                ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:CRaCCheckpointTo=cr", "FutureTaskTest", c);
                OutputAnalyzer out = new OutputAnalyzer(pb.start());
                out.shouldContain("CR: Checkpoint");
                out.shouldHaveExitValue(137);

                pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:CRaCRestoreFrom=cr", "FutureTaskTest", c);
                out = new OutputAnalyzer(pb.start());
                out.shouldHaveExitValue(0);
            }
        }
    }
}
