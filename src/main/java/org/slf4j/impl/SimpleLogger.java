package org.slf4j.impl; /**
 * Copyright (c) 2004-2012 QOS.ch
 * All rights reserved.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 * <p>
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 * <p>
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import org.fusesource.jansi.Ansi;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.Util;
import org.slf4j.spi.LocationAwareLogger;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.fusesource.jansi.Ansi.ansi;

public class SimpleLogger extends MarkerIgnoringBase {

    private static final long serialVersionUID = -632788891211436180L;
    private static final String CONFIGURATION_FILE = "simplelogger.properties";

    private static long START_TIME = System.currentTimeMillis();
    private static final Properties SIMPLE_LOGGER_PROPS = new Properties();

    private static final int LOG_LEVEL_TRACE = LocationAwareLogger.TRACE_INT;
    private static final int LOG_LEVEL_DEBUG = LocationAwareLogger.DEBUG_INT;
    private static final int LOG_LEVEL_INFO = LocationAwareLogger.INFO_INT;
    private static final int LOG_LEVEL_WARN = LocationAwareLogger.WARN_INT;
    private static final int LOG_LEVEL_ERROR = LocationAwareLogger.ERROR_INT;

    private static boolean INITIALIZED = false;

    private static int DEFAULT_LOG_LEVEL = LOG_LEVEL_INFO;
    private static boolean SHOW_DATE_TIME = false;
    private static String DATE_TIME_FORMAT_STR = null;
    private static DateFormat DATE_FORMATTER = null;
    private static boolean SHOW_THREAD_NAME = true;
    private static boolean SHOW_LOG_NAME = true;
    private static boolean SHOW_SHORT_LOG_NAME = false;
    private static String LOG_FILE = "System.err";
    private static PrintStream TARGET_STREAM = null;
    private static boolean LEVEL_IN_BRACKETS = false;
    private static String WARN_LEVEL_STRING = "WARN";


    /** All system properties used by <code>org.slf4j.impl.SimpleLogger</code> start with this prefix */
    public static final String SYSTEM_PREFIX = "org.slf4j.simpleLogger.";

    public static final String DEFAULT_LOG_LEVEL_KEY = SYSTEM_PREFIX + "defaultLogLevel";
    public static final String SHOW_DATE_TIME_KEY = SYSTEM_PREFIX + "showDateTime";
    public static final String DATE_TIME_FORMAT_KEY = SYSTEM_PREFIX + "dateTimeFormat";
    public static final String SHOW_THREAD_NAME_KEY = SYSTEM_PREFIX + "showThreadName";
    public static final String SHOW_LOG_NAME_KEY = SYSTEM_PREFIX + "showLogName";
    public static final String SHOW_SHORT_LOG_NAME_KEY = SYSTEM_PREFIX + "showShortLogName";
    public static final String LOG_FILE_KEY = SYSTEM_PREFIX + "logFile";
    public static final String LEVEL_IN_BRACKETS_KEY = SYSTEM_PREFIX + "levelInBrackets";
    public static final String WARN_LEVEL_STRING_KEY = SYSTEM_PREFIX + "warnLevelString";


    public static final String LOG_KEY_PREFIX = SYSTEM_PREFIX + "log.";

    private static final int COLOR_SEPARATOR = -2;
    private static final int COLOR_BUILD_SUCCESS = -3;
    private static final int COLOR_BUILD_FAILED = -4;
    private static final int COLOR_PLUGIN = -5;
    private static final int COLOR_DEFAULT = -6;
    private static final Map<Integer, Ansi.Color> COLORS = new HashMap<Integer, Ansi.Color>();

    private static String getStringProperty(String name) {
        String prop = null;
        try {
            prop = System.getProperty(name);
        } catch (SecurityException e) {
            // Ignore
        }
        return (prop == null) ? SIMPLE_LOGGER_PROPS.getProperty(name) : prop;
    }

    private static String getStringProperty(String name, String defaultValue) {
        String prop = getStringProperty(name);
        return (prop == null) ? defaultValue : prop;
    }

    private static boolean getBooleanProperty(String name, boolean defaultValue) {
        String prop = getStringProperty(name);
        return (prop == null) ? defaultValue : "true".equalsIgnoreCase(prop);
    }


    // Initialize class attributes.
    // Load properties file, if found.
    // Override with system properties.
    static void init() {
        INITIALIZED = true;
        loadProperties();

        String defaultLogLevelString = getStringProperty(DEFAULT_LOG_LEVEL_KEY, null);
        if (defaultLogLevelString != null)
            DEFAULT_LOG_LEVEL = stringToLevel(defaultLogLevelString);

        SHOW_LOG_NAME = getBooleanProperty(SHOW_LOG_NAME_KEY, SHOW_LOG_NAME);
        SHOW_SHORT_LOG_NAME = getBooleanProperty(SHOW_SHORT_LOG_NAME_KEY, SHOW_SHORT_LOG_NAME);
        SHOW_DATE_TIME = getBooleanProperty(SHOW_DATE_TIME_KEY, SHOW_DATE_TIME);
        SHOW_THREAD_NAME = getBooleanProperty(SHOW_THREAD_NAME_KEY, SHOW_THREAD_NAME);
        DATE_TIME_FORMAT_STR = getStringProperty(DATE_TIME_FORMAT_KEY, DATE_TIME_FORMAT_STR);
        LEVEL_IN_BRACKETS = getBooleanProperty(LEVEL_IN_BRACKETS_KEY, LEVEL_IN_BRACKETS);
        WARN_LEVEL_STRING = getStringProperty(WARN_LEVEL_STRING_KEY, WARN_LEVEL_STRING);

        LOG_FILE = getStringProperty(LOG_FILE_KEY, LOG_FILE);
        TARGET_STREAM = computeTargetStream(LOG_FILE);

        COLORS.put(LOG_LEVEL_TRACE, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.TRACE", "GREEN")));
        COLORS.put(LOG_LEVEL_DEBUG, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.DEBUG", "GREEN")));
        COLORS.put(LOG_LEVEL_ERROR, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.ERROR", "RED")));
        COLORS.put(LOG_LEVEL_WARN, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.WARN", "YELLOW")));
        COLORS.put(LOG_LEVEL_INFO, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.INFO", "DEFAULT")));
        COLORS.put(COLOR_DEFAULT, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.default", "DEFAULT")));
        COLORS.put(COLOR_BUILD_SUCCESS, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.build.success", "GREEN")));
        COLORS.put(COLOR_BUILD_FAILED, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.build.failed", "RED")));
        COLORS.put(COLOR_PLUGIN, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.plugin", "BLUE")));
        COLORS.put(COLOR_SEPARATOR, Ansi.Color.valueOf(getStringProperty(SYSTEM_PREFIX + "color.separator", "BLUE")));

        if (DATE_TIME_FORMAT_STR != null) {
            try {
                DATE_FORMATTER = new SimpleDateFormat(DATE_TIME_FORMAT_STR);
            } catch (IllegalArgumentException e) {
                Util.report("Bad date format in " + CONFIGURATION_FILE + "; will output relative time", e);
            }
        }
    }


    private static PrintStream computeTargetStream(String logFile) {
        if ("System.err".equalsIgnoreCase(logFile))
            return System.err;
        else if ("System.out".equalsIgnoreCase(logFile)) {
            return System.out;
        } else {
            try {
                FileOutputStream fos = new FileOutputStream(logFile);
                PrintStream printStream = new PrintStream(fos);
                return printStream;
            } catch (FileNotFoundException e) {
                Util.report("Could not open [" + logFile + "]. Defaulting to System.err", e);
                return System.err;
            }
        }
    }

    private static void loadProperties() {
        // Add props from the resource simplelogger.properties
        InputStream in = (InputStream) AccessController.doPrivileged(
                new PrivilegedAction() {
                    public Object run() {
                        ClassLoader threadCL = Thread.currentThread().getContextClassLoader();
                        if (threadCL != null) {
                            return threadCL.getResourceAsStream(CONFIGURATION_FILE);
                        } else {
                            return ClassLoader.getSystemResourceAsStream(CONFIGURATION_FILE);
                        }
                    }
                });
        if (null != in) {
            try {
                SIMPLE_LOGGER_PROPS.load(in);
                in.close();
            } catch (java.io.IOException e) {
                // ignored
            }
        }
    }

    /** The current log level */
    protected int currentLogLevel = LOG_LEVEL_INFO;
    /** The short name of this simple log instance */
    private transient String shortLogName = null;

    /**
     * Package access allows only {@link SimpleLoggerFactory} to instantiate
     * org.slf4j.impl.SimpleLogger instances.
     */
    SimpleLogger(String name) {
        if (!INITIALIZED) {
            init();
        }
        this.name = name;

        String levelString = recursivelyComputeLevelString();
        if (levelString != null) {
            this.currentLogLevel = stringToLevel(levelString);
        } else {
            this.currentLogLevel = DEFAULT_LOG_LEVEL;
        }
    }

    String recursivelyComputeLevelString() {
        String tempName = name;
        String levelString = null;
        int indexOfLastDot = tempName.length();
        while ((levelString == null) && (indexOfLastDot > -1)) {
            tempName = tempName.substring(0, indexOfLastDot);
            levelString = getStringProperty(LOG_KEY_PREFIX + tempName, null);
            indexOfLastDot = String.valueOf(tempName).lastIndexOf(".");
        }
        return levelString;
    }

    private static int stringToLevel(String levelStr) {
        if ("trace".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_TRACE;
        } else if ("debug".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_DEBUG;
        } else if ("info".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_INFO;
        } else if ("warn".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_WARN;
        } else if ("error".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_ERROR;
        }
        // assume INFO by default
        return LOG_LEVEL_INFO;
    }


    /**
     * This is our internal implementation for logging regular (non-parameterized)
     * log messages.
     *
     * @param level   One of the LOG_LEVEL_XXX constants defining the log level
     * @param message The message itself
     * @param t       The exception whose stack trace should be logged
     */
    private void log(int level, String message, Throwable t) {
        if (!isLevelEnabled(level)) {
            return;
        }

        StringBuilder buf = new StringBuilder(32);

        // Append date-time if so configured
        if (SHOW_DATE_TIME) {
            if (DATE_FORMATTER != null) {
                buf.append(getFormattedDate());
                buf.append(' ');
            } else {
                buf.append(System.currentTimeMillis() - START_TIME);
                buf.append(' ');
            }
        }

        // Append current thread name if so configured
        if (SHOW_THREAD_NAME) {
            buf.append('[');
            buf.append(Thread.currentThread().getName());
            buf.append("] ");
        }

        if (LEVEL_IN_BRACKETS) buf.append('[');

        // Append a readable representation of the log level
        switch (level) {
            case LOG_LEVEL_TRACE:
                buf.append("TRACE");
                break;
            case LOG_LEVEL_DEBUG:
                buf.append("DEBUG");
                break;
            case LOG_LEVEL_INFO:
                buf.append("INFO");
                break;
            case LOG_LEVEL_WARN:
                buf.append(WARN_LEVEL_STRING);
                break;
            case LOG_LEVEL_ERROR:
                buf.append("ERROR");
                break;
        }
        if (LEVEL_IN_BRACKETS) buf.append(']');
        buf.append(' ');

        // Append the name of the log instance if so configured
        if (SHOW_SHORT_LOG_NAME) {
            if (shortLogName == null) shortLogName = computeShortName();
            buf.append(String.valueOf(shortLogName)).append(" - ");
        } else if (SHOW_LOG_NAME) {
            buf.append(String.valueOf(name)).append(" - ");
        }

        // Append the message
        buf.append(message);

        if (t != null) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.printStackTrace(new PrintStream(baos));
            buf.append(System.getProperty("line.separator")).append(new String(buf));
        }

        write(level, buf.toString());
    }

    private void write(int level, String line) {
        if (line.endsWith("------------------------------------------------------------------------")) {
            dump(COLORS.get(COLOR_SEPARATOR), line);
            return;
        } else if (line.contains("--- ") && line.contains(" @ ") && line.endsWith("---")) {
            dump(COLORS.get(COLOR_PLUGIN), line);
            return;
        } else if (line.contains("BUILD ")) {
            if (line.endsWith("SUCCESS")) {
                dump(COLORS.get(COLOR_BUILD_SUCCESS), line);
                return;
            } else {
                dump(COLORS.get(COLOR_BUILD_FAILED), line);
                return;
            }
        }

        Ansi.Color color = COLORS.get(level);
        if (color == null) {
            color = COLORS.get(COLOR_DEFAULT);
        }
        dump(color, line);
    }

    private void dump(Ansi.Color color, String line) {
        TARGET_STREAM.println(ansi().fg(color).a(line).reset());
        TARGET_STREAM.flush();
    }

    private String getFormattedDate() {
        Date now = new Date();
        String dateText;
        synchronized (DATE_FORMATTER) {
            dateText = DATE_FORMATTER.format(now);
        }
        return dateText;
    }

    private String computeShortName() {
        return name.substring(name.lastIndexOf(".") + 1);
    }

    /**
     * For formatted messages, first substitute arguments and then log.
     *
     * @param level
     * @param format
     * @param arg1
     * @param arg2
     */
    private void formatAndLog(int level, String format, Object arg1,
                              Object arg2) {
        if (!isLevelEnabled(level)) {
            return;
        }
        FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
        log(level, tp.getMessage(), tp.getThrowable());
    }

    /**
     * For formatted messages, first substitute arguments and then log.
     *
     * @param level
     * @param format
     * @param arguments a list of 3 ore more arguments
     */
    private void formatAndLog(int level, String format, Object... arguments) {
        if (!isLevelEnabled(level)) {
            return;
        }
        FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        log(level, tp.getMessage(), tp.getThrowable());
    }

    /**
     * Is the given log level currently enabled?
     *
     * @param logLevel is this level enabled?
     */
    protected boolean isLevelEnabled(int logLevel) {
        // log level are numerically ordered so can use simple numeric
        // comparison
        return (logLevel >= currentLogLevel);
    }

    /** Are {@code trace} messages currently enabled? */
    public boolean isTraceEnabled() {
        return isLevelEnabled(LOG_LEVEL_TRACE);
    }

    /**
     * A simple implementation which logs messages of level TRACE according
     * to the format outlined above.
     */
    public void trace(String msg) {
        log(LOG_LEVEL_TRACE, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(String format, Object param1) {
        formatAndLog(LOG_LEVEL_TRACE, format, param1, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(String format, Object param1, Object param2) {
        formatAndLog(LOG_LEVEL_TRACE, format, param1, param2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_TRACE, format, argArray);
    }

    /** Log a message of level TRACE, including an exception. */
    public void trace(String msg, Throwable t) {
        log(LOG_LEVEL_TRACE, msg, t);
    }

    /** Are {@code debug} messages currently enabled? */
    public boolean isDebugEnabled() {
        return isLevelEnabled(LOG_LEVEL_DEBUG);
    }

    /**
     * A simple implementation which logs messages of level DEBUG according
     * to the format outlined above.
     */
    public void debug(String msg) {
        log(LOG_LEVEL_DEBUG, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(String format, Object param1) {
        formatAndLog(LOG_LEVEL_DEBUG, format, param1, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(String format, Object param1, Object param2) {
        formatAndLog(LOG_LEVEL_DEBUG, format, param1, param2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_DEBUG, format, argArray);
    }

    /** Log a message of level DEBUG, including an exception. */
    public void debug(String msg, Throwable t) {
        log(LOG_LEVEL_DEBUG, msg, t);
    }

    /** Are {@code info} messages currently enabled? */
    public boolean isInfoEnabled() {
        return isLevelEnabled(LOG_LEVEL_INFO);
    }

    /**
     * A simple implementation which logs messages of level INFO according
     * to the format outlined above.
     */
    public void info(String msg) {
        log(LOG_LEVEL_INFO, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(String format, Object arg) {
        formatAndLog(LOG_LEVEL_INFO, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(String format, Object arg1, Object arg2) {
        formatAndLog(LOG_LEVEL_INFO, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_INFO, format, argArray);
    }

    /** Log a message of level INFO, including an exception. */
    public void info(String msg, Throwable t) {
        log(LOG_LEVEL_INFO, msg, t);
    }

    /** Are {@code warn} messages currently enabled? */
    public boolean isWarnEnabled() {
        return isLevelEnabled(LOG_LEVEL_WARN);
    }

    /**
     * A simple implementation which always logs messages of level WARN according
     * to the format outlined above.
     */
    public void warn(String msg) {
        log(LOG_LEVEL_WARN, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(String format, Object arg) {
        formatAndLog(LOG_LEVEL_WARN, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(String format, Object arg1, Object arg2) {
        formatAndLog(LOG_LEVEL_WARN, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_WARN, format, argArray);
    }

    /** Log a message of level WARN, including an exception. */
    public void warn(String msg, Throwable t) {
        log(LOG_LEVEL_WARN, msg, t);
    }

    /** Are {@code error} messages currently enabled? */
    public boolean isErrorEnabled() {
        return isLevelEnabled(LOG_LEVEL_ERROR);
    }

    /**
     * A simple implementation which always logs messages of level ERROR according
     * to the format outlined above.
     */
    public void error(String msg) {
        log(LOG_LEVEL_ERROR, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(String format, Object arg) {
        formatAndLog(LOG_LEVEL_ERROR, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(String format, Object arg1, Object arg2) {
        formatAndLog(LOG_LEVEL_ERROR, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_ERROR, format, argArray);
    }

    /** Log a message of level ERROR, including an exception. */
    public void error(String msg, Throwable t) {
        log(LOG_LEVEL_ERROR, msg, t);
    }
}
