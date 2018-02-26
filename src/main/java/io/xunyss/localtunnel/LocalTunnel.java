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
		}
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
		synchronized (activeTaskCount) {
			// 2018.02.26 XUNYSS
			// activeTaskCount 값은 executeProxyTask() 수행 직전 증가시키도록
//			activeTaskCount.incrementAndGet();
			proxyTaskList.add(caller);
			
			if (monitoringListener != null) {
				monitoringListener.onConnectRemote(activeTaskCount.get());
			}
		}
	}
	
	/**
	 * 
	 * @param caller
	 */
	void onDisconnectRemote(ProxyTask caller) {
		synchronized (activeTaskCount) {
			activeTaskCount.decrementAndGet();
			proxyTaskList.remove(caller);
			
			if (monitoringListener != null) {
				monitoringListener.onDisconnectRemote(activeTaskCount.get());
			}
		}
	}
	
	/**
	 * 
	 * @param caller
	 */
	void onConnectLocal(ProxyTask caller) {
		synchronized (activeTaskCount) {
			if (monitoringListener != null) {
				monitoringListener.onConnectLocal(activeTaskCount.get());
			}
		}
	}
	
	/**
	 * 
	 * @param caller
	 */
	void onDisconnectLocal(ProxyTask caller) {
		synchronized (activeTaskCount) {
			if (monitoringListener != null) {
				monitoringListener.onDisconnectLocal(activeTaskCount.get());
			}
		}
	}
}
