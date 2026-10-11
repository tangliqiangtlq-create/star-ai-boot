package org.jeecg.config.firewall.SqlInjection;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.oConvertUtils;
import java.util.HashSet;
import java.util.Set;

/**
 * Queried table information
 */
@Slf4j
public class SysDictTableWhite {
    //table name
    private String name;
    //table alias
    private String alias;
    // Field name set
    private Set<String> fields;
    // Query all fields
    private boolean all;

    public SysDictTableWhite() {
        
    }

    public SysDictTableWhite(String name, String alias) {
        this.name = name;
        this.alias = alias;
        this.all = false;
        this.fields = new HashSet<>();
    }

    public void addField(String field) {
        this.fields.add(field);
    }

    public String getName() {
        return name;
    }

    public Set<String> getFields() {
        return new HashSet<>(fields);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFields(Set<String> fields) {
        this.fields = fields;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public boolean isAll() {
        return all;
    }

    public void setAll(boolean all) {
        this.all = all;
    }

    /**
     * Check whether duplicate fields exist.
     *
     * @param fieldControlString
     * @return
     */
    public boolean isAllFieldsValid(String fieldControlString) {
        // Return false if field not in whitelist
        String[] controlFields = fieldControlString.split(",");
        if (oConvertUtils.isEmpty(fieldControlString)) {
            return false;
        }

        for (String queryField : fields) {
            if (oConvertUtils.isIn(queryField, controlFields)) {
                log.warn("Dictionary table whitelist validation, table [" + name + "] field [" + queryField + "] no permission to query");
                return false;
            }
        }

        return true;
    }
    

    @Override
    public String toString() {
        return "QueryTable{" +
                "name='" + name + '\'' +
                ", alias='" + alias + '\'' +
                ", fields=" + fields +
                ", all=" + all +
                '}';
    }
}