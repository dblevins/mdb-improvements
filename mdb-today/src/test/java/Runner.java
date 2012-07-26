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
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * @version $Revision$ $Date$
 */
@RunWith(Arquillian.class)
public class Runner {

    @Deployment(testable = false)
    public static EnterpriseArchive createDeployment() {


        final JavaArchive rarLib = ShrinkWrap.create(JavaArchive.class, "lib.jar");
        rarLib.addPackages(true, "com.superconnectors");
        System.out.println(rarLib.toString(true));
        System.out.println();

        final EnterpriseArchive rar = ShrinkWrap.create(EnterpriseArchive.class, "test.rar");
        rar.addAsModule(rarLib);
        rar.addAsManifestResource(new ClassLoaderAsset("com/superconnectors/telnet/adapter/ra.xml"), "ra.xml");
        System.out.println(rar.toString(true));
        System.out.println();

        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "test.jar");
        jar.addPackages(true, "org.developer");
        System.out.println(jar.toString(true));
        System.out.println();

        // Make the EAR
        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, "test.ear")
                .addAsModule(rar).addAsModule(jar);
        System.out.println(ear.toString(true));
        System.out.println();

        return ear;
    }

    @Test
    public void run(){
        try {
            Thread.sleep(TimeUnit.HOURS.toMillis(1));
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
    }
}
