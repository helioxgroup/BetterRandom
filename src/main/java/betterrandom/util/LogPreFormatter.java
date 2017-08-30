package betterrandom.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogPreFormatter {

  private final Logger logger;

  public LogPreFormatter(Logger logger) {
    this.logger = logger;
  }

  public LogPreFormatter(Class<?> clazz) {
    this.logger = Logger.getLogger(clazz.getName());
  }

  public void format(Level level, String formatString, Object... args) {
    if (logger.isLoggable(level)) {
      logger.log(level, String.format(formatString, (Object[]) args));
    }
  }

  public void error(String formatString, Object... args) {
    format(Level.SEVERE, formatString, (Object[]) args);
  }

  public void warn(String formatString, Object... args) {
    format(Level.WARNING, formatString, (Object[]) args);
  }

  public void info(String formatString, Object... args) {
    format(Level.INFO, formatString, (Object[]) args);
  }

  public void verbose(String formatString, Object... args) {
    format(Level.CONFIG, formatString, (Object[]) args);
  }

  public void debug(String formatString, Object... args) {
    format(Level.FINE, formatString, (Object[]) args);
  }

  public void trace(String formatString, Object... args) {
    format(Level.FINER, formatString, (Object[]) args);
  }

  public void finetrace(String formatString, Object... args) {
    format(Level.FINEST, formatString, (Object[]) args);
  }
}
