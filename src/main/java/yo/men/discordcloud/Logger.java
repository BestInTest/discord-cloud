package yo.men.discordcloud;

public class Logger {
    public static void log(Class<?> clazz, String message) {
        System.out.println("[" + clazz.getSimpleName() + "] " + message);
    }

    public static void err(Class<?> clazz, String message) {
        System.err.println("[" + clazz.getSimpleName() + "] " + message);
    }
}
