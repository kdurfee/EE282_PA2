/**
 * Copyright (c) 2016 EE282 Stanford University
 * All rights reserved.
 *
 * @author Mingyu Gao (mgao12@stanford.edu)
 *
 */
package datacenter;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Vector;

import stat.SimpleStatistic;

import core.Experiment;
import core.Sim;
import core.Constants.StatName;

/**
 * Measure power for a set of servers.
 *
 * @author Mingyu Gao (mgao12@stanford.edu)
 */
public final class PowerMeasurer implements Serializable {

    /**
     * The serialization id.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The servers that are measured. */
    private Vector<Server> servers;

    /** The time period at which to measure the power. */
    private double measurePeriod;

    /** the experiment of which the measurer is part. */
    private Experiment experiment;

    /**
     * Creates a new PowerMeasure.
     *
     * @param anExperiment - the experiment the PowerMeasure is part of
     * @param theMeasurePeriod - the measure period (in seconds)
     */
    public PowerMeasurer(final Experiment anExperiment,
                         final double theMeasurePeriod) {
        this.servers = new Vector<Server>();
        this.experiment = anExperiment;
        this.measurePeriod = theMeasurePeriod;
        this.experiment.addEvent(
                new SamplePowerEvent(
                        this.measurePeriod,
                        this.experiment,
                        this));
    }

    /**
     * Adds a server to this measurer.
     *
     * @param server - the server to add to the measurer
     */
    public void addServer(final Server server) {
        this.servers.add(server);
    }

    /**
     * Measure the current power consumption for all servers.
     *
     * @param time - the time at which the sampling takes place
     */
    public void measurePower(final double time) {
        Iterator<Server> iter = this.servers.iterator();
        SimpleStatistic serverPowerStat = new SimpleStatistic();
        SimpleStatistic serverUtilStat = new SimpleStatistic();
        while (iter.hasNext()) {
            Server server = iter.next();
            serverPowerStat.addSample(server.getPower());
            serverUtilStat.addSample(server.getInstantUtilization());
        }
        this.experiment.getStats().getStat(StatName.SERVER_LEVEL_POWER)
                .addSample(Math.max(serverPowerStat.getAverage(), 0));
        this.experiment.getStats().getStat(StatName.TOTAL_POWER)
                .addSample(serverPowerStat.getTotalAccumulation());
        this.experiment.getStats().getStat(StatName.SERVER_LEVEL_UTIL)
                .addSample(Math.max(serverUtilStat.getAverage(), 0));

        this.experiment.addEvent(new SamplePowerEvent(time
                + this.measurePeriod, this.experiment, this));
    }

}
