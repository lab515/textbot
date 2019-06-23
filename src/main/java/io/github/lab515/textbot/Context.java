package io.github.lab515.textbot;

import java.util.List;
import java.util.Map;

class Context {
  public String retVal = null;
  public String lastRet = null;
  public String actionName = null;
  public String actionPage = null;
  public Context parentContext = null;
  public String currentPage = null;
  public Map<String, String> vars = null;
  public int stackDeep = 0;
  public List<Code> codes = null;
  public Map<String, Object> metas = null;
  public int codeStart = 0;
  public int codeEnd = 0;
  public int codePos = 0;

  public static final int TBC_MAX = 4096;

  public Context() {
  }

  public void clear() {
    if (vars != null) vars.clear();
  }

  public Context(Context parent) {
    parentContext = parent;
    stackDeep = parent.stackDeep + 1;
  }
}
