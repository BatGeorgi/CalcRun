package xrun.storage;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

public class GoogleDriveStorage {

  private static final String         APPLICATION_NAME = "Drive API XRunCalc";
  private static final java.io.File   DATA_STORE_DIR   = new java.io.File(
      System.getProperty("user.home"), ".credentials/drive-java-quickstart");
  private static FileDataStoreFactory DATA_STORE_FACTORY;
  private static final JsonFactory    JSON_FACTORY     = JacksonFactory.getDefaultInstance();
  private static HttpTransport        HTTP_TRANSPORT;
  private static final List<String>   SCOPES           = Arrays.asList(DriveScopes.DRIVE);

  static {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private static final String BACKUP_FOLDER_ID = "0B22GzCU9umn-RkhmQkxzY09YdzQ";
  private static final String TRACKS_FOLDER_ID = "0B22GzCU9umn-eF9mVWpGeFMza3c";

  private Drive               service;

  private Credential authorize(java.io.File clientSecret) throws IOException {
    InputStream in = new FileInputStream(clientSecret);
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(DATA_STORE_FACTORY)
            .setAccessType("offline")
            .build();
    Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("7sparkle77");
    System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
    return credential;
  }

  private Drive getDriveService(java.io.File clientSecret) throws IOException {
    Credential credential = authorize(clientSecret);
    return new Drive.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, credential)
            .setApplicationName(APPLICATION_NAME)
            .build();
  }

  public GoogleDriveStorage(java.io.File clientSecret) {
    try {
      service = getDriveService(clientSecret);
    } catch (IOException ioe) {
      System.out.println("Error getting drive service");
      ioe.printStackTrace();
    }
  }

  public void backupDB(final java.io.File db, String pref) {
    final File fileMetadata = new File();
    Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("EET"));
    fileMetadata.setName(pref + '_' + cal.get(Calendar.YEAR) + '_' + (cal.get(Calendar.MONTH) + 1) + '_' +
        cal.get(Calendar.DATE) + '_' + cal.getTimeInMillis() + ".db");
    fileMetadata.setParents(Collections.singletonList(BACKUP_FOLDER_ID));
    final FileContent mediaContent = new FileContent("app/db", db);
    new Thread(new Runnable() {
      public void run() {
        try {
          service.files().create(fileMetadata, mediaContent).setFields("id, parents").execute();
        } catch (IOException e) {
          System.out.println("Error backuping db " + fileMetadata.getName());
          e.printStackTrace();
        }
      }
    }).start();
  }

  public void backupTrack(final java.io.File track) {
    final File fileMetadata = new File();
    fileMetadata.setName(track.getName());
    fileMetadata.setParents(Collections.singletonList(TRACKS_FOLDER_ID));
    final FileContent mediaContent = new FileContent("text/xml", track);
    new Thread(new Runnable() {
      public void run() {
        try {
          service.files().create(fileMetadata, mediaContent).setFields("id, parents").execute();
          if (!track.delete()) {
            track.deleteOnExit();
          }
        } catch (IOException e) {
          System.out.println("Error backuping track " + fileMetadata.getName());
          e.printStackTrace();
        }
      }
    }).start();
  }

}
