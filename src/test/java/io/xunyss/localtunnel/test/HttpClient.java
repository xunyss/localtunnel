package io.xunyss.localtunnel.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.SocketFactory;

public class HttpClient {

	static Socket sock;
	
	public static void main(String[] args) throws Exception {
//		sock = new Socket("localhost", 9797);
		sock = SocketFactory.getDefault().createSocket("localhost", 9797);
		
		new Thread(new Tr()).start();
		System.out.println();
		
//		Thread.sleep(1000);
//		
//		new Thread(new Tr()).start();
//		System.out.println();
		
//		sock.close();
	}
	
	private static void get() throws Exception {
		OutputStream out = sock.getOutputStream();
		InputStream in = sock.getInputStream();
		BufferedReader inr = new BufferedReader(new InputStreamReader(in));
		
		out.write(req.getBytes());
		out.flush();
		
		boolean loop = true;
		while (loop) {
			if (inr.ready()) {
				int i = 0;
				while (i != -1) {
					i = inr.read();
					System.out.print((char) i);
				}
				loop = false;
			}
		}		
	}
	
	static class Tr implements Runnable {
		@Override
		public void run() {
			try {
				get();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	static final String req = "GET http://localhost:9797/rpc/releaseTicket.action?buildNumber=2017.3.4+Build+IU-173.4548.28&clientVersion=5&hostName=xunyss-pc&machineId=9f4d8e8f-deba-4176-8984-10c35b94b373&productCode=49c202d4-ac56-452b-bb84-735056242fb3&productFamilyId=49c202d4-ac56-452b-bb84-735056242fb3&salt=1518007517127&secure=false&ticketId=1&userName=xunyss HTTP/1.1\r\n" + 
			"Accept-Encoding: gzip\r\n" + 
			"User-Agent: Java/1.8.0_152-release\r\n" + 
			"Host: lcs.xunyss.io:81\r\n" + 
			"Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2\r\n" + 
		//	"Connection: keep-alive\r\n" +
		//	"Connection: Close\r\n" +
			"\r\n"; 
}
