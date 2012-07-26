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

import com.superconnectors.telnet.api.TelnetListener;
import com.superconnectors.telnet.impl.Cmd;

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ResourceAdapter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @version $Revision$ $Date$
 */
public class TelnetActivationSpec implements ActivationSpec {

    private ResourceAdapter resourceAdapter;
    private final List<Cmd> cmds = new ArrayList<Cmd>();
    private int port;
    private String prompt;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<Cmd> getCmds() {
        return cmds;
    }

    @Override
    public void validate() throws InvalidPropertyException {
        if (port <= 0) throw new InvalidPropertyException("port");
        if (prompt == null || prompt.length() == 0) {
            prompt = "prompt>";
        }

        final Method[] methods = TelnetListener.class.getMethods();
        for (Method method : methods) {
            if (method.getName().startsWith("do")) {
                final StringBuilder name = new StringBuilder(method.getName());
                name.delete(0, 2);
                name.setCharAt(0, Character.toLowerCase(name.charAt(0)));
                cmds.add(new Cmd(name.toString(), method));
            }
        }
    }

    @Override
    public ResourceAdapter getResourceAdapter() {
        return resourceAdapter;
    }

    @Override
    public void setResourceAdapter(ResourceAdapter ra) throws ResourceException {
        this.resourceAdapter = ra;
    }
}
