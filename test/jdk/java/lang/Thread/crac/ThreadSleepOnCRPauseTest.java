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
 * @test ThreadSleepOnCRPauseTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the Thread.sleep() will be completed on restore immediately
 *          if its end time fell on the CRaC pause period
 *          (i.e. between the checkpoint and restore)
 * @run main ThreadSleepOnCRPauseTest
 */



import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.concurrent.CountDownLatch;


public class ThreadSleepOnCRPauseTest {

    private final static long SLEEP   = 1500; // [ms]
    private final static long CRPAUSE = 2000; // [ms]

    private final static long EPS = 300_000_000; // [ns], i.e. 0.3 s

    private final CountDownLatch sleepLatch = new CountDownLatch(1);

    private volatile long end1Time = 0;
    private volatile long end2Time = 0;


    private void runTest() throws Exception {

        Runnable r1 = () -> {

            try {
                sleepLatch.await();
                Thread.sleep(SLEEP);
                end1Time = System.nanoTime();
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        };

        Runnable r2 = () -> {

            try {
                sleepLatch.await();
                Thread.sleep(SLEEP, 20);
                end2Time = System.nanoTime();
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        };

        Thread t1 = new Thread(r1), t2 = new Thread(r2);
        t1.start();
        t2.start();
        sleepLatch.countDown(); // sleep simultaneously

        while ((t1.getState() != Thread.State.TIMED_WAITING) &&
               (t2.getState() != Thread.State.TIMED_WAITING)) {
            Thread.onSpinWait();
        }

        long beforeCheckpoint = System.nanoTime();

        jdk.crac.Core.checkpointRestore();

        long afterRestore = System.nanoTime();

        t1.join();
        t2.join();

        // being paranoid #1
        long pause = afterRestore - beforeCheckpoint;
        if (pause < 1_000_000 * CRPAUSE) {
            throw new RuntimeException("the CR pause was less than " + CRPAUSE + " msec");
        }

        // being paranoid #2
        if (end1Time < beforeCheckpoint || end2Time < beforeCheckpoint) {
            throw new RuntimeException("sleep finished before checkpoint for at least one thread");
        }

        if (Math.max(
                Math.abs(afterRestore - end1Time),
                Math.abs(afterRestore - end2Time))  > EPS) {
            throw new RuntimeException("the sleeping threads were not "
                    + "finished in " + EPS + " ns");
        }
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            new ThreadSleepOnCRPauseTest().runTest();

        } else {

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCCheckpointTo=cr", "ThreadSleepOnCRPauseTest", "runTest");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldContain("CR: Checkpoint");
            out.shouldHaveExitValue(137);

            // sleep a couple of seconds to ensure the task execution time
            // falls within this pause period
            Thread.sleep(CRPAUSE);

            pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCRestoreFrom=cr", "ThreadSleepOnCRPauseTest", "runTest");
            out = new OutputAnalyzer(pb.start());
            out.shouldHaveExitValue(0);
        }
    }
}
