/* =====================================================================
 *
 * Copyright (c) 2011 David Blevins.  All rights reserved.
 *
 * =====================================================================
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
public class RunTest {

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
    public void foo(){
        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(1));
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
    }
}
