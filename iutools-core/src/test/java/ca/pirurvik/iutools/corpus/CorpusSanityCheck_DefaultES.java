package ca.pirurvik.iutools.corpus;

import java.util.HashMap;
import java.util.Map;

public class CorpusSanityCheck_DefaultES extends CorpusSanityCheck {
    @Override
    protected CompiledCorpus corpusToCheck() throws Exception {
        CompiledCorpus corpus =
                CompiledCorpusRegistry
                        .getCorpus();
        return corpus;
    }

    @Override
    protected Map<String, Object> expectations() {
        Map<String,Object> exp = new HashMap<String,Object>();

        exp.put("totalWords", new Long(387303));

        exp.put("inuktut:freq", new Long(5));
        exp.put("inuktut:totDecomps", new Integer(1));
        exp.put("inuktut:sampleDecomps",
                new String[][] {
                        new String[] {"{inuk/1n}", "{tut/tn-sim-s}", "\\"}
                }
        );

        exp.put("nuna:freq", new Long(2823));

        return exp;
    }


}
