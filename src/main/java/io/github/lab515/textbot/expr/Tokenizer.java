package io.github.lab515.textbot.expr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Tokenizer {
  public List<OP> sops;
  public int pos;
  public Map<String, Tablet> refChecks; // it logs a stuff
  public Map<String, Tablet> rootChecks;
  public String err = null;

  public boolean isUserCall(String Name) {
    return false;
  }

  public boolean isUserObject(String Name) {
    return false;
  }

  public OP op() {
    return sops.get(pos);
  }

  public OP nextOp() {
    if (pos < sops.size() - 1)
      return sops.get(pos + 1);
    return null;
  }

  public boolean hasNext() {
    return pos < sops.size() - 1;
  }

  public OP nextPrev() {
    if (pos < sops.size() - 1) {
      return sops.get(pos++);
    }
    return null;
  }

  public OP nextNext() {
    if (pos < sops.size() - 1) {
      return sops.get(++pos);
    }
    return null;
  }

  public boolean next() {
    if (pos < sops.size() - 1) {
      pos++;
      return true;
    }
    return false;
  }

  public void prev() {
    if (pos >= 0)
      pos--;
  }

  public boolean expectOp(boolean tableOp) {
    if (pos < sops.size() - 1
            && (tableOp ? sops.get(pos + 1).isTableOp() : sops.get(pos + 1)
            .isOp())) {
      pos++;
      return true;
    }
    return false;
  }

  public boolean expect2(int flag1, int flag2) {
    if (pos < sops.size() - 2 && sops.get(pos + 1).Flag == flag1
            && sops.get(pos + 2).Flag == flag2) {
      pos++;
      return true;
    } else
      return false;
  }

  public boolean expect(int flag) {
    if (pos < sops.size() - 1 && sops.get(pos + 1).Flag == flag) {
      pos++;
      return true;
    }
    return false;
  }

  public Tokenizer(String Src) {
    if (Src != null) {
      sops = new ArrayList<OP>();
      err = _tokenize(Src, sops);
      if (err != null)
        sops = null;
      else {
        pos = -1;
        refChecks = new LinkedHashMap<String, Tablet>();
        rootChecks = null;
      }
    } else {
      sops = null;
    }
  }

  private static String _tokenize(String Source, List<OP> ops) {
    char[] chars = Source.toCharArray();
    int pos = 0;
    int len = chars.length;
    int start = -1;
    int end = 0;
    char c = ' ';
    int state = 0;
    String str = "";
    char h = ' ';
    for (pos = 0; pos < len; pos++) {
      c = chars[pos];
      if (Character.isWhitespace(c))
        continue;
      switch (c) {
        case '\"':
        case '\'':
          state = 0;
          h = c;
          start = end = ++pos;
          for (; pos < len; pos++) {
            c = chars[pos];
            if (state == 1) {
              switch (c) {
                case 'r':
                  c = '\r';
                  break;
                case 'n':
                  c = '\n';
                  break;
                case 'b':
                  c = '\b';
                  break;
                case 't':
                  c = '\t';
                  break;
                case '\"':
                case '\'':
                default:
                  break;
              }
              state = 0;
              chars[end++] = c;
            } else if (c == h) {
              ops.add(new OP(new String(chars, start, end - start),
                      OP.TOKEN_STR));
              start = -1;
              break;
            } else if (c == '\\') {
              state = 1;
            } else {
              chars[end++] = c;
            }
          }
          if (start != -1) {
            return "unexpected string dec";
          }
          break;
        case '(':
        case ')':
        case '[':
        case ']':
        case '{':
        case '}':
          ops.add(new OP(c + "", "()[]{}".indexOf(c)));
          if (c == '{' || c == '}')
            return "{} scope is disabled";
          break;
        case '+':
          ops.add(new OP(c + "", OP.TOKEN_PLU));
          break;
        case '-':
          ops.add(new OP(c + "", OP.TOKEN_MIN));
          break;
        case '*':
          ops.add(new OP(c + "", OP.TOKEN_MUL));
          break;
        case '/':
          ops.add(new OP(c + "", OP.TOKEN_DIV));
          break;
        case '%':
          ops.add(new OP(c + "", OP.TOKEN_MOD));
          break;
        case '|':
          if (pos < len - 1 && chars[pos + 1] == '|') {
            ops.add(new OP("||", OP.TOKEN_OR));
            pos++;
          } else
            ops.add(new OP("|", OP.TOKEN_BOR));
          break;
        case '&':
          if (pos < len - 1 && chars[pos + 1] == '&') {
            ops.add(new OP("&&", OP.TOKEN_AND));
            pos++;
          } else
            ops.add(new OP("&", OP.TOKEN_BAND));
          break;
        case '^':
          ops.add(new OP("^", OP.TOKEN_BNOR));
          break;
        case '>':
        case '<':
          if (pos < len - 1 && chars[pos + 1] == '=') {
            ops.add(new OP(c == '>' ? ">=" : "<=",
                    c == '>' ? OP.TOKEN_GET : OP.TOKEN_LET));
            pos++;
          } else if (pos < len - 1
                  && (chars[pos + 1] == '>' || chars[pos + 1] == '<')) {
            if (chars[pos + 1] == c) {
              ops.add(new OP(c == '>' ? ">>" : "<<",
                      c == '>' ? OP.TOKEN_RIGHT_JOIN
                              : OP.TOKEN_LEFT_JOIN));
            } else {
              ops.add(new OP(c == '>' ? "><" : "<>",
                      c == '>' ? OP.TOKEN_INNER_JOIN
                              : OP.TOKEN_OUTER_JOIN));
            }
            pos++;
          } else {
            ops.add(new OP(c + "", c == '>' ? OP.TOKEN_GT : OP.TOKEN_LT));
          }
          break;
        case '!':
          if (pos < len - 1 && chars[pos + 1] == '=') {
            ops.add(new OP("!=", OP.TOKEN_NEQ));
            pos++;
          } else
            ops.add(new OP("!", OP.TOKEN_SELF_NOT));
          break;
        case '~':
          ops.add(new OP("!", OP.TOKEN_SELF_BNOT));
          break;
        case '=':
          if (pos < len - 1 && chars[pos + 1] == '=') {
            ops.add(new OP("=", OP.TOKEN_EQ));
            pos++;
          } else
            ops.add(new OP("=", OP.TOKEN_EQ));
          break;
        case ':':
          ops.add(new OP(":", OP.TOKEN_DEC));
          break;
        case '?':
          ops.add(new OP(":", OP.TOKEN_QM));
          break;
        case '@':
          ops.add(new OP("@", OP.TOKEN_LOCA));
          break;
        case ',':
          ops.add(new OP(",", OP.TOKEN_CM));
          break;
        case '.':
          // could be a double
          if (pos < len - 1 && Character.isDigit(chars[pos + 1])) {
            // read all digits
            start = pos++;
            for (; pos < len; pos++) {
              c = chars[pos];
              if (!Character.isDigit(c)) {
                if (!Character.isWhitespace(c))
                  start = -1;
                break;
              }
            }
            if (start == -1) {
              return "invalid number format";
            }
            ops.add(new OP("0" + new String(chars, start, pos - start),
                    OP.TOKEN_NUM));
            if (pos != len)
              pos--;
          } else {
            ops.add(new OP(".", OP.TOKEN_MEMBER));
          }
          break;
        default:
          if (Character.isLetter(c) || c == '_') {
            start = pos++;
            for (; pos < len; pos++) {
              ;
              c = chars[pos];
              if (!(Character.isLetter(c) || Character.isDigit(c) || c == '_')) {
                break;
              }
            }
            str = new String(chars, start, pos - start);
            if (str.equals("in"))
              ops.add(new OP(str, OP.TOKEN_IN));
            else if (str.equals("like"))
              ops.add(new OP(str, OP.TOKEN_LIKE));
            else {
              if (str.equals("true") || str.equals("false"))
                ops.add(new OP(str, OP.TOKEN_BOOL));
              else if (str.equals("null"))
                ops.add(new OP(str, OP.TOKEN_VOID));
              else
                ops.add(new OP(str, OP.TOKEN_VAR));
            }
            if (pos != len)
              pos--;
          } else if (Character.isDigit(c)) {
            start = pos++;
            state = 0;
            for (; pos < len; pos++) {
              c = chars[pos];
              if (Character.isDigit(c)) {
                if (state == 1)
                  state = 2;
                continue;
              }
              if (c == '.') {
                if (state != 0)
                  break;
                state = 1;
                continue;
              }
              if (Character.isLetter(c))
                start = -1;
              break;
            }
            if (start == -1)
              return "invalid number format";
            if (state == 1)
              ops.add(new OP(new String(chars, start, pos - start)
                      + "0", OP.TOKEN_NUM));
            else
              ops.add(new OP(new String(chars, start, pos - start),
                      OP.TOKEN_NUM));
            if (pos != len)
              pos--;
          } else
            return "unexpected token";
          break;
      }
    }
    return null;
  }
}