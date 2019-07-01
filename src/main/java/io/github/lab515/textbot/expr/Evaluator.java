package io.github.lab515.textbot.expr;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * the expression evaluation support, it reduce the operator support to only common usage
 *
 * @author mpeng Success Factors
 */
public class Evaluator {
  private OP cachedObj = null;
  private String cachedExp = null;
  private String err = null;

  /**
   * get boolean value from some string
   *
   * @param data
   * @return
   */
  public static boolean getBoolean(String data) {
    if (data == null)
      return false;
    return !(data.equals("n/a") || data.equals("null") || data.equals("false") || data.equals("") || data.equals(
            "0"));
  }

  /**
   * clear te error
   */
  private void clearError() {
    err = null;
  }

  /**
   * get primitive number from a string
   *
   * @param data
   * @return
   */
  public static long getNumber(String data) {
    if (data == null)
      return 0;
    try {
      return Long.parseLong(data);
    } catch (Exception e) {
      return 0;
    }
  }

  private String setErr(String e) {
    err = e;
    return null;
  }

  /**
   * get number Object(Integer) from a string
   *
   * @param data
   * @return
   */
  public static Long getNumber2(String data) {
    if (data == null)
      return null;
    try {
      return Long.parseLong(data);
    } catch (Exception e) {
      return null;
    }
  }

  public static String compileExpr(String src) {
    return Parser.compileStdExpr(src);
  }

  /**
   * evaluate as boolean value
   *
   * @param data
   * @param variables
   * @return
   */
  public boolean evaluateAsBool(String data, Map variables, Object uo) {
    return getBoolean(evaluate(data, variables, uo));
  }

  public boolean evaluateAsBool(String data, Map variables) {
    return getBoolean(evaluate(data, variables, null));
  }

  public String getLastError() {
    return err;
  }

  public String evaluate(String data, Map variables) {
    return evaluate(data, variables, null);
  }

  /**
   * evaluate a expression
   *
   * @param data
   * @param variables
   * @return
   */
  public String evaluate(String data, Map variables, Object uo) {
    boolean hasCache = data.equals(cachedExp);
    clearError();
    if (!hasCache) {
      Parser p = new Parser();
      cachedObj = p.parseStdExpr(data);
      cachedExp = data;
      if (cachedObj == null) return setErr("invalid expression: " + p.getErr());
    } else if (cachedObj == null) return null;
    return evaluate2(cachedObj, variables, uo);
  }

  /**
   * evaluate the expression object
   *
   * @param p
   * @param variables
   * @return
   */
  public String evaluate2(OP p, Map variables, Object uo) {
    clearError();
    if (p == null)
      return setErr("empty expression!");
    err = null;
    Oper c = null;
    Oper v1 = null, v2 = null;
    long v3 = 0;
    Object ov = null;
    List<Oper> paras = new LinkedList<Oper>();
    Long v4 = null;

    while (p != null) {
      if (p.Flag == OP.TOKEN_VAR) {
        if (variables == null || (ov = variables.get(p.TkValue)) == null) {
          accessVariable(p.TkValue,v1 = new Oper("", false, null, null), uo);
          if(v1.err == null && v1.Val == null)v1.Val = "";
          else if(v1.err != null)v1.Val = null;
          v1.RefVar = p.TkValue; // reset it, in case
          v1.Next = c;
          c = v1;
        }else
          c = new Oper(ov.toString(), false, c, p.TkValue);
      } else if (p.Flag == OP.TOKEN_NUM || p.Flag == OP.TOKEN_STR || p.Flag == OP.TOKEN_BOOL) {
        c = new Oper(p.TkValue, p.Flag == OP.TOKEN_NUM, c, null);
      } else if (p.Flag == OP.TOKEN_VOID || p.Flag == OP.TOKEN_NOP) {
        c = new Oper(p.Flag == OP.TOKEN_NOP ? null : "", false, c, null);
      } else {
        v2 = c;
        if (c != null) {
          v1 = c.Next;
          if (v1 != null)
            c = v1.Next;
          else
            c = null;
        }
        if (v1 == null || v2 == null)
          return setErr("stack error");
        if(p.Flag == OP.TOKEN_MEMBER || p.Flag == OP.TOKEN_MEMBER3){
          if(p.Flag == OP.TOKEN_MEMBER && variables != null && (ov = variables.get(v1.RefVar + "." + v2.Val)) != null){
            v2.Val = ov.toString();
            v2.err = null; // regardless
            v2.isNum = false;
          }else {
            v2.Next = null;
            v2.err = null;
            if(v1.RefVar != null && p.Flag == OP.TOKEN_MEMBER)v1.err = v1.RefVar + "." + v2.Val;
            else v1.err = null;
            accessMember(v1.RefVar, v1.err != null ? null : v1.Val, v2.Val, v2, uo);
            if(v2.err != null && v1.err == null)return setErr(v2.err);
            v2.RefVar = v1.err; // set it back
            v1.err = null;
            if(v2.err == null && v2.Val == null)v2.Val = "";
            else if(v2.err != null)v2.Val = null;
          }
        }else {
          if(v1.err != null || v2.err != null)return setErr(v1.err != null ? v1.err : v2.err);
          switch (p.Flag) {
            case OP.TOKEN_AND:
              v2.Val = (getBoolean(v1.Val) && getBoolean(v2.Val)) ? "true" : "false";
              v2.isNum = false;
              break;
            case OP.TOKEN_OR:
              v2.Val = getBoolean(v2.Val) ? "true" : "false";
              v2.isNum = false;
              break;
            case OP.TOKEN_EQ:
            case OP.TOKEN_GET:
            case OP.TOKEN_GT:
            case OP.TOKEN_LET:
            case OP.TOKEN_LT:
            case OP.TOKEN_NEQ:

              if (v1.isNum && v2.isNum)
                v3 = Long.parseLong(v1.Val) - Long.parseLong(v2.Val);
              else if (v1.Val.length() > 0 && v2.Val.length() > 0 && (v4 = getNumber2(v1.Val)) != null) {
                v3 = v4;
                if ((v4 = getNumber2(v2.Val)) != null) {
                  v3 -= v4;
                } else {
                  v3 = v1.Val.compareTo(v2.Val);
                }
              } else
                v3 = v1.Val.compareTo(v2.Val);
              if (v3 == 0) {
                v2.Val = (p.Flag == OP.TOKEN_LET || p.Flag == OP.TOKEN_GET || p.Flag == OP.TOKEN_EQ) ?
                        "true" :
                        "false";
              } else if (v3 > 0) {
                v2.Val = (p.Flag == OP.TOKEN_NEQ || p.Flag == OP.TOKEN_GT || p.Flag == OP.TOKEN_GET) ?
                        "true" :
                        "false";
              } else {
                v2.Val = (p.Flag == OP.TOKEN_NEQ || p.Flag == OP.TOKEN_LT || p.Flag == OP.TOKEN_LET) ?
                        "true" :
                        "false";
              }
              v2.isNum = false;
              break;
            case OP.TOKEN_JMP:
            case OP.TOKEN_JMPF:
            case OP.TOKEN_JMPT:
              if (p.Flag == OP.TOKEN_JMP || (getBoolean(v1.Val) != (p.Flag == OP.TOKEN_JMPF))) {
                v3 = Integer.parseInt(p.Flag == OP.TOKEN_JMP ? p.TkValue : v2.Val);
                while (v3-- > 0)
                  p = p._next;
              }
              //if(p.Flag == OP.TOKEN_JMP)
              v2 = v1; // jump won't cause problem
              break;
            case OP.TOKEN_MUL:
              v2.Val = getNumber(v1.Val) * getNumber(v2.Val) + "";
              v2.isNum = true;
              break;
            case OP.TOKEN_PLU:
              if ((v1.Val.length() > 0 && Character.isDigit(v1.Val.charAt(0))) && (v2.Val.length() > 0 && Character.isDigit(v2.Val.charAt(0)))) {
                v2.Val = getNumber(v1.Val) + getNumber(v2.Val) + "";
                v2.isNum = true;
              } else {
                v2.Val = v1.Val + v2.Val;
                v2.isNum = true;
              }
              break;
            case OP.TOKEN_MIN:
              v2.Val = getNumber(v1.Val) - getNumber(v2.Val) + "";
              v2.isNum = true;
              break;
            case OP.TOKEN_DIV:
            case OP.TOKEN_MOD:
              if ((v3 = getNumber(v2.Val)) == 0)
                return setErr("divide by zero");
              v2.Val = (getNumber(v1.Val) / v3) + "";
              v2.isNum = true;
              break;
            case OP.TOKEN_DUM:
              break; // keep the v23
            case OP.TOKEN_SELF_NOT:
              v2.Val = getBoolean(v2.Val) ? "false" : "true";
              v2.isNum = false;
              break;
            case OP.TOKEN_SELF_BNOT:
              v2.Val = ~getNumber(v2.Val) + "";
              v2.isNum = true;
              break;
            case OP.TOKEN_SELF_NEGA:
              v2.Val = -getNumber(v2.Val) + "";
              v2.isNum = true;
              break;
            case OP.TOKEN_SELF_POSI:
              v2.Val = getNumber(v2.Val) + "";
              v2.isNum = true;
              break;
            case OP.TOKEN_PARA:
              if (v1.Val != null) // already paraed
              {
                c = new Oper(null, false, c, null);
                v1.Next = c;
                c = v1;
                v1 = new Oper(null, false, null, null);
              }
              v2.Next = c;
              c = v2;
              v2 = v1;
              v2.RefVar = ""; // a temp solution, distinguish between nOP
              // and paraed NOP
              break;
            case OP.TOKEN_CALL:
              paras.clear();
              if (v2.Val == null) { // NOP?
                if (v2.RefVar != null) {
                  // pop all stuff
                  paras.add(0, v1);
                  v1 = c;
                  while (v1 != null && v1.Val != null) {
                    paras.add(0, v1);
                    v1 = v1.Next;
                  }
                  if (v1 == null || v1.Next == null)
                    return setErr("internal error");
                  v1 = v1.Next;
                  c = v1.Next;
                }
              } else
                paras.add(v2);
              v2 = v1;
              processAPI(v1.RefVar == null ? v1.Val : v2.RefVar, paras, v2, uo);
              if(v2.err != null)return setErr(v2.err);
              if (v2.Val == null)
                v2.Val = "";
              break;
            default:
              return setErr("invalid/unsupported operator!");
          }
        }
        v2.Next = c;
        c = v2;
      }
      p = p._next;
    }
    if(c.err != null)return setErr(c.err);
    else return c.Val;
  }

  protected void accessMember(String varName, String var, String memberName, Oper out, Object uo) {
    if (varName != null && var == null && memberName != null) {
      accessVariable(varName + "." + memberName,out,uo);
    } else {
      out.err = "not defined: " + varName + "." + memberName;
    }
  }

  /**
   * access the context variable
   *
   * @param varName
   * @param out
   *
   */
  protected void accessVariable(String varName, Oper out, Object uo) {
    out.err = "not defined: " + varName;
  }

  /**
   * API support for expression
   *
   * @param apiName
   * @param paras
   * @param out
   */
  protected void processAPI(String apiName, List<Oper> paras, Oper out, Object uo) {
    out.err = "not defined api:" + apiName;
  }

  public static void main(String[] args) throws Exception {
    Object o = "aa.bb".split(".");
    Evaluator c = new Evaluator();
    Map s = new java.util.LinkedHashMap<String, String>();
    s.put("kf_messages", "4265000");
    s.put("kf_load", "4292321125");
    s.put("kf_time", "98638172556");
    //case.status < 0 || (case.status > 0 && passedDays(exec.qaautocand.rundate) >= 1) || (case.status = 0 && passedDays(exec.qaautocand.rundate) > 6)
    System.out.println(c.evaluate("aa.aa",null));//
            //" kf_messages * 1000 / (kf_time / 1000000) + \" messages/s\"", s, null));
  }
}