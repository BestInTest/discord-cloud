package yo.men.discordcloud.managers;

public class Result {

    boolean success;
    private int responseCode;
    private String message;

    Result(boolean success, int responseCode, String message) {
        this.success = success;
        this.responseCode = responseCode;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getMessage() {
        return message;
    }
}
