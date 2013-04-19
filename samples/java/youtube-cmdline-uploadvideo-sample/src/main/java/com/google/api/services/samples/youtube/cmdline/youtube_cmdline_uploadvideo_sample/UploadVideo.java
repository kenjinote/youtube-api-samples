/*
 * Copyright (c) 2012 Google Inc.
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

package com.google.api.services.samples.youtube.cmdline.youtube_cmdline_uploadvideo_sample;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.common.collect.Lists;

/**
 * Demo of uploading a video to a user's account using the YouTube Data API (V3) with OAuth2 for
 * authorization.
 *
 *  TODO: PLEASE NOTE, YOU MUST ADD YOUR VIDEO FILES TO THE PROJECT FOLDER TO UPLOAD THEM WITH THIS
 * APPLICATION!
 *
 * @author Jeremy Walker
 */
public class UploadVideo {

  /** Global instance of the HTTP transport. */
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  /** Global instance of Youtube object to make all API requests. */
  private static YouTube youtube;

  /* Global instance of the format used for the video being uploaded (MIME type). */
  private static String VIDEO_FILE_FORMAT = "video/*";

  /**
   * Authorizes the installed application to access user's protected data.
   *
   * @param scopes list of scopes needed to run youtube upload.
   */
  private static Credential authorize(List<String> scopes) throws Exception {

    // Load client secrets.
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
        JSON_FACTORY, UploadVideo.class.getResourceAsStream("/client_secrets.json"));

    // Checks that the defaults have been replaced (Default = "Enter X here").
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.out.println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=youtube"
          + "into youtube-cmdline-uploadvideo-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }

    // Set up file credential store.
    FileCredentialStore credentialStore = new FileCredentialStore(
        new File(System.getProperty("user.home"), ".credentials/youtube-api-uploadvideo.json"),
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

    // Scope required to upload to YouTube.
    List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload");

    try {
      // Authorization.
      Credential credential = authorize(scopes);

      // YouTube object used to make all API requests.
      youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
          "youtube-cmdline-uploadvideo-sample").build();

      // We get the user selected local video file to upload.
      File videoFile = getVideoFromUser();
      System.out.println("You chose " + videoFile + " to upload.");

      // Add extra information to the video before uploading.
      Video videoObjectDefiningMetadata = new Video();

      /*
       * Set the video to public, so it is available to everyone (what most people want). This is
       * actually the default, but I wanted you to see what it looked like in case you need to set
       * it to "unlisted" or "private" via API.
       */
      VideoStatus status = new VideoStatus();
      status.setPrivacyStatus("public");
      videoObjectDefiningMetadata.setStatus(status);

      // We set a majority of the metadata with the VideoSnippet object.
      VideoSnippet snippet = new VideoSnippet();

      /*
       * The Calendar instance is used to create a unique name and description for test purposes, so
       * you can see multiple files being uploaded. You will want to remove this from your project
       * and use your own standard names.
       */
      Calendar cal = Calendar.getInstance();
      snippet.setTitle("Test Upload via Java on " + cal.getTime());
      snippet.setDescription(
          "Video uploaded via YouTube Data API V3 using the Java library " + "on " + cal.getTime());

      // Set your keywords.
      List<String> tags = new ArrayList<String>();
      tags.add("test");
      tags.add("example");
      tags.add("java");
      tags.add("YouTube Data API V3");
      tags.add("erase me");
      snippet.setTags(tags);

      // Set completed snippet to the video object.
      videoObjectDefiningMetadata.setSnippet(snippet);

      InputStreamContent mediaContent = new InputStreamContent(
          VIDEO_FILE_FORMAT, new BufferedInputStream(new FileInputStream(videoFile)));
      mediaContent.setLength(videoFile.length());

      /*
       * The upload command includes: 1. Information we want returned after file is successfully
       * uploaded. 2. Metadata we want associated with the uploaded video. 3. Video file itself.
       */
      YouTube.Videos.Insert videoInsert = youtube.videos()
          .insert("snippet,statistics,status", videoObjectDefiningMetadata, mediaContent);

      // Set the upload type and add event listener.
      MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();

      /*
       * Sets whether direct media upload is enabled or disabled. True = whole media content is
       * uploaded in a single request. False (default) = resumable media upload protocol to upload
       * in data chunks.
       */
      uploader.setDirectUploadEnabled(false);

      MediaHttpUploaderProgressListener progressListener = new MediaHttpUploaderProgressListener() {
        public void progressChanged(MediaHttpUploader uploader) throws IOException {
          switch (uploader.getUploadState()) {
            case INITIATION_STARTED:
              System.out.println("Initiation Started");
              break;
            case INITIATION_COMPLETE:
              System.out.println("Initiation Completed");
              break;
            case MEDIA_IN_PROGRESS:
              System.out.println("Upload in progress");
              System.out.println("Upload percentage: " + uploader.getProgress());
              break;
            case MEDIA_COMPLETE:
              System.out.println("Upload Completed!");
              break;
            case NOT_STARTED:
              System.out.println("Upload Not Started!");
              break;
          }
        }
      };
      uploader.setProgressListener(progressListener);

      // Execute upload.
      Video returnedVideo = videoInsert.execute();

      // Print out returned results.
      System.out.println("\n================== Returned Video ==================\n");
      System.out.println("  - Id: " + returnedVideo.getId());
      System.out.println("  - Title: " + returnedVideo.getSnippet().getTitle());
      System.out.println("  - Tags: " + returnedVideo.getSnippet().getTags());
      System.out.println("  - Privacy Status: " + returnedVideo.getStatus().getPrivacyStatus());
      System.out.println("  - Video Count: " + returnedVideo.getStatistics().getViewCount());

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

  /**
   * Gets the user selected local video file to upload.
   */
  private static File getVideoFromUser() throws IOException {
    File[] listOfVideoFiles = getLocalVideoFiles();
    return getUserChoice(listOfVideoFiles);
  }

  /**
   * Gets an array of videos in the current directory.
   */
  private static File[] getLocalVideoFiles() throws IOException {

    File currentDirectory = new File(".");
    System.out.println("Video files from " + currentDirectory.getAbsolutePath() + ":");

    // Filters out video files. This list of video extensions is not comprehensive.
    FilenameFilter videoFilter = new FilenameFilter() {
      public boolean accept(File dir, String name) {
        String lowercaseName = name.toLowerCase();
        if (lowercaseName.endsWith(".webm") || lowercaseName.endsWith(".flv")
            || lowercaseName.endsWith(".f4v") || lowercaseName.endsWith(".mov")
            || lowercaseName.endsWith(".mp4")) {
          return true;
        } else {
          return false;
        }
      }
    };
    return currentDirectory.listFiles(videoFilter);
  }

  /**
   * Outputs video file options to the user, records user selection, and returns the video (File
   * object).
   *
   * @param videoFiles Array of video File objects
   */
  private static File getUserChoice(File videoFiles[]) throws IOException {

    if (videoFiles.length < 1) {
      throw new IllegalArgumentException("No video files in this directory.");
    }

    for (int i = 0; i < videoFiles.length; i++) {
      System.out.println(" " + i + " = " + videoFiles[i].getName());
    }

    BufferedReader bReader = new BufferedReader(new InputStreamReader(System.in));
    String inputChoice;

    do {
      System.out.print("Choose the number of the video file you want to upload: ");
      inputChoice = bReader.readLine();
    } while (!isValidIntegerSelection(inputChoice, videoFiles.length));

    return videoFiles[Integer.parseInt(inputChoice)];
  }

  /**
   * Checks if string contains a valid, positive integer that is less than max. Please note, I am
   * not testing the upper limit of an integer (2,147,483,647). I just go up to 999,999,999.
   *
   * @param input String to test.
   * @param max Integer must be less then this Maximum number.
   */
  public static boolean isValidIntegerSelection(String input, int max) {
    if (input.length() > 9) return false;

    boolean validNumber = false;
    // Only accepts positive numbers of up to 9 numbers.
    Pattern intsOnly = Pattern.compile("^\\d{1,9}$");
    Matcher makeMatch = intsOnly.matcher(input);

    if (makeMatch.find()) {
      int number = Integer.parseInt(makeMatch.group());
      if ((number >= 0) && (number < max)) {
        validNumber = true;
      }
    }
    return validNumber;
  }
}