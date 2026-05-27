package bo.wii.discordcloud.core.exceptions;

public class FileMismatchException extends Exception {

    public FileMismatchException(String message) {
        super(message);
    }

    public FileMismatchException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileMismatchException(Throwable cause) {
        super(cause);
    }
}

