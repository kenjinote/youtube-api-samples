<?php

// Call set_include_path() as needed to point to your client library.
require_once 'Google_Client.php';
require_once 'contrib/Google_YouTubeService.php';
session_start();

/* You can acquire an OAuth 2 ID/secret pair from the API Access tab on the Google APIs Console
 <http://code.google.com/apis/console#access>
For more information about using OAuth2 to access Google APIs, please visit:
<https://developers.google.com/accounts/docs/OAuth2>
Please ensure that you have enabled the YouTube Data API for your project. */
$OAUTH2_CLIENT_ID = 'REPLACE_ME';
$OAUTH2_CLIENT_SECRET = 'REPLACE_ME';

$client = new Google_Client();
$client->setClientId($OAUTH2_CLIENT_ID);
$client->setClientSecret($OAUTH2_CLIENT_SECRET);
$redirect = filter_var('http://' . $_SERVER['HTTP_HOST'] . $_SERVER['PHP_SELF'],
    FILTER_SANITIZE_URL);
$client->setRedirectUri($redirect);

// YouTube object used to make all API requests.
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
  try {
    // This code will create a new, private playlist in the authorized user's channel and
    // add the video to the playlist.
    // 1. Create the PlaylistSnippet and add the required data.
    $playlistSnippet = new Google_PlaylistSnippet();
    $playlistSnippet->setTitle('Test Playlist  ' . date("Y-m-d H:i:s"));
    $playlistSnippet->setDescription('A private playlist created with the YouTube API v3');

    // 2. Create playlist status describing the privacy setting.
    $playlistStatus = new Google_PlaylistStatus();
    $playlistStatus->setPrivacyStatus('private');

    // 3. Create a playlist insert request with snippet and status.
    $youTubePlaylist = new Google_Playlist();
    $youTubePlaylist->setSnippet($playlistSnippet);
    $youTubePlaylist->setStatus($playlistStatus);

    // 4. Execute the request and return an object containing information about the new playlist
    $playlistResponse = $youtube->playlists->insert('snippet,status',
        $youTubePlaylist, array());

    // 5. Add the video to the playlist
    //   a. Create a resource id with video id and kind.
    $resourceId = new Google_ResourceId();
    $resourceId->setVideoId('SZj6rAYkYOg');
    $resourceId->setKind('youtube#video');

    //   b. Create a snippet with resource id.
    $playlistItemSnippet = new Google_PlaylistItemSnippet();
    $playlistItemSnippet->setTitle('First video in the test playlist');
    $playlistItemSnippet->setPlaylistId($playlistResponse['id']);
    $playlistItemSnippet->setResourceId($resourceId);

    //   c. Create a playlist item request request with snippet.
    $playlistItem = new Google_PlaylistItem();
    $playlistItem->setSnippet($playlistItemSnippet);

    //   d. Execute the request and return an object containing information about the
    //      new playlistItem
    $playlistItemResponse = $youtube->playlistItems->insert('snippet,contentDetails',
        $playlistItem, array());

    $htmlBody .= "<h3>New Playlist</h3><ul>";
    $htmlBody .= sprintf('<li>%s (%s)</li>',
        $playlistResponse['snippet']['title'],
        $playlistResponse['id']);
    $htmlBody .= '</ul>';

    $htmlBody .= "<h3>New PlaylistItem</h3><ul>";
    $htmlBody .= sprintf('<li>%s (%s)</li>',
        $playlistItemResponse['snippet']['title'],
        $playlistItemResponse['id']);
    $htmlBody .= '</ul>';

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
<title>New Playlist</title>
</head>
<body>
  <?=$htmlBody?>
</body>
</html>
