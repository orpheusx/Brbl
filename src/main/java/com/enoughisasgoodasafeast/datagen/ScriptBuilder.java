package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.Node;
import io.jenetics.util.NanoClock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static java.io.IO.println;

public class ScriptBuilder {

    String SECTION_DELIMITER = "\n\n";
    String LINE_DELIMITER = "\\^";

    public ScriptBuilder() {
    }

    public record NodeStruct(String id, Instant createdAt, String text, int type, String label, Instant updatedAt) {

        static String[] headers = {"id", "created_at", "text", "type", "label", "updated_at"};

        public String[] values() {
            return new String[]{id, createdAt.toString(), text, String.valueOf(type), label, updatedAt.toString()};
        }

        public NodeStruct(String id, String text) {
            var now = NanoClock.utcInstant();
            this(id, now, text, 3, null, now);
        }
    }

    public record EdgeStruct(UUID id, Instant createdAt, String matchPatterns, String response, String srcNodeId, String dstNodeId, Instant updatedAt) {

        static String[] headers = {"id", "created_at", "match_text", "response_text", "src", "dst", "updated_at"};

        public String[] values() {
            return new String[]{id.toString(), createdAt.toString(), matchPatterns, response, srcNodeId, dstNodeId, updatedAt.toString()};
        }

        public EdgeStruct(String srcNodeId, String dstNodeId, String matchPatterns, String response) {
            var now = NanoClock.utcInstant();
            this(UUID.randomUUID(), now, matchPatterns, response, srcNodeId, dstNodeId, now);
        }
    }

    static String script = """
            0^Quiz Topics: What would you like to talk about? 1) animals, 2) AI, or 3) geography
            	1^1|animals^Always a good pick!
            	2^2|ai|a.i.|a.i^A timely choice...
            	3^3|geography|geo^Time for a quiz...
            	4^4|nothing|nada|never mind|bye|stop^Okey doke.
            
            1^What is your opinion on artificial intelligence: Positive, Negative, Neutral
            	0^1|positive|pos^Agreed. It's useful for many tasks.
            	0^2|negative|neg^Agreed. It's a financial bubble that will make the last recession look like a picnic.
            	0^3|neutral|neu^Agreed. It will be a disaster if left unregulated.
            
            2^What is your favorite animal: dogs, cats, elephants
            	0^dogs|d^Me too! Dogs are known for their loyalty and companionship.
            	0^cats|c^Me too! Cats are independent and mysterious creatures.
            	0^elephants|ele|e^Me too! Elephants are intelligent and majestic animals.
            
            3^What is the largest ocean on Earth: 1. Antarctic, 2. Arctic, 3. Atlantic, 4. Indian, 5. Pacific
            	3^3|atlantic|at^The Atlantic Ocean is actually the second largest. It separates the Americas from Europe and Africa. Try again...
            	0^5|pacific|pac^Correct, the Pacific Ocean is the largest. It covers 46% of the planet's surface!
            	3^4|indian|ind^The Indian Ocean is the third largest. It borders Asia, Africa, and Australia. Try again...
            	3^1|antarctic|ant^The Antarctic (aka the Southern) Ocean is second smallest. Try again...
            	3^2|arctic|arc^The Arctic Ocean is the smallest and shallowest. Try again...
            
            4^Goodbye for now. :-)
            """;

    static String[] NODE_ID_TO_UUID_MAP = {
            "019d3522-ac0e-7e2a-95cc-d2db38b8fafc", // 0
            "019d3522-ac15-7678-8b12-e9488166d0da", // 1
            "019d3522-ac15-74b6-bf3b-b407c20bd8ec", // 2
            "019d3522-ac15-7c4f-b0d5-213af2647736", // 3
            "019d3522-ac15-772d-840f-afa9b224c134", // 4
            "019d3522-ac15-72a2-9362-7b848f4478f9", // 5
            "019d3522-ac15-75c2-9b3d-1729337312c0", // 6
            "019d3522-ac15-733d-b07c-6217788fe41d", // 7
            "019d3522-ac15-7919-87a6-f1e52d8074a5", // 8
            "019d3522-ac15-7d3b-8e23-fd266b4d545a"  // 9
    };

    String[] splitIntoBlocks() {
        return script.split(SECTION_DELIMITER, -1);
    }

    String[] splitIntoNodeAndEdges(String data) {
        return data.trim().split("\n");
    }

    NodeStruct newNodeStruct(String data) {
        String[] idAndText = data.split(LINE_DELIMITER);
        String uuidStr = mappedId(idAndText[0]);
        return new NodeStruct(uuidStr, idAndText[1].trim());
    }

    NodeStruct newNodeStruct(String id, String data) {
        return new NodeStruct(id, data);
    }

    EdgeStruct newEdgeStruct(String srcNodeId, String data) {
        String[] idMatchResponse = data.trim().split(LINE_DELIMITER);
        String dstId = idMatchResponse[0].trim();
        String dstUuid = dstId.equals("null") ? null : mappedId(dstId);
        return new EdgeStruct(srcNodeId, dstUuid, idMatchResponse[1].trim(), idMatchResponse[2].trim());
    }

    String mappedId(String idStr) {
        return NODE_ID_TO_UUID_MAP[Integer.parseInt(idStr.trim())];
    }

    boolean validateIdReferences(List<NodeStruct> nodes, List<EdgeStruct> edges) {
        List<EdgeStruct> errors = new ArrayList<>();
        for (EdgeStruct edge : edges) {
            if (edge.dstNodeId == null) continue;
            boolean found = false;
            for (NodeStruct node : nodes) {
                if (node.id.equals(edge.dstNodeId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                errors.add(edge);
            }
        }
        if (!errors.isEmpty()) {
            println("ERROR: The following edges have dstNode that are missing: ");
            errors.forEach(error -> println("\t" + error));
            return false;
        }
        return true;
    }

    void writeNodesToFile(List<NodeStruct> nodes, Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join("\t", NodeStruct.headers));
        for (NodeStruct node : nodes) {
            lines.add(String.join("\t", node.values()));
        }
        Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    void writeEdgesToFile(List<EdgeStruct> edges, Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join("\t", EdgeStruct.headers));
        for (EdgeStruct edge : edges) {
            lines.add(String.join("\t", edge.values()));
        }
        Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void main(String[] args) {
        final var sc = new ScriptBuilder();

        List<NodeStruct> nodes = new ArrayList<>();
        List<EdgeStruct> edges = new ArrayList<>();

        final String[] blocks = sc.splitIntoBlocks();
        for (String block : blocks) {
            // create the needed edges and nodes for each block
            final var lines = sc.splitIntoNodeAndEdges(block);
            if (lines.length == 0) continue;
            
            NodeStruct currentNode = sc.newNodeStruct(lines[0]);
            nodes.add(currentNode);
            NodeStruct connector;

            if (currentNode.type == 3) { //PresentMulti // TODO another place to change the int to a NodeType enum.
                // add an node+edge to a new node of type 4 ProcessMulti
                connector = sc.newNodeStruct(randomUUID().toString(), "n/a");
                nodes.add(connector);
                edges.add(new EdgeStruct(currentNode.id, connector.id, "na/a", "n/a"));
            }
            
            for (int i = 1; i < lines.length; i++) {
                edges.add(sc.newEdgeStruct(currentNode.id, lines[i]));
            }
        }

        if (!sc.validateIdReferences(nodes, edges)) {
            println("ERROR: Validation errors were present. No output will be produced.");
            return;
        }

        try {
            sc.writeNodesToFile(nodes, Path.of("nodes_batch1.tsv"));
            sc.writeEdgesToFile(edges, Path.of("edges_batch1.tsv"));
            println("Success: Data written to nodes_batch1.tsv and edges_batch1.tsv");
        } catch (IOException e) {
            println("ERROR: Failed to write output files: " + e.getMessage());
        }
    }
}
