package com.enoughisasgoodasafeast.operator;

import static com.enoughisasgoodasafeast.operator.Telecom.CANADIAN_PROVINCES.*;

/**
 * Some telephone specific utilities.
 *
 * Useful for determining which national rules apply. Distinguishing Canadian and U.S. numbers
 * is done by process of elimination. We check if the number uses a Canadian area code. If it doesn't
 * we assume the number must be in the U.S.
 */
public class Telecom {

    enum CANADIAN_PROVINCES {
        ALBERTA,
        BRITISH_COLUMBIA,
        MANITOBA,
        NEW_BRUNSWICK,
        NEWFOUNDLAND,
        NORTHWEST_TERRITORIES,
        NOVA_SCOTIA,
        NUNAVUT,
        ONTARIO,
        PRINCE_EDWARD_ISLAND,
        QUEBEC,
        SASKATCHEWAN,
        YUKON,
        // The following are our own invention reflecting shared area code usages
        NW_TERRITORIES_NUNAVUT_OR_YUKON,
        NOVA_SCOTIA_OR_PRINCE_EDWARD_ISLAND
    }

    static String[][] CANADA_AREA_CODES = new String[1000][2];

    static {
        // ref public data as listed on https://www.allareacodes.com/canadian_area_codes.htm
        CANADA_AREA_CODES[204] = new String[]{"Manitoba", MANITOBA.name()};
        CANADA_AREA_CODES[226] = new String[]{"London", ONTARIO.name()};
        CANADA_AREA_CODES[236] = new String[]{"Vancouver", BRITISH_COLUMBIA.name()};
        CANADA_AREA_CODES[249] = new String[]{"Sudbury", ONTARIO.name()};
        CANADA_AREA_CODES[250] = new String[]{"Kelowna", BRITISH_COLUMBIA.name()};
        CANADA_AREA_CODES[263] = new String[]{"Montreal", QUEBEC.name()};
        CANADA_AREA_CODES[289] = new String[]{"Hamilton", ONTARIO.name()};
        CANADA_AREA_CODES[306] = new String[]{"Saskatchewan", SASKATCHEWAN.name()};
        CANADA_AREA_CODES[343] = new String[]{"Ottawa", ONTARIO.name()};
        CANADA_AREA_CODES[354] = new String[]{"Granby", QUEBEC.name()};
        CANADA_AREA_CODES[365] = new String[]{"Hamilton", ONTARIO.name()};
        CANADA_AREA_CODES[367] = new String[]{"Quebec", QUEBEC.name()};
        CANADA_AREA_CODES[368] = new String[]{"Calgary", ALBERTA.name()};
        CANADA_AREA_CODES[382] = new String[]{"London", ONTARIO.name()};
        CANADA_AREA_CODES[403] = new String[]{"Calgary", ALBERTA.name()};
        CANADA_AREA_CODES[416] = new String[]{"Toronto", ONTARIO.name()};
        CANADA_AREA_CODES[418] = new String[]{"Quebec", QUEBEC.name()};
        CANADA_AREA_CODES[428] = new String[]{"New Brunswick", NEW_BRUNSWICK.name()};
        CANADA_AREA_CODES[431] = new String[]{"Manitoba", MANITOBA.name()};
        CANADA_AREA_CODES[437] = new String[]{"Toronto", ONTARIO.name()};
        CANADA_AREA_CODES[438] = new String[]{"Montreal", QUEBEC.name()};
        CANADA_AREA_CODES[450] = new String[]{"Granby", QUEBEC.name()};
        CANADA_AREA_CODES[468] = new String[]{"Sherbrooke", QUEBEC.name()};
        CANADA_AREA_CODES[474] = new String[]{"Saskatchewan", SASKATCHEWAN.name()};
        CANADA_AREA_CODES[506] = new String[]{"New Brunswick", NEW_BRUNSWICK.name()};
        CANADA_AREA_CODES[514] = new String[]{"Montreal", QUEBEC.name()};
        CANADA_AREA_CODES[519] = new String[]{"London", ONTARIO.name()};
        CANADA_AREA_CODES[548] = new String[]{"London", ONTARIO.name()};
        CANADA_AREA_CODES[579] = new String[]{"Granby", QUEBEC.name()};
        CANADA_AREA_CODES[581] = new String[]{"Quebec", QUEBEC.name()};
        CANADA_AREA_CODES[584] = new String[]{"Manitoba", MANITOBA.name()};
        CANADA_AREA_CODES[587] = new String[]{"Calgary", ALBERTA.name()};
        CANADA_AREA_CODES[604] = new String[]{"Vancouver", BRITISH_COLUMBIA.name()};
        CANADA_AREA_CODES[613] = new String[]{"Ottawa", ONTARIO.name()};
        CANADA_AREA_CODES[639] = new String[]{"Saskatchewan", SASKATCHEWAN.name()};
        CANADA_AREA_CODES[647] = new String[]{"Toronto", ONTARIO.name()};
        CANADA_AREA_CODES[672] = new String[]{"Vancouver", BRITISH_COLUMBIA.name()};
        CANADA_AREA_CODES[683] = new String[]{"Sudbury", ONTARIO.name()};
        CANADA_AREA_CODES[705] = new String[]{"Sudbury", ONTARIO.name()};
        CANADA_AREA_CODES[709] = new String[]{"Newfoundland/Labrador", NEWFOUNDLAND.name()};
        CANADA_AREA_CODES[742] = new String[]{"Hamilton", ONTARIO.name()};
        CANADA_AREA_CODES[753] = new String[]{"Ottawa", ONTARIO.name()};
        CANADA_AREA_CODES[778] = new String[]{"Vancouver", BRITISH_COLUMBIA.name()};
        CANADA_AREA_CODES[780] = new String[]{"Edmonton", ALBERTA.name()};
        CANADA_AREA_CODES[782] = new String[]{"Nova Scotia/PE Island", NOVA_SCOTIA_OR_PRINCE_EDWARD_ISLAND.name()};
        CANADA_AREA_CODES[807] = new String[]{"Kenora", ONTARIO.name()};
        CANADA_AREA_CODES[819] = new String[]{"Sherbrooke", QUEBEC.name()};
        CANADA_AREA_CODES[825] = new String[]{"Calgary", ALBERTA.name()};
        CANADA_AREA_CODES[867] = new String[]{"Northern Canada", NORTHWEST_TERRITORIES.name()};
        CANADA_AREA_CODES[873] = new String[]{"Sherbrooke", QUEBEC.name()};
        CANADA_AREA_CODES[879] = new String[]{"Newfoundland/Labrador", NEWFOUNDLAND.name()};
        CANADA_AREA_CODES[902] = new String[]{"Nova Scotia/PE Island", NOVA_SCOTIA_OR_PRINCE_EDWARD_ISLAND.name()};
        CANADA_AREA_CODES[905] = new String[]{"Hamilton", ONTARIO.name()};
    }

    public static boolean isCA(String number) {
        // e.g. 17815551234
        String areaCode = number.substring(1, 4);
        int index = Integer.parseInt(areaCode);
        // Because the area code is less than the array size (1000), the first element indexed will always be not null
        return CANADA_AREA_CODES[index][0] != null;
    }

    /**
     * For SMS assumes we only support Canada, Mexico, and the United States.
     * Assumes non-Canadian area codes are from the U.S.
     * For other platforms, not (yet) defined. For Brbl web, we could possibly use geoIP...
     */
    public static String deriveCountryCodeFromId(String from) {
        return switch (from) {
            case String id when id.length() == 12 && id.startsWith("52") -> "MX";
            case String id when id.length() == 11 && id.startsWith("1") && Telecom.isCA(id) -> "CA";
            default -> "US";
        };
    }

    public static void main() {
        System.out.println(Telecom.isCA("17815551234")); // false
        System.out.println(Telecom.isCA("15815551234")); // true
    }
}
