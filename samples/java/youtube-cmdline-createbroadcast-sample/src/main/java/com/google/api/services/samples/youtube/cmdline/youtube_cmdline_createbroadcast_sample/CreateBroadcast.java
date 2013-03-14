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

package com.google.api.services.samples.youtube.cmdline.youtube_cmdline_createbroadcast_sample;

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
import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.LiveBroadcast;
import com.google.api.services.youtube.model.LiveBroadcastSnippet;
import com.google.api.services.youtube.model.LiveBroadcastStatus;
import com.google.api.services.youtube.model.LiveStream;
import com.google.api.services.youtube.model.LiveStreamCdn;
import com.google.api.services.youtube.model.LiveStreamSnippet;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Demo of inserting a broadcast and a stream then binding them together using the YouTube Live API
 * (V3) with OAuth2 for authorization.
 *
 * @author Ibrahim Ulukaya
 */
public class CreateBroadcast {

  /** Global instance of the HTTP transport. */
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  /** Global instance of Youtube object to make all API requests. */
  private static YouTube youtube;

  /**
   * Authorizes the installed application to access user's protected data.
   *
   * @param scopes list of scopes needed to run YouTube upload.
   * @return authorized credential
   * @throws IOException
   */
  private static Credential authorize(List<String> scopes) throws Exception {

    // Load client secrets.
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
        JSON_FACTORY, CreateBroadcast.class.getResourceAsStream("/client_secrets.json"));

    // Checks that the defaults have been replaced (Default = "Enter X here").
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.out.println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=youtube"
          + "into youtube-cmdline-createbroadcast-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }

    // Set up file credential store.
    FileCredentialStore credentialStore = new FileCredentialStore(
        new File(System.getProperty("user.home"), ".credentials/youtube-api-createbroadcast.json"),
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
   * Creates and inserts a Live Broadcast using OAuth2 for authentication.
   */
  public static void main(String[] args) {

    // Scope required to wrie data to YouTube.
    List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube");

    try {
      // Authorization.
      Credential credential = authorize(scopes);

      // YouTube object used to make all API requests.
      youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
          "youtube-cmdline-createbroadcast-sample").build();

      // Get the user's selected title for broadcast.
      String title = getBroadcastTitle();
      System.out.println("You chose " + title + " for broadcast title.");

      // Create a snippet with title, scheduled start and end times.
      LiveBroadcastSnippet broadcastSnippet = new LiveBroadcastSnippet();
      broadcastSnippet.setTitle(title);
      broadcastSnippet.setScheduledStartTime(new DateTime("2024-01-30T00:00:00.000Z"));
      broadcastSnippet.setScheduledEndTime(new DateTime("2024-01-31T00:00:00.000Z"));

      // Create LiveBroadcastStatus with privacy status.
      LiveBroadcastStatus status = new LiveBroadcastStatus();
      status.setPrivacyStatus("private");

      LiveBroadcast broadcast = new LiveBroadcast();
      broadcast.setKind("youtube#liveBroadcast");
      broadcast.setSnippet(broadcastSnippet);
      broadcast.setStatus(status);

      // Create the insert request
      YouTube.LiveBroadcasts.Insert liveBroadcastInsert =
          youtube.liveBroadcasts().insert("snippet,status", broadcast);

      // Request is executed and inserted broadcast is returned
      LiveBroadcast returnedBroadcast = liveBroadcastInsert.execute();

      // Print out returned results.
      System.out.println("\n================== Returned Broadcast ==================\n");
      System.out.println("  - Id: " + returnedBroadcast.getId());
      System.out.println("  - Title: " + returnedBroadcast.getSnippet().getTitle());
      System.out.println("  - Description: " + returnedBroadcast.getSnippet().getDescription());
      System.out.println("  - Published At: " + returnedBroadcast.getSnippet().getPublishedAt());
      System.out.println(
          "  - Scheduled Start Time: " + returnedBroadcast.getSnippet().getScheduledStartTime());
      System.out.println(
          "  - Scheduled End Time: " + returnedBroadcast.getSnippet().getScheduledEndTime());

      // Get the user's selected title for stream.
      title = getStreamTitle();
      System.out.println("You chose " + title + " for stream title.");

      // Create a snippet with title.
      LiveStreamSnippet streamSnippet = new LiveStreamSnippet();
      streamSnippet.setTitle(title);

      // Create content distribution network with format and ingestion type.
      LiveStreamCdn cdn = new LiveStreamCdn();
      cdn.setFormat("1080p");
      cdn.setIngestionType("rtmp");

      LiveStream stream = new LiveStream();
      stream.setKind("youtube#liveStream");
      stream.setSnippet(streamSnippet);
      stream.setCdn(cdn);

      // Create the insert request
      YouTube.LiveStreams.Insert liveStreamInsert =
          youtube.liveStreams().insert("snippet,cdn", stream);

      // Request is executed and inserted stream is returned
      LiveStream returnedStream = liveStreamInsert.execute();

      // Print out returned results.
      System.out.println("\n================== Returned Stream ==================\n");
      System.out.println("  - Id: " + returnedStream.getId());
      System.out.println("  - Title: " + returnedStream.getSnippet().getTitle());
      System.out.println("  - Description: " + returnedStream.getSnippet().getDescription());
      System.out.println("  - Published At: " + returnedStream.getSnippet().getPublishedAt());

      // Create the bind request
      YouTube.LiveBroadcasts.Bind liveBroadcastBind =
          youtube.liveBroadcasts().bind(returnedBroadcast.getId(), "id,contentDetails");

      // Set stream id to bind
      liveBroadcastBind.setStreamId(returnedStream.getId());

      // Request is executed and bound broadcast is returned
      returnedBroadcast = liveBroadcastBind.execute();

      // Print out returned results.
      System.out.println("\n================== Returned Bound Broadcast ==================\n");
      System.out.println("  - Broadcast Id: " + returnedBroadcast.getId());
      System.out.println(
          "  - Bound Stream Id: " + returnedBroadcast.getContentDetails().getBoundStreamId());

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

  /*
   * Returns a broadcast title (String) from user via the terminal.
   */
  private static String getBroadcastTitle() throws IOException {

    String title = "";

    System.out.print("Please enter a broadcast title: ");
    BufferedReader bReader = new BufferedReader(new InputStreamReader(System.in));
    title = bReader.readLine();

    if (title.length() < 1) {
      // If nothing is entered, defaults to "New Broadcast"
      title = "New Broadcast";
    }
    return title;
  }

  /*
   * Returns a stream title (String) from user via the terminal.
   */
  private static String getStreamTitle() throws IOException {

    String title = "";

    System.out.print("Please enter a stream title: ");
    BufferedReader bReader = new BufferedReader(new InputStreamReader(System.in));
    title = bReader.readLine();

    if (title.length() < 1) {
      // If nothing is entered, defaults to "New Stream"
      title = "New Stream";
    }
    return title;
  }

}