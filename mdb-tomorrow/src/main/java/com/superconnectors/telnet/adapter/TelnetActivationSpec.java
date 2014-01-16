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

import com.superconnectors.telnet.api.Command;
import com.superconnectors.telnet.api.Prompt;
import com.superconnectors.telnet.api.TelnetListener;
import com.superconnectors.telnet.impl.Cmd;

import javax.resource.ResourceException;
import javax.resource.spi.Activation;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ResourceAdapter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Activation(messageListeners = TelnetListener.class)
public class TelnetActivationSpec implements ActivationSpec {

    private ResourceAdapter resourceAdapter;
    private final List<Cmd> cmds = new ArrayList<Cmd>();
    private String prompt;
    private Class<?> beanClass;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public void setBeanClass(Class<?> beanClass) {
        this.beanClass = beanClass;
    }

    public List<Cmd> getCmds() {
        return cmds;
    }

    @Override
    public void validate() throws InvalidPropertyException {
        // Set Prompt
        final Prompt prompt = (Prompt) beanClass.getAnnotation(Prompt.class);
        if (prompt != null) {
            this.prompt = prompt.value();
        }

        // Get Commands
        final Method[] methods = beanClass.getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Command.class)) {
                final Command command = method.getAnnotation(Command.class);
                cmds.add(new Cmd(command.value(), method));
            }
        }

        // Validate
        if (this.prompt == null || this.prompt.length() == 0) {
            this.prompt = "prompt>";
        }
        if (this.cmds.size() == 0) {
            throw new InvalidPropertyException("No @Command methods");
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