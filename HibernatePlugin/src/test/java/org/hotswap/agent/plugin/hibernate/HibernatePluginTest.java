package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Transient;

import java.io.IOException;
import java.lang.reflect.Proxy;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

        String className = TestEntity.class.getName();
        CtClass ctClass = ClassPool.getDefault().getAndRename(className + "2", className);
        hotSwapper.reload(className, ctClass.toBytecode());

        // @Transient annotation (new instance is loaded)
        assertTrue(TestEntity.class.getDeclaredField("description").getAnnotation(Transient.class) != null);

        // TODO instead of sleep find a property to wait for
        Thread.sleep(1000);
//        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
//            @Override
//            public boolean result() throws Exception {
//            }
//        }));

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
