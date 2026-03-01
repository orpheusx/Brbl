package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.operator.Node;
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
}