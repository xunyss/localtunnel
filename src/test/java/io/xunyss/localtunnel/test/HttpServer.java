package io.xunyss.localtunnel.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class HttpServer {

	static boolean stop = false;

	public static void main(String[] args) throws IOException {
		ServerSocket server = null;

		try {
			server = new ServerSocket(9797);
			while (true) {
				Socket socket = server.accept();

				System.out.println(socket.getInetAddress() + " connected.");

				ServerThread thread = new ServerThread(server, socket);
				thread.start();
			}
		}
		catch (SocketException se) {
			if (stop) {
				System.out.println("server stop.");
			}
			else {
				throw se;
			}
		}
		finally {
			server.close();
		}
	}

	static class ServerThread extends Thread {
		//		private ServerSocket server;
		private Socket socket;

		ServerThread(ServerSocket server, Socket socket) {
//			this.server = server;
			this.socket = socket;
		}

		public void run() {
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

				String tname;

				while (true) {
					String line = in.readLine();
					if (line == null) {
						System.out.println(">> read finished.");
						break;
					}
					tname = Thread.currentThread().getName();
					System.out.println("[" + tname + "] " + line);

					if ("".equals(line)) {	// end of http get
						System.out.println(">> end of http request.");
						/* 2022.09.14.
						 * 상대방에게 읽어야 할 거리를 줘버림.
						 */
//						out.write(res);
//						out.flush();
						socket.getOutputStream().write(res.getBytes());
					}
				}
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
			}
			finally {
				try {
					socket.close();
				}
				catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
		}
	}

	static final String res = "HTTP/1.1 200 OK\r\n" +
			"Date: Sat, 10 Feb 2018 16:22:48 GMT\r\n" +
			"Content-Type: text/xml; charset=UTF-8\r\n" +
			"Content-Length: 78\r\n" +
			"Server: Jetty(9.2.z-SNAPSHOT)\r\n" +
			"\r\n" +
			"OK";
}
