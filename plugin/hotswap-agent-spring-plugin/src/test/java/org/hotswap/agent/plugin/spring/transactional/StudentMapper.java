package org.hotswap.agent.plugin.spring.transactional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface StudentMapper {

    @Update("CREATE TABLE STUDENT\n" +
            "(\n" +
            "    name VARCHAR(250) NOT NULL\n" +
            ");")
    void createTable();

    @Insert("INSERT INTO STUDENT (name)\n" +
            "VALUES (#{name})\n")
    int insert(@Param("name") String name);

    @Select("SELECT name FROM student WHERE name = #{name}")
    String find(@Param("name") String name);

    @Update("update student \n" +
            "set name=#{name}\n" +
            "WHERE name = #{ori_name} ")
    int update(@Param("ori_name") String ori_name, @Param("name") String name);

}
