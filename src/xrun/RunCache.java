package xrun;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

class RunCache implements Runnable {
	
	private static final long UPDATE_PAUSE = 1000;
	
	private RunCalcUtils rcUtils;
	
	private Map<String, JSONObject> presetCache = new HashMap<String, JSONObject>();
	private Map<String, JSONObject> dashboardCache = new HashMap<String, JSONObject>();
	
	private volatile boolean isRunning = false;
	private List<String> presets;
	private List<String> dashboards;
	
	RunCache(RunCalcUtils rcUtils) {
		this.rcUtils = rcUtils;
	}
	
	synchronized JSONObject getPresetData(String presetName) {
		return presetCache.get(presetName);
	}
	
	synchronized JSONObject getDashboardData(String dashboardName) {
		return dashboardCache.get(dashboardName);
	}
	
	synchronized void update(List<String> presets, List<String> dashboards) {
		terminateIfNeeded();
		presetCache.clear();
		dashboardCache.clear();
		isRunning = true;
		this.presets = presets;
		this.dashboards = dashboards;
		new Thread(this, "Caching Thread").start();
	}
	
	private void terminateIfNeeded() {
		if (isRunning) {
			isRunning = false;
			notifyAll();
		}
	}

	public void run() {
		int ind1 = 0;
		int ind2 = 0;
		while (true) {
			Map<String, JSONObject> cache = null;
			String name = null;
			int op = 1;
			synchronized (this) {
				if (isRunning) {
					try {
						wait(UPDATE_PAUSE);
					} catch (InterruptedException ignore) {
						// silent catch
					}
					if (!isRunning) {
						break;
					}
					if (ind1 < presets.size()) {
						name = presets.get(ind1++);
						cache = presetCache;
					} else if (ind2 < dashboards.size()) {
						name = dashboards.get(ind2++);
						op = 2;
						cache = dashboardCache;
					} else {
						isRunning = false;
						break;
					}
				} else {
					break;
				}
			}
			System.out.println("name = " + name + " " + op);
			JSONObject data = (op == 1 ? rcUtils.fetchPreset(name) : rcUtils.fetchDashboard(name));
			synchronized (this) {
				if (isRunning && data != null) {
					System.out.println("Cache data for " + name);
					cache.put(name, data);
				}
			}
		}
	}

}
