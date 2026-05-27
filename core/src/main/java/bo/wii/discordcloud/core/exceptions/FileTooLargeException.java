package bo.wii.discordcloud.core.exceptions;

public class FileTooLargeException extends Exception {

    public FileTooLargeException() {
        super("File is too large.");
    }

    public FileTooLargeException(String message) {
        super(message);
    }

    public FileTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileTooLargeException(Throwable cause) {
        super(cause);
    }
}

