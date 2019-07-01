package io.github.lab515.textbot;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class DefaultBot extends Bot implements BotMaker {
  @Override
  public String[] getCommands(){
    return new String[]{"close", "shutdown", "switch", "sleep",
            "open", "alert", "prompt", "cookie",
            "eval", "match", "wait", "click",
            "click2", "type", "select", "text",
            "exists", "count", "value", "check"};
  }
  @Override
  public int[] getCommandFlags(){
    return new int[]{TextBot.TB_COMMAND_PAGE, TextBot.TB_COMMAND_SYSTEM, TextBot.TB_COMMAND_SYSTEM, TextBot.TB_COMMAND_SYSTEM,
            TextBot.TB_COMMAND_PAGE, TextBot.TB_COMMAND_SYSTEM, TextBot.TB_COMMAND_SYSTEM, TextBot.TB_COMMAND_SYSTEM,
            TextBot.TB_COMMAND_SYSTEM, TextBot.TB_COMMAND_SYSTEM, TextBot.TB_COMMAND_PAGE};
  }
  @Override
  public String[] getContextVariables(){
    return new String[]{"url", "bodytext", "source", "title", "timeout"};
  }
  @Override
  public String[] getActions(){
    return new String[]{"exec"};
  }
  @Override
  public String[] getMetaVariables(){
    return null;
  }
  @Override
  public String[] getReservedKeywords(){
    return null;
  }

  private Map<String, String> macs;
  private TextBot tbot = null;
  private boolean opened = true;

  public DefaultBot(String identifier, boolean reuse, boolean restore) throws Exception{
    tbot = loadTextBotInst(reuse,restore,identifier,this,false);
  }

  public String findAction(String match, String cmtPrefix, String pkg) {
    String realAction = null;
    if (pkg != null) {
      pkg = pkg.trim();
      if (pkg.length() > 0) pkg = pkg.substring(1).trim();
      if (pkg.length() < 1) pkg = null;
      else pkg = pkg.replace('/', '.');
    }
    for (String p : tbot.getPages()) {
      String dd = tbot.getPageComment(p);
      if (pkg != null) {
        if (dd == null || !dd.startsWith("##") || dd.length() < 3) continue;
        dd = dd.substring(2).trim().replace('/', '.');
        if (!dd.equalsIgnoreCase(pkg)) continue;
      }
      for (String a : tbot.getActions(p)) {
        String pt = tbot.getActionComment(p, a); // step match
        if (pt == null) continue;
        if ((cmtPrefix == null || pt.startsWith(cmtPrefix)) && pt.indexOf(match) >= 0) {
          realAction = p + "." + a + "();";
        }
      }
    }
    return realAction;
  }

  public String execute(String src, Map<String, String> args) throws Exception {
    return tbot.execute(src, args, null);
  }

  public String execute(String src, Map<String, String> args, Bot exBot) throws Exception {
    return tbot.execute(src, args, exBot);
  }

  @Override
  public void setMetaVars(Map<String, String> macros) throws Exception {
    if(macs == null)macs = new LinkedHashMap<String,String>();
    else macs.clear();
    for (String key : macros.keySet())
      macs.put(key, macros.get(key));

    // extra logic: check the timeout
    String tm = macs.get("timeout");
    if (tm == null)
      tm = "300000";
    try {
      if (Integer.parseInt(tm) < 1000)
        tm = "300000";
    } catch (Exception e) {
      tm = "300000";
    }
    macs.put("timeout", tm);
    if (!macs.containsKey("root"))
      macs.put("root", "http://localhost");
    tm = getInstantVar("uiSkipError");
    log("Set Macros: " + macs);
  }

  private String getPageUrl(String url) throws Exception {
    // first of all, get the rooter
    if (url == null || url.length() < 1)url = currentPageUrl;
    if(url != null && url.startsWith("/")) {
      String root = null;
      int p = currentPageUrl.indexOf(':');
      if (currentPageUrl != null && !currentPageUrl.startsWith("/") && p > 0 && p < currentPageUrl.length() - 3) {
        p = currentPageUrl.indexOf('/', p + 3);
        if (p > 0) url = currentPageUrl.substring(0, p) + url;
        else url = currentPageUrl + url;
      } else {
        url = macs.get("root") + url;
      }
    }else if(url == null || url.length() < 1)url = macs.get("root");
    return url;
  }

  protected abstract void open(String url) throws Exception;

  protected abstract String alert(boolean accept) throws Exception;

  protected abstract String prompt(String input) throws Exception;

  protected abstract String eval(String js) throws Exception;

  protected abstract void click(String target) throws Exception;

  protected abstract String type(String target, String input) throws Exception;

  protected abstract List<String> options(String target, boolean all) throws Exception;

  protected abstract void select(String target, String options, int mode, boolean doSelect) throws Exception;

  protected abstract String text(String target, boolean valueFirst) throws Exception;

  protected abstract boolean exists(String target) throws Exception;

  protected abstract int count(String target) throws Exception;

  protected abstract String check(String target, String op) throws Exception;

  protected abstract String url(boolean urlOrTitle) throws Exception; // get url or title

  protected abstract void timeout(int ms) throws Exception;

  protected abstract void close() throws Exception;

  @Override
  public String executeCommand(String command, String target, String data, String page, String lastRet) throws Exception {
    log("Command: " + command + ", page: " + page + ", target: " + target + ",data: " + (data != null ? (data.length() > 10 ? data.substring(0, 7) + "..." : data) : "n/a"));
    if (command.equals("close") || command.equals("shutdown")) {
      close();
      opened = false;
      return "true";
    } else if (!opened && !command.equals("open")) {
      open(getPageUrl(null));
      opened = true;
    }
    String ret = "";
    int order = 0;

    if (command.equals("switch")) {
      throw new Exception("switch is currently not supported yet!");
    } else if (command.equals("sleep")) {
      order = 1000;
      if (data != null && data.length() > 0) {
        order = Integer.parseInt(data);
        if (order < 1000)
          order = 1000;
      }
      log("Sleep:" + order + "ms");
      Thread.sleep(order);
      ret = "true";
    } else if (command.equals("open")) {
      ret = getPageUrl(data);
      log("OpenPage: " + ret);
      open(ret);
    } else if (command.equals("alert")) { // alert/confirm together
      ret = alert(!(data != null && data.toLowerCase().trim().equals("false")));
    } else if (command.equals("prompt")) {
      prompt(data);
    } else if (command.equals("cookie")) {
      // for now we can only try to leverage the javascript
      if (data != null && data.length() > 0) {
        if (data.substring(0, 1).equals("+")) {
          data = data.substring(1).trim();
          if (data.length() > 0) {
            eval("document.cookie = \"" + data.replace("\"", "\\\"") + "\"");
            ret = "true";
          } else
            ret = "false";
        } else if (data.substring(0, 1).equals("-")) {
          data = data.substring(1).trim();
          if (data.length() > 0) {
            eval("document.cookie = \"" + data.replace("\"", "\\\"") + "; expires=Thu, 01 Jan 1970 00:00:00 UTC;\"");
            ret = "true";
          } else
            ret = "false";
        } else {
          data = data.trim() + "=";
          ret = (String) eval("return document.cookie");
          if (ret != null) {
            for (String ar : ret.split(";")) {
              ar = ar.trim();
              if (ar.startsWith(data)) {
                data = null;
                ret = ar.substring(data.length());
                break;
              }
            }
            if (data != null) ret = "";
          }
        }
      } else
        ret = (String) eval("return document.cookie");
    } else if (command.equals("wait")) {
      if (data != null) data = data.trim();
      if (data == null || data.trim().length() < 1) {
        data = macs.get("timeout");
      }
      ret = "false";
      if (data.charAt(0) == '?') {
        int nums = Integer.parseInt(macs.get("timeout"));
        data = data.substring(1).trim();
        log("Wait for condition:" + data);
        while (nums >= 0 && data.length() > 0) {
          ret = eval(data);
          if (ret == null) ret = "";
          if (ret.equals("true") || ret.equals("1") || ret.equals("-1") || ret.equals("complete")) {
            ret = "true";
            break;
          }
          nums = waitMs(nums);
        }
      } else {
        log("WaitPageLoad:" + data + " mini-seconds"); // in ms
        int nums = Integer.parseInt(data);
        while (nums >= 0) {
          ret = "" + eval("return document.readyState");
          if (ret.equals("complete")) {
            ret = "true";
            break;
          }
          ret = "false";
          nums = waitMs(nums);
        }
      }
    } else if (command.equals("match")) {
      ret = null;
      if (data != null && data.length() > 0) {
        if (data.startsWith("\"") && data.endsWith("\"")) {
          if (lastRet != null && data.length() == 2 || lastRet.indexOf(data.substring(1, data.length() - 1)) >= 0)
            ret = "true";
        } else if (Pattern.matches(data, lastRet)) ret = "true";

      } else if (lastRet == null || lastRet.length() < 1) ret = "true";
      if (ret == null) {
        if (!macs.containsKey("ignoreerror"))
          throw new Exception("match failure, pattern: " + data + ", target: " + lastRet);
      }
      ret = lastRet;
    } else if (command.equals("eval")) {
      if (data != null && data.length() > 0) {
        ret = eval(data);
      } else ret = "";
    } else if (command.equals("click")) {
      click(target);
      ret = "true";
    } else if (command.equals("click2")) {
      click(target);
      if (data == null || data.trim().length() < 1) {
        data = macs.get("timeout");
      }
      log("WaitPageLoad(click2):" + data + "mini-seconds");
      int nums = Integer.parseInt(data);
      while (nums >= 0) {
        ret = eval("return document.readyState");
        if (ret != null && ret.equals("complete")) {
          ret = "true";
          break;
        }
        ret = "false";
        nums = waitMs(nums);
      }
    } else if (command.equals("type")) {
      if (data == null) data = "";
      type(target, data);
      ret = data;
    } else if (command.equals("select")) {
      if (data == null || data.length() < 1) {
        List<String> opts = options(target, true);
        ret = "";
        if (opts != null && opts.size() > 0) {
          ret = opts.get(0);
          for (int i = 1; i < opts.size(); i++) ret += "," + opts.get(i);
        }
      } else {
        ret = "true";
        if (data.startsWith("-")) {
          data = data.substring(1).trim();
          if (data.length() < 1) select(target, null, 0, false);
          else {
            order = data.indexOf('=');
            if (order > 0 && data.substring(0, order).trim().equalsIgnoreCase("index")) {
              select(target, data.substring(order + 1).trim(), 0, false);
            } else if (order > 0 && data.substring(0, order).trim().equalsIgnoreCase("value")) {
              select(target, data.substring(order + 1).trim(), 1, false);
            } else if (order > 0 && data.substring(0, order).trim().equalsIgnoreCase("text")) {
              select(target, data.substring(order + 1).trim(), 2, false);
            } else select(target, data, 2, false);
          }
        } else {
          if (data.startsWith("+")) data = data.substring(1).trim(); // + normally means nothing
          if (data.length() < 1) {
            List<String> opts = options(target, false);
            ret = "";
            if (opts != null && opts.size() > 0) {
              ret = opts.get(0);
              for (int i = 1; i < opts.size(); i++) ret += "," + opts.get(i);
            }
          } else {
            order = data.indexOf('=');
            if (order > 0 && data.substring(0, order).trim().equalsIgnoreCase("index")) {
              select(target, data.substring(order + 1).trim(), 0, true);
            } else if (order > 0 && data.substring(0, order).trim().equalsIgnoreCase("value")) {
              select(target, data.substring(order + 1).trim(), 1, true);
            } else if (order > 0 && data.substring(0, order).trim().equalsIgnoreCase("text")) {
              select(target, data.substring(order + 1).trim(), 2, true);
            } else select(target, data, 2, true);
          }
        }

      }

    } else if (command.equals("text")) {
      ret = text(target, false);
    } else if (command.equals("exists")) {
      ret = exists(target) ? "true" : "false";
    } else if (command.equals("count")) {
      ret = count(target) + "";
    } else if (command.equals("value")) {
      ret = text(target, true);
    } else if (command.equals("check")) {
      if (data == null || data.length() < 1) {
        ret = check(target, null);
      } else {
        ret = "true";
        data = data.trim().toLowerCase();
        if (data.equals("") || data.equals("false") || data.equals("0") || data.equals("n/a") || data.equals("null") || data.equals("no") || data.equals("unchecked"))
          ret = "false";
        if (ret.equals("true")) {
          log("Check: " + target);
        } else {
          log("UnCheck: " + target);
        }
        check(target, ret);
      }
    } else {
      throw new Exception("Unsupported Command: " + command);
    }
    return ret;
  }

  protected abstract String executeAPIExternal(String name, String[] ps, boolean actionMode) throws Exception;

  private String currentPageUrl = null;

  @Override
  public void navigating(String oldPage, String newPage, String url, String cmdOrAction) throws Exception {
    currentPageUrl = url;
    if (cmdOrAction.equals("open")) return;
    log("page change: from " + (oldPage != null && oldPage.length() > 0 ? oldPage : "n/a") + " to " + newPage);
    // let other bot handle the page switching.
    open(getPageUrl(url));
  }

  @Override
  public String executeAPI(String name, String[] ps) throws Exception {
    return executeAPIExternal(name, ps, false);
  }


  @Override
  public String invokeAction(String name, Map<String, String> paras, String target, String page, String lastRet) throws Exception {
    log("Invoke: " + name + ", page: " + page + ", parameters: " + paras.size() + ",target: " + target);
    // format for parameter list, name= foramt
    ArrayList<String> ps = new ArrayList<String>(); // lastRet is ignored here!!
    ps.add( page + "." + (target == null ? "" : target)); // target
    if (paras != null) {
      for (String s : paras.keySet()) {
        if (s.startsWith("!")) continue;
        ps.add(s + "=" + paras.get(s));
      }
    }
    String[] ps2 = new String[ps.size()];
    ps.toArray(ps2);
    ps.clear();
    return executeAPIExternal(name, ps2, true);
  }

  @Override
  public String setContextVar(String name, String val) throws Exception {
    log("Set Internal Var: " + name + ", value: " + val);
    if (name.equals("url")) {
      return url(true);
    }
    if (name.equals("source")) {
      return (String) eval("return document.body.innerHTML;");
    }
    if (name.equals("bodytext")) {
      return (String) eval("return document.body.innerText;");
    }
    if (name.equals("timeout")) {
      timeout(Integer.parseInt(val));
      macs.put("timeout", val);
      return val;
    }
    if (name.equals("title")) {
      return url(false);
    } else {
      return null;
    }
  }

  @Override
  public String getContextVar(String name) throws Exception {
    log("Get Internal Var: " + name);
    if (name.equals("url")) {
      return url(true);
    }
    if (name.equals("source")) {
      return (String) eval("return document.body.innerHTML;");
    }
    if (name.equals("bodytext")) {
      return (String) eval("return document.body.innerText;");
    }
    if (name.equals("timeout")) {
      return macs.get("timeout");
    }
    if (name.equals("title")) {
      return url(false);
    } else {
      return macs.get(name); // macro > instantVar
    }
  }


  private int waitMs(int waiter) {
    if (waiter > 0) {
      try {
        Thread.sleep(waiter >= 1000 ? 1000 : waiter);
      } catch (InterruptedException e) {
      }
      waiter -= 1000;
      return waiter < 0 ? 0 : waiter;
    } else {
      return -1;
    }
  }

  public TextBot getTextBot(){return tbot;}
}
