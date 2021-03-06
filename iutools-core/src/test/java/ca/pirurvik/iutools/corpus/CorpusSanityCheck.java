package ca.pirurvik.iutools.corpus;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Do a sanity check on an actual compiled corpus.
 * 
 * @author desilets
 *
 */
public abstract class CorpusSanityCheck {

	protected abstract CompiledCorpus corpusToCheck() throws Exception;
	protected abstract Map<String,Object> expectations();
	
	CompiledCorpus corpus = null;
	
	@Before
	public void setUp() throws Exception {
		corpus = corpusToCheck();
	}
	
	@Test
	public void test__BasicStats() throws Exception {
		
//		System.out.println("totalWords="+corpus.totalWords());
//		System.out.println("totaltotalOccurences="+corpus.totalOccurences());
//		System.out.println("totalWordsWithDecomps="+corpus.totalWordsWithDecomps());
//		System.out.println("totalOccurencesWithDecomps="+corpus.totalOccurencesWithDecomps());
//		System.out.println("totalWordsWithNoDecomp="+corpus.totalWordsWithNoDecomp());
//		System.out.println("totalOccurencesWithNoDecomp="+corpus.totalOccurencesWithNoDecomp());
		new AssertCompiledCorpus(corpus, "")
			.totalWordsIs(expTotalWords())
			.totalOccurencesIs(1456076)
			.totalWordsWithDecompIs(221088)
			.totalOccurencesWithDecompIs(1098245)
			.totalWordsWithoutDecompsIs(166215)
			.totalOccurencesWithNoDecompIs(357831)
			;
	}
	
	// Check that the information about word 'nunavut' is correct
	@Test
	public void test__inuktut__WordInfo() throws Exception {
		String inuktut = "inuktut";
		
		new AssertCompiledCorpus(corpus, "")
			.infoForWordIs(inuktut, 
				expWordFreq(inuktut), 
				expTotalDecomps(inuktut), 
				expSampleDecomps(inuktut))
			;	
		
		WordInfo gotInfo = corpus.info4word("inuktut");
//		System.out.println("info for inuktut:\n"+PrettyPrinter.print(gotInfo));
	}

	@Test
	public void test__nuna__ngram() throws Exception{
		String nuna = "nuna";

		new AssertCompiledCorpus(corpus, "")
			.totalWordsWithNgramEquals(nuna, expNgramTotalWords(nuna))
			;
	}

	private long expNgramTotalWords(String ngram) throws Exception {
		String key = ngram+":totalWords";
		if (! expectations().containsKey(key)) {
			throw new Exception(
				"Expectations did not provide a totalWords for ngram "+ngram+
				"\nMake sure you include a key "+key+" in the map returned by method expectations()");
		}
		long exp = (long) (expectations().get(key));
		return exp;
	}

	private long expNgramFreq(String ngram) throws Exception{
		String key = ngram+":freq";
		if (! expectations().containsKey(key)) {
			throw new Exception(
				"Expectations did not provide a frequency for ngram "+ngram+
				"\nMake sure you include a key "+key+" in the map returned by method expectations()");
		}
		long exp = (long) (expectations().get(key));
		return exp;
	}

	private String[][] expSampleDecomps(String word) throws Exception {
		String key = word+":sampleDecomps";
		if (! expectations().containsKey(key)) {
			throw new Exception(
					"Expectations did not provide a sampleDecomps for word "+word+
					"\nMake sure you include a key "+key+" in the map returned by method expectations()");
		}			
		String[][] exp = (String[][]) (expectations().get(key));
		return exp;
	}
	
	
	private int expTotalDecomps(String word) throws Exception {
		String key = word+":totDecomps";
		Integer exp = (Integer) (expectations().get(key));
		if (exp == null) {
			throw new Exception(
			"Expectations did not provide a totDecomps for word "+word+
			"\nMake sure you add a key "+key+" to the map returned by expectations()");
		}
		return exp;
	}
	
	private long expWordFreq(String word) throws Exception {
		String key = word+":freq";
		Long exp = (Long) (expectations().get(key));
		if (exp == null) {
			throw new Exception(
				  "Expectations did not provide a frequency for word "+word
				+ "\nMake sure you add a key "+key+" in the map returned by method expectations()");
		}
		return exp;
	}
	
	private long expTotalWords() {
		Long exp = (Long) (expectations().get("totalWords"));
		return exp;
	}
	
}
