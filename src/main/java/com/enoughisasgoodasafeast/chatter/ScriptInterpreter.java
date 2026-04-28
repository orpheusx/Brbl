package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.operator.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.enoughisasgoodasafeast.MessageType.*;
import static java.io.IO.println;

public class ScriptInterpreter {
    private static final Logger LOG = LoggerFactory.getLogger(ScriptInterpreter.class);
    private final PersistenceManager persistenceManager;

    public ScriptInterpreter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public @Nullable List<ChttrScript> translateNodeGraphToChttrScripts(UUID nodeId) {
        Node rootNode = persistenceManager.getNodeGraph(nodeId);
        if (rootNode == null) {
            LOG.error("Node graph not found for nodeId: {}", nodeId);
            return null;
        }

        //Node.printGraph(rootNode, rootNode, 2);
        final Set<List<Node>> paths = findAllPathsInDirectedGraph(rootNode);
        if (paths == null || paths.isEmpty()) {
            LOG.error("No paths identified for node graph: {}", rootNode);
            return null;
        }

        List<ChttrScript> scripts = new ArrayList<>();

        for (List<Node> path : paths) {
            final List<Event> eventsForPath = nodePathToEventList(path);
            if(eventsForPath == null) {
                LOG.error("No events identified for path: {}", path);
                return null; // fail as fast as possible.
            }

            scripts.add(new ChttrScript(eventsForPath));
        }

        return scripts;
    }

    /**
     *  Given an example path:
     *       PresentMulti: A0 ->
     *       ProcessMulti: A1 ->
     *       PresentMulti: C0 ->
     *       ProcessMulti: C1 ->
     *       PresentMulti: E0 ->
     *       ProcessMulti: E1 -> EndOfChat: G0
     *  We want the event list to look like the following:
     *      mt: "a0: present bS,c0,d0"
     *      mo: "c0"
     *      mt: "a1 to c0"
     *      mt: "c0: present e0,f0"
     *      mo: "e0"
     *      mt: "c1 to e0"
     *      mt: "e0: present a0,g0"
     *      mo: "g0"
     * @param nodePath the list of nodes that define a path through a script graph.
     * @return the list of Events used by ChttrClient to interact with Brbl.
     */
    public List<Event> nodePathToEventList(List<Node> nodePath) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < nodePath.size(); i++) {
            Node node = nodePath.get(i);
            switch (node.type()) {
                case PRESENT_MULTI, SEND_MESSAGE, END_OF_CHAT -> {
                    events.add(new Event(MT, node.text()));
                }
                case PROCESS_MULTI -> {
                    if (i + 1 >= nodePath.size()) break;

                    Node nextNode = nodePath.get(1+i); // this is the node the path specifies
                    for (Edge edge : node.edges()) {
                        if(edge.targetNode().equals(nextNode)) {
                            // TODO create variations to test that regex matching works for all cases:
                            events.add(new Event(MO, edge.matchText().getFirst()));
                            events.add(new Event(MT, edge.responseText()));
                            break;
                        }
                    }
                }
            }
        }
        return events;
    }

//    public List<DirectedExchange> nodeListToChttrScript(List<Node> nodePath) {
//        // For the moment let's assume the nodes are in workable order (i.e. the Present and Process nodes are correctly paired.
//        var exchanges = new ArrayList<DirectedExchange>();
//
//        // Iterate over the nodes in the list.
//        // If the node is a PresentMulti, grab its text property
//        // From the node's edge (validate that it is the only edge?) get the paired ProcessMulti node.
//        // from the process node
//
//        for (int i = 0; i < nodePath.size(); i++) {
//            var node = nodePath.get(i);
//
//            String mtInput = null;
//            String moOutput = null;
//            String mtAcknowledgement = null;
//
//            switch(node.type()) {
//                case PresentMulti -> {
//                    // These will only issue MTs
//                    mtInput = node.text();
//                }
//                case ProcessMulti -> {
//                    // Notice that we're incrementing the counter here. Kinda gross but...
//                    Node nextNode = nodePath.get(++i); // this is the node the path specifies
//                    if(nextNode==null) throw new IllegalStateException("A ProcessMulti cannot be the last node in a path.");
//
//                    // find it in the set of edges so we know what the MO response should be.
//                    for (Edge edge : node.edges()) {
//                        if(edge.targetNode().equals(nextNode)) {
//                            moOutput = edge.matchText().getFirst(); // TODO create variations to prove that regex matching works for all cases.
//                            mtAcknowledgement = edge.responseText();
//                        }
//                    }
//
//                }
//                case EndOfChat -> {
//                    LOG.info("EndOfChat reached.");
//                }
//                case SendMessage -> {
//                    mtInput = node.text();
//                }
//            }
//
//            exchanges.add(new DirectedExchange(mtInput, moOutput, mtAcknowledgement));
//        }
//
//        return  exchanges;
//    }

//    private void traverseGraph(Node node, ChttrScript chttrScript, Set<Node> visited) {
//        if (visited.contains(node)) {
//            return;
//        }
//
//        visited.add(node);
//
//        // Only pay attention to PresentMulti.
//        if(node.type().equals(NodeType.PresentMulti)) {
//            // find the corresponding NodeType.ProcessMulti whose Edges contain the responses.
//            Node expectedProcessNode = node.edges().getFirst().targetNode();
//            if (!expectedProcessNode.type().equals(NodeType.ProcessMulti)) {
//                throw new IllegalStateException("Unexpected node type: " + expectedProcessNode.type().name());
//            }
//            List<String> moResponses = new ArrayList<>();
//            for (Edge edge : expectedProcessNode.edges()) {
//                moResponses.addAll(edge.matchText());
//            }
//            Exchange exchange = new Exchange(node.text(), moResponses);
//            LOG.info(exchange.toString());
////            chttrScript.exchanges.add(exchange);
//        }
//
//        for (Edge edge : node.edges()) {
//            if (edge.targetNode() != null) {
//                traverseGraph(edge.targetNode(), chttrScript, visited);
//            }
//        }
//    }

//    public ChttrScript exportScriptToFile(UUID nodeId, String outputPath) {
//        final ChttrScript chttrScript = translateNodeGraphToChttrScripts(nodeId);
//        try (FileOutputStream fileOut = new FileOutputStream(outputPath);
//             ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {
//            objectOut.writeObject(chttrScript);
//            LOG.info("ChttrScript serialized to {}", outputPath);
//        } catch (IOException e) {
//            LOG.error("Error serializing ChttrScript", e);
//        }
//        return  chttrScript;
//    }

    public boolean writeNodeGraphToFile(UUID nodeId, String outputPath) {
        Node rootNode = persistenceManager.getNodeGraph(nodeId);
        LOG.info("Writing Node {} to {}", nodeId, outputPath);
        Node.printGraph(rootNode, rootNode, 2);

        return writeNodeGraphToFile(rootNode, outputPath);
    }

    public boolean writeNodeGraphToFile(Node node, String outputPath) {
        try (FileOutputStream fileOut = new FileOutputStream(outputPath);
             ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {
            objectOut.writeObject(node);
            LOG.info("Node graph serialized to {}", outputPath);
            return true;
        } catch (IOException e) {
            LOG.error("Error serializing Node graph", e);
            return false;
        }
    }

    public Node readNodeGraphFromFile(String inputPath) {
        LOG.info("Reading Node from {}", inputPath);
        try (FileInputStream fileIn = new FileInputStream(inputPath)) {
            ObjectInputStream objectIn = new ObjectInputStream(fileIn);
            return (Node) objectIn.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    public @Nullable Set<List<Node>> findAllPathsInDirectedGraph(String filePath) {
        Node rootNode = readNodeGraphFromFile(filePath);
        if (rootNode == null) {
            LOG.error("Failed to read node graph.");
            return null;
        }
        return findAllPathsInDirectedGraph(rootNode);
    }

    public @Nullable Set<List<Node>> findAllPathsInDirectedGraph(@NonNull Node rootNode) {
        SequencedSet<List<Node>> allPaths = new LinkedHashSet<>();
        List<Node> currentPath = new ArrayList<>();
        findAllPaths(rootNode, currentPath, allPaths);

        // This is kind of a Band-Aid to normalize the differences loading rootNode from disk vs database.
        // Other kinds of cycles are, obviously, not addressed (for now.)
        for  (List<Node> path : allPaths) {
            if(path.getFirst().equals(path.getLast())) {
                LOG.warn("Trimming node causing cycle: '{}'", path.getLast().text());
                path.removeLast();
            }
        }

        return allPaths;
    }

    private void findAllPaths(Node node, List<Node> currentPath, Set<List<Node>> allPaths) {
        currentPath.add(node);

        boolean isLeaf = true;
        if (node.edges() != null && !node.edges().isEmpty()) {
            for (Edge edge : node.edges()) {
                Node target = edge.targetNode();
                if (target != null) {
                    isLeaf = false;
                    if (!currentPath.contains(target)) {
                        findAllPaths(target, currentPath, allPaths);
                    } else {
                        // Cycle detected. Add the paths up to this point, including the node that starts the cycle.
                        List<Node> cyclePath = new ArrayList<>(currentPath);
                        cyclePath.add(target);
                        allPaths.add(cyclePath);
                    }
                }
            }
        }
        
        if (isLeaf) {
            allPaths.add(new ArrayList<>(currentPath));
        }

        currentPath.remove(currentPath.size() - 1); // Backtrack
    }

    void printPath(List<Node> path) {
        System.out.println("Path: " + formatPath(path));
    }

    private String formatPath(List<Node> path) {
        return path.stream()
                .map(node -> node.type().name() + ": " + node.label())
                .collect(Collectors.joining(" -> "));
    }

    public static void collectGraphIds(Node startNode, Node node, Set<String> nodeIds, Set<String> edgeIds) {

        //printId(node);
        nodeIds.add(node.id().toString());

        for (Edge edge : node.edges()) {
            //printId(edge);
            edgeIds.add(edge.id().toString());

            Node childNode = edge.targetNode();
            if (childNode != null && startNode != childNode) {
                collectGraphIds(startNode, childNode, nodeIds, edgeIds);
            }
        }
    }

    static void printId(Node node) {
        println("Node:" + node.id());
    }
    static void printId(Edge edge) {
        println("Edge:" + edge.id());
    }

    static void main(String[] args) throws IOException, PersistenceManagerException {
//        var nodeId = UUID.fromString("89eddcb8-7fe5-4cd1-b18b-78858f0789fb");
        String[] nodeIds = new String[]{
//                "89eddcb8-7fe5-4cd1-b18b-78858f0789fb", -- Fave color
//                "b48d36ce-2512-4ee0-a9b9-b743d72e95e9",
//                "385a1f99-d844-42e6-9fa3-a0e3a116757d",
//                "0b2861b6-a16a-4197-910a-158610967dd9",
//                "525028ae-0a33-4c80-a22f-868f77bb9531",
//                "cf72ce06-50fc-4bf1-852b-dbdbd9f97f66"
//                "525028ae-0a33-4c80-a22f-868f77bb9531" -- BadPeople
                "0fc4ef6c-082f-4e90-b2f4-e14dbac78623"
        };
        var properties = ConfigLoader.readConfig("chttr.properties"); // Assuming a config file
        var ppm = PostgresPersistenceManager.createPersistenceManager(properties);
        for (var nodeId : nodeIds) {
            UUID nodeUuid = UUID.fromString(nodeId);
            Node dbNode = ppm.getNodeGraph(nodeUuid);
            Set<String> nodeList = new LinkedHashSet<String>();
            Set<String> edgeList = new LinkedHashSet<>();
            nodeList.add(dbNode.id().toString());
            collectGraphIds(dbNode, dbNode,  nodeList, edgeList);
            nodeList.forEach( node -> {println(String.format("'%s'", node));});
            println("\n");
            edgeList.forEach( edge -> {println(String.format("'%s'", edge));});
//            IO.println(nodeList);
//            IO.println(edgeList);
        }
        //var interpreter = new ScriptInterpreter(ppm);


//        Node.printGraph(dbNode, dbNode, 2);
//        String fileName = "./data/simpleThreeNodeGraph.ser";
//        interpreter.writeNodeGraphToFile(dbNode, fileName);
//        var diskNode = interpreter.readNodeGraphFromFile(fileName);
    }
}
