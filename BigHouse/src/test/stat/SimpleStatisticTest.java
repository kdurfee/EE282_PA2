/**
 * Copyright (c) 2011 The Regents of The University of Michigan
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met: redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer;
 * redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution;
 * neither the name of the copyright holders nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author David Meisner (meisner@umich.edu)
 *
 */
package test.stat;

import junit.framework.TestCase;
import org.junit.Test;
import stat.SimpleStatistic;

/**
 * Tests the SimpleStatisticTest.
 *
 * @see stat.SimpleStatistic
 * @author David Meisner (meisner@umich.edu)
 */
public class SimpleStatisticTest extends TestCase {

    /**
     * Tests adding a sample to a SimpleStatisticTest.
     *
     * @see stat.SimpleStatistic#addSample(double)
     */
    @Test
    public final void testAddSample() {
        final double firstSample = -1;
        final double secondSample = 1;
        final double thirdSample = 2;
        final double fourthSample = 3;
        final int count = 4;
        final double average = 1.25;
        final double standardDeviation = 1.7078;
        final double tolerance = .001;

        SimpleStatistic stat = new SimpleStatistic();

        stat.addSample(firstSample);
        stat.addSample(secondSample);
        stat.addSample(thirdSample);
        stat.addSample(fourthSample);

        assertEquals(count, stat.getCount());
        assertEquals(average, stat.getAverage());
        assertEquals(standardDeviation, stat.getStdDev(), tolerance);
    }

}
