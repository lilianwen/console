package console;

import java.io.IOException;

public interface ConsoleFace {
  void welcome();
  void close();
  void help(String[] params);
  void deploy(String[] params) throws Exception;
  void call(String[] params) throws Exception;
  void init(String[] args);
}
