#!/usr/bin/python

import httplib2
import os
import random
import sys
import time

from apiclient.discovery import build
from apiclient.errors import HttpError
from oauth2client.file import Storage
from oauth2client.client import flow_from_clientsecrets
from oauth2client.tools import run
from optparse import OptionParser

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
YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube"
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
    scope=YOUTUBE_READONLY_SCOPE,
    message=MISSING_CLIENT_SECRETS_MESSAGE)

  storage = Storage("%s-oauth2.json" % sys.argv[0])
  credentials = storage.get()

  if credentials is None or credentials.invalid:
    credentials = run(flow, storage)

  return build(YOUTUBE_API_SERVICE_NAME, YOUTUBE_API_VERSION,
    http=credentials.authorize(httplib2.Http()))


def insert_broadcast(youtube, options):
  insert_broadcast_response = youtube.liveBroadcasts().insert(
    part="snippet,status",
    body=dict(
      kind='youtube#liveBroadcast',
      snippet=dict(
        title=options.broadcast_title,
        scheduledStartTime=options.start_time,
        scheduledEndTime=options.end_time
        ),
      status=dict(
        privacyStatus=options.privacy_status
      ))).execute()

  snippet = insert_broadcast_response["snippet"]

  print "Broadcast '%s' with title '%s' was published at '%s'" % (insert_broadcast_response["id"], snippet["title"], snippet["publishedAt"])
  return insert_broadcast_response["id"]


def insert_stream(youtube, options):
  insert_stream_response = youtube.liveStreams().insert(
    part="snippet,cdn",
    body=dict(
      kind='youtube#liveStream',
      snippet=dict(
        title=options.stream_title
        ),
      cdn=dict(
       format="1080p",
       ingestionType="rtmp"
    ))).execute()
  snippet = insert_stream_response["snippet"]

  print "Stream '%s' with title '%s' was inserted" % (insert_stream_response["id"], snippet["title"])
  return insert_stream_response["id"]


def bind_broadcast(youtube, broadcast_id, stream_id):
  bind_broadcast_response = youtube.liveBroadcasts().bind(
    part="id,contentDetails",
    id=broadcast_id,
    streamId=stream_id).execute()

  print "Broadcast '%s' was bound to stream '%s'." % (bind_broadcast_response["id"], bind_broadcast_response["contentDetails"]["boundStreamId"])


if __name__ == "__main__":
  parser = OptionParser()
  parser.add_option("--broadcast-title", dest="broadcast_title", help="Broadcast title",
    default="New Broadcast")
  parser.add_option("--privacy-status", dest="privacy_status",
    help="Broadcast privacy status", default="private")
  parser.add_option("--start-time", dest="start_time",
    help="Scheduled start time", default='2014-01-30T00:00:00.000Z')
  parser.add_option("--end-time", dest="end_time",
    help="Scheduled end time", default='2014-01-31T00:00:00.000Z')
  parser.add_option("--stream-title", dest="stream_title", help="Stream title",
    default="New Stream")
  (options, args) = parser.parse_args()

  youtube = get_authenticated_service()
  broadcast_id = insert_broadcast(youtube, options)
  stream_id = insert_stream(youtube, options)
  bind_broadcast(youtube, broadcast_id, stream_id)
