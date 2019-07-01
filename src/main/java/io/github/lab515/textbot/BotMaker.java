package io.github.lab515.textbot;

public interface BotMaker {
  String[] getCommands();
  int[] getCommandFlags();
  String[] getContextVariables();
  String[] getActions();
  String[] getMetaVariables();
  String[] getReservedKeywords();
  String getSource();
}
