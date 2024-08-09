package org.hotswap.agent.plugin.mybatis;

import org.apache.ibatis.io.Resources;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.springboot.Application;
import org.hotswap.agent.plugin.mybatis.springboot.BootUser;
import org.hotswap.agent.plugin.mybatis.springboot.BootUserMapper;
import org.hotswap.agent.plugin.mybatis.springboot.BootUserMapper2;
import org.hotswap.agent.util.test.WaitHelper;
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
public class MybatisSpringBootTest {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MybatisSpringBootTest.class);

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    BootUserMapper bootUserMapper;

    @BeforeClass
    public static void setup() throws Exception {
        File f = Resources.getResourceAsFile("swapXML/BootUserMapper1.xml");
        System.out.println(f.toPath().getParent().getParent());
        Files.copy(f.toPath(), f.toPath().getParent().resolve("BootUserMapper.xml"));
    }

    @AfterClass
    public static void tearDown() throws Exception {
        File tmp = Resources.getResourceAsFile("swapXML/BootUserMapper.xml");
        tmp.delete();
    }

    @Test
    public void testUserFromXML() throws Exception {
        BootUser bootUser = bootUserMapper.getUserXML("jim");
        assertEquals("jim", bootUser.getName());

        swapMapper("swapXML/BootUserMapper2.xml");
        bootUser = bootUserMapper.getUserXML("jim");
        assertNull(bootUser.getName());
        assertNotNull(bootUser.getEmail());
        LOGGER.info("swapMapper: {}", bootUser);
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


    protected static void swapMapper(String mapperNew) throws Exception {
        LOGGER.info("swapMapper: {}", mapperNew);
        MyBatisRefreshCommands.reloadFlag = true;
        File f = Resources.getResourceAsFile(mapperNew);
        Files.copy(f.toPath(),  f.toPath().getParent().resolve("BootUserMapper.xml"), StandardCopyOption.REPLACE_EXISTING);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !MyBatisRefreshCommands.reloadFlag;
            }
        }, 4000 )); // Repository is regenerated within 2*DeltaSpikePlugin.WAIT_ON_REDEFINE

    }
}
