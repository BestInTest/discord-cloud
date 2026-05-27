package bo.wii.discordcloud.core.structure.enums;

/*
https://discord.com/developers/docs/topics/opcodes-and-status-codes#json
 */

public enum ApiJsonCodes {
    NO_ERROR(-1),
    UNKNOWN_CHANNEL(10003),
    UNKNOWN_GUILD(10004), // możliwe że niepotrzebne
    UNKNOWN_MESSAGE(10008),
    UNKNOWN_WEBHOOK(10015),
    //dodac jezeli bedzie trzeba
    ;

    private final int code;
    ApiJsonCodes(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
