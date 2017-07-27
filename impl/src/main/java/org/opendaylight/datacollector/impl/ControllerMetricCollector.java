/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.datacollector.impl;

import com.google.common.base.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.math.BigDecimal;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.InfluxDBIOException;

/**
 * @author Ashish Kumar(ashishk.iiit@gmail.com)
 * Some parts were taken from tsdr module.
 **/
public class ControllerMetricCollector extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(ControllerMetricCollector.class);

    private final class InfluxDbCredentials {
        final String url, username, password;

        public InfluxDbCredentials(final String url, final String username, final String password) {
            this.url = Preconditions.checkNotNull(url);
            this.username = Preconditions.checkNotNull(username);
            this.password = Preconditions.checkNotNull(password);
        }
    }

    private CpuDataCollector cpuDataCollector;
    private InfluxDB influxDB;
    private String dbName;
    private String rp;

    // these should be credentials should be read from somewhere so that it's configurable at runtime. environment variable maybe?
    private final InfluxDbCredentials creds = new InfluxDbCredentials("http://localhost:8086", "root", "root");

    private void setupInflux() throws RuntimeException {
        // setup influx connection
        this.influxDB = InfluxDBFactory.connect(creds.url, creds.username, creds.password);

        // test influx connectivity
        try {
            this.influxDB.ping();
        } catch (final InfluxDBIOException e) {
            logger.error("Cannot connect to influx db server at " + creds.url);
            throw new RuntimeException("Cannot connect to influx db server at " + creds.url);
        }

        this.dbName = "datacollector";
        influxDB.createDatabase(this.dbName);
        // check the databases in influx for persistence, if already exists then use it
        String version = this.influxDB.version();
        if (version.startsWith("0.") ) {
            this.rp = "default";
        } else {
            this.rp = "autogen";
        }
        this.influxDB.setDatabase(dbName);
        this.influxDB.setRetentionPolicy(rp);
    }

    private void setupCollector() throws RuntimeException {
        final Optional<CpuDataCollector> cpuDataCollector = CpuDataCollector.getCpuDataCollector();
        if (cpuDataCollector.isPresent()) {
            this.cpuDataCollector = cpuDataCollector.get();
        } else {
            logger.error("No suitable CPU Data Collector could be found!");
            throw new RuntimeException("No suitable CPU Data Collector could be found!");
        }
    }

    @Override
    public void run() {
        try {
            setupInflux();
            setupCollector();
        } catch (final RuntimeException e) {
            logger.error("Could not successfully complete metric collector setup!", e);
            return;
        }

        try {
            while (!interrupted()) {
                logger.debug("inserting new set of TSDR records");
                insertMemorySample();
                insertControllerCPUSample();
                insertMachineCPUSample();

                Thread.sleep(5000);
            }
        } catch (final InterruptedException err) {
            logger.info("ControllerMetricCollector thread has been interrupted and is now exiting");
            Thread.currentThread().interrupt();
        }
    }

    public Optional<Double> getControllerCPU() {
        logger.debug("Getting controller CPU data");
        return cpuDataCollector.getControllerCpu();
    }

    public Optional<Double> getMachineCPU() {
        logger.debug("Getting machine CPU data");
        return cpuDataCollector.getMachineCpu();
    }

    protected void insertMemorySample() {
        BigDecimal value = new BigDecimal(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        logger.debug("Inserting memory sample value");
        logger.debug(value.toString());
        this.influxDB.write("Controller,metricName=Heap:Memory:Usage metricValue="+value.toString());
    }

    protected void insertControllerCPUSample() {
        final Optional<Double> cpuValue = getControllerCPU();

        if (!cpuValue.isPresent()) {
            return;
        }
        BigDecimal value = new BigDecimal(cpuValue.get());
        logger.debug("Inserting controller cpu value");
        logger.debug(value.toString());
        this.influxDB.write("Controller,metricName=CPU:Usage metricValue="+value.toString());
    }

    protected void insertMachineCPUSample() {
        final Optional<Double> cpuValue = getMachineCPU();

        if (!cpuValue.isPresent()) {
            return;
        }
        BigDecimal value  = new BigDecimal(cpuValue.get());
        logger.debug("Inserting machine cpu value");
        logger.debug(value.toString());
        this.influxDB.write("Machine,metricName=CPU:Usage metricValue="+value.toString());
    }
}
