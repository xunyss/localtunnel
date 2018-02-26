package io.xunyss.localtunnel;

import io.xunyss.localtunnel.monitor.MonitoringListener;

/**
 * 
 * @author XUNYSS
 */
public class LocalTunnelTest {
	
	static LocalTunnel localTunnel;
	static int handleCount = 0;
	static int stopCount = 3;
	
	public static void main(String[] args) throws Exception {
		
		// create local-tunnel
		localTunnel = LocalTunnelClient.getDefault().create(9797);
		localTunnel.setMonitoringListener(getListener());
//		localTunnel.setMaxActive(4);
		
		// open local-tunnel
		localTunnel.open();
		
		// remote details
		RemoteDetails remoteDetails = localTunnel.getRemoteDetails();
		System.out.println("External URL: " + remoteDetails.getUrl());
		System.out.println("Max connections: " + remoteDetails.getMaxConn());
		System.out.println("Tunnel is ready..");
		
		// start tunnel
		localTunnel.start();
	}
	
	public static MonitoringListener getListener() {
		return new MonitoringListener() {
			@Override
			public void onExecuteProxyTask(long threadId) {
//				System.out.println("onExecuteProxyTask: " + threadId);
			}

			@Override
			public void onConnectRemote(int activeTaskCount) {
				System.out.println("onConnectRemote: " + activeTaskCount);
			}

			@Override
			public void onDisconnectRemote(int activeTaskCount) {
				System.out.println("onDisconnectRemote: " + activeTaskCount);
			}

			@Override
			public void onErrorRemote(int activeTaskCount) {
				System.out.println("onErrorRemote: " + activeTaskCount);
			}
			
			@Override
			public void onConnectLocal(int activeTaskCount) {
				System.out.println("onConnectLocal: " + activeTaskCount);
			}

			@Override
			public void onDisconnectLocal(int activeTaskCount) {
				System.out.println("onDisconnectLocal: " + activeTaskCount);
				// stop tunnel
//				if (++handleCount > stopCount) {
//					System.out.println("stop http tunnel");
//					localTunnel.stop();
//				}
			}

			@Override
			public void onErrorLocal(int activeTaskCount) {
				System.out.println("onErrorLocal: " + activeTaskCount);
			}
		};
	}
}
