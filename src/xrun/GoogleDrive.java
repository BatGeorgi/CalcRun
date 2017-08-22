/*
 * Copyright (c) 2017 by Bosch Software Innovations GmbH
 * http://www.bosch-si.com
 * All rights reserved,
 * also regarding any disposal, exploitation, reproduction,
 * editing, distribution, as well as in the event of
 * applications for industrial property rights.
 *
 * This software is the confidential and proprietary information
 * of Bosch Software Innovations GmbH. You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you
 * entered into with Bosch Software Innovations.
 */
package xrun;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

public class GoogleDrive {

  private static final String APPLICATION_NAME = "Drive API XRunCalc";
  private static final java.io.File DATA_STORE_DIR = new java.io.File(
      System.getProperty("user.home"), ".credentials/drive-java-quickstart");
  private static FileDataStoreFactory DATA_STORE_FACTORY;
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static HttpTransport HTTP_TRANSPORT;
  private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);

  static {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  private static Credential authorize() throws IOException {
      InputStream in = GoogleDrive.class.getResourceAsStream("/client_secret.json");
      GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
      GoogleAuthorizationCodeFlow flow =
              new GoogleAuthorizationCodeFlow.Builder(
                      HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
              .setDataStoreFactory(DATA_STORE_FACTORY)
              .setAccessType("offline")
              .build();
      Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("7sparkle77");
      System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
      return credential;
  }

  private static Drive getDriveService() throws IOException {
      Credential credential = authorize();
      return new Drive.Builder(
              HTTP_TRANSPORT, JSON_FACTORY, credential)
              .setApplicationName(APPLICATION_NAME)
              .build();
  }
  
  private Drive service;
  private static final String BASE_FOLDER_ID = "0B22GzCU9umn-SFJlOUd4REJGQUU";
  private static final String GPX_FOLDER_ID = "0B22GzCU9umn-bndYWWsxVmhXeUk";
  
  GoogleDrive() {
    try {
      service = getDriveService();
    } catch (IOException ioe) {
      System.out.println("Error getting drive service");
      ioe.printStackTrace();
    }
  }

  public static void main(String[] args) throws IOException {
      // Build a new authorized API client service.
      Drive service = getDriveService();

      /*System.out.println(service.getBaseUrl());
      File file = service.files().get("0B22GzCU9umn-Wi1XanBNTGp0Ukk").execute();
      for (Entry<String, Object> e : file.entrySet()) {
        System.out.println(e.getValue());
      }
      System.out.println(file);
      System.out.println(file.getParents());
      System.out.println(file.entrySet());*/
      
      /*String folderId = "0B22GzCU9umn-c0pGQVZONkRvMTg";
      File fileMetadata = new File();
      fileMetadata.setName("bai_hui.xml");
      fileMetadata.setParents(Collections.singletonList(folderId));
      java.io.File filePath = new java.io.File("C:/GBV/bai_hui.xml");
      FileContent mediaContent = new FileContent("text/xml", filePath);
      File up = service.files().create(fileMetadata, mediaContent)
              .setFields("id, parents")
              .execute();
      System.out.println("File ID: " + up.getId());*/
  }
}
