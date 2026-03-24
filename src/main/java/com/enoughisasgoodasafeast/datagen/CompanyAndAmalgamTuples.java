package com.enoughisasgoodasafeast.datagen;

import java.util.ArrayList;
import java.util.List;

public record CompanyAndAmalgamTuples(
            CompanyRow companyRow,
            List<AmalgamTuple> amalgamTuples) {

    public CompanyAndAmalgamTuples(CompanyRow companyRow) {
        this(companyRow, new ArrayList<>());
    }

    public void addTuple(AmalgamTuple amalgamTuple) {
        amalgamTuples.add(amalgamTuple);
    }
};
