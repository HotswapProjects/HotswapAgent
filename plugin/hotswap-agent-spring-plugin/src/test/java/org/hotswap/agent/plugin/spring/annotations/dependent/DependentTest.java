package org.hotswap.agent.plugin.spring.annotations.dependent;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.annotations.dependentbak.*;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DepStudentConfiguration.class, DepTeacherConfiguration.class, DepTeacherGroupConfiguration.class})
public class DependentTest {
    @Autowired
    private AbstractApplicationContext applicationContext;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @Test
    public void swapClassTest() throws Exception {
        System.out.println("DependentTest.swapClassTest" + applicationContext.getBeanFactory());
        DepStudent1 depStudent1 = applicationContext.getBean(DepStudent1.class);
        DepStudent2 depStudent2 = applicationContext.getBean(DepStudent2.class);
        DepStudent3 depStudent3 = applicationContext.getBean(DepStudent3.class);
        DepStudent4 depStudent4 = applicationContext.getBean(DepStudent4.class);
        DepTeacher1 depTeacher1 = applicationContext.getBean(DepTeacher1.class);
        DepTeacher2 depTeacher20 = applicationContext.getBean("depTeacher2", DepTeacher2.class);
        DepTeacher2 depTeacher21 = applicationContext.getBean("depTeacher21", DepTeacher2.class);
        DepTeacher3 depTeacher3 = applicationContext.getBean(DepTeacher3.class);
        DepTeacher4 depTeacher4 = applicationContext.getBean(DepTeacher4.class);
        DepTeacher23Mul depTeacherMul = applicationContext.getBean(DepTeacher23Mul.class);
        DepTeacherGroup1 depTeacherGroup1 = applicationContext.getBean(DepTeacherGroup1.class);
        DepTeacherGroup2 depTeacherGroup2 = applicationContext.getBean(DepTeacherGroup2.class);
        DepTeacherGroup3 depTeacherGroup3 = applicationContext.getBean(DepTeacherGroup3.class);
        DepTeacherGroup4 depTeacherGroup4 = applicationContext.getBean(DepTeacherGroup4.class);
        System.out.println("depStudent1: " + depStudent1);
        System.out.println("depStudent2: " + depStudent2);
        System.out.println("depStudent3: " + depStudent3);
        System.out.println("depStudent4: " + depStudent4);

        Assert.assertEquals("student1", depStudent1.getName());
        Assert.assertEquals("student2", depStudent2.getName());
        Assert.assertEquals("student3", depStudent3.getName());
        Assert.assertEquals("student4", depStudent4.getName());
        Assert.assertEquals(depStudent1, depTeacher1.getStudent1());
        Assert.assertEquals(depStudent2, depTeacher20.getStudent2());
        Assert.assertEquals(depStudent2, depTeacher21.getStudent2());
        Assert.assertEquals(depStudent3, depTeacher3.getStudent3());
        Assert.assertEquals(depStudent4, depTeacher4.getStudent4());
        Assert.assertEquals(depStudent2, depTeacherMul.getStudent2());
        Assert.assertEquals(depStudent3, depTeacherMul.getStudent3());

        Assert.assertEquals(depTeacher1, depTeacherGroup1.getDepTeacher1());
        Assert.assertEquals(depTeacher20, depTeacherGroup2.getDepTeacher2());
        Assert.assertEquals(depTeacher3, depTeacherGroup3.getDepTeacher3());
        Assert.assertEquals(depTeacher4, depTeacherGroup4.getDepTeacher4());

        HotSwapper.swapClasses(DepStudent1.class, DepBakStudent1.class.getName());
        HotSwapper.swapClasses(DepStudent2.class, DepBakStudent2.class.getName());
        HotSwapper.swapClasses(DepStudent3.class, DepBakStudent3.class.getName());
        HotSwapper.swapClasses(DepTeacher4.class, DepBakTeacher4.class.getName());
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 1);
            }
        }, 11000));

        DepStudent1 depStudentChange1 = applicationContext.getBean(DepStudent1.class);
        DepStudent2 depStudentChange2 = applicationContext.getBean(DepStudent2.class);
        DepStudent3 depStudentChange3 = applicationContext.getBean(DepStudent3.class);
        DepStudent4 depStudentChange4 = applicationContext.getBean(DepStudent4.class);
        DepTeacher1 depTeacherChange1 = applicationContext.getBean(DepTeacher1.class);
        DepTeacher2 depTeacherChange20 = applicationContext.getBean("depTeacher2", DepTeacher2.class);
        DepTeacher2 depTeacherChange21 = applicationContext.getBean("depTeacher21", DepTeacher2.class);
        DepTeacher3 depTeacherChange3 = applicationContext.getBean(DepTeacher3.class);
        DepTeacher4 depTeacherChange4 = applicationContext.getBean(DepTeacher4.class);
        DepTeacher23Mul depTeacherMulChange = applicationContext.getBean(DepTeacher23Mul.class);
        DepTeacherGroup1 depTeacherGroupChange1 = applicationContext.getBean(DepTeacherGroup1.class);
        DepTeacherGroup2 depTeacherGroupChange2 = applicationContext.getBean(DepTeacherGroup2.class);
        DepTeacherGroup3 depTeacherGroupChange3 = applicationContext.getBean(DepTeacherGroup3.class);
        DepTeacherGroup4 depTeacherGroupChange4 = applicationContext.getBean(DepTeacherGroup4.class);
        System.out.println("depStudent1 changed: " + depStudentChange1);
        System.out.println("depStudent2 changed: " + depStudentChange2);
        System.out.println("depStudent3 changed: " + depStudentChange3);
        System.out.println("depStudent4 changed: " + depStudentChange4);

        // check student
        Assert.assertEquals("student1-changed", depStudentChange1.getName());
        Assert.assertEquals("student2-changed", depStudentChange2.getName());
        Assert.assertEquals("student3-changed", depStudentChange3.getName());
        Assert.assertEquals("student4", depStudentChange4.getName());
        Assert.assertNotEquals(depStudent1, depStudentChange1);
        Assert.assertNotEquals(depStudent2, depStudentChange2);
        Assert.assertNotEquals(depStudent3, depStudentChange3);
        Assert.assertEquals(depStudent4, depStudentChange4);
        // check teacher
        Assert.assertEquals(depStudentChange1, depTeacherChange1.getStudent1());
        Assert.assertEquals(depStudentChange2, depTeacherChange20.getStudent2());
        Assert.assertEquals(depStudentChange2, depTeacherChange21.getStudent2());
        Assert.assertEquals(depStudentChange3, depTeacherChange3.getStudent3());
        Assert.assertEquals(depStudentChange4, depTeacherChange4.getStudent4());
        Assert.assertNotEquals(depTeacher1, depTeacherChange1);
        Assert.assertNotEquals(depTeacher20, depTeacherChange20);
        Assert.assertNotEquals(depTeacher21, depTeacherChange21);
        Assert.assertNotEquals(depTeacher3, depTeacherChange3);
        Assert.assertNotEquals(depTeacher4, depTeacherChange4);
        Assert.assertNotEquals(depTeacherMul, depTeacherMulChange);

        // teacher group
        Assert.assertNotEquals(depTeacher1, depTeacherGroupChange1.getDepTeacher1());
        Assert.assertEquals(depTeacherChange20, depTeacherGroupChange2.getDepTeacher2());
        Assert.assertEquals(depTeacherChange3, depTeacherGroupChange3.getDepTeacher3());
        Assert.assertEquals(depTeacherChange4, depTeacherGroupChange4.getDepTeacher4());
        Assert.assertNotEquals(depTeacherGroup1, depTeacherGroupChange1);
        Assert.assertNotEquals(depTeacherGroup2, depTeacherGroupChange2);
        Assert.assertNotEquals(depTeacherGroup3, depTeacherGroupChange3);
        Assert.assertNotEquals(depTeacherGroup4, depTeacherGroupChange4);


        HotSwapper.swapClasses(DepStudent1.class, DepBakStudentSec1.class.getName());
        HotSwapper.swapClasses(DepStudent2.class, DepBakStudentSec2.class.getName());
        HotSwapper.swapClasses(DepStudent3.class, DepBakStudentSec3.class.getName());
        HotSwapper.swapClasses(DepTeacher4.class, DepBakTeacherSec4.class.getName());
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 2);
            }
        }, 11000));

        DepStudent1 depStudentChangeAgain1 = applicationContext.getBean(DepStudent1.class);
        DepStudent2 depStudentChangeAgain2 = applicationContext.getBean(DepStudent2.class);
        DepStudent3 depStudentChangeAgain3 = applicationContext.getBean(DepStudent3.class);
        DepStudent4 depStudentChangeAgain4 = applicationContext.getBean(DepStudent4.class);
        DepTeacher1 depTeacherChangeAgain1 = applicationContext.getBean(DepTeacher1.class);
        DepTeacher2 depTeacherChangeAgain20 = applicationContext.getBean("depTeacher2", DepTeacher2.class);
        DepTeacher2 depTeacherChangeAgain21 = applicationContext.getBean("depTeacher21", DepTeacher2.class);
        DepTeacher3 depTeacherChangeAgain3 = applicationContext.getBean(DepTeacher3.class);
        DepTeacher4 depTeacherChangeAgain4 = applicationContext.getBean(DepTeacher4.class);
        DepTeacher23Mul depTeacherMulChangeAgain = applicationContext.getBean(DepTeacher23Mul.class);
        DepTeacherGroup1 depTeacherGroupChangeAgain1 = applicationContext.getBean(DepTeacherGroup1.class);
        DepTeacherGroup2 depTeacherGroupChangeAgain2 = applicationContext.getBean(DepTeacherGroup2.class);
        DepTeacherGroup3 depTeacherGroupChangeAgain3 = applicationContext.getBean(DepTeacherGroup3.class);
        DepTeacherGroup4 depTeacherGroupChangeAgain4 = applicationContext.getBean(DepTeacherGroup4.class);
        System.out.println("depStudent1 changed: " + depStudentChangeAgain1);
        System.out.println("depStudent2 changed: " + depStudentChangeAgain2);
        System.out.println("depStudent3 changed: " + depStudentChangeAgain3);
        System.out.println("depStudent4 changed: " + depStudentChangeAgain4);

        // check student
        Assert.assertEquals("student1-changed-again", depStudentChangeAgain1.getName());
        Assert.assertEquals("student2-changed-again", depStudentChangeAgain2.getName());
        Assert.assertEquals("student3-changed-again", depStudentChangeAgain3.getName());
        Assert.assertEquals("student4", depStudentChangeAgain4.getName());
        Assert.assertNotEquals(depStudentChangeAgain1, depStudentChange1);
        Assert.assertNotEquals(depStudentChangeAgain2, depStudentChange2);
        Assert.assertNotEquals(depStudentChangeAgain3, depStudentChange3);
        Assert.assertEquals(depStudentChangeAgain4, depStudentChange4);
        // check teacher
        Assert.assertEquals(depStudentChangeAgain1, depTeacherChangeAgain1.getStudent1());
        Assert.assertEquals(depStudentChangeAgain2, depTeacherChangeAgain20.getStudent2());
        Assert.assertEquals(depStudentChangeAgain2, depTeacherChangeAgain21.getStudent2());
        Assert.assertEquals(depStudentChangeAgain3, depTeacherChangeAgain3.getStudent3());
        Assert.assertEquals(depStudentChangeAgain4, depTeacherChangeAgain4.getStudent4());
        Assert.assertNotEquals(depTeacherChangeAgain1, depTeacherChange1);
        Assert.assertNotEquals(depTeacherChangeAgain20, depTeacherChange20);
        Assert.assertNotEquals(depTeacherChangeAgain21, depTeacherChange21);
        Assert.assertNotEquals(depTeacherChangeAgain3, depTeacherChange3);
        Assert.assertNotEquals(depTeacherChangeAgain4, depTeacherChange4);
        Assert.assertNotEquals(depTeacherMulChangeAgain, depTeacherMulChange);

        // teacher group
        Assert.assertNotEquals(depTeacher1, depTeacherGroupChangeAgain1.getDepTeacher1());
        Assert.assertEquals(depTeacherChangeAgain20, depTeacherGroupChangeAgain2.getDepTeacher2());
        Assert.assertEquals(depTeacherChangeAgain3, depTeacherGroupChangeAgain3.getDepTeacher3());
        Assert.assertEquals(depTeacherChangeAgain4, depTeacherGroupChangeAgain4.getDepTeacher4());
        Assert.assertNotEquals(depTeacherGroupChange1, depTeacherGroupChangeAgain1);
        Assert.assertNotEquals(depTeacherGroupChange2, depTeacherGroupChangeAgain2);
        Assert.assertNotEquals(depTeacherGroupChange3, depTeacherGroupChangeAgain3);
        Assert.assertNotEquals(depTeacherGroupChange4, depTeacherGroupChangeAgain4);
    }

}
