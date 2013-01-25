package com.google.api.services.samples.youtube.cmdline.youtube_analytics_cmdline_report_sample;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtubeAnalytics.YoutubeAnalytics;
import com.google.api.services.youtubeAnalytics.model.ResultTable;
import com.google.api.services.youtubeAnalytics.model.ResultTable.ColumnHeaders;
import com.google.common.collect.Lists;


/**
 * Demo displaying YouTube metrics from a user's channel using the YouTube Data and YouTube
 * Analytics APIs.  It also uses OAuth2 for authorization.
 *
 * @author Christoph Schwab-Ganser and Jeremy Walker
 */
public class YouTubeAnalyticsReports {

  /** Global instance of the HTTP transport. */
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  /** Global instance of Youtube object to make general YouTube API requests. */
  private static YouTube youtube;

  /** Global instance of YoutubeAnalytics object to make analytic API requests. */
  private static YoutubeAnalytics analytics;

  /**
   * Authorizes the installed application to access user's protected YouTube data.
   *
   * @param scopes list of scopes needed to access general and analytic YouTube info.
   */
  private static Credential authorize(List<String> scopes) throws Exception {

    // Load client secrets.
    GoogleClientSecrets clientSecrets =
        GoogleClientSecrets.load(
            JSON_FACTORY,
            YouTubeAnalyticsReports.class.getResourceAsStream("/client_secrets.json"));

    // Checks that the defaults have been replaced (Default = "Enter X here").
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.err.println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=youtube"
          + "into youtube-analytics-cmdline-report-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }

    // Set up file credential store.
    FileCredentialStore credentialStore =
        new FileCredentialStore(
            new File(System.getProperty("user.home"),
                     ".credentials/youtube-analytics-api-report.json"),
                     JSON_FACTORY);

    // Set up authorization code flow.
    GoogleAuthorizationCodeFlow flow = 
        new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT,
                                                JSON_FACTORY,
                                                clientSecrets,
                                                scopes)
      .setCredentialStore(credentialStore).build();

    // Authorize.
    return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
  }

  /**
   * Authorizes user, gets user's default channel via YouTube Data API, and gets/prints stats on
   * user's channel using the YouTube Analytics API.
   *
   * @param args command line args (not used).
   */
  public static void main(String[] args) {

    // Scopes required to access YouTube general and analytics information.
    List<String> scopes = Lists.newArrayList(
        "https://www.googleapis.com/auth/yt-analytics.readonly",
        "https://www.googleapis.com/auth/youtube.readonly"
        );

    try {
      Credential credential = authorize(scopes);

      // YouTube object used to make all non-analytic API requests.
      youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName("youtube-analytics-api-report-example")
        .build();

      // YouTube object used to make all analytic API requests.
      analytics = new YoutubeAnalytics.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName("youtube-analytics-api-report-example")
        .build();

      // Constructs request to get channel id for current user.
      YouTube.Channels.List channelRequest = youtube.channels().list("id,snippet");
      channelRequest.setMine(true);
      channelRequest.setFields("items(id,snippet/title)");
      ChannelListResponse channels = channelRequest.execute();

      // List of channels associated with user.
      List<Channel> listOfChannels = channels.getItems();

      // Grab default channel which is always the first item in the list.
      Channel defaultChannel = listOfChannels.get(0);
      String channelId = defaultChannel.getId();

      PrintStream writer = System.out;
      if (channelId == null) {
        writer.println("No channel found.");
      } else {
        writer.println("Default Channel: " + defaultChannel.getSnippet().getTitle() +
            " ( " + channelId + " )\n");

        printData(writer, "Views Over Time.", executeViewsOverTimeQuery(analytics, channelId));
        printData(writer, "Top Videos", executeTopVideosQuery(analytics, channelId));
        printData(writer, "Demographics", executeDemographicsQuery(analytics, channelId));
      }
    } catch (IOException e) {
      System.err.println("IOException: " + e.getMessage());
      e.printStackTrace();
    } catch (Throwable t) {
      System.err.println("Throwable: " + t.getMessage());
      t.printStackTrace();
    }
  }

  /**
   * Returns the views and unique viewers per day.
   *
   * @param analytics the analytics service object used to access the API.
   * @param id the string id from which to retrieve data.
   * @return the response from the API.
   * @throws IOException if an API error occurred.
   */
  private static ResultTable executeViewsOverTimeQuery(YoutubeAnalytics analytics,
      String id) throws IOException {

    return analytics.reports()
        .query("channel==" + id,     // channel id
               "2012-01-01",         // Start date.
               "2012-01-14",         // End date.
               "views,uniques")      // Metrics.
        .setDimensions("day")
        .setSort("day")
        .execute();
  }

  /**
   * Returns the top video by views.
   *
   * @param analytics the analytics service object used to access the API.
   * @param id the string id from which to retrieve data.
   * @return the response from the API.
   * @throws IOException if an API error occurred.
   */
  private static ResultTable executeTopVideosQuery(YoutubeAnalytics analytics,
      String id) throws IOException {

    return analytics.reports()
        .query("channel==" + id,                          // channel id
               "2012-01-01",                              // Start date.
               "2012-08-14",                              // End date.
               "views,subscribersGained,subscribersLost") // Metrics.
        .setDimensions("video")
        .setSort("-views")
        .setMaxResults(10)
        .execute();
  }

  /**
   * Returns the demographics report
   *
   * @param analytics the analytics service object used to access the API.
   * @param id the string id from which to retrieve data.
   * @return the response from the API.
   * @throws IOException if an API error occurred.
   */
  private static ResultTable executeDemographicsQuery(YoutubeAnalytics analytics,
      String id) throws IOException {
    return analytics.reports()
        .query("channel==" + id,     // channel id
               "2007-01-01",         // Start date.
               "2012-08-14",         // End date.
               "viewerPercentage")   // Metrics.
        .setDimensions("ageGroup,gender")
        .setSort("-viewerPercentage")
        .execute();
  }

  /**
   * Prints the output from the API. The channel name is printed along with
   * each column name and all the data in the rows.
   * @param writer stream to output to
   * @param title title of the report
   * @param results data returned from the API.
   */
  private static void printData(PrintStream writer, String title, ResultTable results) {
    writer.println("Report: " + title);
    if (results.getRows() == null || results.getRows().isEmpty()) {
      writer.println("No results Found.");
    } else {

      // Print column headers.
      for (ColumnHeaders header : results.getColumnHeaders()) {
        writer.printf("%30s", header.getName());
      }
      writer.println();

      // Print actual data.
      for (List<Object> row : results.getRows()) {
        for (int colNum = 0; colNum < results.getColumnHeaders().size(); colNum++) {
          ColumnHeaders header = results.getColumnHeaders().get(colNum);
          Object column = row.get(colNum);
          if ("INTEGER".equals(header.getUnknownKeys().get("dataType"))) {
            long l = ((BigDecimal) column).longValue();
            writer.printf("%30d", l);
          } else if ("FLOAT".equals(header.getUnknownKeys().get("dataType"))) {
            writer.printf("%30f", column);
          }  else if ("STRING".equals(header.getUnknownKeys().get("dataType"))) {
            writer.printf("%30s", column);
          } else {
            // default output.
            writer.printf("%30s", column);
          }
        }
        writer.println();
      }
      writer.println();
    }
  }

}
