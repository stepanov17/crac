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
 * @test JoinOrSleepOnCRPauseTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if Thread.join(timeout) and Thread.sleep(timeout)
 *          will be completed on restore immediately
 *          if their end time fell on the CRaC pause period
 *          (i.e. between the checkpoint and restore)
 * @run main/othervm JoinOrSleepOnCRPauseTest
 */


import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.concurrent.CountDownLatch;


public class JoinOrSleepOnCRPauseTest {

    private static enum TestType {join, sleep};
    private final TestType testType;

    private final boolean nsAccuracy;

    private final static long EPS_NS = Long.parseLong(
        System.getProperty("cractests.time.eps", "100000000")); // default: 0.1s

    private static final long T_MS = 2000;
    private static final  int T_NS = 100;

    private static final long CRPAUSE_MS = 4000;

    private volatile long tDone = -1;

    private final CountDownLatch checkpointLatch = new CountDownLatch(1);


    private JoinOrSleepOnCRPauseTest(TestType testType, boolean nsAccuracy) {
        this.testType   = testType;
        this.nsAccuracy = nsAccuracy;
    }

    private void runTest() throws Exception {

        String op = testType.name();

        Thread mainThread = Thread.currentThread();

        Runnable r = () -> {

            try {

                checkpointLatch.countDown();

                switch (testType) {

                    case join:
                        if (nsAccuracy) {
                            mainThread.join(T_MS, T_NS);
                        } else {
                            mainThread.join(T_MS);
                        }
                        break;

                    case sleep:
                        if (nsAccuracy) {
                            Thread.sleep(T_MS, T_NS);
                        } else {
                            Thread.sleep(T_MS);
                        }
                        break;

                    default:
                        throw new IllegalArgumentException("unknown test type");
                }

            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }

            tDone = System.nanoTime();
        };

        Thread t = new Thread(r);
        t.start();

        // this additional synchronization is probably redundant;
        // adding it to ensure we get the expected TIMED_WAITING state
        // from "our" join or sleep
        checkpointLatch.await();

        // it is expected that EPS_NS is enough to complete the join/sleep
        // on restore => expecting that 5 * EPS_NS is enough to enter them
        long dt = 5 * EPS_NS;
        Thread.sleep(dt / 1_000_000);

        if (t.getState() != Thread.State.TIMED_WAITING) {
            throw new RuntimeException("was not able to enter " + op
                + " in " + dt + " ns");
        }

        long tBeforeCheckpoint = System.nanoTime();

        jdk.crac.Core.checkpointRestore();

        long tAfterRestore = System.nanoTime();

        t.join();

        long pause = tAfterRestore - tBeforeCheckpoint;
        if (pause < 1_000_000 * CRPAUSE_MS - EPS_NS) {
            throw new RuntimeException(
                "the CR pause was less than " + CRPAUSE_MS + " ms");
        }

        if (tDone < tBeforeCheckpoint + EPS_NS) {
            throw new RuntimeException(
                op + " has finished before the checkpoint");
        }

        long eps = Math.abs(tAfterRestore - tDone);

        if (eps > EPS_NS) {
            throw new RuntimeException(
                "the " + op + "ing thread has finished in " + eps + " ns "
                + "after the restore (expected: " + EPS_NS + " ns)");
        }
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            if (args.length < 2) {
                throw new RuntimeException(
                    "please specify test parameters (testType, nsAccuracy)");
            }

            new JoinOrSleepOnCRPauseTest(
                    TestType.valueOf(args[0]),
                    Boolean.parseBoolean(args[1])).runTest();

        } else {

            ProcessBuilder pb;
            OutputAnalyzer out;

            String type[] = {"join", "sleep"};
            String nsAccuracy[] = {"false", "true"};

            for (String t: type) {
                for (String acc: nsAccuracy) {

                    pb = ProcessTools.createJavaProcessBuilder(
                        "-XX:CRaCCheckpointTo=cr", "JoinOrSleepOnCRPauseTest", t, acc);
                    out = new OutputAnalyzer(pb.start());
                    out.shouldContain("CR: Checkpoint");
                    out.shouldHaveExitValue(137);

                    // sleep a few seconds to ensure the task execution time
                    // falls within this pause period
                    Thread.sleep(CRPAUSE_MS);

                    pb = ProcessTools.createJavaProcessBuilder(
                        "-XX:CRaCRestoreFrom=cr", "JoinOrSleepOnCRPauseTest");
                    out = new OutputAnalyzer(pb.start());
                    out.shouldHaveExitValue(0);
                }
            }
        }
    }
}
