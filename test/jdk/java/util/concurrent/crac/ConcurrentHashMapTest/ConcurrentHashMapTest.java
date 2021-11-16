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
 * @test ConcurrentHashMapTest.java
 * @requires (os.family == "linux")
 * @library /test/lib
 * @summary check if the checkpoint/restore does not break ConcurrentHashMap behaviour
 * @run main ConcurrentHashMapTest
 */

import jdk.crac.Core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class ConcurrentHashMapTest {

    private static final int N = 750;


    private static void runTest() throws Exception {

        Map<String, Integer> map = new ConcurrentHashMap<>();
        List<Integer> values = new ArrayList<>();

        Thread cr = new Thread(() -> {
            try {
                Thread.sleep(3000);
                jdk.crac.Core.checkpointRestore();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        cr.start();

        for (int i = 0; i < N; i++) {

            map.put("key", 0);
            ExecutorService pool = Executors.newFixedThreadPool(4);

            for (int j = 0; j < N; ++j) {
                pool.execute(() -> {
                    for (int k = 0; k < N; ++k) {
                        map.computeIfPresent("key", (key, value) -> value + 1);
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(3, TimeUnit.SECONDS);
            values.add(map.get("key"));
        }

        cr.join(); // just in case..

        // check if the values are equal
        long distinctCount = values.stream().distinct().count();
        if (distinctCount > 1) {
            throw new RuntimeException("test failed, different values found");
        }
    }


    public static void main(String args[]) throws Exception {

        if (args.length > 0) {

            runTest();

        } else {

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCCheckpointTo=cr", "ConcurrentHashMapTest", "runTest");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldContain("CR: Checkpoint");
            out.shouldHaveExitValue(137);

            pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CRaCRestoreFrom=cr", "ConcurrentHashMapTest", "runTest");
            out = new OutputAnalyzer(pb.start());
            out.shouldHaveExitValue(0);
        }
    }
}
