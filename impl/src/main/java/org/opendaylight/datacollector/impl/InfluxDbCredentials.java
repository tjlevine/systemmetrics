package org.opendaylight.datacollector.impl;

import com.google.common.base.Preconditions;

import java.util.Optional;

final class InfluxDbCredentials {
    final String host, port, user, password;

    private final class Default {
        final static String host = "localhost";
        final static String port = "8086";
        final static String user = "root";
        final static String password = "root";
    }

    private InfluxDbCredentials(final String host, final String port, final String user, final String password) {
        this.host = Preconditions.checkNotNull(host);
        this.port = Preconditions.checkNotNull(port);
        this.user = Preconditions.checkNotNull(user);
        this.password = Preconditions.checkNotNull(password);
    }

    static InfluxDbCredentials fromEnvironment() {
        final Optional<String> host = Optional.ofNullable(System.getenv("INFLUX_HOST"));
        final Optional<String> port = Optional.ofNullable(System.getenv("INFLUX_PORT"));
        final Optional<String> user = Optional.ofNullable(System.getenv("INFLUX_USER"));
        final Optional<String> pass = Optional.ofNullable(System.getenv("INFLUX_PASSWORD"));

        return new InfluxDbCredentials(host.orElse(Default.host), port.orElse(Default.port), user.orElse(Default.user), pass.orElse(Default.password));
    }

    String url() {
        return "http://" + this.host + ":" + this.port;
    }
}
