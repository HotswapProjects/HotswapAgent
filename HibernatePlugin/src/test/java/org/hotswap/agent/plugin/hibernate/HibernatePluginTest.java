package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.plugin.hibernate.testEntities.TestEntity;
import org.hotswap.agent.plugin.hibernate.testEntitiesHotswap.TestEntity2;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Transient;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.*;

/**
 * Basic test
 *
 * @author Jiri Bubnik
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

        HibernateRefreshCommands.reloadFlag = true;
        hotSwapper.swapClasses(TestEntity.class, TestEntity2.class.getName());
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
