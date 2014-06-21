WsGUI {
	var <wwwPort, <oscPath, <>actionOnClose, suppressPosting;
	var <wsPid, <wwwPid, <wwwPipe;
	var <wsPort, <wsOscPort; //chosen automatically
	var <>onConnection, <>onDisconnection; //not needed?
	// var <pythonPath, <bridgePath, <classPath;
	var <scSendNetAddr, <socketsResponder, <clientDict;//, <guiObjDict
	var <guiObjects; //guiObjects: name -> [widgetParams, function, controlSpec]
	var <namesToIDs;
	var	<numClients;
	var <bodyID; //this will be id of the object referring to the body, when the background is first set;
	var <titleID; //this will be id of the object referring to the title, when the background is first set;
	var <curWidgetID = 0;
	var styleKeys, numericOutputKinds;

	classvar <>pythonPath, <>bridgePath, <>checkPortPath, <classPath; //set in init...
	// classvar <>jsFilePath = "www/ws.js";
	classvar <>wwwPath = "www"; //relative to class
	classvar <>jsFilename = "wsport.js"; //relative to class
	classvar <>discMsgFile = "discMessage.js";

	*new {|wwwPort, // wsPort = 9999, wsOscPort = 7000,
		oscPath = "/sockets", actionOnClose, suppressPosting = false|
		^super.newCopyArgs(wwwPort, // wsPort, wsOscPort,
			oscPath, actionOnClose, suppressPosting).init;
	}

	*killPython { //sometimes needed
		"killall python".unixCmd
	}

	*updateWsPortInFile {arg port = 8000;
		var path = wwwPath; //www path
		var filename = jsFilename;
		var fileContentsArray, filePath;
		if(path[0] == "~", {//it's relative to home directory
			path = path.standardizePath;
		}, {
			if(path[0] != "/", {//it's relative to the class file
				path = File.realpath(this.class.filenameSymbol).dirname ++ "/" ++ path;
			});
		});
		filePath = path.withTrailingSlash ++ filename;
		"Writing ws port number to the file at ".post; filePath.postln;
		// File.use(path, "r", {|file|
		// 	fileContentsArray = file.readAllString.split($\n).collect({|thisLine, lineNumber|
		// 		thisLine.postln;
		// 		if(thisLine.replace(" ", "").replace("	", "").beginsWith("varwsPort"), {
		// 			// "This is the line!".postln;
		// 			thisLine = thisLine.split($=)[0] ++ " = " ++ port ++ ";";
		// 		});
		// 		thisLine;
		// 	});
		// });
		File.use(filePath, "w", {|file|
			// fileContentsArray.do({|thisLine, lineNumber|
			file.write("var wsPort = " ++ port.asString ++ ";")
			// });
		});
		"Writing done.".postln;
	}

	*setDisconnectedMessage {|message|
		var path = wwwPath; //www path
		var filename = discMsgFile;
		var fileContentsArray, filePath;
		if(path[0] == "~", {//it's relative to home directory
			path = path.standardizePath;
		}, {
			if(path[0] != "/", {//it's relative to the class file
				path = File.realpath(this.class.filenameSymbol).dirname ++ "/" ++ path;
			});
		});
		filePath = path.withTrailingSlash ++ filename;
		"Writing disconnection message to the file at ".post; filePath.postln;
		// File.use(path, "r", {|file|
		// 	fileContentsArray = file.readAllString.split($\n).collect({|thisLine, lineNumber|
		// 		thisLine.postln;
		// 		if(thisLine.replace(" ", "").replace("	", "").beginsWith("varwsPort"), {
		// 			// "This is the line!".postln;
		// 			thisLine = thisLine.split($=)[0] ++ " = " ++ port ++ ";";
		// 		});
		// 		thisLine;
		// 	});
		// });
		File.use(filePath, "w", {|file|
			// fileContentsArray.do({|thisLine, lineNumber|
			file.write("var discMessage = \"" ++ message.asString.replace("\n", "<br>") ++ "\";")
			// });
		});
		"Writing done.".postln;
	}

	init {
		actionOnClose ?? {actionOnClose = {}};

		pythonPath ?? {pythonPath = "python"};
		classPath ?? {classPath = File.realpath(this.class.filenameSymbol)};
		// classPath.postln;
		bridgePath ?? {bridgePath = (classPath.dirname ++ "/python/ws_osc.py").escapeChar($ )}; //remember to escape!!!
		checkPortPath = (classPath.dirname ++ "/python/checkport.py").escapeChar($ );
		// "bridgePath: ".post; bridgePath.postln;
		// workingDir = bridgePath.dirname;
		// "workingDir: ".post; workingDir.postln;

		//init vars
		guiObjects = IdentityDictionary.new(know: true);
		clientDict = IdentityDictionary.new(know: true);
		namesToIDs = IdentityDictionary.new(know: true);
		styleKeys = [\bounds, \color, \backgroundColor, \textColor, \font, \textAlign, \css]; //this are all symbols that should not be intepreted as object parameters, but rather as stylig (CSS) elements; custom css string can be added under \css key
		numericOutputKinds = [\slider];

		//check static server, start if port is available
		wwwPort !? {
			if(this.checkStaticPort, {
				this.startStaticServer(wwwPort)
			}, {
				Error("WsGUI: can't bind to port" + wwwPort.asString ++". Please use a different port or terminate the process using it, close any browser windows pointing to that port and wait").throw;
			})
		};

		this.getPorts; //get next free port for websockets and udp communication
		WsGUI.updateWsPortInFile(wsPort);
		this.startBridge; //to give time
	}

	getPorts {
		wsPort = ("exec" + pythonPath + checkPortPath + "0 TCP").unixCmdGetStdOut.asInteger;
		wsOscPort = ("exec" + pythonPath + checkPortPath + "0 UDP").unixCmdGetStdOut.asInteger;
		// "wsOSC: ".post;
		// wsOscPort.postln;
	}

	checkStaticPort {
		^(("exec" + pythonPath + checkPortPath + wwwPort.asString + "TCP").unixCmdGetStdOut.asInteger > 0);
	}


	startBridge {
		var cmd;
		//starting python socket bridge
		//usage: python ws_osc.py SC_OSC_port, ws_OSC_port, oscPath, ws_port
		cmd = "exec" + pythonPath + "-u" + bridgePath + NetAddr.langPort + wsOscPort + oscPath + wsPort; //-u makes posting possible (makes stdout unbuffered)
		wsPid = cmd.unixCmd({|code, exPid|
			("Bridge stopped, exit code: " ++ code ++ "; cleaning up").postln;
			wsPid = nil;
			this.killWWW;
			this.prCleanup;
		}, suppressPosting.not);

		this.prPrepareGlobalResponders; //needs to be done after starting node, so node doesn't end up binding to osc receive port

		//prepare send port
		scSendNetAddr = NetAddr("localhost", wsOscPort);
	}

	startStaticServer {arg port = 8000;
		var rootPath = wwwPath;
		var cmd;
		if(rootPath[0] == "~", {//it's relative to home directory
			rootPath = rootPath.standardizePath;
		}, {
			if(rootPath[0] != "/", {//it's relative to the class file
				rootPath = File.realpath(this.class.filenameSymbol).dirname ++ "/" ++ rootPath;
			});
		});
		rootPath = rootPath.withoutTrailingSlash.escapeChar($ );
		postf("Starting static www server, root path: %\n", rootPath);
		// cmd = "pushd " ++ rootPath ++ "; exec python -m SimpleHTTPServer " ++ port ++ "; popd";
		cmd = "cd " ++ rootPath ++ "; exec python -m SimpleHTTPServer " ++ port;
		// if(suppressPosting, {
		// 	cmd = cmd + "> /dev/null";
		// });
		// postf("cmd: %\n", cmd);
		wwwPid = cmd.unixCmd({
			"Python www (static) server stopped!".postln;
			wwwPid = nil;
			this.killWS;
		}, postOutput: suppressPosting.not);
		// wwwPipe = Pipe.new(cmd, "w");
	}

	prPrepareGlobalResponders {
		socketsResponder = OSCdef(oscPath, {|msg, time, addr, recvPort|
			var command, hostport, data;
			//command is either 'add', 'remove', or 'data'
			#command, hostport, data= msg[[1, 2, 3]];
			command = command.asSymbol;
			hostport = hostport.asSymbol;
			// postf("command: %\n", command);
			// postf("Message from %\n", hostport);
			// postf("Data: %\n", data);
			// postf("Data present: %\n", dataPresent.asBoolean);
			// msg.postln;
			command.switch(
				\add, {this.addWsClient(hostport)},
				\remove, {this.removeWsClient(hostport)},
				\data, {this.interpretWsData(hostport, data)}
			);
		}, oscPath);
	}

	sendMsg {|dest, msg|
		// "Sending from SC: ".post; [dest, msg].postln;
		scSendNetAddr.sendMsg(dest, msg);
		// scSendNetAddr.sendBundle(0, dest, msg);
	}

	sendMsgToAll {|msg|
		clientDict.keysDo({|thisAddr|
			this.sendMsg(thisAddr, msg);
		});
	}

	prAddObj {|dest, objID|
		this.sendMsg(dest, this.prepareJSONcommandId(\add, objID));
	}

	prAddObjToAll {|objID|
		clientDict.keysDo({|thisAddr|
			this.prAddObj(thisAddr, objID);
		});
	}

	prRemoveObj {|dest, objID|
		this.sendMsg(dest, this.prepareJSONcommandId(\remove, objID));
	}

	prRemoveObjFromAll {|objID|
		clientDict.keysDo({|thisAddr|
			this.prRemoveObj(thisAddr, objID);
		});
	}

	prUpdateObj {|dest, objID, whichKey|
		this.sendMsg(dest, this.prepareJSONcommandId(\update, objID, whichKey));
	}

	prUpdateObjInAll {|objID, whichKey|
		clientDict.keysDo({|thisAddr|
			this.prUpdateObj(thisAddr, objID, whichKey);
		});
	}

	prUpdateObjInAllExcept {|objID, whichKey, exceptAddress|
		clientDict.keysDo({|thisAddr|
			if(thisAddr != exceptAddress, {
				this.prUpdateObj(thisAddr, objID, whichKey);
			});
		});
	}

	prAddAllObj {|dest|
		guiObjects.keysDo({|thisID|
			this.prAddObj(dest, thisID);
		});
	}

	send {|msg| //temp syntax shortcut
		this.sendMsgToAll(msg);
	}

	addWsClient {|hostport|
		clientDict.put(hostport, true);
		this.prAddAllObj(hostport);
	}

	removeWsClient {|hostport|
		clientDict.removeAt(hostport)
	}

	interpretWsData {|hostport, data|
		var objID, value;
		#objID, value = data.asString.split($,);
		objID = objID.asInteger;
		// postf("data % from %\n", data, hostport);
		// postf("object %, value %, from %\n", objID, value, hostport);
		//here goes actual function triggering
		this.prUpdateValue(objID, value, hostport);
	}

	prUpdateValue {|objID, value, hostport|
		// "incoming value: ".post;
		// value.postln;
		if(numericOutputKinds.includes(guiObjects[objID][0][\kind]), {
			// value = guiObjects[objID][2].map(value.asFloat); //convert to float and map controlspec here and
			value = value.asFloat;
		});
		//update value in the dictionary
		guiObjects[objID][0][\value] = value;
		// "value in the dictionary: ".post;
		// guiObjects[objID][0][\value].postln;
		// "guiObjects[objID][0]: ".post;
		// guiObjects[objID][0].postln;
		//broadcast change to other clients, use hostport to avoid feedback
		this.prUpdateObjInAllExcept(objID, \value, hostport);
		//trigger function
		if(guiObjects[objID].notNil, {
			guiObjects[objID][1].value(value);
		});
	}

	// killBridge {
	// 	this.killWS;
	// 	// this.killWWW; //called after stopping WS bridge
	// }

	killWS {
		if(wsPid.notNil, {
			postf("Killing ws_osc server, pid %\n", wsPid);
			("kill" + wsPid).unixCmd;
		}, {
			// "Bridge not running, nothing to kill".postln;
		});
	}

	killWWW {
		if(wwwPid.notNil, {
			postf("Killing www server, pid %\n", wwwPid);
			("kill" + wwwPid).unixCmd;
		}, {
			// "Static server not running, nothing to kill".postln;
		});
	}

	free {
		scSendNetAddr.sendMsg("/quit");
		{this.killWS}.defer(0.4); //if bridge won't close, this will kill it; also waits for any outstanding connections
		// this.prCleanup; //this will get called when ws bridge exits
	}

	prCleanup {
		//also close extra open ports here? probably not necessary
		socketsResponder.free;
		this.removeAllImageLinks; //clean images
		// disconReponder.free;
		// this.clear;
		actionOnClose.value;
	}

	prepareJSONcommandId {|command, id, whichKey| //whichKey used for updating single value
		var str, keyString, valString, newDict;
		// postf("whichKey: %\n", whichKey);
		if(command != \remove, {
			//if whichKey, then choose just one from guiObjects[id][0], otherwise full
			if(whichKey.notNil, {
				if(styleKeys.includes(whichKey), {
					// styleKeys = \style; // wrong
					// "whichKey is in styleKeys".postln;
					newDict = IdentityDictionary.new;
					guiObjects[id][0].keysValuesDo({|key, value, inc|
						if(styleKeys.includes(key), {
							newDict.put(key, value);
						});
					});
				}, {
					newDict = IdentityDictionary.new.put(whichKey, guiObjects[id][0][whichKey]);
				});
			}, {
				newDict = guiObjects[id][0];
			});
		}, {
			newDict = IdentityDictionary.new;//to not have unnecessary params for removing?
		});
		newDict.put(\command, command);
		newDict.put(\id, id);
		// "newDict: ".post;
		// newDict.postln;
		if(command == \remove, {
			str = this.prepareJSON(newDict); //don't go through css for removing
		}, {
			str = this.prepareJSON(this.prepareParametersDict(newDict));
		});
		^str;
	}

	prepareJSON {|dict|
		var str, keyString, valString;
		// dict.postln;
		str = "{";
		dict.keysValuesDo({|key, value, inc|
			keyString = "\"" ++ key.asString ++ "\"";
			// "value: ".post; value.postln;
			valString = "\"" ++ value.asString ++ "\"";
			str = str ++ keyString ++ ":" ++ valString;
			// str.postln;
			if(inc < (dict.size - 1), { //add comma only up to the second to the last item
				str = str ++ ", ";
			});
		});
		str = str ++ "}";
		// "full JSON: ".post;
		// str.postln;
		^str;
	}

	prepareParametersDict {|dict| //this converts values in dictionary to format expected by web browser;
		var preparedDict, styleStr;
		preparedDict = IdentityDictionary.new;
		dict.keysValuesDo({|key, value, inc|
			if(styleKeys.includes(key).not, {
				if(key == \menuItems, {
					preparedDict.put(key, "".ccatList(value).copyToEnd(2));
				}, {
					preparedDict.put(key, value);
				});
			});
		});
		styleStr = this.prepareStyleString(dict);
		if(styleStr.notNil, {
			preparedDict.put(\style, styleStr);
		});
		^preparedDict;
	}

	prepareStyleString {|dict|
		var str, bounds, cssDict, keyString, valString;
		cssDict = IdentityDictionary.new;
		bounds = dict[\bounds];
		bounds !? {
			cssDict.put(\position, "absolute");
			cssDict.put(\left, (bounds.left * 100).asString ++ "%");
			cssDict.put(\top, (bounds.top * 100).asString ++ "%");
			if(bounds.width > 0, {cssDict.put(\width, (bounds.width * 100).asString ++ "%")});
			if(bounds.height > 0, {cssDict.put(\height, (bounds.height * 100).asString ++ "%")});
			if((dict[\kind] == \slider) && (bounds.width < bounds.height), {
				cssDict.put('-webkit-appearance', "slider-vertical");
			}); //auto vertical slider
		};
		// dict[\color] ?? {cssDict.put(\color, dict[\color].hexString)}; //not sure if that is needed... see textColor
		dict[\backgroundColor] !? {cssDict.put('background-color', dict[\backgroundColor].hexString)};
		dict[\textColor] !? {cssDict.put('color', dict[\textColor].hexString)};
		dict[\font] !? {
			dict[\font].size !? {cssDict.put('font-size', dict[\font].size.asString ++ "px")};
			dict[\font].name !? {cssDict.put('font-family', dict[\font].name)};
			//add text-decoration here as well - bold, italic?
		};
		dict[\textAlign] !? {cssDict.put('text-align', dict[\textAlign].asString)};
		// "cssDict: ".post;
		// cssDict.postln;
		//create string
		str = "";
		cssDict.keysValuesDo({|key, value, inc|
			keyString = key.asString;
			valString = value.asString;
			str = str ++ keyString ++ ":" ++ valString ++ ";";
		});
		dict[\css] !? {str = str ++ dict[\css].asString}; //add custom css
		// str = str ++ "}";//why was that?
		// postf("Style string: %\n", str);
		if(cssDict.size > 0, {
			^str;
		}, {
			^nil;
		});
	}

	addWidget {arg name, kind = \button, func = {}, parameters = IdentityDictionary.new, spec = [0, 1].asSpec, sendNow=true;
		var paramsDict, id, okToAddWidget, step;
		if(name.notNil, {
			if(namesToIDs[name].isNil && name.isKindOf(SimpleNumber).not, {
				id = this.prGetCurrentIdAndIncrement;
				namesToIDs.put(name, id);
				okToAddWidget = true;
			}, {
				postf("Widget assigned to name % already exists or you're trying to use SimpleNumber as id, not adding\n", name);
				okToAddWidget = false;
			});
		}, {
			id = this.prGetCurrentIdAndIncrement;
			okToAddWidget = true;
		});

		if(okToAddWidget, {
			paramsDict = IdentityDictionary.new(know: true);
			paramsDict.put(\kind, kind);
			if(parameters.isKindOf(Dictionary), {
				paramsDict = paramsDict.putAll(parameters);
			});
			//here params
			kind.switch(
				\slider, {
					paramsDict.put(\min, 0);
					paramsDict.put(\max, 1);
					paramsDict.put(\value, spec.unmap(spec.default));
					paramsDict.put(\step, \any);
				}, //controlspec used only for slider's creation
				\body, {
					bodyID = id;
				},
				\image, {
					//link for image
					paramsDict[\src] !? {
						var relPath = this.createImageLink(paramsDict[\src], id);
						paramsDict[\src] = relPath;
					};
				}
			);
			guiObjects.put(id, [
				paramsDict,
				func,
				spec
			]);
			//send
			sendNow.if{ this.prAddObjToAll(id) };
		});
		^id;
	}

	createImageLink {|path, id|
		var cmd, relativeImgPath;
		relativeImgPath = "images/" ++ id.asString;
		cmd = "ln -sf " ++ path.escapeChar($ ) + (classPath.dirname ++ "/www/" ++ relativeImgPath).escapeChar($ );
		"Creating symlink: ".post;
		// cmd.postln;
		cmd.unixCmdGetStdOut; //synchronously, so we have the link on time
		^relativeImgPath;
	}

	removeAllImageLinks {
		var cmd;
		cmd = "rm " ++ (classPath.dirname ++ "/www/images/*").escapeChar($ );
		"Removing image links".postln;
		// cmd.postln;
		cmd.unixCmd;
	}

	removeWidget {|idOrName|
		var id;
		if(idOrName.isKindOf(SimpleNumber), {
			id = idOrName;
		}, {
			// "bfore id".postln;
			id = namesToIDs[idOrName];
			// "after id".postln;
			namesToIDs.removeAt(idOrName);
		});
		// postf("id: %\n", id);
		if(guiObjects[id].notNil, {
			guiObjects.removeAt(id);
			// "before remove from all".postln;
			this.prRemoveObjFromAll(id);
		}, {
			postf("No object at id %, not removing\n", id);
		});
	}

	clear {//remove all widgets - convenience method
		this.removeAllWidgets;
	}

	removeAllWidgets {
		guiObjects.keysDo({ |key|
			this.removeWidget(key)
		});
		bodyID = nil;
		titleID = nil;
	}

	updateWidget {|idOrName, whichKey|
		var id;
		if(idOrName.isKindOf(SimpleNumber), {
			id = idOrName;
		}, {
			id = namesToIDs[idOrName];
		});
		if(guiObjects[id].notNil, {
			this.prUpdateObjInAll(id, whichKey);
		}, {
			postf("No object at id %, not updating\n", id);
		});
	}

	prGetCurrentIdAndIncrement {
		var curID;
		curID = curWidgetID;
		curWidgetID = curWidgetID + 1;
		^curID;
	}

	backgroundColor_ {|color|
		if(bodyID.isNil, {
			bodyID = this.addWidget(nil, \body, {},
 parameters: IdentityDictionary.new.put(\backgroundColor, color));
		}, {
			guiObjects[bodyID][0][\backgroundColor] = color;
			this.updateWidget(bodyID);
		});
		// updateWidget(bodyID, \backgroundColor);
		^color;
	}

	backgroundColor {
		if(bodyID.notNil, {
			^guiObjects[bodyID][0][\backgroundColor];
		}, {
			^nil;
		});
	}

	title_ {|title|
		if(titleID.isNil, {
			titleID = this.addWidget(nil, \title, {},
 parameters: IdentityDictionary.new.put(\title, title));
		}, {
			guiObjects[titleID][0][\title] = title;
			this.updateWidget(titleID);
		});
		^title;
	}

	title {
		if(titleID.notNil, {
			^guiObjects[titleID][0][\title];
		}, {
			^nil;
		});
	}

	// make addLayout?
	layout_ { |wsLayout|
		var startX, startY, remHSpace, remVSpace;

		// layouts = layouts.add(wsLayout); // to be added for introspection (i.e. .children)
		wsLayout.bounds.notNil.if(
			{	var bounds;
				bounds = wsLayout.bounds;
				remVSpace = bounds.height;
				remHSpace = bounds.width;
				startX = bounds.x;
				startY = bounds.y;
			},
			// else bounds are full page
			{ remVSpace = remHSpace = 1; startX = startY = 0; }
		);

		postf("laying out in these absolute bounds: %, %, %, %\n", startX, startY, remHSpace, remVSpace);

		wsLayout.isKindOf(WsHLayout).if{
			this.buildHLayout(wsLayout, Rect(startX, startY, remHSpace, remVSpace));
		};
	}

	buildHLayout { |layout, parentBounds| //parX, parY, parW, parH
		var elements, nItems, hSpaces, freeSpace, nilSize;
		var nextX, nextY, parW, parH;

		elements = layout.elements;
		nItems = elements.size;
		hSpaces = elements.collect{|elem|
			if( elem.isKindOf(WsLayout) or: elem.isKindOf(WsWidget),
				{ elem.bounds.notNil.if(
					{ "returning a width hSpace ".post; elem.bounds.width.postln; elem.bounds.width },
					{ "returning nil hSpace".postln; nil; }
					);
				},
				{ elem } // assumed to be either nil or a Number
			);
		};
		hSpaces.postln;
		freeSpace = (1 - hSpaces.select({|width| width.notNil}).sum).clip(0,1);
		nilSize = if(freeSpace != 0, {freeSpace / hSpaces.occurrencesOf(nil)},{0});

		postf( "number of items: %\nhorizontal spaces:%\navailable free space:%\nnilSize: %\n", nItems, hSpaces, freeSpace, nilSize);

		// place the widgets
		nextX = parentBounds.left;
		nextY = parentBounds.top;
		parW = parentBounds.width;
		parH = parentBounds.height;

		elements.do{ |elem, i|
			var elemKind, myBounds, myWidth;
			elemKind = elem.class;
			"iterating through: ".post; elem.postln;
			"next X pos: ".post; nextX.postln;
			"next Y pos: ".post; nextY.postln;

			// if it's a Ws layout or widget, get its height and bounds
			if( elem.notNil and: (elemKind != Number), {
			elem.bounds.notNil.if(
					{	var myHeight;
						myWidth = elem.bounds.width * parW;
						myHeight = elem.bounds.height * parH;
						// ignoring original xy for now
						myBounds = Rect(nextX, nextY, myWidth, myHeight);
					},{
						myWidth =  (hSpaces[i] ?? nilSize) * parW;
						myBounds = Rect(nextX, nextY, myWidth, parH);
				});
			});

			case
			{elemKind == Number} { nextX = nextX + (elem * parW)} // advance the x pointer (empty space)
			{elem.isNil} { nextX = nextX + (nilSize * parW)} // advance the x pointer (empty space)
			{elemKind == WsHLayout}  {
				"found a WsHLayout to lay out: ".post; elem.postln;
				this.buildHLayout(elem, Rect(nextX, nextY, (hSpaces[i] ?? nilSize) * parW, parH));
				nextX = nextX + myWidth;
			}
			{elemKind == WsVLayout} {
				"found a WsVLayout to lay out: ".post; elem.postln;
				this.buildVLayout(elem, Rect(nextX, nextY, (hSpaces[i] ?? nilSize) * parW, parH));
				nextX = nextX + myWidth;
			}
			{ elem.isKindOf(WsWidget) } {
				"found a WsWidget to lay out: ".post; elem.postln;
				"setting widget bounds to: ".post; myBounds.postln;
				elem.bounds_(myBounds);
				elem.addToPage;
				nextX = nextX + myWidth;
			};
		};
	}

}

WsWidget {}

WsSimpleButton : WsWidget {
	var ws, <bounds;
	var <id;

	//	addWidget {arg name, kind = \button, func = {}, parameters = IdentityDictionary.new, spec = [0, 1].asSpec;
	*new {|wsGUI, bounds|
		^super.newCopyArgs(wsGUI, bounds).addAndSend;
	}

	// doesn't send to page, just inits the object
	// TODO: change so wsGUI isn't necessary at this stage
	*init { |wsGUI, bounds|
		^super.newCopyArgs(wsGUI, bounds).addDontSend;
	}

	addAndSend {
		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		id = ws.addWidget(nil, \button, {}, IdentityDictionary.new.put(\bounds, bounds), sendNow: true);
	}

	addDontSend {
		id = ws.addWidget(
			nil, \button, {},
			IdentityDictionary.new.put(\bounds, bounds ?? Rect(0, 0, 0.1, 0.1)),
			sendNow: false
		);
	}

	// useful only if the widget is instantiated but hasn't been sent to the page yet
	bounds_ { |boundsRect|
		bounds = boundsRect ?? Rect(0, 0, 0.1, 0.1);
		ws.guiObjects[id][0][\bounds] = bounds;
	}

	addToPage { ws.prAddObjToAll(id) }

	/*init {
		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		id = ws.addWidget(nil, \button, {}, IdentityDictionary.new.put(\bounds, bounds));
	}*/

	action_ {|function|
		^ws.guiObjects[id][1] = function;
	}

	action {
		^ws.guiObjects[id][1];
	}

	backgroundColor_ {|color|
		if(ws.guiObjects[id][0][\backgroundColor].isNil, {
			ws.guiObjects[id][0].put(\backgroundColor, color);
		}, {
			ws.guiObjects[id][0][\backgroundColor] = color;
		});
		ws.updateWidget(id, \backgroundColor);
		// ^color;
	}

	backgroundColor {
		^ws.guiObjects[id][0][\backgroundColor];
	}

	textColor_ {|color|
		if(ws.guiObjects[id][0][\textColor].isNil, {
			ws.guiObjects[id][0].put(\textColor, color);
		}, {
			ws.guiObjects[id][0][\textColor] = color;
		});
		ws.updateWidget(id, \textColor);
		// ^color;
	}

	textColor {
		^ws.guiObjects[id][0][\textColor];
	}

	font_ {|font|
		if(ws.guiObjects[id][0][\font].isNil, {
			ws.guiObjects[id][0].put(\font, font);
		}, {
			ws.guiObjects[id][0][\font] = font;
		});
		ws.updateWidget(id, \textColor);
		// ^font;
	}

	font {
		^ws.guiObjects[id][0][\font];
	}

	textAlign_ {|align|
 		if(ws.guiObjects[id][0][\textAlign].isNil, {
			ws.guiObjects[id][0].put(\textAlign, align);
		}, {
			ws.guiObjects[id][0][\textAlign] = align;
		});
		ws.updateWidget(id, \textAlign);
		// ^align;
	}

	textAlign {
		^ws.guiObjects[id][0][\textAlign];
	}

	css_ {|cssString|
 		if(ws.guiObjects[id][0][\css].isNil, {
			ws.guiObjects[id][0].put(\css, cssString);
		}, {
			ws.guiObjects[id][0][\css] = cssString;
		});
		ws.updateWidget(id, \css);
		// ^cssString;
	}

	css {
		^ws.guiObjects[id][0][\css];
	}

	controlSpec_ {|spec| //how to update dictionary with min and max on change?
		^ws.guiObjects[id][2] = spec;
	}

	controlSpec {
		^ws.guiObjects[id][2];
	}

	string_ {|thisString|
		thisString = thisString.replace("\n", "<br>");//convert newline for html
 		if(ws.guiObjects[id][0][\innerHTML].isNil, {
			ws.guiObjects[id][0].put(\innerHTML, thisString);
		}, {
			ws.guiObjects[id][0][\innerHTML] = thisString;
		});
		ws.updateWidget(id, \innerHTML);
		// ^thisString;
	}

	string {
		^ws.guiObjects[id][0][\innerHTML];
	}

	remove {
		ws.removeWidget(id);
	}
}

WsButton : WsSimpleButton {
	// var ws, <bounds;
	// var <id;
	var id, <value = 0, <numStates = 0, <states, <>action;

	*new {|wsGUI, bounds|
		^super.newCopyArgs(wsGUI, bounds).init;
	}

	init {
		// bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		// id = ws.addWidget(nil, \button, {}, IdentityDictionary.new.put(\bounds, bounds));
		super.init;
		id = super.id;
		action = {};
		super.action_({
			value = (value + 1) % numStates;
			this.prUpdateStringAndColors;
			action.value(this);
		});
	}

	string_ {
	}

	string {
	}

	states_ {|statesArray|
		states = statesArray;
		numStates = states.size;
		this.prUpdateStringAndColors;
	}

	value_ {|val|
		value = val;
		this.prUpdateStringAndColors;
		^value;
	}

	item {
		^states[value][0];
	}

	valueAction_ {|val|
		if(val != value, {
			value = val;
			this.prUpdateStringAndColors;
			action.value(value);
			^val;
		});
	}

	backgroundColor_ {
	}

	textColor_ {
	}

	prUpdateStringAndColors {
		super.string_(states[value][0]);
		super.textColor_(states[value][1]);
		super.backgroundColor_(states[value][2]);
	}
}

WsStaticText : WsWidget{
	var ws, <bounds;
	var <id;

	//	addWidget {arg name, kind = \button, func = {}, parameters = IdentityDictionary.new, spec = [0, 1].asSpec;
	*new {|wsGUI, bounds|
		^super.newCopyArgs(wsGUI, bounds).init;
	}

	init {
		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		id = ws.addWidget(nil, \text, {}, IdentityDictionary.new.put(\bounds, bounds));
	}

	action_ {|function|
		^ws.guiObjects[id][1] = function;
	}

	action {
		^ws.guiObjects[id][1];
	}

	backgroundColor_ {|color|
		if(ws.guiObjects[id][0][\backgroundColor].isNil, {
			ws.guiObjects[id][0].put(\backgroundColor, color);
		}, {
			ws.guiObjects[id][0][\backgroundColor] = color;
		});
		ws.updateWidget(id, \backgroundColor);
		// ^color;
	}

	backgroundColor {
		^ws.guiObjects[id][0][\backgroundColor];
	}

	textColor_ {|color|
		if(ws.guiObjects[id][0][\textColor].isNil, {
			ws.guiObjects[id][0].put(\textColor, color);
		}, {
			ws.guiObjects[id][0][\textColor] = color;
		});
		ws.updateWidget(id, \textColor);
		// ^color;
	}

	textColor {
		^ws.guiObjects[id][0][\textColor];
	}

	font_ {|font|
		if(ws.guiObjects[id][0][\font].isNil, {
			ws.guiObjects[id][0].put(\font, font);
		}, {
			ws.guiObjects[id][0][\font] = font;
		});
		ws.updateWidget(id, \font);
		// ^font;
	}

	font {
		^ws.guiObjects[id][0][\font];
	}

	textAlign_ {|align|
 		if(ws.guiObjects[id][0][\textAlign].isNil, {
			ws.guiObjects[id][0].put(\textAlign, align);
		}, {
			ws.guiObjects[id][0][\textAlign] = align;
		});
		ws.updateWidget(id, \textAlign);
		// ^align;
	}

	textAlign {
		^ws.guiObjects[id][0][\textAlign];
	}

	css_ {|cssString|
 		if(ws.guiObjects[id][0][\css].isNil, {
			ws.guiObjects[id][0].put(\css, cssString);
		}, {
			ws.guiObjects[id][0][\css] = cssString;
		});
		ws.updateWidget(id, \css);
		// ^cssString;
	}

	css {
		^ws.guiObjects[id][0][\css];
	}

	controlSpec_ {|spec|
		^ws.guiObjects[id][2] = spec;
	}

	controlSpec {
		^ws.guiObjects[id][2];
	}

	string_ {|thisString|
		thisString = thisString.replace("\n", "<br>");//convert newline for html
 		if(ws.guiObjects[id][0][\innerHTML].isNil, {
			ws.guiObjects[id][0].put(\innerHTML, thisString);
		}, {
			ws.guiObjects[id][0][\innerHTML] = thisString;
		});
		ws.updateWidget(id, \innerHTML);
		// ^thisString;
	}

	string {
		^ws.guiObjects[id][0][\innerHTML];
	}

	remove {
		ws.removeWidget(id);
	}
}

WsImage : WsWidget{
	var ws, <bounds, <path;
	var <id;

	*new {|wsGUI, bounds, path|
		^super.newCopyArgs(wsGUI, bounds, path).init;
	}

	init {
		var paramsDict;
		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		paramsDict = IdentityDictionary.new.put(\bounds, bounds);
		path !? {paramsDict.put(\src, path)};
		id = ws.addWidget(nil, \image, {}, paramsDict);
	}

	path_ {|newPath, isURL = false|
		var relPath;
		path = newPath;
		if(isURL, {
			relPath = newPath;
		}, {
			relPath = ws.createImageLink(newPath, id);
		});
		if(ws.guiObjects[id][0][\src].isNil, {
			ws.guiObjects[id][0].put(\src, relPath);
		}, {
			ws.guiObjects[id][0][\src] = relPath;
		});
		ws.updateWidget(id, \src);
	}

	action_ {|function|
		^ws.guiObjects[id][1] = function;
	}

	action {
		^ws.guiObjects[id][1];
	}

	css_ {|cssString|
 		if(ws.guiObjects[id][0][\css].isNil, {
			ws.guiObjects[id][0].put(\css, cssString);
		}, {
			ws.guiObjects[id][0][\css] = cssString;
		});
		ws.updateWidget(id, \css);
		// ^cssString;
	}

	css {
		^ws.guiObjects[id][0][\css];
	}

	controlSpec_ {|spec|
		^ws.guiObjects[id][2] = spec;
	}

	controlSpec {
		^ws.guiObjects[id][2];
	}

	remove {
		ws.removeWidget(id);
	}
}

WsSlider : WsWidget {
	var ws, <bounds;
	var <id;
	var function, prFunction;

	//	addWidget {arg name, kind = \button, func = {}, parameters = IdentityDictionary.new, spec = [0, 1].asSpec;
	*new {|wsGUI, bounds|
		^super.newCopyArgs(wsGUI, bounds).init;
	}

	init {
		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		id = ws.addWidget(nil, \slider, {}, IdentityDictionary.new.put(\bounds, bounds));
		function = {};
		prFunction = {
			function.value(this);
		};
		ws.guiObjects[id][1] = prFunction;
	}

	action_ {|func|
		// ^ws.guiObjects[id][1] = function;
		^function = func;
	}

	action {
		// ^ws.guiObjects[id][1];
		^function;
	}

	// backgroundColor_ {|color|
	// 	if(ws.guiObjects[id][0][\backgroundColor].isNil, {
	// 		ws.guiObjects[id][0].put(\backgroundColor, color);
	// 	}, {
	// 		ws.guiObjects[id][0][\backgroundColor] = color;
	// 	});
	// 	ws.updateWidget(id);
	// 	// ^color;
	// }

	// backgroundColor {
	// 	^ws.guiObjects[id][0][\backgroundColor];
	// }

	css_ {|cssString|
 		if(ws.guiObjects[id][0][\css].isNil, {
			ws.guiObjects[id][0].put(\css, cssString);
		}, {
			ws.guiObjects[id][0][\css] = cssString;
		});
		ws.updateWidget(id, \css);
		// ^cssString;
	}

	css {
		^ws.guiObjects[id][0][\css];
	}

	// controlSpec_ {|spec|
	// 	^ws.guiObjects[id][2] = spec;
	// }

	// controlSpec {
	// 	^ws.guiObjects[id][2];
	// }

	value_ {|val|
 		if(ws.guiObjects[id][0][\value].isNil, {
			ws.guiObjects[id][0].put(\value, ws.guiObjects[id][2].unmap(val)); //should not unmap here
		}, {
			ws.guiObjects[id][0][\value] = ws.guiObjects[id][2].unmap(val);
		});
		ws.updateWidget(id, \value);
		^val;
	}

	value {
		^ws.guiObjects[id][2].map(ws.guiObjects[id][0][\value]);
	}

	valueAction_ {|val|
		this.value_(val);
		function.value(this);
		^val;
	}

	remove {
		ws.removeWidget(id);
	}

}

WsEZSlider : WsWidget { //this should later be implemented as call to WsSlider and WsStaticText for label and value
	var ws, <bounds;
	var <id;
	var function, prFunction;

	*new {|wsGUI, bounds|
		^super.newCopyArgs(wsGUI, bounds).init;
	}

	init {
		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		id = ws.addWidget(nil, \slider, {}, IdentityDictionary.new.put(\bounds, bounds));
		function = {};
		prFunction = {
			function.value(this);
		};
		ws.guiObjects[id][1] = prFunction;
	}

	action_ {|func|
		// ^ws.guiObjects[id][1] = function;
		^function = func;
	}

	action {
		// ^ws.guiObjects[id][1];
		^function;
	}

	// backgroundColor_ {|color|
	// 	if(ws.guiObjects[id][0][\backgroundColor].isNil, {
	// 		ws.guiObjects[id][0].put(\backgroundColor, color);
	// 	}, {
	// 		ws.guiObjects[id][0][\backgroundColor] = color;
	// 	});
	// 	ws.updateWidget(id);
	// 	// ^color;
	// }

	// backgroundColor {
	// 	^ws.guiObjects[id][0][\backgroundColor];
	// }

	css_ {|cssString|
 		if(ws.guiObjects[id][0][\css].isNil, {
			ws.guiObjects[id][0].put(\css, cssString);
		}, {
			ws.guiObjects[id][0][\css] = cssString;
		});
		ws.updateWidget(id, \css);
		// ^cssString;
	}

	css {
		^ws.guiObjects[id][0][\css];
	}

	controlSpec_ {|spec| //how to update dictionary with min and max on change?
		^ws.guiObjects[id][2] = spec;
	}

	controlSpec {
		^ws.guiObjects[id][2];
	}

	value_ {|val|
 		if(ws.guiObjects[id][0][\value].isNil, {
			ws.guiObjects[id][0].put(\value, ws.guiObjects[id][2].unmap(val));
		}, {
			ws.guiObjects[id][0][\value] = ws.guiObjects[id][2].unmap(val);
		});
		ws.updateWidget(id, \value);
		^val;
	}

	value {
		^ws.guiObjects[id][2].map(ws.guiObjects[id][0][\value]);
	}

	valueAction_ {|val|
		this.value_(val);
		function.value(this);
		^val;
	}

	remove {
		ws.removeWidget(id);
	}

}

WsPopUpMenu : WsWidget {
	var ws, <bounds;
	var <id;
	var function, prFunction;

	//	addWidget {arg name, kind = \button, func = {}, parameters = IdentityDictionary.new, spec = [0, 1].asSpec;
	*new {|wsGUI, bounds|
		^super.newCopyArgs(wsGUI, bounds).init;
	}

	init {
		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		id = ws.addWidget(nil, \menu, {}, IdentityDictionary.new.put(\bounds, bounds));
		function = {};
		prFunction = {
			function.value(this);
		};
		ws.guiObjects[id][1] = prFunction;
	}

	items_ {|itemArr|
		// thisString = thisString.replace("\n", "<br>");//convert newline for html
		// itemString = "".ccatList(itemArr).copyToEnd(2)
 		if(ws.guiObjects[id][0][\menuItems].isNil, {
			ws.guiObjects[id][0].put(\menuItems, itemArr);
		}, {
			ws.guiObjects[id][0][\menuItems] = itemArr;
		});
		ws.updateWidget(id);
		//init value to the first item
		this.value_(0);
		// ^thisString;
	}

	items {
		^ws.guiObjects[id][0][\menuItems];
	}

	action_ {|func|
		// ^ws.guiObjects[id][1] = function;
		^function = func;
	}

	action {
		// ^ws.guiObjects[id][1];
		^function;
	}

	backgroundColor_ {|color|
		if(ws.guiObjects[id][0][\backgroundColor].isNil, {
			ws.guiObjects[id][0].put(\backgroundColor, color);
		}, {
			ws.guiObjects[id][0][\backgroundColor] = color;
		});
		ws.updateWidget(id, \backgroundColor);
		// ^color;
	}

	backgroundColor {
		^ws.guiObjects[id][0][\backgroundColor];
	}

	textColor_ {|color|
		if(ws.guiObjects[id][0][\textColor].isNil, {
			ws.guiObjects[id][0].put(\textColor, color);
		}, {
			ws.guiObjects[id][0][\textColor] = color;
		});
		ws.updateWidget(id, \textColor);
		// ^color;
	}

	textColor {
		^ws.guiObjects[id][0][\textColor];
	}

	font_ {|font|
		if(ws.guiObjects[id][0][\font].isNil, {
			ws.guiObjects[id][0].put(\font, font);
		}, {
			ws.guiObjects[id][0][\font] = font;
		});
		ws.updateWidget(id, \font);
		// ^font;
	}

	font {
		^ws.guiObjects[id][0][\font];
	}

	textAlign_ {|align|
 		if(ws.guiObjects[id][0][\textAlign].isNil, {
			ws.guiObjects[id][0].put(\textAlign, align);
		}, {
			ws.guiObjects[id][0][\textAlign] = align;
		});
		ws.updateWidget(id, \textAlign);
		// ^align;
	}

	textAlign {
		^ws.guiObjects[id][0][\textAlign];
	}

	css_ {|cssString|
 		if(ws.guiObjects[id][0][\css].isNil, {
			ws.guiObjects[id][0].put(\css, cssString);
		}, {
			ws.guiObjects[id][0][\css] = cssString;
		});
		ws.updateWidget(id, \css);
		// ^cssString;
	}

	css {
		^ws.guiObjects[id][0][\css];
	}

	// controlSpec_ {|spec| //how to update dictionary with min and max on change?
	// 	^ws.guiObjects[id][2] = spec;
	// }

	// controlSpec {
	// 	^ws.guiObjects[id][2];
	// }

	value_ {|val|
 		if(ws.guiObjects[id][0][\value].isNil, {
			ws.guiObjects[id][0].put(\value, val);
		}, {
			ws.guiObjects[id][0][\value] = val;
		});
		ws.updateWidget(id, \value);
		^val;
	}

	value {
		^ws.guiObjects[id][0][\value].asInteger;
	}

	valueAction_ {|val|
		this.value_(val);
		function.value(this);
		^val;
	}

	// item_ {|itemString|
	// 	^this.value(ws.guiObjects[id][0][\menuItems].indexOf(itemString)); //that would require using only symbols or iteration... leaving out for now
	// }

	item {
		^ws.guiObjects[id][0][\menuItems][ws.guiObjects[id][0][\value].asInteger];
	}

	remove {
		ws.removeWidget(id);
	}
}

WsCheckbox : WsWidget {
	var ws, <bounds;
	var <id;
	var function, prFunction;

	//	addWidget {arg name, kind = \button, func = {}, parameters = IdentityDictionary.new, spec = [0, 1].asSpec;
	*new {|wsGUI, bounds|
		^super.newCopyArgs(wsGUI, bounds).init;
	}

	init {
		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
		id = ws.addWidget(nil, \checkbox, {}, IdentityDictionary.new.put(\bounds, bounds));
		function = {};
		prFunction = {
			function.value(this);
		};
		ws.guiObjects[id][1] = prFunction;
	}

	action_ {|func|
		// ^ws.guiObjects[id][1] = function;
		^function = func;
	}

	action {
		// ^ws.guiObjects[id][1];
		^function;
	}

	backgroundColor_ {|color|
		if(ws.guiObjects[id][0][\backgroundColor].isNil, {
			ws.guiObjects[id][0].put(\backgroundColor, color);
		}, {
			ws.guiObjects[id][0][\backgroundColor] = color;
		});
		ws.updateWidget(id);
		// ^color;
	}

	backgroundColor {
		^ws.guiObjects[id][0][\backgroundColor];
	}

	css_ {|cssString|
 		if(ws.guiObjects[id][0][\css].isNil, {
			ws.guiObjects[id][0].put(\css, cssString);
		}, {
			ws.guiObjects[id][0][\css] = cssString;
		});
		ws.updateWidget(id, \css);
		// ^cssString;
	}

	css {
		^ws.guiObjects[id][0][\css];
	}

	controlSpec_ {|spec| //how to update dictionary with min and max on change?
		^ws.guiObjects[id][2] = spec;
	}

	controlSpec {
		^ws.guiObjects[id][2];
	}

	value_ {|val|
 		if(ws.guiObjects[id][0][\checked].isNil, {
			ws.guiObjects[id][0].put(\checked, val);
		}, {
			ws.guiObjects[id][0][\checked] = val;
		});
		ws.guiObjects[id][0][\value] = val; //hack... since html object responds to \checked, but we store value in \value
		ws.updateWidget(id, \checked);
		^val;
	}

	value {
		^ws.guiObjects[id][0][\value];
	}

	valueAction_ {|val|
		this.value_(val);
		function.value(this);
		^val;
	}

	remove {
		ws.removeWidget(id);
	}
}

/* -------------------
	Layouts
*/
WsLayout {}

WsHLayout : WsLayout {
	// copyArgs
	var <bounds, <elements;

	*new { |bounds ... elements |
		^super.newCopyArgs(bounds, elements)
	}

}

WsVLayout : WsLayout {
	// copyArgs
	var <bounds, <elements;

	*new { |bounds ... elements |
		^super.newCopyArgs(bounds, elements)
	}

}


// WsEZCheckbox { //this should be implemented using its own div; possibly checkbox better as well
// 	var ws, <bounds, <checkboxWidth, label;
// 	var checkboxBounds, labelBounds;
// 	var <checkboxObject, <labelObject;

// 	*new {|wsGUI, bounds, checkboxWidth = 0.2|
// 		^super.newCopyArgs(wsGUI, bounds, checkboxWidth).init;
// 	}

// 	init {
// 		bounds ?? {bounds = Rect(0, 0, 0.1, 0.1)};
// 		checkboxBounds = Rect(bounds.left, bounds.top, bounds.width * checkboxWidth, bounds.height);
// 		labelBounds = Rect(bounds.width * checkboxWidth, bounds.top, bounds.width * (1-checkboxWidth), bounds.height);
// 		checkboxObject = WsCheckbox.new(ws, checkboxBounds);
// 		labelObject = WsStaticText.new(ws, labelBounds);
// 	}

// 	action_ {|function|
// 		^checkboxObject.action_(function);
// 	}

// 	action {
// 		^checkboxObject.action;
// 	}

// 	label_ {|string|
// 		^labelObject.string_(string);
// 	}

// 	label {
// 		^labelObject.string;
// 	}

// 	backgroundColor_ {|color|
// 		^labelObject.backgroundColor_(color);
// 		// ^color;
// 	}

// 	backgroundColor {
// 		^labelObject.backgroundColor;
// 	}

// 	textColor_ {|color|
// 		^labelObject.textColor_(color);
// 	}

// 	textColor {
// 		^labelObject.textColor;
// 	}

// 	font_ {|font|
// 		^labelObject.font_(font);
// 	}

// 	font {
// 		^labelObject.font;
// 	}

// 	textAlign_ {|align|
//  		^labelObject.textAlign_(align);
// 	}

// 	textAlign {
// 		^labelObject.textAlign;
// 	}

// 	css_ {|cssString|
//  		^labelObject.css_(cssString)
// 	}

// 	css {
// 		^labelObject.css
// 	}

// 	// controlSpec_ {|spec| //how to update dictionary with min and max on change?
// 	// 	^ws.guiObjects[id][2] = spec;
// 	// }

// 	// controlSpec {
// 	// 	^ws.guiObjects[id][2];
// 	// }

// 	value_ {|val|
//  		^checkboxObject.value_(val);
// 	}

// 	value {
//  		^checkboxObject.value;
// 	}

// 	remove {
// 		checkboxObject.remove;
// 		labelObject.remove;
// 	}
// }