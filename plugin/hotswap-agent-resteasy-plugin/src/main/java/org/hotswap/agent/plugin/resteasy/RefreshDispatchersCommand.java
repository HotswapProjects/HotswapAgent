/*
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.hotswap.agent.plugin.resteasy;

import java.util.Enumeration;
import java.util.Set;

import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.jboss.resteasy.plugins.server.servlet.FilterDispatcher;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

/**
 * RefreshDispatchersCommand
 *
 *  @author Vladimir Dvorak - HotswapAgent implementation,
 *  @author Stuart Douglas - original concept in FakeReplace
 */
public class RefreshDispatchersCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RefreshDispatchersCommand.class);

    ClassLoader classLoader;

    Set<Object> registeredDispatchers;

    public void setupCmd(ClassLoader classLoader, Set<Object> registeredDispatchers) {
        this.classLoader = classLoader;
        this.registeredDispatchers = registeredDispatchers;
    }

    @Override
    public void executeCommand() {
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            for (final Object o : registeredDispatchers) {
                if (o.getClass().getName().equals("org.jboss.resteasy.plugins.server.servlet.FilterDispatcher")) {
                    reinitializeFilterDispatcher((FilterDispatcher) o);
                }
                if (o.getClass().getName().equals("org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher")) {
                    reinitializeServletDispatcher((HttpServletDispatcher) o);
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    private void reinitializeFilterDispatcher(FilterDispatcher filter) {
        try {
            final FilterConfig config = (FilterConfig) ReflectionHelper.get(filter, ResteasyPlugin.FIELD_NAME);
            final Set<String> doNoyClear = (Set<String>) ReflectionHelper.get(filter, ResteasyPlugin.PARAMETER_FIELD_NAME);
            clearContext(config.getServletContext(), doNoyClear);
            filter.destroy();
            filter.init(config);
        } catch (Exception e) {
            LOGGER.warning("Could not reinitialize RESTeasy", e);
        }
    }

    private void reinitializeServletDispatcher(HttpServletDispatcher servlet) {
        try {
            final ServletConfig config = (ServletConfig) ReflectionHelper.get(servlet, ResteasyPlugin.FIELD_NAME);
            final Set<String> doNoyClear = (Set<String>) ReflectionHelper.get(servlet, ResteasyPlugin.PARAMETER_FIELD_NAME);
            clearContext(config.getServletContext(), doNoyClear);
            servlet.destroy();
            servlet.init(config);
        } catch (Exception e) {
            LOGGER.warning("Could not reinitialize RESTeasy", e);
        }
    }

    /**
     * Clear any resteasy stuff from the context
     *
     * @param servletContext
     */
    private void clearContext(final ServletContext servletContext, final Set<String> doNotClear) {
        final Enumeration names = servletContext.getAttributeNames();
        while (names.hasMoreElements()) {
            final String name = names.nextElement().toString();
            if (name.startsWith("org.jboss.resteasy") && !doNotClear.contains(name)) {
                servletContext.removeAttribute(name);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RefreshDispatchersCommand that = (RefreshDispatchersCommand) o;

        if (!classLoader.equals(that.classLoader)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = classLoader.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "BeanClassRefreshCommand{" +
                "classLoader=" + classLoader +
                '}';
    }

}