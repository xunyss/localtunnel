package io.xunyss.localtunnel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.xunyss.localtunnel.monitor.MonitoringListener;

/**
 * 
 * @author XUNYSS
 */
public class LocalTunnel {
	
	/**
	 * 
	 */
	private static final int CONNECT_DELAY = 200;	// 0.2 seconds
	
	/**
	 * 
	 */
	private final LocalTunnelClient localTunnelClient;
	
	private RemoteDetails remoteDetails = null;
	private InetSocketAddress localAddress = null;
	private InetSocketAddress remoteAddress = null;
	private int maxTaskCount = 0;
	
	private Thread tunnelThread = null;
	private volatile boolean running = false;
	private final List<ProxyTask> proxyTaskList = Collections.synchronizedList(new ArrayList<ProxyTask>());
	private final AtomicInteger activeTaskCount = new AtomicInteger(0);
	private RuntimeException occurException = null;
	
	private MonitoringListener monitoringListener = null;
	
	
	/**
	 * 
	 * @param localTunnelClient
	 * @param localHost
	 * @param localPort
	 */
	LocalTunnel(LocalTunnelClient localTunnelClient, String localHost, int localPort) {
		this.localTunnelClient = localTunnelClient;
		this.localAddress = new InetSocketAddress(localHost, localPort);
	}
	
	/**
	 * 
	 * @param monitoringListener
	 */
	public void setMonitoringListener(MonitoringListener monitoringListener) {
		if (running) {
			throw new IllegalStateException("Tunnel is already running");
		}
		this.monitoringListener = monitoringListener;
	}
	
	/**
	 * 
	 * @param maxActive
	 */
	public void setMaxActive(int maxActive) {
		if (running) {
			throw new IllegalStateException("Tunnel is already running");
		}
		if (remoteDetails != null) {
			if (maxActive > remoteDetails.getMaxConn()) {
				return;
			}
		}
		maxTaskCount = maxActive;
	}
	
	/**
	 * 
	 * @return
	 */
	public int getMaxActive() {
		return maxTaskCount;
	}
	
	/**
	 * 
	 * @return
	 */
	public RemoteDetails getRemoteDetails() {
		if (!running) {
			throw new IllegalStateException("Tunnel is not opened");
		}
		return remoteDetails;
	}
	
	/**
	 * 
	 * @param subDomain
	 * @throws IOException
	 */
	public void open(String subDomain) throws IOException {
		remoteDetails = localTunnelClient.setup(subDomain);
		remoteAddress = new InetSocketAddress(remoteDetails.getRemoteHost(), remoteDetails.getRemotePort());
		
		if (maxTaskCount == 0 || maxTaskCount > remoteDetails.getMaxConn()) {
			setMaxActive(remoteDetails.getMaxConn());
		}
		
		running = true;
	}
	
	/**
	 * 
	 * @throws IOException
	 */
	public void open() throws IOException {
		open(null);
	}
	
	/**
	 * 
	 */
	public void start() {
		if (!running) {
			throw new IllegalStateException("Tunnel is not opened");
		}
		
		// create tunnel thread
		tunnelThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (running) {
//					synchronized (activeTaskCount) {
//					(none multi-thread area)
						if (activeTaskCount.get() < maxTaskCount) {
							activeTaskCount.incrementAndGet();
							executeProxyTask();
						}
//					}
					try {
						Thread.sleep(CONNECT_DELAY);
					}
					catch (InterruptedException ex) {
						break;
					}
				}
				if (occurException != null) {
					throw occurException;
				}
			}
		}, "LocalTunnel");
		
		// start tunnel thread
		tunnelThread.start();
	}
	
	/**
	 * 
	 */
	public void stop() {
		// stop tunnel thread
		running = false;
		
		// confirm kill
		if (tunnelThread != null) {
			tunnelThread.interrupt();
			tunnelThread = null;
		}
		
		// stop proxy task thread
		synchronized (proxyTaskList) {
			for (ProxyTask proxyTask : proxyTaskList) {
				proxyTask.destroy();
			}
			// activeTaskCount --> "0"
			// proxyTaskList --> "empty"
			// 로 초기화 될 것임
			// TODO: 제대로 초기화 되었는지 재확인 할 필요 있을 듯
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean isRunning() {
		return running;
	}
	
	/**
	 * 
	 */
	private void executeProxyTask() {
		Thread proxyTaskThread = new Thread(
				new ProxyTask(this, remoteAddress, localAddress),
				"ProxyTask"
		);
		proxyTaskThread.start();
		
		if (monitoringListener != null) {
			monitoringListener.onExecuteProxyTask(proxyTaskThread.getId());
		}
	}
	
	
	//----------------------------------------------------------------------------------------------
	
	/**
	 * 
	 * @param caller
	 */
	void onConnectRemote(ProxyTask caller) {
//		synchronized (activeTaskCount) {
			// 2018.02.26 XUNYSS
			// activeTaskCount 값은 executeProxyTask() 수행 직전 증가시키도록
//			activeTaskCount.incrementAndGet();
			proxyTaskList.add(caller);
			
			if (monitoringListener != null) {
				// activeTaskCount.get() 는 실제 연결된 connection 갯수보다 많을 수 있음
//				monitoringListener.onConnectRemote(activeTaskCount.get());
				monitoringListener.onConnectRemote(proxyTaskList.size());
			}
//		}
	}
	
	/**
	 * 
	 * @param caller
	 */
	void onDisconnectRemote(ProxyTask caller) {
//		synchronized (activeTaskCount) {
			activeTaskCount.decrementAndGet();
			proxyTaskList.remove(caller);
			
			if (monitoringListener != null) {
				monitoringListener.onDisconnectRemote(activeTaskCount.get());
			}
//		}
	}
	
	/**
	 * 
	 * @param caller
	 * @param ex
	 */
	void onErrorRemote(ProxyTask caller, Exception ex) {
//		synchronized (activeTaskCount) {
			activeTaskCount.decrementAndGet();
			proxyTaskList.remove(caller);	// always returns 'false'
			
			if (monitoringListener != null) {
				monitoringListener.onErrorRemote(activeTaskCount.get());
			}
//		}
		
		// handle error
		// retry 에도 불구하고 remote 로의 접속이 실패한 경우
		// stop() 이후 tunnelThread 에서 RuntimeException 발생 유도
		occurException = new IllegalStateException("Failed to connect LocalTunnel-Remote", ex);
		stop();
	}
	
	/**
	 * 
	 * @param caller
	 */
	void onConnectLocal(ProxyTask caller) {
//		synchronized (activeTaskCount) {
			if (monitoringListener != null) {
				monitoringListener.onConnectLocal(activeTaskCount.get());
			}
//		}
	}
	
	/**
	 * 
	 * @param caller
	 */
	void onDisconnectLocal(ProxyTask caller) {
//		synchronized (activeTaskCount) {
			if (monitoringListener != null) {
				monitoringListener.onDisconnectLocal(activeTaskCount.get());
			}
//		}
	}
	
	/**
	 * 
	 * @param caller
	 * @param ex
	 */
	void onErrorLocal(ProxyTask caller, Exception ex) {
//		synchronized (activeTaskCount) {
			if (monitoringListener != null) {
				monitoringListener.onErrorLocal(activeTaskCount.get());
			}
//		}
	}
}
