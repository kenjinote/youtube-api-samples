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

package com.google.api.services.samples.youtube.cmdline.youtube_cmdline_listbroadcasts_sample;

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
import com.google.api.services.youtube.model.LiveBroadcast;
import com.google.api.services.youtube.model.LiveBroadcastList;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Demo of listing broadcasts using the YouTube Live API (V3) with OAuth2 for authorization.
 *
 * @author Ibrahim Ulukaya
 */
public class ListBroadcasts {

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
        JSON_FACTORY, ListBroadcasts.class.getResourceAsStream("/client_secrets.json"));

    // Checks that the defaults have been replaced (Default = "Enter X here").
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.out.println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=youtube"
          + "into youtube-cmdline-listbroadcasts-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }

    // Set up file credential store.
    FileCredentialStore credentialStore = new FileCredentialStore(
        new File(System.getProperty("user.home"), ".credentials/youtube-api-listbroadcasts.json"),
        JSON_FACTORY);

    // Set up authorization code flow.
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes).setCredentialStore(credentialStore)
        .build();

    // Build the local server and bind it to port 9000
    LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(8080).build();

    // Authorize.
    return new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user");
  }

  /**
   * List user's broadcasts using OAuth2 for authentication.
   *
   * @param args command line args (not used).
   */
  public static void main(String[] args) {

    // Scope required to read from YouTube.
    List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.readonly");

    try {
      // Authorization.
      Credential credential = authorize(scopes);

      // YouTube object used to make all API requests.
      youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
          "youtube-cmdline-listbroadcasts-sample").build();

      // Create request to list broadcasts.
      YouTube.LiveBroadcasts.List liveBroadcastRequest =
          youtube.liveBroadcasts().list("id,snippet");

      // Modify results to have broadcasts in all states.
      liveBroadcastRequest.setBroadcastStatus("all");

      // List request is executed and list of broadcasts are returned
      LiveBroadcastList returnedListResponse = liveBroadcastRequest.execute();

      // Get the list of broadcasts associated with the user.
      List<LiveBroadcast> returnedList = returnedListResponse.getItems();

      // Print out returned results.
      System.out.println("\n================== Returned Broadcasts ==================\n");
      for (LiveBroadcast broadcast : returnedList) {
        System.out.println("  - Id: " + broadcast.getId());
        System.out.println("  - Title: " + broadcast.getSnippet().getTitle());
        System.out.println("  - Description: " + broadcast.getSnippet().getDescription());
        System.out.println("  - Published At: " + broadcast.getSnippet().getPublishedAt());
        System.out.println(
            "  - Scheduled Start Time: " + broadcast.getSnippet().getScheduledStartTime());
        System.out.println(
            "  - Scheduled End Time: " + broadcast.getSnippet().getScheduledEndTime());
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