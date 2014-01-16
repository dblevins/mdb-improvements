/*
 * Copyright 2012 David Blevins
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.superconnectors.telnet.adapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.resource.ResourceException;
import javax.resource.spi.Activation;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.Connector;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.superconnectors.telnet.api.TelnetListener;
import com.superconnectors.telnet.impl.TelnetServer;

/**
 * @version $Revision$ $Date$
 */
@Connector(
    description = "Telnet ResourceAdapter",
    displayName = "Telnet ResourceAdapter",
    eisType = "Telnet Adapter",
    version = "1.0"
)
public class TelnetResourceAdapter implements javax.resource.spi.ResourceAdapter {

    private final Map<Integer, TelnetServer> activated = new HashMap<Integer, TelnetServer>();

    /**
     * Corresponds to the ra.xml <config-property>
     */
    @Size(min = 1, max = 0xFFFF)
    @ConfigProperty(defaultValue = "2020")
    @NotNull
    private Integer port;

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void start(BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {
    }

    public void stop() {
    }

    public void endpointActivation(MessageEndpointFactory messageEndpointFactory, ActivationSpec activationSpec) throws ResourceException {
        final TelnetActivationSpec telnetActivationSpec = (TelnetActivationSpec) activationSpec;

        final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

        // This messageEndpoint instance is also castable to the ejbClass of the MDB
        final TelnetListener telnetListener = (TelnetListener) messageEndpoint;

        final TelnetServer telnetServer = new TelnetServer(telnetActivationSpec, telnetListener, port);

        try {
            telnetServer.activate();
            activated.put(port, telnetServer);
        } catch (IOException e) {
            throw new ResourceException(e);
        }
    }

    public void endpointDeactivation(MessageEndpointFactory messageEndpointFactory, ActivationSpec activationSpec) {
        final TelnetActivationSpec telnetActivationSpec = (TelnetActivationSpec) activationSpec;

        final TelnetServer telnetServer = activated.remove(port);

        try {
            telnetServer.deactivate();
        } catch (IOException e) {
            e.printStackTrace();
        }

        final MessageEndpoint endpoint = (MessageEndpoint) telnetServer.getListener();

        endpoint.release();
    }

    public XAResource[] getXAResources(ActivationSpec[] activationSpecs) throws ResourceException {
        return new XAResource[0];
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof TelnetResourceAdapter)) {
            return false;
        }
        TelnetResourceAdapter other = (TelnetResourceAdapter) obj;
        return this.port == other.port;
    }

    @Override
    public int hashCode() {
        return this.port;
    }

}
