/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datacollector.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Taken from tsdr implementation.
 */

public class SunCpuDataCollector extends CpuDataCollector {

    private static final Logger LOG = LoggerFactory.getLogger(SunCpuDataCollector.class);

    private final Optional<Method> procCpuMethod;
    private final Optional<Method> machineCpuMethod;

    public SunCpuDataCollector() throws SunOsMBeanNotPresentException {
        this.procCpuMethod = getSunOsMBeanProcCpuMethod();
        this.machineCpuMethod = getSunOsMBeanMachineCpuMethod();
    }

    private Optional<Method> getSunOsMBeanProcCpuMethod() throws SunOsMBeanNotPresentException {
        final Optional<Class<?>> sunOsMBeanClass = getSunOsMBeanClass();

        if (!sunOsMBeanClass.isPresent()) {
            return Optional.empty();
        }

        try {
            final Method sunProcCpuMethod = sunOsMBeanClass.get().getMethod("getProcessCpuLoad", (Class<?>[]) null);
            return Optional.of(sunProcCpuMethod);
        } catch (final NoSuchMethodException e) {
            throw new SunOsMBeanNotPresentException(e);
        }
    }

    private Optional<Method> getSunOsMBeanMachineCpuMethod() throws SunOsMBeanNotPresentException {
        final Optional<Class<?>> sunOsMBeanClass = getSunOsMBeanClass();

        if (!sunOsMBeanClass.isPresent()) {
            return Optional.empty();
        }

        try {
            final Method sunMachineCpuMethod = sunOsMBeanClass.get().getMethod("getSystemCpuLoad", (Class<?>[]) null);
            return Optional.of(sunMachineCpuMethod);
        } catch (final NoSuchMethodException e) {
            throw new SunOsMBeanNotPresentException(e);
        }
    }

    private Optional<Class<?>> getSunOsMBeanClass() throws SunOsMBeanNotPresentException {
        try {
            final Class osMBeanClass = Class.forName("com.sun.management.OperatingSystemMXBean");

            final OperatingSystemMXBean javaOSMBean = ManagementFactory.getOperatingSystemMXBean();

            if (osMBeanClass.isInstance(javaOSMBean)) {
                // this JVM has the com.sun.management.OperatingSystemMXBean class available
                return Optional.of(osMBeanClass);
            }
        } catch (final ClassNotFoundException e) {
            throw new SunOsMBeanNotPresentException(e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Double> getControllerCpu() {
        if (!procCpuMethod.isPresent()) {
            LOG.error("Tried to get controller CPU from Sun OS MBean's getProcessCpuLoad() method but it is not present! (this should not happen)");
            return Optional.empty();
        }

        try {
            final double controllerCpu = (double) procCpuMethod.get().invoke(ManagementFactory.getOperatingSystemMXBean());
            return Optional.of(controllerCpu);
        } catch (final IllegalAccessException | InvocationTargetException e) {
            LOG.warn("Got exception while getting controller CPU usage from Sun OS MBean", e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Double> getMachineCpu() {
        if (!machineCpuMethod.isPresent()) {
            LOG.error("Tried to get machine CPU from Sun OS MBean's getSystemCpuLoad() method but it is not present! (this should not happen)");
            return Optional.empty();
        }

        try {
            final double machineCpu = (double) machineCpuMethod.get().invoke(ManagementFactory.getOperatingSystemMXBean());
            return Optional.of(machineCpu);
        } catch (final IllegalAccessException | InvocationTargetException e) {
            LOG.warn("Got exception while getting controller CPU usage from Sun OS MBean", e);
        }

        return Optional.empty();
    }
}