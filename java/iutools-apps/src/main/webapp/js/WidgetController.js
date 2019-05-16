class WidgetController {   

	constructor(_config) {
		this.config = _config;
		this.attachHtmlElements();
	}
	
	attachHtmlElements() {
		// This method should be overridden by subclasses if 
		// you want to actually attach elements to the controller.
	}
	
	elementForProp(property) {
		if (property == null) {
			throw new Error("Config property name cannot be null");
		}
		var eltID = this.config[property];
		if (eltID == null) {
			throw new Error("Controller has no config property called '"+property+"'");
		}
		
		var elt = $('#'+eltID);
		if (elt == null || elt.length == 0 || elt.val() == null) {
			elt = null;
		}
		if (elt == null) {
			throw new Error("Element with ID "+eltID+" was not defined. Maybe you need to execute this method after the DOM was loaded?");
		}
		return elt;
	}
	
	
	activeElement() {
		var elt = $(':focus').context.activeElement();
		return elt;
	}
	
	setEventHandler(propName, evtName, handler) {
		var elt = this.elementForProp(propName);
		var controller = this;
		var fct_handler =
				function() {
					handler.call(controller);
				};
		if (evtName == "click") {
			elt.off('click').on("click", fct_handler);
		}
	}	
	
	onReturnKey(id, method) {
		var element = this.elementForProp(id);
		var controller = this;
		
		var keypressHandler = 
				function(event) {
					console.log("-- WidgetController.onReturnKey.function: intercepted a key event");
					var keycode = (event.keyCode ? event.keyCode : event.which);
					if(keycode == '13'){
						console.log("-- WidgetController.onReturnKey.function: ENTER key intercepted. Invoking method="+method+" on controller="+JSON.stringify(controller));
						method.call(controller);
					}
				};		
		element.keypress(keypressHandler);

		return;
	}	
	
}
