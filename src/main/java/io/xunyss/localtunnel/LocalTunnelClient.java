package io.xunyss.localtunnel;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import com.google.gson.Gson;

import io.xunyss.commons.io.IOUtils;
import io.xunyss.commons.net.HttpMethod;
import io.xunyss.commons.net.TrustAllCerts;

/**
 * 
 * @author XUNYSS
 */
public class LocalTunnelClient {
	
	//----------------------------------------------------------------------------------------------
	// https://localtunnel.me
	// https://github.com/localtunnel
	//----------------------------------------------------------------------------------------------
	
	private static final LocalTunnelClient DEFAULT_CLIENT = new LocalTunnelClient();
	
	private static final String DEFAULT_ENDPOINT = "https://localtunnel.me" + "/";
	private static final String RANDOM_SUBDOMAIN = "?new";
	private static final String LOCAL_HOST = "0.0.0.0";
	
	private final String endPoint;
	
	
	/**
	 * 
	 * @param endPoint
	 */
	public LocalTunnelClient(String endPoint) {
		this.endPoint = endPoint;
	}
	
	/**
	 * 
	 */
	public LocalTunnelClient() {
		this(DEFAULT_ENDPOINT);
	}
	
	
	/**
	 * 
	 * @param localHost
	 * @param localPort
	 * @return
	 */
	public LocalTunnel create(String localHost, int localPort) {
		return new LocalTunnel(this, localHost, localPort);
	}
	
	/**
	 * 
	 * @param localPort
	 * @return
	 */
	public LocalTunnel create(int localPort) {
		return create(LOCAL_HOST, localPort);
	}
	
	/**
	 * 
	 * @param subDomain
	 * @return
	 * @throws IOException
	 */
	public RemoteDetails setup(String subDomain) throws IOException {
		URL url = new URL(endPoint + (subDomain != null ? subDomain : RANDOM_SUBDOMAIN));
		HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
		TrustAllCerts.setToConnection(httpConn);
		httpConn.setRequestMethod(HttpMethod.GET);
		httpConn.setDoInput(true);
		httpConn.setDoOutput(false);
		
		String responseJson;
		try (InputStream httpInput = httpConn.getInputStream()) {
			responseJson = IOUtils.toString(httpInput);
		}
		httpConn.disconnect();
		
		@SuppressWarnings("rawtypes")
		Map map = new Gson().fromJson(responseJson, Map.class);
		
		RemoteDetails remoteDetails = new RemoteDetails();
		remoteDetails.setRemoteHost	(url.getHost());
		remoteDetails.setRemotePort	((int) (double) map.get("port"));
		remoteDetails.setSubDomain	((String) map.get("id"));
		remoteDetails.setUrl		((String) map.get("url"));
		remoteDetails.setMaxConn	((int) (double) map.get("max_conn_count"));
		
		return remoteDetails;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public static LocalTunnelClient getDefault() {
		return DEFAULT_CLIENT;
	}
}
