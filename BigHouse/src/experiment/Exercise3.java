/**
 * Copyright (c) 2016 EE282 Stanford University
 * All rights reserved.
 *
 * @author Mingyu Gao (mgao12@stanford.edu)
 *
 */
package experiment;

import generator.EmpiricalGenerator;
import generator.MTRandom;
import math.EmpiricalDistribution;
import core.Experiment;
import core.ExperimentInput;
import core.ExperimentOutput;
import core.Constants.StatName;
import core.Constants.TimeWeightedStatName;
import datacenter.DataCenter;
import datacenter.Server;
import datacenter.Core.CorePowerPolicy;
import datacenter.Socket.SocketPowerPolicy;
import datacenter.PowerMeasurer;

public class Exercise3 {

    public Exercise3() {}

    public void run(String workloadDir, String workload, int nServersA, int nServersB) {

        // service file
        String arrivalFile = workloadDir + "/workloads/" + workload + ".arrival.cdf";
        String serviceAFile = workloadDir + "/workloads/" + workload + ".service.A.cdf";
        //
        // TODO: YOUR CODE HERE
        // serviceBFile
        //

        // specify distribution
        int cores = 4;
        int sockets = 1;
        double targetRho = .5;

        EmpiricalDistribution arrivalDistribution = EmpiricalDistribution.loadDistribution(arrivalFile, 1e-3);
        EmpiricalDistribution serviceADistribution = EmpiricalDistribution.loadDistribution(serviceAFile, 1e-3);
        //
        // TODO: YOUR CODE HERE
        // serviceBDistribution
        //

        double averageInterarrival = arrivalDistribution.getMean();
        double averageServiceTime = serviceADistribution.getMean();
        double qps = 1 / averageInterarrival;
        double rho = qps / (cores * (1 / averageServiceTime));
        double arrivalScale = rho / targetRho;
        arrivalScale *= (nServersA + nServersB) / 300;
        averageInterarrival = averageInterarrival * arrivalScale;
        double serviceRate = 1 / averageServiceTime;
        double scaledQps = qps / arrivalScale;

        MTRandom rand = new MTRandom(1);
        EmpiricalGenerator arrivalGenerator  = new EmpiricalGenerator(rand, arrivalDistribution, "arrival", arrivalScale);
        EmpiricalGenerator serviceAGenerator  = new EmpiricalGenerator(rand, serviceADistribution, "serviceA", 1.0);
        //
        // TODO: YOUR CODE HERE
        // serviceBGenerator
        //

        // setup experiment
        ExperimentInput experimentInput = new ExperimentInput();

        ExperimentOutput experimentOutput = new ExperimentOutput();
        experimentOutput.addOutput(StatName.SOJOURN_TIME, .05, .95, .05, 5000);

        Experiment experiment = new Experiment("Exercise 3", rand, experimentInput, experimentOutput);

        // setup datacenter
        DataCenter dataCenter = new DataCenter();

        // setup power measurer
        double measurePeriod = 1.0;
        PowerMeasurer measurer = new PowerMeasurer(experiment, measurePeriod);

        for(int i = 0; i < nServersA; i++) {
            Server server = new Server(sockets, cores, experiment, arrivalGenerator, serviceAGenerator);

            server.setSocketPolicy(SocketPowerPolicy.NO_MANAGEMENT);
            server.setCorePolicy(CorePowerPolicy.NO_MANAGEMENT);

            dataCenter.addServer(server);
            measurer.addServer(server);
        }

        //
        // TODO: YOUR CODE HERE
        // set up server type B
        //

        experimentInput.setDataCenter(dataCenter);

        // run the experiment
        experiment.run();

        // display results
        System.out.println("====== Results ======");
        double responseTimeMean = experiment.getStats().getStat(StatName.SOJOURN_TIME).getAverage();
        System.out.println("Response Mean: " + responseTimeMean);
        double responseTime95th = experiment.getStats().getStat(StatName.SOJOURN_TIME).getQuantile(.95);
        System.out.println("Response 95: " + responseTime95th);

    }

    public static void main(String[] args) {
        Exercise3 exp  = new Exercise3();
        exp.run(args[0], args[1], Integer.valueOf(args[2]), Integer.valueOf(args[3]));
    }

}
