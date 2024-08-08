package org.hotswap.agent.plugin.mybatis.testBeansHotswap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hotswap.agent.plugin.mybatis.User;

public interface UserMapper {
    @Select("select name1 from users where name1 = #{name1}")
    User getUser(@Param("name1") String name1);
}
