package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.Platform;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.io.IO.println;
import static java.lang.String.join;
import static java.lang.System.out;
import static java.util.UUID.fromString;

// TODO Extends currently but more sensible to merge the code into it's (renamed) parent class

/**
 * Keywords, Routes, Scripts, Nodes, and Edges.
 * Maybe add CampaignUsers and PushCampaigns.
 */
public class BrblLogicTsvGenerator extends BrblUsersTsvGenerator {

    public static Logger LOG = LoggerFactory.getLogger(BrblLogicTsvGenerator.class);

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


    private List<BrblRow> generateKnownKeywords(String[][] keywordIdsAndPatterns, String nodeId, String routeId) {
        var keywordRows = new ArrayList<BrblRow>();
        for (int i = 0; i < keywordIdsAndPatterns.length; i++) {
            var now = NanoClock.utcInstant();
            var keyword = new KeywordRow(fromString(keywordIdsAndPatterns[i][0]), keywordIdsAndPatterns[i][1],
                    fromString(nodeId), now, now, fromString(routeId));
            keywordRows.add(keyword);
        }
        return keywordRows;
    }

    public List<BrblRow> generateKnownScripts(UUID companyId, String[][] knownScriptData, String[] rootNodeIds) {
        if (knownScriptData.length > rootNodeIds.length) {
            throw new IllegalStateException("Expected scripts length to be less than or equal to rootNodeIds length.");
        }
        var scriptRows = new ArrayList<BrblRow>();
        for (int i = 0; i < knownScriptData.length; i++) {
            var script = new ScriptRow(
                        fromString(knownScriptData[i][0]),
                        knownScriptData[i][1],
                        knownScriptData[i][2],
                        companyId,
                        fromString(rootNodeIds[i]));
            scriptRows.add(script);
        }

        return scriptRows;
    }

    public List<BrblRow> generateKnownRoutes(UUID companyId, String[][] routeIdsAndChannels, String[] rootNodeIds) {
        if (routeIdsAndChannels.length != rootNodeIds.length)
            throw new IllegalStateException("Expected equal sized arrays.");

        var routeRows = new ArrayList<BrblRow>();
        for (int i = 0; i < routeIdsAndChannels.length; i++) {
            var route = new RouteRow(fromString(routeIdsAndChannels[i][0]), routeIdsAndChannels[i][1],
                    fromString(rootNodeIds[i]), Platform.SMS, companyId); // all SMS, all ACTIVE for now
            routeRows.add(route);
        }

        return routeRows;
    }

    static void main() {
        var generator = new BrblLogicTsvGenerator();
        // routes for Yoyodyne
        var rowData = generator.generateKnownRoutes(
                fromString(knownCompanyId),
                BrblLogicTsvGenerator.knownRouteIdsAndChannels,
                BrblLogicTsvGenerator.knownRootNodeIds);
        rowData.forEach(out::println);

        // keywords
        var keywordData = generator.generateKnownKeywords(
                knownKeywordIdsAndPatterns1, knownRootNodeIds[0], knownRouteIdsAndChannels[0][0]);
        keywordData.addAll(generator.generateKnownKeywords(
                getKnownKeywordIdsAndPatterns2, knownRootNodeIds[1], knownRouteIdsAndChannels[2][0]
        ));

        keywordData.forEach(out::println);

        // scripts
        var scriptData = generator.generateKnownScripts(
                fromString(knownCompanyId),
                BrblLogicTsvGenerator.knownScriptData,
                BrblLogicTsvGenerator.knownRootNodeIds);
        scriptData.forEach(out::println);

        if (!generator.outputRowsAsTsv(rowData, "routes", "known_")) {
            println("Failed writing route data.");
        }
        if (!generator.outputRowsAsTsv(keywordData, "keywords", "known_")) {
            println("Failed writing keywords data.");
        }
        if (!generator.outputRowsAsTsv(scriptData, "scripts", "known_")) {
            println("Failed writing scripts data.");
        }

    }

    private boolean outputRowsAsTsv(List<BrblRow> rows, String fileName, String prefix) {
        try {
            var writer = dmlWriter(prefix, fileName + ".tsv");

            writer.write(join(DLM, rows.getFirst().headers()) + "\n");

            for (var row : rows) {
                writer.write(join(DLM, row.values()) + "\n");
            }
            writer.close();

            return true;
        } catch (IOException e) {
            return false;
        }
    }

}
