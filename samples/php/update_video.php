<?php

/**
 * This sample adds new tags to a YouTube video by:
 *
 * 1. Retrieving the video with "youtube.videos.list" method setting the "id" parameter
 * 2. Appending new tags to Video Resource's snippet.tags[] list
 * 3. Updating the video itself via youtube.videos.update API call, supplying a new video via a
 * binary upload to replace the old one.
 *
 * @author Ibrahim Ulukaya
*/

// Call set_include_path() as needed to point to your client library.
require_once 'Google_Client.php';
require_once 'contrib/Google_YouTubeService.php';
session_start();

/* You can acquire an OAuth 2 ID/secret pair from the API Access tab on the Google APIs Console
 <http://code.google.com/apis/console#access>
For more information about using OAuth2 to access Google APIs, please visit:
<https://developers.google.com/accounts/docs/OAuth2>
Please ensure that you have enabled the YouTube Data API for your project. */
$OAUTH2_CLIENT_ID = 'REPLACE ME';
$OAUTH2_CLIENT_SECRET = 'REPLACE ME';

$client = new Google_Client();
$client->setClientId($OAUTH2_CLIENT_ID);
$client->setClientSecret($OAUTH2_CLIENT_SECRET);
$redirect = filter_var('http://' . $_SERVER['HTTP_HOST'] . $_SERVER['PHP_SELF'],
    FILTER_SANITIZE_URL);
$client->setRedirectUri($redirect);

// YouTube object used to make all Data API requests.
$youtube = new Google_YoutubeService($client);

if (isset($_GET['code'])) {
  if (strval($_SESSION['state']) !== strval($_GET['state'])) {
    die('The session state did not match.');
  }

  $client->authenticate();
  $_SESSION['token'] = $client->getAccessToken();
  header('Location: ' . $redirect);
}

if (isset($_SESSION['token'])) {
  $client->setAccessToken($_SESSION['token']);
}

// Check if access token successfully acquired
if ($client->getAccessToken()) {
  try{

    // REPLACE with the video ID that you want to update
    $videoId = "VIDEO_ID";

    // Create a video list request
    $listResponse = $youtube->videos->listVideos("snippet",
        array('id' => $videoId));

    $videoList = $listResponse['items'];
    if (empty($videoList)) {
      $htmlBody .= sprintf('<h3>Can\'t find a video with video id: %s</h3>', $videoId);
    } else {
      // Since a unique video id is given, it will only return 1 video.
      $video = $videoList[0];
      $videoSnippet = $video['snippet'];

      $tags = $videoSnippet['tags'];

      // $tags is null if the video didn't have any tags, so we will check for this and
      // create a new list if needed
      if (is_null($tags)) {
        $tags = array("tag1", "tag2");
      } else {
        array_push($tags, "tag1", "tag2");
      }

      // Construct the Google_Video with the updated tags, hence the snippet
      $updateVideo = new Google_Video($video);
      $updateSnippet = new Google_VideoSnippet($videoSnippet);
      $updateSnippet->setTags($tags);
      $updateVideo -> setSnippet($updateSnippet);

      // Create a video update request
      $updateResponse = $youtube->videos->update("snippet", $updateVideo);

      $responseTags = $updateResponse['snippet']['tags'];


    $htmlBody .= "<h3>Video Updated</h3><ul>";
    $htmlBody .= sprintf('<li>Tags "%s" and "%s" added for video %s (%s) </li>',
        array_pop($responseTags), array_pop($responseTags),
        $videoId, $updateResponse['snippet']['title']);

    $htmlBody .= '</ul>';
  }
    } catch (Google_ServiceException $e) {
      $htmlBody .= sprintf('<p>A service error occurred: <code>%s</code></p>',
          htmlspecialchars($e->getMessage()));
    } catch (Google_Exception $e) {
      $htmlBody .= sprintf('<p>An client error occurred: <code>%s</code></p>',
          htmlspecialchars($e->getMessage()));
    }

    $_SESSION['token'] = $client->getAccessToken();
    } else {
      // If the user hasn't authorized the app, initiate the OAuth flow
      $state = mt_rand();
      $client->setState($state);
      $_SESSION['state'] = $state;

      $authUrl = $client->createAuthUrl();
      $htmlBody = <<<END
  <h3>Authorization Required</h3>
  <p>You need to <a href="$authUrl">authorize access</a> before proceeding.<p>
END;
    }
    ?>

    <!doctype html>
    <html>
    <head>
    <title>Video Updated</title>
    </head>
    <body>
      <?=$htmlBody?>
    </body>
    </html>
