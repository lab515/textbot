package io.github.lab515.textbot.expr;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Parser {
  private static Map<String, String> _allConfigs = new LinkedHashMap<String, String>();
  private static Tablet _invalidTable = new Tablet();
  private static String _escapeString = "().:,$; \n\t";

  private StringBuilder err = new StringBuilder();

  public static List<String> parseInLines(String source) {
    // preprocess the source code, replace the ^
    StringBuilder sb = new StringBuilder();
    StringBuilder sbCmt = new StringBuilder();
    char lineSep = (char) '\n';
    char charEscaper = (char) '^';
    char goSep = (char) '-';
    char cmtSep = (char) '#';
    List<String> allLines = new ArrayList<String>();
    int lineNum = 1;
    int hitLine = 1;
    int hitCmtLine = 1;
    char lastC = lineSep;
    int pos = 0;
    for (char c : source.toCharArray()) {
      if (c == lineSep)
        lineNum++;
      if (sbCmt.length() > 0) {
        if (c == lineSep) {
          allLines.add("#" + hitCmtLine + "." + sbCmt.toString());
          // FIX: counting line logic, if there is a #comment and this
          // comment happend after hitLine, add a lineSep
          if (hitCmtLine > hitLine && sb.length() > 0) {
            sb.append(lineSep);
          }
          sbCmt.setLength(0);
          lastC = c;
        } else
          sbCmt.append(c);
        continue;
      }

      if (lastC == charEscaper) {
        if (sb.length() == 0)
          hitLine = lineNum;
        if (c == lineSep || c == charEscaper) {
          sb.append(charEscaper);
        } else {
          pos = _escapeString.indexOf(c);
          if (pos >= 0) {
            // special: "().:,\$; \n\t"
            if (pos == 5) // $ must be converted into $$
              sb.append("$$");
            else
              sb.append((char) pos);
          } else
            sb.append(c);
        }
      } else if (lastC == lineSep) {
        if (Character.isWhitespace(c) && c != lineSep)
          continue;
        if (c == goSep) { // try to add it
          if (sb.length() > 0)
            sb.append(lineSep);
        } else {
          if (c == cmtSep) {
            hitCmtLine = lineNum;
            sbCmt.append(c);
            continue;
          }

          if (sb.length() > 0) {
            allLines.add(hitLine + "." + sb.toString());
            sb.setLength(0);
          }

          if (c != lineSep) {
            if (sb.length() == 0)
              hitLine = lineNum;
            sb.append(c);
          }
        }
      } else if (c != lineSep && c != charEscaper) {
        if (c == cmtSep) {
          hitCmtLine = lineNum;
          sbCmt.append(c);
          continue;
        }
        if (sb.length() == 0)
          hitLine = lineNum;
        sb.append(c);
      }
      lastC = c;
    }
    if (sb.length() > 0)
      allLines.add(hitLine + "." + sb.toString());
    if (sbCmt.length() > 0)
      allLines.add("#" + hitCmtLine + "." + sbCmt.toString());
    return allLines;
  }

  public static String readAllText(String filePath, String enc) {
    if (enc == null)
      enc = "utf-8";
    try {
      BufferedReader ins = new BufferedReader(new InputStreamReader(
              new FileInputStream(filePath), enc));
      char[] chars = new char[4096];
      int len = 0;
      StringBuilder ret = new StringBuilder();
      while ((len = ins.read(chars)) > 0) {
        if (chars[0] == (char) 65279 || chars[0] == (char) 65534)
          ret.append(chars, 1, len - 1);
        else
          ret.append(chars, 0, len);
      }
      ins.close();
      if (ret.length() > 0)
        return ret.toString();
      else
        return null;
    } catch (Exception e) {
      return null;
    }

  }

  public static void loadCommonTables(String src) {
    _allConfigs.clear();
    if (src != null) {
      List<String> allLines = parseInLines(src);
      if (allLines == null)
        return;
      int pos = 0;
      for (String line : allLines) {
        if (line.charAt(0) == '#')
          continue; // comments,
        pos = line.indexOf(".");
        line = line.substring(pos + 1);
        if ((pos = line.indexOf('=')) > 0)
          _allConfigs.put(line.substring(0, pos).trim(),
                  line.substring(pos + 1));
      }
    }
  }

  public static void loadCommonTableFile(String path) {
    loadCommonTables(readAllText(path, null));
  }

  private OP setErr(String errInfo) {
    err.append(errInfo);
    err.append("\r\n");
    return null;
  }

  private Tablet setErr2(String errInfo) {
    err.append(errInfo);
    err.append("\r\n");
    return null;
  }

  public String getErr() {
    if (err != null && err.length() > 0)
      return err.toString();
    return null;
  }

  // +, -, *, \, %
  // >, >=, <, <=, =, !=
  /*
   * [ui:table]
   */
  private OP parseVarTable(OP op, Tokenizer st) {
    Tablet ret = st.refChecks.get(op.TkValue); // self top level reference
    if (ret != null)
      return (ret.TableExpr == null ? setErr("ref of parent:"
              + op.TkValue + " is disabled") : op.setTablet(ret));// declared,
    // but
    // not
    // ready
    // yet
    // means
    // it's
    // still
    // in
    // progress

    Map<String, Tablet> rootChecks = st.rootChecks == null ? st.refChecks
            : st.rootChecks;
    ret = rootChecks.get("!" + op.TkValue); // reference to a global var now
    if (ret != null)
      return (ret.TableExpr == null ? setErr("recusive ref:" + op.TkValue
              + " is disabled") : op.setTablet(ret));

    String script = _allConfigs.get(op.TkValue);
    if (script != null) {
      rootChecks.put("!" + op.TkValue, _invalidTable); // avoid deadloop
      Tokenizer n = new Tokenizer(script);
      n.rootChecks = rootChecks; // copy the root check
      if (n.err != null)
        return setErr("tokenization error for lib var:" + op.TkValue
                + ", error: " + n.err);
      ret = parseTablet(n);
      if (ret == null)
        return null;
      rootChecks.put("!" + op.TkValue, ret);
    }
    return op.setTablet(ret);
  }

  // dedicated for textbot only
  public OP parseStdExpr(String constSource) {
    if (constSource == null || (constSource = constSource.trim()).length() < 1) {
      return setErr("empty expression");
    }
    Tokenizer a = new Tokenizer(constSource);
    if (a.err != null) {
      return setErr(a.err);
    }
    OP ret = parseExpr(a, -1, 2);
    if(getErr() == null && a.hasNext())ret = setErr("unexpected tokens: " + a.nextOp().TkValue);
    return ret;
  }

  public static String compileStdExpr(String constSouce) {
    Parser p = new Parser();
    if (p.parseStdExpr(constSouce) == null) return p.getErr();
    else return null;
  }

  public Tablet parse(String source) {
    Tokenizer a = new Tokenizer(source);
    if (a.err != null)
      return setErr2("tokenization error:" + a.err);
    Tablet aa = parseTablet(a);
    if (aa != null && a.hasNext())
      return setErr2("unexpected token left!");
    return aa;
  }

  public static void describe(Tablet tablet, String tabs, StringBuilder buf) {
    OP op = null;
    String str = null;
    buf.append(tabs);
    buf.append("[\r\n");
    tabs += "  ";
    if (tablet.TableDef != null) {
      buf.append(tabs);
      buf.append("table name:");
      buf.append(tablet.TableDef);
      buf.append("\r\n");
    }
    if (tablet.LocatorExpr != null) {
      op = tablet.LocatorExpr;
      buf.append(tabs);
      buf.append("locator:\r\n");
      while (op != null) {
        buf.append(tabs);
        buf.append("  ");
        buf.append(op.TkValue);
        buf.append("\r\n");
        op = op._next;
      }
    }
    op = tablet.TableExpr;
    buf.append(tabs);
    buf.append("table expr:\r\n");
    while (op != null) {
      if (op.Flag == OP.TOKEN_TABLET)
        describe(op.Tblet, tabs + "  ", buf);
      else {
        buf.append(tabs);
        buf.append("  ");
        buf.append(op.TkValue);
        buf.append("\r\n");
      }
      op = op._next;
    }
    if (tablet.Exprs != null) {
      buf.append(tabs);
      buf.append("field exprs:\r\n");
      for (int i = 0; i < tablet.Exprs.size(); i++) {
        op = tablet.Exprs.get(i);
        if (op._last != null && op._last.isBooleanOp())
          continue;
        str = tablet.ExprDefs.get(i);
        buf.append(tabs);
        buf.append("  ");
        buf.append("{\r\n");
        if (str != null) {
          buf.append(tabs);
          buf.append("    ");
          buf.append("name:");
          buf.append(str);
          buf.append("\r\n");
        }
        while (op != null) {
          if (op.Flag == OP.TOKEN_TABLET)
            describe(op.Tblet, tabs + "    ", buf);
          else {
            buf.append(tabs);
            buf.append("    ");
            buf.append(op.TkValue);
            buf.append("\r\n");
          }
          op = op._next;
        }
        buf.append(tabs);
        buf.append("  ");
        buf.append("}\r\n");
      }

      buf.append(tabs);
      buf.append("filter exprs:\r\n");
      for (int i = 0; i < tablet.Exprs.size(); i++) {
        op = tablet.Exprs.get(i);
        if (!(op._last != null && op._last.isBooleanOp()))
          continue;
        buf.append(tabs);
        buf.append("  ");
        buf.append("{\r\n");
        while (op != null) {
          if (op.Flag == OP.TOKEN_TABLET)
            describe(op.Tblet, tabs + "    ", buf);
          else {
            buf.append(tabs);
            buf.append("    ");
            buf.append(op.TkValue);
            buf.append("\r\n");
          }
          op = op._next;
        }
        buf.append(tabs);
        buf.append("  ");
        buf.append("}\r\n");
      }
    }
    buf.append(tabs.substring(2));
    buf.append("]\r\n");
  }

  private OP parseExpr(Tokenizer st, int priority, int tableMode) {// tablemode:
    // 0
    // normal,
    // 1:
    // table,
    // 2:
    // no-table
    if (!st.hasNext())
      return setErr("expect token");
    OP oper1 = st.nextNext();
    if (oper1.Flag == OP.TOKEN_LBK) {
      if (tableMode == 2)
        return setErr("table def is not allowed!");
      st.prev();
      oper1 = OP.getTablet(parseTablet(st)); // could be anything,
    } else if (oper1.Flag <= OP.TOKEN_VAR) {
      // do nothing here!!
    } else if (oper1.Flag == OP.TOKEN_LPR) {
      oper1 = parseExpr(st, -1, tableMode);
      if (oper1 != null && !st.expect(OP.TOKEN_RPR))
        return setErr("expect a closed )");
    } else if (oper1.Flag == OP.TOKEN_SELF_NOT
            || oper1.Flag == OP.TOKEN_SELF_BNOT) {
      oper1 = new OP("nop", OP.TOKEN_NOP);
      st.prev();
    } else if (oper1.Flag == OP.TOKEN_MIN || oper1.Flag == OP.TOKEN_PLU) {
      oper1.Flag = oper1.Flag == OP.TOKEN_MIN ? OP.TOKEN_SELF_NEGA
              : OP.TOKEN_SELF_POSI;
      oper1 = new OP("nop", OP.TOKEN_NOP);
      st.prev();
    } else
      // { support? map? array? all in one
      return setErr("unexpected token " + oper1.TkValue);
    if (oper1 == null || !st.hasNext())
      return oper1; // done!
    OP oper2 = null, para = null, op = null;
    while (st.hasNext()) {// expect a operator now
      op = st.nextOp();
      if (op.Flag == OP.TOKEN_LPR)
        op = new OP("$", OP.TOKEN_CALL);// && priority <= (OP.TOKEN_CALL
        // & OP.TOKEN_PRIORITY)) // a
        // func call , disable the
        // member (.) function for now
      else if (tableMode == 1 && oper1.Flag == OP.TOKEN_VAR
              && oper1._next == null && parseVarTable(oper1, st) == null)
        return null; // table mode, need to check on the tablespace
      else if (op.isEndOp())
        break;
      if (!(tableMode == 1 ? op.isTableOp() : op.isOp()))
        return setErr("expect a expr operator");
      if (op.priority() <= priority)
        break;
      st.next();
      if (op.Flag == OP.TOKEN_QM) { // ?, match the :
        if ((oper2 = parseExpr(st, -1, tableMode)) == null
                || !st.expect(OP.TOKEN_DEC))
          return setErr(oper2 == null ? "expect expression"
                  : "expect : operator");
        oper1.operate(op.set(OP.TOKEN_JMPF, "jmpf"),
                new OP(oper2.getCount() + 1 + "", OP.TOKEN_NUM))
                .operate(new OP("", OP.TOKEN_JMP), oper2);
        if ((oper2 = parseExpr(st, -1, tableMode)) == null)
          return null;
        oper1._last.TkValue = oper2.getCount() + 1 + "";
        op = new OP("dum", OP.TOKEN_DUM);
      } else if (op.Flag == OP.TOKEN_CALL) {// ok, process the parameters
        // if it's a call
        if (oper1._last != null && oper1._last.Flag == OP.TOKEN_MEMBER)
          oper1._last.set(OP.TOKEN_MEMBER3, "member3");
        else if (oper1._last == null && oper1.Flag == OP.TOKEN_VAR)
          oper1.Flag = OP.TOKEN_STR;
        else
          return setErr("invalid func name to call");
        oper2 = null;
        while (true) {
          if (!st.hasNext())
            return setErr("expect token as parameter");
          if (st.nextOp().Flag == OP.TOKEN_RPR && oper2 == null)
            break;
          if ((para = parseExpr(st, -1, tableMode)) == null)
            return null;
          if (oper2 == null)
            oper2 = para;
          else
            oper2.operate(new OP("para", OP.TOKEN_PARA), para);
          if (!st.expect(OP.TOKEN_CM))
            break;
        }
        if (oper2 == null)
          oper2 = new OP("nop", OP.TOKEN_NOP);
        if (!st.expect(OP.TOKEN_RPR))
          return setErr("exepct ) at end of func call");
      } else {
        if ((oper2 = parseExpr(st, op.priority(), tableMode)) == null)
          return null;
        if (op.Flag == OP.TOKEN_MEMBER) {
          if (oper2._last != null || oper2.Flag != OP.TOKEN_VAR)
            return setErr("invalid member name!");
          oper2.Flag = OP.TOKEN_STR;
        } else if (op.Flag == OP.TOKEN_AND || op.Flag == OP.TOKEN_OR) {
          oper1.operate(op.Flag == OP.TOKEN_AND ? new OP("jmpf",
                          OP.TOKEN_JMPF) : new OP("jmpt", OP.TOKEN_JMPT),
                  new OP(oper2.getCount() + 1 + "", OP.TOKEN_NUM));
        }
      }
      oper1.operate(op, oper2);
    }
    return oper1;
  }

  private Tablet parseTablet(Tokenizer st) {
    OP oper = null;
    if (!st.expect(OP.TOKEN_LBK) || !st.hasNext())
      return setErr2("expect [name at beginning");
    Tablet tablet = new Tablet();
    if (((st.expect2(OP.TOKEN_VAR, OP.TOKEN_DEC) || (st.expect2(
            OP.TOKEN_VAR, OP.TOKEN_LOCA) && (oper = st.nextOp()) != null)) && (tablet.TableDef = st
            .nextPrev().TkValue) != null)
            || (st.expect(OP.TOKEN_LOCA) && (oper = st.nextOp()) != null)) {
      if (oper != null
              && ((tablet.LocatorExpr = this.parseExpr(st, -1, 2)) == null || !st
              .expect(OP.TOKEN_DEC)))
        return setErr2("invalid location def for table");
    }
    if ((tablet.TableExpr = parseExpr(st, -1, 1)) == null)
      return null;
    if (tablet.TableDef != null)
      st.refChecks.put(tablet.TableDef, _invalidTable);
    tablet.Exprs = new ArrayList<OP>();
    tablet.ExprDefs = new ArrayList<String>();
    tablet.DefMap = new LinkedHashMap<String, OP>();
    String n = null;
    while (true) {
      if (!st.hasNext())
        return setErr2("expect more tokens");
      if (st.expect(OP.TOKEN_RBK))
        break;
      if (!st.expect(OP.TOKEN_CM))
        return setErr2("expect , in table def");
      tablet.ExprDefs
              .add(st.expect2(OP.TOKEN_VAR, OP.TOKEN_DEC) ? (n = st
                      .nextPrev().TkValue) : (n = null)); // add alter
      if ((oper = parseExpr(st, -1, 0)) == null)
        return null;
      tablet.Exprs.add(oper);
      if (n != null)
        tablet.DefMap.put(n, oper);
    }
    if (tablet.TableExpr._next == null
            && tablet.TableExpr.Flag == OP.TOKEN_TABLET
            && tablet.TableDef == null && tablet.Exprs.size() == 0)
      return tablet.TableExpr.Tblet; // ADD special: [a] (if a is a
    // table, and fine the reference
    if (tablet.TableDef != null)
      st.refChecks.put(tablet.TableDef, tablet);
    return tablet;
  }

  public static void main(String[] args) {
    Parser p = new Parser();
    p.parse("[a:a,a:[a]]");
  }
}
