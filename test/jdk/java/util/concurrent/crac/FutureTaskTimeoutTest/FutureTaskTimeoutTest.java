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
 * @test FutureTaskTimeoutTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the TimeoutException thrown after checkpoint/restore for the FutureTask, as expected
 * @run main FutureTaskTimeoutTest
 */

import jdk.crac.Core;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class FutureTaskTimeoutTest {

    private static void runTest() {

        Runnable cr = () -> {

            try {
                Thread.sleep(700);
                jdk.crac.Core.checkpointRestore();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };


        Callable task = () -> {

            Thread.sleep(1000);
            return 0.123;
        };

        FutureTask<Double> future = new FutureTask<>(task);
        Thread t = new Thread(future);
        Thread tcr = new Thread(cr);

        t.start();
        tcr.start();

        boolean timedOut = false;

        try {
            future.get(900, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            timedOut = true;
        }

        if (!timedOut) {
            throw new RuntimeException("TimeoutException was not thrown");
        }
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            runTest();

        } else {

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCCheckpointTo=cr", "FutureTaskTimeoutTest", "runTest");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldContain("CR: Checkpoint");
            out.shouldHaveExitValue(137);

            pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCRestoreFrom=cr", "FutureTaskTimeoutTest", "runTest");
            out = new OutputAnalyzer(pb.start());
            out.shouldHaveExitValue(0);
        }
    }
}
