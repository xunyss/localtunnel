package io.xunyss.localtunnel;

import io.xunyss.localtunnel.LocalTunnel;
import io.xunyss.localtunnel.LocalTunnelClient;
import io.xunyss.localtunnel.RemoteDetails;
import io.xunyss.localtunnel.monitor.MonitoringListener;

/**
 * 
 * @author XUNYSS
 */
public class LocalTunnelTest {
	
	public static void main(String[] args) throws Exception {
		
		// create local-tunnel
		LocalTunnel localTunnel = LocalTunnelClient.getDefault().create(9797);
		localTunnel.setMonitoringListener(getListener());
		localTunnel.setMaxActive(2);
		
		// open local-tunnel
		localTunnel.open("xunysslcs");
		
		// remote details
		RemoteDetails remoteDetails = localTunnel.getRemoteDetails();
		System.out.println("External URL: " + remoteDetails.getUrl());
		System.out.println("Max connections: " + remoteDetails.getMaxConn());
		System.out.println("Tunnel is ready..");
		
		ht = localTunnel;
		
		localTunnel.start();
	}
	
	static LocalTunnel ht;
	static int reqcnt = 0;
	public static MonitoringListener getListener() {
		return new MonitoringListener() {
			@Override
			public void onExecuteProxyTask(long threadId) {
				
			}

			@Override
			public void onConnectRemote(int activeTaskCount) {
				System.out.println("onConnectRemote " + activeTaskCount);
			}

			@Override
			public void onDisconnectRemote(int activeTaskCount) {
				System.out.println("onDisconnectRemote " + activeTaskCount);
			}

			@Override
			public void onConnectLocal(int activeTaskCount) {
				System.out.println("onConnectLocal " + activeTaskCount);
			}

			@Override
			public void onDisconnectLocal(int activeTaskCount) {
				System.out.println("onDisconnectLocal " + activeTaskCount);
				
				reqcnt++;
				if (reqcnt > 2) {
					System.out.println("stop http tunnel");
					ht.stop();
				}
			}
		};
	}
}
