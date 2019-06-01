package io.github.lab515.textbot.expr;

class OP {
    public static final int TOKEN_VAR = -1;
    public static final int TOKEN_STR = -2;
    public static final int TOKEN_NUM = -3;
    public static final int TOKEN_BOOL = -4;
    public static final int TOKEN_TABLET = -5;
    public static final int TOKEN_NOP = -6;
    public static final int TOKEN_VOID = -7;
    // public static final TOKEN_IN_MEMORY = -8 // this is a special flag
    // returned by user, means it must be processed in memory

    public static final int TOKEN_LPR = 0; // (
    public static final int TOKEN_RPR = 1; // )
    public static final int TOKEN_LBK = 2; // [
    public static final int TOKEN_RBK = 3; // ]
    public static final int TOKEN_LBR = 4; // {
    public static final int TOKEN_RBR = 5; // }

    public static final int TOKEN_PARA = 6; // internal op

    public static final int TOKEN_MINOP = 0x100; //

    public static final int TOKEN_QM = 0x100; // ? : match

    public static final int TOKEN_OR = 0x201; //
    public static final int TOKEN_AND = 0x301; //

    public static final int TOKEN_BOR = 0x401; //
    public static final int TOKEN_BNOR = 0x501; //
    public static final int TOKEN_BAND = 0x601; //

    public static final int TOKEN_NEQ = 0x701; //
    public static final int TOKEN_EQ = 0x702; //

    public static final int TOKEN_IN = 0x801; //
    public static final int TOKEN_LIKE = 0x802; //

    public static final int TOKEN_GT = 0x0901; //
    public static final int TOKEN_GET = 0x902; //
    public static final int TOKEN_LT = 0x903; //
    public static final int TOKEN_LET = 0x904; //

    public static final int TOKEN_PLU = 0xA01; //
    public static final int TOKEN_MIN = 0xA02; //
    public static final int TOKEN_MUL = 0xB01; //
    public static final int TOKEN_DIV = 0xB02; //
    public static final int TOKEN_MOD = 0xB03; //

    public static final int TOKEN_SELF_NOT = 0xC01; //
    public static final int TOKEN_SELF_BNOT = 0xC02; //
    public static final int TOKEN_SELF_POSI = 0xC03;
    public static final int TOKEN_SELF_NEGA = 0xC04;

    public static final int TOKEN_CALL = 0xD01; // EX_CALL is nothing different
    // than CALL, but it can not
    // return any value
    public static final int TOKEN_MEMBER = 0xD02; //
    public static final int TOKEN_MEMBER3 = 0xD03; //

    public static final int TOKEN_MAXOP = 0xD03; //

    public static final int TOKEN_MINTABLE = 0xE01;

    public static final int TOKEN_INNER_JOIN = 0xE02; // below 4 operators are
    // only applied to table
    // definition!!!!
    public static final int TOKEN_OUTER_JOIN = 0xE03; //
    public static final int TOKEN_LEFT_JOIN = 0xE04; //
    public static final int TOKEN_RIGHT_JOIN = 0xE05; //

    public static final int TOKEN_MAXTABLE = 0xE05;//

    public static final int TOKEN_DEC = 0x1001; // a special stuff
    public static final int TOKEN_CM = 0x1002;
    public static final int TOKEN_JMPF = 0x1003;
    public static final int TOKEN_JMP = 0x1004;
    public static final int TOKEN_DUM = 0x1005;
    public static final int TOKEN_JMPT = 0x1006;
    public static final int TOKEN_LOCA = 0x1007;

    public static final int TOKEN_PRIORITY = 0xFF00;
    public static final int TOKEN_IDX = 0xFF;

    public int Flag = 0;
    public String TkValue = null;
    public Tablet Tblet = null;

    // for expression!!!!
    public OP _next = null;
    public OP _last = null;

    public OP set(int Flg, String Val) {
        Flag = Flg;
        TkValue = Val;
        return this;
    }

    public OP operate(OP Op, OP Oper2) {
        if (Op._next != null)
            return this;
        Op._next = null;
        Op._last = this;

        (_next != null ? _last : this)._next = Oper2;
        _last = Oper2._next != null ? Oper2._last : Oper2;
        _last._next = Op;
        _last = Op;
        return this;
    }

    public OP(String val, int flag) {
        TkValue = val;
        Flag = flag;
    }

    public OP() {
        TkValue = null;
        Flag = TOKEN_NOP;
    }

    public int getCount() {
        int ret = 1;
        OP r = _next;
        while (r != null) {
            ret++;
            r = r._next;
        }
        return ret;
    }

    public OP(Tablet tablet, int flag) {
        Tblet = tablet;
        Flag = flag;
    }

    public String toString() {
        return TkValue;
    }

    public OP setTablet(Tablet tablet) {
        if (tablet != null) {
            Tblet = tablet;
            Flag = TOKEN_TABLET;
        }
        return this;
    }

    public static OP getTablet(Tablet tablet) {
        if (tablet == null)
            return null;
        return new OP(tablet, TOKEN_TABLET);
    }

    public static int getPriority(int pr) {
        return pr & TOKEN_PRIORITY;
    }

    public int priority() {
        return Flag & TOKEN_PRIORITY;
    }

    public boolean isOper() {
        return Flag < 0;
    }

    public boolean isBooleanOp() {
        return Flag >= TOKEN_OR && Flag <= TOKEN_LET;
    }

    public boolean isOp() {
        return TOKEN_MINOP <= Flag && Flag <= TOKEN_MAXOP;
    }

    public boolean isTableOp() {
        return (TOKEN_MINTABLE <= Flag && Flag <= TOKEN_MAXTABLE)
                || isExTableOp() || Flag == TOKEN_CALL || Flag == TOKEN_MEMBER;
    }

    public boolean isExTableOp() {
        return Flag == TOKEN_MIN;
    }

    public boolean isEndOp() {
        return Flag == TOKEN_RPR || Flag == TOKEN_CM || Flag == TOKEN_DEC
                || Flag == TOKEN_RBK || Flag == TOKEN_RBR; // add ; in the
        // future
    }
}