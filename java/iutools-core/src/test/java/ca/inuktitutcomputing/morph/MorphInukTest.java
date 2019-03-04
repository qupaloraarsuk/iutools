package ca.inuktitutcomputing.morph;

import org.junit.Test;

import ca.inuktitutcomputing.data.LinguisticDataSingleton;
import java.util.concurrent.TimeoutException;

public class MorphInukTest {
	
	@Test(expected=TimeoutException.class)
	public void test__decomposeWord__timeout() throws Exception  {
		LinguisticDataSingleton.getInstance("csv");
		MorphInuk.millisTimeout = 3000;
		String word = "ilisaqsitittijunnaqsisimannginnama";
		try {
		MorphInuk.decomposeWord(word);
		} catch(Exception e) {
			System.err.println(e.getClass().getName()+" --- "+e.getMessage());
			throw e;
		}
	}

	@Test(expected=TimeoutException.class)
	public void test__decomposeWord__timeout_10s() throws Exception  {
		LinguisticDataSingleton.getInstance("csv");
		String word = "ilisaqsitittijunnaqsisimannginnama";
		try {
		MorphInuk.decomposeWord(word);
		} catch(Exception e) {
			System.err.println(e.getClass().getName()+" --- "+e.getMessage());
			throw e;
		}
	}

}