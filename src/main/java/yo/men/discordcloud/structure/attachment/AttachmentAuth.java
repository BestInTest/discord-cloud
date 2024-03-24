package yo.men.discordcloud.structure.attachment;

import yo.men.discordcloud.utils.ApiUtil;

import java.util.Map;

public class AttachmentAuth {

    private final long issueTimestampInMillis; // przekonwertowany timestamp utworzenia linku
    private final long expireTimestampInMillis; // przekonwertowany timestamp wygaśnięcia linku - bedzie potrzebne do sprawdzania czy linki sa wazne i nie trzeba odswiezyc
    private final String signature; // sygnatura autoryzacyjna ważna aż do expireTimestamp

    public AttachmentAuth(String url) {
        Map<Param, String> params = ApiUtil.extractParameters(url);
        // *1000 to konwersja na milisekundy, ponieważ currentTimeMillis() zwraca w milisekundach a discord w sekundach
        this.issueTimestampInMillis = ApiUtil.hexToDecimal(params.getOrDefault(Param.ISSUE_TIMESTAMP_HEX, "0")) * 1000;
        this.expireTimestampInMillis = ApiUtil.hexToDecimal(params.getOrDefault(Param.EXPIRE_TIMESTAMP_HEX, "0")) * 1000;
        this.signature = params.get(Param.SIGNATURE);
    }

    public long getIssueTimestamp() {
        return issueTimestampInMillis;
    }

    public long getExpireTimestamp() {
        return expireTimestampInMillis;
    }

    public String getSignature() {
        return signature;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTimestampInMillis;
    }
}
