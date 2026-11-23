# Q-Learning Optimizer (QLO) for Cloud Task Scheduling

[![Java](https://img.shields.io/badge/Java-8%2B-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![CloudSim](https://img.shields.io/badge/Simulator-CloudSim%203.0-blue)](http://www.cloudbus.org/cloudsim/)
[![Reinforcement Learning](https://img.shields.io/badge/RL-Q--Learning-green.svg)](https://en.wikipedia.org/wiki/Q-learning)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An intelligent multi-agent reinforcement learning framework for autonomous task scheduling and energy-aware resource management in cloud environments, implemented on the **CloudSim 3.0** simulation platform.

> **Master's Thesis Project:** Developed as the primary methodological contribution for the M.Sc. thesis in Software Engineering titled:  
> *"A Unified Framework for Resource Management in Cloud Based on Intelligent Agents"* (Islamic Azad University, Science and Research Branch, Tehran, Iran, 2025).

---

## 🏛 System Architecture & Agents

The framework decouples task scheduling and resource allocation into a coordinated multi-agent workflow:

1. **Customer Agent:** Validates incoming work requests, performs structural checks on task sizes and priorities, and batches tasks into non-blocking queues.
2. **Initial Control Agent:** Inspects task queues and calculates the **Load Balancing Factor (LBF)** based on variance over average workload demand to guide resource distribution.
3. **Intelligent Scheduling Agent (QLO):** Employs an off-policy Q-learning optimization engine to iteratively learn optimal task-to-VM allocation policies balancing makespan and energy consumption.

### Mathematical Formulation
The Q-learning action-value matrix is updated using the temporal-difference rule:
$$Q(s, a) \leftarrow Q(s, a) + \alpha \left[ r + \gamma \max_{a'} Q(s', a') - Q(s, a) \right]$$

- **Learning Rate ($\alpha$):** 0.1
- **Discount Factor ($\gamma$):** 0.9
- **Exploration Rate ($\epsilon$):** 0.1
- **Training Episodes:** 1,000 iterations

---

## 📊 Empirical Benchmarking & Results

The framework was benchmarked in **CloudSim 3.0** against standard baseline heuristics (e.g., Round Robin) across two rigorous experimental workloads:

* **Dataset A (Moderate Scale):** 8 heterogeneous VMs (100–800 MIPS), 5 task profiles (100–500 MI length), evaluating workloads scaled from 50 to 140 tasks.
* **Dataset B (Large Scale):** 15 heterogeneous VMs (100–380 MIPS), 12 task profiles (100–320 MI length), evaluating workloads scaled from 140 to 230 tasks.

### Summary of Performance Improvements (QLO vs. Unoptimized Baseline)

| Metric | Dataset A (Peak Gain) | Dataset A (Observed Range) | Dataset B (Peak Gain) | Dataset B (Observed Range) |
| :--- | :---: | :---: | :---: | :---: |
| **Execution Time (Makespan)** | **80.21%** | 68.63% – 80.21% | **60.02%** | 31.75% – 60.02% |
| **Average Response Time** | **38.09%** | 28.83% – 38.09% | **17.88%** | 10.57% – 17.88% |
| **Energy Consumption (kWh)** | **70.61%** | 56.20% – 70.61% | **48.27%** | 23.16% – 48.27% |
| **Operational Energy Cost ($)** | **70.61%** | 56.20% – 70.61% | **48.27%** | 23.16% – 48.27% |

---

## 🛠 System Requirements & Setup

### Prerequisites
* Java Development Kit (JDK) 8 or higher
* CloudSim 3.0.3+ libraries (`cloudsim-3.0.3.jar`, `cloudsim-examples-3.0.3.jar`)

### Quickstart

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/amirsoleymanifar/qlearning-cloud-task-scheduling.git](https://github.com/amirsoleymanifar/qlearning-cloud-task-scheduling.git)
   cd qlearning-cloud-task-scheduling
   ```

2. **Place CloudSim JAR files** into a `lib/` directory inside the project root.

3. **Compile the source files:**
   ```bash
   javac -cp "lib/*:." QLO.java PowerModelSpecPower_QLO.java
   ```

4. **Execute the simulation:**
   ```bash
   java -cp "lib/*:." QLO
   ```

---

## 📁 Repository Structure

```text
├── QLO.java                    # Core Q-Learning Optimizer and scheduling simulation engine
├── PowerModelSpecPower_QLO.java # Dynamic power and energy consumption calculation model
├── algorithm_doc.md            # Detailed mathematical and algorithmic documentation
├── LICENSE                     # MIT License
└── README.md                   # Project overview and benchmark results
```

---

## 📜 Citation & License
This project is licensed under the MIT License. If you use this implementation in your research, please refer to the Master's thesis:
```bibtex
@mastersthesis{soleymanifar2025qlo,
  author  = {Amirhossein Soleymanifar},
  title   = {A Unified Framework for Resource Management in Cloud Based on Intelligent Agents},
  school  = {Islamic Azad University, Science and Research Branch},
  year    = {2025},
  address = {Tehran, Iran}
}
```
