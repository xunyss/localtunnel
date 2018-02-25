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
			// 종종 remote 와 연결이 실패하는 경우 있음
			// 한번 실패하면 영영 remote 로 접속 안됨
			// TODO: re-try 구현 필요 - LocalTunnel.CONNECT_DELAY 활용
			// TODO: 에러처리 필요 - errorConnectRemote, errorConnectLocal 추가 처리
//			synchronized (this) {
				try { Thread.sleep(10); } catch (Exception e) { e.printStackTrace(); }
				//connectRemote();
				Socket s = new Socket(remoteAddress.getHostName(), remoteAddress.getPort());
				System.out.println("connected " + s);
				/////////// 왜 sleep 1호 하면 remote 연결이 안되지
				/////////// thread run runtimeexception 발생하면? > 스레드 비정상 종료 하겟지
				/////////// synchronized (스레드객체) 스레드 객체에 접근하는게 block? 스레드 객체 자체 메소드는 수항하시나?
//			}
		}
		// failed to connect remote-server
		catch (Exception ex) {
			// send disconnect-remote signal
			localTunnel.onDisconnectRemote(this);
			System.out.println(">>>>>>>>> " + ex.toString());
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
					// 즉, remoteSocket.getInputStream().read(buffer) 가 '-1' 을 리턴하는 경우는 없을 것임
					break;
				}
				
				// remote(local-tunnel) server 에서 요청이 들어와야만 local 로 connection 을 맺음
				// 즉, idle 시 remote 와의 TCP connection 만 max_conn 수 만큼 생성된 상태로 있음
				// TODO: 반면, 공식 제공되는 localtunnel-client 에서는 동일한 갯수만큼 local 과의 TCP connection 도 생성되어 있음
				// TODO: 반드시 그렇게 local 과의 connection 을 미리 맺어놔야 하는지에 대해 확인 필요
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
							// 이 경우, localSocket.getInputStream().read(buffer) 는 '-1' 을 즉시 리턴 함
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
	
	
	
	
	
	
	
	public static void main(String[] args) throws Exception {
		System.out.println(Thread.currentThread().getName());
		Thread t = new Thread() {
			@Override
			public void run() {
				Socket sock = new Socket();
				try { Thread.sleep(1); } catch (Exception e) { e.printStackTrace(); }
				try {
				sock.connect(new InetSocketAddress("localHost", 9797));
				}
				catch (Exception e) {e.printStackTrace();}
				System.out.println("connected");
				
				int i = 10 / 0;
				System.out.println(i);
			}
		};
		t.start();
		System.out.println(t.getName());
		System.out.println(t.isAlive());
		System.out.println(t.getState());
		System.out.println("hello world");
		Thread.sleep(2000);
		System.out.println(t.isAlive());
		System.out.println(t.isInterrupted());
		System.out.println(t.getState());
		Thread.sleep(2000);
		System.out.println("end......");
	}
}
