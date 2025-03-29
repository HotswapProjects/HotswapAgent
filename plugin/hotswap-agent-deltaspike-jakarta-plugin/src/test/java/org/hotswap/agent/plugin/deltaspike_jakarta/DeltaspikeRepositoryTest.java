package org.hotswap.agent.plugin.deltaspike_jakarta;

import java.util.Optional;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import org.hotswap.agent.plugin.deltaspike_jakarta.command.RepositoryRefreshAgent;
import org.hotswap.agent.plugin.deltaspike_jakarta.testEntities.TestEntity;
import org.hotswap.agent.plugin.deltaspike_jakarta.testRepositories.TestRepository;
import org.hotswap.agent.plugin.deltaspike_jakarta.testRepositoriesHotswap.TestRepository1;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DeltaspikeRepositoryTest extends HAAbstractUnitTest {

    TestEntity simpleEntity;

    @SuppressWarnings("unchecked")
    public <T> T getBeanInstance(Class<T> beanClass) {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<T> bean = (Bean<T>) beanManager.resolve(beanManager.getBeans(beanClass));
        T result = beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
        return result;
    }

    @Before
    public void init() {
        startContainer();
        simpleEntity = new TestEntity("Test", "descr");
        TestRepository testRepository = getBeanInstance(TestRepository.class);
        testRepository.saveAndFlush(simpleEntity);
    }

    @Test
    public void testRepository() throws Exception {
        TestRepository testRepository = getBeanInstance(TestRepository.class);
        TestEntity entity = testRepository.findOptionalBy(simpleEntity.getId()).get();
        assertNotNull(entity);
        swapClasses(TestRepository.class, TestRepository1.class.getName());
        TestEntity entity2 = ((Optional<TestEntity>)testRepository.getClass().getMethod("findOptionalByName", new Class[] { String.class })
            .invoke(testRepository, new Object[] { "Test" })).get();
        assertNotNull(entity);
        assertEquals(entity2.getId(), entity.getId());
    }

    private void swapClasses(Class original, String swap) throws Exception {
        RepositoryRefreshAgent.reloadFlag = false;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return RepositoryRefreshAgent.reloadFlag;
            }
        }, (DeltaSpikeJakartaPlugin.WAIT_ON_REDEFINE * 3) )); // Repository is regenerated within 2*DeltaSpikePlugin.WAIT_ON_REDEFINE

        // TODO do not know why sleep is needed, maybe a separate thread in owb refresh?
        Thread.sleep(100);
    }

}
