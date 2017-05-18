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
import datacenter.PowerCappingEnforcer;
import datacenter.PowerMeasurer;

public class Exercise2 {

    public Exercise2() {}

    public void run(String workloadDir, String workload, int nServers, double powerCap) {

        // service file
        String arrivalFile = workloadDir + "/workloads/" + workload + ".arrival.cdf";
        String serviceFile = workloadDir + "/workloads/" + workload + ".service.A.cdf";

        // specify distribution
        int cores = 4;
        int sockets = 1;
        double targetRho = .5;

        EmpiricalDistribution arrivalDistribution = EmpiricalDistribution.loadDistribution(arrivalFile, 1e-3);
        EmpiricalDistribution serviceDistribution = EmpiricalDistribution.loadDistribution(serviceFile, 1e-3);

        double averageInterarrival = arrivalDistribution.getMean();
        double averageServiceTime = serviceDistribution.getMean();
        double qps = 1 / averageInterarrival;
        double rho = qps / (cores * (1 / averageServiceTime));
        double arrivalScale = rho / targetRho;
        averageInterarrival = averageInterarrival * arrivalScale;
        double serviceRate = 1 / averageServiceTime;
        double scaledQps = qps / arrivalScale;

        MTRandom rand = new MTRandom(1);
        EmpiricalGenerator arrivalGenerator  = new EmpiricalGenerator(rand, arrivalDistribution, "arrival", arrivalScale * nServers / 500);
        EmpiricalGenerator serviceGenerator  = new EmpiricalGenerator(rand, serviceDistribution, "service", 1.0);

        // setup experiment
        ExperimentInput experimentInput = new ExperimentInput();

        ExperimentOutput experimentOutput = new ExperimentOutput();
        experimentOutput.addOutput(StatName.SOJOURN_TIME, .05, .95, .05, 5000);
        experimentOutput.addOutput(StatName.SERVER_LEVEL_POWER, .05, .95, .05, 5000);
        experimentOutput.addOutput(StatName.SERVER_LEVEL_CAP, .05, .95, .05, 5000);

        Experiment experiment = new Experiment("Exercise 2", rand, experimentInput, experimentOutput);

        // setup datacenter
        DataCenter dataCenter = new DataCenter();

        // setup power measurer
        double measurePeriod = 1.0;
        PowerMeasurer measurer = new PowerMeasurer(experiment, measurePeriod);

        // set up power capping
        //
        // TODO: YOUR CODE HERE
        //

        for(int i = 0; i < nServers; i++) {
            Server server = new Server(sockets, cores, experiment, arrivalGenerator, serviceGenerator);

            server.setSocketPolicy(SocketPowerPolicy.NO_MANAGEMENT);
            server.setCorePolicy(CorePowerPolicy.NO_MANAGEMENT);

            double coreActivePower = 40 * (4.0/5) / cores;
            double coreHaltPower = coreActivePower * .2;
            double coreParkPower = 0;

            server.setCoreActivePower(coreActivePower);
            server.setCoreParkPower(coreParkPower);
            server.setCoreIdlePower(coreHaltPower);

            double socketActivePower = 40 * (1.0/5) / sockets;
            double socketParkPower = 0;

            server.setSocketActivePower(socketActivePower);
            server.setSocketParkPower(socketParkPower);

            dataCenter.addServer(server);
            measurer.addServer(server);

            //
            // TODO: YOUR CODE HERE
            //
        }

        experimentInput.setDataCenter(dataCenter);

        // run the experiment
        experiment.run();

        // display results
        System.out.println("====== Results ======");
        double responseTimeMean = experiment.getStats().getStat(StatName.SOJOURN_TIME).getAverage();
        System.out.println("Response Mean: " + responseTimeMean);
        double responseTime95th = experiment.getStats().getStat(StatName.SOJOURN_TIME).getQuantile(.95);
        System.out.println("Response 95: " + responseTime95th);
        double averageServerLevelPower = experiment.getStats().getStat(StatName.SERVER_LEVEL_POWER).getAverage();
        System.out.println("Average Server Power : " + averageServerLevelPower);
        double averageServerLevelCap = experiment.getStats().getStat(StatName.SERVER_LEVEL_CAP).getAverage();
        System.out.println("Average Server Cap : " + averageServerLevelCap);

    }

    public static void main(String[] args) {
        Exercise2 exp  = new Exercise2();
        exp.run(args[0], args[1], Integer.valueOf(args[2]), Double.valueOf(args[3]));
    }

}
