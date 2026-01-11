package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.CountryCode;
import net.datafaker.Faker;

import java.io.IO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.enoughisasgoodasafeast.datagen.BrblUsersGenerator.adjustPlatformId;
import static java.io.IO.println;

/**
 * This is a fairly gross class, full of nasty string munging and hackery.
 */
public class BrblLogicGenerator {

    public String INPUT_FILE  = "generated_conversations_en.tsv";
    public String OUTPUT_FILE = "generated_conversations_en.sql";
    public String TAB = "\t";

    private final Faker faker = new Faker(Locale.ENGLISH);

    public BrblLogicGenerator() {}

    public BrblLogicGenerator(String inputFile) {
        this.INPUT_FILE = inputFile; // allow override of the default input
    }

    // Remember that conversations end with an edge that lacks a dst Node.
    public Map<NodeRow, List<EdgeRow>> generateNodeEdgeMap() throws IOException {
        var filePath = Paths.get("dml", INPUT_FILE);
        Map<NodeRow, List<EdgeRow>> questionAnswers = new HashMap<>(133);

        try (var lines = Files.lines(filePath)) {
            lines.forEach(line -> {
            String[] kv = line.split(TAB);
                var nodeRow = new NodeRow(kv[0]);
                var answers = questionAnswers.get(nodeRow);
                if (answers == null) {
                    questionAnswers.put(nodeRow, new ArrayList<>(List.of(new EdgeRow(kv[1]))));
                } else {
                    answers.add(new EdgeRow(kv[1]));
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Add src reference
        questionAnswers.forEach((key, value) -> {
            //println("Q: " + key);
            value.forEach(edge -> {
                edge.src = key.id;
                //println("\t: " + edge);
            });
        });

        return questionAnswers;
    }

    // TODO add PushCampaign and CampaignUser
    public DataSet generateRoutesAndScripts(Map<NodeRow, List<EdgeRow>>  questionAnswers, ArrayList<CustomerRow> customerRows) {
        // Only customers with an associated Company have paid to create Scripts
        // For testing purposes, we create a Script/Route for every node that is capable of acting as an entrypoint.
        var routableNodes = questionAnswers.keySet().stream().filter(nodeRow -> isNodeTypeEntrypointCapable(nodeRow)).toList();
        var routeOwners = customerRows.stream().filter(customerRow -> customerRow.companyId != null).toList();
        println("Generating routes and scripts for " + routableNodes.size() + " nodes and " + routeOwners.size() + " customers.");

        List<RouteRow> routes = new ArrayList<>();
        List<ScriptRow> scripts = new ArrayList<>();

        int cpc = routableNodes.size() / routeOwners.size();

        for (int i = 0; i < routableNodes.size(); i++) {
            NodeRow nodeRow = routableNodes.get(i);
            CustomerRow customerRow = routeOwners.get(Math.min(i/cpc, 2));

            routes.add(new RouteRow(adjustPlatformId(CountryCode.US, faker.phoneNumber().phoneNumberNational()), nodeRow.id, customerRow.id));
            scripts.add(new ScriptRow("Script "+ i, "Description " + i, customerRow.id, nodeRow.id));
        }

        return new DataSet(routes, scripts);
    }

    /*
     * Scripts are Nodes that can act as an entry point to a conversation. Not all Nodes are meant to act in this way. We're using the type
     * to filter out ones that are just glue but even the ones that qualify may not be intended to act as a Script.
     */
    private boolean isNodeTypeEntrypointCapable(NodeRow nodeRow) {
        return switch(nodeRow.type) {
            case 3, 6, 8 -> true;
            default -> false;
        };
    }

    public List<String> dataToSQL(Map<NodeRow, List<EdgeRow>> questionAnswers,
                                  DataSet routesScripts) throws IOException {
        var nodesBuilder = new StringBuilder();
        nodesBuilder.append(NodeRow.INSERT_SQL);
        questionAnswers.forEach((node, edges) -> {
            nodesBuilder.append(node.getValuesSql());
        });

        //------------------------------------------------------------------
        var edgesBuilder = new StringBuilder();
        edgesBuilder.append(EdgeRow.INSERT_SQL);
        questionAnswers.forEach((node, edges) -> {
            edges.forEach(edge -> {
                edgesBuilder.append(edge.getValuesSql());
            });
        });

        //------------------------------------------------------------------
        var routesBuilder = new StringBuilder();
        routesBuilder.append(RouteRow.INSERT_SQL);
        routesScripts.routeRows.forEach(route -> {
            routesBuilder.append(route.getValuesSql());
        });

        //------------------------------------------------------------------
        var scriptsBuilder = new StringBuilder();
        scriptsBuilder.append(ScriptRow.INSERT_SQL);
        routesScripts.scriptRows.forEach(script -> {
            scriptsBuilder.append(script.getValuesSql());
        });

        //------------------------------------------------------------------
        List<String> output = new ArrayList<>(6);
        output.add("BEGIN TRANSACTION;");

        var nodesSql = nodesBuilder.toString();
        output.add(nodesSql.substring(0, nodesSql.length() - 2));
        output.add(";");

        var edgesSql = edgesBuilder.toString();
        output.add(edgesSql.trim().substring(0, edgesSql.length() - 2));
        output.add(";");

        var routesSql = routesBuilder.toString();
        output.add(routesSql.substring(0, routesSql.length() - 2));
        output.add(";");

        var scriptsSql= scriptsBuilder.toString();
        output.add(scriptsSql.substring(0, scriptsSql.length() - 2));
        output.add(";");

        output.add("COMMIT;");

        return output;
    }

    public record DataSet(List<RouteRow> routeRows, List<ScriptRow> scriptRows) {}


    // public static void main() throws IOException {
    //     var cg = new BrblLogicGenerator();
    //     Map<NodeRow, List<EdgeRow>> nodeEdgeMap = cg.generateNodeEdgeMap();
    //     final List<String> sql = cg.dataToSQL(nodeEdgeMap);
    //     var outPath  = Paths.get("dml", "logic.sql");
    //     Files.write(outPath, sql, StandardCharsets.UTF_8);
    //     IO.println("Done.");
    // }
}


