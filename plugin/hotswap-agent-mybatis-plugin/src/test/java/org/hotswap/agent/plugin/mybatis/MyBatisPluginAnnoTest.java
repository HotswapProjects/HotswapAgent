package org.hotswap.agent.plugin.mybatis;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.hotswap.agent.plugin.mybatis.testBeansHotswap.UserMapper;
import org.hotswap.agent.plugin.mybatis.testBeansHotswap.UserMapper2;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.*;

public class MyBatisPluginAnnoTest extends BaseTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeClass
    public static void setup() throws Exception {
        // create an SqlSessionFactory
        try (Reader reader = Resources.getResourceAsReader("org/hotswap/agent/plugin/mybatis/mybatis-config-anno.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }

        // populate in-memory database
        runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(), "org/hotswap/agent/plugin/mybatis/CreateDB.sql");
    }

    protected static void runScript(DataSource ds, String resource) throws IOException, SQLException {
        try (Connection connection = ds.getConnection()) {
            ScriptRunner runner = new ScriptRunner(connection);
            runner.setAutoCommit(true);
            runner.setStopOnError(false);
            runner.setLogWriter(null);
            runner.setErrorLogWriter(null);
            runScript(runner, resource);
        }
    }

    private static void runScript(ScriptRunner runner, String resource) throws IOException, SQLException {
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            runner.runScript(reader);
        }
    }

    @Test
    @Ignore
    public void testUserAnnotationSQL() throws Exception {
        // Before the swap, the mapper retrieved only the name1 field
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            User user = mapper.getUser("User1");
            assertEquals("User1", user.getName1());
            assertNull(user.getName2());
        }

        swapClasses(UserMapper.class, UserMapper2.class.getName());

        // After the swap, the mapper retrieves all user columns.
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            User user = mapper.getUser("User1");
            assertEquals("User1", user.getName1());
            assertNotNull(user.getName2());
        }
    }
}
