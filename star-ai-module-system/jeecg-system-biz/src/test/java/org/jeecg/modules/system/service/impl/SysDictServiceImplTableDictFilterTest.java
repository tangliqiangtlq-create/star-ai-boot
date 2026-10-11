package org.jeecg.modules.system.service.impl;

import org.jeecg.modules.system.service.impl.SysDictServiceImpl.TableDictFilter;
import org.junit.Assert;
import org.junit.Test;

/**
 * Parsing of the dictCode 4th segment (e.g. {@code dcs_currency,code,code,type='1'}).
 *
 * <p>Security contract: only a single {@code col = value} equality may be extracted. Anything else
 * must yield {@code null} so the caller ignores it instead of concatenating raw SQL into the query
 * (the raw {@code filterSql} concat was removed in 1cf862ac / "E2 fix").
 */
public class SysDictServiceImplTableDictFilterTest {

  private static TableDictFilter parse(String filter) {
    return SysDictServiceImpl.parseTableDictFilter(filter);
  }

  @Test
  public void shouldParseSingleEqualityFilter() {
    TableDictFilter f = parse(" type='1'");
    Assert.assertNotNull(f);
    Assert.assertEquals("type", f.column);
    Assert.assertEquals("1", f.value);
  }

  @Test
  public void shouldParseSupportedValueForms() {
    TableDictFilter unquoted = parse("type=1");
    Assert.assertEquals("type", unquoted.column);
    Assert.assertEquals("1", unquoted.value);

    TableDictFilter doubleQuoted = parse("del_flag = \"0\"");
    Assert.assertEquals("del_flag", doubleQuoted.column);
    Assert.assertEquals("0", doubleQuoted.value);

    TableDictFilter text = parse("code='USD'");
    Assert.assertEquals("code", text.column);
    Assert.assertEquals("USD", text.value);

    TableDictFilter empty = parse("type=''");
    Assert.assertEquals("type", empty.column);
    Assert.assertEquals("", empty.value);
  }

  @Test
  public void shouldIgnoreEmptyFilter() {
    Assert.assertNull(parse(null));
    Assert.assertNull(parse(""));
    Assert.assertNull(parse("   "));
  }

  @Test
  public void shouldRejectInjectionAndCompoundConditions() {
    String[] rejected = {
      "type='1' and 1=1",
      "type='1' or '1'='1",
      "type='1'; drop table sys_user",
      "type=1;drop",
      "1=1",
      "type is null",
      "type>1",
      "type != 1",
      "upper(type)='1'",
      "type='1' -- x",
      "type='1' /* x */",
      "type='1') or (1=1",
      "(select min(code) from dcs_currency)='USD'",
      "1type='1'",
      "type='a' 'b'",
      "type=`1`"
    };
    for (String filter : rejected) {
      Assert.assertNull("must be rejected: " + filter, parse(filter));
    }
  }
}