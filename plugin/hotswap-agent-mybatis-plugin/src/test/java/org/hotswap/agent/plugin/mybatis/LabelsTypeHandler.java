/*
 * Copyright 2013-2023 the HotswapAgent authors.
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

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

@MappedTypes(Map.class)
public class LabelsTypeHandler implements TypeHandler<Map<String, Object>> {

  @Override
  public void setParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
    // Not Implemented
  }

  @Override
  public Map<String, Object> getResult(ResultSet rs, String columnName) throws SQLException {
    // Not Implemented
    return null;
  }

  @Override
  public Map<String, Object> getResult(ResultSet rs, int columnIndex) throws SQLException {
    // Not Implemented
    return null;
  }

  @Override
  public Map<String, Object> getResult(CallableStatement cs, int columnIndex) throws SQLException {
    // Not Implemented
    return null;
  }

}
