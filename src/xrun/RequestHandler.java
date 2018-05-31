package xrun;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONObject;

import xrun.parser.TrackParser;
import xrun.utils.WeekCalculator;

class RequestHandler extends AbstractHandler {
  
  private static final byte[] CODE = new byte[] {-55, -85, 122, -120, -106, -44, 81, -78, 90, 79, -73, -54, 34, -42,
      -110, -67, 6, 56, -102, -7};
  
  private static final String SEP = "#$^";
  
  private String cachedDefaultFetchLoggedIn;
  private String cachedDefaultFetchNotLoggedIn;
  private long counter = 0;

  private static boolean isAuthorized(String password) {
    if (password == null) {
      return false;
    }
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      return false;
    }
    byte[] arr = md.digest(password.getBytes());
    if (arr == null || arr.length != CODE.length) {
      return false;
    }
    for (int i = 0; i < arr.length; ++i) {
      if (arr[i] != CODE[i]) {
        return false;
      }
    }
    return true;
  }

  private RunCalcUtils rcUtils;
  private File activityTemplateFile;
  private File comparisonTemplateFile;
  private long actTempLastMod = Long.MIN_VALUE;
  private long compTempLastMod = Long.MIN_VALUE;
  private String activityTemplate = "Not loaded :(";
  private String comparisonTemplate = "Not loaded :(";
  private List<String> allowedRefs;

  public RequestHandler(File tracksBase, File clientSecret, File activityTemplateFile, File comparisonTemplateFile, List<String> allowedRefs)
  		throws IOException {
    rcUtils = new RunCalcUtils(this, tracksBase, clientSecret);
    this.activityTemplateFile = activityTemplateFile;
    this.comparisonTemplateFile = comparisonTemplateFile;
    this.allowedRefs = allowedRefs;
    System.out.println("Initialize finished!");
  }
  
  private synchronized long getCounter() {
    return counter;
  }
  
  synchronized void resetCache() { // MUST be called once before modification and once after
    cachedDefaultFetchLoggedIn = null;
    cachedDefaultFetchNotLoggedIn = null;
    ++counter;
  }
  
  private synchronized void trySetNewCache(long oldCounter, String cache, boolean loggedIn) {
    if (oldCounter == counter) {
      if (loggedIn) {
        cachedDefaultFetchLoggedIn = cache;
      } else {
        cachedDefaultFetchNotLoggedIn = cache;
      }
    }
  }
  
  private boolean isAllowed(String refIP) {
  	return allowedRefs == null || allowedRefs.contains(refIP);
  }
  
  private String getActivityTemplate() {
  	String result = getTemplate(activityTemplateFile, actTempLastMod, "Not loaded :(".equals(activityTemplate));
  	if (result == null) {
  		result = activityTemplate;
  	} else {
  		actTempLastMod = activityTemplateFile.lastModified();
  	}
  	return result;
  }
  
  private String getComparisionTemplate() {
  	String result = getTemplate(comparisonTemplateFile, compTempLastMod, "Not loaded :(".equals(comparisonTemplate));
  	if (result == null) {
  		result = comparisonTemplate;
  	} else {
  		compTempLastMod = comparisonTemplateFile.lastModified();
  	}
  	return result;
  }
  
  private String getTemplate(File file, long lastMod, boolean isRequired) {
  	String result = null;
    if (isRequired || (file.isFile() && file.lastModified() != lastMod)) {
      InputStream is = null;
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
        is = new FileInputStream(file);
        byte[] buf = new byte[8192];
        int rd = 0;
        while ((rd = is.read(buf)) != -1) {
          baos.write(buf, 0, rd);
        }
        result = new String(baos.toByteArray());
      } catch (Exception ignore) {
        // silent catch
      } finally {
        RunCalcUtils.silentClose(is);
      } 
    }
    return result;
  }
  
  void dispose() {
    if (rcUtils != null) {
      rcUtils.dispose();
    }
  }
  
  private Calendar parseDate(String dt) {
    StringTokenizer st = new StringTokenizer(dt, "/,;", false);
    if (st.countTokens() != 3) {
      return null;
    }
    Calendar cal = new GregorianCalendar();
    try {
      cal.set(Calendar.MONTH, Integer.parseInt(st.nextToken()) - 1);
      cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(st.nextToken()));
      cal.set(Calendar.YEAR, Integer.parseInt(st.nextToken()));
      return cal;
    } catch (Exception ignore) {
      // silent catch
    }
    return null;
  }
  
	private String handleFileUpload(Request baseRequest, HttpServletRequest request, HttpServletResponse response,
	    String activityName, String activityType, String reliveCC, String photos, String dashboard, boolean secure) {
	  if (activityName != null && activityName.length() == 0) {
	    activityName = null;
	  }
		ServletFileUpload upload = new ServletFileUpload();
		try {
			FileItemIterator iter = upload.getItemIterator(request);
			while (iter.hasNext()) {
				FileItemStream item = iter.next();
				if (!item.isFormField()) {
					return rcUtils.addActivity(item.getName(), item.openStream(), activityName, activityType, reliveCC, photos,
					    dashboard, secure);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void processUpload(String target, Request baseRequest, HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {
    String name = null;
    String type = null;
    String reliveCC = null;
    String photos = null;
    String dashboard = null;
    int ind = target.indexOf('.');
    target = target.substring(ind + 1);
    ind = target.indexOf(SEP);
    name = target.substring(0, ind);
    
    int to = target.indexOf(SEP, ind + SEP.length());
    type = target.substring(ind + SEP.length(), to);
    ind = to;
    
    to = target.indexOf(SEP, ind + SEP.length());
    reliveCC = target.substring(ind + SEP.length(), to);
    ind = to;
    
    to = target.indexOf(SEP, ind + SEP.length());
    photos = target.substring(ind + SEP.length(), to);
    ind = to;
    
    to = target.indexOf(SEP, ind + SEP.length());
    dashboard = target.substring(ind + SEP.length(), to);
    ind = to;
    boolean secure = "t".equals(target.substring(ind + SEP.length()));
    if (type.length() == 0) {
      type = Constants.RUNNING;
    }
    if (reliveCC.length() == 0) {
      reliveCC = "none";
    }
    if (photos.length() == 0) {
      photos = "none";
    }
    PrintWriter pw = response.getWriter();
    resetCache();
    try {
      response.setContentType("text/html");
      if (isLoggedIn(baseRequest)) {
        String result = handleFileUpload(baseRequest, request, response, name, type, reliveCC, photos, dashboard, secure);
        if (result == null) { // all normal
          pw.println("<h2>Upload finished!</h2>");
          pw.println("<a href=\"runcalc\"><h2>Go to main page</h2></a>");
          response.setStatus(HttpServletResponse.SC_OK);
        } else {
          pw.println("<h2>Upload failed!</h2>");
          pw.println(result);
          pw.println("<a href=\"runcalc\"><h2>Go to main page</h2></a>");
          response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
        }
      } else { // just in case
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        pw.println("Not authorized!");
        pw.println("<a href=\"runcalc\"><h2>Go to main page</h2></a>");
      }
    } finally {
      pw.flush();
    }
    resetCache();
    baseRequest.setHandled(true);
	}
	
  private void processBest(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    PrintWriter pw = response.getWriter();
    try {
      response.setContentType("application/json");
      pw.println(rcUtils.getBest());
      pw.flush();
      response.setStatus(HttpServletResponse.SC_OK);
    } finally {
      pw.flush();
    }
    baseRequest.setHandled(true);
  }
  
  private void processBestSplits(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    PrintWriter pw = response.getWriter();
    try {
      response.setContentType("application/json");
      pw.println(rcUtils.getBestSplits());
      pw.flush();
      response.setStatus(HttpServletResponse.SC_OK);
    } finally {
      pw.flush();
    }
    baseRequest.setHandled(true);
  }
  
  private String getDateStr(Calendar date, String def) {
    if (date == null) {
      return def;
    }
    StringBuffer sb = new StringBuffer();
    sb.append(date.get(Calendar.DAY_OF_MONTH));
    sb.append(' ');
    sb.append(Constants.MONTHS[date.get(Calendar.MONTH)]);
    sb.append(' ');
    sb.append(date.get(Calendar.YEAR));
    return sb.toString();
  }
  
  private static final int[] MT_LEN = new int[] {31, -1, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  
  private static int getMonthLen(int month, int year) {
  	if (month != 1) {
  		return MT_LEN[month];
  	}
  	return year % 4 == 0 ? 29 : 28;
  }
  
  private boolean isDefaultFetch(Request baseRequest) {
    if (!"true".equals(baseRequest.getHeader("run")) || !"true".equals(baseRequest.getHeader("trail")) ||
        !"true".equals(baseRequest.getHeader("uphill")) || !"true".equals(baseRequest.getHeader("hike")) ||
        !"false".equals(baseRequest.getHeader("walk")) || !"false".equals(baseRequest.getHeader("other"))) {
      return false;
    }
    try {
      if (Integer.parseInt(baseRequest.getHeader("dateOpt")) != 5) {
        return false;
      }
    } catch (Exception ignore) {
      return false;
    }
    String smin = baseRequest.getHeader("dmin");
    String smax = baseRequest.getHeader("dmax");
    String nf = baseRequest.getHeader("nameFilter");
    if (smin != null && smin.length() > 0) {
      return false;
    }
    if (smax != null && smax.length() > 0) {
      return false;
    }
    if (nf != null && nf.length() > 0) {
      return false;
    }
    String dashboard = baseRequest.getHeader("dashboard");
    if (dashboard != null && dashboard.length() > 0 && !"Main".equals(dashboard)) {
      return false;
    }
    return true;
  }
  
  private void chechCacheUpToDate(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    response.setContentType("application/txt");
    String weekS = baseRequest.getHeader("maxWeek");
    String yearS = baseRequest.getHeader("maxYear");
    int week = -1;
    int year = -1;
    try {
      week = Integer.parseInt(weekS);
      year = Integer.parseInt(yearS);
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      resetCache();
      return;
    }
    Calendar current = new GregorianCalendar(TimeZone.getDefault());
    int currentYear = current.get(Calendar.YEAR);
    if (currentYear != year) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      resetCache();
      return;
    } else {
      int currentWeek = WeekCalculator.identifyWeek(current.get(Calendar.DAY_OF_MONTH), current.get(Calendar.MONTH) + 1, current.get(Calendar.YEAR), new String[1])[0];
      if (currentWeek != week) {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        resetCache();
        return;
      }
    }
    response.setStatus(HttpServletResponse.SC_OK);
  }
  
  private void processFetch(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    boolean isLoggedIn = isLoggedIn(baseRequest);
    String result = null;
    final String cached = isLoggedIn ? cachedDefaultFetchLoggedIn : cachedDefaultFetchNotLoggedIn;
    boolean isDefault = isDefaultFetch(baseRequest);
    if (isDefault) {
      result = cached;
    }
    response.setContentType("application/json");
    PrintWriter pw = response.getWriter();
    try {
      if (result == null) {
        final long localCounter = isDefault ? getCounter() : -1;
        JSONObject data = processFetch0(baseRequest, response);
        if (data != null) { // in other case request is handled
          result = data.toString();
          if (isDefault && "true".equals(baseRequest.getHeader("getWMTotals"))) {
            trySetNewCache(localCounter, result, isLoggedIn);
          }
        }
      }
      if (result != null) {
        pw.println(result.toString());
      }
    } finally {
      pw.flush();
    }
    if (response.getStatus() != HttpServletResponse.SC_UNAUTHORIZED && response.getStatus() != HttpServletResponse.SC_BAD_REQUEST) {
    	response.setStatus(HttpServletResponse.SC_OK);
    	baseRequest.setHandled(true);
    }
  }
  
  private JSONObject processFetch0(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
  	String dash = baseRequest.getHeader("dashboard");
  	if (DBStorage.EXTERNAL_DASHBOARD.equals(dash) && !isLoggedIn(baseRequest)) {
  		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return null;
  	}
    boolean getWMT = "true".equals(baseRequest.getHeader("getWMTotals"));
    boolean run = "true".equals(baseRequest.getHeader("run"));
    boolean trail = "true".equals(baseRequest.getHeader("trail"));
    boolean uphill = "true".equals(baseRequest.getHeader("uphill"));
    boolean hike = "true".equals(baseRequest.getHeader("hike"));
    boolean walk = "true".equals(baseRequest.getHeader("walk"));
    boolean other = "true".equals(baseRequest.getHeader("other"));
    StringBuffer filterStr = new StringBuffer("Activities");
    Calendar startDate = null;
    Calendar endDate = null;
    int dateOpt = -1;
    String opts = baseRequest.getHeader("dateOpt");
    try {
      if (opts != null) {
        dateOpt = Integer.parseInt(opts);
      }
    } catch (NumberFormatException ignore) {
      // silent catch
    }
    startDate = new GregorianCalendar(TimeZone.getDefault());
    Calendar currentDate = new GregorianCalendar();
    int mt = 0;
    int periodLen = 0;
    boolean err = false;
    switch (dateOpt) {
      case 0: // this month
        startDate.set(Calendar.DAY_OF_MONTH, 1);
        periodLen = currentDate.get(Calendar.DAY_OF_MONTH);
        filterStr.append(" for this month");
        break;
      case 1: // this year
        startDate.set(Calendar.DAY_OF_MONTH, 1);
        startDate.set(Calendar.MONTH, 0);
        periodLen = currentDate.get(Calendar.DAY_OF_YEAR);
        filterStr.append(" for this year");
        break;
      case 2: // last 30
      	startDate.add(Calendar.DAY_OF_MONTH, 1);
        mt = startDate.get(Calendar.MONTH);
        if (mt > 0) {
          startDate.set(Calendar.MONTH, mt - 1);
          periodLen = getMonthLen(mt - 1, startDate.get(Calendar.YEAR));
        } else {
          startDate.set(Calendar.MONTH, 11);
          startDate.set(Calendar.YEAR, startDate.get(Calendar.YEAR) - 1);
          periodLen = 31;
        }
        filterStr.append(" after " + getDateStr(startDate, ""));
        break;
      case 3: // last 3m
      	startDate.add(Calendar.DAY_OF_MONTH, 1);
        mt = startDate.get(Calendar.MONTH);
        if (mt >= 3) {
          startDate.set(Calendar.MONTH, mt - 3);
          int year = startDate.get(Calendar.YEAR);
          periodLen = getMonthLen(mt - 3, year) + getMonthLen(mt - 2, year) + getMonthLen(mt - 1, year);
        } else {
          startDate.set(Calendar.MONTH, mt + 9);
          startDate.set(Calendar.YEAR, startDate.get(Calendar.YEAR) - 1);
          int year = startDate.get(Calendar.YEAR);
          periodLen = getMonthLen(mt + 9, year) + getMonthLen((mt + 9) % 12, year) + getMonthLen((mt + 10) % 12, year);
        }
        filterStr.append(" after " + getDateStr(startDate, ""));
        break;
      case 4: // last y
        startDate.set(Calendar.YEAR, startDate.get(Calendar.YEAR) - 1);
        startDate.add(Calendar.DAY_OF_MONTH, 1);
        int year = startDate.get(Calendar.YEAR);
        periodLen = year % 4 == 0 ? 366 : 365;
        filterStr.append(" after " + getDateStr(startDate, ""));
        break;
      case 5: // all
        startDate = null;
        break;
      case 6: // custom
      	String dts = baseRequest.getHeader("dtStart");
      	String dte = baseRequest.getHeader("dtEnd");
      	Calendar tempED = new GregorianCalendar(TimeZone.getDefault());
      	if (dts != null && dts.trim().length() > 0) {
      		startDate = parseDate(dts);
      		if (startDate == null) {
      			err = true;
      		}
      	} else {
      		periodLen = 0;
      		startDate = null;
      	}
      	if (dte != null && dte.trim().length() > 0) {
      		endDate = parseDate(dte);
      		if (endDate == null) {
      			err = true;
      		} else {
      			tempED = endDate;
      		}
      	} else {
      		endDate = null;
      	}
      	if (startDate != null) {
      		ZonedDateTime zd1 = ZonedDateTime.of(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH) + 1, startDate.get(Calendar.DATE), 0, 0, 0, 0, TimeZone.getDefault().toZoneId());
      		ZonedDateTime zd2 = ZonedDateTime.of(tempED.get(Calendar.YEAR), tempED.get(Calendar.MONTH) + 1, tempED.get(Calendar.DATE), 0, 0, 0, 0, TimeZone.getDefault().toZoneId());
      		periodLen =  (int) Duration.between(zd1, zd2).toDays() + 1;
      	}
        filterStr.append(" in period " + getDateStr(startDate, "Begining") + " - " + getDateStr(endDate, "Now"));
    }
    if (err) {
    	response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    	baseRequest.setHandled(true);
    	return null;
    }
    String smin = baseRequest.getHeader("dmin");
    String smax = baseRequest.getHeader("dmax");
    int dmin = 0;
    int dmax = Integer.MAX_VALUE;
    try {
      if (smin != null && smin.trim().length() > 0) {
        dmin = Integer.parseInt(smin);
      }
    } catch (NumberFormatException nfe) {
      err = true;
    }
    try {
      if (smax != null && smax.trim().length() > 0) {
        dmax = Integer.parseInt(smax);
      }
    } catch (NumberFormatException nfe) {
      err = true;
    }
    if (dmin > dmax || err) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      baseRequest.setHandled(true);
      return null;
    }
    if (dmin != 0 || dmax != Integer.MAX_VALUE) {
      filterStr.append(" with distance [" + dmin + ", " + (dmax != Integer.MAX_VALUE ? dmax : "+&#x221e;") + "] km");
    }
    String nameFilter = baseRequest.getHeader("nameFilter");
    if (nameFilter != null && nameFilter.trim().length() > 0) {
      filterStr.append(", matching the name regex");
    }
    JSONObject data = rcUtils.filter(nameFilter, run, trail, uphill, hike, walk, other,
        startDate, endDate, dmin, dmax, dash, periodLen, getWMT, isLoggedIn(baseRequest));
    List<String> types = new LinkedList<String>();
    if (run) {
      types.add(Constants.RUNNING);
    }
    if (trail) {
      types.add(Constants.TRAIL);
    }
    if (uphill) {
      types.add(Constants.UPHILL);
    }
    if (hike) {
      types.add(Constants.HIKING);
    }
    if (walk) {
      types.add(Constants.WALKING);
    }
    if (other) {
      types.add(Constants.OTHER);
    }
    String typesStr = types.toString().replace(" ", " ").replace("[", "").replace("]", "");
    filterStr.append(" of type " + typesStr);
    data.put("filter", filterStr.toString());
    return data;
  }
  
  private void processEdit(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
    String fileName = baseRequest.getHeader("File");
    String name = baseRequest.getHeader("Name");
    String type = baseRequest.getHeader("Type");
    String garminLink = baseRequest.getHeader("garminLink");
    String ccLink = baseRequest.getHeader("ccLink");
    String photosLink = baseRequest.getHeader("photosLink");
    boolean secureFlag = "true".equals(baseRequest.getHeader("secure"));
    String actDist = baseRequest.getHeader("actDist");
    String actTime = baseRequest.getHeader("actTime");
    String actGain = baseRequest.getHeader("actGain");
    String actLoss = baseRequest.getHeader("actLoss");
    Number newDist = null;
    Number newTime = null;
    Number newGain = null;
    Number newLoss = null;
    try {
      if (actDist != null && actDist.trim().length() > 0) {
        newDist = new Double(actDist.replace(',', '.').trim());
        if (newDist.doubleValue() <= 1e-6) {
          throw new IllegalArgumentException("Bad data");
        }
      }
      if (actTime != null && actTime.trim().length() > 0) {
        newTime = new Long(TrackParser.getRealTime(actTime.trim()));
        if (newTime.intValue() <= 0) {
          throw new IllegalArgumentException("Bad data");
        }
      }
      if (actGain != null && actGain.trim().length() > 0) {
        newGain = new Integer(actGain.replace(',', '.').trim());
        if (newGain.intValue() <= 0) {
          throw new IllegalArgumentException("Bad data");
        }
      }
      if (actLoss != null && actLoss.trim().length() > 0) {
        newLoss = new Integer(actLoss.replace(',', '.').trim());
        if (newLoss.intValue() <= 0) {
          throw new IllegalArgumentException("Bad data");
        }
      }
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      baseRequest.setHandled(true);
      return;
    }
    if (fileName != null && fileName.length() > 0 && name != null && name.length() > 0 && type != null
        && type.length() > 0) {
      JSONObject mods = new JSONObject();
      if (newDist != null) {
        mods.put("dist", newDist);
      }
      if (newTime != null) {
        mods.put("time", newTime);
      }
      if (newGain != null) {
        mods.put("gain", newGain);
      }
      if (newLoss != null) {
        mods.put("loss", newLoss);
      }
      String result = rcUtils.editActivity(fileName, name, type, garminLink, ccLink, photosLink, secureFlag, mods);
      if (result == null) {
      	response.setStatus(HttpServletResponse.SC_OK);
      } else {
      	response.getWriter().println(result);
      	response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
    } else {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processRevert(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
    String fileName = baseRequest.getHeader("File");
    if (fileName != null && fileName.length() > 0) {
      String result = rcUtils.editActivity(fileName, null, null, null, null, null, false, null);
      if (result == null) {
      	response.setStatus(HttpServletResponse.SC_OK);
      } else {
      	response.getWriter().println(result);
      	response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
    } else {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processDelete(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    resetCache();
    String fileName = baseRequest.getHeader("File");
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    if (fileName != null && fileName.length() > 0) {
      try {
        rcUtils.deleteActivity(fileName);
        response.setStatus(HttpServletResponse.SC_OK);
      } catch (Exception e) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
    } else {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processCompare(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    JSONObject item1 = rcUtils.getActivity(baseRequest.getHeader("file1"));
    JSONObject item2 = rcUtils.getActivity(baseRequest.getHeader("file2"));
    if (item1 == null || item2 == null) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    } else {
      response.setContentType("application/json");
      response.getWriter().println(rcUtils.compare(item1, item2));
      response.getWriter().flush();
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
    }
  }
  
  /* TODO - deprecate this */
  private void processRescan(Request baseRequest, HttpServletResponse response) {
  	if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
  	resetCache();
    rcUtils.rescan();
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processLogin(Request baseRequest, HttpServletResponse response) {
    if (!isLoggedIn(baseRequest)) {
      if ("BatGeorgi".equals(baseRequest.getHeader("User")) && isAuthorized(baseRequest.getHeader("Password"))) {
        Cookie cookie = rcUtils.generateCookie();
        if (cookie == null) {
          System.out.println("Error - cannot generate cookie");
          response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } else {
          response.addCookie(cookie);
          response.setStatus(HttpServletResponse.SC_OK);
        }
      } else {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      }
    } else {
      response.setStatus(HttpServletResponse.SC_OK);
    }
    baseRequest.setHandled(true);
  }
  
  private boolean isLoggedIn(Request baseRequest) {
  	if(!isAllowed(baseRequest)) {
  		return false;
  	}
  	return isLoggedIn0(baseRequest);
  }
  
  private boolean isLoggedIn0(Request baseRequest) {
    Cookie[] cookies = baseRequest.getCookies();
    if (cookies == null) {
      return false;
    }
    for (Cookie cookie : cookies) {
      if (rcUtils.isValidCookie(cookie)) {
        return true;
      }
    }
    return false;
  }
  
  private void checkCookie(Request baseRequest, HttpServletResponse response) {
    if (isLoggedIn0(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_OK);
    } else {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
    baseRequest.setHandled(true);
  }
  
  private void processGetActivity(String target, Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
  	if (rcUtils.getActivity(target) == null && rcUtils.getActivity(target + ".gpx") == null) {
  		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
  		baseRequest.setHandled(true);
  	} else {
  		response.setContentType("text/html");
  		PrintWriter pw = response.getWriter();
			try {
			  String template = getActivityTemplate();
				int ind = template.indexOf("TBD");
				if (ind != -1) {
					pw.print(template.substring(0, ind));
					pw.print("fa/" + target);
					int to = template.indexOf("TBD", ind + 3);
					if (to != -1) {
					  pw.print(template.substring(ind + 3, to));
					  if (rcUtils.getActivity(target) == null) {
				      target += ".gpx";
				    }
					  pw.print(target);
					  pw.print(template.substring(to + 3));
					} else {
					  pw.println(template.substring(ind + 3));
					}
				}
			} finally {
				pw.flush();
			}
  		response.setStatus(HttpServletResponse.SC_OK);
  		baseRequest.setHandled(true);
  	}
  }
  
  private void processGetComparison(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
  	String name1 = baseRequest.getParameter("a1");
  	String name2 = baseRequest.getParameter("a2");
  	if (name1 == null || name2 == null) {
  		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
  		baseRequest.setHandled(true);
  	}
  	if (rcUtils.getActivity(name1) == null) {
      name1 += ".gpx";
    }
    if (rcUtils.getActivity(name2) == null) {
      name2 += ".gpx";
    }
  	response.setContentType("text/html");
  	String template = getComparisionTemplate();
  	String fixed = template.replace("TBD1", name1).replace("TBD2", name2);
  	PrintWriter pw = response.getWriter();
  	try {
  		pw.println(fixed);
  	} finally {
  		pw.flush();
  	}
  	response.setStatus(HttpServletResponse.SC_OK);
		baseRequest.setHandled(true);
  }
  
  private void processFetchActivity(String target, Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
  	String name = target.substring(4);
  	boolean isProtected = false;
  	JSONObject json = rcUtils.getActivity(name);
  	if (json == null) {
  		json = rcUtils.getActivity(name + ".gpx");
  		isProtected = rcUtils.isSecured(name + ".gpx");
  	} else {
  	  isProtected = rcUtils.isSecured(name);
  	}
    if (isProtected && !isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    } else {
      PrintWriter pw = response.getWriter();
      response.setContentType("application/json");
      try {
        if (json == null) {
          pw.println("No data :(");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else {
          pw.println(json.toString());
          response.setStatus(HttpServletResponse.SC_OK);
        }
      } finally {
        pw.flush();
      }
    }
  	baseRequest.setHandled(true);
  }
  
  private void processGetGoords(Request baseRequest, HttpServletResponse response) throws IOException, ServletException {
    String name = baseRequest.getHeader("activity");
    boolean isProtected = false;
    JSONObject json = rcUtils.getActivity(name);
    if (json != null) {
      isProtected = rcUtils.isSecured(name);
    } else {
      isProtected = rcUtils.isSecured(name + ".gpx");
    }
    if (isProtected && !isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
      baseRequest.setHandled(true);
      return;
    }
    response.setContentType("application/json");
    JSONObject data = null;
    try {
      data = rcUtils.retrieveCoords(name, "true".equals(baseRequest.getHeader("perc")));
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
      baseRequest.setHandled(true);
      return;
    }
    PrintWriter pw = response.getWriter();
    try {
      pw.println(data.toString());
      response.setStatus(HttpServletResponse.SC_OK);
    } finally {
      pw.flush();
    }
    baseRequest.setHandled(true);
  }
  
  private void processAddDashboard(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
    response.setContentType("application/txt");
    PrintWriter pw = response.getWriter();
    try {
      String reason = rcUtils.addDashboard(baseRequest.getHeader("name"));
      pw.println(reason == null ? "Dashboard created!" : reason);
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }

  private void processRenameDashboard(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
    response.setContentType("application/txt");
    PrintWriter pw = response.getWriter();
    try {
      String reason = rcUtils.renameDashboard(baseRequest.getHeader("name"), baseRequest.getHeader("newName"));
      pw.println(reason == null ? "Dashboard renamed!" : reason);
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }

  private void processRemoveDashboard(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
    response.setContentType("application/txt");
    PrintWriter pw = response.getWriter();
    try {
      String reason = rcUtils.removeDashboard(baseRequest.getHeader("name"));
      pw.println(reason == null ? "Dashboard removed!" : reason);
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processGetDashboards(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    response.setContentType("application/json");
    PrintWriter pw = response.getWriter();
    try {
      pw.println(rcUtils.getDashboards().toString());
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
  }
  
  private void processChangeDash(Request baseRequest, HttpServletResponse response, boolean add)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    String activity = baseRequest.getHeader("activity");
    String target = baseRequest.getHeader("dashboard");
    response.setContentType("application/txt");
    String successMsg = "Activity successfully " + (add ? "added" : "removed") + '!';
    PrintWriter pw = response.getWriter();
    try {
      String reason = (add ? rcUtils.addToDashboard(activity, target) : rcUtils.removeFromDashboard(activity, target));
      pw.println(reason == null ? successMsg : reason);
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
  }
  
  private void processSaveFilter(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
  	PrintWriter pw = response.getWriter();
  	String name = baseRequest.getHeader("name");
  	String dashboard = baseRequest.getHeader("dashboard");
  	String pattern = baseRequest.getHeader("pattern");
  	String dateOpt = baseRequest.getHeader("dateOpt");
  	String startDate = baseRequest.getHeader("startDate");
  	String endDate = baseRequest.getHeader("endDate");
  	String rec = baseRequest.getHeader("records");
  	String smin = baseRequest.getHeader("dmin");
    String smax = baseRequest.getHeader("dmax");
  	int records = Integer.MAX_VALUE;
  	int minDist = 0;
  	int maxDist = Integer.MAX_VALUE;
    try {
      if (rec != null && rec.length() > 0) {
        records = Integer.parseInt(rec);
      }
      if (smin != null && smin.length() > 0) {
        minDist = Integer.parseInt(smin);
      }
      if (smax != null && smax.length() > 0) {
        maxDist = Integer.parseInt(smax);
      }
    } catch (NumberFormatException ignore) {
      // silent catch
    }
    StringBuffer types = new StringBuffer();
    if ("true".equals(baseRequest.getHeader("run"))) {
    	types.append("run,");
    }
    if ("true".equals(baseRequest.getHeader("trail"))) {
    	types.append("trail,");
    }
    if ("true".equals(baseRequest.getHeader("uphill"))) {
    	types.append("uphill,");
    }
    if ("true".equals(baseRequest.getHeader("hike"))) {
    	types.append("hike,");
    }
    if ("true".equals(baseRequest.getHeader("walk"))) {
    	types.append("walk,");
    }
    if ("true".equals(baseRequest.getHeader("other"))) {
    	types.append("other,");
    }
    try {
      if (new Integer(dateOpt) < 6) {
        startDate = dateOpt;
        endDate = "";
      }
    } catch (Exception ignore) {
      // silent catch
    }
  	try {
  		String status = rcUtils.addPreset(name, types.toString(), pattern, startDate, endDate, minDist, maxDist, records, dashboard);
  		pw.println(status == null ? "Preset added" : status);
  	} finally {
  		pw.flush();
  	}
  	response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processRenameFilter(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
  	PrintWriter pw = response.getWriter();
  	try {
  		String status = rcUtils.renamePreset(baseRequest.getHeader("name"), baseRequest.getHeader("newName"));
  		pw.println(status == null ? "Preset renamed" : status);
  	} finally {
  		pw.flush();
  	}
  	response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processRemoveFilter(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
  	PrintWriter pw = response.getWriter();
  	try {
  		String status = rcUtils.removePreset(baseRequest.getHeader("name"));
  		pw.println(status == null ? "Preset removed" : status);
  	} finally {
  		pw.flush();
  	}
  	response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processGetFilters(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    response.setContentType("application/json");
    PrintWriter pw = response.getWriter();
    JSONObject result = new JSONObject();
    try {
      result.put("presets", rcUtils.getPresets());
      pw.println(result.toString());
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
  }
  
  private void processReorder(Request baseRequest, HttpServletResponse response, int option)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
    PrintWriter pw = response.getWriter();
    try {
      String status = rcUtils.reorder(baseRequest.getHeader("elements"), option);
      pw.println(status == null ? "Order saved" : status);
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }

  private void processSetFeatures(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    resetCache();
    List<String> links = new ArrayList<String>(4);
    PrintWriter pw = response.getWriter();
    try {
      String linkStr = baseRequest.getHeader("links");
      if (linkStr != null) {
        StringTokenizer st = new StringTokenizer(linkStr, ",", false);
        while (st.hasMoreTokens()) {
          links.add(st.nextToken());
        }
      }
      String status = rcUtils.setFeatures(baseRequest.getHeader("activity"), baseRequest.getHeader("descr"), links);
      pw.println(status == null ? "Activity modified" : status);
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processRemoveCookie(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
  	if (!isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
  	resetCache();
    PrintWriter pw = response.getWriter();    
    try {
    	Cookie[] cookies = baseRequest.getCookies();
  		if (cookies != null) {
  			for (Cookie cookie : cookies) {
  				rcUtils.removeCookie(cookie);
  			}
  		}
      pw.println("Logged out!");
    } finally {
      pw.flush();
    }
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
    resetCache();
  }
  
  private void processGetComps(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    String activity = baseRequest.getHeader("activity");
    boolean ext = "true".equals(baseRequest.getHeader("ext"));
    if (ext && !isLoggedIn(baseRequest)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      baseRequest.setHandled(true);
      return;
    }
    response.setContentType("application/json");
    PrintWriter pw = response.getWriter();    
    try {
      JSONObject json = rcUtils.getCompOptions(activity, ext);
      if (json == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      } else {
        pw.println(json.toString());
        response.setStatus(HttpServletResponse.SC_OK);
      }
    } finally {
      pw.flush();
    }
    baseRequest.setHandled(true);
  }
  
  private void processGetSplitsAndDist(Request baseRequest, HttpServletResponse response)
      throws IOException, ServletException {
    String activity = baseRequest.getHeader("activity");
    response.setContentType("application/json");
    PrintWriter pw = response.getWriter();    
    try {
      JSONObject json = rcUtils.getSplitsAndDist(activity);
      if (json == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      } else {
        pw.println(json.toString());
        response.setStatus(HttpServletResponse.SC_OK);
      }
    } finally {
      pw.flush();
    }
    baseRequest.setHandled(true);
  }
  
	private boolean isAllowed(Request baseRequest) {
		String origin = baseRequest.getHeader("Origin");
		if (origin == null) {
			origin = baseRequest.getHeader("Referer");
		}
		if (origin == null) {
			return false;
		}
		int start = -1;
		for (int i = 5; i < origin.length(); ++i) {
			if (origin.charAt(i) != '/') {
				start = i;
				break;
			}
		}
		if (start == -1) {
			return false;
		}
		for (int i = start + 1; i < origin.length(); ++i) {
			if (origin.charAt(i) == ':' || origin.charAt(i) == '/') {
				return isAllowed(origin.substring(start, i));
			}
		}
		return false;
	}

  public void handle(String target, Request baseRequest, HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {
  	if ("GET".equals(baseRequest.getMethod()) && target.length() > 1) {
  		if (target.startsWith("/compare")) {
  			processGetComparison(baseRequest, response);
  		} else {
  			processGetActivity(target.substring(1), baseRequest, response);
  		}
  	}
		if ("POST".equals(baseRequest.getMethod())) {
			if (target.startsWith("/fa/") && target.length() > 4) {
				processFetchActivity(target, baseRequest, response);
			} else if (target.startsWith("/upload") && target.length() > 8) {
				processUpload(target, baseRequest, request, response);
			} else if ("/best".equalsIgnoreCase(target)) {
				processBest(baseRequest, response);
			} else if ("/bestSplits".equalsIgnoreCase(target)) {
				processBestSplits(baseRequest, response);
			} else if ("/fetch".equalsIgnoreCase(target)) {
				processFetch(baseRequest, response);
			} else if ("/rescanActivities".equalsIgnoreCase(target)) {
				processRescan(baseRequest, response);
			} else if ("/editActivity".equalsIgnoreCase(target)) {
				processEdit(baseRequest, response);
			} else if ("/deleteActivity".equalsIgnoreCase(target)) {
				processDelete(baseRequest, response);
			} else if ("/compare".equalsIgnoreCase(target)) {
				processCompare(baseRequest, response);
			} else if ("/login".equalsIgnoreCase(target)) {
				processLogin(baseRequest, response);
			} else if ("/checkCookie".equalsIgnoreCase(target)) {
				checkCookie(baseRequest, response);
			} else if ("/revert".equalsIgnoreCase(target)) {
			  processRevert(baseRequest, response);
			} else if ("/coords".equalsIgnoreCase(target)) {
			  processGetGoords(baseRequest, response);
			} else if ("/addDash".equalsIgnoreCase(target)) {
			  processAddDashboard(baseRequest, response);
			} else if ("/renameDash".equalsIgnoreCase(target)) {
        processRenameDashboard(baseRequest, response);
      } else if ("/removeDash".equalsIgnoreCase(target)) {
			  processRemoveDashboard(baseRequest, response);
			} else if ("/getDash".equalsIgnoreCase(target)) {
			  processGetDashboards(baseRequest, response);
			} else if ("/addToDash".equalsIgnoreCase(target)) {
			  processChangeDash(baseRequest, response, true);
			} else if ("/removeFromDash".equalsIgnoreCase(target)) {
			  processChangeDash(baseRequest, response, false);
			} else if ("/saveFilter".equalsIgnoreCase(target)) {
			  processSaveFilter(baseRequest, response);
			} else if ("/renameFilter".equalsIgnoreCase(target)) {
			  processRenameFilter(baseRequest, response);
			} else if ("/removeFilter".equalsIgnoreCase(target)) {
        processRemoveFilter(baseRequest, response);
      } else if ("/getFilters".equalsIgnoreCase(target)) {
        processGetFilters(baseRequest, response);
      } else if ("/savePresetOrder".equalsIgnoreCase(target)) {
        processReorder(baseRequest, response, 1);
      } else if ("/saveDashOrder".equalsIgnoreCase(target)) {
        processReorder(baseRequest, response, 2);
      } else if ("/setFeatures".equalsIgnoreCase(target)) {
        processSetFeatures(baseRequest, response);
      } else if ("/removeCookie".equalsIgnoreCase(target)) {
      	processRemoveCookie(baseRequest, response);
      } else if ("/getCompOptions".equalsIgnoreCase(target)) {
        processGetComps(baseRequest, response);
      } else if ("/getSplitsAndDist".equals(target)) {
        processGetSplitsAndDist(baseRequest, response);
      } else if ("/resetCache".equals(target)) {
        resetCache();
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
      } else if ("/checkCache".equals(target)) {
        chechCacheUpToDate(baseRequest, response);
        baseRequest.setHandled(true);
      }
		}
  }
}
