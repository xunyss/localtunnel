package io.xunyss.localtunnel.monitor;

import java.util.EventListener;

/**
 * 
 * @author XUNYSS
 */
public interface MonitoringListener extends EventListener {
	
	/**
	 * 
	 * @param threadId
	 */
	void onExecuteProxyTask(long threadId);
	
	/**
	 * 
	 * @param activeTaskCount
	 */
	void onConnectRemote(int activeTaskCount);
	
	/**
	 * 
	 * @param activeTaskCount
	 */
	void onDisconnectRemote(int activeTaskCount);
	
	/**
	 * 
	 * @param activeTaskCount
	 */
	void onConnectLocal(int activeTaskCount);
	
	/**
	 * 
	 * @param activeTaskCount
	 */
	void onDisconnectLocal(int activeTaskCount);
}
