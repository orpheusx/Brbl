package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.operator.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
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

    public @Nullable ChttrScript translateNodeGraphToChttrScript(UUID nodeId) {
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

    public ChttrScript exportScriptToFile(UUID nodeId, String outputPath) {
        final ChttrScript chttrScript = translateNodeGraphToChttrScript(nodeId);
        try (FileOutputStream fileOut = new FileOutputStream(outputPath);
             ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {
            objectOut.writeObject(chttrScript);
            LOG.info("ChttrScript serialized to {}", outputPath);
        } catch (IOException e) {
            LOG.error("Error serializing ChttrScript", e);
        }
        return  chttrScript;
    }

    public boolean writeNodeGraphToFile(UUID nodeId, String outputPath) {
        Node rootNode = persistenceManager.getNodeGraph(nodeId);
        LOG.info("Writing Node {} to {}", nodeId, outputPath);
        Node.printGraph(rootNode, rootNode, 2);

        try (FileOutputStream fileOut = new FileOutputStream(outputPath);
             ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {
            objectOut.writeObject(rootNode);
            LOG.info("Node graph serialized to {}", outputPath);
        } catch (IOException e) {
            LOG.error("Error serializing Node graph", e);
            return false;
        }
        return true;
    }

    public Node readNodeGraphFromFile(UUID nodeId, String inputPath) {
        LOG.info("Reading Node {} from {}", nodeId, inputPath);
        try (FileInputStream fileIn = new FileInputStream(inputPath)) {
            ObjectInputStream objectIn = new ObjectInputStream(fileIn);
            Node rootNode = (Node) objectIn.readObject();
            if(!rootNode.id().equals(nodeId)) {
                LOG.error("Node graph deserialized from {} does not container root Node with id {}", inputPath, nodeId);
                return null;
            } else {
                return rootNode;
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void foo(UUID nodeId) {
        Node rootNode = persistenceManager.getNodeGraph(nodeId);
        // find all unique paths through the
    }

    static void main(String[] args) throws IOException, PersistenceManagerException {
        UUID nodeId = UUID.fromString("89eddcb8-7fe5-4cd1-b18b-78858f0789fb");
        PersistenceManager persistenceManager = PostgresPersistenceManager.createPersistenceManager(ConfigLoader.readConfig("persistence_manager_test.properties"));
        ScriptInterpreter interpreter = new ScriptInterpreter(persistenceManager);
        boolean writeOk = interpreter.writeNodeGraphToFile(nodeId, "nodeGraph.ser");
        if (writeOk) {
            LOG.info("Write ok");
            Node rootNode = interpreter.readNodeGraphFromFile(nodeId, "nodeGraph.ser");
            if (rootNode != null) {
                if(rootNode.id().equals(nodeId)) {
                    LOG.info("Read ok.");
                } else {
                    LOG.error("Ack! The read Node's id doesn't match {}", nodeId);
                }
            }
        }
    }
}
