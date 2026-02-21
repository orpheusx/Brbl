package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.operator.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ScriptInterpreter {
    private static final Logger LOG = LoggerFactory.getLogger(ScriptInterpreter.class);
    private final PersistenceManager persistenceManager;

    public ScriptInterpreter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public ChttrScript translateNodeGraphToChttrScript(UUID nodeId) {
        Node rootNode = persistenceManager.getNodeGraph(nodeId);
        if (rootNode == null) {
            LOG.error("Node graph not found for nodeId: {}", nodeId);
            return null;
        }

        Node.printGraph(rootNode, rootNode, 2);

        ChttrScript chttrScript = new ChttrScript();
        Set<Node> visited = new HashSet<>();
        traverseGraph(rootNode, chttrScript, visited);

        return chttrScript;
    }
    public ChttrScript exportScriptToFile(UUID nodeId, String outputPath) {
        final ChttrScript chttrScript = translateNodeGraphToChttrScript(nodeId);
        try (FileOutputStream fileOut = new FileOutputStream(outputPath);
             ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {
            objectOut.writeObject(chttrScript);
            LOG.info("ChttrScript serialized to {}", outputPath);
        } catch (IOException e) {
            LOG.error("Error serializing ChttrScript", e);
        } finally {
            return chttrScript;
        }
    }

    private void traverseGraph(Node node, ChttrScript chttrScript, Set<Node> visited) {
        if (visited.contains(node)) {
            return;
        }

        visited.add(node);

        // Only pay attention to PresentMulti.
        if(node.type().equals(NodeType.PresentMulti)) {
            // find the corresponding NodeType.ProcessMulti whose Edges contain the responses.
            Node expectedProcessNode = node.edges().getFirst().targetNode();
            if (!expectedProcessNode.type().equals(NodeType.ProcessMulti)) {
                throw new IllegalStateException("Unexpected node type: " + expectedProcessNode.type().name());
            }
            List<String> moResponses = new ArrayList<>();
            for (Edge edge : expectedProcessNode.edges()) {
                moResponses.addAll(edge.matchText());
            }
            Exchange exchange = new Exchange(node.text(), moResponses);
            LOG.info(exchange.toString());
            chttrScript.exchanges.add(exchange);
        }

        for (Edge edge : node.edges()) {
            if (edge.targetNode() != null) {
                traverseGraph(edge.targetNode(), chttrScript, visited);
            }
        }
    }

    public static void main(String[] args) {
//        if (args.length != 2) {
//            System.err.println("Usage: ScriptExporter <nodeId> <outputFile>");
//            return;
//        }

        UUID nodeId;
//        try {
            nodeId = UUID.fromString("89eddcb8-7fe5-4cd1-b18b-78858f0789fb"/*args[0]*/);
//        } catch (IllegalArgumentException e) {
//            System.err.println("Invalid nodeId: " + args[0]);
//            return;
//        }
        String outputFile = "exportedScript.ser";//args[1];

        try {
            PersistenceManager persistenceManager = PostgresPersistenceManager.createPersistenceManager(ConfigLoader.readConfig("persistence_manager_test.properties"));
            ScriptInterpreter exporter = new ScriptInterpreter(persistenceManager);
            exporter.exportScriptToFile(nodeId, outputFile);
        } catch (IOException | PersistenceManagerException e) {
            LOG.error("Failed to initialize PersistenceManager or export script", e);
        }
    }
}
