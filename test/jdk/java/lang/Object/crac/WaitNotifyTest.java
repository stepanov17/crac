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
 * @test WaitNotifyTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the wait-notify synchronization works correctly on Checkpoint/Restore
 * @run main WaitNotifyTest
 */


import java.util.concurrent.CountDownLatch;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class WaitNotifyTest {

    private static final long T = 10_000;

    private volatile boolean ok1 = false;
    private volatile boolean ok2 = false;
    private volatile boolean ok3 = false;

    private final CountDownLatch notifyLatch = new CountDownLatch(1);

    private synchronized void w1() {

        while (!ok1) {
            try { wait(); }
            catch (InterruptedException ie) { throw new RuntimeException(ie); }
        }
    }

    private synchronized void w2() {

        while (!ok2) {
            try { wait(T); }
            catch (InterruptedException ie) { throw new RuntimeException(ie); }
        }
    }

    private synchronized void w3() {

        while (!ok3) {
            try { wait(T, 1000); }
            catch (InterruptedException ie) { throw new RuntimeException(ie); }
        }
    }

    private synchronized void n(boolean notifyAll) {

        try { notifyLatch.await(); }
        catch (InterruptedException ie) { throw new RuntimeException(ie); }

        ok1 = ok2 = ok3 = true;

        if (notifyAll) {
            notifyAll();
        } else {
            for (int i = 0; i < 3; ++i) { notify(); } // notify one by one
        }
    }

    private void runTest(boolean notifyAll) throws Exception  {

        new Thread(() -> w1()).start();
        new Thread(() -> w2()).start();
        new Thread(() -> w3()).start();

        new Thread(() -> n(notifyAll)).start();

        jdk.crac.Core.checkpointRestore();

        notifyLatch.countDown(); // notify after restore
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            new WaitNotifyTest().runTest(Boolean.parseBoolean(args[0]));

        } else {

            ProcessBuilder pb;
            OutputAnalyzer out;

            String notifyAll[] = {"true", "false"};
            for (String arg: notifyAll) {

                pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:CRaCCheckpointTo=cr", "WaitNotifyTest", arg);
                out = new OutputAnalyzer(pb.start());
                out.shouldContain("CR: Checkpoint");
                out.shouldHaveExitValue(137);


                pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:CRaCRestoreFrom=cr", "WaitNotifyTest", arg);
                out = new OutputAnalyzer(pb.start());
                out.shouldHaveExitValue(0);
            }
        }
    }
}
