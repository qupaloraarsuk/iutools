package ca.pirurvik.iutools.morphrelatives;

import java.util.*;
import java.util.regex.Pattern;

import ca.inuktitutcomputing.script.TransCoder;
import ca.nrc.datastructure.trie.StringSegmenter_IUMorpheme;
import ca.pirurvik.iutools.corpus.WordInfo;
import ca.pirurvik.iutools.corpus.CompiledCorpus;
import ca.pirurvik.iutools.corpus.CompiledCorpusException;
import ca.pirurvik.iutools.corpus.CompiledCorpusRegistry;
import ca.pirurvik.iutools.corpus.CompiledCorpusRegistryException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;

/**
 * Given an input word, this class finds a list of "good" Morphological 
 * Relatives. The relatives are chosen such that they:
 * 
 * - are morphologically close to the input word
 * - have a reasonably high frequency in a given corpus
 * 
 * @author desilets
 *
 */
public class MorphRelativesFinder {
	
	public CompiledCorpus compiledCorpus;
	public int maxRelatives = 5;
	protected boolean verbose = true;

	public MorphRelativesFinder() throws MorphRelativesFinderException {
		CompiledCorpus corpus = null;
		try {
			corpus = CompiledCorpusRegistry.getCorpusWithName_ES();
		} catch (CompiledCorpusRegistryException e) {
			throw new MorphRelativesFinderException(e);
		}
		init_MorphRelativeFinder(corpus);
		return;
	}

	public void init_MorphRelativeFinder(CompiledCorpus corpus) throws MorphRelativesFinderException {
		compiledCorpus = corpus;
		compiledCorpus.setSegmenterClassName(StringSegmenter_IUMorpheme.class);

		return;
	}

	public MorphRelativesFinder(CompiledCorpus corpus) throws MorphRelativesFinderException {
		init_MorphRelativeFinder(corpus);
	}

	/**
	 * 
	 * @param word String - an inuktitut word
	 * @return String[] An array of the most frequent inuktitut words related to the input word
	 * @throws MorphRelativesFinderException 
	 * @throws Exception
	 */
	public MorphologicalRelative[] findRelatives(String word) throws MorphRelativesFinderException  {
    	Logger logger = Logger.getLogger("ca.pirurvik.iutools.morphrelatives.MorphRelativesFinder.getRelatives");
		logger.debug("word: "+word);
		
		String[] segments;
		
		try {
			segments = this.compiledCorpus.decomposeWord(word);
		} catch (Exception e) {
			segments = null;
		}
		
		Set<MorphologicalRelative> relatives = new HashSet<MorphologicalRelative>();

		String[] wordMorphemes = null;
		if (segments != null) {
			wordMorphemes = segments.clone();
		}
		
		List<MorphologicalRelative> bestNeighbors = new ArrayList<MorphologicalRelative>();
		
		if (segments != null) {
			String[] workingSegments = segments.clone();
			Set<MorphologicalRelative> candidateNeighbors = new HashSet<MorphologicalRelative>();
			while (true) {
				boolean keepGoing =
					collectMorphologicalNeighbors(word, wordMorphemes, workingSegments, 
						candidateNeighbors);
				
				if (keepGoing) {
					workingSegments = 
						Arrays.copyOfRange(workingSegments, 0, 
							workingSegments.length - 1);
				} else {
					break;
				}
			}
			try {
				bestNeighbors = bestRelatives(candidateNeighbors);
				
				traceRelatives(logger, bestNeighbors, 
					"bestNeighbors=");
			} catch (Exception e) {
				throw new MorphRelativesFinderException(e);
			} 	
		}
		bestNeighbors = possiblyConvertToSyllabic(word, bestNeighbors);
		
		traceRelatives(logger, bestNeighbors, "Returning bestNeighbors=");
		
		return bestNeighbors.toArray(new MorphologicalRelative[0]);
	}
	
	private boolean collectMorphologicalNeighbors(
		String origWord, String[] origWordMorphemes, 
		String[] currentMorphemes, Set<MorphologicalRelative> collectedSoFar) 
		throws MorphRelativesFinderException {
				
		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.MorphRelativesFinder.collectMorphologicalNeighbors");
		
		if (tLogger.isTraceEnabled()) {
			traceRelatives(tLogger, collectedSoFar, 
				"Collecting for currentMorphemes="+String.join(", ", currentMorphemes)+
				"collectedSoFar=\n"+collectedSoFar);
		}
		Boolean keepGoing = null;
		
		if (currentMorphemes == null || currentMorphemes.length == 0) {
			// We have reached the root of the morphemes Trie.
			// No more searching to be done.
			keepGoing = false;
		}
		
		if (keepGoing == null) {
			keepGoing =
				collectDescendants(origWord, origWordMorphemes,
					currentMorphemes, collectedSoFar);
		}

		if (keepGoing == null) {
			keepGoing = true;
		}

		tLogger.trace("Returning keepGoing="+keepGoing);

		return keepGoing;	
	}

	protected Boolean collectDescendants(String origWord,
		 String[] origWordMorphemes, String[] currentMorphemes,
		 Set<MorphologicalRelative> collectedSoFar) throws MorphRelativesFinderException {

		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.morphrelatives.MorphRelativesFinder.collectDescendants");
		if (tLogger.isTraceEnabled()) {
			traceRelatives(tLogger, collectedSoFar, "Invoked with origWord=" + origWord + ", currentMorphemes=" + String.join(", ", currentMorphemes));
		}

		Boolean keepGoing = null;
		try {
			currentMorphemes = ArrayUtils.insert(0, currentMorphemes, "^");
			Iterator<String> iterDescendants = compiledCorpus.wordsContainingMorphNgram(currentMorphemes);
			while (iterDescendants.hasNext()) {
				String descendant = iterDescendants.next();
				if (!descendant.equals(origWord)) {
					MorphologicalRelative neighbor =
							neighbor =
									word2neigbhor(origWord, origWordMorphemes,
											descendant);
					collectedSoFar.add(neighbor);
				}
			}
			if (collectedSoFar.size() > maxRelatives) {
				// We have collected as many neighbors as reequired
				// No more searching to be done
				keepGoing = false;
			}
		} catch (CompiledCorpusException e) {
			throw new MorphRelativesFinderException(e);
		}

		if (tLogger.isTraceEnabled()) {
			traceRelatives(tLogger, collectedSoFar, "Returning with  origWord=" + origWord);
		}

		return keepGoing;
	}

	private List<MorphologicalRelative> bestRelatives(
			Set<MorphologicalRelative> relatives) throws CompiledCorpusException 
		{
			List<MorphologicalRelative> best = null;
			try {
				best = sortRelatives(relatives);
			} catch (Exception e) {
				throw new CompiledCorpusException(e);
			}
			
			int maxBest = 
				Math.min(maxRelatives, best.size());
			best = best.subList(0, maxBest);
			
			return best;
		}

	protected MorphologicalRelative word2neigbhor(
		String origWord, String[] origMorphemes, String word)
		throws CompiledCorpusException {
		String[] topDecomp = null;
		long frequency = 0;
		WordInfo winfo = compiledCorpus.info4word(word);
		if (winfo != null) {
			topDecomp = winfo.topDecomposition();
			frequency = winfo.frequency;
		}
		
		MorphologicalRelative relative = 
			new MorphologicalRelative(word, topDecomp, frequency, origWord, 
					origMorphemes);
		
		return relative; 
	}
	
	private List<MorphologicalRelative> sortRelatives(
		Set<MorphologicalRelative> relatives) {
		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.MorphRelativesFinder.sortRelatives");
		traceRelatives(tLogger, relatives, "Upon entry, relatives=");

		List<MorphologicalRelative> relativesLst = new ArrayList<MorphologicalRelative>();
		relativesLst.addAll(relatives);
		
		Collections.sort(relativesLst,
			(MorphologicalRelative e1, MorphologicalRelative e2) -> 
			{
				// We favor relatives that have the most morphemes in 
				// common with the original word
				//
				int e1Common = e1.morphemesInCommon().size(); 
				int e2Common = e2.morphemesInCommon().size(); 
				int comparison = Integer.compare(e2Common, e1Common);
				
//				// AD-July2020: THIS SEEMS TO MAKE THINGS WORSE....
//				//   So comment it out for now.
//				// In case of futher tie, we favor relatives that are closest to 
//				// the original word in the morphem Tree
//				//
//				if (comparison == 0) {
//					int e1Dist = e1.morphologicalDistance();
//					int e2Dist = e2.morphologicalDistance();
//					comparison = Integer.compare(e2Dist, e1Dist);
//				}
				
				// In case of further tie, we favor relatives that have high frequency
				//
				if (comparison == 0) {
					comparison = Long.compare(e2.getFrequency(), e1.getFrequency());
				}
				
				// In case of a further tie, we favor relatives whose lenght is 
				// closest to the lenght of the original word.
				//
				if (comparison == 0) {
					int e1LengDiff = 
						Math.abs(e1.getWord().length() - e1.getOrigWord().length());
					int e2LengDiff = 
						Math.abs(e2.getWord().length() - e2.getOrigWord().length());
					comparison = Integer.compare(e1LengDiff, e2LengDiff);
				}
				
				// In case of further tie, we sort alphabetically to garantee 
				// a deterministic order
				//
				if (comparison == 0) {
					comparison = e1.getWord().compareTo(e2.getWord());
				}				
				
				return comparison;
			}
		);
		
		traceRelatives(tLogger, relativesLst, "Returning relativesLst=");
		
		return relativesLst;
	}
	
	private List<MorphologicalRelative> possiblyConvertToSyllabic(
		String word, List<MorphologicalRelative> relatives) {
		boolean inputIsLatin = Pattern.compile("[a-zA-Z]").matcher(word).find();
		if (!inputIsLatin) {
			for (int ii=0; ii < relatives.size(); ii++) {
				relatives.get(ii).setWord(TransCoder.romanToUnicode(relatives.get(ii).getWord()));
			}
			
		}
		return relatives;
	}
	
	public void traceRelatives(Logger tLogger, MorphologicalRelative[] relatives, 
		String message) {
		if (tLogger.isTraceEnabled()) {
			message += "\nThe relatives are: ";
			for (MorphologicalRelative aRelative: relatives) {
				message += aRelative.getWord();
				long freq = aRelative.getFrequency();
				if (freq >= 0) {
					message += ":f="+freq;
				}
				
				List<String> commonMorphemes = aRelative.morphemesInCommon();
				if (commonMorphemes != null) {
					message += ":c="+commonMorphemes.size();
				}
				message += ", ";
			}
			tLogger.trace(message);
		}
	}

	public void traceRelatives(Logger tLogger, Set<MorphologicalRelative> relatiesSet, 
			String message) {
			if (tLogger.isTraceEnabled()) {
				List<MorphologicalRelative> relatives = new ArrayList<MorphologicalRelative>();
				relatives.addAll(relatiesSet);
				relatives.sort(
					(MorphologicalRelative e1, MorphologicalRelative e2) -> {
						return e1.getWord().compareTo(e2.getWord());
					}
				);
				MorphologicalRelative[] relativesArr = relatives.toArray(new MorphologicalRelative[0]);
				traceRelatives(tLogger, relativesArr, message);
			}
		}
		
	private void traceRelatives(Logger logger, 
		List<MorphologicalRelative> relatives, String message) {
		if (logger.isTraceEnabled()) {
			MorphologicalRelative[] relativesArr = null;
			if (relatives != null) {
				relativesArr = relatives.toArray(new MorphologicalRelative[0]);
			}
			traceRelatives(logger, relativesArr, message);
		}
	}	
}
