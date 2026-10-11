package org.jeecg.common.util.sqlInjection;

import net.sf.jsqlparser.parser.CCJSqlParserDefaultVisitor;
import net.sf.jsqlparser.parser.SimpleNode;
import net.sf.jsqlparser.statement.select.UnionOp;
import org.jeecg.common.exception.JeecgSqlInjectionException;

/**
 * Implementation of injection attack analysis based on abstract syntax tree (AST)
 *
 * @author guyadong
 */
public class InjectionAstNodeVisitor extends CCJSqlParserDefaultVisitor {
    public InjectionAstNodeVisitor() {
    }

    /**
     * Process prohibited UNION query
     *
     * @param node
     * @param data
     * @return
     */
    @Override
    public Object visit(SimpleNode node, Object data) {
        Object value = node.jjtGetValue();
        if (value instanceof UnionOp) {
            throw new JeecgSqlInjectionException("DISABLE UNION");
        }
        return super.visit(node, data);
    }
}

