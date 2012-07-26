# Message-Driven Beans Today

A while ago I blogged about how the [MDB/Connector relationship works](http://blog.dblevins.com/2010/10/ejbnext-connectorbean-api-jax-rs-and.html) and proposed
a change to the model that would finally unlock the great potential of this long misunderstood technology.

The example used in that blog was a fictitious "Email" Connector.  While it did a great job explaining the
moving parts of the Connector/MDB relationship, it didn't go far enough.  This is a continuation of that
study presenting a functional Connector that does something very non-asynchronous, Telnet.

The goal of this example is to better stress some important and often misunderstood concepts about the
Connector/MDB model so we can better see its potential and improve it.  Specifically to show how the
Connector has near 100% of the control of the bean, its lifecycle, and how and when it is invoked.

This Telnet Connector intentionally diverges from the typical JMS-centric view and demonstrates:

 - MDBs can be stateful
 - MDBs can be synchronous
 - The "listener" interface is effectively no different than a business interface
 - Just about anything can be done with a Connector

## Writing a Connector

To create your own Connector, you have a very short checklist.  You must supply:

  - An implementation of `javax.resource.spi.ResourceAdapter`
  - An implementation of `javax.resource.spi.ActivationSpec`
  - A "message listener" interface
  - A `ra.xml` file

The **message listener interface** is what you will expect all your MDBs to implement and it is entirely up
to you as the Connector Provider how you wish to design it.  It doesn't have to extend any other interfaces
or have methods of a specific style or pattern.  Sky is the limit.

The **activation spec** is a plain java bean of the getter/setter variety.  It serves as a configuration mechanism.
You use it to expose any configuration options you like to the Application Developer.

The **resource adapter** is bascially the code that does the real work.  It creates (activates) the MDB
whenever it wants and invokes it as it sees fit.  It's not unlike any other code that can invoke an EJB.
Unlike other code that runs in a container it can open sockets, accept connections, start threads and more.
It doesn't have to do any of these complicated things, but it can.

The **ra.xml** file simply ties the above three parts together and describes them to the container so that when a bean
is deployed using our _message listener interface_  the container knows to give it to our _resource adapter_ to manage.

So in short:

 - **message listener interface** is like a business interface for an EJB
 - **resource adapter** is the code that invokes the EJB
 - **activation spec** is a configuration object

## Telnet Connector

Our Telnet Connector is a simple service that opens a port, accepts telnet connections and treats commands typed in the terminal
as method invocations.  The message listener interface determines which commands are available, the MDB supplies the logic
of what these commands do, the Connector takes care of the rest.

A user will connect with a telnet client which will cause our resource adapter to ask the container for an instance of the MDB.
We will let the user invoke the MDB as much as he or she likes.  When the telnet client exits, the connection is closed and the
the MDB is destroyed.

All our Connector code will be in the package `package com.superconnectors.telnet` so we can keep it clear that
this is not "application code", but code supplied by the Connector Provider, which in this situation is us.

Note that the code itself is meant as a functional example, however it is still an example.  Proper exception hanlding and other
aspects that make good production code are lacking.

### Telnet message listener interface

The interface we expect MDBs to implement to use our Telnet Connector is fairly trivial set of fixed commands.

    package com.superconnectors.telnet.api;

    import java.util.regex.Pattern;

    public interface TelnetListener {
        public String doDate();

        public String doJoke();

        public String doList(Pattern pattern);

        public String doSet(String key, String value);

        public String doGet(String key);

        public int doAdd(int a, int b);
    }

It would be wonderful if the Application Developer could chose the commands which are available, however this is as close as we
can get and still have strongly typed methods.  The alternative would be a too generic method `public Object doCommand(String name, String[] args)`, which is
not very expressive and ultimately one step short of reflection and creates a lot of work for the user.

For now we'll live with our less flexible, but far easier set of commands.

### Telnet ActivationSpec

The following is the `ActivationSpec` for our Telnet Connector.  It has two configuration options; `port` and `prompt`

    package com.superconnectors.telnet.adapter;

    //imports

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

The Application Developer can set the `port` and `prompt` via the standard EJB `@javax.ejb.ActivationConfigProperty` annotations in the `@javax.ejb.MessageDriven` annotation
on the class where the MDB is declared.

The MDB Container creates the `TelnetActivationSpec` instance, sets the `port` and `prompt` using the data from the `@MessageDrive` & `@ActivationConfigProperty` annotations,
then hands it to our Resource Adapter.

### Telnet ResourceAdapter

Here is the actual `ResourceAdapter` implementation code with the telnet details moved to another class so we can focus on
seeing the basic parts any `ResourceAdapter` needs to do.

The creation process is very simple.  The `endpointActivation` is called by the MDB Container when an MDB that implements `TelnetListener` is deployed.
In this method our `ResourceAdapter` is given essentially a factory for creating instances of `TelnetListener` along with the configuration
object (`ActivationSpec` instance).

    package com.superconnectors.telnet.adapter;

    import com.superconnectors.telnet.api.TelnetListener;
    import com.superconnectors.telnet.impl.TelnetServer;

    import javax.resource.ResourceException;
    import javax.resource.spi.ActivationSpec;
    import javax.resource.spi.BootstrapContext;
    import javax.resource.spi.ResourceAdapterInternalException;
    import javax.resource.spi.endpoint.MessageEndpoint;
    import javax.resource.spi.endpoint.MessageEndpointFactory;
    import javax.transaction.xa.XAResource;
    import java.io.IOException;
    import java.util.HashMap;
    import java.util.Map;

    /**
     * @version $Revision$ $Date$
     */
    public class TelnetResourceAdapter implements javax.resource.spi.ResourceAdapter {

        private final Map<Integer, TelnetServer> activated = new HashMap<Integer, TelnetServer>();

        public void start(BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {
        }

        public void stop() {
        }

        public void endpointActivation(MessageEndpointFactory messageEndpointFactory, ActivationSpec activationSpec) throws ResourceException {
            final TelnetActivationSpec telnetActivationSpec = (TelnetActivationSpec) activationSpec;

            final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

            final TelnetListener telnetListener = (TelnetListener) messageEndpoint;

            final TelnetServer telnetServer = new TelnetServer(telnetActivationSpec, telnetListener);

            try {
                telnetServer.activate();
                activated.put(telnetActivationSpec.getPort(), telnetServer);
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        public void endpointDeactivation(MessageEndpointFactory messageEndpointFactory, ActivationSpec activationSpec) {
            final TelnetActivationSpec telnetActivationSpec = (TelnetActivationSpec) activationSpec;

            final TelnetServer telnetServer = activated.remove(telnetActivationSpec.getPort());

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

    }

Note that the `TelnetListener` object created by the MDB Container also implements `MessageEndpoint`.  This is possible
because the object is essentially a proxy just like any other EJB reference.  For all intents and purposes the `TelnetListener`
is a business interface.  A business interface created by the Connector Provider.

Not shown here, but prior to invoking the MDB the Telnet Connector simply needs to call `beforeDelivery` and `afterDeliver` before invoking
the MDB via the Container-created proxy.  For example, here's how the Telnet Connector might implement invoking `doJoke`:

    TelnetListener telnetListener = ..//
    Method doJoke = TelnetListener.class.getMethod("doJoke");

    (MessageEndpoint(telnetListener)).beforeDelivery(doJoke);
    telnetListener.doJoke();
    (MessageEndpoint(telnetListener)).afterDelivery();

Proper exception handling withstanding the flow is pretty simple.

### Telnet ra.xml file

To package it all up, we create a `ra.xml` file for the Telnet Connector like the following:

    <connector xmlns="http://java.sun.com/xml/ns/j2ee"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee
               http://java.sun.com/xml/ns/j2ee/connector_1_5.xsd"
               version="1.5">

      <description>Telnet ResourceAdapter</description>
      <display-name>Telnet ResourceAdapter</display-name>

      <vendor-name>SuperConnectors</vendor-name>

      <eis-type>Telnet Adapter</eis-type>

      <resourceadapter-version>1.0</resourceadapter-version>

      <resourceadapter id="TelnetResourceAdapter">

        <resourceadapter-class>com.superconnectors.telnet.adapter.TelnetResourceAdapter</resourceadapter-class>

        <inbound-resourceadapter>
          <messageadapter>
            <messagelistener>
              <messagelistener-type>com.superconnectors.telnet.api.TelnetListener</messagelistener-type>
              <activationspec>
                <activationspec-class>com.superconnectors.telnet.adapter.TelnetActivationSpec</activationspec-class>
              </activationspec>
            </messagelistener>
          </messageadapter>
        </inbound-resourceadapter>

      </resourceadapter>
    </connector>

The Telnet Connector goes into a `.rar` file which is similar to a `.war` or  `.ear` file in that it is an jar of jars.  Let's call our file `telnet.rar` and
package it up to contain the following files:

     META-INF/ra.xml
     superconnectors-telnet.jar

The `superconnectors-telnet.jar` will contain all the above code.

## Sample MDB

Here we use the pretend package `org.developer.application` to make it clear this work is done by the Application Developer, or simply put, some
app that wants to use our Telnet Connector.

    package org.developer.application;

    import com.superconnectors.telnet.api.TelnetListener;

    import javax.ejb.ActivationConfigProperty;
    import javax.ejb.MessageDriven;
    import java.text.SimpleDateFormat;
    import java.util.Date;
    import java.util.Map;
    import java.util.Properties;
    import java.util.regex.Pattern;

    @MessageDriven(activationConfig = {
            @ActivationConfigProperty(propertyName = "port", propertyValue = "2020"),
            @ActivationConfigProperty(propertyName = "prompt", propertyValue = "pronto>")
    })
    public class MyMdb implements TelnetListener {

        private final SimpleDateFormat dateFormat = new SimpleDateFormat();
        private final Properties properties = new Properties();

        @Override
        public String doDate() {
            return dateFormat.format(new Date(System.currentTimeMillis()));
        }

        @Override
        public String doJoke() {
            return "Where do hamburgers go to dance?  To a meatball.";
        }

        @Override
        public int doAdd(int a, int b) {
            return a + b;
        }

        @Override
        public String doGet(String key) {
            return properties.getProperty(key);
        }

        @Override
        public String doSet(String key, String value) {
            final Object old = properties.setProperty(key, value);
            final StringBuilder sb = new StringBuilder();
            sb.append("set ").append(key).append(" to ").append(value);
            sb.append("\n");
            if (old != null) {
                sb.append("old value: ").append(old);
                sb.append("\n");
            }
            return sb.toString();
        }

        @Override
        public String doList(Pattern pattern) {
            if (pattern == null) pattern = Pattern.compile(".*");
            final StringBuilder sb = new StringBuilder();
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                final String key = entry.getKey().toString();
                if (pattern.matcher(key).matches()) {
                    sb.append(key).append(" = ").append(entry.getValue()).append("\n");
                }
            }
            return sb.toString();
        }
    }


## Running our MDB

With both `telnet.rar` and our application deployed in a complient Java EE server, we should be able to telnet to port 2020 and start using our
MDB.

Here's what the output might look like:

    $ telnet localhost 2020
    Trying ::1...
    Connected to localhost.
    Escape character is '^]'.

    type 'help' for a list of commands
    pronto> help
    add
    date
    exit
    get
    help
    joke
    list
    set

    pronto> help add
    add <int> <int>

    pronto> add 5 6
    11

    pronto> list


    pronto> set greeting ciao
    set greeting to ciao

    pronto> set farwell ciao
    set farwell to ciao

    pronto> list
    farwell = ciao
    greeting = ciao

    pronto> date
    7/26/12 10:39 PM

    pronto> joke
    Where do hamburgers go to dance?  To a meatball.

    pronto> exit
    Connection closed by foreign host.

# Message-Driven Beans Tomorrow

With a couple small tweaks in the specs, we can add great amounts of expressiveness to the existing MDB/Connector relationship.

 - Allow the ResourceAdapter to obtain the bean class through the ActivationSpec
 - Allow the ResourceAdapter to obtain a no-interface view of the bean

This can be done with text and no new API classes or signatures are required.

The contract would be simple.

 - The Connector Provider can request the MDB implementation class (ejb class) via the
ActivationSpec
 - If the ActivationSpec has an 'ejbClass' property the MDB Container would be required to:
     - set a reference to the ejb class of the MDB when creating the ActivationSpec instance
     - return a no-interface view of the MDB from the `MessageEndpointFactory.createEndpoint` method

Of course the "no-interface" view would still implement `MessageEndpoint` and the message listener interface.

## Revamping Telnet Connector

With this simple change we can dramatically improve our Telnet Connector.

First, we no longer need to force a specific set of methods.

    package com.superconnectors.telnet.api;

    import java.util.regex.Pattern;

    public interface TelnetListener {
    }

Instead we'll create an annotation Application Developers can use in conjunction with our `TelnetListener` interface.

    package com.superconnectors.telnet.api;

    import java.lang.annotation.Target;
    import java.lang.annotation.Retention;
    import java.lang.annotation.ElementType;
    import java.lang.annotation.RetentionPolicy;

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Command {
        String name() default "";
        String description() default "";
    }

Second, we'll update our `TelnetActivationSpec` so that it requests the `ejbClass` which will allow us to check for our `@Command` annotation
in any MDBs that may use our Telnet Connector.  Note we can simplify our `validate` method as well.

    package com.superconnectors.telnet.adapter;

    import com.superconnectors.telnet.api.Command;
    import com.superconnectors.telnet.impl.Cmd;

    import javax.resource.ResourceException;
    import javax.resource.spi.ActivationSpec;
    import javax.resource.spi.InvalidPropertyException;
    import javax.resource.spi.ResourceAdapter;
    import java.lang.reflect.Method;
    import java.util.ArrayList;
    import java.util.List;

    public class TelnetActivationSpec implements ActivationSpec {

        private ResourceAdapter resourceAdapter;
        private final List<Cmd> cmds = new ArrayList<Cmd>();
        private int port;
        private String prompt;
        private Class ejbClass;

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

        public Class getEjbClass() {
            return ejbClass;
        }

        public void setEjbClass(Class ejbClass) {
            this.ejbClass = ejbClass;
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

            final Method[] methods = ejbClass.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Command.class)) {
                    final Command command = method.getAnnotation(Command.class);
                    cmds.add(new Cmd(command.name(), method));
                }
            }

            if (cmds.size() == 0) {
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

At this point, we're done making changes to our Telnet Connector.  If you inspected the code you'll remember the `TelnetResourceAdapter` already used
reflection to invoke the `TelnetListener`.  Now that the `TelnetListener` will be castable to the `ejbClass` as well, we have full access to
invoke all the `@Command` methods we find.

We might, however, add some comments in the `TelnetResourceAdapter` code to make it extra clear:

    public void endpointActivation(MessageEndpointFactory messageEndpointFactory, ActivationSpec activationSpec) throws ResourceException {
        final TelnetActivationSpec telnetActivationSpec = (TelnetActivationSpec) activationSpec;

        final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

        // This messageEndpoint instance is also castable to the ejbClass of the MDB
        final TelnetListener telnetListener = (TelnetListener) messageEndpoint;

        final TelnetServer telnetServer = new TelnetServer(telnetActivationSpec, telnetListener);

        try {
            telnetServer.activate();
            activated.put(telnetActivationSpec.getPort(), telnetServer);
        } catch (IOException e) {
            throw new ResourceException(e);
        }
    }


## Revamping the sample MDB

Free to decide what commands to expose, users of the improved Telnet Connector might define an MDB like the following.

    package org.developer.application;

    import com.superconnectors.telnet.api.Command;
    import com.superconnectors.telnet.api.Option;
    import com.superconnectors.telnet.api.TelnetListener;

    import javax.ejb.ActivationConfigProperty;
    import javax.ejb.MessageDriven;
    import java.text.SimpleDateFormat;
    import java.util.Date;
    import java.util.Map;
    import java.util.Properties;
    import java.util.regex.Pattern;

    @MessageDriven(activationConfig = {
            @ActivationConfigProperty(propertyName = "port", propertyValue = "2020"),
            @ActivationConfigProperty(propertyName = "prompt", propertyValue = "pronto>")
    })
    public class MyMdb implements TelnetListener {

        private final Properties properties = new Properties();

        @Command("get")
        public String doGet(@Option("key") String key) {
            return properties.getProperty(key);
        }

        @Command("set")
        public String doSet(@Option("key") String key, @Option("value") String value) {

            final Object old = properties.setProperty(key, value);
            final StringBuilder sb = new StringBuilder();
            sb.append("set ").append(key).append(" to ").append(value);
            sb.append("\n");
            if (old != null) {
                sb.append("old value: ").append(old);
                sb.append("\n");
            }
            return sb.toString();
        }

        @Command("list")
        public String doList(@Option("pattern") Pattern pattern) {

            if (pattern == null) pattern = Pattern.compile(".*");
            final StringBuilder sb = new StringBuilder();
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                final String key = entry.getKey().toString();
                if (pattern.matcher(key).matches()) {
                    sb.append(key).append(" = ").append(entry.getValue()).append("\n");
                }
            }
            return sb.toString();
        }
    }
