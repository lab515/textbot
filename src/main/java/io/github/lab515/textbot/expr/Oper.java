package io.github.lab515.textbot.expr;

public class Oper {
  public String Val;
  public boolean isNum;
  public String RefVar;
  public Oper Next;
  public String err;
  public Oper(String val, boolean isNumber, Oper next, String refV) {
    Val = val;
    Next = next;
    isNum = isNumber;
    RefVar = refV;
    err = null;
  }
}