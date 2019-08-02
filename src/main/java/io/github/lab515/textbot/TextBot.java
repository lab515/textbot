package io.github.lab515.textbot;

import io.github.lab515.textbot.expr.Evaluator;
import io.github.lab515.textbot.utils.FileUtils;
import io.github.lab515.textbot.expr.Oper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TextBot extends Evaluator {
  // static final int TB_MACRO = -1
  //public static final int TB_COMMAND_AUTO = 0x40000; // REMOVE AUTO flag, not making sense anymore
  public static final int TB_COMMAND_SYSTEM = 0x20000;
  public static final int TB_COMMAND_PAGE = 0x10000;
  public static final int TB_COMMAND_NORMAL = 0x0;

  private static final int TB_COMMAND_FLAGS = TB_COMMAND_NORMAL | TB_COMMAND_PAGE | TB_COMMAND_SYSTEM;

  private static final int TB_COMMAND_MAX = 0xFFFF;
  // we only support 65535 commands at most!!
  private static final int TB_COMMENT = 0;
  private static final int TB_VAR = 1;
  private static final int TB_CALL = 2;
  private static final int TB_INVOKE = 3;
  private static final int TB_RETURN = 4;
  private static final int TB_GOTO = 5;
  private static final int TB_DEFPARA = 6;
  private static final int TB_PARA = 7;
  private static final int TB_META = 8; // for element, page definition, we
  private static final int TB_ASSERT = 9;

  private static final int TB_CMD_MAX = 9;
  private static final int TB_META_MAX = 2;
  private static final int TB_KEYWORD_MAX = 11;
  private static final int TB_INTENRAL_VAR_MAX = 3;

  private static final String _DEFAULT = "default";
  // also track all the info into code
  // (as well as metaInfos)

  // //////////// meta data declaration part ////////////////
  private Map<String, Integer> supportedMetas;

  private Map<String, Integer> preservedKeywords;

  private static String _escapeString = "().:,$; \n\t";

  private Map<String, Integer> internalVars;

  private Map<String, Integer> supportedCommands;
  //private boolean autoRefreshCommands = false;

  // could be page.action, or action name
  private Map<String, Integer> supportedActions; // for customized
  // actions(functions)

  // ///////// meta data declaration end ///////////////

  private void addSupportedMeta(String meta) throws Exception {
    addInternalDefinition(meta, 2, supportedMetas, TB_META_MAX, null);
  }

  private void addPreservedKeyword(String key) throws Exception {
    addInternalDefinition(key, TB_KEYWORD_MAX + 1, preservedKeywords, TB_KEYWORD_MAX, null);
  }

  private void removePreservedKeyword(String key) throws Exception {
    removeDefinition(key, preservedKeywords, TB_KEYWORD_MAX);
  }

  private void removeSupportedMeta(String meta) throws Exception {
    removeDefinition(meta, supportedMetas, TB_META_MAX);
  }

  private void addSupportedContextVar(String varName) throws Exception { // change(2010/12/22)
    addExternalDefinition(varName, 4, internalVars, TB_INTENRAL_VAR_MAX, null);
  }

  private void removeSupportedContextVar(String varName) throws Exception {
    removeDefinition(varName, internalVars, TB_INTENRAL_VAR_MAX);// change(2010/12/22)
  }

  private void addSupportedAction(String actionName) throws Exception {
    if (supportedActions.size() >= TB_COMMAND_MAX)
      return;
    addExternalDefinition(actionName, supportedActions.size(), supportedActions, -1, supportedCommands);
  }

  private void removeSupportedAction(String actionName) throws Exception {
    removeDefinition(actionName, supportedActions, -1);
  }

  private void addExternalDefinition(String name, int val,
                                     Map<String, Integer> attrs, int threshold, Map<String, Integer> recheck) throws Exception {
    if (bot != null) throw new Exception("Error: only for execution, not enabled with compiling change!");
    if (botCodes != null) throw new Exception("Error: textbot inited can not be modified!");
    if (name == null && name.length() < 1) throw new Exception("Error: empty text meta word!");
    name = name.toLowerCase();
    if (!isValidName(name)) throw new Exception("Error: invalid text meta word:" + name);
    // check if it exists
    if (recheck != null && recheck.containsKey(name))
      throw new Exception("Error: text metaword already registered in other category: " + name);
    if (attrs.containsKey(name)) {
      if (threshold < 0) throw new Exception("Error: duplicated text meta word: " + name);
      if ((attrs.get(name) & TB_COMMAND_MAX) <= threshold)
        throw new Exception("Error: internal text meta word can not be overwritten:" + name);
    }
    attrs.put(name, val);
  }

  private void addInternalDefinition(String name, int val,
                                     Map<String, Integer> attrs, int overwriteThreshold, Map<String, Integer> recheck) throws Exception {
    if (bot != null) throw new Exception("Error: only for execution, not enabled with compiling change!");
    if (botCodes != null) throw new Exception("Error: textbot inited can not be modified!");
    if (name == null && name.length() < 1) throw new Exception("Error: empty name for add text meta word!");
    boolean hasNumbers = false;
    boolean hasLetters = false;
    for (char c : name.toCharArray()) {
      if (!Character.isLetter(c)) {
        if (!Character.isDigit(c))
          throw new Exception("Error: invalid name for add text meta word:" + name);
        if (!hasLetters)
          throw new Exception("Error: invalid name for add text meta word:" + name);
        hasNumbers = true;
      } else if (hasNumbers)
        throw new Exception("Error: invalid name for add text meta word:" + name);
      else
        hasLetters = true;
    }
    name = name.toLowerCase();
    // check if it exists
    if (recheck != null && recheck.containsKey(name))
      throw new Exception("Error: text metaword already registered in other category: " + name);
    if (attrs.containsKey(name)) {
      if (overwriteThreshold < 0) throw new Exception("Error: duplicated text metaword:" + name);
      if ((attrs.get(name) & TB_COMMAND_MAX) <= overwriteThreshold) {
        throw new Exception("Error: internal text meta word can not be overwritten:" + name);
      }
    }
    attrs.put(name, val);
  }

  private void removeDefinition(String name, Map<String, Integer> attrs,
                                int limit) throws Exception {
    if (bot != null) throw new Exception("Error: only for execution, not enabled with compiling change!");
    if (botCodes != null) throw new Exception("Error: textbot inited can not be modified!");
    if (name == null && name.length() < 1) throw new Exception("Error: empty meta word for remove!");
    name = name.toLowerCase();
    if (attrs.containsKey(name)) {
      if (attrs.get(name) > limit) attrs.remove(name);
      else throw new Exception("Error: internal text meta word can not be removed: " + name);
    }
  }

  private void addSupportedCommand(String cmd) throws Exception {
    addSupportedCommand(cmd, 0);
  }

  private void addSupportedCommand(String cmd, int cmdType) throws Exception {
    if (supportedCommands.size() >= TB_COMMAND_MAX)
      return;
    cmdType = (cmdType & 0xF0000);
    addInternalDefinition(cmd, (cmdType | (TB_CMD_MAX + 1)), // id doesn't means anything
            supportedCommands, TB_CMD_MAX, supportedActions);
  }

  public void removeSupportedCommand(String cmd) throws Exception {
    removeDefinition(cmd, supportedCommands, TB_CMD_MAX);
  }

  private boolean isValidVarName(String name) {
    if (name == null)
      return false;
    name = name.toLowerCase();
    return isValidName(name) && !internalVars.containsKey(name) && !supportedMetas.containsKey(name);
  }

  private TextBot(Bot d) {
    bot = d;
  }

  private TextBot() {
    // log = logger // only for logging purpose
    supportedMetas = new LinkedHashMap<String, Integer>();
    supportedMetas.put("timeout", 0);
    supportedMetas.put("root", 1);
    supportedMetas.put("import", 2); // import can not be used as well
    preservedKeywords = new LinkedHashMap<String, Integer>();
    preservedKeywords.put(_DEFAULT, 1);
    preservedKeywords.put("mode", 2);
    preservedKeywords.put("import", 3);
    preservedKeywords.put("true", 4);
    preservedKeywords.put("false", 5);
    preservedKeywords.put("_main_", 6);
    preservedKeywords.put("null", 7);
    preservedKeywords.put("var", 8);
    preservedKeywords.put("return", 9);
    preservedKeywords.put("goto", 10);
    preservedKeywords.put("assert", 11);
    internalVars = new LinkedHashMap<String, Integer>();
    internalVars.put("retval", 0);
    internalVars.put("last", 1);
    internalVars.put("home", 2);
    internalVars.put("page", 3);// change(2010/12/22)
    // "url":3,
    // "bodytext":"true",
    // "source":"true",
    // "title":"true",
    // "timeout":"true"
    supportedCommands = new LinkedHashMap<String, Integer>();
    supportedCommands.put("var", TB_VAR | TB_COMMAND_SYSTEM);
    supportedCommands.put("return", TB_RETURN | TB_COMMAND_SYSTEM);
    supportedCommands.put("goto", TB_GOTO | TB_COMMAND_SYSTEM);
    supportedCommands.put("assert", TB_ASSERT | TB_COMMAND_SYSTEM);
    supportedActions = new LinkedHashMap<String, Integer>();
    bot = null;
  }

  // binaries info
  private List<Code> botCodes = null;
  private Map<String, Object> metaInfos = null; // a hash table: ?: metas
  private List<String> loadedSourceFiles = null;
  private Map<String, String> descInfos = null;


  // runtime meta info, change as demanded
  private Bot bot = null;

  // contextual info, restorable
  private Map<String, String> pageVars = null;
  private Map<String, String> cacheData = null;
  private Context cxt = null; // just to save stuff

  // stuff, global parameters,
  // such as ?timeout, also page
  // element here two

  //private String lastError = null; // error tolerance is disabled

  //private String executionPage = "";
  //private Map<String, String> args = null;


  // @: labels
  // $ and %: action start and end instruments, integer, the index in botCodes
  // !: default parameters start and end instruments , but actually it's
  // deprecated, all def parametes converted into botCode
  // . element definition
  private int getCommandType(String cmd) {
    if (supportedCommands.containsKey(cmd))
      return (supportedCommands.get(cmd) & TB_COMMAND_MAX);
    else
      return -1;
  }

  private int getCommandFlags(String cmd) {
    if (supportedCommands.containsKey(cmd))
      return (supportedCommands.get(cmd) & TB_COMMAND_FLAGS);
    else
      return 0;
  }
  //private boolean isAutoCommand(String cmd) {
  //   return supportedCommands.containsKey(cmd)
  //          && (supportedCommands.get(cmd) & TB_COMMAND_AUTO) != 0;
  // }

  private boolean isSystemCommand(String cmd) {
    return supportedCommands.containsKey(cmd)
            && (supportedCommands.get(cmd) & TB_COMMAND_SYSTEM) != 0;
  }

  private boolean isNormalCommand(String cmd) {
    return supportedCommands.containsKey(cmd)
            && (supportedCommands.get(cmd) & TB_COMMAND_FLAGS) == 0;
  }

  private boolean isPageCommand(String cmd) {
    return supportedCommands.containsKey(cmd)
            && (supportedCommands.get(cmd) & TB_COMMAND_PAGE) != 0;
  }

  // var context supporting methods, NOTE: name is not in lower case now!!
  private String accessVar(String name, String data, String defaultVal, Code code) throws Exception {
    // name could have oper flag
    char flag = name.charAt(0);
    if (flag == '?') name = name.substring(1).trim(); // just in case
    // assume name is valid or "*"
    // ? simply check if a variable exists!!
    boolean haveData = data != null;
    String stdName = name.toLowerCase();
    boolean pageVar = name.indexOf(".") > 0;
    // check if this is internal vars
    if (internalVars.containsKey(stdName)) {
      if (flag == '?') data = "true";
      else {
        if (stdName.equals("retval")) {
          if (haveData) {
            cxt.retVal = data;
          } else {
            data = cxt.retVal != null ? cxt.retVal : cxt.lastRet;
          }
        } else if (stdName.equals("last")) {
          if (haveData) throw new Exception(getError("Error: last var can not be set ", code));
          data = cxt.lastRet;
        } else if (stdName.equals("home")) {
          if (haveData) throw new Exception(getError("Error: home var can not be set ", code));
          data = getData((String) cxt.metas.get("?" + cxt.currentPage), code, stdName, null);
        } else if (stdName.equals("page")) {
          if (haveData) throw new Exception(getError("Error: page var can not be set ", code));
          data = cxt.currentPage;
        } else {
          if (haveData) data = bot.setContextVar(stdName, data);
          else data = bot.getContextVar(stdName);
        }
      }
    } else if (supportedMetas.containsKey(stdName)) {
      data = getMetaInfo(stdName, code);
      if (data == null) throw new Exception(getError("Error: meta var value is not set: " + stdName, code));
    } else {
      Map<String, String> vars = null;
      if (pageVar) {
        if (pageVars == null) pageVars = new LinkedHashMap<String, String>();
        if (haveData || pageVars.containsKey(stdName)) {
          vars = pageVars;
        }
      } else {
        // note, arg processed differently
        if (haveData || cxt.vars.containsKey(stdName)) {
          vars = cxt.vars;
        }// else if(ct.parentContext != null){
        //   while (ct.parentContext != null) ct = ct.parentContext;
        //   if(ct.vars.containsKey(stdName))vars = ct.vars;
        //}
      }
      if (vars != null) {
        if (flag == '?') data = "true";
        else if (haveData) vars.put(stdName, data);
        else data = vars.get(stdName);
      } else {
        data = bot.getInstantVar(stdName);
        if (flag == '?') data = data != null ? "true" : "false";
        else if (haveData) {
          throw new Exception(getError("Error: instant(external) var can not be set:" + stdName, code));
        } else if (data == null && defaultVal == null)
          throw new Exception(getError("Error: var " + stdName + " doesn't exists!", code));
      }
    }
    if (data == null) data = defaultVal != null ? defaultVal : "";
    return data;
  }

  private String getMetaInfo(String name, Code code) {
    String val = (String) metaInfos.get("@" + name + "." + code.fileIndex);
    if (val == null)
      val = (String) metaInfos.get("@" + name);
    return val;
  }

  private boolean getExprBoolean(String expr, Code code, Map vars) throws Exception {
    return getBoolean(getExpr(expr, code, vars));
  }

  private String getExpr(String expr, Code code, Map vars) throws Exception {
    Context c = cxt;
    Map<String, String> cd = cacheData;
    Map<String, String> pvs = pageVars;
    try {
      String ret = evaluate(expr, vars, code);
      if (getLastError() != null)
        throw new Exception(getError("Error: expression evaluate failure: " + getLastError(), code));
      if (ret == null) ret = "";
      return ret;
    } finally { // restore, since expression might be able to call execute (bad design, but useful)
      cxt = c;
      cacheData = cd;
      pageVars = pvs;
    }
  }

  private String getData(String data, Code code, String fromName, String placeHolder) throws Exception {
    if (data == null) return null;
    if (fromName != null) {
      if (cacheData != null && cacheData.containsKey(fromName)) {
        data = cacheData.get(fromName);
        return data == null ? "{" + fromName + "}" : null;
      } else if (cacheData == null) cacheData = new LinkedHashMap<String, String>();
      cacheData.put(fromName, null); // preset
    }
    String[] arr = ("-" + data + "-").split("\\$");
    String val = null;
    int len = arr.length;
    if (len < 3) return data;
    arr[0] = arr[0].substring(1);
    arr[len - 1] = arr[len - 1].substring(0, arr[len - 1].length() - 1);
    int c = len >> 1;
    String key = null;
    String defVal = null;
    int pos = 0;
    for (int i = 0; i < c; i++) {
      key = arr[i * 2 + 1];
      if (key.length() < 1)
        arr[i * 2 + 1] = "$";
      else if (i * 2 + 1 == len - 1)
        arr[i * 2 + 1] = "$" + arr[i * 2 + 1];
      else {
        // ADD: 2018-12-12
        if (placeHolder != null) arr[i * 2 + 1] = placeHolder;
        else {
          // Add: 2011/7/4: default value support
          // CHANGE: 2018/11/30, default value format:  name:value(expression)
          // ? support as well, so it could be ?name, expression, or name:expression
          key = key.trim();
          if (key.length() < 1) {
            arr[i * 2 + 1] = "";
          } else if (key.charAt(0) == '?') {
            if (isValidName(key)) {
              arr[i * 2 + 1] = accessVar(key, null, null, code);
            } else
              throw new Exception(getError("Error: invalid inline ?varname pattern:" + arr[i * 2 + 1], code));
          } else {
            pos = key.indexOf(':');
            if (pos > 0) {
              defVal = key.substring(pos + 1).trim();
              key = key.substring(0, pos).trim();
              if (!isValidName(key))
                throw new Exception(getError("Error: invalid inline varname:defval pattern:" + arr[i * 2 + 1], code));
              defVal = getExpr(defVal, code, null);
              arr[i * 2 + 1] = accessVar(key, null, defVal, code);
            } else {
              defVal = null;
              key = key.trim();
              if (key.length() < 1) {
                arr[i * 2 + 1] = "";
                continue; // no change
              }
              arr[i * 2 + 1] = getExpr(key, code, null);
            }
          }
        }
      }
    }
    StringBuilder sb = new StringBuilder();
    for (String s : arr)
      sb.append(s);
    data = sb.toString();
    if (fromName != null) cacheData.put(fromName, data);
    return data;
  }

  private static List<String> parseInLines(String source) {
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
    if (sbCmt.length() > 0) allLines.add("#" + hitCmtLine + "." + sbCmt.toString());
    if (sb.length() > 0) allLines.add(hitLine + "." + sb.toString());
    return allLines;
  }

  private List<String> parseLine(String line, boolean forMeta) {// = false
    // try to get the data first , and then parse the rest of stuff, the
    // format could be very easy
    // [command,data]
    // default.wait
    // ?selenium.browserbot.getCurrentWindow().jscoverage_openInWindowButton_click()
    int curPos = 0;
    List<String> ret = new ArrayList<String>();
    char c = 0;
    StringBuilder sb = new StringBuilder();
    char lastC = '\0';
    char dot = (char) '.';
    char leftBk = (char) '[';
    char fc = (char) '_';
    char leftPR = (char) '(';
    char macroSp = (char) '=';
    int pos = 0;
    int pos2 = 0;
    boolean hadLBK = false;
    boolean haveSpace = false;
    while (curPos < line.length()) {
      c = line.charAt(curPos);
      // FIX: no reason
      // curPos++
      if (Character.isWhitespace(c)) {
        if (sb.length() > 0)
          haveSpace = true;
        curPos++;
        continue;
      } else if (leftBk == c && !forMeta) {
        if (ret.size() > 0 && (lastC != dot || hadLBK)) {
          // this is not a valid case, unload
          if (haveSpace && sb.length() > 0) {
            ret.add(sb.toString());
            sb.setLength(0);
          } else
            curPos -= sb.length();
          break;
        }
        hadLBK = true;
        // try to find the ]
        pos = line.indexOf("]", curPos);
        while (pos > 0) {
          pos2 = pos + 1;
          while (pos2 < line.length()) {
            lastC = line.charAt(pos2);
            if (lastC == dot) {
              break;
            } else if (Character.isWhitespace(lastC)) {
            } else {
              // error case
              pos2 = -1;
              break;
            }
            pos2++;
          }
          if (pos2 > 0 && pos2 < line.length()) {
            pos = pos2;
            break;
          }
          pos = line.indexOf("]", pos + 1);
        }
        if (pos > 0 && pos < line.length()) {
          // got it
          if (sb.length() > 0) {
            ret.add(sb.toString());
            sb.setLength(0);
            haveSpace = false;
          }

          ret.add(line.substring(curPos, pos).trim());
          curPos = pos + 1;
          lastC = dot;
          continue;
        } else {
          break;
        }
      } else {
        if (c == dot) {
          if (sb.length() > 0) {
            ret.add(sb.toString());
            sb.setLength(0);
            haveSpace = false;
          } else
            break;
        } else if (!haveSpace
                && (fc == c || Character.isLetter(c) || (sb.length() > 0 && Character
                .isDigit(c)))) {
          sb.append(c);
        } else {
          if (!haveSpace
                  && sb.length() > 0
                  && (Character.isWhitespace(c) || c == leftPR || (forMeta && c == macroSp)))
            haveSpace = true;
          if (!haveSpace) {
            curPos -= sb.length();
            sb.setLength(0);
          } else if (sb.length() > 0) {
            ret.add(sb.toString());
            sb.setLength(0);
          }
          break;
        }
      }
      lastC = c;
      curPos++;
    }
    if (sb.length() > 0)
      ret.add(sb.toString());
    hadLBK = false;
    while (curPos > 0 && curPos < line.length()) {
      lastC = line.charAt(curPos);
      if (lastC == dot || Character.isWhitespace(lastC)
              || lastC == leftPR || (forMeta && lastC == macroSp)) {
        hadLBK = true;
      } else if (hadLBK) {
        curPos++;
        break;
      }
      curPos--;
    }
    if (curPos < 1 && ret.size() > 0)
      return null;
    ret.add(line.substring(curPos));
    return ret;
  }

  private boolean isValidPageName(String name) {
    return name.equals(_DEFAULT) || isValidName(name);
  }

  private boolean isValidCommand(String name) {
    if (name.equals("assert") || name.equals("var") || name.equals("goto") || name.equals("return")) return true;
    return isValidName(name);
  }

  private boolean isValidName(String name) {
    if (name == null || name.length() < 1)
      return false;
    if (!(name.charAt(0) == '_' || Character
            .isLetter(name.charAt(0))))
      return false;
    for (char c : name.toCharArray()) {
      if (!(c == '_' || Character.isLetterOrDigit(c)))
        return false;
    }
    return !preservedKeywords.containsKey(name.toLowerCase());
  }

  private static String _unescapeString(String val) {
    char[] arr = val.toCharArray();
    char[] l = _escapeString.toCharArray();
    char c = 0;
    for (int i = 0; i < arr.length; i++) {
      c = arr[i];
      if (c >= 0 && c < l.length)
        arr[i] = l[c];
    }
    return new String(arr);
  }

  private void countLines(String text, int[] lines) {
    lines[0] = 0;
    lines[1] = 0;
    char cn = (char) '\n';
    boolean cnt = true;
    for (char c : text.toCharArray()) {
      if (c == cn) {
        if (cnt)
          lines[0]++;
        lines[1]++;
      } else if (cnt && !Character.isWhitespace(c))
        cnt = false;
    }
  }

  private void parseParameters(Map<String, String> paras,
                               List<String> paralist, String text, String lineInfo, boolean invoke) throws Exception {// =
    // false
    paras.clear();
    paralist.clear();
    if (text.trim().length() < 1)
      return;
    String[] arr = text.split(",");
    int prpos = 0;
    String pname = null;
    for (String para : arr) {
      prpos = para.indexOf("=");
      if (prpos > 0) {
        pname = para.substring(0, prpos).trim().toLowerCase();
        if (!isValidVarName(pname)) {
          throw new Exception("Error: invalid parameter name: " + pname + lineInfo);
        }
        if (!paras.containsKey(pname))
          paralist.add(pname); // if a=v,b =a, a=v2, then guess what?
        // b = v2, it should be that way, a
        // design issue
        paras.put(pname, para.substring(prpos + 1).trim());
      } else if (invoke)
        throw new Exception("Error: invoked parameter must have name=value format"
                + lineInfo);
    }
  }

  private void compileAction(String page, String action, String src,
                             int line, String filePath, int fileOrder, List<Code> codes,
                             Map<String, Object> metas, Map<String, String> descs, String tagCmt) throws Exception {
    // split the source for action, but
    String[] arrStats = src.split(";");
    String label = null, item = null, lineInfo = null, call = null, cmd, actionData = null;
    int pos = 0;
    List<String> arr = null;
    Map<String, String> paras = new LinkedHashMap<String, String>();
    List<String> paraNames = new ArrayList<String>();
    int lineNum = 0;
    int idx = 0;
    int[] lineCounters = new int[2];// [0,0]
    if (page == null || (page = page.trim()).length() < 1)
      page = _DEFAULT;
    for (String ite : arrStats) {
      countLines(ite, lineCounters);
      lineNum = line + lineCounters[0];
      lineInfo = ", Line: " + lineNum + ", File: " + filePath;
      line += lineCounters[1];

      item = ite.trim();
      label = null;
      if (item.length() < 1) {
        continue;
      }
      if (item.startsWith("@")) {
        // this is a label!!
        pos = item.indexOf(":");
        if (pos < 1) {
          throw new Exception("Error: expect a : after Label" + lineInfo);
        }

        label = item.substring(1, pos).trim().toLowerCase();
        if (!isValidVarName(label)) {
          throw new Exception("Error: invalid label:" + label + lineInfo);
        }
        item = item.substring(pos + 1);
        if (item.trim().length() < 1) {
          throw new Exception("Error: empty statement after label: " + label
                  + lineInfo);
        }
      }
      if (label != null) {
        cmd = "@" + page + "." + action + "." + label;
        if (metas.containsKey(cmd))
          throw new Exception("Error: duplicated label in action: "
                  + cmd.substring(1) + lineInfo);
        metas.put(cmd, codes.size());
        cmd = "@" + page + "." + action;
        if (descs != null) {
          if (descs.containsKey(cmd))
            descs.put(cmd, descs.get(cmd) + "," + label);
          else
            descs.put(cmd, label);
        }
      }
      // when there is a space, and no "." arround, we need to move it
      // element.type $username$;element2.click;item();
      // TODO: make sure the parse method should be OK!!
      arr = parseLine(item, false);
      if (arr == null) {
        throw new Exception("Error: invalid action statement" + lineInfo);
      }

      call = null;
      // how it will be a action call ?, it must have the .validname(ssss)
      item = arr.get(arr.size() - 1).trim();
      if (arr.size() > 1 && item.endsWith(")") && item.startsWith("(")) {
        call = arr.get(arr.size() - 2).trim().toLowerCase();
        if (!isValidCommand(call)) {
          throw new Exception("Error: invalid call function name: " + call + lineInfo);
        } else {
          item = item.substring(1, item.length() - 1);
        }
      }

      if (call != null) {
        if (arr.size() > 4) {
          // target, page is also in there
          throw new Exception("Error: too many segments in call, only page/system/element level call expected" + lineInfo);
        }
        // 4, page.element.actions
        parseParameters(paras, paraNames, item, lineInfo, true);
        for (String para : paraNames) {
          codes.add(new Code(fileOrder, lineNum, TB_PARA | TB_COMMAND_SYSTEM, page,
                  action, para, _unescapeString(paras.get(para))));
        }
        // FIX: counting line number counting issue
        countLines(item, lineCounters);
        lineNum += lineCounters[1];
        if (arr.size() == 4) {
          item = arr.get(1); // element
          if (!item.startsWith("[")) {
            if (!isValidVarName(item)) {
              throw new Exception("Error: invalid page.element for call: " + item + lineInfo);
            }
          } else {
            item = arr.get(0).trim().toLowerCase();
            if (!isValidPageName(item)) {
              throw new Exception("Error: invalid page for call: " + item + lineInfo);
            }
          }
          // this has to be invoke action, no other way
          if (!supportedActions.containsKey(call))
            throw new Exception("Error: invoke action doesn't exsit: " + call + lineInfo);
          codes.add(new Code(fileOrder, lineNum, TB_INVOKE, item, arr.get(1).trim(), call, null));
        } else if (arr.size() == 3) { // NOTE: it may not be a call, maybe a
          // customized invoke, could be page or element invoke, check it later!!!!
          item = arr.get(0).trim();
          if (item.startsWith("[")) {
            if (!supportedActions.containsKey(call))
              throw new Exception("Error: invoke action doesn't exsit: " + call + lineInfo);
            codes.add(new Code(fileOrder, lineNum, TB_INVOKE, page, item, call, null));
          } else {
            item = item.toLowerCase();
            if (!isValidPageName(item)) throw new Exception("Error: invalid page name for call: " + item + lineInfo);
            codes.add(new Code(fileOrder, lineNum, TB_CALL | TB_COMMAND_PAGE, page,
                    item, call, null)); // could be element/page/system invoke, or page call,
          }
        } else { // again it could be a page action, or system/page invoke action
          codes.add(new Code(fileOrder, lineNum, TB_CALL | TB_COMMAND_PAGE | TB_COMMAND_SYSTEM, page, null,
                  call, null));
        }
      } else {
        if (arr.size() > 4 || arr.size() < 2) {
          throw new Exception("Error: too " + (arr.size() > 4 ? "many" : "few") + " segments in action statement"
                  + lineInfo);
        }
        actionData = _unescapeString(arr.get(arr.size() - 1).trim());
        call = arr.get(arr.size() - 2).trim().toLowerCase();

        if (!isValidCommand(call)) {
          throw new Exception("Error: invalid action type: " + call + lineInfo);
        }
        pos = getCommandType(call);
        if (pos < 0) {
          throw new Exception("Error: unsupported action type: " + call + lineInfo);
        }

        if (arr.size() > 3) {
          if (!isNormalCommand(call)) {
            throw new Exception("Error: too many segments for page/system command"
                    + lineInfo);
          }
          item = arr.get(0).trim().toLowerCase();
          //arr.set(0, );
          if (!isValidPageName(item))
            throw new Exception("Error: invalid pageName in statement"
                    + item + lineInfo);
          cmd = arr.get(1).trim();
          if (isValidName(cmd))
            cmd = cmd.toLowerCase();
          else if (cmd.length() < 3) {
            throw new Exception("Error: empty instance target value between []"
                    + lineInfo);
          } else
            cmd = _unescapeString(cmd);
          codes.add(new Code(fileOrder, lineNum, pos | getCommandFlags(call), item, cmd, call, actionData));
        } else if (arr.size() > 2) {
          // could be a page command, or local stuff
          if (isSystemCommand(call))
            throw new Exception("Error: tow many segments for system command:"
                    + call + lineInfo);
          else if (isPageCommand(call)) {
            item = arr.get(0).trim().toLowerCase();
            if (!isValidPageName(item)) {
              throw new Exception("Error: invalid page name for pageCommand:"
                      + item + lineInfo);
            }
            codes.add(new Code(fileOrder, lineNum, pos | getCommandFlags(call), item,
                    null, call, actionData));
          } else {
            item = arr.get(0).trim();
            if (isValidName(item))
              item = arr.get(0).toLowerCase();
            else if (item.length() < 3) {
              throw new Exception("Error: empty instance target value between []"
                      + lineInfo);
            } else
              item = _unescapeString(item);
            codes.add(new Code(fileOrder, lineNum, pos | getCommandFlags(call), page, item, call, actionData));
          }
        } else {
          if (isSystemCommand(call)) {
            // special check, for goto,var, assert, those accept expressions (goto a: b, var a:b, and assert a:b
            if (pos == TB_GOTO || pos == TB_VAR) {
              cmd = arr.get(arr.size() - 1).trim();
              if (cmd.length() < 1) throw new Exception("Error: empty target after " + call + " statement" + lineInfo);
              cmd = _unescapeString(cmd); // just in case
              idx = cmd.indexOf(':');
              actionData = null;
              if (idx >= 0) {
                actionData = cmd.substring(idx + 1).trim();
                cmd = cmd.substring(0, idx).trim();
              }
              item = cmd;
              if (item.startsWith("?") && pos == TB_VAR) item = item.substring(1).trim();
              if (pos == TB_VAR) {
                idx = item.indexOf(".");
                if (idx >= 0) {
                  if (!isValidName(item.substring(idx + 1).trim()))
                    throw new Exception("Error: invalid " + call + " target name: " + item + " in call " + call + lineInfo);
                  if (idx > 0 && !isValidPageName(item.substring(0, idx).trim().toLowerCase()))
                    throw new Exception("Error: invalid " + call + " target name: " + item + " in call " + call + lineInfo);
                  item = item.substring(0, idx).trim() + "." + item.substring(idx + 1).trim();
                  if(item.startsWith("."))item = page + item; // in case it's default pointing to this
                } else if (!isValidName(item))
                  throw new Exception("Error: invalid " + call + " target name: " + item + " in call " + lineInfo);

              } else if (!isValidName(item)) {
                throw new Exception("Error: invalid " + call + " target name: " + item + " in call " + lineInfo);
              }
              if (cmd.startsWith("?")) item = "?" + item;
              if (actionData != null) {
                if (actionData.length() < 1)
                  throw new Exception("Error: expect " + call + " condition expression after " + item + ": in call " + lineInfo);
                cmd = compileExpr(actionData);
                if (cmd != null)
                  throw new Exception("Error: invalid expression in " + call + ": " + actionData + "error: " + cmd + lineInfo);
              }
              codes.add(new Code(fileOrder, lineNum, pos | getCommandFlags(call),
                      pos == TB_GOTO ? page + "." + action : page, item.toLowerCase(), call, actionData)); // FIX: for goto, use page + action
            } else if (pos == TB_ASSERT || pos == TB_RETURN) {
              cmd = arr.get(arr.size() - 1).trim();
              actionData = null;
              if (cmd.length() > 0) {
                if (pos == TB_ASSERT) {
                  idx = cmd.indexOf(':');
                  while (idx > 0) {
                    item = compileExpr(cmd.substring(0, idx));
                    if (item == null) break;
                    idx = cmd.indexOf(':', idx + 1);
                  }
                  if (idx >= 0) {
                    item = cmd.substring(0, idx).trim();
                    actionData = cmd.substring(idx + 1).trim();
                    if (item.length() < 1) item = null;
                  } else {
                    item = compileExpr(cmd);
                    if (item != null)
                      throw new Exception("Error: invalid expression in " + call + ": " + cmd + "error: " + item + lineInfo);
                    item = cmd;
                  }
                } else {
                  item = compileExpr(cmd);
                  if (item != null)
                    throw new Exception("Error: invalid expression in " + call + ": " + cmd + "error: " + item + lineInfo);
                  item = cmd;
                }
              } else {
                item = null;
              }
              codes.add(new Code(fileOrder, lineNum, pos | getCommandFlags(call),
                      page, item, call, actionData));
            } else // for system command
              codes.add(new Code(fileOrder, lineNum, pos | getCommandFlags(call), page,
                      null, call, actionData));
          } else if (isPageCommand(call)) {
            codes.add(new Code(fileOrder, lineNum, pos | getCommandFlags(call), page, null,
                    call, actionData));
          } else {
            throw new Exception("Error: too few segments for normal action "
                    + call + lineInfo);
          }
        }
      }
    }
  }

  private void compileLines(String filePath,
                            Map<String, Integer> allLoadedFiles,
                            List<List<String>> loadedFileLines) throws Exception {
    // try to load all external stuff
    // ADD: first try to locate the file
    int fileOrder = 0;
    List<String> lines = null;
    if (allLoadedFiles.containsKey(filePath)) {
      fileOrder = allLoadedFiles.get(filePath);
      if (fileOrder >= 0 && fileOrder < loadedFileLines.size())
        lines = loadedFileLines.get(fileOrder);
      allLoadedFiles.remove(filePath);
      if (lines == null)
        throw new Exception("Error: file not existed or empty content: " + filePath);
    } else
      return;

    int lNum = 0;
    String cmd = null, lineInfo = null, curPath = null, lineNum = null;
    List<String> arr = null;
    int pos = 0, pos2 = 0, pos3 = 0;
    String curPage = null, curEle = null, curAction = null, curVal = null;
    Map<String, String> defParas = new LinkedHashMap<String, String>();
    List<String> defParaList = new ArrayList<String>();
    Code cmtCode = null; // find it from botCodes from bottom, if line not match
    int[] lineCounters = new int[2];
    for (String line : lines) {
      cmd = line.substring(line.indexOf(".") + 1);
      if (line.startsWith("#")) {
        lineNum = line.substring(1, line.length() - cmd.length() - 1);
        botCodes.add(cmtCode = new Code(fileOrder, Integer.parseInt(lineNum),
                TB_COMMENT | TB_COMMAND_SYSTEM, null, null, null, cmd));
        continue;
      }
      if (cmd.startsWith("@")) { // ignore thestuff
        lineNum = line.substring(0, line.length() - cmd.length() - 1);
        arr = parseLine(cmd.substring(1), true);
        lineInfo = ", Line: " + lineNum + ", File: " + filePath;
        if (arr == null || arr.size() != 2) { // all macro must contains
          // two subs
          throw new Exception("Error: invalid meta var " + cmd + lineInfo);
        }
        arr.set(0, arr.get(0).trim().toLowerCase());
        if (arr.get(1).startsWith("="))
          arr.set(1, arr.get(1).substring(1));
        arr.set(1, arr.get(1).trim());
        if (arr.get(0).equals("import")) {
          curPath = arr.get(1);
          if (allLoadedFiles.containsKey(curPath)) {
            try {
              compileLines(curPath, allLoadedFiles,
                      loadedFileLines);
            } catch (Exception e) {
              throw new Exception(e.getMessage() + ", imported file: " + filePath
                      + ", line: " + lineNum);
            }
          }
        } else if (supportedMetas.containsKey(arr.get(0))) {
          metaInfos.put("@" + arr.get(0) + "." + fileOrder,
                  _unescapeString(arr.get(1)));
          if (fileOrder == 0)
            metaInfos.put("@" + arr.get(0),
                    _unescapeString(arr.get(1)));
        } else
          throw new Exception("Error: unsupported/invalid meta var:" + arr.get(0)
                  + lineInfo);
        botCodes.add(new Code(fileOrder, Integer.parseInt(lineNum),
                TB_META | TB_COMMAND_SYSTEM, "meta", null, arr.get(0), arr.get(1)));
      } else {
        // let's do it!!!
        lineNum = line.substring(0, line.length() - cmd.length() - 1);
        lineInfo = ", Line: " + lineNum + ", File: " + filePath;
        lNum = Integer.parseInt(lineNum);

        pos = cmd.indexOf(":::");
        pos2 = cmd.indexOf("::");
        pos3 = cmd.indexOf(":");

        if (pos < 0 || pos2 < pos || pos3 < pos) {
          if (curPage == null) {
            throw new Exception("Error: statement declaration must be ffter page declaration"
                    + lineInfo);
          }
          pos = pos2;
          if (pos >= 0 && pos == pos3) { // a element definition!! ::
            curEle = cmd.substring(0, pos).trim().toLowerCase();
            curVal = cmd.substring(pos + 2).trim();
            if (!isValidVarName(curEle)) {
              throw new Exception("Error: invalid element name" + lineInfo);
            }
            if (curVal.length() < 1) {
              throw new Exception("Error: empty locator value for element:"
                      + curEle + lineInfo);
            }
            // if(!hash.containsKey("." + curPage + "." + ele))
            // pages.add(curPage + "." + ele + ".")
            cmd = "." + curPage + "." + curEle;
            if (metaInfos.containsKey(cmd))
              throw new Exception("Error: duplicated element definition: "
                      + cmd.substring(1) + lineInfo);
            if (metaInfos.containsKey("$" + curPage + "." + curEle))
              throw new Exception("Error: conflict element definition with action name: "
                      + cmd.substring(1) + lineInfo);
            metaInfos.put(cmd, _unescapeString(curVal)); // get the
            // element
            // locator
            // set in
            // hash
            cmd = "." + curPage;
            if (descInfos.containsKey(cmd))
              descInfos.put(cmd, descInfos.get(cmd) + ","
                      + curEle);
            else
              descInfos.put(cmd, curEle);
            botCodes.add(new Code(fileOrder, lNum, TB_META | TB_COMMAND_SYSTEM,
                    "element", curPage, curEle, curVal));
          } else { // could be a action!!
            pos = pos3;
            if (pos < 1) {
              throw new Exception("Error: action name is empty" + lineInfo);
            }

            // FIX: defpara=$Com$, Com can not be lowercase!!!!
            curAction = cmd.substring(0, pos).trim();
            curVal = cmd.substring(pos + 1);

            // Enhancement, we suppot default parameters now!!
            pos2 = curAction.indexOf("(");
            pos3 = curAction.indexOf(")");
            if ((pos2 * pos3) <= 0 || pos2 > pos3) {
              throw new Exception("Error: invalid default parameter declaration"
                      + lineInfo);
            }
            // add the page action
            botCodes.add(new Code(fileOrder, lNum, TB_META | TB_COMMAND_SYSTEM,
                    "action", curPage, curAction, null));
            pos = botCodes.size();
            if (pos2 > 0) {
              cmd = curAction.substring(pos2 + 1, pos3);
              curAction = curAction.substring(0, pos2).trim();
              if (pos3 - pos2 > 1) {
                parseParameters(defParas, defParaList,
                        cmd, lineInfo, false);
                for (String para : defParaList) {
                  botCodes.add(new Code(fileOrder, lNum,
                          TB_DEFPARA | TB_COMMAND_SYSTEM, curPage, curAction,
                          para, _unescapeString(defParas
                          .get(para))));
                }
              }
              // FIX: counting line number issue
              countLines(cmd, lineCounters);
              lNum += lineCounters[1];
            }
            curAction = curAction.toLowerCase();
            if (!isValidVarName(curAction)) {
              throw new Exception("Error: invalid action name: " + curAction
                      + lineInfo);
            }

            if (metaInfos.containsKey("$" + curPage + "."
                    + curAction))
              throw new Exception("Error: duplicated action definition: "
                      + curPage + "." + curAction + lineInfo);
            if (metaInfos.containsKey("." + curPage + "."
                    + curAction))
              throw new Exception("Error: conflict action definition with element: "
                      + curPage + "." + curAction + lineInfo);
            if (cmtCode != null && cmtCode.line != lNum) {
              for (int i = botCodes.size() - 1; i >= 0; i--) {
                cmtCode = botCodes.get(i);
                if ((cmtCode.type & TB_COMMAND_MAX) == TB_COMMENT) {
                  if (cmtCode.line <= lNum) break;
                }
              }
              if (cmtCode != null && ((cmtCode.type & TB_COMMAND_MAX) != TB_COMMENT || cmtCode.line != lNum))
                cmtCode = null;
            }
            compileAction(curPage, curAction, curVal, lNum,
                    filePath, fileOrder, botCodes, metaInfos,
                    descInfos,
                    cmtCode != null ? cmtCode.command : null);
            // Add: change the action name
            botCodes.get(pos - 1).command = curAction;

            metaInfos.put("$" + curPage + "." + curAction, pos);
            metaInfos.put("%" + curPage + "." + curAction,
                    botCodes.size());
            cmd = "$" + curPage;
            if (descInfos.containsKey(cmd)) {
              descInfos.put(cmd, descInfos.get(cmd) + ","
                      + curAction);
            } else {
              descInfos.put(cmd, curAction);
            }
            descInfos.put("#" + curPage + "." + curAction, cmtCode != null ? cmtCode.data : null);
          }
        } else {
          curPage = cmd.substring(0, pos).trim().toLowerCase();
          curVal = cmd.substring(pos + 3).trim();
          if (!isValidVarName(curPage)) {
            throw new Exception("Error: invalid page name: " + curPage
                    + lineInfo);
          }
          botCodes.add(new Code(fileOrder, lNum, TB_META | TB_COMMAND_SYSTEM, "page",
                  curPage, curVal, null));
          if (!metaInfos.containsKey("?" + curPage)) {
            metaInfos.put("?" + curPage, _unescapeString(curVal)); // doesn't
            // need
            // to
            // include
            // the
            // root
            if (descInfos.containsKey("."))
              descInfos.put(".", descInfos.get(".") + ","
                      + curPage);
            else
              descInfos.put(".", curPage);
          }
          if (cmtCode != null && cmtCode.line != lNum) {
            for (int i = botCodes.size() - 1; i >= 0; i--) {
              cmtCode = botCodes.get(i);
              if ((cmtCode.type & TB_COMMAND_MAX) == TB_COMMENT) {
                if (cmtCode.line <= lNum) break;
              }
            }
            if (cmtCode != null && ((cmtCode.type & TB_COMMAND_MAX) != TB_COMMENT || cmtCode.line != lNum))
              cmtCode = null;
          }
          descInfos.put("#" + curPage, cmtCode != null ? cmtCode.data : null);
        }

      }
    }
  }

  private String getError(String err, Code code) {
    return err
            + ", line: "
            + code.line
            + ", file: "
            + (code.fileIndex < 0 ? "_main_" : loadedSourceFiles
            .get(code.fileIndex));
  }

  private void checkCompiledCodes(List<Code> codes,
                                  Map<String, Object> metas) throws Exception {
    String temp = null;
    int pos = 0;
    int tp = 0;
    for (Code code : codes) {
      tp = code.type & TB_COMMAND_MAX;
      if (tp == TB_COMMENT || tp == TB_META)
        continue;
      if (tp == TB_CALL) {
        pos = code.type & TB_COMMAND_FLAGS; // check the flag
        if ((pos & TB_COMMAND_SYSTEM) != 0) { // it means it could be page or element invoke, or page action
          // could be a page action, or a invokation
          if (!metaInfos
                  .containsKey("$" + code.page + "." + code.command)) {
            // it maybe a call
            if (supportedActions.containsKey(code.command)) {
              code.type = TB_INVOKE | TB_COMMAND_SYSTEM; // change to invoke, default it's system call
            } else {
              if (code.page.equals(_DEFAULT)) {
                // nothing could be wrong with default, runtime could be a problem
              } else {
                throw new Exception(getError("Error: action doesn't exist: " + code.page + "." + code.command, code));
              }
            }
          }
        } else { // could be a page/element invoke, or a page action
          // chekc it as element,
          if (code.target.equals(_DEFAULT)) {
            if (metaInfos.containsKey("$" + code.page + "." + code.command)) {
              code.type = TB_CALL | TB_COMMAND_PAGE;
            } else if (supportedActions.containsKey(code.command)) {
              code.target = null;
              code.type = TB_INVOKE | TB_COMMAND_PAGE; // change to invoke
            } else
              throw new Exception(getError("Error: action doesn't exist: " + code.page + "." + code.command, code));
          } else if (metaInfos.containsKey("$" + code.target + "." + code.command)) {// chekc for a action call
            code.type = TB_CALL | TB_COMMAND_PAGE;
            code.page = code.target;
            code.target = null;
          } else if (supportedActions.containsKey(code.command)) { // could be page or element
            if (metaInfos.containsKey("." + code.page + "." + code.target)) {
              code.type = TB_INVOKE; // element invoke
            } else if (metaInfos.containsKey("?" + code.target)) {
              code.type = TB_INVOKE | TB_COMMAND_PAGE;
              code.page = code.target;
              code.target = null;
            } else throw new Exception(getError("Error: invoke call page doesn't exist: " + code.target, code));
          } else throw new Exception(getError("Error: action doesn't exist: " + code.command, code));
        }
      } else if (tp == TB_INVOKE) {
        if (code.target.startsWith("[")) {
          if (!metaInfos.containsKey("?" + code.page))
            throw new Exception(getError("Error: invoke action page doesn't exist: " + code.page, code));
        } else if (!metaInfos.containsKey("." + code.page + "." + code.target))
          throw new Exception(getError("Error: invoke action page/element doesn't exist: " + code.page, code));
      } else if (tp == TB_GOTO) {
        // FIX: check goto from metas, not metaInfos(global metas)
        if (!metas.containsKey("@" + code.page + "." + code.target))
          throw new Exception(getError("Error: goto label doesn't exist: "
                  + code.page + "." + code.target, code));
      } else if (tp == TB_VAR) { // CHANGE(2010/12/22), check the
        // page.var format!!
        temp = code.target;
        if ("?".indexOf(temp.substring(0, 1)) >= 0)
          temp = temp.substring(1);
        pos = temp.indexOf(".");
        if (pos > 0) {
          temp = temp.substring(0, pos);
          if (!temp.equals(_DEFAULT)
                  && !metaInfos.containsKey("?" + temp))
            throw new Exception(getError(
                    "Error: var name's page doesn't exist in: "
                            + code.page, code));
        }
      } else if (code.page != null && !code.page.equals(_DEFAULT)
              && !metaInfos.containsKey("?" + code.page)) { // must be a
        // non system
        // command
        throw new Exception(getError("Error: page doesn't exist: " + code.page, code));
      }
      if (tp > TB_META) {
        pos = code.type & TB_COMMAND_FLAGS;
        if (pos == 0) { // normal command
          if (!code.target.startsWith("[")
                  && !metaInfos.containsKey("." + code.page + "."
                  + code.target)) {
            if (code.page.equals(_DEFAULT)) {
              // Change: we can allow it, check it during runtime!!
              // return
              // getError("Error: Default Page Doesn't  Support Element Definition!",code)
            } else
              throw new Exception(getError("Error: element doesn't exist,: "
                      + code.page + "." + code.target, code));
          }
        } else if ((pos & TB_COMMAND_PAGE) != 0) {

        }

      }
    }
  }

  public static TextBot initialize(String path, String[] cmds, int[] cmdFlags, String[] cxtVars, String[] actions, String[] metas, String[] keywords) throws Exception {
    if (path == null || path.length() < 1)
      throw new Exception("Error: missed surce path!");
    return new TextBot().initializeInternal(path, cmds, cmdFlags, cxtVars, actions, metas, keywords);
  }

  private TextBot initializeInternal(String path, String[] cmds, int[] cmdFlags, String[] cxtVars, String[] actions, String[] metas, String[] keywords) throws Exception {
    // start to compile the source code
    // read the stuff
    String source = FileUtils.readAllText(path, null);
    if (source == null || source.length() < 1)
      throw new Exception("Error: textbot source file not existed or empty content: " + path);

    List<String> allLines = parseInLines(source);
    if (allLines.size() < 1)
      throw new Exception("Error: no valid logic code defined in source file: " + path);

    // step 1: add supported stuff
    if (cmds != null) {
      for (int i = 0; i < cmds.length; i++) {
        addSupportedCommand(cmds[i], cmdFlags != null && cmdFlags.length > i ? cmdFlags[i] : 0);
      }
    }
    if (cxtVars != null) {
      for (String v : cxtVars) addSupportedContextVar(v);
    }
    if (actions != null) {
      for (String v : actions) addSupportedAction(v);
    }
    if (metas != null) {
      for (String v : metas) addSupportedMeta(v);
    }
    if (keywords != null) {
      for (String v : keywords) addPreservedKeyword(v);
    }
    // step 2: start to parse the stuff
    Map<String, Integer> loadedFiles = new LinkedHashMap<String, Integer>();
    List<List<String>> loadedFileList = new ArrayList<List<String>>();
    List<String> loadedFilePaths = new ArrayList<String>();
    int loadIndex = 0;
    loadedFileList.add(allLines);
    loadedFilePaths.add(path);
    String curPath = null;
    String cmd = null;
    List<String> arr = null;
    String localPath = null; // FIX: Path must be saved for compiling
    while (loadIndex < loadedFileList.size()) {
      allLines = loadedFileList.get(loadIndex);
      localPath = loadedFilePaths.get(loadIndex);
      loadedFiles.put(localPath, loadIndex); // remember the stuff
      loadIndex++;
      String lineNum = null;
      boolean allowed = true;
      for (String line : allLines) {
        if (line.startsWith("#"))
          continue;
        cmd = line.substring(line.indexOf(".") + 1);
        if (!cmd.startsWith("@")) {
          allowed = false;
          continue;
        }
        lineNum = ", Line: "
                + line.substring(0, line.length() - cmd.length() - 1)
                + ", File: " + localPath;

        if (!allowed) {
          throw new Exception("Error: meta only allowed before action code"
                  + lineNum);
        }
        arr = parseLine(cmd.substring(1), true);
        if (arr == null || arr.size() != 2) { // all meta must contains
          // two subs
          throw new Exception("Error: invalid meta var " + cmd + lineNum);
        }
        if (arr.get(0).trim().toLowerCase().equals("import")) {
          curPath = arr.get(1).trim();
          if (curPath.startsWith("="))
            curPath = curPath.substring(1).trim();

          if (curPath.length() < 1) {
            throw new Exception("Error: meta import: missed file path"
                    + lineNum);
          }

          if (loadedFiles.containsKey(curPath)) {
            // Log("Warn: Skip Loaded File:" + curPath + lineNum)
            continue;
          }
          source = FileUtils.readAllText(curPath, null);
          if (source == null)
            throw new Exception("Error: meta import: file not existed"
                    + lineNum);
          List<String> locLines = parseInLines(source);
          if (locLines.size() < 1) {
            // Log("Warn: Empty Imported File" + lineNum)
          } else {
            loadedFileList.add(locLines);
            loadedFilePaths.add(curPath);
          }
        }
      }
    }
    // ok, start the compilation
    metaInfos = new LinkedHashMap<String, Object>();
    botCodes = new ArrayList<Code>();
    descInfos = new LinkedHashMap<String, String>();
    // ok, for already defined metas, we must set the correct value
    for (String m : supportedMetas.keySet())
      metaInfos.put("@" + m, "");
    try {
      loadedSourceFiles = loadedFilePaths;
      compileLines(path, loadedFiles, loadedFileList);
      // ok, finally we need to check
      // parsLine : a. -b
      // all functional call, the page, action should be existed!!!!!!!!!
      checkCompiledCodes(botCodes, metaInfos);
    } catch (Exception e) {
      botCodes = null;
      metaInfos = null;
      descInfos = null; // not necessary
      loadedSourceFiles = null;
      throw e;
    }
    return this;
  }

  public TextBot simpleClone(Bot bot) {
    if (botCodes == null || bot == null)
      return null;
    TextBot ret = new TextBot(bot);
    ret.botCodes = botCodes;
    ret.metaInfos = metaInfos;
    ret.descInfos = descInfos;
    ret.pageVars = new LinkedHashMap<String, String>();
    ret.loadedSourceFiles = loadedSourceFiles;
    ret.bot = bot;
    //ret.lastError = null;

    // meta data copy is not necessary for now
    ret.supportedActions = supportedActions;
    ret.supportedMetas = supportedMetas;// new LinkedHashMap<String, Integer>();
    //ret.supportedMacros.putAll(supportedMacros);
    ret.preservedKeywords = preservedKeywords;
    //new LinkedHashMap<String, Integer>();
    //ret.preservedKeywords.putAll(preservedKeywords);
    ret.internalVars = internalVars;// new LinkedHashMap<String, Integer>();
    //ret.internalVars.putAll(internalVars);
    ret.supportedCommands = supportedCommands;//new LinkedHashMap<String, Integer>();
    //ret.supportedCommands.putAll(supportedCommands);
    ret.supportedActions = supportedActions;//new LinkedHashMap<String, Integer>();
    //ret.supportedActions.putAll(supportedActions);
    return ret;
  }

  public String getDescription(String page, int mode) { // = 0
    if (descInfos == null)
      return null;
    String ret = null;
    if (page == null) {
      page = "";
    } else
      page = page.trim().toLowerCase();
    if (mode == 0) { // get page names
      ret = descInfos.get(".");
      if (ret == null)
        return null;

      if (!page.equals("")) {
        if (!isValidName(page))
          return null;
        ret = "," + ret + ",";
        if (ret.indexOf("," + page + ",") < 0)
          return null;
        else
          ret = page;
      }
    } else if (mode == 1) {
      // ok, get the elements
      if (page.equals("") || !isValidName(page))
        return null;

      page = "." + page;
      ret = descInfos.get(page);
    } else if (mode == 2) {
      if (page.equals("") || !isValidName(page))
        return null;
      page = "$" + page;
      ret = descInfos.get(page);
    } else if (mode == 3) { // macro
      if (page.indexOf(".") < 1)
        return null;
      page = "@" + page;
      ret = descInfos.get(page);
    } else if (mode == 4) { // macro
      ret = descInfos.get(page);
    } else
      return null;
    return ret;
  }

  private String getDescFont(String cls, String content) {
    return "<font class=" + cls + ">" + content + "</font>";
  }

  public String renderCodeAsHTML() {
    // render all macros
    StringBuilder sb = new StringBuilder();
    int line = 1;
    // very simple, output based on textbotCodes
    for (Code code : botCodes) {
      // simply render all the codes
      while (line++ < code.line)
        sb.append("<br />");
      switch (code.type & TB_COMMAND_MAX) {
        case TB_COMMENT:
          break;
        case TB_VAR:
          break;
        case TB_CALL:
          break;
        case TB_INVOKE:
          break;
        case TB_RETURN:
          break;
        case TB_GOTO:
          break;
        case TB_DEFPARA:
          break;
        case TB_PARA:
          break;
        case TB_META:
          break;
        default:
          break;
      }
      // sb.append(getDescFont("op","@") + getDescFont("op",key) + +" " +
      // getDescFont("val", metaInfos["@" + key]) + "<br />")
    }
    // get all pages
    String pgs = getDescription(null, 0);// get all pages
    if (pgs == null)
      return sb.toString();
    String fs = "&nbsp;&nbsp;&nbsp;&nbsp;";
    for (String page : pgs.split(",")) {
      sb.append(getDescFont("var", page) + getDescFont("op", ":::")
              + getDescFont("val", metaInfos.get("?" + page).toString())
              + "<br />");
      // ok, start to output all elements defined in the file!!
      String els = getDescription(page, 1);
      if (els != null && els.length() > 0) {
        for (String el : els.split(",")) {
          // sb.append(
        }
      }
      // sb.append(fs +
    }
    return null;
  }

  /**
   * @return a page lists of currently loaded source
   */
  public String[] getPages() {
    String info = getDescription(null, 0); // get all pages
    if (info == null)
      return new String[0];
    return info.split(",");
  }

  public String[] getActions(String page) {
    if (page == null || page.length() < 1) return new String[0];
    String info = getDescription(page, 2);
    if (info == null || info.length() < 1) return new String[0];
    return info.split(",");
  }

  public String[] getElements(String page) {
    if (page == null || page.length() < 1) return new String[0];
    String info = getDescription(page, 1);
    if (info == null || info.length() < 1) return new String[0];
    return info.split(",");
  }

  public String getActionComment(String page, String action) {
    if (page == null || action == null) return null;
    return getDescription("#" + page + "." + action, 4);
  }

  public String getPageComment(String page) {
    if (page == null) return null;
    return getDescription("#" + page, 4);
  }

  public String getMetaInfo() {
    StringBuilder sb = new StringBuilder();
    String info = null;
    for (String page : getPages()) {
      sb.append("Page: " + page);
      sb.append("\r\n\r\n");

      info = getDescription(page, 1);
      if (info == null)
        sb.append("\tNo Elements\r\n\r\n");
      else {
        for (String el : info.split(",")) {
          sb.append("\tElement: " + el);
          sb.append("\r\n");
        }
        sb.append("\r\n");
      }

      info = getDescription(page, 2); // actions
      if (info == null)
        sb.append("\tNo Actions\r\n\r\n");
      else {
        for (String ac : info.split(",")) {
          sb.append("\tAction: " + ac);
          sb.append("\r\n");
          info = getDescription(page + "." + ac, 3); // labels
          if (info == null)
            sb.append("\t\tNo Labels\r\n\r\n");
          else {
            for (String lb : info.split(",")) {
              sb.append("\t\tLabel: " + lb);
              sb.append("\r\n");
            }
          }
        }
        sb.append("\r\n");
      }

    }
    return sb.toString();
  }


  public String execute(String InstSrc) throws Exception {
    return execute(InstSrc, null);
  }

  public String execute(String InstSrc, Map<String, String> ExtraAttrs)
          throws Exception {
    return execute(InstSrc, ExtraAttrs, null);
  }

  private boolean restoreScene = false;

  public void setRestoreScene(boolean restore) {
    restoreScene = restore;
  }

  public String execute(String src, Map<String, String> startArgs,
                        Bot exBot) throws Exception {
    if (src == null || src.length() < 1) throw new Exception("Error: nothing to execute!");
    if (botCodes == null) throw new Exception("Error: textbot not initialized!");
    if (exBot == null) exBot = bot;
    if (exBot == null) throw new Exception("Error: no bot specified, please specify a valid bot for execution!");

    List<String> instLines = parseInLines(src);
    if (instLines.size() < 1) {
      throw new Exception("Error: no valid logic code defined in source content");
    }

    StringBuilder sb = new StringBuilder();
    int lineNum = 0;
    int lineC = 1;
    String cmd = null;
    int pos = 0;
    for (String line : instLines) {
      if (line.startsWith("#"))
        continue;
      pos = line.indexOf(".");
      lineNum = Integer.parseInt(line.substring(0, pos));
      cmd = line.substring(pos + 1);
      // FIX: lineC++, really work?
      while (lineC < lineNum) {
        lineC++;
        sb.append("\n");
      }
      sb.append(cmd);
      if (!cmd.endsWith(";"))
        sb.append(";");
    }
    src = sb.toString();
    Map<String, Object> locMetas = new LinkedHashMap<String, Object>();
    locMetas.put("?default", "");
    List<Code> locCodes = new ArrayList<Code>();
    String val = null;
    compileAction(_DEFAULT, "_main_", src, 1,
            "_main_", -1, locCodes, locMetas, null, null);
    if (locCodes.size() < 1) {
      throw new Exception("Error: no code to execute");
    }
    checkCompiledCodes(locCodes, locMetas);
    // restore purpose
    val = "";
    if (restoreScene && cxt != null) val = cxt.currentPage; // use current page
    else {
      cacheData = null;
      pageVars = null;
    }
    cxt = new Context();
    cxt.currentPage = val;
    cxt.actionPage = "";
    cxt.vars = new LinkedHashMap<String, String>();
    cxt.parentContext = null;
    cxt.actionName = "_main_";
    cxt.stackDeep = 0;
    cxt.codes = locCodes;
    cxt.metas = locMetas;
    cxt.codeStart = 0;
    cxt.codeEnd = locCodes.size();
    cxt.codePos = 0;

    // process parameters attribute
    Map<String, String> stargs = null;
    if (startArgs != null) {
      stargs = new LinkedHashMap<String, String>();
      for (String attr : startArgs.keySet()) {
        attr = attr.trim().toLowerCase();
        if (attr.length() > 0 && isValidVarName(attr)) {
          val = getData(startArgs.get(attr), locCodes.get(0), null, null);
          if (val == null) val = "";
          cxt.vars.put(attr, val);
          stargs.put(attr, val);
        }
      }
    }
    // ok now, we can go ahead to execute it!!!
    Map<String, String> paras = new LinkedHashMap<String, String>(); // a
    String data = null;
    Code code = null;
    Code lastCode = null;
    String lastPage = null;
    //boolean startAction = true;
    boolean haveData = false;
    // belong to
    while (true) {
      if (cxt.codePos >= cxt.codeEnd) {
        if (cxt.parentContext == null)
          break;
        // restore the context and continue
        val = cxt.retVal != null ? cxt.retVal : cxt.lastRet; // last
        cxt.parentContext.currentPage = cxt.currentPage; // action exit, we've use last context page
        cxt = cxt.parentContext; // decide to
        if (val != null) cxt.lastRet = val;
        continue;
      }
      code = cxt.codes.get(cxt.codePos++);
      // changed: 2018/12/18, file is not the final
      if (lastCode == null || lastCode.fileIndex != code.fileIndex) {
        // first, set macros
        paras.clear();
        for (String key : supportedMetas.keySet()) {
          val = (String) metaInfos.get("@" + key + "."
                  + code.fileIndex);
          if (val == null)
            val = (String) metaInfos.get("@" + key);
          val = getData(val, code, null, null);
          if (val == null) val = "";
          paras.put(key, val);
        }
        exBot.setMetaVars(paras); // do not couting on it
        paras.clear();

      }
      lastCode = code;
      // FIX: data could be null, if it's a call action
      data = code.data;
      haveData = false;
      pos = code.type & TB_COMMAND_MAX;
      if (data != null && data.length() > 0) {
        haveData = true;
        if (pos != TB_DEFPARA && pos != TB_GOTO && pos != TB_VAR && pos != TB_RETURN) {
          data = getData(data, code, null, null); // DEF para will be translated later
          if (data == null) data = "";
        }
      }
      if (!haveData) data = null;
      lastPage = cxt.currentPage;
      val = "";
      // normal command, have page to process
      if ((code.type & TB_COMMAND_FLAGS) == 0) {
        val = code.page.equals(_DEFAULT) ? cxt.currentPage : code.page;
        if (val == null || val.length() < 1) {
          throw new Exception(getError(
                  "Error: current there is not page inited for command:"
                          + code.command, code));
        }
        if (!code.target.startsWith("[")
                && !metaInfos.containsKey("." + val + "."
                + code.target)) {
          throw new Exception(getError(
                  "Error: element is not defined in page:"
                          + val + "." + code.target, code));
        }
      } else if ((code.type & TB_COMMAND_PAGE) != 0) {
        val = code.page.equals(_DEFAULT) ? cxt.currentPage : code.page;
        if (val == null || val.length() < 1 || val.equals(_DEFAULT)) val = cxt.currentPage;
      } else {
        val = cxt.currentPage; // system level command
      }
      cxt.currentPage = val;
      val = "";
      switch (pos) {
        case TB_COMMENT:
          // comment, do nothing
          val = cxt.lastRet;
          break;
        case TB_RETURN:
          if (data != null && data.length() > 0) {
            val = accessVar("retval", data, null, code);
          } else {
            val = cxt.lastRet;
          }
          if (val == null) val = ""; // set it
          cxt.retVal = val;
          cxt.codePos = cxt.codeEnd;
          break;
        case TB_ASSERT:
          //FIX: if assert; or assert : text, a "last" operator will be used
          if (getExprBoolean(code.target == null || code.target.length() < 1 ? "last" : code.target, code, null)) {
            val = "true";
          } else {
            throw new Exception(getError("Error: assert failure: " + (data != null ? data : "no info"), code));
          }
          break;
        case TB_GOTO:
          if (data == null || data.length() < 1 || getExprBoolean(data, code, null)) {
            cxt.codePos = (Integer) cxt.metas.get("@"
                    + code.page + "." + code.target);
            val = "true";
          } else
            val = "false";
          cxt.currentPage = cxt.actionPage;
          break;
        case TB_VAR:
          val = null;
          if (data != null && data.length() > 0) {
            val = getExpr(data, code, null);
            if (val == null) val = "";
          }
          val = accessVar(code.target, val, null, code);
          break;
        case TB_CALL:
          if (cxt.stackDeep >= Context.TBC_MAX) {
            throw new Exception(getError("Error: stack overflow at "
                            + cxt.actionPage + "." + cxt.actionName,
                    code));
          }
          // ADD: check it if page is default
          if (cxt.currentPage == null || cxt.currentPage.length() < 1) {
            throw new Exception(getError(
                    "Error: current there is not page inited for call:"
                            + code.command, code));
          }
          if (!metaInfos.containsKey("$" + cxt.currentPage + "." + code.command)) {
            throw new Exception(getError("Error: action doesn't exist: "
                    + cxt.currentPage + "." + code.command, code));
          }
          val = cxt.currentPage;
          cxt.currentPage = lastPage; // restore it
          cxt = new Context(cxt); // create new one
          cxt.actionPage = val;
          cxt.currentPage = lastPage;
          if (stargs != null) {
            for (String s : stargs.keySet()) {
              if (!paras.containsKey(s)) paras.put(s, stargs.get(s));
            }
          }
          cxt.vars = paras; // all right now!
          // try to process the default parameters in the code
          cxt.codePos = (Integer) metaInfos.get("$" + cxt.actionPage + "."
                  + code.command);
          cxt.codeEnd = (Integer) metaInfos.get("%" + cxt.actionPage + "."
                  + code.command);
          cxt.metas = metaInfos;
          cxt.codes = botCodes;
          cxt.actionName = code.command;
          cxt.lastRet = cxt.parentContext.lastRet;
          val = cxt.lastRet; // not start it yet!!
          paras = new LinkedHashMap<String, String>();
          break;
        case TB_DEFPARA:
          // FIX: def para is lower pri than args
          if (!cxt.vars.containsKey(code.command)) {
            if (haveData) data = getData(data, code, null, null);
            if (data == null) data = "";
            cxt.vars.put(code.command, data);
          }
          break;
        case TB_META:
          throw new Exception(getError("Error: code internal error, meta not supported", code));
        case TB_PARA:
          paras.put(code.command, data);
          // ADD: mark the para as passed para, this is different than
          // default para!!
          paras.put("!" + code.command, "true");
          break;
        default:
          val = null;
          if ((code.type & TB_COMMAND_FLAGS) == 0) { // it's a element, must get it // invoke normally
            val = code.target;
            if (val.startsWith("[")) {
              val = getData(val.substring(1,
                      val.length() - 1), code, null, null);
            } else {
              val = getData(
                      (String) metaInfos.get("." + cxt.currentPage + "."
                              + val), code, null, null);
            }
            // add check:
            if (val == null || val.length() < 1) {
              throw new Exception(getError(
                      "Error: internal error: access data failed for target",
                      code));
            }
          }
          try {
            if (!cxt.currentPage.equals(lastPage))
              exBot.navigating(lastPage, cxt.currentPage, getData((String) metaInfos.get("?" + cxt.currentPage),
                      code, null, null), code.command);
            if (pos == TB_INVOKE) val = exBot.invokeAction(code.command, paras, val, cxt.currentPage, cxt.lastRet);
            else val = exBot.executeCommand(code.command, val, data, cxt.currentPage, cxt.lastRet);
          } catch (Exception e) {
            throw new Exception(getError("Error: " + e.getMessage() + " at "
                            + cxt.actionPage + "." + cxt.actionName,
                    code), e);
          } finally {
            if (pos == TB_INVOKE) paras.clear();
          }
          break;
      }
      cxt.lastRet = val == null ? "" : val;
    }
    return cxt.retVal != null ? cxt.retVal : cxt.lastRet;
  }

  public void uninitialize() {
    botCodes = null;
    metaInfos = null;
    descInfos = null;
    if(pageVars != null)pageVars.clear();
    pageVars = null;
    if(cacheData != null)cacheData.clear();
    cacheData = null;
    if(bot != null)bot.stop();
    bot = null;
  }

  @Override
  protected void accessMember(String varName, String var, String memberName, Oper out, Object uo) {
    if(varName != null){
      accessVariable(varName + "." + memberName,out,uo);
    }else{
      out.err = "not defined: " + (varName == null ? var : varName) + "." + memberName;
    }
  }

  @Override
  protected void accessVariable(String varName, Oper out, Object uo) {
    try {
      out.isNum = false;
      out.Val = accessVar(varName, null, null, (Code) uo);
      if(out.Val== null)out.err = varName + " not defined";
    } catch (Exception e) {
      out.err = e.getMessage();
    }
  }

  /**
   * API support for expression
   *
   * @param apiName
   * @param paras
   * @param out
   */
  @Override
  protected void processAPI(String apiName, List<Oper> paras, Oper out, Object uo) {
    out.isNum = false;
    out.Val = "";
    String[] ps = null;
    if (paras != null && paras.size() > 0) {
      ps = new String[paras.size()];
      for (int i = 0; i < ps.length; i++) ps[i] = paras.get(i).Val;
    }
    try {
      out.Val = bot.executeAPI(apiName, ps);
      if (out.Val == null) out.Val = "";
    } catch (Exception e) {
      StackTraceElement[] eles = e.getStackTrace();
      StackTraceElement[] parEles = e.getCause() != null ? e.getCause().getStackTrace() : null;
      out.err = getError("Bot API Error: " +
              (e.getMessage() != null ? e.getMessage() : e.getClass().getName()) +
              (eles != null ? "/" + eles[0].toString() : "") +
              (parEles != null ? "/" + parEles[0].toString() : ""), (Code) uo);
    }
  }

  //public void setBot(Bot bot) {
  //  if (this.bot != null && bot != null) this.bot = bot;
  //}

  public Bot getBot(){
    return bot;
  }

}
