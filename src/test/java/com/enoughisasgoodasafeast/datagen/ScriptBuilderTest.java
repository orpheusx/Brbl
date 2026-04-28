package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.PostgresPersistenceManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static java.io.IO.println;

class ScriptBuilderTest {

    @Test
    void verifyScript() throws IOException, PersistenceManager.PersistenceManagerException {

        var properties = ConfigLoader.readConfig("chttr.properties"); // Assuming a config file
        var persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);

        var rootNode = persistenceManager.getNodeGraph(UUID.fromString(ScriptBuilder.NODE_ID_LIST[0].toString()/*"019d3522-ac0e-7e2a-95cc-d2db38b8fafc")*/));

        println(rootNode);
        //Node.printGraph(rootNode, rootNode, 2); // This will blow up. It's not written to handle the multiple cycles present in this script
    }

}