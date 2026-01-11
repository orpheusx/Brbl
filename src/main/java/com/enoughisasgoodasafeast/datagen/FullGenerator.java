package com.enoughisasgoodasafeast.datagen;

import java.io.IO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class FullGenerator {
    static void main() throws IOException {
        BrblUsersGenerator usersGen = new BrblUsersGenerator();
        BrblLogicGenerator logicGen = new BrblLogicGenerator();

        final BrblUsersGenerator.DataSet userDataSet = usersGen.generateUserGraphs(100, 30, 10, 50);
        final List<String>  userSql = usersGen.dataToSQL(userDataSet);
        sqlToFile("brbl_users.sql", userSql);

        final Map<NodeRow, List<EdgeRow>> nodeEdgeMap = logicGen.generateNodeEdgeMap();
        final BrblLogicGenerator.DataSet routeScriptDataSet = logicGen.generateRoutesAndScripts(nodeEdgeMap, userDataSet.customers());
        final List<String> logicSql = logicGen.dataToSQL(nodeEdgeMap, routeScriptDataSet);
        sqlToFile("brbl_logic.sql", logicSql);
    }

    public static void sqlToFile(String fileName, List<String> sqlStatements) throws IOException {
        var filePath = Paths.get("dml", fileName);
        Files.write(filePath, sqlStatements, StandardCharsets.UTF_8);
        IO.println("Wrote " + filePath.getFileName() + " to disk.");
    }
}
