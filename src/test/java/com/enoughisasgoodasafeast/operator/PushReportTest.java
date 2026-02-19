package com.enoughisasgoodasafeast.operator;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PushReportTest {

    @Test
    void isPushComplete() {
        final UUID dummyCampaignId = UUID.randomUUID();

        // Scenario 1: No failures
        PushReport report1 = new PushReport(dummyCampaignId);
        assertFalse(report1.isPushComplete(), "isPushComplete should be false when no failures are set.");

        // Scenario 2: campaignNotFound
        PushReport report2 = new PushReport(dummyCampaignId);
        report2.campaignNotFoundFail();
        assertTrue(report2.isPushComplete(), "isPushComplete should be true when campaignNotFound is set.");

        // Scenario 3: customerStatusNotActive
        PushReport report3 = new PushReport(dummyCampaignId);
        report3.campaignStatusNotActiveFail(CustomerStatus.SUSPENDED);
        assertTrue(report3.isPushComplete(), "isPushComplete should be true when customerStatusNotActive is set.");

        // Scenario 4: scriptStatusNotProd
        PushReport report4 = new PushReport(dummyCampaignId);
        report4.scriptStatusNotProdFail(ScriptStatus.DRAFT);
        assertTrue(report4.isPushComplete(), "isPushComplete should be true when scriptStatusNotProd is set.");

        // Scenario 5: routeStatusNotActive
        PushReport report5 = new PushReport(dummyCampaignId);
        report5.routeStatusNotActive(RouteStatus.LAPSED);
        assertTrue(report5.isPushComplete(), "isPushComplete should be true when routeStatusNotActive is set.");

        // Scenario 6: campaignUsersEmpty
        PushReport report6 = new PushReport(dummyCampaignId);
        report6.campaignUsersEmptyFail();
        assertTrue(report6.isPushComplete(), "isPushComplete should be true when campaignUsersEmpty is set.");

        // Scenario 7: All failures (just to be thorough)
        PushReport report7 = new PushReport(dummyCampaignId);
        report7.campaignNotFoundFail();
        report7.campaignStatusNotActiveFail(CustomerStatus.LAPSED);
        report7.scriptStatusNotProdFail(ScriptStatus.INACTIVE);
        report7.routeStatusNotActive(RouteStatus.REQUESTED);
        report7.campaignUsersEmptyFail();
        assertTrue(report7.isPushComplete(), "isPushComplete should be true when multiple failures are set.");
    }

}