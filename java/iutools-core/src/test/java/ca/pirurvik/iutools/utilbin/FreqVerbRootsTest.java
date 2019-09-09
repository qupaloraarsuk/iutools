package ca.pirurvik.iutools.utilbin;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.junit.Test;

import ca.nrc.datastructure.trie.StringSegmenterException;
import ca.nrc.datastructure.trie.StringSegmenter_IUMorpheme;
import ca.nrc.datastructure.trie.Trie;
import ca.nrc.datastructure.trie.TrieNode;
import ca.nrc.testing.AssertHelpers;
import ca.pirurvik.iutools.CompiledCorpus;
import ca.pirurvik.iutools.CompiledCorpusException;

public class FreqVerbRootsTest {

	@Test
	public void test_compileFreqs() throws IOException, CompiledCorpusException, StringSegmenterException {
		String[] stringsOfWords = new String[] {
				"nunami takujuq iglumik siniktuq takujaujuq iijuq"
				};
		String corpusDirPathname = createTemporaryCorpusDirectory(stringsOfWords);
        CompiledCorpus compiledCorpus = new CompiledCorpus(StringSegmenter_IUMorpheme.class.getName());
        compiledCorpus.setVerbose(false);
        compiledCorpus.compileCorpusFromScratch(corpusDirPathname);
		Trie trie = compiledCorpus.getTrie();
		HashMap<String,TrieNode> nodesOfRootsOfWords = trie.getRoot().getChildren();
		String rootIds[] = nodesOfRootsOfWords.keySet().toArray(new String[] {});
		assertEquals("", 5, rootIds.length);
		FreqVerbRootsCompiler freqVerbRootsCompiler = new FreqVerbRootsCompiler();
		HashMap<String,Long> freqsVerbRoots = freqVerbRootsCompiler.compileFreqs(compiledCorpus);
		String verbRootIds[] = freqsVerbRoots.keySet().toArray(new String[] {});
		assertEquals("The number of verb roots returned is incorrect.",3,verbRootIds.length);
		String expected[] = new String[] {"taku/1v","sinik/1v","ii/1v"};
		Long expectedFreqs[] = new Long[] {new Long(2),new Long(1),new Long(1)};
		AssertHelpers.assertContainsAll("The verb roots returned are not as expected.", verbRootIds, expected);
		for (int i=0; i<verbRootIds.length; i++)
			assertEquals("The frequency of "+expected[i]+" is not as expected.",expectedFreqs[i],freqsVerbRoots.get(expected[i]));
	}
	
	// -----------------------------------------------------------------

    private String createTemporaryCorpusDirectory(String[] stringOfWords) throws IOException {
    	Logger logger = Logger.getLogger("CompiledCorpusTest.createTemporaryCorpusDirectory");
        File corpusDirectory = Files.createTempDirectory("").toFile();
        corpusDirectory.deleteOnExit();
        String corpusDirPath = corpusDirectory.getAbsolutePath();
        for (int i=0; i<stringOfWords.length; i++) {
        	File wordFile = new File(corpusDirPath+"/contents"+(i+1)+".txt");
        	BufferedWriter bw = new BufferedWriter(new FileWriter(wordFile));
        	bw.write(stringOfWords[i]);
        	bw.close();
        	logger.debug("wordFile= "+wordFile.getAbsolutePath());
        	logger.debug("contents= "+wordFile.length());
        }
        return corpusDirPath;
	}
}
