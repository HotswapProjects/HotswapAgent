package org.hotswap.agent.plugin.mybatis;

import org.apache.ibatis.io.Resources;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:spring-mybatis.xml" })
public class MybatisSpringTest extends BaseTest {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    Mapper mapper;

    @Autowired
    DataSource dataSource;

    @BeforeClass
    public static void setup() throws Exception {
        File f = Resources.getResourceAsFile("org/hotswap/agent/plugin/mybatis/Mapper1.xml");
        Files.copy(f.toPath(), f.toPath().getParent().resolve("Mapper.xml"));
    }

    @AfterClass
    public static void tearDown() throws Exception {
        File tmp = Resources.getResourceAsFile("org/hotswap/agent/plugin/mybatis/Mapper.xml");
        tmp.delete();
    }

    @Before
    public void init() throws Exception {
        runScript(dataSource, "org/hotswap/agent/plugin/mybatis/CreateDB.sql");
    }

    @Test
    public void testUserFromAnnotation() throws Exception {
        User user = mapper.getUserXML("User1");
        assertEquals("User1", user.getName1());

        swapMapper("org/hotswap/agent/plugin/mybatis/Mapper2.xml");
        user = mapper.getUserXML("User1");
        assertEquals("User2", user.getName1());
    }
}
