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
		sock = SocketFactory.getDefault().createSocket("localhost", 9797);

		new Thread(new Tr()).start();
		System.out.println();
	}

	private static void get() throws Exception {
		OutputStream out = sock.getOutputStream();
		InputStream in = sock.getInputStream();
		BufferedReader inr = new BufferedReader(new InputStreamReader(in));

		out.write(req.getBytes());
//		out.flush();

		//-- test1. write 하고 close 함
		Thread.sleep(2000);
		if (true) {
//			out.close();
//			in.close();
			/* 2022.09.14.
			 * input 으로부터 읽을것이 남아 있을 경우 socket 을 닫으면, 상대방에게 RST 을 날림 (상대방은 Connection rest 예외 발생)
			 * input 으로부터 읽을것이 없을 경우 socket 을 닫으면, 상대방의 read 메소드는 EOF 를 리턴 함
			 */
			System.out.println(in.available());
			sock.close();
//			return;
		}
		//----


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

	static final String req = "GET http://localhost:9797/ HTTP/1.1\r\n" +
			"Accept-Encoding: gzip\r\n" +
			"User-Agent: Java/1.8.0_152-release\r\n" +
			"Host: localhost:19797\r\n" +
			"Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2\r\n" +
			//	"Connection: keep-alive\r\n" +
			//	"Connection: Close\r\n" +
			"\r\n";
}
