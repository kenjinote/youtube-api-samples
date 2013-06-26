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

# An OAuth 2 access scope that allows for full read/write access
YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube"
YOUTUBE_API_SERVICE_NAME = "youtube"
YOUTUBE_API_VERSION = "v3"

def get_authenticated_service():
  flow = flow_from_clientsecrets(CLIENT_SECRETS_FILE, scope=YOUTUBE_SCOPE,
    message=MISSING_CLIENT_SECRETS_MESSAGE)

  storage = Storage("%s-oauth2.json" % sys.argv[0])
  credentials = storage.get()

  if credentials is None or credentials.invalid:
    credentials = run(flow, storage)

  return build(YOUTUBE_API_SERVICE_NAME, YOUTUBE_API_VERSION,
    http=credentials.authorize(httplib2.Http()))

def add_featured_video(options):
  youtube = get_authenticated_service()

  add_video_request = youtube.channels().update(
    part="invideoPromotion",
    # Test different payloads in the API explorer:
    #    https://developers.google.com/youtube/v3/docs/channels/update#try-it
    body={
      "invideoPromotion": {
        "position": {
          "cornerPosition": options.position,
          "type": "corner"
      },
      "items": [{
        "type": "video",
        "videoId": options.video_id
      }],
      "timing": {
        "offsetMs": options.offset_ms,
        "type": options.offset_type
      }
    },
    "id": options.channel_id
  })

  add_video_response = add_video_request.execute()
  print "Added featured video %s to channel %s." % (
      add_video_response["invideoPromotion"]["items"][0]["videoId"],
      add_video_response["id"])

# If offsetMs or position are not valid, the API will throw an error
VALID_OFFSET_TYPES = ("offsetFromEnd", "offsetFromStart",)
VALID_POSITIONS = ("topLeft", "topRight", "bottomLeft", "bottomRight",)

if __name__ == '__main__':
  parser = OptionParser()
  parser.add_option("--channel_id", dest="channel_id", help="Channel ID of the channel to add a featured video")
  parser.add_option("--video_id", dest="video_id", help="Video ID to feature on your channel")
  parser.add_option("--position", dest="position",
    help="Position to show promotion. Options are: %s" % ", ".join(VALID_POSITIONS),
    default="bottomLeft")
  parser.add_option("--offset_ms", dest="offset_ms",
    help="Offset in milliseconds to show video. Default is 10000, or 10 seconds",
    default="10000")
  parser.add_option("--offset_type", dest="offset_type",
    help="Describes whether the offset is from the beginning or end of video playback."
      + " Valid options are: %s" % ",".join(VALID_OFFSET_TYPES),
    default="offsetFromEnd")
  (options, args) = parser.parse_args()

  # Require a channel ID and video ID
  if options.channel_id is None:
    exit("Please specify a valid channel ID using the --channel_id parameter.")
  elif options.video_id is None:
    exit("Please specify a valid video ID to feature using the --video_id parameter.")
  # Validate offset type and position parameters
  if options.offset_type not in VALID_OFFSET_TYPES:
    exit("offset_type must be one of: %s" % ",".join(VALID_OFFSET_TYPES))
  if options.position not in VALID_POSITIONS:
    exit("position must be one of: %s" % ", ".join(VALID_POSITIONS))
  else:
    add_featured_video(options)
