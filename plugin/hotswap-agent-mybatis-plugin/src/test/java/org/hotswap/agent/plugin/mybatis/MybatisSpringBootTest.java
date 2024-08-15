package org.hotswap.agent.plugin.mybatis;

import org.apache.ibatis.io.Resources;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.springboot.Application;
import org.hotswap.agent.plugin.mybatis.springboot.BootUser;
import org.hotswap.agent.plugin.mybatis.springboot.BootUserMapper;
import org.hotswap.agent.plugin.mybatis.springboot.BootUserMapper2;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class MybatisSpringBootTest extends BaseTest {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MybatisSpringBootTest.class);

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    BootUserMapper bootUserMapper;

    @BeforeClass
    public static void setup() throws Exception {
        File f = Resources.getResourceAsFile("swapXML/BootUserMapper1.xml");
        Files.copy(f.toPath(), f.toPath().getParent().resolve("BootUserMapper.xml"), StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        File f = Resources.getResourceAsFile("swapXML/BootUserMapper1.xml");
        Files.copy(f.toPath(), f.toPath().getParent().resolve("BootUserMapper.xml"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void testUserFromXML() throws Exception {
        BootUser bootUser = bootUserMapper.getUserXML("jim");
        assertEquals("jim", bootUser.getName());

        swapMapper("swapXML/BootUserMapper2.xml", "BootUserMapper.xml");
        bootUser = bootUserMapper.getUserXML("jim");
        assertNull(bootUser.getName());
        assertNotNull(bootUser.getEmail());
    }


    @Test
    public void testUserFromAnnotation() throws Exception {
        BootUser bootUser = bootUserMapper.getUser("jim");
        assertEquals("jim", bootUser.getName());
        assertNotNull(bootUser.getEmail());
        assertNotNull(bootUser.getPhone());

        MyBatisPluginAnnoTest.swapClasses(BootUserMapper.class, BootUserMapper2.class.getName());

        BootUser bootUserAfterSwap = bootUserMapper.getUser("jim");
        assertNull(bootUserAfterSwap.getName());
        assertNull(bootUserAfterSwap.getEmail());
        assertNotNull(bootUserAfterSwap.getPhone());
    }
}
