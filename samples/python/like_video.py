#!/usr/bin/python

import httplib2
import os
import sys

from apiclient.discovery import build
from oauth2client.file import Storage
from oauth2client.client import flow_from_clientsecrets
from oauth2client.tools import run
from optparse import OptionParser


# CLIENT_SECRETS_FILE, name of a file containing the OAuth 2.0 information for
# this application, including client_id and client_secret. You can acquire an
# ID/secret pair from the API Access tab on the Google APIs Console
#   http://code.google.com/apis/console#access
# For more information about using OAuth2 to access Google APIs, please visit:
#   https://developers.google.com/accounts/docs/OAuth2
# For more information about the client_secrets.json file format, please visit:
#   https://developers.google.com/api-client-library/python/guide/aaa_client_secrets
# Please ensure that you have enabled the YouTube Data API for your project.
CLIENT_SECRETS_FILE = "client_secrets.json"

# Helpful message to display if the CLIENT_SECRETS_FILE is missing.
MISSING_CLIENT_SECRETS_MESSAGE = """
WARNING: Please configure OAuth 2.0

To make this sample run you will need to populate the client_secrets.json file
found at:

   %s

with information from the APIs Console
https://code.google.com/apis/console#access

For more information about the client_secrets.json file format, please visit:
https://developers.google.com/api-client-library/python/guide/aaa_client_secrets
""" % os.path.abspath(os.path.join(os.path.dirname(__file__),
                                   CLIENT_SECRETS_FILE))

# An OAuth 2 access scope that allows for full read/write access.
YOUTUBE_READ_WRITE_SCOPE = "https://www.googleapis.com/auth/youtube"
YOUTUBE_API_SERVICE_NAME = "youtube"
YOUTUBE_API_VERSION = "v3"

def get_authenticated_service():
  flow = flow_from_clientsecrets(CLIENT_SECRETS_FILE,
    scope=YOUTUBE_READ_WRITE_SCOPE,
    message=MISSING_CLIENT_SECRETS_MESSAGE)

  storage = Storage("%s-oauth2.json" % sys.argv[0])
  credentials = storage.get()

  if credentials is None or credentials.invalid:
    credentials = run(flow, storage)

  return build(YOUTUBE_API_SERVICE_NAME, YOUTUBE_API_VERSION,
    http=credentials.authorize(httplib2.Http()))

def like_video(youtube, video_id):
  channels_list_response = youtube.channels().list(
    mine=True,
    part="contentDetails"
  ).execute()

  # Adding a video as a favorite or to the watch later list is done via the
  # same basic process. Just read the list id of the corresponding playlist
  # instead of "likes" as we're doing here.
  liked_list_id = channels_list_response["items"][0]["contentDetails"]["relatedPlaylists"]["likes"]

  body = dict(
    snippet=dict(
      playlistId=liked_list_id,
      resourceId=dict(
        kind="youtube#video",
        videoId=video_id
      )
    )
  )
  youtube.playlistItems().insert(
    part=",".join(body.keys()),
    body=body
  ).execute()

  print "%s has been liked." % video_id

if __name__ == "__main__":
  parser = OptionParser()
  parser.add_option("--videoid", dest="videoid",
    default="L-oNKK1CrnU", help="ID of video to like.")
  (options, args) = parser.parse_args()

  youtube = get_authenticated_service()
  like_video(youtube, options.videoid)