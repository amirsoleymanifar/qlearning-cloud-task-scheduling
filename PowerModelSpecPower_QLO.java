/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cloudbus.cloudsim.power.models;

/**
 *
 * @author m_mohammadi
 */
public class PowerModelSpecPower_QLO implements PowerModel{
    
    private final double[] power = {70.0, 75.0, 80.0, 85.0, 90.0, 95.0, 100.0,105.5,112.1,114.3,118.2}; // Example values

    @Override
    public double getPower(double utilization) throws IllegalArgumentException {
        if (utilization < 0 || utilization > 1) {
            throw new IllegalArgumentException("Utilization must be between 0 and 1");
        }
        int index = (int) (utilization * (power.length - 1));
        return power[index];
    }
    
}
