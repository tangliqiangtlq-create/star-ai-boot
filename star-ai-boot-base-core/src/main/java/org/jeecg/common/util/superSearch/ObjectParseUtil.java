//package org.jeecg.common.util.superSearch;
//
//import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
//
///**
// * Determine Type and add query rules
// *
// * @Author Scott
// * @Date February 14, 2019
// */
//public class ObjectParseUtil {
//
//	/**
//	 *
//	 * @param queryWrapper QueryWrapper
//	 * @param name Field name
//	 * @param rule query rule
//	 * @param value Query Condition value
//	 */
//	public static void addCriteria(QueryWrapper<?> queryWrapper, String name, QueryRuleEnum rule, Object value) {
//		if (value == null || rule == null) {
//			return;
//		}
//		switch (rule) {
//		case GT:
//			queryWrapper.gt(name, value);
//			break;
//		case GE:
//			queryWrapper.ge(name, value);
//			break;
//		case LT:
//			queryWrapper.lt(name, value);
//			break;
//		case LE:
//			queryWrapper.le(name, value);
//			break;
//		case EQ:
//			queryWrapper.eq(name, value);
//			break;
//		case NE:
//			queryWrapper.ne(name, value);
//			break;
//		case IN:
//			queryWrapper.in(name, (Object[]) value);
//			break;
//		case LIKE:
//			queryWrapper.like(name, value);
//			break;
//		case LEFT_LIKE:
//			queryWrapper.likeLeft(name, value);
//			break;
//		case RIGHT_LIKE:
//			queryWrapper.likeRight(name, value);
//			break;
//		default:
//			break;
//		}
//	}
//
//}
