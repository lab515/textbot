package io.github.lab515.textbot.expr;

import java.util.List;
import java.util.Map;

public class Tablet {
  public OP TableExpr;
  public OP LocatorExpr;
  public String TableDef;
  public List<OP> Exprs;
  public List<String> ExprDefs;
  public Map<String, OP> DefMap = null;

  public Tablet() {
  }

  public Tablet(OP table, String tableDef) {
    TableExpr = table;
    TableDef = tableDef;
  }
}
