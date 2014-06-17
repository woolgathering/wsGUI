// var wsPort = 9999; //in a separate file now, set from SC
//var discMessage = "this will show up after loosing connection to websockets" //as above
var initialMsg = true;
var isConnected = false;
var isChecking = false;
var checking;
var ws;
var documentID;

//this is not working as expected... since opened browser window will block static port when used
// function checkWwwConnection(){
//     var req = new XMLHttpRequest();
//     var staticConnection = true;
//     console.log("checking connection")
//     isChecking = true;
//     var url = document.location;
//     req.open('GET', url += ((/\?/).test(url) ? "&" : "?") + (new Date()).getTime(), false); //checking for server connection with a timestamp
//     try {
// 	req.send(null);
//     } catch (ex) {
// 	console.log("no connection");
// 	isChecking
// 	staticConnection = false;
//     }
//     if(staticConnection) {
// 	location.reload()
//     }

//     // var headers = req.getAllResponseHeaders().toLowerCase();
//     // var status = req.status;
//     // console.log(req);
//     // console.log(status);
// }

function checkWwwConnection(){
    location.reload()
}

// checking = setInterval( function(){checkWwwConnection()},3000); //start here
openWS(); //start

function onWsOpen() {
    document.body.innerHTML = "WebSocket connection opened. This message will disappear after adding the first object.";
    isConnected = true;
    clearInterval(checking);
    isChecking = false;
}

function onWsClose() {
    // document.body.innerHTML = "Socket connection closed, interface cleared";
    document.body.style.background = "#ffffff"; //white background
    document.body.innerHTML = discMessage;
    // console.log(isConnected);
    if(!isChecking) {
	checking = setInterval( function(){checkWwwConnection()},3000);
    }
    isConnected = false;
}


function openWS() {
    try {
	
	var host = "ws://" + window.location.hostname + ":" + wsPort;
	console.log("Host:", host);

    ws = new WebSocket(host);
    // document.body.innerHTML = "Establishing WebSocket connection on port " + wsPort + "..."; //not working here
    ws.onopen = function (param) {
	onWsOpen();
	console.log("Socket opened.");
	//maybe clear page body here?
	initialMsg = true;
    };
	
	ws.onclose = function (param) {
	    onWsClose();
	    console.log("Socket closed.");
	};
	
	ws.onmessage = function (param) {
	    // console.log("Socket full:", param);
	    console.log("Socket message:", param.data);
	    var inMsg = JSON.parse(param.data);
	    console.log("Incoming object: ", inMsg);
	    var command = inMsg.command;
	    var id = inMsg.id;
	    delete inMsg.command;
	    delete inMsg.id;
	    switch(command){
	    case "add":
		addWidget(id, inMsg);
		break;
	    case "remove":
		removeWidget(id);
		break;
	    case "update":
		updateWidget(id, inMsg);
		break;
	    case "run":
		eval(inMsg.code) //this is unsafe I think...
		break;
	    }
	};
	
	ws.onerror = function (er) {
	    console.log("Socket error:", er);
	    document.body.innerHTML = "Socket error: " + er;
	};
	
    } catch (ex) {
	console.log("Socket exception:", ex);
	checking = setInterval( function(){checkWwwConnection()},3000);
    }
}

var addWidget = function(id, params) {
    //clear the initial text
    if(initialMsg) {
	document.body.innerHTML = "";
    };
    initialMsg = false;
    console.log("addingWidget");
    var kind = params.kind;
    delete params.kind;
    var thisWidget;
    console.log("kind: ", kind);
    switch(kind) {
    case "button":
	// thisWidget = document.createElement('input');
	// thisWidget.setAttribute('type', 'button');
	thisWidget = document.createElement('button');
	var isPressed = false; //to avoid multiple clicks on mousedown ontouchstart

 	thisWidget.onclick = function(){ws.send([id, 0])}; //simpler for now, compatible with all devices

	//following bevavior needs revisiting!!!!

	// thisWidget.ontouchstart = function(){
	//     if(!isPressed) {
	// 	ws.send([id, 1]);
	// 	isPressed = true;
	//     }
	// };
	// thisWidget.onmousedown = function(){
	//     if(!isPressed) {
	// 	ws.send([id, 1]);
	// 	isPressed = true;
	//     }
	// };
	// thisWidget.ontouchend = function(){
	//     if(isPressed) {
	// 	ws.send([id, 0]);
	// 	isPressed = false;
	//     }
	// };
	// thisWidget.onmouseup = function(){
	//     if(isPressed) {
	// 	ws.send([id, 0]);
	// 	isPressed = false;
	//     }
	// };
	// console.log("in case: ", thisWidget);
	break;
    case "slider":
	thisWidget = document.createElement('input');
	thisWidget.setAttribute('type', 'range');
	// thisWidget.onclick = function(){ws.send([id, thisWidget.value])}; //not sure if we want it
	thisWidget.oninput = function(){ws.send([id, thisWidget.value])};
	//sending commands here
	break;
    case "image":
	thisWidget = document.createElement('img');
	// thisWidget.onclick = function(){ws.send([id, 0])};
	// thisWidget.onmousedown = function(){ws.send([id, 1])};
	var isPressed = false; //to avoid multiple clicks on mousedown ontouchstart
	// thisWidget.ontouchstart = function(){
	//     if(!isPressed) {
	// 	ws.send([id, 1]);
	// 	isPressed = true;
	//     }
	// };
	// thisWidget.onmousedown = function(){
	//     if(!isPressed) {
	// 	ws.send([id, 1]);
	// 	isPressed = true;
	//     }
	// };
	// thisWidget.ontouchend = function(){
	//     if(isPressed) {
	// 	ws.send([id, 0]);
	// 	isPressed = false;
	//     }
	// };
	// thisWidget.onmouseup = function(){
	//     if(isPressed) {
	// 	ws.send([id, 0]);
	// 	isPressed = false;
	//     }
	// };
	thisWidget.onclick = function(){ws.send([id, 0])};
	break;
    // case "knob": //this will require additional js library probably?
	// break;
    case "body": //main body of the document, for background styling for now 
	thisWidget = document.body; //should be enough to style it?
	break;
    case "title": //document; can't be assigned an id
	// thisWidget = document; 
	// documentID = id;
	break;
    case "text":
	thisWidget = document.createElement('div');
	break;
    case "checkbox":
	thisWidget = document.createElement('input');
	thisWidget.setAttribute('type', 'checkbox');
	thisWidget.onchange = function(){ws.send([id, thisWidget.checked ? 1 : 0])};
	break;
    case "menu":
	thisWidget = document.createElement('select');
	thisWidget.onchange = function(){ws.send([id, thisWidget.value])};
	break;
    };
    // console.log("after case: ", thisWidget);
    if(kind!="title") {
	thisWidget.setAttribute('id',id);
    }

    // updateWidget(id, params); //this was not working, there was no widget at the id yet
    updateWidgetObj(thisWidget, params);
    if((kind != "body") && (kind != "title")) { 
	document.body.appendChild(thisWidget)
    }
}

var removeWidget = function(id) {
    console.log("removing widget");
    var thisWidget = document.getElementById(id);
    thisWidget.parentNode.removeChild(thisWidget);
    // var kind = params.kind;
    // var id = params.id;
    // document.createElement('button');
}

var updateWidgetObj = function(thisWidget, params) {
    console.log("updating widget");
    console.log(params);
    if(params.title) {
	document.title = params.title;
    } else {
	for(var key in params){
            var attrValue = params[key];
	    if(key == 'innerHTML') {
		thisWidget.innerHTML = attrValue;
	    } else if (key == 'value') {
		thisWidget.value = attrValue;
		// console.log("setting value");
	    } else if (key == 'menuItems') {
		var menuLength = thisWidget.options.length;
		for (var i = 0; i < menuLength; i++) { 
		    console.log("removing ", i);
		    thisWidget.options.remove(menuLength - i - 1);
		} 
		attrValue = attrValue.split(',');
		for (var i = 0; i < attrValue.length; i++) { 
		    var option = document.createElement("option");
		    option.text = attrValue[i];
		    option.value = i;
		    thisWidget.options.add(option);
		}
	    } else if (key == 'checked') {
		thisWidget.checked = parseInt(attrValue);
		// console.log("is checked");
	    } else {
		thisWidget.setAttribute(key, attrValue);
	    }
	}
    }

    // var kind = params.kind;
    // var id = params.id;
    // document.createElement('button');
}

var updateWidget = function(id, params) {
    updateWidgetObj(document.getElementById(id), params);
}
