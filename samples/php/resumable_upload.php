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
  try{
    // REPLACE with the path to your file that you want to upload
    $videoPath = "/path/to/file.mp4";

    // Create a snipet with title, description, tags and category id
    $snippet = new Google_VideoSnippet();
    $snippet->setTitle("Test title");
    $snippet->setDescription("Test description");
    $snippet->setTags(array("tag1", "tag2"));

    // Numeric video category. See
    // https://developers.google.com/youtube/v3/docs/videoCategories/list 
    $snippet->setCategoryId("22");

    // Create a video status with privacy status. Options are "public", "private" and "unlisted".
    $status = new Google_VideoStatus();
    $status->privacyStatus = "public";

    // Create a YouTube video with snippet and status
    $video = new Google_Video();
    $video->setSnippet($snippet);
    $video->setStatus($status);

    // Size of each chunk of data in bytes. Setting it higher leads faster upload (less chunks,
    // for reliable connections). Setting it lower leads better recovery (fine-grained chunks)
    $chunkSizeBytes = 1 * 1024 * 1024;

    // Create a MediaFileUpload with resumable uploads
    $media = new Google_MediaFileUpload('video/*', null, true, $chunkSizeBytes);
    $media->setFileSize(filesize($videoPath));

    // Create a video insert request
    $insertResponse = $youtube->videos->insert("status,snippet", $video,
        array('mediaUpload' => $media));

    $uploadStatus = false;

    // Read file and upload chunk by chunk
    $handle = fopen($videoPath, "rb");
    while (!$uploadStatus && !feof($handle)) {
      $chunk = fread($handle, $chunkSizeBytes);
      $uploadStatus = $media->nextChunk($insertResponse, $chunk);
    }

    fclose($handle);


    $htmlBody .= "<h3>Video Uploaded</h3><ul>";
    $htmlBody .= sprintf('<li>%s (%s)</li>',
        $uploadStatus['snippet']['title'],
        $uploadStatus['id']);

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
<title>Video Uploaded</title>
</head>
<body>
  <?=$htmlBody?>
</body>
</html>
