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

package com.google.api.services.samples.youtube.cmdline.youtube_cmdline_updatevideo_sample;

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
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo of updating a video by adding a tag, using the YouTube Data API (V3) with OAuth2 for
 * authorization.
 *
 * @author Ibrahim Ulukaya
 */
public class UpdateVideo {

  /** Global instance of the HTTP transport. */
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  /** Global instance of YouTube object to make all API requests. */
  private static YouTube youtube;

  /**
   * Authorizes the installed application to access user's protected data.
   *
   * @param scopes list of scopes needed to run YouTube upload.
   */
  private static Credential authorize(List<String> scopes) throws Exception {

    // Load client secrets.
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
        JSON_FACTORY, UpdateVideo.class.getResourceAsStream("/client_secrets.json"));

    // Checks that the defaults have been replaced (Default = "Enter X here").
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.out.println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=youtube"
          + "into youtube-cmdline-updatevideo-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }

    // Set up file credential store.
    FileCredentialStore credentialStore = new FileCredentialStore(
        new File(System.getProperty("user.home"), ".credentials/youtube-api-updatevideo.json"),
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
   * Uploads user selected video in the project folder to the user's YouTube account using OAuth2
   * for authentication.
   *
   * @param args command line args (not used).
   */
  public static void main(String[] args) {

    // An OAuth 2 access scope that allows for full read/write access.
    List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube");

    try {
      // Authorization.
      Credential credential = authorize(scopes);

      // YouTube object used to make all API requests.
      youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
          "youtube-cmdline-updatevideo-sample").build();

      // Get the user selected video Id.
      String videoId = getVideoIdFromUser();
      System.out.println("You chose " + videoId + " to update.");

      // Get the user selected tag for video.
      String tag = getTagFromUser();
      System.out.println("You chose " + tag + " as a tag.");

      // Create the video list request
      YouTube.Videos.List listVideosRequest = youtube.videos().list(videoId, "snippet");

      // Request is executed and video list response is returned
      VideoListResponse listResponse = listVideosRequest.execute();

      List<Video> videoList = listResponse.getItems();
      if (videoList.isEmpty()) {
        System.out.println("Can't find a video with video id: " + videoId);
        return;
      }

      // Since a unique video id is given, it will only return 1 video.
      Video video = videoList.get(0);
      VideoSnippet snippet = video.getSnippet();

      List<String> tags = snippet.getTags();

      // getTags() returns null if the video didn't have any tags, so we will check for this and
      // create a new list if needed
      if (tags == null) {
        tags = new ArrayList<String>(1);
        snippet.setTags(tags);
      }
      tags.add(tag);

      // Create the video update request
      YouTube.Videos.Update updateVideosRequest = youtube.videos().update("snippet", video);

      // Request is executed and updated video is returned
      Video videoResponse = updateVideosRequest.execute();

      // Print out returned results.
      System.out.println("\n================== Returned Video ==================\n");
      System.out.println("  - Title: " + videoResponse.getSnippet().getTitle());
      System.out.println("  - Tags: " + videoResponse.getSnippet().getTags());

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
   * Prompts for a tag from standard input and returns it.
   */
  private static String getTagFromUser() throws IOException {

    String title = "";

    System.out.print("Please enter a tag for your video: ");
    BufferedReader bReader = new BufferedReader(new InputStreamReader(System.in));
    title = bReader.readLine();

    if (title.length() < 1) {
      // If nothing is entered, defaults to "New Tag"
      title = "New Tag";
    }
    return title;
  }

  /*
   * Prompts for a video ID from standard input and returns it.
   */
  private static String getVideoIdFromUser() throws IOException {

    String title = "";

    System.out.print("Please enter a video Id to update: ");
    BufferedReader bReader = new BufferedReader(new InputStreamReader(System.in));
    title = bReader.readLine();

    if (title.length() < 1) {
      // If nothing is entered, exits
      System.out.print("Video Id can't be empty!");
      System.exit(1);
    }

    return title;
  }

}
