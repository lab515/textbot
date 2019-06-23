package io.github.lab515.textbot;

class Code {
  public int fileIndex = 0;
  public int line = 0;
  public int type = 0; // -1: comment, 0: 1: normal instructions,
  public String page = null;
  public String target = null;
  public String command = null; // such as default
  public String data = null;

  public Code(int FileIndex, int Line, int Type, String Page, String Target,
              String Command, String Data) {
    fileIndex = FileIndex;
    line = Line;
    type = Type; // -1: comment, 0: 1: normal instructions,
    page = Page;
    target = Target;
    command = Command; // such as default
    data = Data;
  }
}