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
package datacenter;

import generator.Generator;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;

import stat.Statistic;
import stat.TimeWeightedStatistic;
import core.Constants;
import core.Experiment;
import core.Job;
import core.JobArrivalEvent;
import core.Sim;
import core.Constants.StatName;
import datacenter.Core.CorePowerPolicy;
import datacenter.Socket.SocketPowerPolicy;

/**
 * This class represents a physical server in a data center. It behaves much
 * like a queuing theory queue with servers equal to the number of cores. A
 * server has a set of sockets which are the physical chips, each with a number
 * of cores.
 *
 * @author David Meisner (meisner@umich.edu)
 */
public class Server implements Powerable, Serializable {

    /**
     * The serialization id.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The scheduling algorithm for assigning jobs to sockets.
     */
    private enum Scheduler {

        /**
         * Bin packing means trying to fill a socket with jobs
         * before assigning jobs to any other.
         */
        BIN_PACK,

        /**
         * Load balancing assigns jobs to all sockets equally.
         */
        LOAD_BALANCE,

        /**
         * Round-robin regardless of the load.
         */
        ROUND_ROBIN,


	OPTIONAL,
    };

    /**
     * The job priority scheme.
     */
    private enum Priority {

        /**
         * No priority.
         */
        NO_PRIORITY,

        /**
         * Short requests can bypass longer requests.
         */
        SHORTEST_JOB_FIRST,

        /**
         * Prioritize certain types of jobs.
         */
        JOB_PRIORITIES,

        /**
         * Define your own!
         */
	DROP_LONG,

	DROP_NON_PRIORITIES,

    };

    /**
     * The scheduling algorithm currently used.
     */
    private Scheduler scheduler;

    /**
     * The priority policy.
     */
    private Priority priorityPolicy;

    /**
     * The server's sockets.
     */
    protected Socket[] sockets;

    /**
     * Map that saves which socket has a job to avoid searching.
     */
    protected HashMap<Job, Socket> jobToSocketMap;

    /**
     * The experiment the server is running in.
     */
    protected Experiment experiment;

    /**
     * Flag for if the server is paused.
     */
    private boolean paused;

    /**
     * Queue to put jobs in when cores are busy.
     */
    protected LinkedList<Job> queue;

    /**
     * A generator to provide arrival times for jobs for the server.
     */
    private Generator arrivalGenerator;

    /**
     * A generator to provide service times for jobs for the server.
     */
    private Generator serviceGenerator;

    /**
     * average service time metric for priority
     */
    private double serviceTime;

    /**
     * A variable to track the number of jobs in the system and double check
     * the rest of the logic doesn't add/drop jobs.
     */
    protected int jobsInServerInvariant;


    /**
     *A variable to keep track of the previously scheduled Server
     *We need this 'memory' for round robin
     */
    private int prevSocketIndex;


    /**
     *A variable to keep track of average job time
     *We need this 'memory' for shortest job first
     */
    private double JobSizeTotal;
    private int jobCount;
    private int priorityJobCount;
    private int passedOver; //how many times have we bypassed the queue in a row?
    /**
     * Creates a new server.
     *
     * @param theNumberOfSockets - the number of sockets in the server
     * @param theCoresPerSocket - the number of cores per socket
     * @param anExperiment - the experiment the core is part of
     * @param anArrivalGenerator - the interarrival time generator
     * @param aServiceGenerator - the service time generator
     */
    public Server(final int theNumberOfSockets,
                  final int theCoresPerSocket,
                  final Experiment anExperiment,
                  final Generator anArrivalGenerator,
                  final Generator aServiceGenerator,
		  final double averageServiceTime) {
        this.experiment = anExperiment;
        this.arrivalGenerator = anArrivalGenerator;
        this.serviceGenerator = aServiceGenerator;
        this.serviceTime = averageServiceTime;
        this.queue = new LinkedList<Job>();
        this.sockets = new Socket[theNumberOfSockets];
        for (int i = 0; i < theNumberOfSockets; i++) {
            this.sockets[i] = new Socket(experiment, this, theCoresPerSocket);
        }
        this.jobToSocketMap = new HashMap<Job, Socket>();
        this.scheduler = Scheduler.LOAD_BALANCE;
        this.jobsInServerInvariant = 0;
        this.paused = false;
	this.passedOver=0;
    }

    /**
     * Pauses the server. No processing occurs.
     */
    public final void pause() {
        this.paused = true;
    }

    /**
     * Unpauses the server. Processing resumes.
     */
    public final void unpause() {
        this.paused = false;
    }


    /**
     * Returns if the server is paused of not.
     * @return if the server is paused
     */
    public final boolean isPaused() {
        return this.paused;
    }

    /**
     * Creates a new arrival for the server.
     *
     * @param time - the time the new arrival is created
     */
    public final void createNewArrival(final double time) {
        double interarrivalTime = this.arrivalGenerator.next();
        double arrivalTime = time + interarrivalTime;
        double serviceTime = this.serviceGenerator.next();
        Statistic arrivalStat
            = this.experiment.getStats().getStat(
                                StatName.GENERATED_ARRIVAL_TIME);
        arrivalStat.addSample(interarrivalTime);
        Statistic serviceStat
            = this.experiment.getStats().getStat(
                                StatName.GENERATED_SERVICE_TIME);
        serviceStat.addSample(serviceTime);

        Job job = new Job(serviceTime);
        JobArrivalEvent jobArrivalEvent
                = new JobArrivalEvent(arrivalTime,
                                      experiment,
                                      job,
                                      this);
        this.experiment.addEvent(jobArrivalEvent);
    }

//    /**
//     * Assigns a job to
//     * @param time
//     * @param socket
//     * @param job
//     */
//    public void setSocketJobMapping(double time, Socket socket, Job job) {
//        this.jobToSocketMap.put(job, socket);
//    }

    /**
     * Inserts a job into the server.
     * This method is called when a job FIRST arrives at a server
     * (not started processing of resumed etc)
     *
     * @param time - the time the job is inserted
     * @param job - the job that is inserted
     */
    public void insertJob(final double time, final Job job) {
	//double AVGCSEDNS =.18843493;
	//double AVGNEWMAN = .08244058999;
	double SHORTEST_MULT = 3;
	int MAX_PASSOVER = 4;
	if (this.priorityPolicy == Priority.JOB_PRIORITIES){
		if(this.getRemainingCapacity()==0){
		//if we are full up then we manage the queue
		//if the job should be prioritized, add it to the front of the queue
			if(job.getSize()<=1.1*this.serviceTime && job.getSize()>=.9*this.serviceTime){
				this.queue.addFirst(job);
			}else{//otherwise, send it to the back of the queue
				this.queue.addLast(job);
			}
		}else{
		//if not full, always schedule job
			this.startJobService(time,job);
		}
	}else if(this.priorityPolicy == Priority.SHORTEST_JOB_FIRST){
		if(this.getRemainingCapacity()==0){
			//if the job is short enough, put it at the front of the queue
			//and increment the counter for passed over so we can keep track of how often we do this
			if(job.getSize()<= (this.serviceTime*SHORTEST_MULT) && this.passedOver < MAX_PASSOVER){
				this.queue.addFirst(job);
				this.passedOver=this.passedOver+1;
			}else{
				//add to end of queue like normal
				this.queue.addLast(job);
				this.passedOver=0;
			}
		}else{
			//if not full, always schedule the job
			this.startJobService(time,job);
			this.passedOver=0;
		}

	}else if(this.priorityPolicy == Priority.DROP_NON_PRIORITIES){
	    //determine if the length is +/- 10% of mean
	    if(this.getRemainingCapacity()==0){
		this.queue.add(job);
	    }else if(job.getSize()<= 1.1*this.serviceTime && job.getSize()>= .9*this.serviceTime){
		this.startJobService(time,job);
	    }else{
		this.queue.add(job);
	    }
	}else if(this.priorityPolicy==Priority.DROP_LONG){
	    //first, update the mean
	    this.jobCount = this.jobCount+1;
	    this.JobSizeTotal = this.JobSizeTotal + job.getSize();
	    double currentMean = this.JobSizeTotal / this.jobCount;
	    if(this.getRemainingCapacity()==0){ //have to queue if full up
		this.queue.add(job);
	    }else if(job.getSize()<= (this.serviceTime*SHORTEST_MULT)){ //if it is small enough skip the queue
		this.startJobService(time,job);
		this.priorityJobCount = this.priorityJobCount+1;
		if(!this.queue.isEmpty()){
			this.passedOver = this.passedOver+1; //we skipped the queue
		}		
	    }else{//put the long jobs in the queue
		this.queue.add(job);
	    }
	    	    //System.out.println("job count is " +this.jobCount+ " and prioritized oucnt is " + this.priorityJobCount);
	}else{
	    // Check if the job should be serviced now or put in the queue
	    if (this.getRemainingCapacity() == 0) {
		// There was no room in the server, put it in the queue
		this.queue.add(job);
	    } else {
		// The job can start service immediately
		this.startJobService(time, job);
	    }
	}
	// Job has entered the system
        this.jobsInServerInvariant++;
        checkInvariants();
    }

    /**
     * Gets the number of jobs in the server.
     *
     * @return the number of jobs in the server
     */
    public final int getJobsInSystem() {
        // Jobs that need to be counted for socket parking
        int transJobs = 0;
        for (int i = 0; i < this.sockets.length; i++) {
            transJobs += this.sockets[i].getNJobsWaitingForTransistion();
        }

        int jobsInSystem = this.getQueueLength() + this.getJobsInService()
                            + transJobs;
        return jobsInSystem;
    }

    /**
     * Runs sanity check to make sure we didn't lose a  job.
     */
    public final void checkInvariants() {
        int jobsInSystem = this.getJobsInSystem();
        if (jobsInSystem != this.jobsInServerInvariant) {
            Sim.fatalError("From insert: Job balance is off.");
        }
    }

    /**
     * Update the statistics monitoring the server.
     *
     * @param time - the time the update occurs
     */
    public void updateStatistics(final double time) {
        TimeWeightedStatistic serverPowerStat
            = this.experiment.getStats().getTimeWeightedStat(
                    Constants.TimeWeightedStatName.SERVER_POWER);
        serverPowerStat.addSample(this.getPower(), time);

        TimeWeightedStatistic serverUtilStat
            = this.experiment.getStats().getTimeWeightedStat(
                    Constants.TimeWeightedStatName.SERVER_UTILIZATION);
        serverUtilStat.addSample(this.getInstantUtilization(), time);

        double idleness = 1.0;
        if (this.isIdle()) {
            idleness = 0.0;
        }

        TimeWeightedStatistic serverIdleStat
            = this.experiment.getStats().getTimeWeightedStat(
                    Constants.TimeWeightedStatName.SERVER_IDLE_FRACTION);
        serverIdleStat.addSample(idleness, time);
    }

    //TODO what if its paused?
    /**
     * Check if the server is idle (no jobs).
     * @return if the server is idle
     */
    public final boolean isIdle() {
        return this.getJobsInSystem() > 0;
    }

    /**
     * Get the length of the server queue.
     *
     * @return the length of the server queue
     */
    public final int getQueueLength() {
        return this.queue.size();
    }

    /**
     * Get the remaining capacity of the server (in jobs).
     *
     * @return the remaining capacity of the server (in jobs)
     */
    public final int getRemainingCapacity() {
        int capacity = 0;
        for (int i = 0; i < this.sockets.length; i++) {
            capacity += this.sockets[i].getRemainingCapacity();
        }
        return capacity;
    }

    /**
     * Gets the number of jobs this server can ever support.
     *
     * @return the number of jobs this server can ever support
     */
    public final int getTotalCapacity() {
        int nJobs = 0;
        for (int i = 0; i < this.sockets.length; i++) {
            nJobs += this.sockets[i].getTotalCapacity();
        }
        return nJobs;
    }

    /**
     * Gets the number of jobs currently being processed.
     *
     * @return - the number of jobs currently being processed
     */
    public int getJobsInService() {
        int nInService = 0;
        for (int i = 0; i < this.sockets.length; i++) {
            nInService += this.sockets[i].getJobsInService();
        }

        return nInService;
    }

    /**
     * This method is called when the job first starts service. (When it first
     * arrives in and there's spare capacity or when it is taken out
     * of the queue for the first time).
     *
     * @param time - the time the job is started
     * @param job - the job that starts
     */
    public void startJobService(final double time, final Job job) {
        Socket targetSocket = null;
        Socket mostUtilizedSocket = null;
        double highestUtilization = Double.NEGATIVE_INFINITY;
        Socket leastUtilizedSocket = null;
        double lowestUtilization = Double.POSITIVE_INFINITY;
	Socket nextSocket =null;
	Socket optionalSocket=null;
	int foundNext=0;
	if(this.scheduler==Scheduler.OPTIONAL){
		double AVGCSEDNS =.18843493;
		double AVGNEWMAN = .08244058999;
		if(job.getSize()<AVGCSEDNS/10 && this.sockets[0].getRemainingCapacity()>0){//very small always goes in bucket 0
			optionalSocket=this.sockets[0];
			foundNext=1;
			System.out.println("SMALL SOCKET");
		}else if(job.getSize()>AVGCSEDNS*2.8 && this.sockets[1].getRemainingCapacity()>0){//very large always goes in bucket 1
			optionalSocket=this.sockets[1];
			foundNext=1;
			System.out.println("LARGE SOCKET");
		}else{
			if(job.getSize()>AVGCSEDNS*2.8){
				System.out.println("LARGE SOCKET, BUT FULL");
			}
			if(job.getSize()<AVGCSEDNS/10){
				System.out.println("SMALL SOCKET, BUT FULL");
			}
			foundNext=0;
		}
		if(foundNext==0){//load balance between the remaining		
			for (int i = 2; i < this.sockets.length; i++) {
                		Socket currentSocket = this.sockets[i];
                		double currentUtilization = currentSocket.getInstantUtilization();

               			if (currentUtilization > highestUtilization
                    		&& currentSocket.getRemainingCapacity() > 0) {
                    			highestUtilization = currentUtilization;
                    			mostUtilizedSocket = currentSocket;
                		}
			}
			optionalSocket=mostUtilizedSocket;	
		}							
	}else if(this.scheduler==Scheduler.ROUND_ROBIN){ //dont waste our time with more loops if we don't need them
	    int currentIndex; //check for wrap around case
	    if(this.prevSocketIndex+1 == this.sockets.length){
		currentIndex=0;
	    }else{
		currentIndex=this.prevSocketIndex+1;
	    }
	    //seperate loops for round robin. Not super efficient but...
	    for (int i = currentIndex;i<this.sockets.length;i++){
		if(foundNext==0 && this.sockets[i].getRemainingCapacity()>0){
		    nextSocket = this.sockets[i];
		    foundNext=1;
		    this.prevSocketIndex=i;
		}
	    }
	    //need to start from the bottom if we didn't find one in the first partial loop
	    if(foundNext==0){
		for (int i = 0;i<currentIndex;i++){
		    if(foundNext==0 && this.sockets[i].getRemainingCapacity()>0){
			nextSocket = this.sockets[i];
			foundNext=1;
			this.prevSocketIndex=i;
		    }
		}
	    }
	}else{	//loop for non round robin
	    for (int i = 0; i < this.sockets.length; i++) {
		Socket currentSocket = this.sockets[i];
		double currentUtilization = currentSocket.getInstantUtilization();
		
		if (currentUtilization > highestUtilization
                    && currentSocket.getRemainingCapacity() > 0) {
		    highestUtilization = currentUtilization;
		    mostUtilizedSocket = currentSocket;
		}
		
		if (currentUtilization < lowestUtilization
                    && currentSocket.getRemainingCapacity() > 0) {
		    lowestUtilization = currentUtilization;
		    leastUtilizedSocket = currentSocket;
		}
		
	    }
	}

	    // Pick a socket to put the job on depending on the scheduling policy
	    if (this.scheduler == Scheduler.BIN_PACK) {
		targetSocket = mostUtilizedSocket;
	    } else if (this.scheduler == Scheduler.LOAD_BALANCE) {
		targetSocket = leastUtilizedSocket;
	    } else if (this.scheduler == Scheduler.ROUND_ROBIN){
		targetSocket=nextSocket;
	    }else if (this.scheduler == Scheduler.OPTIONAL){
		targetSocket=optionalSocket;
	    }else {
		Sim.fatalError("Bad scheduler");
	    }
	    
        job.markStart(time);
	if (targetSocket==null){
	    System.out.print ("Value of FoundNext is = " + foundNext);
	    System.out.println("Previous Socket Index is = " + this.prevSocketIndex);
	    System.out.println("Total socket count is " + this.sockets.length);
	    for (int i = 0; i < this.sockets.length; i++) {
		System.out.println("index "+ i + " has follow capacity = " +this.sockets[i].getRemainingCapacity());
	    }
	}
        targetSocket.insertJob(time, job);
        this.jobToSocketMap.put(job, targetSocket);
    }

    /**
     * This method is called when a job leaves the server because it has
     * finished service.
     *
     * @param time - the time the job is removed
     * @param job - the jobs that is removed
     */
    public void removeJob(final double time, final Job job) {

        // Remove the job from the socket it is running on
        Socket socket = this.jobToSocketMap.remove(job);

        // Error check we could resolve which socket the job was on
        if (socket == null) {
            Sim.fatalError("Job to Socket mapping failed");
        }

        // See if we're going to schedule another job or if it can go to sleep
        boolean jobWaiting = !this.queue.isEmpty();

        // Remove the job from the socket (which will remove it from the core)
        socket.removeJob(time, job, jobWaiting);

        // There is now a spot for a job, see if there's one waiting
        if (jobWaiting) {
            Job dequeuedJob = this.queue.poll();
            this.startJobService(time, dequeuedJob);
        }

        // Job has left the systems
        this.jobsInServerInvariant--;
        this.checkInvariants();
    }

    /**
     * Gets the instant utilization of the server.
     * utilization = (jobs running)/(total capacity)
     *
     * @return the instant utilization of the server
     */
    public double getInstantUtilization() {
        double avg = 0.0d;

        for (int i = 0; i < this.sockets.length; i++) {
            avg += this.sockets[i].getInstantUtilization();
        }
        avg /= this.sockets.length;

        return avg;
    }

    /**
     * Get the experiment this server is part of.
     *
     * @return the experiment this server is part of
     */
    public Experiment getExperiment() {
        return this.experiment;
    }

    /**
     * Get the sockets this server has.
     *
     * @return the sockets the server has
     */
    public Socket[] getSockets() {
        return this.sockets;
    }

    /**
     * Sets the power management policy for the CPU cores in this server.
     *
     * @param corePowerPolicy - the power management policy to use
     */
    public void setCorePolicy(final CorePowerPolicy corePowerPolicy) {
        for (int i = 0; i < sockets.length; i++) {
            this.sockets[i].setCorePolicy(corePowerPolicy);
        }
    }

    /**
     * Sets the scheduler type.
     *
     * @param theSchedulerType - the type name of the scheduler
     */
    public void setScheduler(final String theSchedulerType) {
        if (theSchedulerType.equals("LOAD_BALANCE")) {
            this.scheduler = Scheduler.LOAD_BALANCE;
        } else if (theSchedulerType.equals("BIN_PACK")) {
            this.scheduler = Scheduler.BIN_PACK;
        } else if (theSchedulerType.equals("ROUND_ROBIN")){
	    this.scheduler = Scheduler.ROUND_ROBIN;
	} else if (theSchedulerType.equals("OPTIONAL")){
		this.scheduler=Scheduler.OPTIONAL;
	}else {
            Sim.fatalError("Bad scheduler");
        }
    }

    /**
     * Sets the priority policy.
     *
     * @param thePriorityPolicy - the priority policy name.
     */
    public void setPriority(final String thePriorityPolicy) {
        if (thePriorityPolicy.equals("NO_PRIORITY")) {
            this.priorityPolicy = Priority.NO_PRIORITY;
        } else if (thePriorityPolicy.equals("SHORTEST_JOB_FIRST")) {
            this.priorityPolicy = Priority.SHORTEST_JOB_FIRST;
        } else if (thePriorityPolicy.equals("JOB_PRIORITIES")) {
            this.priorityPolicy = Priority.JOB_PRIORITIES;
        } else {
            Sim.fatalError("Bad priority policy");
        }
    }

    /**
     * Gets the current power consumption of the server (in watts).
     *
     * @return the current power consumption of the server (in watts)
     */
    public double getPower() {
        double totalPower = this.getDynamicPower() + this.getIdlePower();

        return totalPower;
    }

    //TODO get rid of magic numbers
    /**
     * Gets the dynamic power consumption of the server.(in watts).
     *
     * @return the dynamic power consumption of the server (in watts)
     */
    public double getDynamicPower() {
        double dynamicPower = 0.0d;
        for (int i = 0; i < this.sockets.length; i++) {
            dynamicPower += this.sockets[i].getDynamicPower();
        }
        double util = this.getInstantUtilization();
        double memoryPower = 10 * util;
        double diskPower = 1.0 * util;
        double otherPower = 5.0 * util;
        dynamicPower += memoryPower + diskPower + otherPower;

        return dynamicPower;
    }

    //TODO get rid of magic numbers
    /**
     * Gets the maximum dynamic power consumption of
     * the server's CPUs (in watts).
     *
     * @return the maximum dynamic power consumption of
     * the server's CPUs (in watts).
     */
    private double getMaxCpuDynamicPower() {
        return 25.0;
    }

    /**
     * Get the idle power consumption of the server (in watts).
     *
     * @return the idle power consumption of the server (in watts)
     */
    public double getIdlePower() {
        double idlePower = 0.0d;

        for (int i = 0; i < this.sockets.length; i++) {
            idlePower += this.sockets[i].getIdlePower();
        }

        double memoryPower = 25;
        double diskPower = 9;
        double otherPower = 10;
        idlePower += memoryPower + diskPower + otherPower;

        return idlePower;
    }

    /**
     * Resume processing at the server.
     *
     * @param time - the time processing is resumed
     */
    public void resumeProcessing(final double time) {
        this.paused = false;

        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].resumeProcessing(time);
        }

        while (this.getRemainingCapacity() > 0 && this.queue.size() != 0) {
            Job job = this.queue.poll();
            this.startJobService(time, job);
        }
    }

    /**
     * Pause processing at the server.
     *
     * @param time - the time the processing is paused
     */
    public void pauseProcessing(final double time) {
        this.paused = true;

        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].pauseProcessing(time);
        }
    }

    /**
     * Set the server's sockets' power management policy.
     *
     * @param socketPolicy - the power management policy
     */
    public void setSocketPolicy(final SocketPowerPolicy socketPolicy) {
        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].setPowerPolicy(socketPolicy);
        }

    }

    /**
     * Sets the server's cores' active power (in watts).
     *
     * @param coreActivePower - the server's cores' active power (in watts)
     */
    public void setCoreActivePower(final double coreActivePower) {
        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].setCoreActivePower(coreActivePower);
        }

    }

    /**
     * Set the power of the server's cores when in park (in watts).
     *
     * @param coreParkPower - the power of the server's cores
     * when in park (in watts)
     */
    public void setCoreParkPower(final double coreParkPower) {
        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].setCoreParkPower(coreParkPower);
        }
    }

    /**
     * Sets the power of the server's cores while idle (in watts).
     *
     * @param coreIdlePower - the power of the server's cores
     * while idle (in watts).
     */
    public void setCoreIdlePower(final double coreIdlePower) {
        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].setCoreIdlePower(coreIdlePower);
        }
    }

    /**
     * Sets the active power of the server's sockets (in watts).
     *
     * @param socketActivePower - the active power of
     * the server's sockets (in watts).
     */
    public void setSocketActivePower(final double socketActivePower) {
        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].setSocketActivePower(socketActivePower);
        }
    }

    /**
     * Sets the park power of the server's sockets (in watts).
     *
     * @param socketParkPower - the park power of the socket in park (in watts)
     */
    public void setSocketParkPower(final double socketParkPower) {
        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].setSocketParkPower(socketParkPower);
        }
    }

    /**
     * Set the DVFS speed of the server's cores.
     *
     * @param time - the time the speed is set
     * @param speed - the speed to set the cores to (relative to 1.0)
     */
    public void setDvfsSpeed(final double time, final double speed) {
        for (int i = 0; i < this.sockets.length; i++) {
            this.sockets[i].setDvfsSpeed(time, speed);
        }
    }

    /**
     * Assign a power budget to a server (in watts).
     * The server will change it's DVFS setting to try to meet this budget.
     *
     * @param time - the time the budget is assigned
     * @param allocatedPower - the power budget (in watts)
     */
    public void assignPowerBudget(final double time,
                                  final double allocatedPower) {
        double dvfsSpeed = 0.0;
        double nonScalablePower = this.getMaxPower()
                - this.getMaxCpuDynamicPower();
        if (allocatedPower < nonScalablePower) {
            dvfsSpeed = 0.5;
        } else if (allocatedPower > this.getMaxPower()) {
            dvfsSpeed = 1.0;
        } else {
            double targetCpuPower = allocatedPower - nonScalablePower;
            dvfsSpeed = Math.pow(targetCpuPower / this.getMaxCpuDynamicPower(),
                    1 / 3.0);
            dvfsSpeed = Math.max(dvfsSpeed, .5);
        }

        this.setDvfsSpeed(time, dvfsSpeed);
    }

    //TODO change this
    /**
     *Get the maximum possible power consumption for the server (in watts).
     *
     * @return the maximum possible power consumption for the server (in watts)
     */
    public double getMaxPower() {
        return 100.0;
    }

}
