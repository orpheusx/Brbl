package com.enoughisasgoodasafeast.operator;

/**
 * The set of messaging platforms supported by Brbl.
 */
public enum Platform {
    BRBL("B"), // Brbl native
    SMS("S"), // Short Message Service
    WAP("W")  // Whatsapp
    // FBM('F')  // FB_MESSENGER
    // SLK('S'), // SLACK
    ;

    private final String code;

    Platform(String code) {
        this.code = code;
    }

    public String code() {
        return this.code;
    }

    public static Platform byCode(String code) {
        for (Platform p : values()) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        final Platform sms = Enum.valueOf(Platform.class, "S");
        System.out.println(sms);
    }
}
