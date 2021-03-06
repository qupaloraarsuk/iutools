/*
 * Controller for the search.html page.
 */

class OccurrenceController extends WidgetController {
	
	constructor(config) {
		super(config);
		this.wordGistController = new WordGistController(config);
	} 
	
	// Setup handler methods for different HTML elements specified in the config.
	attachHtmlElements() {
		this.setEventHandler("btnGet", "click", this.onFindExamples);
		this.onReturnKey("inpMorpheme", this.onFindExamples);
	}
	
	onFindExamples() {
		this.elementForProp("divGist_contents").html('').parent().hide();
		this.elementForProp('inpExampleWord').val('');
		this.elementForProp("divGist").hide();
		var isValid = this.validateQueryInput();
		if (isValid) {
			this.clearResults();
			this.setGetBusy(true);
			var requestData = this.getSearchRequestData();
			this.invokeFindExampleService(requestData, 
					this.findExamplesSuccessCallback, this.findExamplesFailureCallback);
		}
	}
	
	onExampleSelect(ev) {
		var element = ev.target;
		var exampleWord = $(element).text();
		occurrenceController.elementForProp("divGist_contents").html('');
		occurrenceController.elementForProp("divGist_word").html('');
		this.wordGistController.gistWord(exampleWord);				
	}

	invokeFindExampleService(jsonRequestData, _successCbk, _failureCbk) {
		this.invokeService(jsonRequestData, _successCbk, _failureCbk, 
				'srv/occurrences');
	}
	
	
	invokeService(jsonRequestData, _successCbk, _failureCbk, _url) {
			var tracer = new Tracer('OccurenceController.invokeService', true);
			this.busy = true;
			var controller = this;
			var fctSuccess = 
					function(resp) {
						_successCbk.call(controller, resp);
					};
			var fctFailure = 
					function(resp) {
						_failureCbk.call(controller, resp);
					};
					
			// this line is for development only, allowing to present results without calling Bing.
			//var jsonResp = this.mockSrvSearch();fctSuccess(jsonResp);
		
			$.ajax({
				method: 'POST',
				url: _url,
				data: jsonRequestData,
				dataType: 'json',
				async: true,
		        success: fctSuccess,
		        error: fctFailure
			});
			
	}
	
	
	findExamplesSuccessCallback(resp) {
		var tracer = new Tracer('OccurenceController.findExamplesSuccessCallback', true);
		
		if (resp.errorMessage != null) {
			this.findExamplesFailureCallback(resp);
		} else {
			this.setGetResults(resp);	
		}
		this.setGetBusy(false);
	}
	
	findExamplesFailureCallback(resp) {
		if (! resp.hasOwnProperty("errorMessage")) {
			// Error condition comes from tomcat itself, not from our servlet
			resp.errorMessage = 
				"Server generated a "+resp.status+" error:\n\n" +
				resp.responseText;
		}				
		this.error(resp.errorMessage);
		this.setGetBusy(false);
	}
	
	setGetBusy(flag) {
		this.busy = flag;
		if (flag) {
			this.disableSearchButton();	
			this.showSpinningWheel('divMessage','Searching');
			this.error("");
		} else {
			this.enableSearchButton();		
			this.hideSpinningWheel('divMessage');
		}
	}
	
	getSearchRequestData() {
		var tracer = new Tracer('OccurenceController.getSearchRequestData');
		var wordPattern = this.elementForProp("inpMorpheme").val().trim();
		if (wordPattern=='')
			wordPattern = null;
		var corpusName = this.elementForProp("inpCorpusName").val().trim();
		if (corpusName=='')
			corpusName = null;
		var selectCorpus = this.elementForProp("selCorpusName");
		var selectedCorpusName = selectCorpus.val();
		corpusName = selectedCorpusName;
		var nbExamples = this.elementForProp("inpNbExamples").val().trim();
		if (nbExamples=='')
			nbExamples = "20";

		var request = {
				wordPattern: wordPattern,
				corpusName: corpusName,
				nbExamples: nbExamples
		};
		
		var jsonInputs = JSON.stringify(request);;
		
		return jsonInputs;
	}
	
	enableSearchButton() {
		this.elementForProp('btnGet').attr("disabled", false);
	}
	
	disableSearchButton() {
		this.elementForProp('btnGet').attr("disabled", true);
	}
	
	error(err) {
		err = err.replace("\n", "<br/>\n");
		this.elementForProp('divError').html(err);
		this.elementForProp('divError').show();	 
	}
	

	setGetResults(results) {
		var jsonResults = JSON.stringify(results);
		var divResults = this.elementForProp("divResults");
		
		divResults.empty();
		
		var morphemesMap = results.matchingWords;
		var morphemes = Object.keys(morphemesMap);
		var html = 'The input is the canonical form of '+morphemes.length+' morpheme'+
			(morphemes.length==1?'':'s')+': ';
		html += '<div id="list-of-morphemes">';
		html += '<ul>';
		for (var imorph=0; imorph<morphemes.length; imorph++) {
			var key = morphemes[imorph];
			var meaning = morphemesMap[key].meaning;
			html += '<li><a href="#'+key+'">'+key+'</a>&nbsp;&nbsp;&nbsp;&ndash;&nbsp;&nbsp;&nbsp;'+meaning+'</li>'
		}
		html += '</ul>';
		html += '</div>';
		
		
		for (var imorph=0; imorph<morphemes.length; imorph++) {
			var morpheme = morphemes[imorph];
			var meaning = morphemesMap[morpheme].meaning;
			var words = morphemesMap[morpheme].words;
			var wordFreqs = morphemesMap[morpheme].wordScores;
			var wordsFreqsArray = new Array(wordFreqs.length);
			for (var iwf=0; iwf<wordFreqs.length; iwf++) {
				wordsFreqsArray[iwf] = 
					'<a class="word-example" id="word-example-'+words[iwf]+'"'
					+ '>'+words[iwf]+'</a>'
					;
			}
			html += '<div class="morpheme-details">';
			html += '<a name="'+morpheme+'"></a>'+'<strong>'+morpheme+
			'</strong>&nbsp;&nbsp;&nbsp;&ndash;&nbsp;&nbsp;&nbsp;'+meaning+
				'<div style="margin:5px 80px 15px 15px;">'+
				wordsFreqsArray.join(';&nbsp;&nbsp;&nbsp; ')+'</div>';
			html += '</div>';
		}
		divResults.append(html);
		
		this.attachListenersToExampleWords();
		
		new RunWhen().domReady(function() {
				divResults.show();
		});
	}
	
	attachListenersToExampleWords() {		
		var anchorsWords = $(document).find('.word-example');
	    for (var ipn=0; ipn<anchorsWords.length; ipn++) {
	    	this.setEventHandler(anchorsWords.eq(ipn), "click", this.onExampleSelect);	   
	    }
	}
	
	validateQueryInput() {
		var isValid = true;
		var query = this.elementForProp("inpMorpheme").val();
		if (query == null || query === "") {
			isValid = false;
			this.error("You need to enter something in the morpheme field");
		}
		return isValid;
	}
	
	clearResults() {
		this.elementForProp('divError').empty();
		this.elementForProp('divResults').empty();
	}
	

	
	// ---------------------- Test Section ------------------------------ //

	onTest() {
		this.invokeTestService({}, 
				this.testSuccessCallback, this.testFailureCallback);
	}

	invokeTestService(jsonRequestData, _successCbk, _failureCbk) {
			var controller = this;
			var fctSuccess = 
					function(resp) {
						_successCbk.call(controller, resp);
					};
			var fctFailure = 
					function(resp) {
						_failureCbk.call(controller, resp);
					};
		
			$.ajax({
				method: 'POST',
				url: 'srv/hello',
				data: jsonRequestData,
				dataType: 'json',
				async: true,
		        success: fctSuccess,
		        error: fctFailure
			});
	}
	
	testSuccessCallback(resp) {
		var element = this.elementForProp("divTestResponse");
		element.empty();
		element.html(resp.message);
	}
	    
	testFailureCallback(resp) {
		var element = this.elementForProp("divTestResponse");
		element.empty();
		element.html("Server returned error, resp="+JSON.stringify(resp));
	}
}
