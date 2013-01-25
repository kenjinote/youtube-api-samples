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

# An OAuth 2 access scope that allows for full read/write access.
YOUTUBE_READ_WRITE_SCOPE = "https://www.googleapis.com/auth/youtube"
YOUTUBE_API_SERVICE_NAME = "youtube"
YOUTUBE_API_VERSION = "v3"

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


def post_bulletin(youtube, options):
  body = dict(
    snippet=dict(
      description=options.message
    )
  )
  if options.videoid:
    body["contentDetails"] = dict(
      bulletin=dict(
        resourceId=dict(
          kind="youtube#video",
          videoId=options.videoid
        )
      )
    )
  if options.playlistid:
    body["contentDetails"] = dict(
      bulletin=dict(
        resourceId=dict(
          kind="youtube#playlist",
          playlistId=options.playlistid
        )
      )
    )

  youtube.activities().insert(
    part=",".join(body.keys()),
    body=body
  ).execute()

  print "The bulletin was posted to your channel."


if __name__ == "__main__":
  parser = OptionParser()
  parser.add_option("--message", dest="message",
    help="Required text of message to post.")
  parser.add_option("--videoid", dest="videoid",
    help="Optional ID of video to post.")
  parser.add_option("--playlistid", dest="playlistid",
    help="Optional ID of playlist to post.")
  (options, args) = parser.parse_args()

  # You can post a message with or without an accompanying video or playlist.
  # You can't post both a video and playlist at the same time.
  
  if options.videoid and options.playlistid:
    parser.print_help()
    exit("\nYou cannot post a video and a playlist at the same time.")

  if not options.message:
    parser.print_help()
    exit("\nPlease provide a message.")

  youtube = get_authenticated_service()
  post_bulletin(youtube, options)
