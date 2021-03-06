/**
 * Controller for a div that displays the Gist of an Inuktut word.
 */

class WordGistController extends WidgetController {
	
	constructor(config) {
		super(config);
	} 
	
	// Setup handler methods for different HTML elements specified in the config.
	attachHtmlElements() {
		this.setEventHandler("divGist_iconizer", "click", this.iconizeDivExampleWord);
		this.setEventHandler("divGist_iconized", "click", this.deiconizeDivExampleWord);
	}
	
	gistWord(word) {
		this.elementForProp("divGist").show();
		this.clearGist();
		this.displayWordBeingGisted(word);
		this.showSpinningWheel("divGist_message","Gisting word");
		
		this.invokeGistWordService(
				this.getExampleWordRequestData(word),
				this.successExampleWordCallback, 
				this.failureExampleWordCallback);
	}

	displayWordBeingGisted(word) {
		var divWord = this.elementForProp("divGist_word");
		divWord.html("<h2>"+word+"</h2>\n");
	}
	
	invokeGistWordService(jsonRequestData, _successCbk, _failureCbk) {
		this.invokeWebService('srv/gist/gistword', jsonRequestData,
				_successCbk, _failureCbk);
	}

	successExampleWordCallback(resp) {
		var tracer = new Tracer('OccurenceController.successExampleWordCallback', true);
		if (resp.errorMessage != null) {
			this.failureExampleWordCallback(resp);
		} else {
			this.displayWordGist(resp);	
		}
		this.setWordExampleBusy(false);
	}
	
	failureExampleWordCallback(resp) {
		if (! resp.hasOwnProperty("errorMessage")) {
			// Error condition comes from tomcat itself, not from our servlet
			resp.errorMessage = 
				"Server generated a "+resp.status+" error:\n\n" +
				resp.responseText;
		}				
		this.error(resp.errorMessage);
		this.hideSpinningWheel("divGist_message");
	}
	
	setWordExampleBusy(flag) {
		this.busy = flag;		
		if (flag) {
			this.showSpinningWheel('divGist_message','Searching');
		} else {
			this.hideSpinningWheel('divGist_message');
		}
	}	
	
	getExampleWordRequestData(_word) {
		var request = { 
			word: _word
			};
		var jsonInputs = JSON.stringify(request);;
		return jsonInputs;
	}
	
	clearGist() {
		this.elementForProp("divGist_word").html("");
		this.elementForProp("divGist_contents").html("");
	}
	
	displayWordGist(results) {
		var tracer = new Tracer('WordGistController.displayWordGist', true);
		var divGist = this.elementForProp("divGist");
		this.hideSpinningWheel("divGist_message");
		
		var html = this.htmlMorphologicalAnalyses(results, html);
		html = this.htmlAlignments(results, html);
		this.elementForProp("divGist_contents").html(html);
	}
	
	htmlMorphologicalAnalyses(results, html) {
		var html = "<h3>Morphological decomposition<h3>\n";
		var wordComponents = results.wordGist.wordComponents;
		if (wordComponents != null) {
			html += '<table id="tbl-gist" class="gist"><tr><th>Morpheme</th><th>Meaning</th></tr>';
			for (var iwc=0; iwc<wordComponents.length; iwc++) {
				var component = wordComponents[iwc];
				html += '<tr><td>'+component.fst+'</td><td>'+component.snd+'</td></tr>'
			}
			html += '</table>';
		} else {
			html += "<h4>Word could not be decomposed into morphemes</h4>"
		}
	
		return html;
	}
	
	
	htmlAlignments(results, html) {
		var gist = results.wordGist
		var alignments = results.alignments;
		html += "<h3>Examples</h3>\n";
		if (alignments != null && alignments.length > 0) {
			html += '<table id="tbl-alignments" class="alignments"><th>Inuktitut</th><th>English</th></tr>';
			for (var ial=0; ial<Math.min(30,alignments.length); ial++) {
				var alignment = alignments[ial];
				console.log('alignment: '+JSON.stringify(alignment));
				console.log('iu: '+alignment.sentences['iu']);
				console.log('en: '+alignment.sentences['en']);
				var inuktitutSentence = alignment.sentences['iu'].replace(gist.word,'<strong>'+gist.word+'</strong>').replace(/\.{5,}/,'...');
				var englishSentence = alignment.sentences['en'].replace(/\.{5,}/,'...').trim();
				html += '<tr><td>'+inuktitutSentence+'</td><td>'+englishSentence+'</td></tr>';
			}
			html += '</table>';
		} else {
			html += "<h4>Could not find examples of use for this word.</h4>"
		}
		
		return html;
	}
	
	iconizeDivExampleWord() {
		var divGist = this.elementForProp("divGist");
		divGist.hide();
		var divIconizedWordExample = this.elementForProp("divGist_iconized");
		divIconizedWordExample.show();
	}
	
	deiconizeDivExampleWord() {
		var divGist = this.elementForProp("divGist");
		divGist.show();
		var divIconizedWordExample = this.elementForProp("divGist_iconized");
		divIconizedWordExample.hide();
	}
}
