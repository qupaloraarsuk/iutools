package ca.pirurvik.iutools.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.nrc.datastructure.Pair;

public class BingSearchMultithrd {
	
	private BingSearchWorker[] workers = null;
	
	public BingSearchMultithrd() {
	}
	
	public Pair<Long,List<SearchHit>> search(String[] terms)  {
		return search(terms, 0);
	}
	
	public Pair<Long,List<SearchHit>> search(String[] terms, int hitsPageNum)  {
		return search(terms, hitsPageNum, 10);
	}

	public Pair<Long,List<SearchHit>> search(String[] terms, int hitsPageNum, Integer hitsPerPage)  {
		return search(terms, hitsPageNum, hitsPerPage, new HashSet<String>());
	};

	public Pair<Long,List<SearchHit>> search(String[] terms, int hitsPageNum, int hitsPerPage, Set<String> excludeURLs)  {
		long totalEstHits = 0;		
		List<SearchHit> hits = new ArrayList<SearchHit>();
		
		// Create one worker per term
		int numWorkers = terms.length;
		workers = new BingSearchWorker[numWorkers];
		for (int ii=0; ii < numWorkers; ii++) {
			String aTerm = terms[ii];
			BingSearchWorker aWorker = new BingSearchWorker(aTerm, hitsPageNum, hitsPerPage, "thr-"+ii+"-"+aTerm);
			aWorker.excludeUrls(excludeURLs);
			workers[ii] = aWorker;
			aWorker.start();
		}
		
		// Monitor the workers until they are all done
		while (true) {
			int stillRunning = 0;
			for (int ii=0; ii < numWorkers; ii++) {
				BingSearchWorker currWorker = workers[ii];
				if (currWorker != null) {
					if (currWorker.stillWorking()) {
						stillRunning++;
					} else {
						// This worker just finished running. Integrate its
						// results in the total
						totalEstHits += currWorker.totalHits;
					}
				}				
			}
			// All workers have finished
			if (stillRunning == 0) break;
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// Nothing to do if the sleep is interrupted
			}
		}
		
		List<SearchHit> sortedHits = aggregateWorkerHits();
		
		return Pair.of(totalEstHits, sortedHits);
	}

	private List<SearchHit> aggregateWorkerHits() {

		//
		// Mix hits of the different workers. That way, the top
		// 10 hits will contain a mix of hits for different terms.
		//
		
		List<SearchHit> aggregated = new ArrayList<SearchHit>();
		boolean someHitsLeft = true;
		while (someHitsLeft) {
			int emptyWorkers = 0;
			for (int ii=0; ii < workers.length; ii++) {
				List<SearchHit> remainingHits = workers[ii].hits;
				if (remainingHits.size() == 0) {
					emptyWorkers++;
				} else {
					aggregated.add(remainingHits.remove(0));
				}
			}
			if (emptyWorkers == workers.length) someHitsLeft = false;
		}
		
		return aggregated;
	}



}