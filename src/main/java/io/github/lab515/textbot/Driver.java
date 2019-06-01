package io.github.lab515.textbot;

import java.util.Map;

public interface Driver {

    void navigating(String oldPage, String newPage, String url, String cmdOrAction) throws Exception;

    void setMetaVars(Map<String, String> metas)throws Exception;

    String executeCommand(String command, String target, String data, String page, String lastRet) throws Exception;

    String executeAPI(String name, String[] paras) throws Exception;

    String invokeAction(String name, Map<String, String> paras, String target, String page,String lastRet) throws Exception; // differeent between invoke action and execute api, it provide chance for driver to distinguish them

    String setContextVar(String name, String val) throws Exception;

    String getContextVar(String name)throws Exception;

    String getInstantVar(String name)throws Exception;

    void log(String Val);

}