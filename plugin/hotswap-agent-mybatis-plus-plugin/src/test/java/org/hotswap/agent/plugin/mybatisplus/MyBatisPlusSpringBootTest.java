package org.hotswap.agent.plugin.mybatisplus;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatisplus.entity.PlusUser;
import org.hotswap.agent.plugin.mybatisplus.entity.PlusUser2;
import org.hotswap.agent.plugin.mybatisplus.mapper.PlusMapper;
import org.hotswap.agent.plugin.mybatisplus.mapper.PlusMapper2;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Order;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class, properties = "spring.config.location=classpath:application-plus.properties")
public class MyBatisPlusSpringBootTest extends BaseTest {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MyBatisPlusSpringBootTest.class);

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    PlusMapper plusMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeClass
    public static void setup() throws Exception {
        initMapper();
    }

    private static void initMapper() throws IOException {
        File f = Resources.getResourceAsFile("org/hotswap/agent/plugin/mybatisplus/PlusMapper1.xml");
        Files.copy(f.toPath(), f.toPath().getParent().resolve("PlusMapper.xml"), StandardCopyOption.REPLACE_EXISTING);
    }


    @Test
    @Order(1)
    public void testUserDynamicSql() throws Exception {
        PlusUser user = plusMapper.selectById(1);
        assertEquals("User1", user.getName1());

        // add a column for
        SqlSessionFactory sqlSessionFactory = applicationContext.getBean(SqlSessionFactory.class);
        runScript(dataSource, "org/hotswap/agent/plugin/mybatisplus/AddColumn.sql");
        // noting change
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            PlusMapper mapper = sqlSession.getMapper(PlusMapper.class);
            assertEquals("User1", mapper.selectById(1).getName1());

            // assert gender not exi
            // st
            boolean genderFieldExist = true;
            try {
                user.getClass().getDeclaredField("gender");
            }catch (NoSuchFieldException e) {
                genderFieldExist = false;
            }
            assertFalse(genderFieldExist);
        }

        // after swap, the user should have gender field. and selectById should have gender column
        swapClasses(PlusUser.class, PlusUser2.class.getName());
        PlusUser user2 = plusMapper.selectById(1);

        assertEquals("User1", user2.getName1());
        Field gender = user2.getClass().getDeclaredField("gender");
        gender.setAccessible(true);
        assertEquals("male", gender.get(user2));
    }

    @Test
    @Order(2)
    public void testUserFromXML() throws Exception {
        PlusUser user = plusMapper.getUserXML("User1");
        assertEquals("User1", user.getName1());
        swapMapper("org/hotswap/agent/plugin/mybatisplus/PlusMapper2.xml", "PlusMapper.xml");

        PlusUser user2 = plusMapper.getUserXML("User1");
        assertEquals("User2", user2.getName1());
    }

    @Test
    public void testUserFromAnnotation() throws Exception {
        PlusUser user = plusMapper.getUser("User1");
        assertEquals("User2", user.getName2());
        assertNotNull(user.getName1());
        assertNotNull(user.getId());

        swapClasses(PlusMapper.class, PlusMapper2.class.getName());

        PlusUser userSwap = plusMapper.getUser("User1");
        assertEquals("User2", userSwap.getName1());
        assertNull(userSwap.getName2());
        assertNull(userSwap.getId());
    }
}
