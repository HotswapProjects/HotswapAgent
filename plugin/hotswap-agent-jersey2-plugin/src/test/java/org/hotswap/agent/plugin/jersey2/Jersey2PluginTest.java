/*
 * Copyright 2013-2026 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.jersey2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.ws.rs.container.ContainerResponseFilter;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.jersey2.app.GreetingResource;
import org.hotswap.agent.plugin.jersey2.app.GuiceBridgeApplication;
import org.hotswap.agent.plugin.jersey2.cdi.CdiApplication;
import org.hotswap.agent.plugin.jersey2.cdi.CdiResource;
import org.hotswap.agent.plugin.jersey2.defaults.DefaultsResource;
import org.hotswap.agent.plugin.jersey2.defaults.EmptyApplication;
import org.hotswap.agent.plugin.jersey2.defaults.ResponseOnlyFilter;
import org.hotswap.agent.plugin.jersey2.exclusive.ExclusiveApplication;
import org.hotswap.agent.plugin.jersey2.exclusive.ExclusiveResource;
import org.hotswap.agent.plugin.jersey2.exclusive.ThrowingShutdownApplication;
import org.hotswap.agent.plugin.jersey2.exclusive.ThrowingShutdownResource;
import org.hotswap.agent.plugin.jersey2.failing.FailingApplication;
import org.hotswap.agent.plugin.jersey2.failing.FailingResource;
import org.hotswap.agent.plugin.jersey2.givingup.GivingUpApplication;
import org.hotswap.agent.plugin.jersey2.givingup.GivingUpResource;
import org.hotswap.agent.plugin.jersey2.instance.InstanceApplication;
import org.hotswap.agent.plugin.jersey2.instance.InstanceConfig;
import org.hotswap.agent.plugin.jersey2.instance.InstanceResource;
import org.hotswap.agent.plugin.jersey2.instance.ReloadCountingServletContainer;
import org.hotswap.agent.plugin.jersey2.instance.StateBinder;
import org.hotswap.agent.plugin.jersey2.manual.BrokenApplication;
import org.hotswap.agent.plugin.jersey2.manual.ExtraResource;
import org.hotswap.agent.plugin.jersey2.manual.ManualApplication;
import org.hotswap.agent.plugin.jersey2.manual.ManualResource;
import org.hotswap.agent.plugin.jersey2.manual.OtherApplication;
import org.hotswap.agent.plugin.jersey2.oneshot.OneShotApplication;
import org.hotswap.agent.plugin.jersey2.oneshot.OneShotFinder;
import org.hotswap.agent.plugin.jersey2.oneshot.OneShotResource;
import org.hotswap.agent.plugin.jersey2.packaged.PackagedApplication;
import org.hotswap.agent.plugin.jersey2.packaged.PackagedResource;
import org.hotswap.agent.plugin.jersey2.scanned.ScannedResource;
import org.hotswap.agent.plugin.jersey2.swap.AmbiguousGreetingResource;
import org.hotswap.agent.plugin.jersey2.swap.CdiResource2;
import org.hotswap.agent.plugin.jersey2.swap.DefaultsResource2;
import org.hotswap.agent.plugin.jersey2.swap.ExclusiveResource2;
import org.hotswap.agent.plugin.jersey2.swap.FailingResource2;
import org.hotswap.agent.plugin.jersey2.swap.GivingUpResource2;
import org.hotswap.agent.plugin.jersey2.swap.GreetingResource2;
import org.hotswap.agent.plugin.jersey2.swap.InstanceResource2;
import org.hotswap.agent.plugin.jersey2.swap.ManualResource2;
import org.hotswap.agent.plugin.jersey2.swap.OneShotResource2;
import org.hotswap.agent.plugin.jersey2.swap.PackagedResource2;
import org.hotswap.agent.plugin.jersey2.swap.ScannedResource2;
import org.hotswap.agent.plugin.jersey2.swap.ThrowingShutdownResource2;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Swaps a resource class of a running Jersey servlet and checks that the container is reloaded on the next request:
 * a resource method added by the swap is found only after the reload.
 */
public class Jersey2PluginTest {

    private static final long RELOAD_TIMEOUT_MS = 10000;

    private static Server server;
    private static ServletContainer manualContainer;
    private static ServletContainer otherContainer;
    private static ServletHolder givingUpHolder;
    private static ServletHolder applicationHolder;
    private static ServletHolder throwingHolder;
    private static int port;

    @BeforeClass
    public static void startServer() throws Exception {
        server = new Server(0);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");

        // configured with an application class
        ServletHolder application = new ServletHolder(ServletContainer.class);
        applicationHolder = application;
        application.setInitParameter("javax.ws.rs.Application", GuiceBridgeApplication.class.getName());
        application.setInitParameter("greeting.property", "set");
        application.setInitOrder(1);
        context.addServlet(application, "/app/*");

        // configured with the packages to scan
        ServletHolder scanned = new ServletHolder(ServletContainer.class);
        scanned.setInitParameter("jersey.config.server.provider.packages", ScannedResource.class.getPackage().getName());
        scanned.setInitOrder(1);
        context.addServlet(scanned, "/scanned/*");

        // as the servlet container initializer creates it for an application without classes named in web.xml, with
        // the scanned classes as default classes
        // and with a binder and a property registered on the config
        ServletHolder defaults = new ServletHolder(new ServletContainer(ResourceConfig.forApplicationClass(
                EmptyApplication.class, Collections.<Class<?>>singleton(DefaultsResource.class))
                .register(new StateBinder("registered"))
                .register(ResponseOnlyFilter.class, ContainerResponseFilter.class)
                .property(DefaultsResource.PROPERTY, "set")));
        defaults.setInitParameter("javax.ws.rs.Application", EmptyApplication.class.getName());
        defaults.setInitOrder(1);
        context.addServlet(defaults, "/defaults/*");

        // with a config instance, as Spring Boot creates it, also with the application class as init parameter
        // by a subclass overriding reload
        ServletHolder instance = new ServletHolder(
                new ReloadCountingServletContainer(new InstanceConfig("from instance")));
        instance.setInitParameter("javax.ws.rs.Application", InstanceConfig.class.getName());
        instance.setInitOrder(1);
        context.addServlet(instance, "/instance/*");

        // with an application instance wrapped by ResourceConfig.forApplication()
        ServletHolder applicationInstance = new ServletHolder(new ServletContainer(
                ResourceConfig.forApplication(new InstanceApplication("from instance"))));
        applicationInstance.setInitOrder(1);
        context.addServlet(applicationInstance, "/appinstance/*");

        // with an application that cannot be created again
        ServletHolder failing = new ServletHolder(ServletContainer.class);
        failing.setInitParameter("javax.ws.rs.Application", FailingApplication.class.getName());
        failing.setInitOrder(1);
        context.addServlet(failing, "/failing/*");

        // reloaded by the application itself with another config
        manualContainer = new ServletContainer(ResourceConfig.forApplicationClass(ManualApplication.class));
        ServletHolder manual = new ServletHolder(manualContainer);
        manual.setInitOrder(1);
        context.addServlet(manual, "/manual/*");

        // with an application provided by CDI
        ServletHolder cdi = new ServletHolder(ServletContainer.class);
        cdi.setInitParameter("javax.ws.rs.Application", CdiApplication.class.getName());
        cdi.setInitOrder(1);
        context.addServlet(cdi, "/cdi/*");

        // with an application that is not a ResourceConfig and packages to scan registered on its config
        ServletHolder packaged = new ServletHolder(new ServletContainer(ResourceConfig
                .forApplicationClass(PackagedApplication.class)
                .packages(PackagedResource.class.getPackage().getName())));
        packaged.setInitOrder(1);
        context.addServlet(packaged, "/packaged/*");

        // with an application that cannot be created again at all
        ServletHolder givingUp = new ServletHolder(ServletContainer.class);
        givingUpHolder = givingUp;
        givingUp.setInitParameter("javax.ws.rs.Application", GivingUpApplication.class.getName());
        givingUp.setInitOrder(1);
        context.addServlet(givingUp, "/givingup/*");

        // with an application holding a resource until it is shut down
        ServletHolder exclusive = new ServletHolder(ServletContainer.class);
        exclusive.setInitParameter("javax.ws.rs.Application", ExclusiveApplication.class.getName());
        exclusive.setInitOrder(1);
        context.addServlet(exclusive, "/exclusive/*");

        // with a resource finder that cannot be reset
        ServletHolder oneShot = new ServletHolder(new ServletContainer(ResourceConfig
                .forApplicationClass(OneShotApplication.class)
                .registerFinder(new OneShotFinder())));
        oneShot.setInitOrder(1);
        context.addServlet(oneShot, "/oneshot/*");

        // reloaded by the application itself with a config created from another application class
        otherContainer = new ServletContainer(new ResourceConfig());
        ServletHolder other = new ServletHolder(otherContainer);
        other.setInitOrder(1);
        context.addServlet(other, "/other/*");

        // with an application that throws when it is shut down
        ServletHolder throwing = new ServletHolder(ServletContainer.class);
        throwingHolder = throwing;
        throwing.setInitParameter("javax.ws.rs.Application", ThrowingShutdownApplication.class.getName());
        throwing.setInitOrder(1);
        context.addServlet(throwing, "/throwing/*");

        server.setHandler(context);
        server.start();
        port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        server.stop();
    }

    /**
     * The application is created again, so the bindings it adds to the new service locator in its constructor (here
     * the HK2 Guice bridge) are there after the reload, see #191. The init parameters are still properties of the
     * application, and the registrations of the servlet container providers are those of the new web component.
     */
    @Test
    public void applicationIsCreatedAgainOnReload() throws Exception {
        assertEquals("hello from guice v1", get("/app/greeting"));
        assertEquals("HTTP 404", get("/app/greeting/added"));
        String provider = get("/app/greeting/provider");
        assertTrue(provider, provider.startsWith("provider "));

        HotSwapper.swapClasses(GreetingResource.class, GreetingResource2.class.getName());
        assertEquals("hello from guice set added", waitForResponse("/app/greeting/added", "hello from guice set added"));
        assertEquals("hello from guice v2", get("/app/greeting"));
        // the binder the servlet container provider registers for the new web component, not the old one
        assertNotEquals(provider, get("/app/greeting/provider"));

        HotSwapper.swapClasses(GreetingResource.class, GreetingResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/app/greeting/added", "HTTP 404"));
        assertEquals("hello from guice v1", get("/app/greeting"));
    }

    /**
     * A resource Jersey cannot create an application with, from a copy of the config neither, does not count as a
     * failed attempt to create the application again, so it is created again after the resource is fixed.
     */
    @Test
    public void invalidResourceDoesNotStopCreatingApplicationAgain() throws Exception {
        assertEquals("hello from guice v1", get("/app/greeting"));
        Object container = applicationHolder.getServlet();
        for (int i = 0; i < Jersey2Plugin.MAX_FAILED_RECREATIONS + 1; i++) {
            HotSwapper.swapClasses(GreetingResource.class, AmbiguousGreetingResource.class.getName());
            reloadOnNextRequest(container, "/app/greeting");
        }
        HotSwapper.swapClasses(GreetingResource.class, GreetingResource.class.getName());
        reloadOnNextRequest(container, "/app/greeting");
        // with the bindings of the Guice bridge, which a copy of the config does not have
        assertEquals("hello from guice v1", get("/app/greeting"));
    }

    @Test
    public void scannedResourceIsReloaded() throws Exception {
        assertEquals("scanned v1", get("/scanned/scanned"));
        assertEquals("HTTP 404", get("/scanned/scanned/added"));

        HotSwapper.swapClasses(ScannedResource.class, ScannedResource2.class.getName());
        assertEquals("scanned added", waitForResponse("/scanned/scanned/added", "scanned added"));
        assertEquals("scanned v2", get("/scanned/scanned"));

        HotSwapper.swapClasses(ScannedResource.class, ScannedResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/scanned/scanned/added", "HTTP 404"));
        assertEquals("scanned v1", get("/scanned/scanned"));
    }

    /**
     * The default classes of the application and what is registered on its config are kept, so its resources are
     * found after the reload, with the same bindings, properties and contracts.
     */
    @Test
    public void defaultClassesAreKeptOnReload() throws Exception {
        assertEquals("defaults registered set v1", get("/defaults/defaults"));

        HotSwapper.swapClasses(DefaultsResource.class, DefaultsResource2.class.getName());
        assertEquals("defaults registered set added", waitForResponse("/defaults/defaults/added", "defaults registered set added"));
        assertEquals("defaults registered set v2", get("/defaults/defaults"));

        HotSwapper.swapClasses(DefaultsResource.class, DefaultsResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/defaults/defaults/added", "HTTP 404"));
        assertEquals("defaults registered set v1", get("/defaults/defaults"));
    }

    /**
     * A config or an application given to the container as an instance is not created again from its class, and the
     * container is reloaded by reload() as before, which a subclass may override.
     */
    @Test
    public void configInstanceIsKeptOnReload() throws Exception {
        assertEquals("from instance v1", get("/instance/instance"));
        int reloads = ReloadCountingServletContainer.RELOADS.get();

        assertEquals("from instance v1", get("/appinstance/instance"));

        HotSwapper.swapClasses(InstanceResource.class, InstanceResource2.class.getName());
        assertEquals("from instance added", waitForResponse("/instance/instance/added", "from instance added"));
        assertEquals("from instance v2", get("/instance/instance"));
        // reloaded by the subclass overriding reload, as before
        assertTrue(ReloadCountingServletContainer.RELOADS.get() > reloads);
        assertEquals("from instance added", waitForResponse("/appinstance/instance/added", "from instance added"));
        assertEquals("from instance v2", get("/appinstance/instance"));

        HotSwapper.swapClasses(InstanceResource.class, InstanceResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/instance/instance/added", "HTTP 404"));
        assertEquals("from instance v1", get("/instance/instance"));
        assertEquals("HTTP 404", waitForResponse("/appinstance/instance/added", "HTTP 404"));
        assertEquals("from instance v1", get("/appinstance/instance"));
    }

    /**
     * If the application cannot be created again, the running one is kept and the container is reloaded with a copy
     * of its current config. The next reload creates the application again.
     */
    @Test
    public void applicationIsCreatedAgainAfterFailedReload() throws Exception {
        String state = get("/failing/failing").replace(" v1", "");
        assertTrue(state, state.startsWith("failing instance "));

        FailingApplication.fail = true;
        try {
            HotSwapper.swapClasses(FailingResource.class, FailingResource2.class.getName());
            assertEquals(state + " added", waitForResponse("/failing/failing/added", state + " added"));
            assertEquals(state + " v2", get("/failing/failing"));
        } finally {
            FailingApplication.fail = false;
        }

        HotSwapper.swapClasses(FailingResource.class, FailingResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/failing/failing/added", "HTTP 404"));
        String newState = get("/failing/failing").replace(" v1", "");
        assertTrue(newState, newState.startsWith("failing instance "));
        assertNotEquals(state, newState);
    }

    /**
     * After the application reloaded the container with another config, the container is reloaded with a copy of it,
     * so what the application registered then is kept.
     */
    @Test
    public void configOfReloadByApplicationIsKept() throws Exception {
        assertEquals("HTTP 404", get("/manual/extra"));
        manualContainer.reload(new ResourceConfig(manualContainer.getConfiguration()).register(ExtraResource.class));
        assertEquals("extra", get("/manual/extra"));

        HotSwapper.swapClasses(ManualResource.class, ManualResource2.class.getName());
        assertEquals("manual added", waitForResponse("/manual/manual/added", "manual added"));
        assertEquals("extra", get("/manual/extra"));

        HotSwapper.swapClasses(ManualResource.class, ManualResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/manual/manual/added", "HTTP 404"));
        assertEquals("extra", get("/manual/extra"));
    }

    /**
     * After the application reloaded the container with a config created from another application class, a change of
     * that class creates the application again. A failed reload by the application does not change that.
     */
    @Test
    public void applicationOfReloadByApplicationIsCreatedAgain() throws Exception {
        otherContainer.reload(ResourceConfig.forApplicationClass(OtherApplication.class));
        String state = get("/other/other");
        assertTrue(state, state.startsWith("other instance "));

        HotSwapper.swapClasses(OtherApplication.class, OtherApplication.class.getName());
        String newState = waitForChange("/other/other", state);
        assertTrue(newState, newState.startsWith("other instance "));
        assertNotEquals(state, newState);

        // a failed reload by the application does not replace the template
        try {
            otherContainer.reload(ResourceConfig.forApplicationClass(BrokenApplication.class));
        } catch (RuntimeException e) {
            // Jersey logs a ServletException, other exceptions are thrown
        }
        HotSwapper.swapClasses(OtherApplication.class, OtherApplication.class.getName());
        // the failed reload shut down the application, the requests fail until the next reload
        long end = System.currentTimeMillis() + RELOAD_TIMEOUT_MS;
        String recreatedState = get("/other/other");
        while ((!recreatedState.startsWith("other instance ") || recreatedState.equals(newState))
                && System.currentTimeMillis() < end) {
            Thread.sleep(50);
            recreatedState = get("/other/other");
        }
        assertTrue(recreatedState, recreatedState.startsWith("other instance "));
        assertNotEquals(newState, recreatedState);
    }

    /**
     * An application provided by CDI is not created again, the container is reloaded with a copy of its config.
     */
    @Test
    public void applicationProvidedByCdiIsNotCreatedAgain() throws Exception {
        String state = get("/cdi/cdi").replace(" v1", "");
        assertTrue(state, state.startsWith("cdi instance "));

        HotSwapper.swapClasses(CdiResource.class, CdiResource2.class.getName());
        assertEquals(state + " added", waitForResponse("/cdi/cdi/added", state + " added"));

        HotSwapper.swapClasses(CdiResource.class, CdiResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/cdi/cdi/added", "HTTP 404"));
        assertEquals(state + " v1", get("/cdi/cdi"));
    }

    /**
     * The resource finders registered on the config of the application find its resources after the reload too.
     */
    @Test
    public void resourceFindersAreReadAgainOnReload() throws Exception {
        assertEquals("packaged v1", get("/packaged/packaged"));
        int instances = PackagedApplication.INSTANCES.get();

        HotSwapper.swapClasses(PackagedResource.class, PackagedResource2.class.getName());
        assertEquals("packaged added", waitForResponse("/packaged/packaged/added", "packaged added"));
        assertEquals("packaged v2", get("/packaged/packaged"));
        // created again
        assertTrue(PackagedApplication.INSTANCES.get() > instances);

        HotSwapper.swapClasses(PackagedResource.class, PackagedResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/packaged/packaged/added", "HTTP 404"));
        assertEquals("packaged v1", get("/packaged/packaged"));
    }

    /**
     * A resource finder that cannot be reset would not find its resources again, so the application is not created
     * again and the container is reloaded with a copy of its config, which has them registered.
     */
    @Test
    public void applicationWithResourceFinderThatCannotBeResetIsNotCreatedAgain() throws Exception {
        assertEquals("oneshot v1", get("/oneshot/oneshot"));
        int instances = OneShotApplication.INSTANCES.get();

        HotSwapper.swapClasses(OneShotResource.class, OneShotResource2.class.getName());
        assertEquals("oneshot added", waitForResponse("/oneshot/oneshot/added", "oneshot added"));
        assertEquals("oneshot v2", get("/oneshot/oneshot"));

        HotSwapper.swapClasses(OneShotResource.class, OneShotResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/oneshot/oneshot/added", "HTTP 404"));
        assertEquals("oneshot v1", get("/oneshot/oneshot"));
        assertEquals(instances, OneShotApplication.INSTANCES.get());
    }

    /**
     * Each failed attempt to create the application again leaves a service locator behind, so the attempts stop
     * after a few in a row, and the container is reloaded with a copy of its config. When the application class is
     * redefined, it is created again.
     */
    @Test
    public void attemptsToCreateApplicationAgainStopUntilApplicationIsRedefined() throws Exception {
        String state = get("/givingup/givingup").replace(" v1", "");
        assertTrue(state, state.startsWith("givingup instance "));

        GivingUpApplication.fail = true;
        try {
            for (int i = 0; i < Jersey2Plugin.MAX_FAILED_RECREATIONS + 2; i++) {
                if (i % 2 == 0) {
                    HotSwapper.swapClasses(GivingUpResource.class, GivingUpResource2.class.getName());
                    assertEquals(state + " added", waitForResponse("/givingup/givingup/added", state + " added"));
                } else {
                    HotSwapper.swapClasses(GivingUpResource.class, GivingUpResource.class.getName());
                    assertEquals("HTTP 404", waitForResponse("/givingup/givingup/added", "HTTP 404"));
                }
            }
        } finally {
            GivingUpApplication.fail = false;
        }
        assertEquals(Jersey2Plugin.MAX_FAILED_RECREATIONS, GivingUpApplication.FAILED_ATTEMPTS.get());

        // not created again anymore
        HotSwapper.swapClasses(GivingUpResource.class, GivingUpResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/givingup/givingup/added", "HTTP 404"));
        assertEquals(state + " v1", get("/givingup/givingup"));

        // also after the application reloaded the container with its current config
        ((ServletContainer) givingUpHolder.getServlet()).reload();
        assertEquals(state + " v1", get("/givingup/givingup"));

        // a fixed application class is created again, also if other classes are redefined after it
        HotSwapper.swapClasses(GivingUpApplication.class, GivingUpApplication.class.getName());
        HotSwapper.swapClasses(GivingUpResource.class, GivingUpResource.class.getName());
        String newState = waitForChange("/givingup/givingup", state + " v1").replace(" v1", "");
        assertTrue(newState, newState.startsWith("givingup instance "));
        assertNotEquals(state, newState);
        assertEquals(Jersey2Plugin.MAX_FAILED_RECREATIONS, GivingUpApplication.FAILED_ATTEMPTS.get());
    }

    /**
     * An application that throws when it is shut down is reloaded anyway, also when the application reloads the
     * container itself.
     */
    @Test
    public void applicationThrowingOnShutdownIsReloaded() throws Exception {
        assertEquals("throwing v1", get("/throwing/throwing"));

        HotSwapper.swapClasses(ThrowingShutdownResource.class, ThrowingShutdownResource2.class.getName());
        assertEquals("throwing added", waitForResponse("/throwing/throwing/added", "throwing added"));
        assertEquals("throwing v2", get("/throwing/throwing"));

        HotSwapper.swapClasses(ThrowingShutdownResource.class, ThrowingShutdownResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/throwing/throwing/added", "HTTP 404"));
        assertEquals("throwing v1", get("/throwing/throwing"));

        // also by the application itself
        ((ServletContainer) throwingHolder.getServlet()).reload();
        assertEquals("throwing v1", get("/throwing/throwing"));
    }

    /**
     * The old application is shut down before the new one is created, so a resource only one instance can hold is
     * free for the new one.
     */
    @Test
    public void oldApplicationIsShutDownBeforeNewOneIsCreated() throws Exception {
        String state = get("/exclusive/exclusive").replace(" v1", "");
        assertTrue(state, state.startsWith("exclusive instance "));

        HotSwapper.swapClasses(ExclusiveResource.class, ExclusiveResource2.class.getName());
        String added = waitForResponse("/exclusive/exclusive/added", "HTTP 404", true);
        assertTrue(added, added.startsWith("exclusive instance ") && added.endsWith(" added"));
        assertNotEquals(state + " added", added);

        HotSwapper.swapClasses(ExclusiveResource.class, ExclusiveResource.class.getName());
        assertEquals("HTTP 404", waitForResponse("/exclusive/exclusive/added", "HTTP 404"));
        // also when other tests reloaded the container
        assertEquals(0, ExclusiveApplication.FAILED_ATTEMPTS.get());
    }

    /**
     * The reload is done by the request after the class is redefined, so a few requests may get the old response.
     */
    private static String waitForResponse(String path, String expected) throws Exception {
        return waitForResponse(path, expected, false);
    }

    private static String waitForChange(String path, String old) throws Exception {
        return waitForResponse(path, old, true);
    }

    // until the response is (or, if changed, is not) the given one
    private static String waitForResponse(String path, String response, boolean changed) throws Exception {
        long end = System.currentTimeMillis() + RELOAD_TIMEOUT_MS;
        String current = get(path);
        while (response.equals(current) == changed && System.currentTimeMillis() < end) {
            Thread.sleep(50);
            current = get(path);
        }
        return current;
    }

    // waits until the container is marked for reload, then reloads it by a request
    private static void reloadOnNextRequest(Object container, String path) throws Exception {
        Field pending = container.getClass().getDeclaredField("__haReloadPending");
        pending.setAccessible(true);
        long end = System.currentTimeMillis() + RELOAD_TIMEOUT_MS;
        while (!pending.getBoolean(container) && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        assertTrue("not marked for reload", pending.getBoolean(container));
        get(path);
    }

    private static String get(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        try {
            int status = connection.getResponseCode();
            InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (in != null) {
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    out.write(buffer, 0, n);
                }
                in.close();
            }
            String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return status == 200 ? body : "HTTP " + status;
        } finally {
            connection.disconnect();
        }
    }
}
