package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.enoughisasgoodasafeast.datagen.KnownData.*;
import static java.io.IO.println;
import static java.lang.String.join;
import static java.lang.System.out;
import static java.time.Instant.now;
import static java.util.UUID.fromString;

// TODO Extends currently but more sensible to merge the code into it's (renamed) parent class

/**
 * Keywords, Routes, Scripts, Nodes, and Edges.
 * Maybe add CampaignUsers and PushCampaigns.
 */
public class BrblLogicTsvGenerator extends BrblUsersTsvGenerator {

    public static Logger LOG = LoggerFactory.getLogger(BrblLogicTsvGenerator.class);

    private List<BrblRow> generateKnownKeywords(String[][] keywordIdsAndPatterns, String scriptId, String routeId) {
        var keywordRows = new ArrayList<BrblRow>();
        for (int i = 0; i < keywordIdsAndPatterns.length; i++) {
            var now = now();
            var keyword = new KeywordRow(fromString(keywordIdsAndPatterns[i][0]), keywordIdsAndPatterns[i][1],
                    fromString(scriptId), now, now, fromString(routeId));
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

    public List<BrblRow> generateKnownRoutes(UUID companyId, String[][] routeIdsAndChannels, String[][] scriptData, String interruptScript) {
        if (routeIdsAndChannels.length != scriptData.length)
            throw new IllegalStateException("Expected equal sized arrays.");

        var routeRows = new ArrayList<BrblRow>();
        for (int i = 0; i < routeIdsAndChannels.length; i++) {
            var route = new RouteRow(fromString(routeIdsAndChannels[i][0]), routeIdsAndChannels[i][1],
                    fromString(scriptData[i][0]), Platform.SMS, companyId, fromString(interruptScript)); // all SMS, all ACTIVE for now
            routeRows.add(route);
        }

        return routeRows;
    }

    static void main() {
        var generator = new BrblLogicTsvGenerator();
        // routes for Yoyodyne
        var rowData = generator.generateKnownRoutes(
                fromString(knownCompanyId),
                knownRouteIdsAndChannels,
                knownScriptData,
                knownUnreferencedScriptData[0][0]); // Only one 'change topic' script
        rowData.forEach(out::println);

        // keywords
        var keywordData = generator.generateKnownKeywords(
                knownKeywordIdsAndPatterns1, knownScriptData[0][0], knownRouteIdsAndChannels[0][0]);
        keywordData.addAll(generator.generateKnownKeywords(
                getKnownKeywordIdsAndPatterns2, knownScriptData[1][0], knownRouteIdsAndChannels[2][0]
        ));
        // The food quiz graph remains un-referenced by either the keywords or routes (default_script_id) tables.

        keywordData.forEach(out::println);

        var companyUUID = fromString(knownCompanyId);
        // scripts
        var scriptData = generator.generateKnownScripts(
                companyUUID,
                knownScriptData,
                knownRootNodeIds);

        scriptData.addAll(generator.generateKnownScripts(companyUUID, knownUnreferencedScriptData, knownUnreferencedNodeIds));

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
