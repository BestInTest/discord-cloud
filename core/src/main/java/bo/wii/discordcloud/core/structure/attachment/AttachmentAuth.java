package bo.wii.discordcloud.core.structure.attachment;

import bo.wii.discordcloud.core.utils.ApiUtil;

import java.util.Map;

public class AttachmentAuth {

    private final long issueTimestampInMillis; // converted link creation timestamp
    private final long expireTimestampInMillis; // converted link expiration timestamp - needed to check if links are still valid and don't need refreshing
    private final String signature; // authorization signature valid until expireTimestamp

    public AttachmentAuth(String url) {
        Map<Param, String> params = ApiUtil.extractParameters(url);
        // *1000 converts to milliseconds because currentTimeMillis() returns in milliseconds while Discord uses seconds
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
