/*
 * Copyright (c) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.services.samples.youtube.cmdline.youtube_cmdline_liststreams_sample;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.LiveStream;
import com.google.api.services.youtube.model.LiveStreamList;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Demo of listing streams using the YouTube Live API (V3) with OAuth2 for authorization.
 *
 * @author Ibrahim Ulukaya
 */
public class ListStreams {

  /** Global instance of the HTTP transport. */
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  /** Global instance of Youtube object to make all API requests. */
  private static YouTube youtube;

  /**
   * @param scopes list of scopes needed to run YouTube upload.
   * @return authorized credential
   * @throws IOException
   */
  private static Credential authorize(List<String> scopes) throws Exception {

    // Load client secrets.
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
        JSON_FACTORY, ListStreams.class.getResourceAsStream("/client_secrets.json"));

    // Checks that the defaults have been replaced (Default = "Enter X here").
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.out.println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=youtube"
          + "into youtube-cmdline-liststreams-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }

    // Set up file credential store.
    FileCredentialStore credentialStore = new FileCredentialStore(
        new File(System.getProperty("user.home"), ".credentials/youtube-api-liststreams.json"),
        JSON_FACTORY);

    // Set up authorization code flow.
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes)
        .setCredentialStore(credentialStore)
        .build();

    // Build the local server and bind it to port 9000
    LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(8080).build();

    // Authorize.
    return new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user");
  }

  /**
   * Subscribes user's YouTube account to a user selected channel using OAuth2 for authentication.
   */
  public static void main(String[] args) {

    // Scope required to read from YouTube.
    List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.readonly");

    try {
      // Authorization.
      Credential credential = authorize(scopes);

      // YouTube object used to make all API requests.
      youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
          .setApplicationName("youtube-cmdline-liststreams-sample")
          .build();

      // List streams request is created.
      YouTube.LiveStreams.List livestreamRequest = youtube.liveStreams().list("id,snippet");

      // Modify results to have only user's streams.
      livestreamRequest.setMine(true);

      // List request is executed and list of streams are returned
      LiveStreamList returnedListResponse = livestreamRequest.execute();

      // Get the list of streams associated with the user.
      List<LiveStream> returnedList = returnedListResponse.getItems();

      // Print out returned results.
      System.out.println("\n================== Returned Streams ==================\n");
      for (LiveStream stream : returnedList) {
        System.out.println("  - Id: " + stream.getId());
        System.out.println("  - Title: " + stream.getSnippet().getTitle());
        System.out.println("  - Description: " + stream.getSnippet().getDescription());
        System.out.println("  - Published At: " + stream.getSnippet().getPublishedAt());
        System.out.println("\n-------------------------------------------------------------\n");
      }

    } catch (GoogleJsonResponseException e) {
      System.err.println("GoogleJsonResponseException code: " + e.getDetails().getCode() + " : "
          + e.getDetails().getMessage());
      e.printStackTrace();

    } catch (IOException e) {
      System.err.println("IOException: " + e.getMessage());
      e.printStackTrace();
    } catch (Throwable t) {
      System.err.println("Throwable: " + t.getMessage());
      t.printStackTrace();
    }
  }
}