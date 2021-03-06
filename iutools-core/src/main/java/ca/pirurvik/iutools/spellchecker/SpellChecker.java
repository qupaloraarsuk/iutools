package ca.pirurvik.iutools.spellchecker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.inuktitutcomputing.utilities.StopWatch;
import ca.pirurvik.iutools.corpus.*;
import org.apache.commons.collections4.iterators.IteratorChain;
import org.apache.log4j.Logger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;

import ca.nrc.config.ConfigException;
import ca.nrc.datastructure.Pair;
import ca.nrc.datastructure.trie.StringSegmenterException;
import ca.nrc.datastructure.trie.StringSegmenter_AlwaysNull;
import ca.nrc.datastructure.trie.StringSegmenter_IUMorpheme;
import ca.nrc.json.PrettyPrinter;
import ca.pirurvik.iutools.NumericExpression;
import ca.pirurvik.iutools.edit_distance.EditDistanceCalculator;
import ca.pirurvik.iutools.edit_distance.EditDistanceCalculatorException;
import ca.pirurvik.iutools.edit_distance.EditDistanceCalculatorFactory;
import ca.pirurvik.iutools.edit_distance.EditDistanceCalculatorFactoryException;
import ca.pirurvik.iutools.text.ngrams.NgramCompiler;
import ca.pirurvik.iutools.text.segmentation.IUTokenizer;
import ca.inuktitutcomputing.config.IUConfig;
import ca.inuktitutcomputing.data.LinguisticDataException;
import ca.inuktitutcomputing.morph.Decomposition;
import ca.inuktitutcomputing.morph.MorphologicalAnalyzer;
import ca.inuktitutcomputing.morph.MorphologicalAnalyzerException;
import ca.inuktitutcomputing.script.Orthography;
import ca.inuktitutcomputing.script.Syllabics;
import ca.inuktitutcomputing.script.TransCoder;
import ca.inuktitutcomputing.utilbin.AnalyzeNumberExpressions;

// TODO-June2020: Handling of numeric vs non-numeric expressions is a bloody 
//    mess!! Refactor code so we don't need to keep separate data structures 
//    (ex: ngram stats) for numeric versus non-numeric expressions.
//
//    Better to spell check numeric and non-numeric expressions in the exact 
//    same fashion, except that for numeric expressions, we run a final filter 
//    at the end to only retain candidates which are numeric expressions.
//

public class SpellChecker {
	
	public int MAX_SEQ_LEN = 3;

	public int MIN_NGRAM_LEN = 3;
	public int MAX_NGRAM_LEN = 4;


	public int MAX_CANDIDATES = 2000;
	public int DEFAULT_CORRECTIONS = 5;
	
	/** Maximum msecs allowed for decomposing a word during 
	 *  spell checker
	 */
	private final long MAX_DECOMP_MSECS = 5*1000;

	protected CompiledCorpus explicitlyCorrectWords =
		new CompiledCorpus_InMemory()
		.setSegmenterClassName(StringSegmenter_AlwaysNull.class);
	
	// TODO-June2020: Can we get rid of this and use the explicitlyCorrectWords
	//   CompiledCorpus instance instead?
	/** 
	 * Words that are NOT numeric expressions and were EXPLICLITLY as being 
	 * correct
	 */
	protected Set<String> explicitlyCorrect_NonNumeric = new HashSet<String>();

	// TODO-June2020: Can we get rid of this and use the explicitlyCorrectWords
	//   CompiledCorpus instance instead?	
	/** 
	 * Words that ARE numeric expressions and were EXPLICLITLY as being 
	 * correct
	 */
	protected Set<String> explicitlyCorrect_Numeric = new HashSet<String>();

	// TODO-June2020: Can we get rid of this attribute?
	public String allWords = ",,";
	
	// TODO-June2020: Can we get rid of this attribute?
	public String allNormalizedNumericTerms = ",,";
	
	/** If true, partial corrections are enabled. That measns the spell checker
	 *  will identify the longest leading and tailing strings that seem 
	 *  correctly spelled.*/
	private boolean partialCorrectionEnabled = false;
		public SpellChecker setPartialCorrectionEnabled(boolean flag) {
			partialCorrectionEnabled = flag;
			return this;
		}
		public SpellChecker enablePartialCorrections() {
			partialCorrectionEnabled = true;
			return this;
		}
		public SpellChecker disablePartialCorrections() {
			partialCorrectionEnabled = false;
			return this;
		}
	
	public transient EditDistanceCalculator editDistanceCalculator;
	public transient boolean verbose = true;
	
	public CompiledCorpus corpus = null;
	
	private static StringSegmenter_IUMorpheme segmenter = null;
	private transient String[] makeUpWords = new String[] {"sivu","sia"};
	private static ArrayList<String> latinSingleInuktitutCharacters = new ArrayList<String>();
	static {
		for (int i=0; i<Syllabics.syllabicsToRomanICI.length; i++) {
			latinSingleInuktitutCharacters.add(Syllabics.syllabicsToRomanICI[i][1]);
		};
	}
	
    public static Cache<String, Set<String>> 
		wordsWithNgramCache = 
			Caffeine.newBuilder().maximumSize(10000)
			.build();

	public static Cache<String, Boolean>
			isMisspelledCache =
			Caffeine.newBuilder().maximumSize(10000)
					.build();

	public SpellChecker() throws StringSegmenterException, SpellCheckerException {
		CompiledCorpus nullCorpus = null;
		init_SpellChecker_CorpusObject(nullCorpus);
	}
	
	public SpellChecker(String corpusName) throws StringSegmenterException, SpellCheckerException {
		try {
			CompiledCorpus corpus = CompiledCorpusRegistry.getCorpus(corpusName);
			init_SpellChecker_CorpusObject(corpus);
		} catch (CompiledCorpusRegistryException e) {
			throw new SpellCheckerException(e);
		}
	}

	public SpellChecker(CompiledCorpus _corpus) throws SpellCheckerException {
		init_SpellChecker_CorpusObject(_corpus);
	}

	public SpellChecker(File corpusFile) throws StringSegmenterException, SpellCheckerException {
		try {
			CompiledCorpus corpus = RW_CompiledCorpus.read(corpusFile);
			init_SpellChecker_CorpusObject(corpus);
		} catch (CompiledCorpusException e) {
			throw new SpellCheckerException(e);
		}
	}

	void init_SpellChecker_CorpusObject(CompiledCorpus _corpus)
		throws SpellCheckerException  {
		try {
			editDistanceCalculator = EditDistanceCalculatorFactory.getEditDistanceCalculator();
			segmenter = new StringSegmenter_IUMorpheme();
			setDictionaryFromCorpus(_corpus);
		} catch (StringSegmenterException | FileNotFoundException | SpellCheckerException | ConfigException e) {
			throw new SpellCheckerException(e);
		}
	}

	public void setDictionaryFromCorpus() throws SpellCheckerException, ConfigException, FileNotFoundException {
		try {
			corpus = CompiledCorpusRegistry.getCorpus(null);
			__processCorpus();
		} catch (CompiledCorpusRegistryException e) {
			throw new SpellCheckerException(e);
		}
	}

	public void setDictionaryFromCorpus(CompiledCorpus _corpus) throws SpellCheckerException, ConfigException, FileNotFoundException {
		this.corpus = _corpus;
		__processCorpus();
	}

	public void setDictionaryFromCorpus(String _corpusName) throws SpellCheckerException, ConfigException, FileNotFoundException {
		try {
			corpus = CompiledCorpusRegistry.getCorpus(_corpusName);
			setDictionaryFromCorpus(corpus);
		} catch (CompiledCorpusRegistryException e) {
			throw new SpellCheckerException(e);
		}
	}

	public void setDictionaryFromCorpus(File compiledCorpusFile) throws SpellCheckerException {
		try {
			CompiledCorpus corpus = RW_CompiledCorpus.read(compiledCorpusFile);
			setDictionaryFromCorpus(corpus);
		} catch (Exception e) {
			throw new SpellCheckerException(
					"Could not create the compiled corpus from file: " + compiledCorpusFile.toString(), e);
		}

		return;
	}
	
	protected void __processCorpus() throws ConfigException, FileNotFoundException {
		if (corpus instanceof CompiledCorpus_InMemory) {
			this.allWords = ((CompiledCorpus_InMemory)corpus).decomposedWordsSuite;
		}
		// Ideally, these should be compiled along with allWords and ngramsStats during corpus compilation
		String dataPath = IUConfig.getIUDataPath();
		FileReader fr = new FileReader(dataPath+"/data/numericTermsCorpus.json");
		AnalyzeNumberExpressions numberExpressionsAnalysis = new Gson().fromJson(fr, AnalyzeNumberExpressions.class);
		this.allNormalizedNumericTerms = getNormalizedNumericTerms(numberExpressionsAnalysis);

		return;
	}
	

	/*
	 * Ideally those 2 values should have been compiled during the corpus compilation.
	 * But for now, they are compiled externally and stored in a special corpus compilation (json) file.
	 * (see AnalyseNumberExpressions.java in ca.inuktitutcomputing.utilbin)
	 */
	private Map<String, Long> getNgramsStatsOfNumericTerms(AnalyzeNumberExpressions numberExpressionsAnalysis) {
		return numberExpressionsAnalysis.getNgramStats();
	}


	private String getNormalizedNumericTerms(AnalyzeNumberExpressions numberExpressionsAnalysis) {
		return numberExpressionsAnalysis.getDecomposedNormalizedNumericTermsSuite();
	}


	public void setEditDistanceAlgorithm(EditDistanceCalculatorFactory.DistanceMethod name) throws ClassNotFoundException, EditDistanceCalculatorFactoryException {
		editDistanceCalculator = EditDistanceCalculatorFactory.getEditDistanceCalculator(name);
	}
	
	public void setVerbose(boolean value) {
		verbose = value;
	}
	
	public void addExplicitlyCorrectWord(String word) throws SpellCheckerException {
		try {
			explicitlyCorrectWords.addWordOccurence(word);
		} catch (CompiledCorpusException e) {
			throw new SpellCheckerException(e);
		}
		
		String[] numericTermParts = null;
		boolean wordIsNumericTerm = (numericTermParts=splitNumericExpression(word)) != null;
		if (wordIsNumericTerm) {
			explicitlyCorrect_Numeric.add(word);
		} else {
			explicitlyCorrect_NonNumeric.add(word);
		}
		
		// TODO-June2020: Is this still needed?
		if (wordIsNumericTerm && allNormalizedNumericTerms.indexOf(","+"0000"+numericTermParts[1]+",") < 0) {
			if (allNormalizedNumericTerms == null || allNormalizedNumericTerms.isEmpty()) {
				allNormalizedNumericTerms = "";
			}
			allNormalizedNumericTerms += ",0000"+numericTermParts[1]+",";
		} else {
			if (allWords == null || allWords.isEmpty()) {
				allWords = "";
			}
			allWords += ","+word+",";
		}
		__updateSequenceIDFForWord(word,wordIsNumericTerm);
		clearWordsWithNgramCache();
		removeFromMisspelledCache(word);
	}

	public void deleteExplicitlyCorrectWord(String word) throws SpellCheckerException {
		try {
			explicitlyCorrectWords.deleteWord(word);
		} catch (CompiledCorpusException e) {
			throw new SpellCheckerException(e);
		}
	}
	
	private void __updateSequenceIDFForWord(String word, boolean wordIsNumericTerm) {
		Set<String> seqSeenInWord = new HashSet<String>();
		try {
			for (int seqLen = 1; seqLen <= MAX_SEQ_LEN; seqLen++) {
				for (int  start=0; start <= word.length() - seqLen; start++) {
					String charSeq = word.substring(start, start+seqLen);
					seqSeenInWord.add(charSeq);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void saveToFile(File checkerFile) throws IOException {
		FileWriter saveFile = new FileWriter(checkerFile);
		Gson gson = new Gson();
		gson.toJson(this, saveFile);
		saveFile.flush();
		saveFile.close();
		//System.out.println("saved in "+checkerFile.getAbsolutePath());
	}

	public SpellChecker readFromFile(File checkerFile) throws FileNotFoundException, IOException {
		FileReader jsonFileReader = new FileReader(checkerFile);
		Gson gson = new Gson();
		SpellChecker checker = gson.fromJson(jsonFileReader, SpellChecker.class);
		jsonFileReader.close();
		this.MAX_SEQ_LEN = checker.getMaxSeqLen();
		this.MAX_CANDIDATES = checker.getMaxCandidates();
		this.allWords = checker.getAllWords();
		return this;
	}

	private String getAllWords() {
		return this.allWords;
	}

	private int getMaxCandidates() {
		return this.MAX_CANDIDATES;
	}

	private int getMaxSeqLen() {
		return this.MAX_SEQ_LEN;
	}

	public SpellingCorrection correctWord(String word) throws SpellCheckerException {
		return correctWord(word,-1);
	}

	public SpellingCorrection correctWord(String word, int maxCorrections) throws SpellCheckerException {
		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.spellchecker.SpellChecker.correctWord");

		long start = StopWatch.nowMSecs();

		SpellDebug.trace("SpellChecker.correctWord",
				"Invoked on word="+word,
				word, null);
		
		boolean wordIsSyllabic = Syllabics.allInuktitut(word);
		
		String wordInLatin = word;
		if (wordIsSyllabic) {
			wordInLatin = TransCoder.unicodeToRoman(word);
		}
		
		SpellingCorrection corr = new SpellingCorrection(word);
		corr.wasMispelled = isMispelled(wordInLatin);		
		tLogger.debug("wasMispelled= "+corr.wasMispelled);

		SpellDebug.trace("SpellChecker.correctWord",
				"corr.wasMispelled="+corr.wasMispelled,
				word, null);
		
		if (corr.wasMispelled) {
			// set ngramStats and suite of words for candidates according to type of word (normal word or numeric expression)
			String[] numericTermParts = splitNumericExpression(wordInLatin);
			boolean wordIsNumericTerm = numericTermParts != null;

			SpellDebug.trace("SpellChecker.correctWord",
				"wordIsNumericTerm="+wordIsNumericTerm,
				word, null);

			if (partialCorrectionEnabled) {
				SpellDebug.trace("SpellChecker.correctWord",
						"Computing longest correct head and tail of the word",
						word, null);
				computeCorrectPortions(wordInLatin, corr);
			}

			SpellDebug.trace("SpellChecker.correctWord",
				"Computing 1st pass candidates",
				word, null);

			Set<String> candidates = candidatesWithSimilarNgrams(wordInLatin, wordIsNumericTerm);

			SpellDebug.trace("SpellChecker.correctWord",
			"Number of 1st pass candidates="+(candidates.size()),
			word, null);

			SpellDebug.containsCorrection(
				"SpellChecker.correctWord",
				"First pass candidates",
				word, "nunavut", candidates);

			SpellDebug.trace("SpellChecker.correctWord",
				"Computing candidates similariy using "+editDistanceCalculator.getClass(),
				word, null);

			List<ScoredSpelling> scoredSpellings = computeCandidateSimilarities(wordInLatin, candidates);

			SpellDebug.containsCorrection(
					"SpellChecker.correctWord",
					"UNSORTED scored spellings",
					word, scoredSpellings);

			scoredSpellings = sortCandidatesBySimilarity(scoredSpellings);

			SpellDebug.containsCorrection(
				"SpellChecker.correctWord",
				"SORTED scored spellings",
				word, scoredSpellings);

			if (wordIsNumericTerm) {
				for (int ic=0; ic<scoredSpellings.size(); ic++) {
					ScoredSpelling scoredCandidate = scoredSpellings.get(ic);
					scoredCandidate.spelling = 
							scoredCandidate.spelling.replaceAll("\\d+-*",numericTermParts[0]);
				}
			}

			scoredSpellings = selectTopCandidates(maxCorrections, scoredSpellings);

			SpellDebug.containsCorrection(
				"SpellChecker.correctWord",
				"TOP PORTION of SORTED scored spellings",
				word, scoredSpellings);
			if (SpellDebug.traceIsActive("SpellChecker.correctWord", word)) {
				SpellDebug.trace("SpellChecker.correctWord",
						"TOP PORTION of SORTED scored spellings is:\n"+
							PrettyPrinter.print(scoredSpellings),
						word, null);
			}
			
	 		corr.setPossibleSpellings(scoredSpellings);
		}
		
		if (wordIsSyllabic) {
			transcodeCandidatesToSyllabic(corr);
		}

		long elapsed = StopWatch.elapsedMsecsSince(start);
		tLogger.trace("word="+word+" took "+elapsed+"msecs");
	
 		return corr;
	}

	private List<ScoredSpelling> selectTopCandidates(int maxCorrections, List<ScoredSpelling> sortedCandidates) throws SpellCheckerException {
		List<ScoredSpelling> topCandidates = sortedCandidates;
		if (maxCorrections != -1) {
			topCandidates = new ArrayList<ScoredSpelling>();
			Iterator<ScoredSpelling> iterCand = sortedCandidates.iterator();
			while (iterCand.hasNext() && topCandidates.size() < maxCorrections) {
				ScoredSpelling candidate = iterCand.next();
				// Make sure all the retained candidates are correctly spelled
				//
				try {
					if (!isMispelled(candidate.spelling)) {
						topCandidates.add(candidate);
					}
				} catch (SpellCheckerException e) {
					throw new SpellCheckerException(e);
				}
			}
		}

		return topCandidates;
	}

	protected void computeCorrectPortions(String badWordRoman, 
			SpellingCorrection corr) throws SpellCheckerException {
		computeCorrectLead(badWordRoman, corr);
		computeCorrectTail(badWordRoman, corr);
	}

	private void computeCorrectLead(String badWordRoman, 
			SpellingCorrection corr) throws SpellCheckerException {
		
		final int MAX_WORDS_TO_TRY = 5;
		
		String amongWords = getAllWordsToBeUsedForCandidates(badWordRoman);
		
		String longestCorrectLead = null;
		for (int endPos=badWordRoman.length()-1; endPos > 3; endPos--) {
			//
			// Loop through all the leading strings L of the bad word, starting 
			// the complete bad word and removing one tailing character at a time, 
			// until we find a L that satifies the following conditions:
			// 
			// - There is a correctly spelled word W that also starts with L
			// - L does not span across phonemes. In other words,
			//   the last character L corresponds to the end of a 
			//   morpheme in W.
			//
			String lead = badWordRoman.substring(0, endPos-1);
			Iterator<String> iterWords = wordsContainingNgram("^"+lead, amongWords);
			boolean wordWasFoundForLead = false;
			
			int wordCount = 0;
			while (iterWords.hasNext()) {
				String aWord = iterWords.next();
				wordCount++;
				if (wordCount > 5) {
					break;
				}
				if (leadRespectsMorphemeBoundaries(lead, aWord)) {
					// Found a word with the right characteristics
					wordWasFoundForLead = true;					
					longestCorrectLead = lead;
//					System.out.println("** SpellChecker.computeCorrectLead: setting longestCorrectLead="+
//							longestCorrectLead);
					break;
				}
			}
			
			if (wordWasFoundForLead) {
//				System.out.println("** SpellChecker.computeCorrectLead: setting longestCorrectLead="+
//						longestCorrectLead);
				break;
			}			
		}
		corr.setCorrectLead(longestCorrectLead);
	}
	
	protected boolean leadRespectsMorphemeBoundaries(String lead, String word) 
			throws SpellCheckerException {
//		System.out.println("** SpellChecker.leadRespectsMorphemeBoundaries: lead="+
//				lead+", word=="+word);
		
		Boolean answer = null;
		
		Decomposition[] decomps = null;
		try {
			 decomps = 
				new MorphologicalAnalyzer()
						.setTimeout(MAX_DECOMP_MSECS)
						.activateTimeout()
						.decomposeWord(word);
		} catch(TimeoutException e) {
			answer = false;
		} catch(MorphologicalAnalyzerException | LinguisticDataException e) {
			throw new SpellCheckerException(e);
		}
		
		if (decomps != null) {
			for (Decomposition aDecomp: decomps) {
				List<String> morphemes = aDecomp.morphemeSurfaceForms();
//				System.out.println("** SpellChecker.leadRespectsMorphemeBoundaries:"+
//						" looking at morphemes='"+String.join("', '", morphemes)+"'");
				
				String morphLead = "";
				for (String morph: morphemes) {
					morphLead += morph;
					if (morphLead.equals(lead)) {
						answer = true;
						break;
					}
				}
				if (answer != null) { break; }
//				System.out.println("** SpellChecker.leadRespectsMorphemeBoundaries:"+
//						"    after looking at morphemes, answer="+answer);
			}
		}
		
		if (answer == null) { answer = false; }
		
//		System.out.println("** SpellChecker.leadRespectsMorphemeBoundaries: lead="+
//				lead+", word="+word+", returns answer="+answer);
		
		return answer.booleanValue();
	}
	

	private void computeCorrectTail(String badWordRoman, 
			SpellingCorrection corr) throws SpellCheckerException {
		
		final int MAX_WORDS_TO_TRY = 5;
		
		String amongWords = getAllWordsToBeUsedForCandidates(badWordRoman);
		
		String longestCorrectTail = null;
		for (int startPos=0; startPos < badWordRoman.length()-2; startPos++) {
			//
			// Loop through all the tailing strings L of the bad word, starting 
			// the complete bad word and removing one leading character at a time, 
			// until we find a L that satifies the following conditions:
			// 
			// - There is a correctly spelled word W that also starts with L
			// - L does not span across phonemes. In other words,
			//   the last character L corresponds to the end of a 
			//   morpheme in W.
			//
			String tail = badWordRoman.substring(startPos);
			Iterator<String> iterWords = wordsContainingNgram(tail+"$", amongWords);
			boolean wordWasFoundForTail = false;
			int wordCount = 0;
			while (iterWords.hasNext()) {
				String aWord = iterWords.next();
				wordCount++;
				if (wordCount > 5) {
					break;
				}
				if (tailRespectsMorphemeBoundaries(tail, aWord)) {
					// Found a word with the right characteristics
					wordWasFoundForTail = true;					
					longestCorrectTail = tail;
					break;
				}
			}
			
			if (wordWasFoundForTail) {
				break;
			}			
		}
		corr.setCorrectTail(longestCorrectTail);
	}
	
	public boolean tailRespectsMorphemeBoundaries(String tail, String word) 
			throws SpellCheckerException {
		
		Boolean answer = null;
		
		Decomposition[] decomps = null;
		try {
			 decomps = 
				new MorphologicalAnalyzer()
						.setTimeout(MAX_DECOMP_MSECS)
						.activateTimeout()
						.decomposeWord(word);
		} catch(TimeoutException e) {
			answer = false;
		} catch(MorphologicalAnalyzerException | LinguisticDataException e) {
			throw new SpellCheckerException(e);
		}
		
//		if (decomps == null) { System.out.println("** SpellChecker.tailRespectsMorphemeBoundaries:"+
//				" decomps is NULL"); }

//		if (decomps.length == 0) { System.out.println("** SpellChecker.tailRespectsMorphemeBoundaries:"+
//				" decomps is Empty"); }
		
		if (decomps != null) {
			for (Decomposition aDecomp: decomps) {
				List<String> morphemes = aDecomp.morphemeSurfaceForms();
//				System.out.println("** SpellChecker.tailRespectsMorphemeBoundaries:"+
//						" looking at morphemes='"+String.join("', '", morphemes)+"'");
				
				String morphTail = "";
				for (int ii=morphemes.size()-1; ii > 0; ii--) {
					String morph = morphemes.get(ii);
					morphTail = morph + morphTail;
					if (morphTail.equals(tail)) {
						answer = true;
						break;
					}
				}
				if (answer != null) { break; }
//				System.out.println("** SpellChecker.tailRespectsMorphemeBoundaries:"+
//						"    after looking at morphemes, answer="+answer);
				
			}
		}
		
		if (answer == null) { answer = false; }
		
//		System.out.println("** SpellChecker.tailRespectsMorphemeBoundaries: tail="+
//				tail+", word="+word+", returns answer="+answer);
		
		return answer.booleanValue();
	}
		
	
	/**
	 * Transcode a list of scored candidate spellings to 
	 * syllabic.
	 */
	private void transcodeCandidatesToSyllabic(SpellingCorrection corr) {
		for (int ic=0; ic < corr.scoredCandidates.size(); ic++) {
			ScoredSpelling candidate = corr.scoredCandidates.get(ic);
			candidate.spelling = TransCoder.romanToUnicode(candidate.spelling);
		}
		
		return;
	}


	/*
	 * A term is considered ok if:
	 *   - it is recorded as successfully decomposed by the IMA during the compilation of the Hansard corpus, or
	 *   - it consists of digits only, or
	 *   - it consists of only 1 syllabic character (latin equivalent of)
	 *   - it is not recorded as not decomposed by the IMA during the compilation of the Hansard corpus, or
	 *   - it is a punctuation mark
	 *   
	 * A word is considered mispelled if:
	 *   - it is recorded as UNsuccessfully decomposed by the IMA during the compilation of the Nunavut corpus, or
	 *   - it cannot be decomposed by the IMA (if never encountered in the Hansard corpus)
	 */
	protected Boolean isMispelled(String word) throws SpellCheckerException {
		Logger logger = Logger.getLogger("SpellChecker.isMispelled");
		logger.debug("word: "+word);

		Boolean wordIsMispelled = uncacheIsMisspelled(word);
		if (wordIsMispelled == null) {

			if (isExplicitlyCorrect(word)) {
				logger.debug("word is was explicity tagged as being correct");
				wordIsMispelled = false;
			}

			if (wordIsMispelled == null && corpus != null) {
				try {
					WordInfo wInfo = corpus.info4word(word);
					if (wInfo != null && wInfo.totalDecompositions > 0) {
						wordIsMispelled = false;
						logger.debug("Corpus contains some decompositions for this word");
					}
				} catch (CompiledCorpusException e) {
					throw new SpellCheckerException(e);
				}
			}

			if (wordIsMispelled == null && word.matches("^[0-9]+$")) {
				logger.debug("word is all digits");
				wordIsMispelled = false;
			}

			if (wordIsMispelled == null && latinSingleInuktitutCharacters.contains(word)) {
				logger.debug("single inuktitut character");
				wordIsMispelled = false;
			}

			if (wordIsMispelled == null && wordContainsMoreThanTwoConsecutiveConsonants(word)) {
				logger.debug("more than 2 consecutive consonants in the word");
				wordIsMispelled = true;
			}

			String[] numericTermParts = null;
			if (wordIsMispelled == null && (numericTermParts = splitNumericExpression(word)) != null) {
				logger.debug("numeric expression: " + word + " (" + numericTermParts[1] + ")");
				boolean pseudoWordWithSuffixAnalysesWithSuccess = assessEndingWithIMA(numericTermParts[1]);
				wordIsMispelled = !pseudoWordWithSuffixAnalysesWithSuccess;
				logger.debug("numeric expression - wordIsMispelled: " + wordIsMispelled);
			}

			if (wordIsMispelled == null && wordIsPunctuation(word)) {
				logger.debug("word is punctuation");
				wordIsMispelled = false;
			}

			if (wordIsMispelled == null) {
				try {
					String[] segments = segmenter.segment(word);
					logger.debug("word submitted to IMA: " + word);
					if (segments == null || segments.length == 0) {
						wordIsMispelled = true;
					}
				} catch (TimeoutException e) {
					wordIsMispelled = true;
				} catch (StringSegmenterException | LinguisticDataException e) {
					throw new SpellCheckerException(e);
				}
				logger.debug("word submitted to IMA - mispelled: " + wordIsMispelled);
			}

			if (wordIsMispelled == null) {
				wordIsMispelled = false;
			}

			cacheIsMisspelled(word, wordIsMispelled);
		}
		
		return wordIsMispelled;
	}

	private boolean isExplicitlyCorrect(String word) throws SpellCheckerException {
		boolean answer;
		try {
			answer = explicitlyCorrectWords.containsWord(word);
		} catch (CompiledCorpusException e) {
			throw new SpellCheckerException(e);
		}
		return answer;
	}
	
	protected boolean wordIsPunctuation(String word) {
		Pattern p = Pattern.compile("(\\p{Punct}|[\u2013\u2212])+");
		Matcher mp = p.matcher(word);
		return mp.matches();
	}


	/**
	 * Check if a word is a numeric expression of the form
	 * 
	 *    DDDDD suffix1 suffix2 etc...
	 *    
	 * If so, split it into parts:
	 * 
	 *    - Numeric part
	 *    - Suffixes
	 *    
	 * Otherwise, return null
	 * 
	 * @param word
	 * @return
	 */
	protected String[] splitNumericExpression(String word) {
		NumericExpression numericExpression = NumericExpression.tokenIsNumberWithSuffix(word);
		if (numericExpression != null)
			return new String[] { numericExpression.numericFrontPart+numericExpression.separator, numericExpression.morphemicEndPart };
		else
			return null;
	}

	protected boolean wordContainsMoreThanTwoConsecutiveConsonants(String word) {
		Logger logger = Logger.getLogger("SpellChecker.wordContainsMoreThanTwoConsecutiveConsonants");
		boolean result = false;
		String wordInSimplifiedOrthography = Orthography.simplifiedOrthography(word, false);
		logger.debug("wordInSimplifiedOrthography= "+wordInSimplifiedOrthography+" ("+word+")");
		Pattern p = Pattern.compile("[gjklmnprstvN]{3,}");
		Matcher mp = p.matcher(wordInSimplifiedOrthography);
		if (mp.find()) {
			logger.debug("match= "+mp.group());
			result = true;
		} 
		
		return result;
	}


	private List<ScoredSpelling> sortCandidatesBySimilarity(List<ScoredSpelling> scoredSpellings) {
		
		Iterator<ScoredSpelling> iteratorCand = scoredSpellings.iterator();
		Collections.sort(scoredSpellings, (ScoredSpelling p1, ScoredSpelling p2) -> {
			return p1.score.compareTo(p2.score);
		});
		
		return scoredSpellings;
	}

	protected List<ScoredSpelling> computeCandidateSimilarities(String badWord, Set<String> candidates) throws SpellCheckerException {
		SpellDebug.trace("SpellChecker.computeCandidateSimilarities",
				"Invoked on word="+badWord+
					", editDistanceCalculator=\n"+editDistanceCalculator.getClass()+
					PrettyPrinter.print(editDistanceCalculator),
				badWord, null);
		List<ScoredSpelling> scoredCandidates = new ArrayList<ScoredSpelling>();
		
		Iterator<String> iterator = candidates.iterator();
		while (iterator.hasNext()) {
			String candidate = iterator.next();
			double similarity = computeCandidateSimilarity(badWord,candidate);
			scoredCandidates.add(new ScoredSpelling(candidate, new Double(similarity)));
		}
		
		return scoredCandidates;
	}

	private double computeCandidateSimilarity(String badWord, String candidate) throws SpellCheckerException {

		SpellDebug.trace("SpellChecker.computeCandidateSimilarity",
			"Invoked, editDistanceCalculator="+editDistanceCalculator.getClass(),
			badWord, candidate);
		
		double distance;
		try {
			distance = editDistanceCalculator.distance(badWord, candidate);
		} catch (EditDistanceCalculatorException e) {
			throw new SpellCheckerException(e);
		}

		SpellDebug.trace("SpellChecker.computeCandidateSimilarity",
				"returning distance="+distance,
				badWord, candidate);

		return distance;
	}

	public Set<String> candidatesWithSimilarNgrams(String badWord,
	   boolean wordIsNumericTerm) throws SpellCheckerException {

		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.spellchecker.SpellChecker.candidatesWithSimilarNgrams");
		tLogger.trace("Starting");

		long start = StopWatch.nowMSecs();

		// 1. compile ngrams of badWord
		// 2. compile IDF of each ngram
		// 3. add words for top IDFs to the set of candidates until the number 
		//    of candidates exceeds the maximum
		// 4. compute scores for each word and order words highest score first
		//    compute edit distance, etc.
		
		String allWordsForCandidates = 
			getAllWordsToBeUsedForCandidates(wordIsNumericTerm);

		tLogger.trace("Computing the most significant NGrams for the mis-spelled words");
		
		NgramCompiler ngramCompiler = new NgramCompiler();
		ngramCompiler.setMin(MIN_NGRAM_LEN).setMax(MAX_NGRAM_LEN);
		ngramCompiler.includeExtremities(true);
		
		// Step 1: compile ngrams for the bad word
		//
		String[] badWordNgrams = ngrams4word(badWord, ngramCompiler);
		
		// Step 2: compile Inverse Document Frequency (IDF) of each 
		// ngram and sort them from highest to lowest IDF 
		//
		//    IDF(word) = 1 / (#words with this ngram + 1)
		//
		Pair<String,Double>[] ngramFreqs =
				computeNgramFrequencies(badWordNgrams);

		SpellDebug.containsNgramsToTrace(
		"SpellChecker.firstPassCandidates_TFIDF",
		"Most significant ngrams of the misspelled word",
			(String)null, (String)null, ngramFreqs);

		SpellDebug.traceNgrams("SpellChecker.firstPassCandidates_TFIDF",
			"Most significant ngrams of the misspelled word",
			(String)null, (String)null, ngramFreqs);

		
		// Step 3: Find words that most closely match the ngrams of the bad 
		// (up to a maximum of MAX_CANDIDATES
		//

		tLogger.trace("Finding candidates whose NGrams best matches those of the misspelled word");

		// TODO-June2020: Use this approach instead, in order to 
		//   determine the initial candidates...
		//
//			Set<String> explicitCandidates = 
//					candidatesWithBestNGramsMatch(ngramsIDF, 
//							explicitlyCorrectWords);
//			Set<String> nonExplicitCandidates = 
//					candidatesWithBestNGramsMatch(ngramsIDF, 
//							explicitlyCorrectWords);
//			Set<String> candidates = new HashSet<String>();
//			candidates.addAll(explicitCandidates);
//			candidates.addAll(nonExplicitCandidates);

		Set<String> candidates =
				candidatesWithBestNGramsMatch(ngramFreqs,
						allWordsForCandidates);

		tLogger.trace("Scoring candidates in terms of similarity to the mis-spelled word");
		
		// Step 4: compute scores for each word and sort them from highest to
		//   lowest score.
		//
		Pair<String,Double>[] arrScoreValues =
				scoreAndSortCandidates(wordIsNumericTerm, candidates, 
					badWordNgrams, ngramFreqs, ngramCompiler);

		Set<String> allCandidates = new HashSet<String>();
		for (int i=0; i<arrScoreValues.length; i++) {
			allCandidates.add(arrScoreValues[i].getFirst());
		}
		
		SpellDebug.containsCorrection("SpellChecker.firstPassCandidates_TFIDF", 
				"Returned list allCandidates", badWord,
				allCandidates);

		tLogger.trace("Returning candidates list of size="+allCandidates.size());

		long elapsed = StopWatch.elapsedMsecsSince(start);
		tLogger.trace("word="+badWord+" took "+elapsed+"msecs");

		return allCandidates;
	}

	private Pair<String,Double>[] computeNgramFrequencies(String[] ngrams) throws SpellCheckerException {
		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.spellchecker.SpellChecker.computeNgramIDFs");

		long start = StopWatch.nowMSecs();

		Pair<String,Double> ngramFreqs[] = new Pair[ngrams.length];

		for (int i=0; i<ngrams.length; i++) {
			Long ngramFreq = ngramFrequency(ngrams[i]);
			tLogger.trace("for ngram="+ngrams[i]+", ngramFreq="+ngramFreq);
			ngramFreqs[i] = new Pair<String,Double>(ngrams[i],1.0*ngramFreq);
		}
		IDFComparator dcomparator = new IDFComparator();
		Arrays.sort(ngramFreqs,dcomparator);

//		ngramFreqs = removeNgramsWithNoOccurences(ngramFreqs);

		if (tLogger.isTraceEnabled()) {
			tLogger.trace("returning idf="+PrettyPrinter.print(ngramFreqs));
			tLogger.trace("completed in "+StopWatch.elapsedMsecsSince(start)+"msecs");
		}
		return ngramFreqs;
	}

//	private Pair<String, Double>[] removeNgramsWithNoOccurences(Pair<String, Double>[] ngramFreqs) {
//		List<Pair<String, Double>> retainedNgrams = new ArrayList<Pair<String, Double>>();
//		for (Pair<String, Double> aNgramFreq: ngramFreqs) {
//			if (aNgramFreq.getSecond() > 0) {
//				retainedNgrams.add(aNgramFreq);
//			}
//		}
//
//
//		Pair<String, Double>[] retainedArr =
//			retainedNgrams.toArray(new Pair[0]);
//
//		return retainedArr;
//	}

	public long ngramFrequency(String ngram) throws SpellCheckerException {
		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.spellchecker.SpellChecker.ngramFrequency");
		long freq = 0;
		try {
			freq = corpus.totalWordsWithCharNgram(
					ngram, CompiledCorpus.SearchOption.EXCL_MISSPELLED);
		} catch (CompiledCorpusException e) {
			throw new SpellCheckerException(e);
		}

		tLogger.trace("for ngram="+ngram+"; returning freq="+freq);
		return freq;
	}

	private Set<String> candidatesWithBestNGramsMatch(
			Pair<String, Double>[] idf,
			String amongWords) throws SpellCheckerException {

		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.spellchecker.SpellChecker.candidatesWithBestNGramsMatch");

		long start = StopWatch.nowMSecs();
		tLogger.trace("Started");

		Set<String> candidates = new HashSet<String>();
		for (int i=0; i<idf.length; i++) {

			String ngram = idf[i].getFirst();
			Double ngramIDF = idf[i].getSecond();

			Iterator<String> iterCandsWithNgram =
					wordsContainingNgram(ngram, amongWords,
						CompiledCorpus.SearchOption.EXCL_MISSPELLED);

			tLogger.trace("adding candidates that contain ngram=" + ngram + " (ngramIDF=" + ngramIDF + ")");
			Set<String> candidatesWithNgram =
					collectCandidatesWithNgram(iterCandsWithNgram);

			candidates.addAll(candidatesWithNgram);

			SpellDebug.containsCorrection(
					"SpellChecker.candidatesWithBestNGramsMatch",
					"After adding words containing ngram=" + ngram,
					null, candidates);

			tLogger.trace("DONE adding candidates that contain ngram=" + ngram + "; total added = " + candidatesWithNgram.size());

			if (candidates.size() > MAX_CANDIDATES) {
				break;
			}
		}

		long elapsed = 0;
		elapsed = StopWatch.elapsedMsecsSince(start);

		tLogger.trace("Completed in "+elapsed+"msecs");
		
		return candidates;

	}

	private Set<String> collectCandidatesWithNgram(
		Iterator<String> iterCandsWithNgram) throws SpellCheckerException {
		Set<String> candidatesWithNgram = new HashSet<String>();
//		while (iterCandsWithNgram.hasNext()) {
//			String aCandidate = iterCandsWithNgram.next();
//			if (! isMispelled(aCandidate)) {
//				candidatesWithNgram.add(aCandidate);
//			}
//		}

		iterCandsWithNgram.forEachRemaining(candidatesWithNgram::add);

		return candidatesWithNgram;
	}

	private Set<String> candidatesWithBestNGramsMatch(
			Pair<String, Double>[] ngramsWithIDF, 
			CompiledCorpus inCorpus) throws SpellCheckerException {
		Set<String> candidates = new HashSet<String>();		
		for (int i=0; i<ngramsWithIDF.length; i++) {
			Set<String> candidatesWithNgram = new HashSet<String>();
			try {
				Iterator<String> iterCandidates = inCorpus.wordsContainingNgram(ngramsWithIDF[i].getFirst());
				while (iterCandidates.hasNext()) {
					candidatesWithNgram.add(iterCandidates.next());
				}
			} catch (CompiledCorpusException e) {
				throw new SpellCheckerException(e);
			}
			
			SpellDebug.containsCorrection("SpellChecker.firstPassCandidates_TFIDF", 
					"Words that contain ngram="+ngramsWithIDF[i].getFirst(), 
					"maliklugu","maligluglu", 
					candidatesWithNgram);
			
			candidates.addAll(candidatesWithNgram);	
			
			if (candidates.size() > MAX_CANDIDATES) {
				break;
			}
		}
		
		return candidates;
	}

	private Pair<String, Double>[] scoreAndSortCandidates(
			boolean onlyNumericTerms, Set<String> initialCands, 
			String[] badWordNGrams, Pair<String, Double>[] badWordNgramFreqs,
			NgramCompiler ngramCompiler) {

		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.spellchecker.SpellChecker.scoreAndSortCandidates");
		tLogger.trace("invoked");

		Set<String> candidates = initialCands;
		if (onlyNumericTerms) {
			candidates = keepOnlyNumericTerms(initialCands);
		}
		
		Map<String,Double> badWordNgramInvFreqHash = new HashMap<String,Double>();
		for (Pair<String,Double> ngramInfo: badWordNgramFreqs) {
			badWordNgramInvFreqHash.put(
				ngramInfo.getFirst(),
				inverseFrequency(ngramInfo.getSecond()));
		}
		
		Set<String> ngramsOfBadWord_Set = new HashSet<String>();
		for (String ngram: badWordNGrams) {
			ngramsOfBadWord_Set.add(ngram);
		}
		
		List<Pair<String,Double>> scoreValues = new ArrayList<Pair<String,Double>>();
		Iterator<String> it = candidates.iterator();
		while (it.hasNext()) {
			String candidate = it.next();
			Set<String> ngramsOfCandidate = ngramCompiler.compile(candidate);
			Set<String> all = new HashSet<String>();
			for (String ngram: badWordNGrams) {
				all.add(ngram);
			}
			all.addAll(ngramsOfCandidate);
			double totalScore = 0;
			Iterator<String> itall = all.iterator();
			while (itall.hasNext()) {
				String el = itall.next();
				if (ngramsOfBadWord_Set.contains(el) && ngramsOfCandidate.contains(el)) {
					Double score = badWordNgramInvFreqHash.get(el);
					if (score != null) {
						totalScore += score;
					}
				}
			}
			scoreValues.add(new Pair<String,Double>(candidate,totalScore));
		}
		WordScoreComparator comparator = new WordScoreComparator();
		Pair<String,Double>[] arrScoreValues = scoreValues.toArray(new Pair[] {});
		Arrays.sort(arrScoreValues, comparator);

		tLogger.trace("finished");

		return arrScoreValues;
	}
	

	private Set<String> keepOnlyNumericTerms(Set<String> initialCands) {
		Set<String> filteredCands = new HashSet<String>();
		for (String aCand: initialCands) {
			String[] numericParts = splitNumericExpression(aCand);
			if (numericParts != null) {
				filteredCands.add(aCand);
			}
		}
		return filteredCands;
	}
	
	private String[] ngrams4word(String word, 
			NgramCompiler ngramCompiler) {
		Set<String>ngramsOfBadWord = ngramCompiler.compile(word);
		String[] ngrams = ngramsOfBadWord.toArray(new String[] {});
		
		if (SpellDebug.traceIsActive("SpellChecker.ngrams4word", word)) {
			SpellDebug.trace("SpellChecker.ngrams4word", 
					"word ngrams=['"+String.join("', '", ngrams)+"']", 
					word, null);
		}
		
		return ngrams;
	}

	public class IDFComparator implements Comparator<Pair<String,Double>> {
	    @Override
	    public int compare(Pair<String,Double> a, Pair<String,Double> b) {
	    	if (a.getSecond() > b.getSecond())
	    		return 1;
	    	else if (a.getSecond() < b.getSecond())
	    		return -1;
	    	else 
	    		return 0;
	    }
	}

	public class WordScoreComparator implements Comparator<Pair<String,Double>> {
	    @Override
	    public int compare(Pair<String,Double> a, Pair<String,Double> b) {
	    	if (a.getSecond() > b.getSecond())
	    		return -1;
	    	else if (a.getSecond() < b.getSecond())
				return 1;
	    	else 
	    		return a.getFirst().compareToIgnoreCase(b.getFirst());
	    }
	}

	protected Iterator<String> wordsContainingNgram(
		String seq, String amongWords) throws SpellCheckerException {
		return wordsContainingNgram(seq, amongWords, new CompiledCorpus.SearchOption[0]);
	}

	protected Iterator<String> wordsContainingNgram(String seq,
		String amongWords, CompiledCorpus.SearchOption... options) throws SpellCheckerException {
		Logger logger = Logger.getLogger("ca.pirurvik.iutools.spellchecker.SpellChecker.wordsContainingSequ");

		long start = StopWatch.nowMSecs();

		Set<String> wordsWithSeq = new HashSet<String>();

		// TODO-Sept2020: Get rid of this 'if' once we don't use
		//  CompiledCorpus_InMemory anymore
		//
		Iterator<String> wordsIter = null;
		if (!(corpus instanceof CompiledCorpus_InMemory)) {
			try {

				Iterator<String> wordsIter1 =
					corpus.wordsContainingNgram(
						seq, options);

				Iterator<String> wordsIter2 =
					explicitlyCorrectWords.wordsContainingNgram(
						seq, CompiledCorpus.SearchOption.EXCL_MISSPELLED);

				wordsIter = new IteratorChain<String>(wordsIter1, wordsIter2);
			} catch (CompiledCorpusException e) {
				throw new SpellCheckerException(e);
			}
		} else {
			wordsWithSeq = uncacheWordsWithNgram(seq);
			if (wordsWithSeq == null) {
				Pattern p;
				if (seq.charAt(0) == '^' && seq.charAt(seq.length() - 1) == '$') {
					seq = seq.substring(1, seq.length() - 1);
					p = Pattern.compile(",(" + seq + "),");
				} else if (seq.charAt(0) == '^') {
					seq = seq.substring(1);
					p = Pattern.compile(",(" + seq + "[^,]*),");
				} else if (seq.charAt(seq.length() - 1) == '$') {
					logger.debug("seq= " + seq);
					seq = seq.substring(0, seq.length() - 1);
					logger.debug(">>> seq= " + seq);
					p = Pattern.compile(",([^,]*" + seq + "),");
				} else
					p = Pattern.compile(",([^,]*" + seq + "[^,]*),");

				Matcher m = p.matcher(amongWords); //p.matcher(allWords)
				wordsWithSeq = new HashSet<String>();
				while (m.find()) {
					wordsWithSeq.add(m.group(1));
				}
				cacheWordsWithNgram(seq, wordsWithSeq);
			}
			wordsIter = wordsWithSeq.iterator();
		}

		long elapsed = StopWatch.elapsedMsecsSince(start);
		logger.trace("seq="+seq+" took "+elapsed+"msecs");
		return wordsIter;
	}

	public List<SpellingCorrection> correctText(String text) throws SpellCheckerException {
		return correctText(text, null);
	}

	public List<SpellingCorrection> correctText(String text, Integer nCorrections) throws SpellCheckerException {
		Logger tLogger = Logger.getLogger("SpellChecker.correctText");
		if (nCorrections == null) nCorrections = DEFAULT_CORRECTIONS;
		List<SpellingCorrection> corrections = new ArrayList<SpellingCorrection>();
		
		IUTokenizer iutokenizer = new IUTokenizer();
		iutokenizer.tokenize(text);
		List<Pair<String,Boolean>> tokens = iutokenizer.getAllTokens();
		
		if (tLogger.isTraceEnabled()) tLogger.trace("tokens= "+PrettyPrinter.print(tokens));
		
		for (Pair<String,Boolean> aToken: tokens) {
			String tokString = aToken.getFirst();
			Boolean isDelimiter = !aToken.getSecond(); // IUTokenizer returns TRUE for words and FALSE for non-words.
			SpellingCorrection correction = null;
			if (isDelimiter) {
				correction = new SpellingCorrection(tokString);
			} else {
				correction = this.correctWord(tokString, nCorrections);
			}
			corrections.add(correction);
			
		}
		
		return corrections;
	}
	
	protected boolean assessEndingWithIMA(String ending) {
		Logger logger = Logger.getLogger("SpellChecker.assessEndingWithIMA");
		boolean accepted = false;
		MorphologicalAnalyzer morphAnalyzer = segmenter.getAnalyzer();
		for (int i=0; i<makeUpWords.length; i++) {
			accepted = false;
			String term = makeUpWords[i]+ending;
			logger.debug("term= "+term);
			Decomposition[] decs = null;
			try {
				decs = morphAnalyzer.decomposeWord(term);
			} catch (TimeoutException | MorphologicalAnalyzerException e) {
			}
			logger.debug("decs: "+(decs==null?"null":decs.length));
			if (decs!=null && decs.length!=0) {
				accepted = true;
				break;
			}
		}
		
		return accepted;
	}
	
	
//	Map<String,Long> getNgramStatsToBeUsedForCandidates(boolean wordIsNumericTerm) {
//		return wordIsNumericTerm? this.ngramStatsOfNumericTerms : this.ngramStats;
//	}

	String getAllWordsToBeUsedForCandidates(boolean wordIsNumericTerm) {
		return wordIsNumericTerm? this.allNormalizedNumericTerms : this.allWords;
	}
	
	String getAllWordsToBeUsedForCandidates(String word) {
		boolean isNumericTerm = (null != splitNumericExpression(word));
		return getAllWordsToBeUsedForCandidates(isNumericTerm);
	}
	
	private void cacheWordsWithNgram(String ngram, Set<String> words) {
		wordsWithNgramCache.put(ngram, words);
	}

	private Set<String> uncacheWordsWithNgram(String ngram) {
		Set<String> words = wordsWithNgramCache.getIfPresent(ngram);
		return words;
	}
	
	private void clearWordsWithNgramCache() {
		wordsWithNgramCache = 
				Caffeine.newBuilder().maximumSize(10000)
				.build();
	}

	private void cacheIsMisspelled(String word, Boolean misspelled) {
		isMisspelledCache.put(word, misspelled);
	}

	private Boolean uncacheIsMisspelled(String word) {
		Boolean misspelled = isMisspelledCache.getIfPresent(word);
		return misspelled;
	}

	private void clearIsMisspelledCache() {
		isMisspelledCache =
				Caffeine.newBuilder().maximumSize(10000)
						.build();
	}

	private void removeFromMisspelledCache(String word) {
		isMisspelledCache.invalidate(word);
	}

	protected double inverseFrequency(double freq) {
		double iFreq = 1.0 / (freq + 1);
		return iFreq;
	}
}
