/*
 * Q-Learning Optimizer (QLO) for Energy-Aware Cloud Task Scheduling
 * Master's Thesis Implementation — Islamic Azad University, Science and Research Branch (2025)
 * Built on the CloudSim 3.0 Simulation Toolkit
 */

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPower_QLO;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

/**
 * Multi-agent Q-Learning Optimizer (QLO) for dynamic cloud task scheduling
 * and energy-aware load balancing in CloudSim 3.0.
 *
 * Usage:
 *   java -cp "lib/*:." QLO            -> Runs Dataset A (8 VMs, 70 tasks)
 *   java -cp "lib/*:." QLO A 90       -> Runs Dataset A (8 VMs, 90 tasks)
 *   java -cp "lib/*:." QLO B 180      -> Runs Dataset B (15 VMs, 180 tasks)
 */
public class QLO {

    // Active workload configuration (default: Dataset A)
    private static int vmCount = 8;
    private static int[] vmMips = { 100, 200, 300, 400, 500, 600, 700, 800 };
    private static int taskCount = 70;
    private static int taskTypeCount = 5;
    private static Integer[] taskTypeList = { 100, 200, 300, 400, 500 };

    // Q-Learning hyperparameters (State 1 from thesis parameter study)
    private static final double ALPHA = 0.1;     // Learning rate
    private static final double GAMMA = 0.9;     // Discount factor
    private static final double EPSILON = 0.1;   // Exploration rate
    private static final int TRAINING_EPISODES = 1000;
    private static final int STEPS_PER_EPISODE = 1000;
    private static final long RANDOM_SEED = 42L;

    // Energy pricing ($0.15 per kWh)
    private static final double ENERGY_COST_PER_KWH = 0.15;

    // Q-table indexed by [sourceVm * taskTypeCount + jobTypeIdx][destinationVm]
    private static double[][] qTable;

    // Comparative evaluation metrics (Optimized vs. Round-Robin Baseline)
    private static double optimizedExecutionTime;
    private static double nonOptimizedExecutionTime;
    private static double optimizedResponseTime;
    private static double nonOptimizedResponseTime;
    private static double optimizedEnergyCost;
    private static double nonOptimizedEnergyCost;
    private static double optimizedEnergyConsumption;
    private static double nonOptimizedEnergyConsumption;

    public static void main(String[] args) {
        configureDataset(args);
        qTable = new double[vmCount * taskTypeCount][vmCount];

        Log.printLine("Starting Q-Learning Optimizer (QLO) ...");
        Log.printLine("Configuration: VMs=" + vmCount + ", Tasks=" + taskCount + ", TaskTypes=" + taskTypeCount);

        Random rand = new Random(RANDOM_SEED);
        int mipsSum = IntStream.of(vmMips).sum();

        // Phase 1: Train Q-Learning scheduling policy
        trainQTable(rand, mipsSum);
        Log.printLine("End of Training Phase.");

        // Phase 2: Evaluate trained policy against unoptimized Round-Robin baseline in CloudSim
        runCloudSimEvaluation(rand, mipsSum);
    }

    /**
     * Selects Dataset A (8 VMs) or Dataset B (15 VMs) and optional task count from CLI arguments.
     */
    private static void configureDataset(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("B")) {
            vmCount = 15;
            vmMips = new int[] { 100, 120, 140, 160, 180, 200, 220, 240, 260, 280, 300, 320, 340, 360, 380 };
            taskTypeCount = 12;
            taskTypeList = new Integer[] { 100, 120, 140, 160, 180, 200, 220, 240, 260, 280, 300, 320 };
            taskCount = 180;
        }
        if (args.length > 1) {
            try {
                int parsedTasks = Integer.parseInt(args[1]);
                if (parsedTasks > 0) {
                    taskCount = parsedTasks;
                }
            } catch (NumberFormatException ignored) {
                Log.printLine("Invalid task count argument; using default: " + taskCount);
            }
        }
    }

    /**
     * Off-policy Q-Learning training loop with epsilon-greedy exploration.
     * Reward is defined as the reduction in total absolute load-balancing variance across VMs.
     */
    private static void trainQTable(Random rand, int mipsSum) {
        long[] vmOptQueueLen = new long[vmCount];
        long[] vmCurQueueLen = new long[vmCount];
        long[] vmAbsVariance = new long[vmCount];

        for (int episode = 0; episode < TRAINING_EPISODES; episode++) {
            int[][] vmQueues = new int[vmCount][taskCount];
            int totalTaskLength = generateRoundRobinWorkload(rand, vmQueues, null);

            double loadBalanceCoeff = (double) totalTaskLength / mipsSum;
            for (int vm = 0; vm < vmCount; vm++) {
                vmOptQueueLen[vm] = Math.round(loadBalanceCoeff * vmMips[vm]);
            }

            long curTotalAbsVar = computeTotalAbsVariance(vmQueues, vmOptQueueLen, vmCurQueueLen, vmAbsVariance);

            int state = selectNonEmptyVm(rand, vmQueues);
            if (state == -1) {
                continue;
            }

            for (int step = 0; step < STEPS_PER_EPISODE; step++) {
                long prevTotalAbsVar = curTotalAbsVar;

                int[] nonZeroIndices = collectNonZeroIndices(vmQueues[state]);
                if (nonZeroIndices.length == 0) {
                    state = selectNonEmptyVm(rand, vmQueues);
                    if (state == -1) {
                        break;
                    }
                    nonZeroIndices = collectNonZeroIndices(vmQueues[state]);
                }

                int chosenSlot = nonZeroIndices[rand.nextInt(nonZeroIndices.length)];
                int selectedJob = vmQueues[state][chosenSlot];
                int jobTypeIdx = Arrays.asList(taskTypeList).indexOf(selectedJob);

                // Epsilon-greedy action selection (choose destination VM != current state)
                int nextState;
                if (rand.nextDouble() < EPSILON) {
                    do {
                        nextState = rand.nextInt(vmCount);
                    } while (nextState == state);
                } else {
                    nextState = getBestActionExcluding(state, jobTypeIdx, state, rand);
                }

                // Relocate selected job from source VM (state) to destination VM (nextState)
                vmQueues[state][chosenSlot] = 0;
                insertIntoQueue(vmQueues[nextState], selectedJob);

                curTotalAbsVar = computeTotalAbsVariance(vmQueues, vmOptQueueLen, vmCurQueueLen, vmAbsVariance);
                long reward = prevTotalAbsVar - curTotalAbsVar;

                double currentQ = getQ(state, jobTypeIdx, nextState);
                double maxNextQ = getMaxQ(nextState, jobTypeIdx);
                double updatedQ = currentQ + ALPHA * (reward + GAMMA * maxNextQ - currentQ);
                setQ(state, jobTypeIdx, nextState, updatedQ);

                state = nextState;
            }
        }
    }

    /**
     * Generates a test workload, optimizes task placement using the learned Q-table,
     * and benchmarks it against the unoptimized Round-Robin placement in CloudSim.
     */
    private static void runCloudSimEvaluation(Random rand, int mipsSum) {
        try {
            int[][] optimizedVmQueues = new int[vmCount][taskCount];
            int[][] baselineVmQueues = new int[vmCount][taskCount];

            int totalTaskLength = generateRoundRobinWorkload(rand, optimizedVmQueues, baselineVmQueues);

            long[] vmOptQueueLen = new long[vmCount];
            long[] vmCurQueueLen = new long[vmCount];
            long[] vmAbsVariance = new long[vmCount];

            double loadBalanceCoeff = (double) totalTaskLength / mipsSum;
            for (int vm = 0; vm < vmCount; vm++) {
                vmOptQueueLen[vm] = Math.round(loadBalanceCoeff * vmMips[vm]);
            }

            long curTotalAbsVar = computeTotalAbsVariance(optimizedVmQueues, vmOptQueueLen, vmCurQueueLen, vmAbsVariance);

            // Apply learned Q-table policy without mutating the Q-table
            for (int state = 0; state < vmCount; state++) {
                for (int tt = 0; tt < taskTypeCount; tt++) {
                    double[] qRow = qTable[(state * taskTypeCount) + tt];
                    boolean[] visited = new boolean[vmCount];

                    for (int attempt = 0; attempt < vmCount; attempt++) {
                        int bestDestVm = -1;
                        double bestQ = -Double.MAX_VALUE;
                        for (int dest = 0; dest < vmCount; dest++) {
                            if (!visited[dest] && dest != state && qRow[dest] > bestQ) {
                                bestQ = qRow[dest];
                                bestDestVm = dest;
                            }
                        }
                        if (bestDestVm == -1) {
                            break;
                        }
                        visited[bestDestVm] = true;

                        for (int slot = 0; slot < optimizedVmQueues[state].length; slot++) {
                            if (optimizedVmQueues[state][slot] == taskTypeList[tt]) {
                                int selectedJob = optimizedVmQueues[state][slot];
                                long newSourceAbsVar = Math.abs(vmOptQueueLen[state] - (vmCurQueueLen[state] - selectedJob));
                                long newDestAbsVar = Math.abs(vmOptQueueLen[bestDestVm] - (vmCurQueueLen[bestDestVm] + selectedJob));
                                long candidateTotalVar = curTotalAbsVar
                                        - vmAbsVariance[state] + newSourceAbsVar
                                        - vmAbsVariance[bestDestVm] + newDestAbsVar;

                                if (candidateTotalVar < curTotalAbsVar) {
                                    vmCurQueueLen[state] -= selectedJob;
                                    vmCurQueueLen[bestDestVm] += selectedJob;
                                    vmAbsVariance[state] = newSourceAbsVar;
                                    vmAbsVariance[bestDestVm] = newDestAbsVar;
                                    curTotalAbsVar = candidateTotalVar;

                                    optimizedVmQueues[state][slot] = 0;
                                    insertIntoQueue(optimizedVmQueues[bestDestVm], selectedJob);
                                }
                            }
                        }
                    }
                }
            }

            // Initialize CloudSim environment
            int numUsers = 2;
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(numUsers, calendar, false);

            Datacenter datacenter0 = createDatacenter("Datacenter_0");
            Host host0 = datacenter0.getHostList().get(0);
            Host host1 = datacenter0.getHostList().get(1);

            DatacenterBroker broker0 = createBroker(0);
            DatacenterBroker broker1 = createBroker(1);
            int brokerId0 = broker0.getId();
            int brokerId1 = broker1.getId();

            List<Vm> vmList0 = new ArrayList<>();
            List<Vm> vmList1 = new ArrayList<>();
            long imageSize = 10000;
            int ram = 512;
            long bw = 1000;
            int pesNumber = 1;
            String vmm = "Xen";

            for (int i = 0; i < vmCount; i++) {
                Vm optVm = new Vm(i, brokerId0, vmMips[i], pesNumber, ram, bw, imageSize, vmm, new CloudletSchedulerSpaceShared());
                optVm.setHost(host0);
                vmList0.add(optVm);

                Vm baseVm = new Vm(i, brokerId1, vmMips[i], pesNumber, ram, bw, imageSize, vmm, new CloudletSchedulerSpaceShared());
                baseVm.setHost(host1);
                vmList1.add(baseVm);
            }

            broker0.submitVmList(vmList0);
            broker1.submitVmList(vmList1);

            // Create and bind Cloudlets
            List<Cloudlet> cloudletList0 = new ArrayList<>();
            List<Cloudlet> cloudletList1 = new ArrayList<>();
            long fileSize = 3000;
            long outputSize = 3000;
            UtilizationModel utilizationModel = new UtilizationModelFull();

            int cloudletId0 = 0;
            int cloudletId1 = 1000;
            int[][] binder0 = new int[vmCount][taskCount];
            int[][] binder1 = new int[vmCount][taskCount];

            for (int i = 0; i < vmCount; i++) {
                for (int j = 0; j < taskCount; j++) {
                    if (optimizedVmQueues[i][j] > 0) {
                        Cloudlet cl = new Cloudlet(cloudletId0, optimizedVmQueues[i][j], pesNumber, fileSize, outputSize,
                                utilizationModel, utilizationModel, utilizationModel);
                        cl.setUserId(brokerId0);
                        cloudletList0.add(cl);
                        binder0[i][j] = cloudletId0++;
                    } else {
                        binder0[i][j] = -1;
                    }

                    if (baselineVmQueues[i][j] > 0) {
                        Cloudlet cl = new Cloudlet(cloudletId1, baselineVmQueues[i][j], pesNumber, fileSize, outputSize,
                                utilizationModel, utilizationModel, utilizationModel);
                        cl.setUserId(brokerId1);
                        cloudletList1.add(cl);
                        binder1[i][j] = cloudletId1++;
                    } else {
                        binder1[i][j] = -1;
                    }
                }
            }

            broker0.submitCloudletList(cloudletList0);
            broker1.submitCloudletList(cloudletList1);

            for (int i = 0; i < vmCount; i++) {
                for (int j = 0; j < taskCount; j++) {
                    if (binder0[i][j] > -1) {
                        broker0.bindCloudletToVm(binder0[i][j], vmList0.get(i).getId());
                    }
                    if (binder1[i][j] > -1) {
                        broker1.bindCloudletToVm(binder1[i][j], vmList1.get(i).getId());
                    }
                }
            }

            Log.printLine("Starting CloudSim Simulation...");
            CloudSim.startSimulation();

            List<Cloudlet> receivedOpt = broker0.getCloudletReceivedList();
            List<Cloudlet> receivedBase = broker1.getCloudletReceivedList();
            List<PowerHost> powerHosts = datacenter0.getHostList();

            CloudSim.stopSimulation();

            Log.printLine("=============================================================================");
            Log.printLine("=============> User (QLO Optimized) " + brokerId0);
            evaluateCloudletResults(receivedOpt, powerHosts.get(0), true);

            Log.printLine("=============================================================================");
            Log.printLine("=============> User (Round-Robin Baseline) " + brokerId1);
            evaluateCloudletResults(receivedBase, powerHosts.get(1), false);

            printImprovementResults();
            Log.printLine("QLO Simulation finished successfully.");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation terminated due to an unexpected error.");
        }
    }

    private static int generateRoundRobinWorkload(Random rand, int[][] targetQueue, int[][] mirrorQueue) {
        int vmIdx = 0;
        int slotIdx = 0;
        int totalTaskLength = 0;

        for (int j = 0; j < taskCount; j++) {
            int jobType = rand.nextInt(taskTypeCount);
            int jobLength = taskTypeList[jobType];
            totalTaskLength += jobLength;

            targetQueue[vmIdx][slotIdx] = jobLength;
            if (mirrorQueue != null) {
                mirrorQueue[vmIdx][slotIdx] = jobLength;
            }

            vmIdx++;
            if (vmIdx == vmCount) {
                vmIdx = 0;
                slotIdx++;
            }
        }
        return totalTaskLength;
    }

    private static long computeTotalAbsVariance(
            int[][] vmQueues, long[] vmOptQueueLen, long[] vmCurQueueLen, long[] vmAbsVariance) {
        long totalAbsVar = 0;
        for (int vm = 0; vm < vmCount; vm++) {
            vmCurQueueLen[vm] = IntStream.of(vmQueues[vm]).sum();
            vmAbsVariance[vm] = Math.abs(vmOptQueueLen[vm] - vmCurQueueLen[vm]);
            totalAbsVar += vmAbsVariance[vm];
        }
        return totalAbsVar;
    }

    private static int selectNonEmptyVm(Random rand, int[][] vmQueues) {
        List<Integer> nonEmpty = new ArrayList<>();
        for (int vm = 0; vm < vmCount; vm++) {
            if (IntStream.of(vmQueues[vm]).sum() > 0) {
                nonEmpty.add(vm);
            }
        }
        return nonEmpty.isEmpty() ? -1 : nonEmpty.get(rand.nextInt(nonEmpty.size()));
    }

    private static int[] collectNonZeroIndices(int[] queue) {
        int count = 0;
        for (int job : queue) {
            if (job != 0) {
                count++;
            }
        }
        int[] indices = new int[count];
        int ptr = 0;
        for (int i = 0; i < queue.length; i++) {
            if (queue[i] != 0) {
                indices[ptr++] = i;
            }
        }
        return indices;
    }

    private static void insertIntoQueue(int[] queue, int jobLength) {
        for (int i = 0; i < queue.length; i++) {
            if (queue[i] == 0) {
                queue[i] = jobLength;
                return;
            }
        }
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        int hostMips = 5000;
        int ram = 20480;
        long storage = 1000000;
        int bw = 100000;

        // Create isolated PE lists for each PowerHost to avoid shared resource conflicts
        for (int hostId = 0; hostId < 2; hostId++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(hostMips)));

            hostList.add(
                    new PowerHost(
                            hostId,
                            new RamProvisionerSimple(ram),
                            new BwProvisionerSimple(bw),
                            storage,
                            peList,
                            new VmSchedulerTimeShared(peList),
                            new PowerModelSpecPower_QLO()
                    )
            );
        }

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, timeZone, cost, costPerMem, costPerStorage, costPerBw);

        try {
            return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static DatacenterBroker createBroker(int id) {
        try {
            return new DatacenterBroker("Broker" + id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Logs Cloudlet execution results and computes makespan, average response time,
     * host energy consumption (kWh), and operational energy cost ($).
     */
    private static void evaluateCloudletResults(List<Cloudlet> list, PowerHost host, boolean isOptimized) {
        int size = list.size();
        String indent = "    ";
        DecimalFormat dft = new DecimalFormat("###.##");

        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
                "Data center ID" + indent + "VM ID" + indent + "Time" + indent +
                "Start Time" + indent + "Finish Time" + indent + "Cost" + indent + "Response Time");

        double maxFinishTime = 0.0;
        double totalResponseTime = 0.0;

        for (Cloudlet cloudlet : list) {
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");

                double costPerSecond = 0.01;
                double costPerRam = 0.001;
                double costPerBw = 0.0001;
                double cost = (cloudlet.getActualCPUTime() * costPerSecond)
                        + (cloudlet.getUtilizationOfRam(cloudlet.getActualCPUTime()) * (costPerRam * cloudlet.getCloudletLength()))
                        + (cloudlet.getUtilizationOfBw(cloudlet.getCloudletLength() / 1000) * costPerBw);

                double responseTime = cloudlet.getFinishTime() - cloudlet.getExecStartTime();
                totalResponseTime += responseTime;

                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId()
                        + indent + indent + dft.format(cloudlet.getActualCPUTime())
                        + indent + indent + dft.format(cloudlet.getExecStartTime())
                        + indent + indent + dft.format(cloudlet.getFinishTime())
                        + indent + indent + dft.format(cost)
                        + indent + indent + dft.format(responseTime));

                if (cloudlet.getFinishTime() > maxFinishTime) {
                    maxFinishTime = cloudlet.getFinishTime();
                }
            }
        }

        // Compute discrete-time host power profile and cumulative energy consumption
        int timeLength = (int) Math.ceil(maxFinishTime * 100) + 1;
        double[][] timeTable = new double[vmCount][timeLength];
        for (Cloudlet cl : list) {
            int startSlot = Math.max(0, (int) Math.round(cl.getExecStartTime() * 100));
            int endSlot = Math.min(timeLength, (int) Math.round(cl.getFinishTime() * 100));
            for (int t = startSlot; t < endSlot; t++) {
                timeTable[cl.getVmId()][t] = vmMips[cl.getVmId()];
            }
        }

        double totalMips = IntStream.of(vmMips).sum();
        double hostPower = 0.0;
        for (int t = 0; t < timeLength; t++) {
            double utilSum = 0.0;
            for (int vm = 0; vm < vmCount; vm++) {
                utilSum += timeTable[vm][t];
            }
            double utilization = Math.min(1.0, utilSum / totalMips);
            hostPower += host.getPowerModel().getPower(utilization);
        }

        double hostEnergyCost = hostPower * ENERGY_COST_PER_KWH;

        Log.printLine("=============================================================");
        Log.printLine("Host " + host.getId() + " Energy Consumption: " + dft.format(hostPower) + " kWh");
        Log.printLine("Host " + host.getId() + " Energy Cost: $" + dft.format(hostEnergyCost));
        Log.printLine("=============================================================");

        double avgResponseTime = (size > 0) ? (totalResponseTime / size) : 0.0;
        if (isOptimized) {
            optimizedExecutionTime = maxFinishTime;
            optimizedResponseTime = avgResponseTime;
            optimizedEnergyCost = hostEnergyCost;
            optimizedEnergyConsumption = hostPower;
        } else {
            nonOptimizedExecutionTime = maxFinishTime;
            nonOptimizedResponseTime = avgResponseTime;
            nonOptimizedEnergyCost = hostEnergyCost;
            nonOptimizedEnergyConsumption = hostPower;
        }
    }

    private static int getBestActionExcluding(int state, int jobTypeIdx, int excludedVm, Random rand) {
        double maxQ = Double.NEGATIVE_INFINITY;
        List<Integer> bestActions = new ArrayList<>();
        double[] row = qTable[(state * taskTypeCount) + jobTypeIdx];

        for (int vm = 0; vm < vmCount; vm++) {
            if (vm == excludedVm) {
                continue;
            }
            if (row[vm] > maxQ) {
                maxQ = row[vm];
                bestActions.clear();
                bestActions.add(vm);
            } else if (row[vm] == maxQ) {
                bestActions.add(vm);
            }
        }
        return bestActions.get(rand.nextInt(bestActions.size()));
    }

    private static double getMaxQ(int state, int jobTypeIdx) {
        double maxValue = Double.NEGATIVE_INFINITY;
        double[] row = qTable[(state * taskTypeCount) + jobTypeIdx];
        for (int i = 0; i < vmCount; i++) {
            if (row[i] > maxValue) {
                maxValue = row[i];
            }
        }
        return maxValue;
    }

    private static double getQ(int state, int jobTypeIdx, int nextState) {
        return qTable[(state * taskTypeCount) + jobTypeIdx][nextState];
    }

    private static void setQ(int state, int jobTypeIdx, int nextState, double value) {
        qTable[(state * taskTypeCount) + jobTypeIdx][nextState] = value;
    }

    private static double computePercentImprovement(double baseline, double optimized) {
        return (baseline > 0.0) ? ((baseline - optimized) / baseline) * 100.0 : 0.0;
    }

    private static void printImprovementResults() {
        DecimalFormat dft = new DecimalFormat("###.##");

        double executionTimeImp = computePercentImprovement(nonOptimizedExecutionTime, optimizedExecutionTime);
        double responseTimeImp = computePercentImprovement(nonOptimizedResponseTime, optimizedResponseTime);
        double energyImp = computePercentImprovement(nonOptimizedEnergyConsumption, optimizedEnergyConsumption);
        double energyCostImp = computePercentImprovement(nonOptimizedEnergyCost, optimizedEnergyCost);

        Log.printLine("=============================================================");
        Log.printLine("Comparative Improvement Summary (QLO vs. Round-Robin):");
        Log.printLine("Execution Time (Optimized)     : " + dft.format(optimizedExecutionTime) + " s");
        Log.printLine("Execution Time (Baseline)      : " + dft.format(nonOptimizedExecutionTime) + " s");
        Log.printLine("Execution Time Improvement     : " + dft.format(executionTimeImp) + "%");
        Log.printLine("Avg Response Time (Optimized)  : " + dft.format(optimizedResponseTime) + " s");
        Log.printLine("Avg Response Time (Baseline)   : " + dft.format(nonOptimizedResponseTime) + " s");
        Log.printLine("Avg Response Time Improvement  : " + dft.format(responseTimeImp) + "%");
        Log.printLine("Energy Consumption (Optimized) : " + dft.format(optimizedEnergyConsumption) + " kWh");
        Log.printLine("Energy Consumption (Baseline)  : " + dft.format(nonOptimizedEnergyConsumption) + " kWh");
        Log.printLine("Energy Consumption Improvement : " + dft.format(energyImp) + "%");
        Log.printLine("Energy Cost (Optimized)        : $" + dft.format(optimizedEnergyCost));
        Log.printLine("Energy Cost (Baseline)         : $" + dft.format(nonOptimizedEnergyCost));
        Log.printLine("Energy Cost Improvement        : " + dft.format(energyCostImp) + "%");
        Log.printLine("=============================================================");
    }
}
