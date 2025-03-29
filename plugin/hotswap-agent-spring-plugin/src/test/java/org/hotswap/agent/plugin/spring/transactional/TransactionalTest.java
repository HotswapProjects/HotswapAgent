package org.hotswap.agent.plugin.spring.transactional;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextHierarchy({@ContextConfiguration(classes = TransactionalApplication.class)})
public class TransactionalTest {
    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentTransactionalService1 studentTransactionalService;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        swappingRule.setBeanFactory(beanFactory);
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) beanFactory);
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) beanFactory);
    }

    @Test
    @Ignore
    public void transactionalTest() throws Exception {
        System.out.println("TransactionalTest.transactionalTest." + beanFactory);
        //create table
        studentService.createTable();

        //insert data
        String name1 = "name1";
        Assert.assertEquals(1, studentService.insertOriginalData(name1));

        //1.change name1 to name2, but expect rollback to name1 because an IOException was thrown
        String name2 = "name2";
        try {
            studentTransactionalService.changeName(name1, name2, new IOException());
        } catch (Exception ignored) {
        }
        Assert.assertEquals(name1, studentService.findName(name1));

        //swap "rollbackFor = IOException.class" to "rollbackFor = ParseException.class"
        int reloadTimes = 1;
        swappingRule.swapClasses(StudentTransactionalService1.class, StudentTransactionalService2.class, reloadTimes++);

        //2.change name1 to name2 and expect not rollback because rollbackFor=ParseException.class but throw IOException
        try {
            studentTransactionalService.changeName(name1, name2, new IOException());
        } catch (Exception ignored) {
        }
        Assert.assertEquals(name2, studentService.findName(name2));
    }

}
