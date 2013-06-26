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

package com.google.api.services.samples.youtube.cmdline.youtube_cmdline_addfeaturedvideo_sample;

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
import com.google.api.services.youtube.model.*;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * This program adds a featured video to a channel via the Invideo Programming API.
 *
 * @author Ikai Lan <ikai@google.com>
 */
public class AddFeaturedVideo {

    /**
     * Global instance of the HTTP transport.
     */
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    /**
     * Global instance of Youtube object to make all API requests.
     */
    private static YouTube youtube;


    /**
     * Authorizes the installed application to access user's protected data.
     *
     * @param scopes list of scopes needed to run youtube upload.
     */
    private static Credential authorize(List<String> scopes) throws IOException {

        // Load client secrets.
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY, AddFeaturedVideo.class.getResourceAsStream("/client_secrets.json"));

        // Checks that the defaults have been replaced (Default = "Enter X here").
        if (clientSecrets.getDetails().getClientId().startsWith("Enter")
                || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
            System.out.println(
                    "Enter Client ID and Secret from https://code.google.com/apis/console/?api=youtube"
                            + "into youtube-cmdline-addfeaturedvideo-sample/src/main/resources/client_secrets.json");
            System.exit(1);
        }

        // Set up file credential store.
        FileCredentialStore credentialStore = new FileCredentialStore(
                new File(System.getProperty("user.home"), ".credentials/youtube-api-addfeaturedvideo.json"),
                JSON_FACTORY);

        // Set up authorization code flow.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes).setCredentialStore(credentialStore)
                .build();

        // Build the local server and bind it to port 8080
        LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(8080).build();

        // Authorize.
        return new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user");
    }

    /**
     * This is a very simple code sample that looks up a user's channel, then features the most recently
     * uploaded video in the bottom left hand corner of every single video in the channel.
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
                    "youtube-cmdline-addfeaturedvideo-sample").build();

            // Fetch the user's channel. We also fetch the uploads playlist so we can use this later
            // to find the most recently uploaded video
            ChannelListResponse channelListResponse = youtube.channels().list("id,contentDetails")
                    .setMine(true)
                    .setFields("items(contentDetails/relatedPlaylists/uploads,id)")
                    .execute();

            // This assumes the user has a channel already. If the user does not have a channel, this should
            // throw a GoogleJsonResponseException explaining the issue
            Channel myChannel = channelListResponse.getItems().get(0);
            String channelId = myChannel.getId();
            String uploadsPlaylistId = myChannel.getContentDetails().getRelatedPlaylists().getUploads();

            // Fetch the most recently uploaded video
            PlaylistItemListResponse playlistItemListResponse = youtube.playlistItems().list("snippet")
                    .setPlaylistId(uploadsPlaylistId)
                    .setFields("items/snippet")
                    .execute();

            String featuredVideoId;
            if (playlistItemListResponse.getItems().isEmpty()) {
                // There are no videos on the channel. Therefore, we cannot feature a video. Exit.
                System.out.println("Channel contains no videos. Featuring a default video instead from the Google Developers channel.");
                featuredVideoId = "w4eiUiauo2w";
            } else {
                // The latest video should be the first video in the playlist response
                PlaylistItem featuredVideo = playlistItemListResponse.getItems().get(0);
                featuredVideoId = featuredVideo.getSnippet()
                        .getResourceId()
                        .getVideoId();

                System.out.println("Featuring video: " + featuredVideo.getSnippet().getTitle());
            }

            // Feature this video on the channel via the Invideo programming API
            // This describes the position of the video. Valid positions are bottomLeft, bottomRight, topLeft and
            // topRight
            InvideoPosition invideoPosition = new InvideoPosition();
            invideoPosition.setCornerPosition("bottomLeft");
            invideoPosition.setType("corner");

            // The allowed offsets are offsetFromEnd and offsetFromStart, with offsetMs being an offset in milliseconds
            InvideoTiming invideoTiming = new InvideoTiming();
            invideoTiming.setOffsetMs(BigInteger.valueOf(15000l));
            invideoTiming.setType("offsetFromEnd");

            // Represents the type of promotion. In this case, a video with a video ID
            PromotedItemId promotedItemId = new PromotedItemId();
            promotedItemId.setType("video");
            promotedItemId.setVideoId(featuredVideoId);

            // Construct the Invidideo promotion
            InvideoPromotion invideoPromotion = new InvideoPromotion();
            invideoPromotion.setPosition(invideoPosition);
            invideoPromotion.setTiming(invideoTiming);
            invideoPromotion.setItems(Lists.newArrayList(promotedItemId));

            // Now let's add the invideo promotion to the channel
            Channel channel = new Channel();
            channel.setId(channelId);
            channel.setInvideoPromotion(invideoPromotion);

            // Make the API call
            Channel updateChannelResponse = youtube.channels()
                    .update("invideoPromotion", channel)
                    .execute();

            // Print out returned results.
            System.out.println("\n================== Updated Channel Information ==================\n");
            System.out.println("\t- Channel ID: " + updateChannelResponse.getId());

            InvideoPromotion promotion = updateChannelResponse.getInvideoPromotion();
            System.out.println("\t- Invideo promotion video ID: " + promotion.getItems()
                    .get(0)
                    .getVideoId());
            System.out.println("\t- Promotion position: " + promotion.getPosition().getCornerPosition());
            System.out.println("\t- Promotion timing: " + promotion.getTiming().getOffsetMs()
                    + " Offset: " + promotion.getTiming().getType());
        } catch (GoogleJsonResponseException e) {
            System.err.println("GoogleJsonResponseException code: " + e.getDetails().getCode() + " : "
                    + e.getDetails().getMessage());
            e.printStackTrace();

        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
