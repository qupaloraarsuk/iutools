package ca.pirurvik.iutools.webservice;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import ca.inuktitutcomputing.data.LinguisticDataSingleton;
import ca.inuktitutcomputing.data.Morpheme;
import ca.inuktitutcomputing.morph.Gist;
import ca.inuktitutcomputing.nunhansearch.ProcessQuery;
import ca.nrc.config.ConfigException;
import ca.nrc.datastructure.Pair;
import ca.nrc.json.PrettyPrinter;
import ca.pirurvik.iutools.CompiledCorpus;
import ca.pirurvik.iutools.CompiledCorpusRegistry;
import ca.pirurvik.iutools.CompiledCorpusRegistryException;
import ca.pirurvik.iutools.MorphemeExtractor;

public class OccurenceSearchEndpoint extends HttpServlet {
			
	public OccurenceSearchEndpoint() {
		this.initialize();
	};

	protected void initialize() {
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		Logger tLogger = Logger.getLogger("ca.pirurvik.iutools.webservice.OccurenceSearchEndpoint.doPost");
		tLogger.trace("invoked");
		tLogger.trace("request URI= "+request.getRequestURI());
		
		PrintWriter out = response.getWriter();
		String jsonResponse = null;
		
		OccurenceSearchInputs inputs = null;
		try {
			EndPointHelper.setContenTypeAndEncoding(response);
			inputs = EndPointHelper.jsonInputs(request, OccurenceSearchInputs.class);
			ServiceResponse results = executeEndPoint(inputs);
			jsonResponse = new ObjectMapper().writeValueAsString(results);
		} catch (Exception exc) {
			jsonResponse = EndPointHelper.emitServiceExceptionResponse("General exception was raised\n", exc);
		}
		writeJsonResponse(response, jsonResponse);
	}
	
	private void writeJsonResponse(HttpServletResponse response, String json) throws IOException {
		PrintWriter writer = response.getWriter();
		writer.write(json);
		writer.close();
	}

	public OccurenceSearchResponse executeEndPoint(OccurenceSearchInputs inputs) 
			throws Exception  {
		Logger logger = Logger.getLogger("ca.pirurvik.iutools.webservice.OccurenceSearchEndpoint.executeEndPoint");
		logger.trace("inputs= "+PrettyPrinter.print(inputs));
		OccurenceSearchResponse results = new OccurenceSearchResponse();
		
		if (inputs.wordPattern == null || inputs.wordPattern.isEmpty()) {
			throw new SearchEndpointException("Word pattern was empty or null");
		}
		
		String corpusName = inputs.corpusName;
		if (inputs.corpusName == null || inputs.corpusName.isEmpty()) {
			corpusName = null; // will use default corpus = Hansard2002
		}
		
		if (inputs.exampleWord == null) {
			// Retrieve all words that match the wordPattern
			// and put the results in results.matchingWords
//			List<Pair<String,List<String>>> wordsForMorphemes = getOccurrences(inputs);
			HashMap<String,Pair<String,Pair<String,Long>[]>> wordsForMorphemes = getOccurrences(inputs,corpusName);
//			Gson gson = new Gson();
//			String str = gson.toJson(wordsForMorphemes, HashMap.class);
//			logger.trace("results in json: "+str);
//			results.matchingWords = str;
			results.matchingWords = wordsForMorphemes;
		} else {
			// Retrieve some occurences of the provided exampleWord
			// and put the results in results.occurences
			Gist gistOfWord = new Gist(inputs.exampleWord);
			logger.trace("gist= "+PrettyPrinter.print(gistOfWord));
			ProcessQuery processQuery = new ProcessQuery();
			String query = inputs.exampleWord;
			logger.trace("query= "+query);
			String[] alignments = processQuery.run(query);
			logger.trace("alignments= "+PrettyPrinter.print(alignments));
			//Gson gson = new Gson();
			//results.exampleWord = gson.toJson(gistOfWord, Gist.class);
			HashMap<String,Object> res = new HashMap<String,Object>();
			res.put("gist", gistOfWord);
			res.put("alignments", alignments);
			results.exampleWord = res;
		}
		

		return results;
	}

	/*private CompiledCorpus getCorpus(String corpusName) throws ConfigException, CompiledCorpusRegistryException {
		CompiledCorpus corpus = CompiledCorpusRegistry.getCorpus(corpusName);
		return corpus;
	}*/

	private HashMap<String,Pair<String,Pair<String,Long>[]>> getOccurrences(OccurenceSearchInputs inputs, String corpusName) 
			throws SearchEndpointException, ConfigException, CompiledCorpusRegistryException, 
					IOException, Exception {
		
		MorphemeExtractor morphExtr = new MorphemeExtractor();
		CompiledCorpus compiledCorpus = CompiledCorpusRegistry.getCorpus(corpusName);
		morphExtr.useCorpus(compiledCorpus);
		
		LinguisticDataSingleton.getInstance("csv");
		
		List<MorphemeExtractor.Words> wordsForMorphemes = morphExtr.wordsContainingMorpheme(inputs.wordPattern);
		HashMap<String,Pair<String,Pair<String,Long>[]>> results = new HashMap<String,Pair<String,Pair<String,Long>[]>>();
		Iterator<MorphemeExtractor.Words> itWFM = wordsForMorphemes.iterator();
		while (itWFM.hasNext()) {
			MorphemeExtractor.Words w = itWFM.next();
			String meaning = Morpheme.getMorpheme(w.morphemeWithId).englishMeaning;
			List<Pair<String,Long>> wordsFreqs = w.words;
			Pair<String,Long>[] wordsFreqsArray = wordsFreqs.toArray(new Pair[] {});
			Arrays.sort(wordsFreqsArray, new WordFreqComparator());
			results.put(w.morphemeWithId, new Pair<String,Pair<String,Long>[]>(meaning,wordsFreqsArray));
		}
	
		return results;
	}


}

class WordFreqComparator implements Comparator<Pair<String,Long>> {
    @Override
    public int compare(Pair<String,Long> a, Pair<String,Long> b) {
    	if (a.getSecond().longValue() > b.getSecond().longValue())
    		return -1;
    	else if (a.getSecond().longValue() < b.getSecond().longValue())
			return 1;
    	else 
    		return a.getFirst().compareToIgnoreCase(b.getFirst());
    }
}
