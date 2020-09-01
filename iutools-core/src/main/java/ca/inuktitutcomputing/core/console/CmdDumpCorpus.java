package ca.inuktitutcomputing.core.console;

import ca.nrc.ui.commandline.ProgressMonitor_Terminal;
import ca.pirurvik.iutools.corpus.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CmdDumpCorpus extends ConsoleCommand {

    public CmdDumpCorpus(String name) {
        super(name);
    }

    @Override
    public String getUsageOverview() {
        return "Dump the content of a corpus to a JSON file.";
    }

    @Override
    public void execute() throws Exception {
        String corpusName = getCorpusName(false);
        CompiledCorpus corpus = CompiledCorpusRegistry.getCorpus(corpusName);
        boolean wordsOnly = getWordsOnlyOpt();
        File outputFile = getDataFile(true);
        dumpCorpus(corpus, wordsOnly, outputFile);
    }

    private void dumpCorpus(CompiledCorpus corpus, boolean wordsOnly, 
    		File outputFile) throws IOException, CLIException, CompiledCorpusException {

        long totalWords = corpus.totalWords();
        ProgressMonitor_Terminal progMonitor =
            new ProgressMonitor_Terminal(totalWords, "Dumping words of corpus to file");

        FileWriter fWriter = new FileWriter(outputFile);

        printHeaders(fWriter);

        Iterator<String> iterator = corpus.allWords();
        while (iterator.hasNext()) {
            String word = iterator.next();
            printWord(word, corpus, wordsOnly, fWriter);
            progMonitor.stepCompleted();
        }
        fWriter.close();
    }

    private void printHeaders(FileWriter fWriter) throws IOException {
        fWriter.write("" +
            "class=ca.pirurvik.iutools.corpus.WordInfo_ES\n" +
            "bodyEndMarker=NEW_LINE\n\n");
    }

    private void printWord(String word, CompiledCorpus corpus, 
			boolean wordsOnly, FileWriter fWriter) 
			throws CLIException, IOException, CompiledCorpusException {
		String infoStr = word;
		if (!wordsOnly) {
            WordInfo wInfo = corpus.info4word(word);
            if (wInfo == null) {
                wInfo = new WordInfo_ES(word);
                wInfo.setDecompositions(new String[0][], 0);
            }
			ObjectMapper mapper = new ObjectMapper();
			try {
				infoStr = mapper.writeValueAsString(wInfo);
			} catch (JsonProcessingException e) {
				throw new CLIException(e);
			}
		}
		fWriter.write(infoStr+"\n");
	}
}
