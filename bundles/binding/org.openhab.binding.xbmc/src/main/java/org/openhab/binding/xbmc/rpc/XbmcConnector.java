/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.binding.xbmc.rpc;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.openhab.binding.xbmc.internal.XbmcHost;
import org.openhab.binding.xbmc.rpc.calls.FilesPrepareDownload;
import org.openhab.binding.xbmc.rpc.calls.GUIShowNotification;
import org.openhab.binding.xbmc.rpc.calls.PlayerGetActivePlayers;
import org.openhab.binding.xbmc.rpc.calls.PlayerGetItem;
import org.openhab.binding.xbmc.rpc.calls.PlayerPlayPause;
import org.openhab.binding.xbmc.rpc.calls.PlayerStop;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.StringType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Realm;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.providers.netty.NettyAsyncHttpProvider;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;

/**
 * Manages the web socket connection for a single XBMC instance.
 * 
 * @author tlan, Ben Jones
 * @since 1.5.0
 */
public class XbmcConnector {

	private static final Logger logger = LoggerFactory.getLogger(XbmcConnector.class);

	// the XBMC instance and openHAB event publisher handles
	private final XbmcHost xbmc;
	private final EventPublisher eventPublisher;

	private final String rsUri;
	private final String wsUri;

	// stores which property is associated with each item
	private final Map<String, String> watches = new HashMap<String, String>();

	// the async connection to the XBMC instance
	private AsyncHttpClient client;
	private WebSocket webSocket;

	/**
	 * @param xbmc
	 *            The host to connect to. Give a reachable hostname or ip
	 *            address, without protocol or port
	 * @param eventPublisher
	 *            EventPublisher to push out state updates
	 */
	public XbmcConnector(XbmcHost xbmc, EventPublisher eventPublisher) {
		this.xbmc = xbmc;
		this.eventPublisher = eventPublisher;

		rsUri = String.format("http://%s:%d/jsonrpc", xbmc.getHostname(), xbmc.getPort());
		wsUri = String.format("ws://%s:%d/jsonrpc", xbmc.getHostname(), xbmc.getWSPort());
	}

	/***
	 * Check if the connection to the XBMC instance is active
	 * 
	 * @return true if an active connection to the XBMC instance exists, false otherwise
	 */
	public boolean isOpen() { 
		return webSocket != null && webSocket.isOpen();
	}
	
	/**
	 * Attempts to create a connection to the XBMC host and begin listening
	 * for updates over the async http web socket
	 *  
	 * @throws URISyntaxException
	 *             If the result of adding protocol and port to the hostname is
	 *             not a valid uri
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	public void open() throws URISyntaxException, IOException, InterruptedException, ExecutionException {
		AsyncHttpClientConfig config = createAsyncHttpClientConfig();
		WebSocketUpgradeHandler handler = createWebSocketHandler();
		
		client = new AsyncHttpClient(new NettyAsyncHttpProvider(config));
		webSocket = client.prepareGet(wsUri).execute(handler).get();
	}

	/***
	 * Close this connection to the XBMC instance
	 */
	public void close() {
		if (webSocket != null) 
			webSocket.close();
		if (client != null) 
			client.close();
	}
		
	private AsyncHttpClientConfig createAsyncHttpClientConfig() {
		Builder builder = new AsyncHttpClientConfig.Builder();
		builder.setRealm(createRealm());
		return builder.build();
	}

	private Realm createRealm() {
		Realm.RealmBuilder builder = new Realm.RealmBuilder();
		builder.setPrincipal(xbmc.getUsername());
		builder.setPassword(xbmc.getPassword());
		builder.setUsePreemptiveAuth(true);
		builder.setScheme(AuthScheme.BASIC);		
		return builder.build();
	}

	private WebSocketUpgradeHandler createWebSocketHandler() {
		WebSocketUpgradeHandler.Builder builder = new WebSocketUpgradeHandler.Builder();
		builder.addWebSocketListener(new XbmcWebSocketListener());
		return builder.build(); 
	}
	
	class XbmcWebSocketListener implements WebSocketTextListener {
		private final ObjectMapper mapper = new ObjectMapper();

		@Override
		public void onOpen(WebSocket webSocket) {
			logger.debug("Websocket opened on {}:{}", xbmc.getHostname(), xbmc.getWSPort());
			try {
				requestPlayerStatusUpdate();
			} catch (Exception e) {
				logger.error("Error requesting player status update", e);
			}
		}
		
		@Override
		public void onError(Throwable e) {
			if (e instanceof ConnectException) {
				logger.debug("Websocket connection error");
			} else if (e instanceof TimeoutException) {
				logger.debug("Websocket timeout error");
			} else {
				logger.error("Websocket error", e);
			}
		}
		
		@Override
		public void onClose(WebSocket webSocket) {
			logger.warn("Websocket closed on {}:{}", xbmc.getHostname(), xbmc.getWSPort());
			webSocket = null;
		}
		
		@Override
		@SuppressWarnings("unchecked")
		public void onMessage(String message) {
			 Map<String, Object> json;
			 try {
				 json = mapper.readValue(message, Map.class);
			 } catch (JsonParseException e) {
				 logger.error("Error parsing JSON:\n" + message);
				 return;
			 } catch (JsonMappingException e) {
				 logger.error("Error mapping JSON:\n" + message);
				 return;
			 } catch (IOException e) {
				 logger.error("An I/O error occured while decoding JSON:\n" + message);
				 return;
			 }

			 // We only care about certain notifications on the websocket
			 // feed, since all our actual data fetching is done via http
			 try {
				 if (json.containsKey("method")) {
					String method = (String)json.get("method");
					if (method.startsWith("Player.On")) {
						processPlayerStateChanged(method, json);
					}
				 }
			 } catch (Exception e) {
				 logger.error("Error handling player state change message", e);
			 }
		}
		
		 @Override
		public void onFragment(String fragment, boolean last) {
		}
	}
	
	/**
	 * Create a mapping between an item and an xbmc property
	 * 
	 * @param itemName
	 *            The name of the item which should receive updates
	 * @param property
	 *            The property of this xbmc instance, which is to be 
	 *            watched for changes
	 * 
	 */
	public void addItem(String itemName, String property) {
		if (!watches.containsKey(itemName)) {
			watches.put(itemName, property);

			// Request a player update, so maybe we can fill in whatever our new
			// item cares about
			if (isOpen()) {
				requestPlayerStatusUpdate();
			}
		}
	}

	private void requestPlayerStatusUpdate() {
		PlayerGetActivePlayers activePlayers = new PlayerGetActivePlayers(client, rsUri);
		activePlayers.execute();

		if (activePlayers.isPlaying()) {
			updateWatch("Player.State", "Play");
			updateWatch("Player.Type", activePlayers.getPlayerType());
			requestPlayerUpdate(activePlayers.getPlayerId());
		} else {
			updateWatch("Player.State", "Stop");
			updateWatch("Player.Title", "");
			updateWatch("Player.ShowTitle", "");
			updateWatch("Player.Fanart", "");
		}
	}

	private void processPlayerStateChanged(String method, Map<String, Object> json) {
		if ("Player.OnPlay".equals(method)) {
			updateWatch("Player.State", "Play");

			Map<String, Object> params = RpcCall.getMap(json, "params");
			Map<String, Object> data = RpcCall.getMap(params, "data");
			Map<String, Object> player = RpcCall.getMap(data, "player");
			Integer playerId = (Integer)player.get("playerid");			
			requestPlayerUpdate(playerId);
		}

		if ("Player.OnPause".equals(method)) {
			updateWatch("Player.State", "Pause");
		}

		if ("Player.OnStop".equals(method)) {
			updateWatch("Player.State", "Stop");
			updateWatch("Player.Title", "");
			updateWatch("Player.ShowTitle", "");
			updateWatch("Player.Fanart", "");
		}
	}

	private void requestPlayerUpdate(int playerId) {
		PlayerGetItem item = new PlayerGetItem(client, rsUri);
		item.setPlayerId(playerId);
		item.execute();

		updateWatch("Player.Title", item.getTitle());
		updateWatch("Player.ShowTitle", item.getShowtitle());
//		updateWatch("Player.Type", activePlayers.getPlayerType());

		if (!StringUtils.isEmpty(item.getFanart())) {
			FilesPrepareDownload fanart = new FilesPrepareDownload(client, rsUri);
			fanart.setImagePath(item.getFanart());
			fanart.execute();
			updateWatch("Player.Fanart", String.format("http://%s:%d/%s", xbmc.getHostname(), xbmc.getPort(), fanart.getPath()));
		}
	}

	public void showNotification(String title, String message) {
		GUIShowNotification showNotification = new GUIShowNotification(client, rsUri);
		showNotification.setTitle(title);
		showNotification.setMessage(message);
		showNotification.execute();
	}
	
	public void playerPlayPause() {
		PlayerGetActivePlayers activePlayers = new PlayerGetActivePlayers(client, rsUri);
		activePlayers.execute();

		PlayerPlayPause playPause = new PlayerPlayPause(client, rsUri);
		playPause.setPlayerId(activePlayers.getPlayerId());
		playPause.execute();
	}
	
	public void playerStop() {
		PlayerGetActivePlayers activePlayers = new PlayerGetActivePlayers(client, rsUri);
		activePlayers.execute();

		PlayerStop stop = new PlayerStop(client, rsUri);
		stop.setPlayerId(activePlayers.getPlayerId());
		stop.execute();
	}
	
	private void updateWatch(String watch, String value) {
		for (Entry<String, String> e : watches.entrySet()) {
			String item = e.getKey();
			String elem = e.getValue();
			if (watch.equals(elem)) {
				eventPublisher.postUpdate(item, new StringType(value));
			}
		}
	}
}
