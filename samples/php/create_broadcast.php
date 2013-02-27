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
    // Create a snippet with title, scheduled start and end times.
    $broadcastSnippet = new Google_LiveBroadcastSnippet();
    $broadcastSnippet->setTitle('New Broadcast');
    $broadcastSnippet->setScheduledStartTime('2034-01-30T00:00:00.000Z');
    $broadcastSnippet->setScheduledEndTime('2034-01-31T00:00:00.000Z');

    // Create LiveBroadcastStatus with privacy status.
    $status = new Google_LiveBroadcastStatus();
    $status->setPrivacyStatus('private');

    // Create the insert request
    $broadcastInsert = new Google_LiveBroadcast();
    $broadcastInsert->setSnippet($broadcastSnippet);
    $broadcastInsert->setStatus($status);
    $broadcastInsert->setKind('youtube#liveBroadcast');

    // Execute the request and return an object containing information about the new broadcast
    $broadcastsResponse = $youtube->liveBroadcasts->insert('snippet,status',
        $broadcastInsert, array());

    // Create a snippet with title.
    $streamSnippet = new Google_LiveStreamSnippet();
    $streamSnippet->setTitle('New Stream');

    // Create content distribution network with format and ingestion type.
    $cdn = new Google_LiveStreamCdn();
    $cdn->setFormat("1080p");
    $cdn->setIngestionType('rtmp');

    // Create the insert request
    $streamInsert = new Google_LiveStream();
    $streamInsert->setSnippet($streamSnippet);
    $streamInsert->setCdn($cdn);
    $streamInsert->setKind('youtube#liveStream');
    $streamsResponse = $youtube->liveStreams->insert('snippet,cdn',
        $streamInsert, array());

    // Execute the request and return an object containing information about the bound broadcast
    $bindBroadcastResponse = $youtube->liveBroadcasts->bind(
        $broadcastsResponse['id'],'id,contentDetails',
        array(
            'streamId' => $streamsResponse['id'],
        ));

    $htmlBody .= "<h3>Added Broadcast</h3><ul>";
    $htmlBody .= sprintf('<li>%s published at %s (%s)</li>',
        $broadcastsResponse['snippet']['title'],
        $broadcastsResponse['snippet']['publishedAt'],
        $broadcastsResponse['id']);
    $htmlBody .= '</ul>';

    $htmlBody .= "<h3>Added Stream</h3><ul>";
    $htmlBody .= sprintf('<li>%s (%s)</li>',
        $streamsResponse['snippet']['title'],
        $streamsResponse['id']);
    $htmlBody .= '</ul>';

    $htmlBody .= "<h3>Bound Broadcast</h3><ul>";
    $htmlBody .= sprintf('<li>Broadcast (%s) was bound to stream (%s).</li>',
        $bindBroadcastResponse['id'],
        $bindBroadcastResponse['contentDetails']['boundStreamId']);
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
<title>Bound Live Broadcast</title>
</head>
<body>
  <?=$htmlBody?>
</body>
</html>
