package org.jeecg.common.util.sqlInjection.parse;

import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 *  SQL statement analysis converter base class<br>
 * Modification of SQL based on SQL syntax objects
 * (not used at the moment)
 *
 * @author guyadong
 * @since 3.17.0
 */
public class SqlSyntaxNormalizer extends TablesNamesFinder {
    protected static final ThreadLocal<Boolean> changed = new ThreadLocal<>();

    public SqlSyntaxNormalizer() {
        super();
        init(true);

    }

    /**
     * Returns {@code true} if the statement changes, otherwise returns {@code false}
     */
    public boolean changed() {
        return Boolean.TRUE.equals(changed.get());
    }

    /**
     * Reset thread-local {@link #changed} state
     */
    public SqlSyntaxNormalizer resetChanged() {
        changed.remove();
        return this;
    }
}

