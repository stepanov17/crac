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
 * @test CountDownLatchTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the checkpoint/restore does not break CountDownLatch behaviour
 * @run main CountDownLatchTest
 */

import jdk.crac.Core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class CountDownLatchTest {

    private static int N = 5;

    private static class DummyTask implements Runnable {

        private final CountDownLatch countDownLatch;
        private final int delay;
        private final AtomicInteger count;

        public DummyTask(CountDownLatch countDownLatch, AtomicInteger count, int delay) {

            this.countDownLatch = countDownLatch;
            this.delay = delay;
            this.count = count;
        }

        @Override
        public void run() {

            try { Thread.sleep(delay); }
            catch (InterruptedException ie) { throw new RuntimeException(ie); }

            count.incrementAndGet();
            countDownLatch.countDown();
        }
    }

    private static void runTest() throws Exception {

        CountDownLatch countDownLatch = new CountDownLatch(N);
        AtomicInteger count = new AtomicInteger(0);

        for (int i = 1; i <= N; ++i) {
            new Thread(new DummyTask(countDownLatch, count, 500 * i)).start();
        }

        Thread.sleep(1500);
        jdk.crac.Core.checkpointRestore();

        countDownLatch.await(); // reach this point after restore

        int c = count.get();
        if (c != N) {
            throw new RuntimeException("invalid count: " + c);
        }

        if (countDownLatch.getCount() != 0) {
            throw new RuntimeException("zero count expected");
        }

        countDownLatch.countDown(); // just check that nothing happens
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            runTest();

        } else {

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCCheckpointTo=cr", "CountDownLatchTest", "runTest");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldContain("CR: Checkpoint");
            out.shouldHaveExitValue(137);

            pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCRestoreFrom=cr", "CountDownLatchTest", "runTest");
            out = new OutputAnalyzer(pb.start());
            out.shouldHaveExitValue(0);
        }
    }
}
