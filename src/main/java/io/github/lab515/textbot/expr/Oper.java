package io.github.lab515.textbot.expr;

public class Oper {
    public String Val = null;
    public boolean isNum = false;
    public String RefVar = null;
    public Oper Next = null;

    public Oper(String val, boolean isNumber, Oper next, String refV) {
        Val = val;
        Next = next;
        isNum = isNumber;
        RefVar = refV;
    }
}