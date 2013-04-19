/*
 * Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.api.services.samples.youtube.cmdline.youtube_cmdline_topics_sample;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Thumbnail;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Demo of a semantic YouTube search getting a topic and search term from the user.  The class
 * calls the Freebase API to get a topics id based on user input, then passes that id along with
 * another user query term to the YouTube APIs.  The result is a list of videos based on a
 * semantic search.
 *
 * @author Jeremy Walker
 */
public class Topics {

  /** Global instance properties filename. */
  private static String PROPERTIES_FILENAME = "youtube.properties";

  /** Global instance of the HTTP transport. */
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  /** Global instance of the max number of videos we want returned. */
  private static final long NUMBER_OF_VIDEOS_RETURNED = 5;

  /** Global instance of the max number of topics we want returned. */
  private static final long NUMBER_OF_TOPICS_RETURNED = 5;

  /** Global instance of Youtube object to make all API requests. */
  private static YouTube youtube;

  /**
   * Method kicks off a search via the Freebase API for a topics id.  It initializes a YouTube
   * object to search for videos on YouTube (Youtube.Search.List) using that topics id to make the
   * search more specific.  The program then prints the names and thumbnails of each of the videos
   * (only first 5 videos).  Please note, user input is taken for both search on Freebase and on
   * YouTube.
   *
   * @param args command line args not used.
  */
  public static void main( String[] args ) {
    // Read the developer key from youtube.properties
    Properties properties = new Properties();
    try {
      InputStream in = Topics.class.getResourceAsStream("/" + PROPERTIES_FILENAME);
      properties.load(in);

    } catch (IOException e) {
      System.err.println("There was an error reading " + PROPERTIES_FILENAME + ": " + e.getCause()
          + " : " + e.getMessage());
      System.exit(1);
    }


    try {
      // Gets a topic id via the Freebase API based on user input.
      String topicsId = getTopicId();
      if(topicsId.length() < 1) {
        System.out.println("No topic id will be applied to your search.");
      }

      /*
       * Get query term from user.  The "search" parameter is just used as output to clarify that
       * we want a "search" term (vs. a "topics" term).
       */
      String queryTerm = getInputQuery("search");

      /*
       * The YouTube object is used to make all API requests.  The last argument is required, but
       * because we don't need anything initialized when the HttpRequest is initialized, we
       * override the interface and provide a no-op function.
       */
      youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpRequestInitializer() {
          public void initialize(HttpRequest request) throws IOException {}})
        .setApplicationName("youtube-cmdline-search-sample")
        .build();

      YouTube.Search.List search = youtube.search().list("id,snippet");
      /*
       * It is important to set your developer key from the Google Developer Console for
       * non-authenticated requests (found under the API Access tab at this link:
       * code.google.com/apis/). This is good practice and increases your quota.
       */
      String apiKey = properties.getProperty("youtube.apikey");
      search.setKey(apiKey);
      search.setQ(queryTerm);
      if(topicsId.length() > 0) {
        search.setTopicId(topicsId);
      }

      /*
       * We are only searching for videos (not playlists or channels).  If we were searching for
       * more, we would add them as a string like this: "video,playlist,channel".
       */
      search.setType("video");
      /*
       * This method reduces the info returned to only the fields we need.  It makes things more
       * efficient, because we are transmitting less data.
       */
      search.setFields("items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url)");
      search.setMaxResults(NUMBER_OF_VIDEOS_RETURNED);
      SearchListResponse searchResponse = search.execute();

      List<SearchResult> searchResultList = searchResponse.getItems();

      if(searchResultList != null) {
        prettyPrint(searchResultList.iterator(), queryTerm, topicsId);
      } else {
        System.out.println("There were no results for your query.");
      }
    } catch (GoogleJsonResponseException e) {
      System.err.println("There was a service error: " + e.getDetails().getCode() +
          " : " + e.getDetails().getMessage());
      e.printStackTrace();
    } catch (IOException e) {
      System.err.println("There was an IO error: " + e.getCause() + " : " + e.getMessage());
      e.printStackTrace();
    }
  }

  /*
   * Returns a query term (String) from user via the terminal.
   *
   * @param searchCategory This is for output to the user to clariy what info we need from them.
   */
  private static String getInputQuery(String searchCategory) throws IOException {

    String inputQuery = "";

    BufferedReader bReader = new BufferedReader(new InputStreamReader(System.in));

    do {
      System.out.print("Please enter a " + searchCategory + " term: ");
      inputQuery = bReader.readLine();
    } while(inputQuery.length() < 1);

    return inputQuery;
  }

  /**
   * The Java Freebase client library does not include search functionality, so we created a call
   * directly via URL.  We use jackson functionality to put the JSON response into a POJO (Plain
   * Old Java Object).  The additional classes to create the object from JSON were created based on
   * the JSON response to make it easier to get the values we need.  For more info on jackson
   * classes, please search on the term.
   */
  private static String getTopicId() throws IOException {

    /*
     * Returned as an empty string if we can't find a matching topicsId or there aren't any
     * results available.
     */
    String topicsId = "";

    /*
     * Get query term from user.  The "topics" parameter is just used as output to clarify that
     * we want a "topics" term (vs. a general "search" term).
     */
    String topicQuery = getInputQuery("topics");

    /*
     * Again, there isn't search functionality in the Freebase Java Library, so we have to call
     * directly against the URL.  Below we construct the proper URL, then use jackson classes to
     * convert the JSON into an object for reading.  You can find out more about the search calls
     * here: http://wiki.freebase.com/wiki/ApiSearch.
     */
    HttpClient httpclient = new DefaultHttpClient();
    List<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new BasicNameValuePair("query", topicQuery));
    params.add(new BasicNameValuePair("limit", Long.toString(NUMBER_OF_TOPICS_RETURNED)));

    String serviceURL = "https://www.googleapis.com/freebase/v1/search";
    String url = serviceURL + "?" + URLEncodedUtils.format(params, "UTF-8");

    HttpResponse httpResponse = httpclient.execute(new HttpGet(url));
    HttpEntity entity = httpResponse.getEntity();

    if (entity != null) {
        InputStream instream = entity.getContent();
        try {
          /*
           * Converts JSON to a Tree.  I could have specified extra classes and done an exact map
           * from JSON to POJO, but I was trying to keep the sample within one Java file.  If the
           * .get() function calls here and in getUserChoice() aren't your cup of tea, feel free
           * to create those classes and use them with the mapper.readValue() function.
           */
          ObjectMapper mapper = new ObjectMapper();
          JsonNode rootNode = mapper.readValue(instream, JsonNode.class);

          // Check that the response is valid.
          if(rootNode.get("status").asText().equals("200 OK")) {
            // I know the "result" field contains the list of results I need.
            ArrayNode arrayNodeResults = (ArrayNode) rootNode.get("result");
            // Only place we set the topicsId for a valid selection in this function.
            topicsId = getUserChoice(arrayNodeResults);
          }
        } finally {
          instream.close();
        }
    }
    return topicsId;
  }

  /**
   * Outputs topic search results to the user, records user selection, and returns topic id.
   *
   * @param freebaseResults ArrayNode object representing results of search.
   */
  private static String getUserChoice(ArrayNode freebaseResults) throws IOException {

    String freebaseId = "";

    if(freebaseResults.size() < 1) {
      return freebaseId;
    }

    for(int i = 0; i < freebaseResults.size(); i++) {
      JsonNode node = freebaseResults.get(i);
      System.out.print(" " + i + " = " + node.get("name").asText());
      if(node.get("notable") != null) {
        System.out.print(" (" + node.get("notable").get("name").asText() + ")");
      }
      System.out.println("");
    }

    BufferedReader bReader = new BufferedReader(new InputStreamReader(System.in));
    String inputChoice;

    do {
      System.out.print("Choose the number of the Freebase Node: ");
      inputChoice = bReader.readLine();
    } while (!isValidIntegerSelection(inputChoice, freebaseResults.size()));

    // Returns Topic id needed for YouTube Search.
    JsonNode node = freebaseResults.get(Integer.parseInt(inputChoice));
    freebaseId = node.get("mid").asText();
    return freebaseId;
  }

  /**
   * Checks if string contains a valid, positive integer that is less than max.  Please note, I am
   * not testing the upper limit of an integer (2,147,483,647).  I just go up to 999,999,999.
   *
   * @param input String to test.
   * @param max Integer must be less then this Maximum number.
   */
  public static boolean isValidIntegerSelection(String input, int max) {
    if (input.length() > 9)
      return false;

    boolean validNumber = false;
    // Only accepts positive numbers of up to 9 numbers.
    Pattern intsOnly = Pattern.compile("^\\d{1,9}$");
    Matcher makeMatch = intsOnly.matcher(input);

    if(makeMatch.find()){
      int number = Integer.parseInt(makeMatch.group());
      if((number >= 0) && (number < max)) {
        validNumber = true;
      }
    }
    return validNumber;
  }

  /*
   * Prints out all SearchResults in the Iterator.  Each printed line includes title, id, and
   * thumbnail.
   *
   * @param iteratorSearchResults Iterator of SearchResults to print
   * @param query Search query (String)
   */
  private static void prettyPrint(Iterator<SearchResult> iteratorSearchResults, String query, String topicsId) {

    System.out.println("\n=============================================================");
    System.out.println("   First " + NUMBER_OF_VIDEOS_RETURNED + " videos for search on \"" + query + "\" with Topics id: " + topicsId + ".");
    System.out.println("=============================================================\n");

    if(!iteratorSearchResults.hasNext()) {
      System.out.println(" There aren't any results for your query.");
    }

    while(iteratorSearchResults.hasNext()) {

      SearchResult singleVideo = iteratorSearchResults.next();
      ResourceId rId = singleVideo.getId();

      // Double checks the kind is video.
      if(rId.getKind().equals("youtube#video")) {
        Thumbnail thumbnail = singleVideo.getSnippet().getThumbnails().get("default");

        System.out.println(" Video Id" + rId.getVideoId());
        System.out.println(" Title: " + singleVideo.getSnippet().getTitle());
        System.out.println(" Thumbnail: " + thumbnail.getUrl());
        System.out.println("\n-------------------------------------------------------------\n");
      }
    }
  }
}
