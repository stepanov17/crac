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
 * @test PhaserTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the checkpoint/restore does not break Phaser behaviour
 * @run main PhaserTest
 */

import jdk.crac.Core;

import java.util.concurrent.Phaser;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class PhaserTest {

    private static void delay(int msec) {

        try { Thread.sleep(msec); }
        catch (InterruptedException ie) { throw new RuntimeException(ie); }
    }

    private static class DummyTask implements Runnable {

        private final int n;
        private final Phaser phaser;

        public DummyTask(int n, Phaser phaser) {

            this.n = n;
            this.phaser = phaser;
            phaser.register();
        }

        @Override
        public void run() {

            // phase 0
            delay(1000 + n * 500);

            if (n % 2 == 0) {
                phaser.arriveAndDeregister(); // no phase 1 for even numbers
            } else {
                phaser.arriveAndAwaitAdvance();
                delay(700 + n * 20);
                phaser.arriveAndDeregister(); // phase 1
            }
        }
    }

    private static void runTest() throws Exception {

        Phaser phaser = new Phaser();
        phaser.register();

        ///// phase 0

        for (int i = 0; i < 5; ++i) {
            new Thread(new DummyTask(i, phaser)).start();
        }

        Thread.sleep(2000);
        jdk.crac.Core.checkpointRestore(); // C/R during the phase 0

        int phase = phaser.getPhase();

        phaser.arriveAndAwaitAdvance(); // await: phase 0
        System.out.println("phase " + phase + " is over");

        ///// phase 1 (check phaser behaviour after the restore)

        new Thread(() -> {
            phaser.register();
            delay(3000);
            phaser.arrive();
        }).start(); // add extra task

        Thread.sleep(500);

        phase = phaser.getPhase();

        phaser.arriveAndAwaitAdvance(); // await: phase 1
        System.out.println("phase " + phase + " is over");

        phaser.arriveAndDeregister();
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            runTest();

        } else {

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCCheckpointTo=cr", "PhaserTest", "runTest");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldContain("CR: Checkpoint");
            out.shouldHaveExitValue(137);

            pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCRestoreFrom=cr", "PhaserTest", "runTest");
            out = new OutputAnalyzer(pb.start());
            out.shouldContain("phase 0 is over");
            out.shouldContain("phase 1 is over");
            out.shouldHaveExitValue(0);
        }
    }
}
