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
package org.hotswap.agent.plugin.hibernate_jakarta;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.Transient;

import org.hibernate.Version;
import org.hotswap.agent.plugin.hibernate_jakarta.testEntities.TestEntity;
import org.hotswap.agent.plugin.hibernate_jakarta.testEntitiesHotswap.TestEntity2;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Basic test
 *
 * @author Jiri Bubnik
 */
public class HibernateJakartaPluginTest {

    static EntityManagerFactory entityManagerFactory;

    @BeforeClass
    public static void setup() throws Exception {
        String[] version = Version.getVersionString().split("\\.");
        boolean version52OrGreater = Integer.valueOf(version[0]) == 5 && Integer.valueOf(version[1]) >= 2;
        if (version52OrGreater) {
            entityManagerFactory = Persistence.createEntityManagerFactory("TestPU52");
        }  else {
            entityManagerFactory = Persistence.createEntityManagerFactory("TestPU");
        }
    }

    @Test
    public void testSetupOk() throws Exception {
        doInTransaction(new InTransaction() {
            @Override
            public void process(EntityManager entityManager) throws Exception {
                TestEntity entity = (TestEntity) entityManager.createQuery(
                        "from TestEntity where name='Test'").getSingleResult();

                assertNotNull(entity);
                assertEquals("Test", entity.getName());
                assertEquals("descr", entity.getDescription());
            }
        });

        swapClasses();

        doInTransaction(new InTransaction() {
            @Override
            public void process(EntityManager entityManager) throws Exception {
                TestEntity entity = (TestEntity) entityManager.createQuery(
                        "from TestEntity where name='Test'").getSingleResult();

                assertNotNull(entity);
                assertEquals("Test", entity.getName());
                assertNull(entity.getDescription());
            }
        });
    }

    private void swapClasses() throws Exception {

        // no annotation on description field
        assertTrue(TestEntity.class.getDeclaredField("description").getAnnotations().length == 0);

        HibernateRefreshCommands.reloadFlag = true;
        HotSwapper.swapClasses(TestEntity.class, TestEntity2.class.getName());
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !HibernateRefreshCommands.reloadFlag;
            }
        }));

        // @Transient annotation (new instance is loaded)
        assertTrue(TestEntity.class.getDeclaredField("description").getAnnotation(Transient.class) != null);
    }


    private void doInTransaction(InTransaction inTransaction) throws Exception {
        System.out.println(entityManagerFactory);
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();

        try {
            TestEntity simpleEntity = new TestEntity("Test", "descr");
            entityManager.persist(simpleEntity);

            // flush and clear persistence context
            entityManager.flush();
            entityManager.clear();

            inTransaction.process(entityManager);
        } finally {
            entityManager.getTransaction().rollback();
            entityManager.close();
        }
    }

    private static interface InTransaction {
        public void process(EntityManager entityManager) throws Exception;
    }
}
