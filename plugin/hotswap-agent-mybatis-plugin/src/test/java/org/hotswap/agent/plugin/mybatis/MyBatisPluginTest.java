/*
 * Copyright 2013-2024 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.mybatis;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class MyBatisPluginTest extends BaseTest {

  private static SqlSessionFactory sqlSessionFactory;

    @BeforeClass
    public static void setup() throws Exception {
        // create an SqlSessionFactory
        File f = Resources.getResourceAsFile("org/hotswap/agent/plugin/mybatis/Mapper1.xml");
        Files.copy(f.toPath(), f.toPath().getParent().resolve("Mapper.xml"));
        try (Reader reader = Resources.getResourceAsReader("org/hotswap/agent/plugin/mybatis/mybatis-config.xml")) {
          sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }

        // populate in-memory database
        runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
                "org/hotswap/agent/plugin/mybatis/CreateDB.sql");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        File tmp = Resources.getResourceAsFile("org/hotswap/agent/plugin/mybatis/Mapper.xml");
        tmp.delete();
    }

    @Test
    public void testUserFromAnnotation() throws Exception {
      try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
          Mapper mapper = sqlSession.getMapper(Mapper.class);
          User user = mapper.getUserXML("User1");
          assertEquals("User1", user.getName1());
      }
      swapMapper("org/hotswap/agent/plugin/mybatis/Mapper2.xml");
      try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
          Mapper mapper = sqlSession.getMapper(Mapper.class);
          User user = mapper.getUserXML("User1");
          assertEquals("User2", user.getName1());
      }
    }

}
