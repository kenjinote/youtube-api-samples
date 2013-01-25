(function() {
  // Retrieve your client ID from the Google APIs Console at
  // https://code.google.com/apis/console.
  var OAUTH2_CLIENT_ID = 'YOUR_CLIENT_ID';
  var OAUTH2_SCOPES = [
    'https://www.googleapis.com/auth/yt-analytics.readonly',
    'https://www.googleapis.com/auth/youtube.readonly'
  ];

  var ONE_MONTH_IN_MILLISECONDS = 1000 * 60 * 60 * 24 * 30;

  // Keeps track of the currently authenticated user's YouTube user ID.
  var channelId;

  // For information about the Google Chart Tools API, see
  // https://developers.google.com/chart/interactive/docs/quick_start
  google.load('visualization', '1.0', {'packages': ['corechart']});

  // The Google APIs JS client invokes this callback automatically after loading.
  // See http://code.google.com/p/google-api-javascript-client/wiki/Authentication
  window.onJSClientLoad = function() {
    gapi.auth.init(function() {
      window.setTimeout(checkAuth, 1);
    });
  };

  // Attempt the immediate OAuth 2 client flow as soon as the page loads.
  // If the currently logged-in Google Account has previously authorized
  // OAUTH2_CLIENT_ID, then it will succeed with no user intervention.
  // Otherwise, it will fail and the user interface that prompts for
  // authorization will need to be displayed.
  function checkAuth() {
    gapi.auth.authorize({
      client_id: OAUTH2_CLIENT_ID,
      scope: OAUTH2_SCOPES,
      immediate: true
    }, handleAuthResult);
  }

  // Handle the result of a gapi.auth.authorize() call.
  function handleAuthResult(authResult) {
    if (authResult) {
      // Auth was successful. Hide auth prompts and show things
      // that should be visible after auth succeeds.
      $('.pre-auth').hide();
      $('.post-auth').show();

      loadAPIClientInterfaces();
    } else {
      // Auth was unsuccessful. Show things related to prompting for auth
      // and hide the things that should be visible after auth succeeds.
      $('.post-auth').hide();
      $('.pre-auth').show();

      // Make the #login-link clickable. Attempt a non-immediate OAuth 2 client
      // flow. The current function will be called when that flow completes.
      $('#login-link').click(function() {
        gapi.auth.authorize({
          client_id: OAUTH2_CLIENT_ID,
          scope: OAUTH2_SCOPES,
          immediate: false
        }, handleAuthResult);
      });
    }
  }

  // Load the client interface for the YouTube Analytics and Data APIs, which
  // is required to use the Google APIs JS client. More info is available at
  // http://code.google.com/p/google-api-javascript-client/wiki/GettingStarted#Loading_the_Client
  function loadAPIClientInterfaces() {
    gapi.client.load('youtube', 'v3', function() {
      gapi.client.load('youtubeAnalytics', 'v1', function() {
        // After both client interfaces load, use the Data API to request
        // information about the authenticated user's channel.
        getUserChannel();
      });
    });
  }

  // Calls the Data API to retrieve info about the currently authenticated
  // user's YouTube channel.
  function getUserChannel() {
    // https://developers.google.com/youtube/v3/docs/channels/list
    var request = gapi.client.youtube.channels.list({
      // "mine: true" indicates that you want to retrieve the authenticated user's channel.
      mine: true,
      part: 'id,contentDetails'
    });

    request.execute(function(response) {
      if ('error' in response) {
        displayMessage(response.error.message);
      } else {
        // We will need the channel's channel ID to make calls to the
        // Analytics API. The channel ID looks like "UCdLFeWKpkLhkguiMZUp8lWA".
        channelId = response.items[0].id;
        // This string, of the form "UUdLFeWKpkLhkguiMZUp8lWA", is a unique ID
        // for a playlist of videos uploaded to the authenticated user's channel.
        var uploadsListId = response.items[0].contentDetails.relatedPlaylists.uploads;
        // Use the uploads playlist ID to retrieve the list of uploaded videos.
        getPlaylistItems(uploadsListId);
      }
    });
  }

  // Calls the Data API to retrieve the items in a particular playlist. In this
  // example, we are retrieving a playlist of the currently authenticated user's
  // uploaded videos. By default, the list returns the most recent videos first.
  function getPlaylistItems(listId) {
    // https://developers.google.com/youtube/v3/docs/playlistitems/list
    var request = gapi.client.youtube.playlistItems.list({
      playlistId: listId,
      part: 'snippet'
    });

    request.execute(function(response) {
      if ('error' in response) {
        displayMessage(response.error.message);
      } else {
        if ('items' in response) {
          // jQuery.map() iterates through all of the items in the response and
          // creates a new array that only contains the specific property we're
          // looking for: videoId.
          var videoIds = $.map(response.items, function(item) {
            return item.snippet.resourceId.videoId;
          });

          // Now that we know the IDs of all the videos in the uploads list,
          // we can retrieve info about each video.
          getVideoMetadata(videoIds);
        } else {
          displayMessage('There are no videos in your channel.');
        }
      }
    });
  }

  // Given an array of video ids, obtains metadata about each video and then
  // uses that metadata to display a list of videos to the user.
  function getVideoMetadata(videoIds) {
    // https://developers.google.com/youtube/v3/docs/videos/list
    var request = gapi.client.youtube.videos.list({
      // The 'id' property value is a comma-separated string of video IDs.
      id: videoIds.join(','),
      part: 'id,snippet,statistics'
    });

    request.execute(function(response) {
      if ('error' in response) {
        displayMessage(response.error.message);
      } else {
        // Get the jQuery wrapper for #video-list once outside the loop.
        var videoList = $('#video-list');
        $.each(response.items, function() {
          // Exclude videos that don't have any views, since those videos
          // will not have any interesting viewcount analytics data.
          if (this.statistics.viewCount == 0) {
            return;
          }

          var title = this.snippet.title;
          var videoId = this.id;

          // Create a new &lt;li&gt; element that contains an &lt;a&gt; element.
          // Set the &lt;a&gt; element's text content to the video's title, and
          // add a click handler that will display Analytics data when invoked.
          var liElement = $('<li>');
          var aElement = $('<a>');
          // The dummy href value of '#' ensures that the browser renders the
          // &lt;a&gt; element as a clickable link.
          aElement.attr('href', '#');
          aElement.text(title);
          aElement.click(function() {
            displayVideoAnalytics(videoId);
          });

          // Call the jQuery.append() method to add the new &lt;a&gt; element to
          // the &lt;li&gt; element, and the &lt;li&gt; element to the parent
          // list, which is identified by the 'videoList' variable.
          liElement.append(aElement);
          videoList.append(liElement);
        });

        if (videoList.children().length == 0) {
          displayMessage('Your channel does not have any videos that have been viewed.');
        }
      }
    });
  }

  // Requests YouTube Analytics for a video, and displays results in a chart.
  function displayVideoAnalytics(videoId) {
    if (channelId) {
      // To use a different date range, modify the ONE_MONTH_IN_MILLISECONDS
      // variable to a different millisecond delta as desired.
      var today = new Date();
      var lastMonth = new Date(today.getTime() - ONE_MONTH_IN_MILLISECONDS);

      var request = gapi.client.youtubeAnalytics.reports.query({
        // The start-date and end-date parameters need to be YYYY-MM-DD strings.
        'start-date': formatDateString(lastMonth),
        'end-date': formatDateString(today),
        // A future YouTube Analytics API release should support channel==default.
        // In the meantime, you need to explicitly specify channel==channelId.
        // See https://devsite.googleplex.com/youtube/analytics/v1/#ids
        ids: 'channel==' + channelId,
        dimensions: 'day',
        // See https://developers.google.com/youtube/analytics/v1/available_reports for details
        // on different filters and metrics you can request when dimensions=day.
        metrics: 'views',
        filters: 'video==' + videoId
      });

      request.execute(function(response) {
        // This function is called regardless of whether the request succeeds.
        // The response either has valid analytics data or an error message.
        if ('error' in response) {
          displayMessage(response.error.message);
        } else {
          displayChart(videoId, response);
        }
      });
    } else {
      displayMessage('The YouTube user id for the current user is not available.');
    }
  }

  // Boilerplate code to take a Date object and return a YYYY-MM-DD string.
  function formatDateString(date) {
    var yyyy = date.getFullYear().toString();
    var mm = padToTwoCharacters(date.getMonth() + 1);
    var dd = padToTwoCharacters(date.getDate());

    return yyyy + '-' + mm + '-' + dd;
  }

  // If number is a single digit, prepend a '0'. Otherwise, return it as a string.
  function padToTwoCharacters(number) {
    if (number < 10) {
      return '0' + number;
    } else {
      return number.toString();
    }
  }

  // Calls the Google Chart Tools API to generate a chart of analytics data.
  function displayChart(videoId, response) {
    if ('rows' in response) {
      hideMessage();

      // The columnHeaders property contains an array of objects representing
      // each column's title &ndash; e.g.: [{name:"day"},{name:"views"}]
      // We need these column titles as a simple array, so we call jQuery.map()
      // to get each element's "name" property and create a new array that only
      // contains those values.
      var columns = $.map(response.columnHeaders, function(item) {
        return item.name;
      });
      // The google.visualization.arrayToDataTable() wants an array of arrays.
      // The first element is an array of column titles, calculated above as
      // "columns". The remaining elements are arrays that each represent
      // a row of data. Fortunately, response.rows is already in this format,
      // so it can just be concatenated.
      // See https://developers.google.com/chart/interactive/docs/datatables_dataviews#arraytodatatable
      var chartDataArray = [columns].concat(response.rows);
      var chartDataTable = google.visualization.arrayToDataTable(chartDataArray);

      var chart = new google.visualization.LineChart(document.getElementById('chart'));
      chart.draw(chartDataTable, {
        // Additional options can be set if desired.
        // See https://developers.google.com/chart/interactive/docs/reference#visdraw
        title: 'Views per Day of Video ' + videoId
      });
    } else {
      displayMessage('No data available for video ' + videoId);
    }
  }

  // Helper method to display a message on the page.
  function displayMessage(message) {
    $('#message').text(message).show();
  }

  // Helper method to hide a previously displayed message on the page.
  function hideMessage() {
    $('#message').hide();
  }
})();
