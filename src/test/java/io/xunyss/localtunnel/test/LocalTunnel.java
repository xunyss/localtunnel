package io.xunyss.localtunnel.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.gson.Gson;

// https://github.com/mukatee/java-tcp-tunnel
// localtunnel 서버에서 http 요청시 localserver 로 가는 http hreader Connection: close 로 바꿈
public class LocalTunnel {
	
	static Gson gson = new Gson();
	
	public static void main(String[] args) throws Exception {
		new LocalTunnel().run();
	}
	
	class Client {
		
		String endpoint;
		
		Client(String endpoint) {
			this.endpoint = endpoint;
		}
		
		Tunnel newTunnel(String host, int port) {
			return new Tunnel(this, host, port);
		}
	}
	
	class Tunnel {
		
		Client client;
		
		String remoteHost;
		int remotePort;
		String localHost;
		int localPort;
		String subdomain;
		String url;
		int maxConn;
		
		Tunnel(Client client, String localHost, int localPort) {
			this.client = client;
			this.localHost = localHost;
			this.localPort = localPort;
		}
		
		void open(String subdomain) throws Exception {
			setup(subdomain);
			establish();
		}
		
		void setup(String subdomain) {
			String surl = client.endpoint + "/" + subdomain;
			try {
				URL url = new URL(surl);
				System.out.println("url.getHost(): " + url.getHost());
				trustAllHosts();
				HttpsURLConnection httpConn = (HttpsURLConnection) url.openConnection();
				httpConn.setRequestMethod("GET");
				httpConn.setDoInput(true);
				httpConn.setDoOutput(false);
				
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				byte[] buff = new byte[1024];
				int len = 0;
				InputStream is = httpConn.getInputStream();
				while ((len = is.read(buff)) > -1) {
					bos.write(buff, 0, len);
				}
				
				is.close();
				bos.close();
				
				System.out.println(bos.toString());
				@SuppressWarnings("rawtypes") Map map = gson.fromJson(bos.toString(), Map.class);
				this.remoteHost = url.getHost();
				this.remotePort = (int) (double) map.get("port");
				this.maxConn = (int) (double) map.get("max_conn_count");
				this.subdomain = (String) map.get("id");
				this.url = (String) map.get("url");
				
			}
			catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		
		void establish() throws Exception {
			Conn conn = new Conn(this);
			conn.open();
		}
	}
	
	static class Conn {
		
		Tunnel tunnel;
		Socket rs = null;
		Socket ls;
		
		Conn(Tunnel tunnel) throws Exception {
			this.tunnel = tunnel;
			rs = new Socket(tunnel.remoteHost, tunnel.remotePort);
			ls = new Socket(tunnel.localHost, tunnel.localPort);
		}
		
		void open() {
			new Thread(new Runnable() {
				@Override
				public void run() {
					System.out.println("start copy remote -> local");
					try {
						byte[] bf = new byte[512];
						int len;
						while (true) {
							System.out.println("remote input read...");
							len = rs.getInputStream().read(bf); System.out.println("remote input readed: " + len);
							if (len == -1) {
								break;
							}
							ls.getOutputStream().write(bf, 0, len);
							ls.getOutputStream().flush();
						}
						System.out.println("end copy remote -> local");
					}
					catch (IOException ioe) { ioe.printStackTrace(); }
				//	finally { try { rs.close(); } catch (Exception e) { e.printStackTrace(); } }
				}
			}).start();
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					System.out.println("start copy local -> remote");
					try {
						byte[] bf = new byte[512];
						int len;
						while (true) {
							System.out.println("local input read...");
							len = ls.getInputStream().read(bf); System.out.println("local input readed: " + len);
							if (len == -1) {
								break;
							}
							rs.getOutputStream().write(bf, 0, len);
							rs.getOutputStream().flush();
						}
						System.out.println("end copy local -> remote");
					}
					catch (IOException ioe) { ioe.printStackTrace(); }
					finally { try { ls.close(); rs.close(); tunnel.establish(); } catch (Exception e) { e.printStackTrace(); } }
				}
			}).start();
		}
	}
	
	public void run() throws Exception {
		Client client = new Client("https://localtunnel.me");
		Tunnel tunnel = client.newTunnel("localhost", 9797);
		tunnel.open("?new");
	}
	
	
	
	private static String joinHostPort(String host, int port) {
		if (host.contains(":")) {
			return "[" + host + "]:" + port;
		}
		return host + ":" + port;
	}
	
	
	
	
	
	
	
	
	
	
	static final String response = "HTTP/1.1 200 OK\r\n" + 
			"Date: Sat, 10 Feb 2018 14:10:16 GMT\r\n" + 
			"Content-Type: text/xml; charset=UTF-8\r\n" + 
			"Connection: close\r\n" + 
			"Server: Jetty(9.2.z-SNAPSHOT)\r\n" + 
			"\r\n" + 
			"<ReleaseTicketResponse><responseCode>OK</responseCode></ReleaseTicketResponse>\r\n";
	
	
	
	
	   private static void trustAllHosts()
		{
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager()
			{
				public java.security.cert.X509Certificate[] getAcceptedIssuers()
				{
					return new java.security.cert.X509Certificate[] {};
				}

				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
				{
				}

				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
				{
				}
			} };

			// Install the all-trusting trust manager
			try
			{
				SSLContext sc = SSLContext.getInstance("TLS");
				sc.init(null, trustAllCerts, new java.security.SecureRandom());
				HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
}






























