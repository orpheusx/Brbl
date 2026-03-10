package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.operator.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IO;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScriptInterpreterTest {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptInterpreterTest.class);

    static final String A0 = "A0";
    static final String A1 = "A1";
    static final String B0 = "Bs";
    static final String C0 = "C0";
    static final String C1 = "C1";
    static final String D0 = "D0";
    static final String D1 = "D1";
    static final String E0 = "E0";
    static final String E1 = "E1";
    static final String F0 = "F0";
    static final String F1 = "F1";
    static final String G0 = "G0";
    static final String G1 = "G1";


    ScriptInterpreter interpreter = null;
    PersistenceManager legitPersistenceManager = null;

    @BeforeEach
    void setUp() throws IOException, PersistenceManager.PersistenceManagerException {
        interpreter = new ScriptInterpreter(new TestingPersistenceManager());
        var properties = ConfigLoader.readConfig("chttr.properties"); // Assuming a config file
        legitPersistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);
    }

    @Test
    void findAllPathsInDirectedGraphSimple() {
        final var allPathsInDirectedGraph = interpreter.findAllPathsInDirectedGraph("./data/simpleThreeNodeGraph.ser");
        assertNotNull(allPathsInDirectedGraph);
        assertEquals(3, allPathsInDirectedGraph.size());

        for (List<Node> path : allPathsInDirectedGraph) {
            interpreter.printPath(path);
        }
    }

    @Test
    void findAllPathsInDirectedGraphComplex() {
        var node = constructComplexGraph();
        final var allPaths = interpreter.findAllPathsInDirectedGraph(node);
        assertNotNull(allPaths);
        assertEquals(12, allPaths.size());

        LOG.info("Found {} unique paths for the given graph.", allPaths.size());
        for (List<Node> path : allPaths) {
            interpreter.printPath(path);
        }
    }

//    @Test
//    void convertNodePathToDirectedExchangeList() {
//        final var node = constructComplexGraph();
//        final var pathOfNodes = interpreter.findAllPathsInDirectedGraph(node);
//        for (List<Node> path : pathOfNodes) {
//            final var directedExchanges = interpreter.nodeListToChttrScript(path);
//            interpreter.printPath(path);
//            LOG.info("exchanges for path: {}", directedExchanges);
//        }
//    }

    @Test
    void convertNodePathEventList() {
        final var node = constructComplexGraph();
        final var pathOfNodes = interpreter.findAllPathsInDirectedGraph(node);
        assertNotNull(pathOfNodes);
        var eventList = interpreter.nodePathToEventList(pathOfNodes.stream().findFirst().get());
        assertNotNull(eventList);
        LOG.info(eventList.toString());
        assertEquals(5, eventList.size());
    }

    @Test
    void convertSimpleNodePathEventListIntegration() {
        var nodeFromDisk = interpreter.readNodeGraphFromFile("./data/simpleThreeNodeGraph.ser");
        assertNotNull(nodeFromDisk);
        final var pathsFromDisk = interpreter.findAllPathsInDirectedGraph(nodeFromDisk);
        assertNotNull(pathsFromDisk);
        assertFalse(pathsFromDisk.isEmpty());
        for (List<Node> path : pathsFromDisk) {
            //interpreter.printPath(simplePath);
            var events = interpreter.nodePathToEventList(path);
            assertNotNull(events);
            LOG.info("Events from disk:\n{}", events);
            assertEquals(7, events.size());
        }

        // Load it from the database and compare the results.
        final var nodeId = UUID.fromString("89eddcb8-7fe5-4cd1-b18b-78858f0789fb");
        final var nodeFromDb = legitPersistenceManager.getNodeGraph(nodeId);

        assertEquals(nodeFromDb, nodeFromDisk); // NB: only compares the nodes by their id. Not a full comparison of the graph.

        final var pathsFromDb = interpreter.findAllPathsInDirectedGraph(nodeFromDb);

        assertNotNull(pathsFromDb);
        assertFalse(pathsFromDb.isEmpty());

        for (List<Node> path : pathsFromDb) {
            //interpreter.printPath(simplePath);
            var events = interpreter.nodePathToEventList(path);
            assertNotNull(events);
            LOG.info("Events from database:\n{}", events);
            assertEquals(7, events.size());
        }



        assertEquals(pathsFromDisk.size(), pathsFromDb.size());
        //assertEquals(pathsFromDisk, pathsFromDb);
    }

    private Node constructComplexGraph() {
        // Remember PresentMulti doesn't make sense unless paired with a0 ProcessMulti.
        var a0 = new Node("a0: present bS,c0,d0", NodeType.PresentMulti, A0);
        var a1 = new Node("a0: process bS,c0,d0", NodeType.ProcessMulti, A1);

        var bS = new Node("bS: send to e0", NodeType.SendMessage, B0);

        var c0 = new Node("c0: present e0,f0", NodeType.PresentMulti, C0);
        var c1 = new Node("c1: process e0,f0", NodeType.ProcessMulti, C1);

        var d0 = new Node("d0: present f0", NodeType.PresentMulti, D0);
        var d1 = new Node("d1: process f0", NodeType.ProcessMulti, D1);

        var e0 = new Node("e0: present a0,g0", NodeType.PresentMulti, E0);
        var e1 = new Node("e1: process a0,g0", NodeType.ProcessMulti, E1);

        var f0 = new Node("f0: present a0,g0", NodeType.PresentMulti, F0);
        var f1 = new Node("f1: process a0,g0", NodeType.ProcessMulti, F1);

        var g0 = new Node("g0: eoc", NodeType.EndOfChat, G0);

        // Connect all the presentMulti nodes to their corresponding processMulti nodes, no matching is used here.
        var a0_a1 = new Edge("a0 to a1", a1);
        a0.edges().add(a0_a1);

        // bS goes direct to e0 so no connector is needed.

        var c0_c1 = new Edge("c0 to c1", c1);
        c0.edges().add(c0_c1);

        var d0_d1 = new Edge("d0 to d1", d1);
        d0.edges().add(d0_d1);

        var e0_e1 = new Edge("e0 to e1", e1);
        e0.edges().add(e0_e1);

        var f0_f1 = new Edge("f0 to f1", f1);
        f0.edges().add(f0_f1);

        // Next attach the Edges to the processMulti nodes
        // bS,c0,d0
        var a1_bS = new Edge(List.of("bS"),"a1 to bS", bS);
        var a1_c0 = new Edge(List.of("c0"),"a1 to c0", c0);
        var a1_d0 = new Edge(List.of("d0"),"a1 to d0", d0);
        a1.edges().add(a1_bS);
        a1.edges().add(a1_c0);
        a1.edges().add(a1_d0);

        var bS_to_e0 = new Edge("bS to e0", e0); // no matching here
        bS.edges().add(bS_to_e0);

        var c1_e0 = new Edge(List.of("e0"),"c1 to e0", e0);
        var c1_f0 = new Edge(List.of("f0"),"c1 to f0", f0);
        c1.edges().add(c1_e0);
        c1.edges().add(c1_f0);

        var d1_c0 = new Edge(List.of("c0"),"d1 to c0", c0);
        var d1_f0 = new Edge(List.of("f0"),"d1 to f0", f0);
        d1.edges().add(d1_c0);
        d1.edges().add(d1_f0);

        var e1_a0 = new Edge(List.of("a0"),"e1 to a0", a0);
        var e1_g0 = new Edge(List.of("g0"),"e1 to g0", g0);
        e1.edges().add(e1_a0);
        e1.edges().add(e1_g0);

        var f1_g0 = new Edge(List.of("g0"),"f1 to g0", g0);
        var f1_a0 = new Edge(List.of("a0"),"f1 to a0", a0);
        f1.edges().add(f1_g0);
        f1.edges().add(f1_a0);

        g0.edges().add(new Edge("EndOfChat", null));

        return a0;
    }
}