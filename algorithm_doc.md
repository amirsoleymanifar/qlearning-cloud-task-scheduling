# Q-Learning Algorithm Implementation

## Overview
This document explains the Q-Learning algorithm implementation for cloud task scheduling optimization.

## Algorithm Description

### Q-Learning Fundamentals
Q-Learning is a model-free reinforcement learning algorithm that learns the optimal action-selection policy for a given finite Markov Decision Process (MDP).

### State Space
- **States**: Virtual Machines (VMs) in the cloud environment
- **State Count**: 4 VMs with different MIPS capacities (100, 300, 500, 700)

### Action Space
- **Actions**: Task migration between VMs
- **Action Types**: 2 task types with different computational requirements

### Q-Table Structure
```
Q[state × task_type][next_state] = Q-value
```

### Learning Parameters
- **Learning Rate (α)**: 0.1 - Controls how much new information overrides old information
- **Discount Factor (γ)**: 0.9 - Determines importance of future rewards
- **Exploration Rate (ε)**: 0.1 - Probability of choosing random action for exploration

### Reward Function
```
Reward = Previous_Variance - Current_Variance
```
Where variance represents the load balancing metric across VMs.

### Update Rule
```
Q(s,a) = Q(s,a) + α[r + γ max Q(s',a') - Q(s,a)]
```

## Implementation Details

### Training Phase
1. Initialize Q-table with zeros
2. For each episode:
   - Generate random tasks
   - Calculate load balancing coefficients
   - Apply Q-learning updates
   - Update Q-values based on rewards

### Testing Phase
1. Use trained Q-table for decision making
2. Select optimal actions using greedy policy
3. Compare with non-optimized scheduling

## Performance Metrics
- Execution Time
- Response Time
- Energy Consumption
- Energy Cost
- Load Balancing Variance