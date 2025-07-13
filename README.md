# qlearning-cloud-task-scheduling
Q-Learning based task scheduling optimization for cloud computing environments using CloudSim

## Master's Thesis Project
An intelligent cloud task scheduling optimization system using Q-Learning reinforcement learning algorithm to improve performance, response time, and energy consumption.

## Project Overview
This project presents an intelligent system for optimizing task scheduling in cloud environments using Q-Learning reinforcement learning algorithm. The system enhances performance, reduces response time, and minimizes energy consumption while maintaining load balancing across virtual machines.

## Key Features
- Q-Learning algorithm implementation for task scheduling
- Energy consumption optimization and cost reduction
- Cloud environment simulation using CloudSim framework
- Performance metrics calculation and visualization
- Load balancing with variance minimization
- Comparative analysis between optimized and non-optimized approaches

## System Requirements
- Java 8 or higher
- CloudSim 3.0+
- Maven 3.6+ (optional)
- Memory: 4GB RAM minimum

## Installation & Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/cloud-task-scheduling-qlearning.git
   cd cloud-task-scheduling-qlearning
   ```

2. Add CloudSim libraries to the lib/ directory

3. Compile the project:
   ```bash
   javac -cp "lib/*" QLO.java PowerModelSpecPower_QLO.java
   ```

4. Run the simulation:
   ```bash
   java -cp ".:lib/*" QLO
   ```

## Algorithm Parameters
- **Learning Rate (α)**: 0.1
- **Discount Factor (γ)**: 0.9
- **Exploration Rate (ε)**: 0.1
- **VM Count**: 4 (with MIPS: 100, 300, 500, 700)
- **Task Count**: 10 per simulation
- **Task Types**: 2 (with lengths: 115, 345)

## Results & Improvements
- **Execution Time**: Up to 25% improvement
- **Energy Consumption**: Up to 30% reduction
- **Response Time**: Up to 20% improvement
- **Energy Cost**: Significant cost reduction

