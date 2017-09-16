package xrun;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class TokenHandler {
	
	private static final long DELAY = 5 * 60 * 1000;
	
	private Map<String, Boolean> tokens = new HashMap<String, Boolean>();
	private Timer timer = new Timer();
	
	TokenHandler() {
	}
	
	synchronized String getToken() {
		String token = null;
		while (true) {
			token = UUID.randomUUID().toString();
			if (!tokens.containsKey(token)) {
				break;
			}
		}
		tokens.put(token, Boolean.TRUE);
		timer.schedule(new TT(this, token), DELAY);
		return token;
	}
	
	synchronized boolean isAuthorized(String token) {
		return tokens.containsKey(token);
	}
	
	synchronized void dispose() {
		tokens.clear();
		timer.cancel();
	}
	
	synchronized void removeToken(String token) {
		tokens.remove(token);
	}

}

class TT extends TimerTask {
	
	private TokenHandler handler;
	private String token;
	
	TT(TokenHandler handler, String token) {
		this.handler = handler;
		this.token = token;
	}

	public void run() {
		handler.removeToken(token);
	}
	
}
