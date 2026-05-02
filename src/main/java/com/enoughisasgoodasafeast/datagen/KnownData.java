package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.CompanyStatus;
import com.enoughisasgoodasafeast.operator.CountryCode;

import static com.enoughisasgoodasafeast.operator.CompanyStatus.*;
import static com.enoughisasgoodasafeast.operator.CountryCode.*;

public class KnownData {

    // Ordered from smallest to largest.
    public static final String[] standardCompanyNames = {
            "MicroCorp", "MiniCorp", "MegaCorp", "MondoCorp", "GigaCorp", "NadaCorp"
    };

    public static final CompanyStatus[] standardCompanyStatuses = {
            ACTIVE, ACTIVE, ACTIVE, SUSPENDED, ACTIVE, LAPSED
    };

    public static final String knownCompanyName = "Yoyodyne Systems"; // always ACTIVE

    public static final String knownCompanyId = "019d2055-922c-75f7-a80e-091f01382fa3";

    public static final String[] knownUserIds = {
            "019d2055-9235-73cc-8c75-2cb97dbbc285",
            "019d2055-9235-736d-908e-b1c0229b0392",
            "019d2055-9235-713a-8fb8-ef96c0b7b6c0",
            "019d2055-9235-72b6-a72a-bdbe4dfc6a07",
            "019d2055-9235-7465-bd7a-8805df51b3ee",
            "019d2055-9235-7849-a26d-199fb6738fbe",
            "019d2055-9235-7290-8266-37ce60819893",
            "019d2055-9235-789e-999b-3e47084d5081",
            "019d2055-9235-7c22-ade9-f6b416f123a7",
            "019d2055-9235-7c95-8610-1945de2d2910"
    };

    // The five ids here are correlated by index to the elements in knownUserIds
    // which means that the second half of knownUserIds are unlinked.
    public static final String[] knownLinkedUserIds = {
            "019d2553-bdcc-7433-b100-b4fb88d92b0a",
            "019d2553-bdcc-7abb-8a6e-f607f0045a15",
            "019d2553-bdcc-7a2a-82d1-5becd6ff7f9d",
            "019d2553-bdcc-72a1-a01f-596c19c9455c",
            "019d2553-bdcc-7142-81f2-4539a656bbda"
    };

    // public static final Platform[] knownPlatformsForUser = {SMS, SMS, SMS, SMS, SMS, SMS, SMS, SMS, SMS, SMS};

    public static final String[] knownNumbersForUsers = {
            "17817209450", // The rest are for U.S.
            "14157209451", // California
            "17817209452", // Massachusetts
            "15167209453", // Kansas City
            "17817209454",
            "19297209455", // NYC
            "17817209456",
            "19787209457",
            "14167209458",  // Toronto area code.
            "526641112222", // A very fake number in Mexico City.
    };

    public static final CountryCode[] knownCountryCodes = {US, US, US, US, US, US, US, US, CA, MX};

    public static final String[] knownProfileIds = {
            "019d2055-9235-7daf-98ce-695cea1f25bf",
            "019d2055-9235-7fab-8150-86496418de20",
            "019d2055-9235-75d0-83b4-173de282ff9e"
    };

    public static final String[] knownCustomerIds = {
            "019d2055-9235-7c52-bfe1-0bdeb8fbe6ca",
            "019d2055-9235-7354-aaae-067e97853f4a"
    };

    // The group_id values for each amalgam.
    // NB: Expected to be equal to size of knownNumbersForUsers.
    public static final String[] knownAmalgamIds = {
            "019d2055-9235-7e29-b0de-8903d9f662db",
            "019d2055-9235-7ce6-bfc6-4ccbb9c16a89",
            "019d2055-9235-7073-a9ae-9dbaf02e817c",
            "019d2055-9235-72f4-8f7e-3b23435a5717",
            "019d2055-9235-74fc-bbf1-da2f32199ffc",
            "019d2055-9235-7a1c-9d1d-268d9ec1b6ba",
            "019d2055-9235-7bce-acf2-33353f7287df",
            "019d2055-9236-7b00-a680-f7d417788432",
            "019d2055-9236-7806-b193-24270d2698d0",
            "019d2055-9236-7701-b644-114d0e32a565"
    };

    public static final String DLM = "\t";


    // FIXME These are defined only in a tsv file currently. Need to add them as a static constants here.
    // TODO Switch from pointing at Nodes directly to pointing at Scripts.
    public static String[] knownRootNodeIds = {
            "89eddcb8-7fe5-4cd1-b18b-78858f0789fb", // What is your favorite color?
            "525028ae-0a33-4c80-a22f-868f77bb9531", // True or false: people are the worst?
            "89eddcb8-7fe5-4cd1-b18b-78858f0789fb", // What is your favorite color?
    };

    public static String[][] knownRouteIdsAndChannels = {
            {"019dca4b-bb1e-756c-9050-7960e5828d68", "17814567890"},
            {"019dca4b-bb23-7e0d-bfb6-9226d5c4166b", "18163456789"},
            {"019dca4b-bb23-7091-a32b-66f2e18cd073", "12124468003"}
    };

    // Associate these with the favorite color script.
    public static String[][] knownKeywordIdsAndPatterns1 = {
            // id, pattern
            {"3a99ca92-d24b-41f9-bca2-3c2375c88738", "(color|colour|colr).*(quiz|q|kwiz).*" },
            {"571741cf-20f2-456d-92d6-f7f1c9d2b319", "bar" },
            {"4cac70a8-2712-438b-8550-22aa9099ee3f", "baz" },
            {"019c9123-744b-79f5-9a3b-b7e51d552fe3", "foo" }
    };

    // Associate with people are the worst script
    public static String[][] getKnownKeywordIdsAndPatterns2 = {
            {"53578bea-7f9e-49b3-8412-948f072f75b6", "meh" }
    };

    // (String name, String description, UUID customerId, UUID nodeId
    public static String[][] knownScriptData = {
            {"019dcf76-aa1e-7fb6-87d4-733deb0d4c95", "Fave color", "Starts conversation with chained multiple choice questions"},
            {"019dcf76-aa24-738d-99c0-28fb89344f22", "The truth", "People bad: true or false"}
    };

}
