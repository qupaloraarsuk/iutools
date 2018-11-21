/*
 * Conseil national de recherche Canada 2003
 * 
 * Cr�� le 5-Dec-2003
 * par Benoit Farley
 * 
 */
package ca.inuktitutcomputing.unitTests.data;

import junit.framework.TestCase;

import java.util.Vector;

import ca.inuktitutcomputing.data.DemonstrativeEnding;
import ca.inuktitutcomputing.data.LinguisticDataAbstract;
import ca.inuktitutcomputing.data.SurfaceFormOfAffix;

public class DemonstrativeEndingTest extends TestCase {

	public DemonstrativeEndingTest(String sourceOfData) {
		LinguisticDataAbstract.init(sourceOfData);
	}

	public void testDemonstrativeEnding() {
		Object fs = LinguisticDataAbstract.getSurfaceForms("anngat");
		assertTrue("anngat pas trouvé dans 'surfaceFormsOfAffixes'",fs != null);
		Vector v = (Vector)fs;
		assertTrue("anngat plus d'une fois dans 'surfaceFormsOfAffixes'", v.size()==1);
		SurfaceFormOfAffix f = (SurfaceFormOfAffix)v.elementAt(0);
		DemonstrativeEnding x = (DemonstrativeEnding)f.getAffix();
		assertTrue("anngat pas trouvé comme affixe de la SurfaceFormOfAffix",x!=null);
	}

}
