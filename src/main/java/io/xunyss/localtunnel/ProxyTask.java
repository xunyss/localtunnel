package io.xunyss.localtunnel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import io.xunyss.commons.io.IOUtils;

/**
 * 
 * @author XUNYSS
 */
public class ProxyTask implements Runnable {
	
	/**
	 * 
	 */
	private static final int BUFFER_SIZE = 512;
	
	
	private final LocalTunnel localTunnel;
	
	private final InetSocketAddress remoteAddress;
	private final InetSocketAddress localAddress;
	
	private final Socket remoteSocket = new Socket();
	private final Socket localSocket = new Socket();
	
	private boolean localHandling = false;
	
	
	/**
	 * 
	 * @param localTunnel
	 * @param remoteAddress
	 * @param localAddress
	 */
	ProxyTask(LocalTunnel localTunnel, InetSocketAddress remoteAddress, InetSocketAddress localAddress) {
		this.localTunnel = localTunnel;
		this.remoteAddress = remoteAddress;
		this.localAddress = localAddress;
	}
	
	/**
	 * 
	 */
	@Override
	public void run() {
		// send connect-remote signal
		localTunnel.onConnectRemote(this);
		
		// connect to remote-server
		try {
			connectRemote();
		}
		// failed to connect remote-server
		catch (IOException ex) {
			// send disconnect-remote signal
			localTunnel.onDisconnectRemote(this);
			
			// stop current thread
			return;
		}
		
		byte[] buffer = new byte[BUFFER_SIZE];
		int readLen;
		try {
			while (true) {
				// read from remote-server
				readLen = remoteSocket.getInputStream().read(buffer);
				if (readLen == IOUtils.EOF) {
					// remoteSocket.getInputStream().read(buffer) 문장은
					// "ProxyTask-LocalForwarder" 스레드에서 IOUtils.closeQuietly(remoteSocket); 문장 수행 후에야 비로소 
					// 'java.net.SocketException: Socket closed' 예외를 발생 시키며 종료 될 수 있을 것임
					// 즉, remoteSocket.getInputStream().read(buffer) 가 '-1' 을 리턴하는 경우는 없을 것임.
					break;
				}
				
				if (!localHandling) {
					localHandling = true;
					connectLocal();		// connect to local-server
					forwardLocal();		// start stream pump from local to remote
				}
				
				// write read data to local-server
				localSocket.getOutputStream().write(buffer, 0, readLen);
				localSocket.getOutputStream().flush();
			}
		}
		catch (SocketException ex) {
			// case-1
			// at read()
			// java.net.SocketException: Socket closed
			// (forwardLocal() 에서 remote 로 write 끝낸 후 remote socket 을 close 한 상태)
			// (destroy() 에서 socket 을 close 한 상태)
			if ("Socket closed".equals(ex.getMessage())) {
				// do nothing
			}
			// case-2
			// at read()
			// java.net.SocketException: Connection reset
			// (remote(local-tunnel) server 에서 접속을 끊은 상태)
			else if ("Connection reset".equals(ex.getMessage())) {
				// do nothing
			}
			// another case
			else {
				// ignore exception
			}
		}
		catch (IOException ex) {
			// ignore exception
		}
		finally {
			IOUtils.closeQuietly(localSocket);
			
			// send disconnect-remote signal
			localTunnel.onDisconnectRemote(this);
		}
	}
	
	
	/**
	 * 
	 * @throws IOException
	 */
	private void connectRemote() throws IOException {
		// connect to remote-server
		remoteSocket.connect(remoteAddress);
	}
	
	/**
	 * 
	 * @throws IOException
	 */
	private void connectLocal() throws IOException {
		// connect to local-server
		localSocket.connect(localAddress);
	}
	
	/**
	 * 
	 */
	private void forwardLocal()  {
		// copy local
		new Thread(new Runnable() {
			@Override
			public void run() {
				// send connect-local signal
				localTunnel.onConnectLocal(ProxyTask.this);
				
				byte[] buffer = new byte[BUFFER_SIZE];
				int readLen;
				try {
					while (true) {
						// read from local-server
						readLen = localSocket.getInputStream().read(buffer);
						if (readLen == IOUtils.EOF) {
							// 브라우저에서 localtunnel 서버로 요청시 header 에 'Connection: keep-alive' 설정해도
							// localtunnel 서버에서 local-server 로 가는 http hreader 를 'Connection: close' 로 바꿈
							// 즉, local-server 에서 받는 request 는 항상 'Connection: close' 이기 때문에, local-server 는 response 이후 connection 을 종료 할 것임
							// 이 경우, localSocket.getInputStream().read(buffer) 는 '-1' 을 즉시 리턴 함.
							break;
						}
						
						// write read data to remote-server
						remoteSocket.getOutputStream().write(buffer, 0, readLen);
						remoteSocket.getOutputStream().flush();
					}
				}
				catch (IOException ex) {
					// ignore exception
				}
				finally {
					IOUtils.closeQuietly(remoteSocket);
					
					// send disconnect-local signal
					localTunnel.onDisconnectLocal(ProxyTask.this);
				}
			}
		}, "ProxyTask-LocalForwarder").start();
	}
	
	/**
	 * 
	 */
	public void destroy() {
		IOUtils.closeQuietly(remoteSocket);
		IOUtils.closeQuietly(localSocket);
	}
}
