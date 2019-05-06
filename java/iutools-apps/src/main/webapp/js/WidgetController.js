class WidgetController {   

	constructor(_config) {
		this.config = _config;
		this.attachHtmlElements();
	}
	
	attachHtmlElements() {
		// This method should be overridden by subclasses if 
		// you want to actually attach elements to the controller.
	}
	
	elementID(property) {
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
	
	onReturnKey(id, method) {
		var element = this.elementID(id);
		var controller = this;
		
		var keypressHandler = 
				function(event) {
					var keycode = (event.keyCode ? event.keyCode : event.which);
					if(keycode == '13'){
						method.call(controller);
					}
				};		
		element.keypress(keypressHandler);

		return;
	}	
	
}
