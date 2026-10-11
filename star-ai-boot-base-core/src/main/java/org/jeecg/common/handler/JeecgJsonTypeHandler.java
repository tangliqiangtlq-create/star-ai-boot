package org.jeecg.common.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.sql.*;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class JeecgJsonTypeHandler extends BaseTypeHandler<Object> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
      throws SQLException {
    if (parameter instanceof JSONObject) {
      ps.setString(i, parameter.toString());
    } else if (parameter instanceof JSONArray) {
      ps.setString(i, parameter.toString());
    } else {
      ps.setString(i, parameter.toString());
    }
  }

  @Override
  public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
    String jsonString = rs.getString(columnName);
    return parseJsonString(jsonString);
  }

  @Override
  public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    String jsonString = rs.getString(columnIndex);
    return parseJsonString(jsonString);
  }

  @Override
  public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    String jsonString = cs.getString(columnIndex);
    return parseJsonString(jsonString);
  }

  private Object parseJsonString(String jsonString) {
    if (jsonString == null || jsonString.isEmpty()) {
      return null;
    }
    if (jsonString.trim().startsWith("{")) {
      return JSONUtil.parseObj(jsonString);
    } else if (jsonString.trim().startsWith("[")) {
      return JSONUtil.parseArray(jsonString);
    } else {
      return jsonString;
    }
  }
}
