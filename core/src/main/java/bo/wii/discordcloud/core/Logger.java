package bo.wii.discordcloud.core;

import java.util.function.Consumer;

public class Logger {

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    private static volatile LogLevel currentLevel = LogLevel.INFO;

    // Custom output writers - when set, Logger routes through these instead of System.out/err
    private static volatile Consumer<String> stdoutWriter = null;
    private static volatile Consumer<String> stderrWriter = null;

    public static void setLevel(LogLevel level) {
        currentLevel = level;
    }

    public static LogLevel getLevel() {
        return currentLevel;
    }

    /**
     * Set custom output writers. Pass null to reset to default (System.out/err).
     */
    public static void setOutputWriter(Consumer<String> stdout, Consumer<String> stderr) {
        stdoutWriter = stdout;
        stderrWriter = stderr;
    }

    public static void debug(Class<?> clazz, String message) {
        if (currentLevel.ordinal() <= LogLevel.DEBUG.ordinal()) {
            writeOut("[" + clazz.getSimpleName() + "] " + message);
        }
    }

    public static void info(Class<?> clazz, String message) {
        if (currentLevel.ordinal() <= LogLevel.INFO.ordinal()) {
            writeOut("[" + clazz.getSimpleName() + "] " + message);
        }
    }

    public static void warn(Class<?> clazz, String message) {
        if (currentLevel.ordinal() <= LogLevel.WARN.ordinal()) {
            writeErr("[WARN] [" + clazz.getSimpleName() + "] " + message);
        }
    }

    public static void error(Class<?> clazz, String message) {
        if (currentLevel.ordinal() <= LogLevel.ERROR.ordinal()) {
            writeErr("[ERROR] [" + clazz.getSimpleName() + "] " + message);
        }
    }

    /**
     * @deprecated Use {@link #info(Class, String)} instead
     */
    @Deprecated
    public static void log(Class<?> clazz, String message) {
        debug(clazz, message);
    }

    /**
     * @deprecated Use {@link #error(Class, String)} instead
     */
    @Deprecated
    public static void err(Class<?> clazz, String message) {
        error(clazz, message);
    }

    private static void writeOut(String formatted) {
        Consumer<String> writer = stdoutWriter;
        if (writer != null) {
            writer.accept(formatted);
        } else {
            System.out.println(formatted);
        }
    }

    private static void writeErr(String formatted) {
        Consumer<String> writer = stderrWriter;
        if (writer != null) {
            writer.accept(formatted);
        } else {
            System.err.println(formatted);
        }
    }
}
