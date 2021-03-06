package ca.pirurvik.iutools.edit_distance;

//A Dynamic Programming based Java program to find minimum 
//number operations to convert str1 to str2 
public class DP5 implements EditDistanceCalculator
{ 
	static int min(int x,int y,int z) 
	{ 
		if (x <= y && x <= z) return x; 
		if (y <= x && y <= z) return y; 
		else return z; 
	} 

	public double distance(String str1, String str2) {
		return distance(str1,str2,str1.length(),str2.length());
	}
	int distance(String str1, String str2, int m, int n) 
	{ 
		// Create a table to store results of subproblems 
		int dp[][] = new int[m+1][n+1]; 
	
		// Fill d[][] in bottom up manner 
		for (int i=0; i<=m; i++) 
		{ 
			for (int j=0; j<=n; j++) 
			{ 
				// If first string is empty, only option is to 
				// insert all characters of second string 
				if (i==0) 
					dp[i][j] = j; // Min. operations = j 
	
				// If second string is empty, only option is to 
				// remove all characters of second string 
				else if (j==0) 
					dp[i][j] = i; // Min. operations = i 
	
				// If last characters are same, ignore last char 
				// and recur for remaining string 
				else if (str1.charAt(i-1) == str2.charAt(j-1)) 
					dp[i][j] = dp[i-1][j-1]; 
	
				// If the last character is different, consider all 
				// possibilities and find the minimum 
				else
					dp[i][j] = 1 + min(dp[i][j-1], // Insert 
									dp[i-1][j], // Remove 
									dp[i-1][j-1]); // Replace 
			} 
		} 

		return dp[m][n]; 
	} 

	

	public static void main(String args[]) throws EditDistanceCalculatorFactoryException, EditDistanceCalculatorException 
	{ 
//		EditDistanceCalculator calculator = EditDistanceCalculatorFactory.getEditDistanceCalculator("DP5");
		EditDistanceCalculator calculator = EditDistanceCalculatorFactory.getEditDistanceCalculator(EditDistanceCalculatorFactory.DistanceMethod.DP5);
		String str1 = "sunday"; 
		String str2 = "saturday"; 
		System.out.println( calculator.distance( str1 , str2 ) ); 
	} 
}/*This code is contributed by Rajat Mishra*/
