package io.github.lab515.textbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Bot {

  protected abstract void navigating(String oldPage, String newPage, String url, String cmdOrAction) throws Exception;

  protected abstract void setMetaVars(Map<String, String> metas) throws Exception;

  protected abstract String executeCommand(String command, String target, String data, String page, String lastRet) throws Exception;

  protected abstract String executeAPI(String name, String[] paras) throws Exception;

  protected abstract String invokeAction(String name, Map<String, String> paras, String target, String page, String lastRet) throws Exception; // differeent between invoke action and execute api, it provide chance for driver to distinguish them

  protected abstract String setContextVar(String name, String val) throws Exception;

  protected abstract String getContextVar(String name) throws Exception;

  protected abstract String getInstantVar(String name) throws Exception;

  protected abstract void log(String Val);

  protected abstract void stop();

  // implementation for basic usage
  private static final ConcurrentHashMap<String,TextBot> _bots = new ConcurrentHashMap<String,TextBot>();

  private TextBot loadTextBot(boolean force, BotMaker botMaker) throws Exception{
    String path = null;
    if(botMaker == null || (path = botMaker.getSource()) == null)throw new Exception("botMaker or source is null");
    String clsName = this.getClass().getCanonicalName();
    String key = clsName + ":::" + path;
    TextBot bt = _bots.get(key);
    if(bt != null && !force)return bt; // already initialized

    bt = TextBot.initialize(
            path,
            botMaker.getCommands(),
            botMaker.getCommandFlags(),
            botMaker.getContextVariables(),
            botMaker.getActions(),
            botMaker.getMetaVariables(),
            botMaker.getReservedKeywords());
    _bots.put(key,bt);
    return bt;
  }

  private static ThreadLocal<TextBotHolder> theBot = new ThreadLocal<TextBotHolder>();


  private static class TextBotHolder {
    public TextBot tb = null;
    public String id = null;
    public TextBotHolder(String id, TextBot tb) throws Exception {
      this.tb = tb;
      this.id = id;
    }

    public boolean qualify(String id, Bot bot) {
      return (id == null || id.equals(this.id)) && (bot == null || bot.getClass() == tb.getBot().getClass());
    }

  }

  protected static void cleanUp(){
    if (theBot.get() != null) {
      theBot.get().tb.uninitialize(); // stop if possbile
      theBot.remove();
    }
  }
  protected TextBot loadTextBotInst(boolean reuse, boolean restore, String identifier, BotMaker botMaker, boolean force) throws Exception{
    TextBot tb = null;
    if(reuse && theBot.get() != null && theBot.get().qualify(identifier,this)){
        tb = theBot.get().tb;
        tb.setRestoreScene(restore);
        stop(); // simply stop current instance
        return tb;
    }

    // load from textbot
    tb = loadTextBot(force,botMaker);
    tb = tb.simpleClone(this);
    // ok, meanwhile, stop anything
    if(reuse) {
      cleanUp();
      theBot.set(new TextBotHolder(identifier, tb));
    }
    return tb;
  }
}