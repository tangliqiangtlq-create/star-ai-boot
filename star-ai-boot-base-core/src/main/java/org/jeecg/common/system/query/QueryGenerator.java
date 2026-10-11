package org.jeecg.common.system.query;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.beans.PropertyDescriptor;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.DataBaseConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.util.JeecgDataAutorUtils;
import org.jeecg.common.system.util.SqlConcatUtil;
import org.jeecg.common.system.vo.SysPermissionDataRuleModel;
import org.jeecg.common.util.*;
import org.springframework.util.NumberUtils;

/**
 * @Description: query builder
 *
 * @author: jeecg-boot
 */
@Slf4j
public class QueryGenerator {
  public static final String SQL_RULES_COLUMN = "SQL_RULES_COLUMN";

  private static final String BEGIN = "_begin";
  private static final String END = "_end";

  /** Numeric type fields, concatenated with this suffix, accept multi-value params */
  private static final String MULTI = "_MultiString";

  private static final String STAR = "*";
  private static final String COMMA = ",";

  /** Query comma escape character equivalent to a comma [obsolete] */
  public static final String QUERY_COMMA_ESCAPE = "++";

  private static final String NOT_EQUAL = "!";

  /** The page has a rule value query, and spaces are used as separators. */
  private static final String QUERY_SEPARATE_KEYWORD = " ";

  /** The params name passed by the advanced query front-end */
  private static final String SUPER_QUERY_PARAMS = "superQueryParams";

  /** The splicing method params name sent from the advanced query front-end */
  private static final String SUPER_QUERY_MATCH_TYPE = "superQueryMatchType";

  /** single quote */
  public static final String SQL_SQ = "'";

  /** sort column */
  private static final String ORDER_COLUMN = "column";

  /** sort by */
  private static final String ORDER_TYPE = "order";

  private static final String ORDER_TYPE_ASC = "ASC";

  /** Mysql fuzzy query special characters underline (_, \) */
  public static final String LIKE_MYSQL_SPECIAL_STRS = "_,%";

  /** Date format yyyy-MM-dd */
  public static final String YYYY_MM_DD = "yyyy-MM-dd";

  /** to_date */
  public static final String TO_DATE = "to_date";

  /** time formatting */
  private static final ThreadLocal<SimpleDateFormat> LOCAL = new ThreadLocal<SimpleDateFormat>();

  private static SimpleDateFormat getTime() {
    SimpleDateFormat time = LOCAL.get();
    if (time == null) {
      time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      LOCAL.set(time);
    }
    return time;
  }

  /**
   * Get the query condition constructor QueryWrapper instance. The general query condition has been encapsulated.
   *
   * @param searchObj query entity
   * @param parameterMap request.getParameterMap()
   * @return QueryWrapper instance
   */
  public static <T> QueryWrapper<T> initQueryWrapper(
      T searchObj, Map<String, String[]> parameterMap) {
    long start = System.currentTimeMillis();
    QueryWrapper<T> queryWrapper = new QueryWrapper<T>();
    installMplus(queryWrapper, searchObj, parameterMap, null);
    log.debug("---Query condition builder initialized, elapsed:" + (System.currentTimeMillis() - start) + "ms----");
    return queryWrapper;
  }

  // update-begin---author:chenrui ---date:20240527 for: [TV360X-378] Add custom field query rule function------------
  /**
   * Get the query condition constructor QueryWrapper instance. The general query condition has been encapsulated.
   *
   * @param searchObj query entity
   * @param parameterMap request.getParameterMap()
   * @param customRuleMap Custom field query rules {field:QueryRuleEnum}
   * @return QueryWrapper instance
   */
  public static <T> QueryWrapper<T> initQueryWrapper(
      T searchObj, Map<String, String[]> parameterMap, Map<String, QueryRuleEnum> customRuleMap) {
    long start = System.currentTimeMillis();
    QueryWrapper<T> queryWrapper = new QueryWrapper<T>();
    installMplus(queryWrapper, searchObj, parameterMap, customRuleMap);
    log.debug("---Query condition builder initialized, elapsed:" + (System.currentTimeMillis() - start) + "ms----");
    return queryWrapper;
  }

  // update-end---author:chenrui ---date:20240527 for: [TV360X-378] Add custom field query rule function------------

  /**
   * Assemble Mybatis Plus query conditions
   *
   * <p>When using this method, you need to pay attention to the following points: <br>
   * 1.useQueryWrapper instead of LambdaQueryWrapper; <br>
   * 2. Entities cannot be passed into parameters when instantiating QueryWrapper <br>
   * Error example: likeQueryWrapper<JeecgDemo> queryWrapper = new QueryWrapper<JeecgDemo>(jeecgDemo); <br>
   * Correct example: QueryWrapper<JeecgDemo> queryWrapper = new QueryWrapper<JeecgDemo>(); <br>
   * 3. You can also directly call {@link #initQueryWrapper} to obtain the instance directly without using this method.
   */
  private static void installMplus(
      QueryWrapper<?> queryWrapper,
      Object searchObj,
      Map<String, String[]> parameterMap,
      Map<String, QueryRuleEnum> customRuleMap) {
    /*
     * Note: The permission query is configured by the front-end data rules. When a person has multiple departments, the rule configuration can include the condition orgCode contains #{sys_org_code}
    However, customization is not supportedSQLChinese writingorgCode in #{sys_org_code}
    When a person has only one department, he can directly configure the equal conditions.: orgCode equal #{sys_org_code} Or configure customSQL: orgCode = '#{sys_org_code}'
    */

    // Interval condition assembly, fuzzy query, advanced query assembly, simple sorting, permission query
    PropertyDescriptor[] origDescriptors = PropertyUtils.getPropertyDescriptors(searchObj);
    Map<String, SysPermissionDataRuleModel> ruleMap = getRuleMap();

    // Customize permission rulesSQLexpression
    for (String c : ruleMap.keySet()) {
      if (oConvertUtils.isNotEmpty(c) && c.startsWith(SQL_RULES_COLUMN)) {
        queryWrapper.and(i -> i.apply(getSqlRuleValue(ruleMap.get(c).getRuleValue())));
      }
    }

    String name, type, column;
    //update-begin--Author:taoyan Date:20200923 for: issues/1671 If the field is annotated@TableField(exist =
    // false),Not leavingDBQuery-------
    // Define the mapping between entity fields and database field names. In advanced queries, only entity fields can be obtained if setTableFieldNote: Then there will be problems with the query conditions.
    Map<String, String> fieldColumnMap = new HashMap<>(5);
    for (int i = 0; i < origDescriptors.length; i++) {
      // aliasName = origDescriptors[i].getName();  mybatis  There are no entity attributes and there is no need to deal with aliases.
      name = origDescriptors[i].getName();
      type = origDescriptors[i].getPropertyType().toString();
      try {
        if (judgedIsUselessField(name) || !PropertyUtils.isReadable(searchObj, name)) {
          continue;
        }

        Object value = PropertyUtils.getSimpleProperty(searchObj, name);
        column = ReflectHelper.getTableFieldName(searchObj.getClass(), name);
        if (column == null) {
          // columnfornullThere is only one situation, that is, adding annotations@TableField(exist = false) There is no need to deal with it later.
          continue;
        }
        fieldColumnMap.put(name, column);
        // Data permission query
        if (ruleMap.containsKey(name)) {
          addRuleToQueryWrapper(
              ruleMap.get(name), column, origDescriptors[i].getPropertyType(), queryWrapper);
        }
        // Interval query
        doIntervalQuery(queryWrapper, parameterMap, type, name, column);
        // Judgment of single value parameters with different identification strings and different queries
        // TODO This kind of commas before and after supports fuzzy query after splitting.(Multi-select field query takes effect) Example:,1,3,
        if (null != value
            && value.toString().startsWith(COMMA)
            && value.toString().endsWith(COMMA)) {
          String multiLikeval = value.toString().replace(",,", COMMA);
          String[] vals = multiLikeval.substring(1, multiLikeval.length()).split(COMMA);
          final String field = oConvertUtils.camelToUnderline(column);
          if (vals.length > 1) {
            queryWrapper.and(
                j -> {
                  log.info(
                      "---Query filter, Query rule---field:{}, rule:{}, value:{}", field, "like", vals[0]);
                  j = j.like(field, vals[0]);
                  for (int k = 1; k < vals.length; k++) {
                    j = j.or().like(field, vals[k]);
                    log.info(
                        "---Query filter, Query rule .or()---field:{}, rule:{}, value:{}",
                        field,
                        "like",
                        vals[k]);
                  }
                  // return j;
                });
          } else {
            log.info("---Query filter, Query rule---field:{}, rule:{}, value:{}", field, "like", vals[0]);
            queryWrapper.and(j -> j.like(field, vals[0]));
          }
        } else {
          // update-begin---author:chenrui ---date:20240527
          //for: [TV360X-378]Add custom field query rule function------------
          QueryRuleEnum rule;
          if (null != customRuleMap && customRuleMap.containsKey(name)) {
            // Have custom rules,Use custom rules.
            rule = customRuleMap.get(name);
          } else {
            // Determine what type of query to use based on the key string in the parameter value.
            rule = convert2Rule(value);
          }
          //update-end---author:chenrui ---date:20240527 for: [TV360X-378]Add custom field query rule function------------
          value = replaceValue(rule, value);
          // add -begin When adding a string, set it to a fully fuzzy query
          // if( (rule==null || QueryRuleEnum.EQ.equals(rule)) && "class
          // java.lang.String".equals(type)) {
          // You can set left and right blur or full blur, which varies from person to person.
          // rule = QueryRuleEnum.LIKE;
          // }
          // add -end When adding a string, set it to a fully fuzzy query
          addEasyQuery(queryWrapper, column, rule, value);
        }

      } catch (Exception e) {
        log.error("operation failed", e);
      }
    }
    // Sorting logic processing
    doMultiFieldsOrder(queryWrapper, parameterMap, fieldColumnMap);

    // Advanced query
    doSuperQuery(queryWrapper, parameterMap, fieldColumnMap);
    //update-end--Author:taoyan Date:20200923 for: issues/1671 If the field is annotated@TableField(exist =
    // false),Not leavingDBQuery-------

  }

  /**
   * Interval query
   *
   * @param queryWrapper queryobject
   * @param parameterMap parametermap
   * @param type Field type
   * @param filedName Field name
   * @param columnName Column name
   */
  private static void doIntervalQuery(
      QueryWrapper<?> queryWrapper,
      Map<String, String[]> parameterMap,
      String type,
      String filedName,
      String columnName)
      throws ParseException {
    // Add to determine whether there is an interval value
    String endValue = null, beginValue = null;
    if (parameterMap != null && parameterMap.containsKey(filedName + BEGIN)) {
      beginValue = parameterMap.get(filedName + BEGIN)[0].trim();
      addQueryByRule(queryWrapper, columnName, type, beginValue, QueryRuleEnum.GE);
    }
    if (parameterMap != null && parameterMap.containsKey(filedName + END)) {
      endValue = parameterMap.get(filedName + END)[0].trim();
      addQueryByRule(queryWrapper, columnName, type, endValue, QueryRuleEnum.LE);
    }
    // multi-value query
    if (parameterMap != null && parameterMap.containsKey(filedName + MULTI)) {
      endValue = parameterMap.get(filedName + MULTI)[0].trim();
      addQueryByRule(queryWrapper, columnName.replace(MULTI, ""), type, endValue, QueryRuleEnum.IN);
    }
  }

  private static void doMultiFieldsOrder(
      QueryWrapper<?> queryWrapper,
      Map<String, String[]> parameterMap,
      Map<String, String> fieldColumnMap) {
    Set<String> allFields = fieldColumnMap.keySet();
    String column = null, order = null;
    if (parameterMap != null && parameterMap.containsKey(ORDER_COLUMN)) {
      column = parameterMap.get(ORDER_COLUMN)[0];
    }
    if (parameterMap != null && parameterMap.containsKey(ORDER_TYPE)) {
      order = parameterMap.get(ORDER_TYPE)[0];
    }
    log.debug("Sort rule>> column:" + column + ",sort order:" + order);

    //update-begin-author:scott date:2022-11-07 for:Avoid user-defined tables without default fields{creation time}, Cause sorting error
    // TODO Avoid user-defined tables without default field creation time, resulting in sorting errors
    if (DataBaseConstant.CREATE_TIME.equals(column)
        && !fieldColumnMap.containsKey(DataBaseConstant.CREATE_TIME)) {
      column = "id";
      log.warn("Detected no createTime field in entity, switched to ID sorting!");
    }
    //update-end-author:scott date:2022-11-07 for:Avoid user-defined tables without default fields{creation time}, Cause sorting error

    if (oConvertUtils.isNotEmpty(column) && oConvertUtils.isNotEmpty(order)) {
      // Dictionary field, remove the dictionary translation text suffix
      if (column.endsWith(CommonConstant.DICT_TEXT_SUFFIX)) {
        column = column.substring(0, column.lastIndexOf(CommonConstant.DICT_TEXT_SUFFIX));
      }

      // update-begin-author:taoyan date:2022-5-16 for: issues/3676 When getting the system user list, useSQLInjection takes effect
      // judgecolumnIs it the current entity?
      log.debug("Current fields are: " + allFields);
      if (!allColumnExist(column, allFields)) {
        throw new JeecgBootException("Note: column field to be sorted does not exist: " + column);
      }
      // update-end-author:taoyan date:2022-5-16 for: issues/3676 When getting the system user list, useSQLInjection takes effect

      // update-begin-author:scott date:2022-10-10
      // for:【jeecg-boot/issues/I5FJU6】doMultiFieldsOrder() Problems with multi-field sorting methods
      // Multifield sort method not reading MybatisPlus annotation @TableField inside value value
      if (column.contains(",")) {
        List<String> columnList = Arrays.asList(column.split(","));
        String columnStrNew =
            columnList.stream().map(c -> fieldColumnMap.get(c)).collect(Collectors.joining(","));
        if (oConvertUtils.isNotEmpty(columnStrNew)) {
          column = columnStrNew;
        }
      } else {
        column = fieldColumnMap.get(column);
      }
      // update-end-author:scott date:2022-10-10 for:【jeecg-boot/issues/I5FJU6】doMultiFieldsOrder()
      // Problems with multi-field sorting methods

      // SQLinjectioncheck
      SqlInjectionUtil.filterContentMulti(column);

      //update-begin--Author:scott Date:20210531 for: 36 Multi-condition sorting invalid problem fixed-------
      // Sorting rule modification
      // Sort existing _ Front-end delivery sorting conditions{....,column: 'column1,column2',order: 'desc'} translated intosql "column1,column2
      // desc"
      // Modify to _ Front-end delivery sorting conditions{....,column: 'column1,column2',order: 'desc'} translated intosql "column1 desc,column2
      // desc"
      if (order.toUpperCase().indexOf(ORDER_TYPE_ASC) >= 0) {
        queryWrapper.orderByAsc(SqlInjectionUtil.getSqlInjectSortFields(column.split(",")));
      } else {
        queryWrapper.orderByDesc(SqlInjectionUtil.getSqlInjectSortFields(column.split(",")));
      }
      //update-end--Author:scott Date:20210531 for: 36 Multi-condition sorting invalid problem fixed-------
    }
  }

  // update-begin-author:taoyan date:2022-5-23 for: issues/3676 When getting the system user list, useSQLInjection takes effect
  /**
   * Multi-field sorting determines whether the passed field exists
   *
   * @return
   */
  private static boolean allColumnExist(String columnStr, Set<String> allFields) {
    boolean exist = true;
    if (columnStr.indexOf(COMMA) >= 0) {
      String[] arr = columnStr.split(COMMA);
      for (String column : arr) {
        if (!allFields.contains(column)) {
          exist = false;
          break;
        }
      }
    } else {
      exist = allFields.contains(columnStr);
    }
    return exist;
  }

  // update-end-author:taoyan date:2022-5-23 for: issues/3676 When getting the system user list, useSQLInjection takes effect

  /**
   * Advanced query
   *
   * @param queryWrapper Query object
   * @param parameterMap parameter object
   * @param fieldColumnMap Entity fields correspond to database columnsmap
   */
  private static void doSuperQuery(
      QueryWrapper<?> queryWrapper,
      Map<String, String[]> parameterMap,
      Map<String, String> fieldColumnMap) {
    if (parameterMap != null && parameterMap.containsKey(SUPER_QUERY_PARAMS)) {
      String superQueryParams = parameterMap.get(SUPER_QUERY_PARAMS)[0];
      String superQueryMatchType =
          parameterMap.get(SUPER_QUERY_MATCH_TYPE) != null
              ? parameterMap.get(SUPER_QUERY_MATCH_TYPE)[0]
              : MatchTypeEnum.AND.getValue();
      MatchTypeEnum matchType = MatchTypeEnum.getByValue(superQueryMatchType);
      //update-begin--Author:sunjianlei Date:20200325 for: Advanced query conditions should be enclosed in parentheses to prevent conflicts with other user conditions. -------
      try {
        superQueryParams = URLDecoder.decode(superQueryParams, "UTF-8");
        List<QueryCondition> conditions = JSON.parseArray(superQueryParams, QueryCondition.class);
        if (conditions == null || conditions.size() == 0) {
          return;
        }
        // update-begin-author:sunjianlei date:20220119 for: 【JTC-573】 Filter empty condition queries to prevent sql Splice the excess and
        List<QueryCondition> filterConditions =
            conditions.stream()
                .filter(
                    rule ->
                        oConvertUtils.isNotEmpty(rule.getField())
                            && oConvertUtils.isNotEmpty(rule.getRule())
                            && oConvertUtils.isNotEmpty(rule.getVal()))
                .collect(Collectors.toList());
        if (filterConditions.size() == 0) {
          return;
        }
        // update-end-author:sunjianlei date:20220119 for: 【JTC-573】 Filter empty condition queries to prevent sql Splice the excess and
        log.debug("---Advanced query params-->" + filterConditions);

        queryWrapper.and(
            andWrapper -> {
              for (int i = 0; i < filterConditions.size(); i++) {
                QueryCondition rule = filterConditions.get(i);
                if (oConvertUtils.isNotEmpty(rule.getField())
                    && oConvertUtils.isNotEmpty(rule.getRule())
                    && oConvertUtils.isNotEmpty(rule.getVal())) {

                  log.debug("SuperQuery ==> " + rule.toString());

                  // update-begin-author:taoyan date:20201228 for: 【Advanced query] oracle Date equals query error
                  Object queryValue = rule.getVal();
                  if ("date".equals(rule.getType())) {
                    queryValue = DateUtils.str2Date(rule.getVal(), DateUtils.date_sdf.get());
                  } else if ("datetime".equals(rule.getType())) {
                    queryValue = DateUtils.str2Date(rule.getVal(), DateUtils.datetimeFormat.get());
                  }
                  // update-begin--author:sunjianlei date:20210702
                  //for: [/issues/I3VR8E]There is no type conversion for advanced queries, and query parameters are all string types. ----
                  String dbType = rule.getDbType();
                  if (oConvertUtils.isNotEmpty(dbType)) {
                    try {
                      String valueStr = String.valueOf(queryValue);
                      switch (dbType.toLowerCase().trim()) {
                        case "int":
                          queryValue = Integer.parseInt(valueStr);
                          break;
                        case "bigdecimal":
                          queryValue = new BigDecimal(valueStr);
                          break;
                        case "short":
                          queryValue = Short.parseShort(valueStr);
                          break;
                        case "long":
                          queryValue = Long.parseLong(valueStr);
                          break;
                        case "float":
                          queryValue = Float.parseFloat(valueStr);
                          break;
                        case "double":
                          queryValue = Double.parseDouble(valueStr);
                          break;
                        case "boolean":
                          queryValue = Boolean.parseBoolean(valueStr);
                          break;
                        default:
                      }
                    } catch (Exception e) {
                      log.error("Advanced query value conversion failed:", e);
                    }
                  }
                  // update-begin--author:sunjianlei date:20210702
                  //for: [/issues/I3VR8E]There is no type conversion for advanced queries, and query parameters are all string types. ----
                  addEasyQuery(
                      andWrapper,
                      fieldColumnMap.get(rule.getField()),
                      QueryRuleEnum.getByValue(rule.getRule()),
                      queryValue);
                  // update-end-author:taoyan date:20201228 for: 【Advanced query] oracle Date equals query error

                  //If the splicing method isOR, Just spliceOR
                  if (MatchTypeEnum.OR == matchType && i < (filterConditions.size() - 1)) {
                    andWrapper.or();
                  }
                }
              }
              // return andWrapper;
            });
      } catch (UnsupportedEncodingException e) {
        log.error("--Advanced query param decode failed: " + superQueryParams, e);
      } catch (Exception e) {
        log.error("operation failed");
        log.error("operation failed", e);
      }
      //update-end--Author:sunjianlei Date:20200325 for: Advanced query conditions should be enclosed in parentheses to prevent conflicts with other user conditions. -------
    }
    // log.info(" superQuery getCustomSqlSegment: "+ queryWrapper.getCustomSqlSegment());
  }

  /**
   * According to the passed value, it is converted into the corresponding comparison method. Supported><= like in !
   *
   * @param value
   * @return
   */
  public static QueryRuleEnum convert2Rule(Object value) {
    // Avoid empty data
    // update-begin-author:taoyan date:20210629 for: Entering spaces in query conditions results inreturn nullSubsequent judgment leads to throwingnullabnormal
    if (value == null) {
      return QueryRuleEnum.EQ;
    }
    String val = (value + "").toString().trim();
    if (val.length() == 0) {
      return QueryRuleEnum.EQ;
    }
    // update-end-author:taoyan date:20210629 for: Entering spaces in query conditions results inreturn nullSubsequent judgment leads to throwingnullabnormal
    QueryRuleEnum rule = null;

    //update-begin--Author:scott Date:20190724 for: initQueryWrapperAssemblesqlQuery condition error
    // #284-------------------
    // TODO The rules here only apply to le lt ge gt
    // step 2 .>= =<
    int length2 = 2;
    int length3 = 3;
    if (rule == null && val.length() >= length3) {
      if (QUERY_SEPARATE_KEYWORD.equals(val.substring(length2, length3))) {
        rule = QueryRuleEnum.getByValue(val.substring(0, 2));
      }
    }
    // step 1 .> <
    if (rule == null && val.length() >= length2) {
      if (QUERY_SEPARATE_KEYWORD.equals(val.substring(1, length2))) {
        rule = QueryRuleEnum.getByValue(val.substring(0, 1));
      }
    }
    //update-end--Author:scott Date:20190724 for: initQueryWrapperAssemblesqlQuery condition error
    // #284---------------------

    // step 3 like
    //update-begin-author:taoyan for: /issues/3382 Default with*Just go blurry, but if there's only one*, Then go equal to query
    if (rule == null && val.equals(STAR)) {
      rule = QueryRuleEnum.EQ;
    }
    //update-end-author:taoyan for: /issues/3382 Default with*Just go blurry, but if there's only one*, Then go equal to query
    if (rule == null && val.contains(STAR)) {
      if (val.startsWith(STAR) && val.endsWith(STAR)) {
        rule = QueryRuleEnum.LIKE;
      } else if (val.startsWith(STAR)) {
        rule = QueryRuleEnum.LEFT_LIKE;
      } else if (val.endsWith(STAR)) {
        rule = QueryRuleEnum.RIGHT_LIKE;
      }
    }

    // step 4 in
    if (rule == null && val.contains(COMMA)) {
      // TODO in There should be a query herebug  If a field itself is multi-select, useinQuery may not be able to find out
      rule = QueryRuleEnum.IN;
    }
    // step 5 !=
    if (rule == null && val.startsWith(NOT_EQUAL)) {
      rule = QueryRuleEnum.NE;
    }
    // step 6 xx+xx+xx This situation is suitable if you want to use commas for precise query, but the system defaults to commasin So it can be used++Replace [this logic is invalid]
    if (rule == null && val.indexOf(QUERY_COMMA_ESCAPE) > 0) {
      rule = QueryRuleEnum.EQ_WITH_ADD;
    }

    //update-begin--Author:taoyan Date:20201229 for: initQueryWrapperAssemblesqlQuery condition error
    // #284---------------------
    // Special handling:Oracleexpressionto_date('xxx','yyyy-MM-dd')Containing commas will be recognized asinQuery, converted to equal to query
    if (rule == QueryRuleEnum.IN && val.indexOf(YYYY_MM_DD) >= 0 && val.indexOf(TO_DATE) >= 0) {
      rule = QueryRuleEnum.EQ;
    }
    //update-end--Author:taoyan Date:20201229 for: initQueryWrapperAssemblesqlQuery condition error
    // #284---------------------

    return rule != null ? rule : QueryRuleEnum.EQ;
  }

  /**
   * Replace keyword characters
   *
   * @param rule
   * @param value
   * @return
   */
  private static Object replaceValue(QueryRuleEnum rule, Object value) {
    if (rule == null) {
      return null;
    }
    if (!(value instanceof String)) {
      return value;
    }
    String val = (value + "").toString().trim();
    //update-begin-author:taoyan date:20220302 for: The value of the query condition is the equal sign (=)bug #3443
    if (QueryRuleEnum.EQ.getValue().equals(val)) {
      return val;
    }
    //update-end-author:taoyan date:20220302 for: The value of the query condition is the equal sign (=)bug #3443
    if (rule == QueryRuleEnum.LIKE) {
      value = val.substring(1, val.length() - 1);
      //mysql Fuzzy query special character underline (_, \)
      value = specialStrConvert(value.toString());
    } else if (rule == QueryRuleEnum.LEFT_LIKE || rule == QueryRuleEnum.NE) {
      value = val.substring(1);
      //mysql Fuzzy query special character underline (_, \)
      value = specialStrConvert(value.toString());
    } else if (rule == QueryRuleEnum.RIGHT_LIKE) {
      value = val.substring(0, val.length() - 1);
      //mysql Fuzzy query special character underline (_, \)
      value = specialStrConvert(value.toString());
    } else if (rule == QueryRuleEnum.IN) {
      value = val.split(",");
    } else if (rule == QueryRuleEnum.EQ_WITH_ADD) {
      value = val.replaceAll("\\+\\+", COMMA);
    } else {
      //update-begin--Author:scott Date:20190724 for: initQueryWrapperAssemblesqlQuery condition error
      // #284-------------------
      if (val.startsWith(rule.getValue())) {
        // TODO The logic here should be commented out-> If the query content contains query matching rule symbols, it will be intercepted (for example:>=Hello)
        value = val.replaceFirst(rule.getValue(), "");
      } else if (val.startsWith(rule.getCondition() + QUERY_SEPARATE_KEYWORD)) {
        value = val.replaceFirst(rule.getCondition() + QUERY_SEPARATE_KEYWORD, "").trim();
      }
      //update-end--Author:scott Date:20190724 for: initQueryWrapperAssemblesqlQuery condition error
      // #284-------------------
    }
    return value;
  }

  private static void addQueryByRule(
      QueryWrapper<?> queryWrapper, String name, String type, String value, QueryRuleEnum rule)
      throws ParseException {
    if (oConvertUtils.isNotEmpty(value)) {
      //update-begin--Author:sunjianlei Date:20220104 for: [JTC-409]Fixed the problem of not converting types when separated by commas, resulting in errors in database queries with strict types.
      // -------------------
      // For numeric type fields, multi-value query
      if (value.contains(COMMA)) {
        Object[] temp =
            Arrays.stream(value.split(COMMA))
                .map(
                    v -> {
                      try {
                        return QueryGenerator.parseByType(v, type, rule);
                      } catch (ParseException e) {
                        log.error("operation failed", e);
                        return v;
                      }
                    })
                .toArray();
        addEasyQuery(queryWrapper, name, rule, temp);
        return;
      }
      Object temp = QueryGenerator.parseByType(value, type, rule);
      addEasyQuery(queryWrapper, name, rule, temp);
      //update-end--Author:sunjianlei Date:20220104 for: [JTC-409]Fixed the problem of not converting types when separated by commas, resulting in errors in database queries with strict types.
      // -------------------
    }
  }

  /**
   * Convert the given value according to the type
   *
   * @param value
   * @param type
   * @param rule
   * @return
   * @throws ParseException
   */
  private static Object parseByType(String value, String type, QueryRuleEnum rule)
      throws ParseException {
    Object temp;
    switch (type) {
      case "class java.lang.Integer":
        temp = Integer.parseInt(value);
        break;
      case "class java.math.BigDecimal":
        temp = new BigDecimal(value);
        break;
      case "class java.lang.Short":
        temp = Short.parseShort(value);
        break;
      case "class java.lang.Long":
        temp = Long.parseLong(value);
        break;
      case "class java.lang.Float":
        temp = Float.parseFloat(value);
        break;
      case "class java.lang.Double":
        temp = Double.parseDouble(value);
        break;
      case "class java.util.Date":
        temp = getDateQueryByRule(value, rule);
        break;
      default:
        temp = value;
        break;
    }
    return temp;
  }

  /**
   * Get a date type value
   *
   * @param value
   * @param rule
   * @return
   * @throws ParseException
   */
  private static Date getDateQueryByRule(String value, QueryRuleEnum rule) throws ParseException {
    Date date = null;
    int length = 10;
    if (value.length() == length) {
      if (rule == QueryRuleEnum.GE) {
        // comparatively greater than
        date = getTime().parse(value + " 00:00:00");
      } else if (rule == QueryRuleEnum.LE) {
        // less than
        date = getTime().parse(value + " 23:59:59");
      }
      // TODO The date type is quite special, maybeoracleIt may not work easily
    }
    if (date == null) {
      date = getTime().parse(value);
    }
    return date;
  }

  /**
   * Perform different queries based on rules
   *
   * @param queryWrapper QueryWrapper
   * @param name Field name
   * @param rule Query rules
   * @param value Query condition value
   */
  public static void addEasyQuery(
      QueryWrapper<?> queryWrapper, String name, QueryRuleEnum rule, Object value) {
    if (name == null || value == null || rule == null || oConvertUtils.isEmpty(value)) {
      return;
    }
    name = oConvertUtils.camelToUnderline(name);
    log.debug("---Advanced query Query rule---field:{} , rule:{} , value:{}", name, rule.getValue(), value);
    switch (rule) {
      case GT:
        queryWrapper.gt(name, value);
        break;
      case GE:
        queryWrapper.ge(name, value);
        break;
      case LT:
        queryWrapper.lt(name, value);
        break;
      case LE:
        queryWrapper.le(name, value);
        break;
      case EQ:
      case EQ_WITH_ADD:
        queryWrapper.eq(name, value);
        break;
      case NE:
        queryWrapper.ne(name, value);
        break;
      case IN:
        if (value instanceof String) {
          queryWrapper.in(name, (Object[]) value.toString().split(COMMA));
        } else if (value instanceof String[]) {
          queryWrapper.in(name, (Object[]) value);
        }
        // update-begin-author:taoyan date:20200909 for:【bug】in Type multi-value query is not suitablepostgresql #1671
        else if (value.getClass().isArray()) {
          queryWrapper.in(name, (Object[]) value);
        } else {
          queryWrapper.in(name, value);
        }
        // update-end-author:taoyan date:20200909 for:【bug】in Type multi-value query is not suitablepostgresql #1671
        break;
      case LIKE:
        queryWrapper.like(name, value);
        break;
      case LEFT_LIKE:
        queryWrapper.likeLeft(name, value);
        break;
      case NOT_LEFT_LIKE:
        queryWrapper.notLikeLeft(name, value);
        break;
      case RIGHT_LIKE:
        queryWrapper.likeRight(name, value);
        break;
      case NOT_RIGHT_LIKE:
        queryWrapper.notLikeRight(name, value);
        break;
      // update-begin---author:chenrui ---date:20240527
      //for: [TV360X-378]The drop-down multi-box cannot be queried based on conditions.:Add custom field query rule function------------
      case LIKE_WITH_OR:
        final String nameFinal = name;
        Object[] vals;
        if (value instanceof String) {
          vals = value.toString().split(COMMA);
        } else if (value instanceof String[]) {
          vals = (Object[]) value;
        }
        // update-begin-author:taoyan date:20200909 for:【bug】in Type multi-value query is not suitablepostgresql #1671
        else if (value.getClass().isArray()) {
          vals = (Object[]) value;
        } else {
          vals = new Object[] {value};
        }
        queryWrapper.and(
            j -> {
              log.info(
                  "---Query filter, Query rule---field:{}, rule:{}, value:{}", nameFinal, "like", vals[0]);
              j = j.like(nameFinal, vals[0]);
              for (int k = 1; k < vals.length; k++) {
                j = j.or().like(nameFinal, vals[k]);
                log.info(
                    "---Query filter, Query rule .or()---field:{}, rule:{}, value:{}",
                    nameFinal,
                    "like",
                    vals[k]);
              }
            });
        break;
      // update-end---author:chenrui ---date:20240527
      //for: [TV360X-378]The drop-down multi-box cannot be queried based on conditions.:Add custom field query rule function------------
      default:
        log.info("--Query rule not matched---");
        break;
    }
  }

  /**
   * @param name
   * @return
   */
  private static boolean judgedIsUselessField(String name) {
    return "class".equals(name)
        || "ids".equals(name)
        || "page".equals(name)
        || "rows".equals(name)
        || "sort".equals(name)
        || "order".equals(name);
  }

  /**
   * Get the data permission rules corresponding to the request TODO There is a problem with multiple permissions for the same column
   *
   * @return
   */
  public static Map<String, SysPermissionDataRuleModel> getRuleMap() {
    Map<String, SysPermissionDataRuleModel> ruleMap = new HashMap<>(5);
    List<SysPermissionDataRuleModel> list = null;
    // update-begin-author:taoyan date:2023-6-1 for:QQYUN-5441 【Jianliu】Get multiple users/department/Role Set Department Query Error Report
    try {
      list = JeecgDataAutorUtils.loadDataSearchConditon();
    } catch (Exception e) {
      log.error("Failed to get permission data from request object, may be executed in scheduled task.", e);
    }
    // update-end-author:taoyan date:2023-6-1 for:QQYUN-5441 【Jianliu】Get multiple users/department/Role Set Department Query Error Report
    if (list != null && list.size() > 0) {
      if (list.get(0) == null) {
        return ruleMap;
      }
      for (SysPermissionDataRuleModel rule : list) {
        String column = rule.getRuleColumn();
        if (QueryRuleEnum.SQL_RULES.getValue().equals(rule.getRuleConditions())) {
          column = SQL_RULES_COLUMN + rule.getId();
        }
        ruleMap.put(column, rule);
      }
    }
    return ruleMap;
  }

  private static void addRuleToQueryWrapper(
      SysPermissionDataRuleModel dataRule,
      String name,
      Class propertyType,
      QueryWrapper<?> queryWrapper) {
    QueryRuleEnum rule = QueryRuleEnum.getByValue(dataRule.getRuleConditions());
    if (rule.equals(QueryRuleEnum.IN) && !propertyType.equals(String.class)) {
      String[] values = dataRule.getRuleValue().split(",");
      Object[] objs = new Object[values.length];
      for (int i = 0; i < values.length; i++) {
        objs[i] = NumberUtils.parseNumber(values[i], propertyType);
      }
      addEasyQuery(queryWrapper, name, rule, objs);
    } else {
      if (propertyType.equals(String.class)) {
        addEasyQuery(queryWrapper, name, rule, converRuleValue(dataRule.getRuleValue()));
      } else if (propertyType.equals(Date.class)) {
        String dateStr = converRuleValue(dataRule.getRuleValue());
        int length = 10;
        if (dateStr.length() == length) {
          addEasyQuery(
              queryWrapper, name, rule, DateUtils.str2Date(dateStr, DateUtils.date_sdf.get()));
        } else {
          addEasyQuery(
              queryWrapper,
              name,
              rule,
              DateUtils.str2Date(dateStr, DateUtils.datetimeFormat.get()));
        }
      } else {
        addEasyQuery(
            queryWrapper,
            name,
            rule,
            NumberUtils.parseNumber(dataRule.getRuleValue(), propertyType));
      }
    }
  }

  public static String converRuleValue(String ruleValue) {
    return null;
  }

  /**
   * @author: scott @Description: Remove the single quotes around the value
   * @date: 2020/3/19 21:26
   * @param ruleValue: @Return: java.lang.String
   */
  public static String trimSingleQuote(String ruleValue) {
    if (oConvertUtils.isEmpty(ruleValue)) {
      return "";
    }
    if (ruleValue.startsWith(QueryGenerator.SQL_SQ)) {
      ruleValue = ruleValue.substring(1);
    }
    if (ruleValue.endsWith(QueryGenerator.SQL_SQ)) {
      ruleValue = ruleValue.substring(0, ruleValue.length() - 1);
    }
    return ruleValue;
  }

  public static String getSqlRuleValue(String sqlRule) {
    try {
      Set<String> varParams = getSqlRuleParams(sqlRule);
      for (String var : varParams) {
        String tempValue = converRuleValue(var);
        sqlRule = sqlRule.replace("#{" + var + "}", tempValue);
      }
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return sqlRule;
  }

  /** getsqlin#{key} thiskeycomposed ofset */
  public static Set<String> getSqlRuleParams(String sql) {
    if (oConvertUtils.isEmpty(sql)) {
      return null;
    }
    Set<String> varParams = new HashSet<String>();
    String regex = "\\#\\{\\w+\\}";

    Pattern p = Pattern.compile(regex);
    Matcher m = p.matcher(sql);
    while (m.find()) {
      String var = m.group();
      varParams.add(var.substring(var.indexOf("{") + 1, var.indexOf("}")));
    }
    return varParams;
  }

  /**
   * Get query conditions
   *
   * @param field
   * @param alias
   * @param value
   * @param isString
   * @return
   */
  public static String getSingleQueryConditionSql(
      String field, String alias, Object value, boolean isString) {
    return SqlConcatUtil.getSingleQueryConditionSql(field, alias, value, isString, null);
  }

  /**
   * Generate relevant permissions based on permission-related configurationsSQL statement
   *
   * @param clazz
   * @return
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static String installAuthJdbc(Class<?> clazz) {
    StringBuffer sb = new StringBuffer();
    // Permission query
    Map<String, SysPermissionDataRuleModel> ruleMap = getRuleMap();
    PropertyDescriptor[] origDescriptors = PropertyUtils.getPropertyDescriptors(clazz);
    String sqlAnd = " and ";
    for (String c : ruleMap.keySet()) {
      if (oConvertUtils.isNotEmpty(c) && c.startsWith(SQL_RULES_COLUMN)) {
        sb.append(sqlAnd + getSqlRuleValue(ruleMap.get(c).getRuleValue()));
      }
    }
    String name, column;
    for (int i = 0; i < origDescriptors.length; i++) {
      name = origDescriptors[i].getName();
      if (judgedIsUselessField(name)) {
        continue;
      }
      if (ruleMap.containsKey(name)) {
        column = ReflectHelper.getTableFieldName(clazz, name);
        if (column == null) {
          continue;
        }
        SysPermissionDataRuleModel dataRule = ruleMap.get(name);
        QueryRuleEnum rule = QueryRuleEnum.getByValue(dataRule.getRuleConditions());
        Class propType = origDescriptors[i].getPropertyType();
        boolean isString = propType.equals(String.class);
        Object value;
        // update-begin---author:chenrui ---date:20240527
        //for: [TV360X-539]Data permissions, when the configuration date is equal to the condition, the backend reports a conversion error------------
        if (isString || Date.class.equals(propType)) {
          // update-end---author:chenrui ---date:20240527
          //for: [TV360X-539]Data permissions, when the configuration date is equal to the condition, the backend reports a conversion error------------
          value = converRuleValue(dataRule.getRuleValue());
        } else {
          value = NumberUtils.parseNumber(dataRule.getRuleValue(), propType);
        }
        String filedSql =
            SqlConcatUtil.getSingleSqlByRule(
                rule, oConvertUtils.camelToUnderline(column), value, isString);
        sb.append(sqlAnd + filedSql);
      }
    }
    log.info("query auth sql is:" + sb.toString());
    return sb.toString();
  }

  /**
   * Assemble based on permission-related configurationsmpRequired permissions
   *
   * @param queryWrapper
   * @param clazz
   * @return
   */
  public static void installAuthMplus(QueryWrapper<?> queryWrapper, Class<?> clazz) {
    // Permission query
    Map<String, SysPermissionDataRuleModel> ruleMap = getRuleMap();
    PropertyDescriptor[] origDescriptors = PropertyUtils.getPropertyDescriptors(clazz);
    for (String c : ruleMap.keySet()) {
      if (oConvertUtils.isNotEmpty(c) && c.startsWith(SQL_RULES_COLUMN)) {
        queryWrapper.and(i -> i.apply(getSqlRuleValue(ruleMap.get(c).getRuleValue())));
      }
    }
    String name, column;
    for (int i = 0; i < origDescriptors.length; i++) {
      name = origDescriptors[i].getName();
      if (judgedIsUselessField(name)) {
        continue;
      }
      column = ReflectHelper.getTableFieldName(clazz, name);
      if (column == null) {
        continue;
      }
      if (ruleMap.containsKey(name)) {
        addRuleToQueryWrapper(
            ruleMap.get(name), column, origDescriptors[i].getPropertyType(), queryWrapper);
      }
    }
  }

  /**
   * Convertsqlsystem variables in
   *
   * @param sql
   * @return
   */
  public static String convertSystemVariables(String sql) {
    return getSqlRuleValue(sql);
  }

  /** Get system database type */
  private static String getDbType() {
    return CommonUtils.getDatabaseType();
  }

  /**
   * mysql Fuzzy query special character underline (_, \)
   *
   * @param value: @Return: java.lang.String
   */
  private static String specialStrConvert(String value) {
    if (DataBaseConstant.DB_TYPE_MYSQL.equals(getDbType())
        || DataBaseConstant.DB_TYPE_MARIADB.equals(getDbType())) {
      String[] specialStr = QueryGenerator.LIKE_MYSQL_SPECIAL_STRS.split(",");
      for (String str : specialStr) {
        if (value.indexOf(str) != -1) {
          value = value.replace(str, "\\" + str);
        }
      }
    }
    return value;
  }
}
