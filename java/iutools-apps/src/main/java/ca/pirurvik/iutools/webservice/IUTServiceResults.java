package ca.pirurvik.iutools.webservice;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class IUTServiceResults {

	public String status = null;
	public String errorMessage = null;
	public String stackTrace = null;

	public IUTServiceResults() {}

	public void setException(Exception exc) {
		errorMessage = exc.getMessage();
		stackTrace = ExceptionUtils.getStackTrace(exc);
	}
}


