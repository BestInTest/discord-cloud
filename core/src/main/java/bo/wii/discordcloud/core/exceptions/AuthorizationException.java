package bo.wii.discordcloud.core.exceptions;

public class AuthorizationException extends Exception {

    public AuthorizationException() {
        super("Authorisation failed");
    }

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthorizationException(Throwable cause) {
        super(cause);
    }
}

