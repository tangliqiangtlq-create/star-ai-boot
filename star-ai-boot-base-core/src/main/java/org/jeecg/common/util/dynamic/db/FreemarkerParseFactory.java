package org.jeecg.common.util.dynamic.db;

import freemarker.cache.StringTemplateLoader;
import freemarker.core.ParseException;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.constant.DataBaseConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecgframework.codegenerate.generate.util.SimpleFormat;

import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Zhao Junfu
 * @version V1.0
 * @Title:FreemarkerHelper
 * @description:Freemarker engine assistance class
 * @date Jul 5, 2013 2:58:29 PM
 */
@Slf4j
public class FreemarkerParseFactory {

    private static final String ENCODE = "utf-8";
    private static final String MINI_DAO_FORMAT = "DaoFormat";

    private static Configuration tplConfig;
    private static Configuration sqlConfig;

    private static StringTemplateLoader stringTemplateLoader = new StringTemplateLoader();

    private final static Pattern NOTES_PATTERN = Pattern
            .compile("(?ms)/\\*.*?\\*/|^\\s*//.*?$");

    private static Configuration getTplConfig() {
        if (tplConfig == null) {
            synchronized (FreemarkerParseFactory.class) {
                if (tplConfig == null) {
                    try {
                        tplConfig = SpringContextUtils.getBean("sharedFreemarkerConfiguration", Configuration.class);
                        tplConfig.setClassForTemplateLoading(new FreemarkerParseFactory().getClass(), "/");
                        tplConfig.setNumberFormat("0.#####################");
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to obtain sharedFreemarkerConfiguration for TPL_CONFIG", e);
                    }
                }
            }
        }
        return tplConfig;
    }

    private static Configuration getSqlConfig() {
        if (sqlConfig == null) {
            synchronized (FreemarkerParseFactory.class) {
                if (sqlConfig == null) {
                    try {
                        sqlConfig = SpringContextUtils.getBean("sharedFreemarkerConfiguration", Configuration.class);
                        sqlConfig.setTemplateLoader(stringTemplateLoader);
                        sqlConfig.setNumberFormat("0.#####################");
                        sqlConfig.setClassicCompatible(true);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to obtain sharedFreemarkerConfiguration for SQL_CONFIG", e);
                    }
                }
            }
        }
        return sqlConfig;
    }

    public static boolean isExistTemplate(String tplName) throws Exception {
        try {
            Template mytpl = getTplConfig().getTemplate(tplName, "UTF-8");
            if (mytpl == null) {
                return false;
            }
        } catch (Exception e) {
            if (e instanceof ParseException) {
                log.error(e.getMessage(), e.fillInStackTrace());
                throw new Exception(e);
            }
            log.debug("----isExistTemplate----" + e.toString());
            return false;
        }
        return true;
    }

    public static String parseTemplate(String tplName, Map<String, Object> paras) {
        try {
            log.debug(" minidao sql templdate : " + tplName);
            StringWriter swriter = new StringWriter();
            Template mytpl = getTplConfig().getTemplate(tplName, ENCODE);
            if (paras.containsKey(MINI_DAO_FORMAT)) {
                throw new RuntimeException("DaoFormat is a minidao reserved keyword and cannot be used; please change the parameter definition!");
            }
            paras.put(MINI_DAO_FORMAT, new SimpleFormat());
            mytpl.process(paras, swriter);
            String sql = getSqlText(swriter.toString());
            paras.remove(MINI_DAO_FORMAT);
            return sql;
        } catch (Exception e) {
            log.error(e.getMessage(), e.fillInStackTrace());
            log.error("One-time template key:{ " + tplName + " }");
            //System.err.println(e.getMessage());
            //System.err.println("Template name:{ "+ tplName +" }");
            throw new RuntimeException("Failed to parse SQL template");
        }
    }

    public static String parseTemplateContent(String tplContent, Map<String, Object> paras) {
        // Backward-compat: keepSpace defaults to false for existing 2-arg callers (DynamicDBUtil etc.)
        return parseTemplateContent(tplContent, paras, false);
    }

    public static String parseTemplateContent(String tplContent, Map<String, Object> paras, boolean keepSpace) {
        try {
            String sqlUnderline="sql_";
            StringWriter swriter = new StringWriter();
            if (stringTemplateLoader.findTemplateSource(sqlUnderline + tplContent.hashCode()) == null) {
                stringTemplateLoader.putTemplate(sqlUnderline + tplContent.hashCode(), tplContent);
            }
            Template mytpl = getSqlConfig().getTemplate(sqlUnderline + tplContent.hashCode(), ENCODE);
            if (paras.containsKey(MINI_DAO_FORMAT)) {
                throw new RuntimeException("DaoFormat is a minidao reserved keyword and cannot be used; please change the parameter definition!");
            }
            paras.put(MINI_DAO_FORMAT, new SimpleFormat());
            mytpl.process(paras, swriter);
            String sql = getSqlText(swriter.toString(), keepSpace);
            paras.remove(MINI_DAO_FORMAT);
            return sql;
        } catch (Exception e) {
            log.error(e.getMessage(), e.fillInStackTrace());
            log.error("One-time template key:{ " + tplContent + " }");
            //System.err.println(e.getMessage());
            //System.err.println("Template content:{ "+ tplContent +" }");
            throw new RuntimeException("Failed to parse SQL template");
        }
    }

    /**
     * Remove invalid fields and comments, otherwise batch processing may report an error. Remove invalid equals.
     */
    private static String getSqlText(String sql) {
        return getSqlText(sql, false);
    }

    private static String getSqlText(String sql, boolean keepSpace) {
        // Replace the comment with""
        sql = NOTES_PATTERN.matcher(sql).replaceAll("");
        if (!keepSpace) {
            sql = sql.replaceAll("\\n", " ").replaceAll("\\t", " ")
                    .replaceAll("\\s{1,}", " ").trim();
        }
        // Remove the last one whereSuch a question
        //wherespace "where "
        String whereSpace = DataBaseConstant.SQL_WHERE+" ";
        //"where and"
        String whereAnd = DataBaseConstant.SQL_WHERE+" and";
        //", where"
        String commaWhere = SymbolConstant.COMMA+" "+DataBaseConstant.SQL_WHERE;
        //", "
        String commaSpace = SymbolConstant.COMMA + " ";
        if (sql.endsWith(DataBaseConstant.SQL_WHERE) || sql.endsWith(whereSpace)) {
            sql = sql.substring(0, sql.lastIndexOf("where"));
        }
        // Removewhere and Such a question
        int index = 0;
        while ((index = StringUtils.indexOfIgnoreCase(sql, whereAnd, index)) != -1) {
            sql = sql.substring(0, index + 5)
                    + sql.substring(index + 9, sql.length());
        }
        // Remove , where Such a question
        index = 0;
        while ((index = StringUtils.indexOfIgnoreCase(sql, commaWhere, index)) != -1) {
            sql = sql.substring(0, index)
                    + sql.substring(index + 1, sql.length());
        }
        // Remove the last one ,Such a question
        if (sql.endsWith(SymbolConstant.COMMA) || sql.endsWith(commaSpace)) {
            sql = sql.substring(0, sql.lastIndexOf(","));
        }
        return sql;
    }
}