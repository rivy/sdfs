/*******************************************************************************
 * Copyright (C) 2016 Sam Silverberg sam.silverberg@gmail.com	
 *
 * This file is part of OpenDedupe SDFS.
 *
 * OpenDedupe SDFS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * OpenDedupe SDFS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.opendedup.sdfs.mgmt.websocket;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.opendedup.hashing.HashFunctions;
import org.opendedup.logging.SDFSLogger;
import org.opendedup.sdfs.Main;
import org.opendedup.util.RandomGUID;
import org.simpleframework.http.Request;
import org.simpleframework.http.socket.DataFrame;
import org.simpleframework.http.socket.Frame;
import org.simpleframework.http.socket.FrameChannel;
import org.simpleframework.http.socket.FrameListener;
import org.simpleframework.http.socket.FrameType;
import org.simpleframework.http.socket.Reason;
import org.simpleframework.http.socket.Session;
import org.simpleframework.http.socket.service.Service;

public class PingService implements Service, Runnable {
	private final PingListener listener;
	private final Map<String, FrameChannel> sockets;
	private final Set<String> users;

	public PingService() {
		sockets = new ConcurrentHashMap<String, FrameChannel>();
		listener = new PingListener(this);
		this.users = new CopyOnWriteArraySet<String>();
		Thread th = new Thread(this);
		th.start();
	}

	@Override
	public void connect(Session connection) {
		FrameChannel socket = connection.getChannel();
		Request req = connection.getRequest();
		String password = req.getQuery().get("password");
		String vol = req.getQuery().get("volumeid");
		boolean auth = false;
		if (vol == null) {
			vol = RandomGUID.getGuid();
		}
		if (Main.sdfsCliRequireAuth) {
			if (password != null) {
				String hash;
				try {
					hash = HashFunctions.getSHAHash(password.trim().getBytes(),
							Main.sdfsPasswordSalt.getBytes());
					if (hash.equals(Main.sdfsPassword)) {
						auth = true;
					}
				} catch (NoSuchAlgorithmException
						| UnsupportedEncodingException
						| NoSuchProviderException e) {
					SDFSLogger.getLog().error("unable to authenitcate user", e);
				}

			} else {
				SDFSLogger.getLog().warn("could not authenticate user to cli");
			}
		} else {
			auth = true;
		}
		if (auth == true) {
			try {
				socket.register(listener);
				join(vol, socket);
			} catch (Exception e) {
				SDFSLogger.getLog().error("unable to connect", e);
			}
		} else
			try {
				connection.getChannel().close();
			} catch (IOException e) {
				SDFSLogger.getLog().error("unable to close connection", e);
			}

	}

	public void join(String user, FrameChannel operation) {
		users.add(user);
		sockets.put(user, operation);
	}

	public void leave(String user, FrameChannel operation) {
		sockets.remove(user);
		users.remove(user);
	}

	public void distribute(Frame frame) {
		try {
			for (String user : users) {
				FrameChannel operation = sockets.get(user);

				try {

					operation.send(frame);
					SDFSLogger.getLog().info("sent " + frame.getText());
				} catch (Exception e) {
					SDFSLogger.getLog().error("unable to send message", e);
					sockets.remove(user);
					users.remove(user);
					operation.close();

				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static class PingListener implements FrameListener {

		private PingListener(PingService service) {

		}

		@Override
		public void onClose(Session ses, Reason arg1) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onError(Session ses, Exception arg1) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onFrame(Session ses, Frame arg1) {

		}

	}

	@Override
	public void run() {
		for (;;) {
			try {
				Frame replay = new DataFrame(FrameType.TEXT, "ping");
				this.distribute(replay);
				Thread.sleep(5000);
			} catch (Exception e) {

			}
		}

	}

}
