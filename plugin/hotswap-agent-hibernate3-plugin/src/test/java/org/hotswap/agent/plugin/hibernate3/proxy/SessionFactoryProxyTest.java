/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.hibernate3.proxy;

import static org.junit.Assert.fail;

import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.classic.Session;
import org.hotswap.agent.plugin.hibernate3.en.Stock;
import org.hotswap.agent.plugin.hibernate3.en.Stock2;
import org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig.ConfiguredBy;
import org.hotswap.agent.plugin.hibernate3.session.proxy.ReInitializable;
import org.hotswap.agent.plugin.hibernate3.session.proxy.SessionFactoryProxy;
import org.junit.Test;

/**
 * @author Jiri Bubnik
 */
public class SessionFactoryProxyTest {

    // @Test
    public void testConfig() {
        try {
            Configuration c = new Configuration();
            c.configure("hibernate.cfg.xml");
            c.buildSessionFactory();

            ReInitializable r = ReInitializable.class.cast(c);

            r.hotSwap();

            SessionFactory s = c.buildSessionFactory();

            Session ss = s.openSession();
            Transaction tt = ss.beginTransaction();
            Stock stock = new Stock("A", "B");
            ss.persist(stock);

            stock = new Stock("C", "D");
            ss.persist(stock);

            stock = new Stock("E", "F");
            ss.persist(stock);

            tt.commit();
            ss.close();

            System.err.println("StockId:" + stock.getStockId());

            r.getOverrideConfig().configuredBy = ConfiguredBy.STRING;
            r.getOverrideConfig().config = "/hibernate2.cfg.xml";

            r.hotSwap();

            try {
                s = c.buildSessionFactory();
                ss = s.openSession();
                tt = ss.beginTransaction();
                stock = new Stock("An", "Bn");
                ss.persist(stock);
                tt.commit();
                ss.close();
                fail("Should not reach!");
            } catch (AssertionError x) {
                throw x;
            } catch (MappingException e) {
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testFactory() {

        try {
            Configuration c = new Configuration();
            c.configure("hibernate.cfg.xml");
            c.buildSessionFactory();

            ReInitializable r = ReInitializable.class.cast(c);

            r.hotSwap();

            SessionFactory s = c.buildSessionFactory();

            org.hibernate.classic.Session ss = s.openSession();
            Transaction tt = ss.beginTransaction();
            Stock stock = new Stock("A", "B");
            ss.persist(stock);

            stock = new Stock("C", "D");
            ss.persist(stock);

            stock = new Stock("E", "F");
            ss.persist(stock);

            tt.commit();
            ss.close();

            System.err.println("StockId:" + stock.getStockId());

            r.getOverrideConfig().configuredBy = ConfiguredBy.STRING;
            r.getOverrideConfig().config = "/hibernate2.cfg.xml";

            // r.hotSwap();

            // Hibernate3Plugin p =
            // PluginManager.getInstance().getPlugin(Hibernate3Plugin.class,
            // this.getClass().getClassLoader());

            // p.refresh(1);

            SessionFactoryProxy.refreshProxiedFactories();

            try {
                ss = s.openSession();
                tt = ss.beginTransaction();
                stock = new Stock("An", "Bn");
                ss.persist(stock);
                tt.commit();
                ss.close();
                fail("Should not reach!");
            } catch (java.lang.AssertionError x) {
                throw x;
            } catch (org.hibernate.MappingException e) {
            }

            ss = s.openSession();
            tt = ss.beginTransaction();
            Stock2 stock2 = new Stock2("Anx", "Bnx");
            ss.persist(stock2);
            tt.commit();
            ss.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
