package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.operator.Edge;
import com.enoughisasgoodasafeast.operator.Node;
import com.enoughisasgoodasafeast.operator.NodeType;
import com.enoughisasgoodasafeast.operator.TestingPersistenceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScriptInterpreterTest {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptInterpreterTest.class);

    private static String A = "A";
    private static String B = "B";
    private static String C = "C";
    private static String D = "D";
    private static String E = "E";
    private static String F = "F";
    private static String G = "G";


    ScriptInterpreter interpreter = null;

    @BeforeEach
    void setUp() {
        interpreter = new ScriptInterpreter(new TestingPersistenceManager());
    }

    @Test
    void findAllPathsInDirectedGraph() {
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

    private Node constructComplexGraph() {
        var a = new Node("a: b,c,d", NodeType.PresentMulti, A);
        var b = new Node("b: e", NodeType.ProcessMulti, B);
        var c = new Node("c: e,f", NodeType.PresentMulti, C);
        var d = new Node("d: f", NodeType.PresentMulti, D);
        var e = new Node("e: a,g", NodeType.PresentMulti, E);
        var f = new Node("f: a,g", NodeType.PresentMulti, F);
        var g = new Node("g: nil", NodeType.ProcessMulti, G); // could reasonably point to A, as well.

        var a2b = new Edge("a to b", b);
        var a2c = new Edge("a to c", c);
        var a2d = new Edge("a to d", d);
        a.edges().add(a2b);
        a.edges().add(a2c);
        a.edges().add(a2d);

        var b2e = new Edge("b to e", e);
        b.edges().add(b2e);

        var c2e = new Edge("c to e", e);
        var c2f = new Edge("c to f", f);
        c.edges().add(c2e);
        c.edges().add(c2f);

        var d2c = new Edge("d to c", c);
        var d2f = new Edge("d to f", f);
        d.edges().add(d2c);
        d.edges().add(d2f);

        var e2a = new Edge("e to a", a);
        var e2g = new Edge("e to g", g);
        e.edges().add(e2a);
        e.edges().add(e2g);

        var f2g = new Edge("f to g", g);
        var f2a = new Edge("f to a", a);
        f.edges().add(f2g);
        f.edges().add(f2a);

        return a;
    }
}