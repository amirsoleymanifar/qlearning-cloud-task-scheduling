/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation
 *               of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009, The University of Melbourne, Australia
 */

 import java.util.stream.*;
 import java.text.DecimalFormat;
 import java.util.Random;
 
 import java.text.DecimalFormat;
 import java.util.ArrayList;
 import java.util.Arrays;
 import java.util.Calendar;
 import java.util.LinkedList;
 import java.util.List;
 import java.util.Random;
 
 import org.cloudbus.cloudsim.Cloudlet;
 import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
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
 import org.cloudbus.cloudsim.power.PowerVm;
 import org.cloudbus.cloudsim.VmAllocationPolicySimple;
 import org.cloudbus.cloudsim.VmSchedulerTimeShared;
 import org.cloudbus.cloudsim.core.CloudSim;
 import org.cloudbus.cloudsim.power.PowerHost;
 import org.cloudbus.cloudsim.power.models.PowerModelSpecPower_QLO;
 import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
 import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
 import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
 
 /**
  * A simple example showing how to create
  * a datacenter with one host and run two
  * cloudlets on it. The cloudlets run in
  * VMs with the same MIPS requirements.
  * The cloudlets will take the same time to
  * complete the execution.
  */
 public class QLO {
 
     /** The cloudlet list. */
     private static List<Cloudlet> cloudletList0;
     private static List<Cloudlet> cloudletList1;
 
     /** The vmlist. */
     private static List<Vm> vmlist0;
     private static List<Vm> vmlist1;
 
     final static int vmcount = 4;
     final static int vmmips[] = { 100, 300, 500, 700 };
 
     final static int taskcount = 10;
     final static int tasktype = 2;
     final static Integer tasktypelist[] = { 115, 345 };
 
     // Q-Learning parameters
     final static int states = vmcount;
     final static double alpha = 0.1; // Learning rate
     final static double gamma = 0.9; // Discount factor
     final static double epsilon = 0.1; // Exploration rate
     final static double[][] Q = new double[states * tasktype][states]; // Q learning table
 
     // Energy cost per kWh ($0.15)
     final static double energyCostPerKWh = 0.15;
 
     // Performance metrics storage
     private static double optimizedExecutionTime;
     private static double nonOptimizedExecutionTime;
     private static double optimizedResponseTime;
     private static double nonOptimizedResponseTime;
     private static double optimizedEnergyCost;
     private static double nonOptimizedEnergyCost;
     private static double optimizedEnergyConsumption;
     private static double nonOptimizedEnergyConsumption;
 
     /**
      * Creates main() to run this example
      */
     public static void main(String[] args) {
         Log.printLine("Starting QLO ...");
 
         // Model Training Episode
         // =============================================================================================
         Random rand = new Random();
         int mipssum = IntStream.of(vmmips).sum();
         long[] vmqoplen = new long[vmcount]; // VM queue optimum length of task in load balancing
         long[] vmcurqlen = new long[vmcount]; // VM queue current length of task in load balancing
         long[] vmqlvar = new long[vmcount]; // VM queue task variance
         long[] vmqlabsvar = new long[vmcount]; // VM queue task absolute variance
         long cursumvmqlabsvar = 0; // Current total VM queue task absolute variance
         long prevsumvmqlabsvar = 0; // Previous total VM queue task absolute variance
 
         // Model Training main loop
         for (int i = 0; i < 1000; i++) {
             int vq = 0;
             int k = 0;
             int tasksum = 0;
             int[][] vmq = new int[vmcount][taskcount]; // Vm's task queue table
 
             for (int j = 0; j < taskcount; j++) {
                 int jobtype = rand.nextInt(tasktype); // Create task randomly
                 tasksum = tasksum + tasktypelist[jobtype]; // Calc total created task in every pass
                 // Task Round Robin scheduling in VM's queue
                 vmq[vq][k] = tasktypelist[jobtype];
                 vq = vq + 1;
                 if (vq == vmcount) {
                     vq = 0;
                     k = k + 1;
                 }
             }
 
             double lbco = (double) tasksum / mipssum; // Calc Load Balancing Coefficient
 
             // Calc VM queue optimum length of task in load balancing
             for (int j = 0; j < vmcount; j++) {
                 vmqoplen[j] = Math.round(lbco * vmmips[j]);
             }
 
             // Calc VM queue current length of task in load balancing
             // Calc VM queue task variance
             // Calc VM queue task absolute variance
             // Calc Current total VM queue task absolute variance
             cursumvmqlabsvar = 0;
             for (int j = 0; j < vmcount; j++) {
                 vmcurqlen[j] = IntStream.of(vmq[j]).sum();
                 vmqlvar[j] = vmqoplen[j] - vmcurqlen[j];
                 vmqlabsvar[j] = Math.abs(vmqlvar[j]);
                 cursumvmqlabsvar = cursumvmqlabsvar + vmqlabsvar[j];
             }
 
             // Choose start state randomly. The VM queue must not be empty
             int state = rand.nextInt(states);
             while (IntStream.of(vmq[state]).sum() == 0) {
                 state = rand.nextInt(states);
             }
             int nextstate = state; // At first current state and next state are the same
 
             for (int learn = 0; learn < 1000; learn++) {
                 prevsumvmqlabsvar = cursumvmqlabsvar;
 
                 // Choose a job to relocation from current queue(state) randomly
                 int nonzerojobs = 0;
                 int[] jobidx = new int[taskcount];
                 for (int idx = 0; idx < vmq[state].length; idx++) {
                     if (vmq[state][idx] != 0) {
                         nonzerojobs++;
                         jobidx[nonzerojobs - 1] = idx;
                     }
                 }
 
                 int choosedjobidx = rand.nextInt(nonzerojobs);
                 int choosedjob = vmq[state][jobidx[choosedjobidx]];
                 vmq[state][jobidx[choosedjobidx]] = 0;
 
                 while (state == nextstate) {
                     nextstate = rand.nextInt(states);
                 }
 
                 for (int idx = 0; idx < vmq[nextstate].length; idx++) {
                     if (vmq[nextstate][idx] == 0) {
                         vmq[nextstate][idx] = choosedjob;
                         break;
                     }
                 }
 
                 // Calc current total VM queue task absolute variance
                 cursumvmqlabsvar = 0;
                 for (int j = 0; j < vmcount; j++) {
                     vmcurqlen[j] = IntStream.of(vmq[j]).sum();
                     vmqlvar[j] = vmqoplen[j] - vmcurqlen[j];
                     vmqlabsvar[j] = Math.abs(vmqlvar[j]);
                     cursumvmqlabsvar = cursumvmqlabsvar + vmqlabsvar[j];
                 }
 
                 long r = prevsumvmqlabsvar - cursumvmqlabsvar; // Get action Reward
 
                 double q = getQ(state, Arrays.asList(tasktypelist).indexOf(choosedjob), nextstate); // Get current Q table value for state & action
 
                 double maxQ = getMaxQ(nextstate, Arrays.asList(tasktypelist).indexOf(choosedjob)); // Get Max-Q value for next state
 
                 double value = q + alpha * (r + gamma * maxQ - q); // Calc action value
 
                 setQ(state, Arrays.asList(tasktypelist).indexOf(choosedjob), nextstate, value); // Insert action value in Q table
 
                 state = nextstate; // Change state to next state
             }
         }
         Log.printLine("End of Training Episode...");
         // End of Training Episode
         // ==============================================================================================
 
         // Model Test Episode
         try {
             // Generate tasks for test randomly
             int vq = 0;
             int k = 0;
             int tasksum = 0;
             int[][] vmq = new int[vmcount][taskcount]; // Vm's task queue table
             int[][] noOpVmQ = new int[vmcount][taskcount]; // Vm's task queue table
 
             for (int j = 0; j < taskcount; j++) {
                 int jobtype = rand.nextInt(tasktype); // Create task randomly
                 tasksum = tasksum + tasktypelist[jobtype]; // Calc total created task in every pass
                 // Task Round Robin scheduling in VM's queue
                 vmq[vq][k] = tasktypelist[jobtype];
                 noOpVmQ[vq][k] = tasktypelist[jobtype];
                 vq = vq + 1;
                 if (vq == vmcount) {
                     vq = 0;
                     k = k + 1;
                 }
             }
 
             double lbco = (double) tasksum / mipssum; // Calc Load Balancing Coefficient
 
             // Calc VM queue optimum length of task in load balancing
             for (int j = 0; j < vmcount; j++) {
                 vmqoplen[j] = Math.round(lbco * vmmips[j]);
             }
 
             // Calc VM queue current length of task in load balancing
             // Calc VM queue task variance
             // Calc VM queue task absolute variance
             // Calc Current total VM queue task absolute variance
             cursumvmqlabsvar = 0;
             for (int j = 0; j < vmcount; j++) {
                 vmcurqlen[j] = IntStream.of(vmq[j]).sum();
                 vmqlvar[j] = vmqoplen[j] - vmcurqlen[j];
                 vmqlabsvar[j] = Math.abs(vmqlvar[j]);
                 cursumvmqlabsvar = cursumvmqlabsvar + vmqlabsvar[j];
             }
             prevsumvmqlabsvar = cursumvmqlabsvar;
 
             // Schedule jobs on VMs with Q-table using
             for (int state = 0; state < states; state++) {
                 for (int tt = 0; tt < tasktype; tt++) {
                     double[] qline = Q[(state * tasktype) + tt]; // Trace Q-table in row mode
                     // Determine Q-table current row max value index
                     while (DoubleStream.of(qline).sum() != 0) {
                         double maxValue = -Double.MAX_VALUE;
                         int maxValueIdx = 0;
                         for (int qlidx = 0; qlidx < states; qlidx++) {
                             if (maxValue < qline[qlidx] && qline[qlidx] != 0) {
                                 maxValue = qline[qlidx];
                                 maxValueIdx = qlidx;
                             }
                         }
                         qline[maxValueIdx] = 0;
 
                         for (int l = 0; l < vmq[state].length; l++) {
                             if (vmq[state][l] == tasktypelist[tt]) {
                                 int choosedjob = vmq[state][l];
                                 long sourceVmQlAbsvar = Math.abs(vmqoplen[state] - (vmcurqlen[state] - choosedjob));
                                 long destVmQlAbsvar = Math.abs(vmqoplen[maxValueIdx] - (vmcurqlen[maxValueIdx] + choosedjob));
                                 long newSumVmQlAbsVar = cursumvmqlabsvar - vmqlabsvar[state] + sourceVmQlAbsvar
                                         - vmqlabsvar[maxValueIdx] + destVmQlAbsvar;
                                 if (newSumVmQlAbsVar < cursumvmqlabsvar) {
                                     vmcurqlen[state] = vmcurqlen[state] - choosedjob;
                                     vmcurqlen[maxValueIdx] = vmcurqlen[maxValueIdx] + choosedjob;
                                     vmqlabsvar[state] = sourceVmQlAbsvar;
                                     vmqlabsvar[maxValueIdx] = destVmQlAbsvar;
                                     cursumvmqlabsvar = newSumVmQlAbsVar;
                                     vmq[state][l] = 0;
                                     for (int idx = 0; idx < vmq[maxValueIdx].length; idx++) {
                                         if (vmq[maxValueIdx][idx] == 0) {
                                             vmq[maxValueIdx][idx] = choosedjob;
                                             break;
                                         }
                                     }
                                 }
                             }
                         }
                     }
                 }
             }
 
             // Create VMs and Tasks in Cloud and assign Tasks to VMs same optimized VM's queue
             // First step: Initialize the CloudSim package. It should be called
             // before creating any entities.
             int num_user = 2; // number of cloud users
             Calendar calendar = Calendar.getInstance();
             boolean trace_flag = false; // mean trace events
 
             // Initialize the CloudSim library
             CloudSim.init(num_user, calendar, trace_flag);
 
             // Second step: Create Datacenters
             // Datacenters are the resource providers in CloudSim. We need at list one of them to run a CloudSim simulation
             @SuppressWarnings("unused")
             Datacenter datacenter0 = createDatacenter("Datacenter_0");
             Host host0 = datacenter0.getHostList().get(0);
             Host host1 = datacenter0.getHostList().get(1);
 
             // Third step: Create Broker
             DatacenterBroker broker0 = createBroker(0);
             int brokerId0 = broker0.getId();
 
             DatacenterBroker broker1 = createBroker(1);
             int brokerId1 = broker1.getId();
 
             // Fourth step: Create virtual machines
             vmlist0 = new ArrayList<Vm>();
             vmlist1 = new ArrayList<Vm>();
             // Vm fqvm1,;
             int vmid;
             int mips = 0;
             long size = 10000; // image size (MB)
             int ram = 512; // vm memory (MB)
             long bw = 1000;
             int pesNumber = 1; // number of cpus
             String vmm = "Xen"; // VMM name
 
             for (int i = 0; i < vmcount; i++) {
                 // VM description VM
                 vmid = i;
                 mips = vmmips[i];
                 // create VM
                 Vm fqvm0 = new Vm(vmid, brokerId0, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerSpaceShared());
                 fqvm0.setHost(host0);
                 vmlist0.add(fqvm0);
 
                 Vm fqvm1 = new Vm(vmid, brokerId1, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerSpaceShared());
                 fqvm1.setHost(host1);
                 vmlist1.add(fqvm1);
             }
             // submit vm list to the broker
             broker0.submitVmList(vmlist0);
             broker1.submitVmList(vmlist1);
 
             // Fifth step: Create Cloudlets
             cloudletList0 = new ArrayList<Cloudlet>();
             cloudletList1 = new ArrayList<Cloudlet>();
 
             // Cloudlet properties
             pesNumber = 1;
             long fileSize = 3000;
             long outputSize = 3000;
             UtilizationModel utilizationModel = new UtilizationModelFull();
             int cloudletid0 = 0;
             int cloudletid1 = 1000;
             int[][] binder0 = new int[vmcount][taskcount]; // Vm to Task bind table
             int[][] binder1 = new int[vmcount][taskcount]; // Vm to Task bind table
             for (int i = 0; i < vmcount; i++) {
                 for (int j = 0; j < taskcount; j++) {
                     if (vmq[i][j] > 0) {
                         // if (noOpVmQ[i][j] > 0){
                         long length = vmq[i][j];
                         Cloudlet cloudlet = new Cloudlet(cloudletid0, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
                         cloudlet.setUserId(brokerId0);
                         cloudletList0.add(cloudlet);
                         binder0[i][j] = cloudletid0;
                         cloudletid0++;
                     } else {
                         binder0[i][j] = -1;
                     }
                     if (noOpVmQ[i][j] > 0) {
                         long length = noOpVmQ[i][j];
                         Cloudlet cloudlet = new Cloudlet(cloudletid1, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
                         cloudlet.setUserId(brokerId1);
                         cloudletList1.add(cloudlet);
                         binder1[i][j] = cloudletid1;
                         cloudletid1++;
                     } else {
                         binder1[i][j] = -1;
                     }
 
                 }
             }
 
             // submit cloudlet list to the broker
             broker0.submitCloudletList(cloudletList0);
             broker1.submitCloudletList(cloudletList1);
             // bind the cloudlets to the vms. This way, the broker
             // will submit the bound cloudlets only to the specific VM
             for (int i = 0; i < vmcount; i++) {
                 for (int j = 0; j < taskcount; j++) {
                     if (binder0[i][j] > -1) {
                         broker0.bindCloudletToVm(binder0[i][j], vmlist0.get(i).getId());
                     }
                     if (binder1[i][j] > -1) {
                         broker1.bindCloudletToVm(binder1[i][j], vmlist1.get(i).getId());
                     }
 
                 }
             }
 
             // Sixth step: Starts the simulation
             Log.printLine("QLO Optimized started!");
             CloudSim.startSimulation();
 
             // Final step: Print results when simulation is over
             List<Cloudlet> cloudletList0 = broker0.getCloudletReceivedList();
             List<Cloudlet> cloudletList1 = broker1.getCloudletReceivedList();
             List<PowerHost> powerhosts = datacenter0.getHostList();
 
             CloudSim.stopSimulation();
             Log.printLine("=============================================================================");
             Log.printLine("=============> User (Optimized) " + brokerId0 + "    ");
             printCloudletList(cloudletList0, powerhosts.get(0), true);
 
             Log.printLine("=============================================================================");
             Log.printLine("=============> User (No Optimized)" + brokerId1 + "    ");
             printCloudletList(cloudletList1, powerhosts.get(1), false);
 
             Log.printLine("QLO Optimized finished!");
 
             // Print improvement results
             printImprovementResults();
 
         } catch (Exception e) {
             e.printStackTrace();
             Log.printLine("The simulation has been terminated due to an unexpected error");
         }
     }
 
     private static Datacenter createDatacenter(String name) {
 
         // Here are the steps needed to create a PowerDatacenter:
         // 1. We need to create a list to store
         // our machine
         List<Host> hostList = new ArrayList<Host>();
 
         // 2. A Machine contains one or more PEs or CPUs/Cores.
         // In this example, it will have only one core.
         List<Pe> peList = new ArrayList<Pe>();
 
         int mips = 5000;
 
         // 3. Create PEs and add these into a list.
         peList.add(new Pe(0, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating
 
         // 4. Create Host with its id and list of PEs and add them to the list of machines
         int hostId = 0;
         int ram = 20480; // host memory (MB)
         long storage = 1000000; // host storage
         int bw = 100000;
 
         hostList.add(
                 new PowerHost(
                         hostId,
                         new RamProvisionerSimple(ram),
                         new BwProvisionerSimple(bw),
                         storage,
                         peList,
                         new VmSchedulerTimeShared(peList),
                         new PowerModelSpecPower_QLO())
         ); // This is our machine
 
         hostId = 1;
         hostList.add(
                 new PowerHost(
                         hostId,
                         new RamProvisionerSimple(ram),
                         new BwProvisionerSimple(bw),
                         storage,
                         peList,
                         new VmSchedulerTimeShared(peList),
                         new PowerModelSpecPower_QLO())
         ); // This is our machine
 
         // 5. Create a DatacenterCharacteristics object that stores the
         // properties of a data center: architecture, OS, list of
         // Machines, allocation policy: time- or space-shared, time zone
         // and its price (G$/Pe time unit).
         String arch = "x86"; // system architecture
         String os = "Linux"; // operating system
         String vmm = "Xen";
         double time_zone = 10.0; // time zone this resource located
         double cost = 3.0; // the cost of using processing in this resource
         double costPerMem = 0.05; // the cost of using memory in this resource
         double costPerStorage = 0.001; // the cost of using storage in this resource
         double costPerBw = 0.0; // the cost of using bw in this resource
         LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN devices by now
 
         DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                 arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);
 
         // 6. Finally, we need to create a PowerDatacenter object.
         Datacenter datacenter = null;
         try {
             datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
         } catch (Exception e) {
             e.printStackTrace();
         }
 
         return datacenter;
     }
 
     // We strongly encourage users to develop their own broker policies, to submit vms and cloudlets according
     // to the specific rules of the simulated scenario
     private static DatacenterBroker createBroker(int id) {
 
         DatacenterBroker broker = null;
         try {
             broker = new DatacenterBroker("Broker" + id);
         } catch (Exception e) {
             e.printStackTrace();
             return null;
         }
         return broker;
     }
 
     /**
      * Prints the Cloudlet objects
      * 
      * @param list list of Cloudlets
      */
     private static void printCloudletList(List<Cloudlet> list, PowerHost host, boolean isOptimized) {
         int size = list.size();
         Cloudlet cloudlet;
 
         String indent = "    ";
         Log.printLine();
         Log.printLine("========== OUTPUT ==========");
         Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
                 "Data center ID" + indent + "VM ID" + indent + "Time" + indent +
                 "Start Time" + indent + "Finish Time" + indent + "Cost" + indent + "Response Time");
 
         DecimalFormat dft = new DecimalFormat("###.##");
         double maxFinishTime = 0;
         double totalCost = 0;
         double totalResponseTime = 0;
 
         for (int i = 0; i < size; i++) {
             cloudlet = list.get(i);
             Log.print(indent + cloudlet.getCloudletId() + indent + indent);
 
             if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                 Log.print("SUCCESS");
 
                 // Calculate cost
                 double costPerSecond = 0.01; // Cost of using CPU per second
                 double costPerRam = 0.001; // Cost of using RAM per MB
                 double costPerBw = 0.0001; // Cost of using bandwidth per MB
                 double cost = (cloudlet.getActualCPUTime() * costPerSecond) +
                         (cloudlet.getUtilizationOfRam(cloudlet.getActualCPUTime()) * (costPerRam * cloudlet.getCloudletLength())) +
                         (cloudlet.getUtilizationOfBw(cloudlet.getCloudletLength() / 1000) * costPerBw);
 
                 // Calculate response time
                 double responseTime = cloudlet.getFinishTime() - cloudlet.getExecStartTime();
 
                 // Update total cost and response time
                 totalCost += cost;
                 totalResponseTime += responseTime;
 
                 // Print the cloudlet details in table format
                 Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                         indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent +
                         dft.format(cloudlet.getExecStartTime()) + indent + indent + dft.format(cloudlet.getFinishTime()) +
                         indent + indent + dft.format(cost) + indent + indent + dft.format(responseTime));
 
                 // Update maxFinishTime
                 if (cloudlet.getFinishTime() > maxFinishTime) {
                     maxFinishTime = cloudlet.getFinishTime();
                 }
             }
         }
 
         // Calculate host energy consumption
         int timeLength = (int) Math.round(maxFinishTime * 100);
         double[][] timeTable = new double[vmcount][timeLength];
         for (Cloudlet cl : list) {
             for (int i = (int) Math.round(cl.getExecStartTime() * 100); i < (int) Math.round(cl.getFinishTime() * 100); i++) {
                 timeTable[cl.getVmId()][i] = vmmips[cl.getVmId()];
             }
         }
 
         double hostPower = 0; // Host power consumption in kWh
         for (int j = 0; j < timeLength; j++) {
             double utilsum = 0;
             for (int i = 0; i < vmcount; i++) {
                 utilsum += timeTable[i][j];
             }
             double hostUtilPart = host.getPowerModel().getPower(utilsum / IntStream.of(vmmips).sum());
             hostPower += hostUtilPart;
         }
 
         // Calculate energy cost
         double hostEnergyCost = hostPower * energyCostPerKWh; // Host energy cost
 
         Log.printLine("=============================================================");
         Log.printLine("Host " + host.getId() + " Energy Consumption: " + hostPower + " kWh");
         Log.printLine("Host " + host.getId() + " Energy Cost: $" + hostEnergyCost); // نمایش هزینه انرژی
         Log.printLine("=============================================================");
 
         // Save values for percentage improvement calculation
         if (isOptimized) {
             optimizedExecutionTime = maxFinishTime;
             optimizedResponseTime = totalResponseTime / size;
             optimizedEnergyCost = hostEnergyCost;
             optimizedEnergyConsumption = hostPower;
         } else {
             nonOptimizedExecutionTime = maxFinishTime;
             nonOptimizedResponseTime = totalResponseTime / size;
             nonOptimizedEnergyCost = hostEnergyCost;
             nonOptimizedEnergyConsumption = hostPower;
         }
     }
 
     /**
      * Get Max Q
      * 
      * @param list state
      */
     private static double getMaxQ(int state, int job) {
 
         double maxValue = Double.NEGATIVE_INFINITY;
 
         for (int i = 0; i < vmcount; i++) {
 
             if (maxValue < Q[(state * tasktype) + job][i]) {
 
                 maxValue = Q[(state * tasktype) + job][i];
 
             }
         }
         return maxValue;
 
         // return Arrays.stream(Q[(state * tasktype) + job]).max().getAsDouble();
 
     }
 
     /**
      * Get Q table value
      * 
      * @param list state, action
      */
 
     private static double getQ(int state, int job, int netxstate) {
 
         return Q[(state * tasktype) + job][netxstate];
 
     }
 
     /**
      * Set Q table value
      * 
      * @param list state, action, value
      */
     private static void setQ(int state, int job, int netxstate, double value) {
 
         Q[(state * tasktype) + job][netxstate] = value;
 
     }
 
     /**
      * Print improvement results
      */
     private static void printImprovementResults() {
         DecimalFormat dft = new DecimalFormat("###.##");
 
         // Calculate the percentage of execution time improvement
         double executionTimeImprovement = ((nonOptimizedExecutionTime - optimizedExecutionTime) / nonOptimizedExecutionTime) * 100;
 
         // Calculate the percentage of response time improvement
         double responseTimeImprovement = ((nonOptimizedResponseTime - optimizedResponseTime) / nonOptimizedResponseTime) * 100;
 
         // Calculate the percentage of energy consumption improvement
         double energyImprovement = ((nonOptimizedEnergyConsumption - optimizedEnergyConsumption) / nonOptimizedEnergyConsumption) * 100;
 
         // Calculate the percentage of energy cost improvement
         double energyCostImprovement = ((nonOptimizedEnergyCost - optimizedEnergyCost) / nonOptimizedEnergyCost) * 100;
 
         Log.printLine("=============================================================");
         Log.printLine("Improvement Results:");
         Log.printLine("Execution Time (Optimized): " + dft.format(optimizedExecutionTime));
         Log.printLine("Execution Time (Non-Optimized): " + dft.format(nonOptimizedExecutionTime));
         Log.printLine("Execution Time Improvement: " + dft.format(executionTimeImprovement) + "%");
         Log.printLine("Average Response Time (Optimized): " + dft.format(optimizedResponseTime));
         Log.printLine("Average Response Time (Non-Optimized): " + dft.format(nonOptimizedResponseTime));
         Log.printLine("Response Time Improvement: " + dft.format(responseTimeImprovement) + "%");
         Log.printLine("Energy Cost (Optimized): $" + dft.format(optimizedEnergyCost));
         Log.printLine("Energy Cost (Non-Optimized): $" + dft.format(nonOptimizedEnergyCost));
         Log.printLine("Energy Cost Improvement: " + dft.format(energyCostImprovement) + "%");
         Log.printLine("Energy Consumption (Optimized): " + dft.format(optimizedEnergyConsumption) + " kWh");
         Log.printLine("Energy Consumption (Non-Optimized): " + dft.format(nonOptimizedEnergyConsumption) + " kWh");
         Log.printLine("Energy Improvement: " + dft.format(energyImprovement) + "%");
         Log.printLine("=============================================================");
     }
 }