// Some variables to remember state.
var playlistId, nextPageToken, prevPageToken;

// Once the api loads call a function to get the uploads playlist id.
function handleAPILoaded() {
  requestUserUploadsPlaylistId();
}

//Retrieve the uploads playlist id.
function requestUserUploadsPlaylistId() {
  // https://developers.google.com/youtube/v3/docs/channels/list
  var request = gapi.client.youtube.channels.list({
    // mine: '' indicates that we want to retrieve the channel for the authenticated user.
    mine: '',
    part: 'contentDetails'
  });
  request.execute(function(response) {
    playlistId = response.result.items[0].contentDetails.uploads;
    requestVideoPlaylist(playlistId);
  });
}

// Retrieve a playist of videos.
function requestVideoPlaylist(playlistId, pageToken) {
  $('#video-container').html('');
  var requestOptions = {
    playlistId: playlistId,
    part: 'snippet',
    maxResults: 9
  };
  if (pageToken) {
    requestOptions.pageToken = pageToken;
  }
  var request = gapi.client.youtube.playlistItems.list(requestOptions);
  request.execute(function(response) {
    // Only show the page buttons if there's a next or previous page.
    nextPageToken = response.result.nextPageToken;
    var nextVis = nextPageToken ? 'visible' : 'hidden';
    $('#next-button').css('visibility', nextVis);
    prevPageToken = response.result.prevPageToken
    var prevVis = prevPageToken ? 'visible' : 'hidden';
    $('#prev-button').css('visibility', prevVis);

    var playlistItems = response.result.items;
    if (playlistItems) {
      // For each result lets show a thumbnail.
      jQuery.each(playlistItems, function(index, item) {
        createDisplayThumbnail(item.snippet);
      });
    } else {
      $('#video-container').html('Sorry you have no uploaded videos');
    }
  });
}


// Create a thumbnail for a video snippet.
function createDisplayThumbnail(videoSnippet) {
  var titleEl = $('<h3>');
  titleEl.addClass('video-title');
  $(titleEl).html(videoSnippet.title);
  var thumbnailUrl = videoSnippet.thumbnails.medium.url;

  var div = $('<div>');
  div.addClass('video-content');
  div.css('backgroundImage', 'url("' + thumbnailUrl + '")');
  div.append(titleEl);
  $('#video-container').append(div);
}

// Retrieve the next page of videos.
function nextPage() {
  requestVideoPlaylist(playlistId, nextPageToken);
}

// Retrieve the previous page of videos.
function previousPage() {
  requestVideoPlaylist(playlistId, prevPageToken);
}
