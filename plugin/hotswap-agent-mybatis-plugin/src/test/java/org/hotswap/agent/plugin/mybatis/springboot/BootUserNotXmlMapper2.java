package org.hotswap.agent.plugin.mybatis.springboot;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@org.apache.ibatis.annotations.Mapper
public interface BootUserNotXmlMapper2 {

  @Select("select phone from `boot_user` where name = #{name}")
  BootUser getUser(@Param("name") String name);
}
