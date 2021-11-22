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
 * @test TimerTaskOnCRPauseTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the TimerTask will be completed on restore immediately
 *          if its execution time fell on the CRaC pause period
 *          (i.e. between the checkpoint and restore)
 * @run main TimerTaskOnCRPauseTest
 */


import java.util.Timer;
import java.util.TimerTask;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class TimerTaskOnCRPauseTest {

    private static final long DT_MAX = 300_000_000; // 0.3s

    static volatile boolean done = false;

    private static void runTest() throws Exception {

        TimerTask task = new TimerTask() {

            @Override
            public void run() { done = true; }
        };

        Timer timer = new Timer();
        timer.schedule(task, 1500); // schedule task on C/R pause

        jdk.crac.Core.checkpointRestore();

        long t0 = System.nanoTime(), dt = 0;

        while (!done && (dt <= DT_MAX)) {

            Thread.onSpinWait();
            dt = System.nanoTime() - t0;
        }

        timer.cancel();

        if (!done) {
            throw new RuntimeException("the task was not completed");
        }
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            runTest();

        } else {

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCCheckpointTo=cr", "TimerTaskOnCRPauseTest", "runTest");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldContain("CR: Checkpoint");
            out.shouldHaveExitValue(137);

            // sleep a couple of seconds to ensure the task execution time
            // falls within this pause period
            Thread.sleep(2000L);

            pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCRestoreFrom=cr", "TimerTaskOnCRPauseTest", "runTest");
            out = new OutputAnalyzer(pb.start());
            out.shouldHaveExitValue(0);
        }
    }
}
