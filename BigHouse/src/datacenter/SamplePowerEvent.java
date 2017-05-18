/**
 * Copyright (c) 2016 EE282 Stanford University
 * All rights reserved.
 *
 * @author Mingyu Gao (mgao12@stanford.edu)
 *
 */
package datacenter;

import core.AbstractEvent;
import core.Experiment;

/**
 * Represents an event at which time to sample the power by PowerMeasurer.
 *
 * @author Mingyu Gao (mgao12@stanford.edu)
 */
public final class SamplePowerEvent extends AbstractEvent {

    /**
     * The serialization id.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The PowerMeasurer that is going to sample.
     */
    private PowerMeasurer measurer;

    /**
     * Creates a new SamplePowerEvent.
     *
     * @param time - the time the recalculation occurs
     * @param experiment - the experiment the event occurs in
     * @param aPowerMeasurer - the PowerMeasurer to sample
     */
    public SamplePowerEvent(final double time,
                            final Experiment experiment,
                            final PowerMeasurer aPowerMeasurer) {
        super(time, experiment);
        this.measurer = aPowerMeasurer;
    }

    /**
     * Has the PowerMeasurer sample the power of the servers.
     */
    @Override
    public void process() {
        this.measurer.measurePower(this.getTime());
    }

}
