package org.hotswap.agent.plugin.spring.annotations;

import org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1.*;
import org.hotswap.agent.plugin.spring.annotations.placeholder.annotation2.Annotation2Student1;
import org.hotswap.agent.plugin.spring.annotations.placeholder.annotation3.Annotation3Student1;
import org.hotswap.agent.plugin.spring.annotations.placeholder.annotation4.Annotation4Component;
import org.hotswap.agent.plugin.spring.annotations.placeholder.annotation4.Annotation4Student;
import org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {Annotation1Configuration.class, Annotation4Component.class, Annotation2Student1.class, Annotation3Student1.class})
public class ConfigurationTest {
    @Autowired
    private ApplicationContext applicationContext;

    private final Resource propertyFile = new ClassPathResource("configuration-item.properties");
    private final Resource changedPropertyFile = new ClassPathResource("configuration-item-changed.properties");

    @Test
    public void swapPropertyTest() throws Exception {
        Assert.assertEquals("teacher-name", applicationContext.getBean(Teacher1.class).getName());
        Assert.assertEquals("teacher2-name", applicationContext.getBean("teacher2", Teacher2.class).getName());
        Assert.assertEquals("teacher2-name", applicationContext.getBean("teacher22", Teacher2.class).getName());
        Assert.assertEquals("student-name", applicationContext.getBean(Student1.class).getName());
        Assert.assertEquals("student2-name", applicationContext.getBean(Student2.class).getName());
        Assert.assertEquals("student3-name", applicationContext.getBean(Student3.class).getName());
        Assert.assertEquals("student4-name", applicationContext.getBean(Student4.class).getName());

        Assert.assertEquals("annotation2-student-name", applicationContext.getBean(Annotation2Student1.class).getName());
        Assert.assertEquals("annotation3-student-name", applicationContext.getBean(Annotation3Student1.class).getName());
        Assert.assertEquals("annotation4-student-name", applicationContext.getBean(Annotation4Student.class).getName());
        Assert.assertEquals("annotation4-component-name", applicationContext.getBean(Annotation4Component.class).getName());


        XmlBeanDefinitionScannerAgent.reloadFlag = true;
        byte[] content = Files.readAllBytes(propertyFile.getFile().toPath());
        try {
            modifyPropertyFile();
            Thread.sleep(10000);

            System.out.println(applicationContext.getBean(Teacher1.class).getName());
            System.out.println(applicationContext.getBean("teacher2", Teacher2.class).getName());
            System.out.println(applicationContext.getBean("teacher22", Teacher2.class).getName());
            System.out.println(applicationContext.getBean(Student1.class).getName());
            System.out.println(applicationContext.getBean(Student2.class).getName());
            System.out.println(applicationContext.getBean(Student3.class).getName());
            System.out.println(applicationContext.getBean(Student4.class).getName());

            System.out.println(applicationContext.getBean(Annotation2Student1.class).getName());
            System.out.println(applicationContext.getBean(Annotation3Student1.class).getName());
            System.out.println(applicationContext.getBean(Annotation4Student.class).getName());
            System.out.println(applicationContext.getBean(Annotation4Student.class).getName());

            assertEquals("teacher-name-changed", applicationContext.getBean(Teacher1.class).getName());
            assertEquals("teacher2-name-changed", applicationContext.getBean("teacher2", Teacher2.class).getName());
            assertEquals("teacher2-name-changed", applicationContext.getBean("teacher2", Teacher2.class).getName());
            assertEquals("student-name-changed", applicationContext.getBean(Student1.class).getName());
            assertEquals("student2-name-changed", applicationContext.getBean(Student2.class).getName());
            assertEquals("${student3.name}", applicationContext.getBean(Student3.class).getName());
            Assert.assertEquals("student4-name-changed", applicationContext.getBean(Student4.class).getName());
            Assert.assertEquals("annotation2-student-name-changed", applicationContext.getBean(Annotation2Student1.class).getName());
            Assert.assertEquals("annotation3-student-name-changed", applicationContext.getBean(Annotation3Student1.class).getName());
            Assert.assertEquals("annotation4-student-name-changed", applicationContext.getBean(Annotation4Student.class).getName());
            Assert.assertEquals("annotation4-component-name-changed", applicationContext.getBean(Annotation4Component.class).getName());
        } finally {
            Files.write(propertyFile.getFile().toPath(), content);
        }
    }

//    @Test
//    public void swapSingleClassTest() throws Exception {
//        assertNotNull(applicationContext.getBean("item2", TeacherConfiguration.class).getName());
//
//        HotSwapper.swapClasses(TeacherConfiguration.class, Item2WithoutValue.class.getName());
//        XmlBeanDefinitionScannerAgent.reloadFlag = true;
//        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
//            @Override
//            public boolean result() throws Exception {
//                return !XmlBeanDefinitionScannerAgent.reloadFlag;
//            }
//        }, 5000));
//
//        assertNull(applicationContext.getBean("item2", TeacherConfiguration.class).getName());
//    }

    private void modifyPropertyFile() throws Exception {
        Files.copy(changedPropertyFile.getFile().toPath(), propertyFile.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
