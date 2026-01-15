package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.CountryCode;

import java.io.IO;

public class Functions {

    public static String convertPlatformId(String platformId) {
        return platformId.replaceAll("[\\s\\-(\\)]", "");
    }

    public static String adjustPlatformId(CountryCode countryCode, String platformId) {
        var mungedPlatformId = convertPlatformId(platformId);
        return switch (countryCode) {
            case US, CA -> "1" + mungedPlatformId;
            case MX -> "52" + mungedPlatformId;
        };
    }

    static void main() {
        var phoneNumber = "1(862) 646-2877";
        IO.println(Functions.adjustPlatformId(CountryCode.US, phoneNumber));
    }
}
