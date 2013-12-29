package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import java.io.IOException;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by bubnik on 11.10.13.
 */
public class HibernatePluginTest {

    static EntityManagerFactory entityManagerFactory;
    static HotSwapper hotSwapper;

    @BeforeClass
    public static void setup() throws Exception {
        entityManagerFactory = Persistence.createEntityManagerFactory("TestPU");
        hotSwapper = new HotSwapper("8000");
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

                swapClasses();
                entityManager.clear();

                entity = (TestEntity) entityManager.createQuery(
                        "from TestEntity where name='Test'").getSingleResult();

                assertNotNull(entity);
                assertEquals("Test", entity.getName());
                assertNull(entity.getDescription());
            }
        });
    }

    // TODO instead of sleep provide pluginRegistry.waitForInitialization()
    private void swapClasses() throws Exception {
        Thread.sleep(500);
        String className = TestEntity.class.getName();
        CtClass ctClass = ClassPool.getDefault().getAndRename(className + "2", className);
        hotSwapper.reload(className, ctClass.toBytecode());
        Thread.sleep(500);

    }


    private void doInTransaction(InTransaction inTransaction) throws Exception {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();

        try {
            TestEntity simpleEntity = new TestEntity("Test", "descr");
            entityManager.persist(simpleEntity);

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
